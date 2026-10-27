package com.eirahoutmoss.nextools.ui.tools

import android.Manifest
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.measure.RangeFilter
import com.eirahoutmoss.nextools.core.measure.UwbPairing
import com.eirahoutmoss.nextools.core.measure.UwbPairing.Companion.hex
import com.eirahoutmoss.nextools.core.qr.QrEncoder
import com.eirahoutmoss.nextools.records.Column as DataColumn
import com.eirahoutmoss.nextools.ui.Fmt
import com.eirahoutmoss.nextools.ui.components.BadgeLevel
import com.eirahoutmoss.nextools.ui.components.BigReadout
import com.eirahoutmoss.nextools.ui.components.LiveChart
import com.eirahoutmoss.nextools.ui.components.NoticeBanner
import com.eirahoutmoss.nextools.ui.components.RecordingSpec
import com.eirahoutmoss.nextools.ui.components.RollingSeries
import com.eirahoutmoss.nextools.ui.components.SectionCard
import com.eirahoutmoss.nextools.ui.components.StatRow
import com.eirahoutmoss.nextools.ui.components.ToolScaffold
import com.eirahoutmoss.nextools.ui.components.rememberRecording
import com.eirahoutmoss.nextools.ui.theme.LocalInstrument
import com.eirahoutmoss.nextools.uwb.UwbLink
import com.eirahoutmoss.nextools.uwb.UwbReading
import com.eirahoutmoss.nextools.uwb.UwbRole
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.min

/** UWB Mesafe / Yön: iki UWB'li telefon arasında santimetre düzeyinde mesafe ve (destekleniyorsa) yön. */
@Composable
fun UwbTool(onBack: () -> Unit, onOpenRecord: (String) -> Unit) {
    val context = LocalContext.current
    val rec = rememberRecording(ToolId.UWB_RANGE)
    ToolScaffold(
        tool = ToolId.UWB_RANGE, onBack = onBack, onOpenRecord = onOpenRecord,
        recording = rec,
        onStartRecording = {
            val note = context.getString(R.string.uwb_note)
            rec.start(
                RecordingSpec(
                    columns = listOf(DataColumn("distance_m", "m", 3), DataColumn("azimuth_deg", "°", 1), DataColumn("elevation_deg", "°", 1)),
                    headerComments = csvHeader(context, ToolId.UWB_RANGE, "UWB (androidx.core.uwb)", note),
                    calibrated = false, calibrationNote = note, sensorName = "UWB",
                ),
            )
        },
        frozen = false, onFreezeToggle = null, onCalibrate = null,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) { NoticeBanner(stringResource(R.string.uwb_old_android), BadgeLevel.ALARM); return@ToolScaffold }
        PermissionGate(Manifest.permission.UWB_RANGING, stringResource(R.string.uwb_rationale)) { UwbContent(rec) }
    }
}

@Composable
private fun UwbContent(rec: com.eirahoutmoss.nextools.ui.components.RecordingController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val link = remember { runCatching { UwbLink(context) }.getOrNull() }
    var available by remember { mutableStateOf<Boolean?>(null) }
    var role by remember { mutableStateOf<UwbRole?>(null) }
    var pairing by remember { mutableStateOf<UwbPairing?>(null) }
    var ownAddress by remember { mutableStateOf<ByteArray?>(null) }
    var peerCode by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var last by remember { mutableStateOf<UwbReading?>(null) }
    val filter = remember { RangeFilter(8) }
    val series = remember { RollingSeries(200) }

    LaunchedEffect(link) { available = link?.available() ?: false }

    fun startRanging(p: UwbPairing, peer: ByteArray) {
        val l = link ?: return
        job?.cancel(); filter.clear(); series.clear()
        job = scope.launch {
            try {
                l.range(p, peer).collect { r ->
                    last = r
                    r.distanceM?.let { d ->
                        filter.add(d.toDouble()); series.add(d)
                        if (rec.isRecording) rec.add(doubleArrayOf(d.toDouble(), r.azimuthDeg?.toDouble() ?: Double.NaN, r.elevationDeg?.toDouble() ?: Double.NaN))
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                error = context.getString(R.string.uwb_failed, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun begin(r: UwbRole) {
        val l = link ?: return
        error = null; role = r; pairing = null; ownAddress = null
        scope.launch {
            try {
                if (r == UwbRole.CONTROLLER) pairing = l.startController() else ownAddress = l.startControlee()
            } catch (e: Exception) { error = context.getString(R.string.uwb_failed, e.message ?: e.javaClass.simpleName); role = null }
        }
    }

    when (available) {
        null -> { Text(stringResource(R.string.waiting_sensor)); return }
        false -> { NoticeBanner(stringResource(R.string.uwb_unavailable), BadgeLevel.ALARM); Text(stringResource(R.string.uwb_tip), style = MaterialTheme.typography.bodySmall); return }
        else -> Unit
    }
    val running = job?.isActive == true
    val d = filter.median
    BigReadout(
        value = d?.let { Fmt.num(it, if (it < 10) 2 else 1) } ?: "—", unit = "m",
        caption = when {
            last?.disconnected == true -> stringResource(R.string.uwb_disconnected)
            running -> stringResource(R.string.uwb_ranging)
            else -> stringResource(R.string.uwb_caption)
        },
        badge = stringResource(R.string.uwb_badge), badgeLevel = BadgeLevel.GOOD,
    )
    if (running) {
        StatRow(
            listOf(
                stringResource(R.string.uwb_azimuth) to (last?.azimuthDeg?.let { Fmt.signed(it.toDouble(), 0) + "°" } ?: "—"),
                stringResource(R.string.uwb_elevation) to (last?.elevationDeg?.let { Fmt.signed(it.toDouble(), 0) + "°" } ?: "—"),
                stringResource(R.string.rtt_spread) to (filter.sd?.let { "±" + Fmt.num(it * 100, 0) + " cm" } ?: "—"),
            ),
        )
        last?.azimuthDeg?.let { az -> SectionCard(stringResource(R.string.uwb_direction)) { DirectionArrow(az) } }
        SectionCard(stringResource(R.string.rtt_trend)) { LiveChart(listOf(series to LocalInstrument.current.accent), fixedMin = 0f, minSpan = 1f) }
        OutlinedButton(onClick = { job?.cancel(); job = null; role = null; pairing = null; ownAddress = null }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.uwb_stop)) }
    } else {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(role == UwbRole.CONTROLLER, { begin(UwbRole.CONTROLLER) }, SegmentedButtonDefaults.itemShape(0, 2)) { Text(stringResource(R.string.uwb_role_controller)) }
            SegmentedButton(role == UwbRole.CONTROLEE, { begin(UwbRole.CONTROLEE) }, SegmentedButtonDefaults.itemShape(1, 2)) { Text(stringResource(R.string.uwb_role_controlee)) }
        }
        when (role) {
            UwbRole.CONTROLLER -> pairing?.let { p ->
                SectionCard(stringResource(R.string.uwb_step1_controller)) {
                    val bmp = remember(p) { QrEncoder.encodeText(p.encode()).toBitmap() }
                    Box(Modifier.fillMaxWidth().padding(horizontal = 40.dp).aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(Color.White)) {
                        Image(bmp.asImageBitmap(), null, contentScale = ContentScale.Fit, filterQuality = FilterQuality.None, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                    }
                }
                SectionCard(stringResource(R.string.uwb_step2_controller)) {
                    OutlinedTextField(peerCode, { peerCode = it.uppercase().filter { c -> c in "0123456789ABCDEF" }.take(16) },
                        label = { Text(stringResource(R.string.uwb_peer_code)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    val peer = runCatching { UwbPairing.unhex(peerCode) }.getOrNull()?.takeIf { it.size >= 2 }
                    Button(onClick = { if (peer != null) startRanging(p, peer) }, enabled = peer != null, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.uwb_start)) }
                }
            }
            UwbRole.CONTROLEE -> ownAddress?.let { own ->
                SectionCard(stringResource(R.string.uwb_step1_controlee)) {
                    Text(own.hex(), style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
                SectionCard(stringResource(R.string.uwb_step2_controlee)) {
                    Button(onClick = {
                        GmsBarcodeScanning.getClient(context).startScan()
                            .addOnSuccessListener { b ->
                                val p = UwbPairing.decode(b.rawValue.orEmpty())
                                if (p == null) error = context.getString(R.string.uwb_bad_qr) else { pairing = p; startRanging(p, p.address) }
                            }
                            .addOnFailureListener { e -> error = context.getString(R.string.qr_scan_failed, e.message ?: e.javaClass.simpleName) }
                    }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.uwb_scan)) }
                }
            }
            null -> Unit
        }
    }
    error?.let { NoticeBanner(it, BadgeLevel.ALARM) }
    Text(stringResource(R.string.uwb_tip), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Karşı cihazın yönü: yukarı = telefonun üst kenarının baktığı yön. */
@Composable
private fun DirectionArrow(azimuthDeg: Float) {
    val inst = LocalInstrument.current
    Canvas(Modifier.fillMaxWidth(0.6f).aspectRatio(1f)) {
        val c = center; val r = min(size.width, size.height) / 2 * 0.9f
        drawCircle(inst.grid, r, c, style = Stroke(1.dp.toPx()))
        rotate(azimuthDeg, c) {
            drawLine(inst.accent, c, Offset(c.x, c.y - r * 0.85f), 5.dp.toPx())
            drawCircle(inst.accent, 8.dp.toPx(), Offset(c.x, c.y - r * 0.85f))
        }
        drawCircle(inst.alarm, 5.dp.toPx(), c)
    }
}
