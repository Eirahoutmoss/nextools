package com.eirahoutmoss.nextools.ui.tools

import android.Manifest
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.measure.Flicker
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
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.Executors

/** Kısa pozlama (1/4000 s), sabit ISO ve şebeke titreşimi bastırma kapalı: bantlar görünür kalsın. */
@OptIn(ExperimentalCamera2Interop::class)
private fun buildAnalysis(onSkew: (Long) -> Unit): ImageAnalysis {
    val b = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
    Camera2Interop.Extender(b)
        .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
        .setCaptureRequestOption(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_OFF)
        .setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, 250_000L)
        .setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, 800)
        .setSessionCaptureCallback(object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                result.get(CaptureResult.SENSOR_ROLLING_SHUTTER_SKEW)?.let(onSkew)
            }
        })
    return b.build()
}

/** Işık Titreşimi: rolling shutter bantlarından titreşim yüzdesi ve frekansı. */
@Composable
fun FlickerTool(onBack: () -> Unit, onOpenRecord: (String) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val rec = rememberRecording(ToolId.FLICKER)
    val results = remember { MutableStateFlow<Flicker.Result?>(null) }
    val result by results.collectAsStateWithLifecycle()
    val series = remember { RollingSeries(150) }
    var frozen by remember { mutableStateOf(false) }
    val frozenRef = remember { booleanArrayOf(false) }
    frozenRef[0] = frozen
    val skew = remember { longArrayOf(0L) }
    val lastRec = remember { longArrayOf(0L) }
    val executor = remember { Executors.newSingleThreadExecutor() }

    ToolScaffold(
        tool = ToolId.FLICKER, onBack = onBack, onOpenRecord = onOpenRecord,
        recording = rec,
        onStartRecording = {
            val note = context.getString(R.string.flicker_note)
            rec.start(
                RecordingSpec(
                    columns = listOf(DataColumn("flicker_pct", "%", 1), DataColumn("flicker_hz", "Hz", 0)),
                    headerComments = csvHeader(context, ToolId.FLICKER, "camera rolling shutter", note),
                    calibrated = false, calibrationNote = note, sensorName = context.getString(R.string.hw_camera),
                ),
            )
        },
        frozen = frozen, onFreezeToggle = { frozen = !frozen }, onCalibrate = null,
    ) {
        PermissionGate(Manifest.permission.CAMERA, stringResource(R.string.camera_rationale)) {
            val previewView = remember { PreviewView(context) }
            DisposableEffect(owner) {
                val future = ProcessCameraProvider.getInstance(context)
                var provider: ProcessCameraProvider? = null
                future.addListener({
                    val p = runCatching { future.get() }.getOrNull() ?: return@addListener
                    provider = p
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    val analysis = buildAnalysis { skew[0] = it }
                    analysis.setAnalyzer(executor) { img ->
                        try {
                            val y = img.planes[0]
                            val rows = DoubleArray(img.height) { r ->
                                var s = 0L; var n = 0
                                var c = 0
                                while (c < img.width) { s += y.buffer.get(r * y.rowStride + c * y.pixelStride).toInt() and 0xFF; n++; c += 8 }
                                s.toDouble() / n
                            }
                            val rowTime = if (skew[0] > 0) skew[0] / 1e9 / img.height else null
                            val r = Flicker.analyze(rows, rowTime)
                            val now = System.nanoTime()
                            if (rec.isRecording && now - lastRec[0] > 250_000_000L) { lastRec[0] = now; rec.add(doubleArrayOf(r.percent, r.frequencyHz ?: Double.NaN)) }
                            if (!frozenRef[0]) { results.value = r; series.add(r.percent.toFloat()) }
                        } finally { img.close() }
                    }
                    runCatching { p.unbindAll(); p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis) }
                }, ContextCompat.getMainExecutor(context))
                onDispose { provider?.unbindAll() }
            }
            DisposableEffect(Unit) { onDispose { executor.shutdown() } }
            AndroidView({ previewView }, Modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(16.dp)))
            val r = result
            val risk = r?.let { Flicker.risk(it.percent) }
            BigReadout(
                value = r?.let { Fmt.num(it.percent, 1) } ?: "—", unit = "%",
                caption = risk?.let { stringResource(when (it) { Flicker.Risk.LOW -> R.string.flicker_low; Flicker.Risk.MODERATE -> R.string.flicker_moderate; Flicker.Risk.HIGH -> R.string.flicker_high }) } ?: stringResource(R.string.waiting_sensor),
                badge = stringResource(R.string.flicker_badge), badgeLevel = BadgeLevel.APPROX,
                valueColor = when (risk) { Flicker.Risk.HIGH -> LocalInstrument.current.alarm; Flicker.Risk.MODERATE -> LocalInstrument.current.warn; else -> MaterialTheme.colorScheme.onBackground },
            )
            StatRow(listOf(stringResource(R.string.flicker_freq) to (r?.frequencyHz?.let { Fmt.num(it, 0) + " Hz" } ?: "—")))
            SectionCard(null) { LiveChart(listOf(series to LocalInstrument.current.accent), fixedMin = 0f, minSpan = 10f) }
            if (skew[0] == 0L) NoticeBanner(stringResource(R.string.flicker_no_skew), BadgeLevel.NEUTRAL)
            Text(stringResource(R.string.flicker_tip), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
