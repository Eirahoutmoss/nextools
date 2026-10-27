package com.eirahoutmoss.nextools.ui.tools

import android.location.Location
import android.os.Build
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.geo.Geo
import com.eirahoutmoss.nextools.core.geo.LatLon
import com.eirahoutmoss.nextools.core.measure.RunningStats
import com.eirahoutmoss.nextools.core.measure.SpoofCheck
import com.eirahoutmoss.nextools.core.measure.SpoofCheck.Sign
import com.eirahoutmoss.nextools.records.Column as DataColumn
import com.eirahoutmoss.nextools.sensors.GnssStream
import com.eirahoutmoss.nextools.sensors.RawEpoch
import com.eirahoutmoss.nextools.sensors.Satellite
import com.eirahoutmoss.nextools.ui.Fmt
import com.eirahoutmoss.nextools.ui.components.BadgeLevel
import com.eirahoutmoss.nextools.ui.components.BigReadout
import com.eirahoutmoss.nextools.ui.components.KeyValue
import com.eirahoutmoss.nextools.ui.components.NoticeBanner
import com.eirahoutmoss.nextools.ui.components.RecordingSpec
import com.eirahoutmoss.nextools.ui.components.SectionCard
import com.eirahoutmoss.nextools.ui.components.StatRow
import com.eirahoutmoss.nextools.ui.components.StatusDot
import com.eirahoutmoss.nextools.ui.components.ToolScaffold
import com.eirahoutmoss.nextools.ui.components.rememberRecording
import com.eirahoutmoss.nextools.ui.theme.LocalInstrument

private fun signText(s: Sign) = when (s) {
    Sign.MOCK_LOCATION -> R.string.spoof_mock
    Sign.UNIFORM_CN0 -> R.string.spoof_uniform
    Sign.HIGH_CN0 -> R.string.spoof_high
    Sign.POSITION_JUMP -> R.string.spoof_jump
    Sign.TIME_MISMATCH -> R.string.spoof_time
    Sign.CLOCK_JUMP -> R.string.spoof_clock
    Sign.AGC_DROP -> R.string.spoof_agc
    Sign.SINGLE_CONSTELLATION -> R.string.spoof_single
}

private class SpoofEvent(val sign: Sign, val atMs: Long)

/** Sahte GPS Tespiti: sahte konum, eşit güçte uydular, konum/saat sıçraması, AGC düşüşü gibi işaretler. */
@Composable
fun GpsSpoofTool(onBack: () -> Unit, onOpenRecord: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val rec = rememberRecording(ToolId.GPS_SPOOF)
    var sats by remember { mutableStateOf<List<Satellite>>(emptyList()) }
    var fix by remember { mutableStateOf<Location?>(null) }
    var raw by remember { mutableStateOf<RawEpoch?>(null) }
    var rawAvailable by remember { mutableStateOf<Boolean?>(null) }
    var active by remember { mutableStateOf<Set<Sign>>(emptySet()) }
    val log = remember { mutableStateListOf<SpoofEvent>() }
    // Kalıcı işaretler (sıçramalar) bir süre görünür kalsın
    val sticky = remember { HashMap<Sign, Long>() }
    val prevFix = remember { arrayOfNulls<Location>(1) }
    val prevDisc = remember { intArrayOf(Int.MIN_VALUE) }
    val agcBase = remember { RunningStats() }
    val seenConstellations = remember { HashSet<Int>() }

    fun mark(s: Sign) {
        val now = System.currentTimeMillis()
        if (s !in active) log.add(0, SpoofEvent(s, now)); while (log.size > 50) log.removeAt(log.lastIndex)
        sticky[s] = now
    }

    fun evaluate() {
        val now = System.currentTimeMillis()
        val cur = HashSet<Sign>()
        val l = fix
        if (l != null) {
            val mock = if (Build.VERSION.SDK_INT >= 31) l.isMock else @Suppress("DEPRECATION") l.isFromMockProvider
            if (mock) cur += Sign.MOCK_LOCATION
            if (SpoofCheck.isTimeMismatch(l.time, now)) cur += Sign.TIME_MISMATCH
        }
        val cn0 = sats.filter { it.cn0 > 0 && it.usedInFix }.map { it.cn0.toDouble() }.ifEmpty { sats.filter { it.cn0 > 0 }.map { it.cn0.toDouble() } }
        cur += SpoofCheck.cn0Signs(cn0)
        val strong = sats.filter { it.cn0 > 20 }
        strong.forEach { seenConstellations += it.constellation }
        if (strong.size >= 6 && strong.map { it.constellation }.toSet().size == 1 && seenConstellations.size > 1) cur += Sign.SINGLE_CONSTELLATION
        sticky.entries.removeAll { now - it.value > 30_000 }
        cur += sticky.keys
        cur.filter { it !in active }.forEach { mark(it) }
        active = cur
    }

    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var got = false
            kotlinx.coroutines.withTimeoutOrNull(10_000) { GnssStream.rawEpochs(context).collect { got = true; rawAvailable = true; onRaw(it, prevDisc, agcBase, ::mark); raw = it } }
            if (!got) rawAvailable = false
            else GnssStream.rawEpochs(context).collect { onRaw(it, prevDisc, agcBase, ::mark); raw = it }
        }
    }

    ToolScaffold(
        tool = ToolId.GPS_SPOOF, onBack = onBack, onOpenRecord = onOpenRecord,
        recording = rec,
        onStartRecording = {
            val note = context.getString(R.string.spoof_note)
            rec.start(
                RecordingSpec(
                    columns = listOf(DataColumn("signs", "", 0), DataColumn("mean_cn0", "dB-Hz", 1), DataColumn("cn0_sd", "dB", 2), DataColumn("agc_db", "dB", 1)),
                    headerComments = csvHeader(context, ToolId.GPS_SPOOF, "GNSS", note, listOf("signs = number of active indicators")),
                    calibrated = false, calibrationNote = note, sensorName = "GNSS",
                ),
            )
        },
        frozen = false, onFreezeToggle = null, onCalibrate = null,
    ) {
        PermissionsGate(LOCATION_PERMISSIONS, stringResource(R.string.gps_rationale), required = listOf(android.Manifest.permission.ACCESS_FINE_LOCATION)) {
            CollectGnss(
                onLocation = { l ->
                    val p = prevFix[0]
                    if (p != null) {
                        val d = Geo.distance(LatLon(p.latitude, p.longitude), LatLon(l.latitude, l.longitude))
                        val dt = (l.elapsedRealtimeNanos - p.elapsedRealtimeNanos) / 1e9
                        if (SpoofCheck.isJump(d, dt, if (l.hasAccuracy()) l.accuracy.toDouble() else 20.0)) mark(Sign.POSITION_JUMP)
                    }
                    prevFix[0] = l; fix = l
                    evaluate()
                    if (rec.isRecording) {
                        val s = RunningStats(); sats.filter { it.cn0 > 0 }.forEach { s.add(it.cn0.toDouble()) }
                        rec.add(doubleArrayOf(active.size.toDouble(), s.average, s.stdDev, raw?.agcDb ?: Double.NaN))
                    }
                },
                onSatellites = { sats = it; evaluate() },
            )
            GpsOffBanner()
            val level = SpoofCheck.level(active)
            val inst = LocalInstrument.current
            BigReadout(
                value = stringResource(when (level) { SpoofCheck.Level.OK -> R.string.spoof_ok; SpoofCheck.Level.NOTICE -> R.string.spoof_notice; SpoofCheck.Level.WARNING -> R.string.spoof_warning }),
                unit = "", caption = if (fix == null) stringResource(R.string.spoof_waiting) else stringResource(R.string.spoof_caption, active.size),
                badge = stringResource(R.string.spoof_badge), badgeLevel = BadgeLevel.NEUTRAL,
                valueColor = when (level) { SpoofCheck.Level.OK -> inst.good; SpoofCheck.Level.NOTICE -> inst.warn; SpoofCheck.Level.WARNING -> inst.alarm },
            )
            val s = RunningStats(); sats.filter { it.cn0 > 0 }.forEach { s.add(it.cn0.toDouble()) }
            StatRow(
                listOf(
                    stringResource(R.string.spoof_mean_cn0) to (if (s.count > 0) Fmt.num(s.average, 1) else "—"),
                    stringResource(R.string.spoof_cn0_sd) to (if (s.count > 1) Fmt.num(s.stdDev, 1) else "—"),
                    "AGC" to (raw?.agcDb?.let { Fmt.num(it, 1) + " dB" } ?: "—"),
                ),
            )
            if (rawAvailable == false) NoticeBanner(stringResource(R.string.spoof_no_raw), BadgeLevel.NEUTRAL)
            SectionCard(stringResource(R.string.spoof_checks)) {
                Sign.entries.forEach { sign ->
                    val on = sign in active
                    val needsRaw = sign == Sign.CLOCK_JUMP || sign == Sign.AGC_DROP
                    val unknown = needsRaw && (rawAvailable != true || (sign == Sign.AGC_DROP && raw?.agcDb == null))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(when { on -> inst.alarm; unknown -> inst.passive; else -> inst.good })
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(signText(sign)), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(stringResource(when { on -> R.string.spoof_seen; unknown -> R.string.spoof_unknown; else -> R.string.spoof_normal }), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            if (log.isNotEmpty()) {
                SectionCard(stringResource(R.string.spoof_log)) { log.take(20).forEach { KeyValue(Fmt.dateTime(it.atMs), stringResource(signText(it.sign))) } }
            }
            Text(stringResource(R.string.spoof_tip), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun onRaw(e: RawEpoch, prevDisc: IntArray, agcBase: RunningStats, mark: (Sign) -> Unit) {
    if (prevDisc[0] != Int.MIN_VALUE && e.clockDiscontinuity != prevDisc[0]) mark(Sign.CLOCK_JUMP)
    prevDisc[0] = e.clockDiscontinuity
    e.gnssUtcMs?.let { if (SpoofCheck.isTimeMismatch(it, System.currentTimeMillis())) mark(Sign.TIME_MISMATCH) }
    e.agcDb?.let { a ->
        if (agcBase.count < 30) agcBase.add(a)
        else if (SpoofCheck.isAgcDrop(agcBase.average, a)) mark(Sign.AGC_DROP)
    }
}
