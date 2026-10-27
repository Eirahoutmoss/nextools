package com.eirahoutmoss.nextools.ui.tools

import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CaptureRequest
import android.os.BatteryManager
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.measure.CosmicDetector
import com.eirahoutmoss.nextools.records.Column as DataColumn
import com.eirahoutmoss.nextools.ui.Fmt
import com.eirahoutmoss.nextools.ui.components.BadgeLevel
import com.eirahoutmoss.nextools.ui.components.BigReadout
import com.eirahoutmoss.nextools.ui.components.KeepScreenOn
import com.eirahoutmoss.nextools.ui.components.KeyValue
import com.eirahoutmoss.nextools.ui.components.LiveChart
import com.eirahoutmoss.nextools.ui.components.NoticeBanner
import com.eirahoutmoss.nextools.ui.components.RecordingSpec
import com.eirahoutmoss.nextools.ui.components.RollingSeries
import com.eirahoutmoss.nextools.ui.components.SectionCard
import com.eirahoutmoss.nextools.ui.components.StatRow
import com.eirahoutmoss.nextools.ui.components.ToolScaffold
import com.eirahoutmoss.nextools.ui.components.rememberRecording
import com.eirahoutmoss.nextools.ui.theme.LocalInstrument
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.Executors

private data class CosmicState(
    val frames: Int = 0,
    val learning: Boolean = true,
    val total: Int = 0,
    val masked: Int = 0,
    val meanLuma: Int = 0,
    val countingSinceMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val lastPeak: Int = 0,
)

/** Uzun pozlama (0,1 s), yüksek ISO, otomatik pozlama kapalı: karanlık karede tek tük parlak izler görünsün. */
@OptIn(ExperimentalCamera2Interop::class)
private fun cosmicAnalysis(): ImageAnalysis {
    val b = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY).build())
    Camera2Interop.Extender(b)
        .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
        .setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, 100_000_000L)
        .setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, 3200)
        .setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF)
    return b.build()
}

/** Kozmik Işın Sayacı: objektifi tamamen kapalı kamerada parlak iz sayımı. */
@Composable
fun CosmicRayTool(onBack: () -> Unit, onOpenRecord: (String) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val rec = rememberRecording(ToolId.COSMIC_RAY)
    val state = remember { MutableStateFlow(CosmicState()) }
    val st by state.collectAsStateWithLifecycle()
    var running by remember { mutableStateOf(false) }
    var threshold by remember { mutableIntStateOf(40) }
    var tempC by remember { mutableStateOf<Double?>(null) }
    val perMinute = remember { RollingSeries(120) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }

    ToolScaffold(
        tool = ToolId.COSMIC_RAY, onBack = onBack, onOpenRecord = onOpenRecord,
        recording = rec,
        onStartRecording = {
            val note = context.getString(R.string.cosmic_note, threshold)
            rec.start(
                RecordingSpec(
                    columns = listOf(DataColumn("hits_per_min", "1/min", 0), DataColumn("total", "", 0), DataColumn("temp_c", "°C", 1)),
                    headerComments = csvHeader(context, ToolId.COSMIC_RAY, "camera (back), lens covered", note, listOf("one row per minute")),
                    calibrated = false, calibrationNote = note, sensorName = context.getString(R.string.hw_camera),
                ),
            )
        },
        frozen = false, onFreezeToggle = null, onCalibrate = null,
    ) {
        KeepScreenOn(running)
        PermissionGate(Manifest.permission.CAMERA, stringResource(R.string.camera_rationale)) {
            if (running) {
                DisposableEffect(owner, threshold) {
                    val future = ProcessCameraProvider.getInstance(context)
                    var provider: ProcessCameraProvider? = null
                    var det: CosmicDetector? = null
                    var total = 0
                    var started = 0L
                    state.value = CosmicState()
                    future.addListener({
                        val p = runCatching { future.get() }.getOrNull() ?: return@addListener
                        provider = p
                        val analysis = cosmicAnalysis()
                        analysis.setAnalyzer(executor) { img ->
                            try {
                                val plane = img.planes[0]
                                val buf = plane.buffer
                                val bytes = ByteArray(buf.remaining()); buf.get(bytes)
                                val d = det ?: CosmicDetector(img.width, img.height, threshold = threshold).also { det = it }
                                val hits = d.process(bytes, plane.rowStride, plane.pixelStride)
                                var s = 0L; var n = 0
                                var i = 0; while (i < bytes.size) { s += bytes[i].toInt() and 0xFF; n++; i += 997 }
                                if (!d.learning && started == 0L) started = System.currentTimeMillis()
                                total += hits.size
                                state.value = CosmicState(
                                    d.frames, d.learning, total, d.maskedPixels, if (n > 0) (s / n).toInt() else 0, started,
                                    img.width, img.height, hits.maxOfOrNull { it.peak } ?: state.value.lastPeak,
                                )
                            } finally { img.close() }
                        }
                        runCatching { p.unbindAll(); p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, analysis) }
                    }, ContextCompat.getMainExecutor(context))
                    onDispose { provider?.unbindAll() }
                }
                // Dakikalık sayım ve pil sıcaklığı
                LaunchedEffect(Unit) {
                    var lastTotal = 0
                    while (true) {
                        delay(60_000)
                        val now = state.value
                        val perMin = now.total - lastTotal; lastTotal = now.total
                        val t = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                            ?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)?.takeIf { it != Int.MIN_VALUE }?.div(10.0)
                        tempC = t
                        if (!now.learning) {
                            perMinute.add(perMin.toFloat())
                            if (rec.isRecording) rec.add(doubleArrayOf(perMin.toDouble(), now.total.toDouble(), t ?: Double.NaN))
                        }
                    }
                }
            }
            val minutes = if (st.countingSinceMs > 0) (System.currentTimeMillis() - st.countingSinceMs) / 60_000.0 else 0.0
            val rate = if (minutes >= 1) st.total / minutes else null
            BigReadout(
                value = st.total.toString(), unit = stringResource(R.string.cosmic_unit),
                caption = when {
                    !running -> stringResource(R.string.cosmic_caption)
                    st.learning -> stringResource(R.string.cosmic_learning, st.frames, 30)
                    else -> stringResource(R.string.cosmic_counting)
                },
                badge = stringResource(R.string.cosmic_badge), badgeLevel = BadgeLevel.APPROX,
            )
            StatRow(
                listOf(
                    stringResource(R.string.cosmic_rate) to (rate?.let { Fmt.num(it, 2) + " " + stringResource(R.string.cosmic_per_min_unit) } ?: "—"),
                    stringResource(R.string.cosmic_time) to Fmt.duration((minutes * 60_000).toLong()),
                    stringResource(R.string.cosmic_temp) to (tempC?.let { Fmt.num(it, 1) + " °C" } ?: "—"),
                ),
            )
            if (running && st.meanLuma > 25) NoticeBanner(stringResource(R.string.cosmic_light_leak), BadgeLevel.ALARM)
            if ((tempC ?: 0.0) > 40) NoticeBanner(stringResource(R.string.cosmic_hot))
            if (!running) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(25, 40, 60).forEach { t -> FilterChip(threshold == t, { threshold = t }, label = { Text(stringResource(R.string.cosmic_threshold, t)) }) }
                }
                Button(onClick = { perMinute.clear(); running = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.cosmic_start)) }
            } else {
                OutlinedButton(onClick = { running = false }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.cosmic_stop)) }
            }
            if (st.frames > 0) {
                SectionCard(stringResource(R.string.cosmic_per_min)) { LiveChart(listOf(perMinute to LocalInstrument.current.accent), fixedMin = 0f, minSpan = 3f) }
                SectionCard(stringResource(R.string.cosmic_details)) {
                    KeyValue(stringResource(R.string.cosmic_frames), st.frames.toString())
                    KeyValue(stringResource(R.string.cosmic_resolution), "${st.width} × ${st.height}")
                    KeyValue(stringResource(R.string.cosmic_masked), st.masked.toString())
                    KeyValue(stringResource(R.string.cosmic_mean), st.meanLuma.toString())
                }
            }
            Text(stringResource(R.string.cosmic_tip), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
