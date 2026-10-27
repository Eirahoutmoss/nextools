package com.eirahoutmoss.nextools.ui.tools

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.measure.RunningStats
import com.eirahoutmoss.nextools.core.measure.Sonar
import com.eirahoutmoss.nextools.records.Column as DataColumn
import com.eirahoutmoss.nextools.records.SummaryItem
import com.eirahoutmoss.nextools.ui.Fmt
import com.eirahoutmoss.nextools.ui.components.BadgeLevel
import com.eirahoutmoss.nextools.ui.components.BigReadout
import com.eirahoutmoss.nextools.ui.components.KeyValue
import com.eirahoutmoss.nextools.ui.components.NoticeBanner
import com.eirahoutmoss.nextools.ui.components.NumberInputDialog
import com.eirahoutmoss.nextools.ui.components.RecordChart
import com.eirahoutmoss.nextools.ui.components.RecordingSpec
import com.eirahoutmoss.nextools.ui.components.SectionCard
import com.eirahoutmoss.nextools.ui.components.StatRow
import com.eirahoutmoss.nextools.ui.components.ToolScaffold
import com.eirahoutmoss.nextools.ui.components.rememberRecording
import com.eirahoutmoss.nextools.ui.theme.LocalInstrument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SONAR_FS = 48_000
private const val PINGS = 10
private const val SPACING_S = 0.1
private const val MAX_DIST_M = 5.0

private class SonarResult(val distanceM: Double?, val confidence: Double, val curve: DoubleArray, val lead: Int)

/** Tarama dizisini çalar ve aynı anda kaydeder; ilişkiyi darbe darbe hizalayıp ortalar. */
@SuppressLint("MissingPermission")
private suspend fun runSonar(context: Context, tempC: Double): SonarResult = withContext(Dispatchers.IO) {
    val chirp = Sonar.chirp(SONAR_FS, 3000.0, 9000.0, 0.004)
    val spacing = (SPACING_S * SONAR_FS).toInt()
    val play = FloatArray(spacing * PINGS)
    for (k in 0 until PINGS) for (i in chirp.indices) play[k * spacing + i] = (0.9 * chirp[i]).toFloat()
    val total = play.size + SONAR_FS / 2
    val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val src = if (am.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true") MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.VOICE_RECOGNITION
    val minBuf = AudioRecord.getMinBufferSize(SONAR_FS, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
    val rec = AudioRecord(src, SONAR_FS, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, 19200))
    val track = AudioTrack.Builder()
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .setAudioFormat(AudioFormat.Builder().setSampleRate(SONAR_FS).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
        .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(play.size * 4).build()
    val buf = ShortArray(total)
    try {
        if (rec.state != AudioRecord.STATE_INITIALIZED) throw IllegalStateException("mic")
        track.write(play, 0, play.size, AudioTrack.WRITE_BLOCKING)
        rec.startRecording()
        var pos = 0
        // Kayıt başladıktan biraz sonra çal: başlangıç geçişi doğrudan sesi bozmasın
        val warm = SONAR_FS / 20
        while (pos < warm) { val r = rec.read(buf, pos, warm - pos); if (r <= 0) break; pos += r }
        track.play()
        while (pos < total) { val r = rec.read(buf, pos, minOf(4800, total - pos)); if (r <= 0) break; pos += r }
    } finally {
        runCatching { rec.stop() }; rec.release(); runCatching { track.stop() }; track.release()
    }
    val x = DoubleArray(total) { buf[it] / 32768.0 }
    val corr = Sonar.correlate(x, chirp, total - chirp.size)
    val c = Sonar.soundSpeed(tempC)
    val lead = 60
    val win = lead + (2 * MAX_DIST_M / c * SONAR_FS).toInt() + 10
    val max = corr.max()
    val first = corr.indexOfFirst { it > 0.5 * max }.coerceAtLeast(0)
    fun localMax(around: Int, r: Int): Int { var b = around.coerceIn(0, corr.size - 1); for (i in (around - r).coerceAtLeast(0)..(around + r).coerceAtMost(corr.size - 1)) if (corr[i] > corr[b]) b = i; return b }
    val d0 = localMax(first + 20, 40)
    val avg = DoubleArray(win)
    var used = 0
    for (k in 0 until PINGS) {
        val dk = localMax(d0 + k * spacing, 30)
        if (dk - lead < 0 || dk - lead + win > corr.size) continue
        for (i in 0 until win) avg[i] += corr[dk - lead + i]
        used++
    }
    if (used > 0) for (i in avg.indices) avg[i] /= used
    // Doğrudan ses ilk 20 ms içinde aransın diye eğri, doğrudan tepeden [lead] önce başlar
    val e = Sonar.echo(avg, SONAR_FS, c, maxDistM = MAX_DIST_M)
    SonarResult(e?.distanceM, e?.confidence ?: 0.0, avg, lead)
}

/** Sonar Mesafe: hoparlörden kısa taramalar, yakın duvardan dönen yankının süresi. Kabadır. */
@Composable
fun SonarTool(onBack: () -> Unit, onOpenRecord: (String) -> Unit) {
    val context = LocalContext.current
    val rec = rememberRecording(ToolId.SONAR)
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<SonarResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var tempC by rememberSaveable { mutableDoubleStateOf(20.0) }
    var editTemp by remember { mutableStateOf(false) }
    val history = remember { mutableStateListOf<Double>() }

    ToolScaffold(
        tool = ToolId.SONAR, onBack = onBack, onOpenRecord = onOpenRecord,
        recording = rec, onStartRecording = null, frozen = false, onFreezeToggle = null, onCalibrate = { editTemp = true },
    ) {
        PermissionGate(Manifest.permission.RECORD_AUDIO, stringResource(R.string.mic_rationale_generic)) {
            val r = result
            val good = r?.distanceM != null && r.confidence >= 4
            BigReadout(
                value = if (good) Fmt.num(r!!.distanceM!!, 2) else "—", unit = "m",
                caption = when {
                    busy -> stringResource(R.string.sonar_measuring)
                    r != null && !good -> stringResource(R.string.sonar_no_echo)
                    else -> stringResource(R.string.sonar_caption)
                },
                badge = stringResource(R.string.sonar_badge), badgeLevel = BadgeLevel.APPROX,
            )
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Button(onClick = {
                busy = true; error = null
                scope.launch {
                    try {
                        val res = runSonar(context, tempC)
                        result = res
                        if (res.distanceM != null && res.confidence >= 4) { history.add(0, res.distanceM); while (history.size > 10) history.removeAt(history.lastIndex) }
                    } catch (e: Exception) { error = context.getString(R.string.sound_mic_failed, 0) } finally { busy = false }
                }
            }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.sonar_start)) }
            error?.let { NoticeBanner(it, BadgeLevel.ALARM) }
            val st = RunningStats().also { s -> history.forEach { s.add(it) } }
            StatRow(
                listOf(
                    stringResource(R.string.sonar_confidence) to (r?.let { Fmt.num(it.confidence, 1) } ?: "—"),
                    stringResource(R.string.stat_avg) to (if (st.count > 0) Fmt.num(st.average, 2) + " m" else "—"),
                    stringResource(R.string.sonar_temp) to Fmt.num(tempC, 0) + " °C",
                ),
            )
            r?.let { res ->
                SectionCard(stringResource(R.string.sonar_curve)) {
                    val c = Sonar.soundSpeed(tempC)
                    val xs = DoubleArray(res.curve.size) { (it - res.lead).toDouble() / SONAR_FS * c / 2 }
                    val top = res.curve.max().coerceAtLeast(1e-12)
                    val ys = DoubleArray(res.curve.size) { res.curve[it] / top }
                    RecordChart(listOf(Triple(xs, ys, LocalInstrument.current.accent)), xSuffix = " m")
                    Text(stringResource(R.string.sonar_curve_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (good) OutlinedButton(onClick = {
                    rec.saveOneShot(
                        RecordingSpec(
                            columns = listOf(DataColumn("distance_m", "m", 2), DataColumn("confidence", "", 1)),
                            headerComments = csvHeader(context, ToolId.SONAR, "speaker + microphone", context.getString(R.string.sonar_note, Fmt.num(tempC, 0))),
                            calibrated = false, calibrationNote = context.getString(R.string.sonar_note, Fmt.num(tempC, 0)), sensorName = context.getString(R.string.hw_microphone),
                            summarize = { _, _ -> listOf(SummaryItem("last", "distance_m", res.distanceM!!, "m", 2)) },
                        ),
                        listOf(doubleArrayOf(res.distanceM!!, res.confidence)),
                    )
                }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save_to_notebook)) }
            }
            if (history.size > 1) SectionCard(stringResource(R.string.sonar_history)) { history.forEachIndexed { i, d -> KeyValue("#${history.size - i}", Fmt.num(d, 2) + " m") } }
            Text(stringResource(R.string.sonar_tip), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (editTemp) {
        val bad = stringResource(R.string.error_number)
        NumberInputDialog(
            title = stringResource(R.string.sonar_temp), message = stringResource(R.string.sonar_temp_msg), label = "°C", initial = tempC,
            onConfirm = { v -> if (v in -30.0..50.0) { tempC = v; editTemp = false; null } else bad }, onDismiss = { editTemp = false },
        )
    }
}
