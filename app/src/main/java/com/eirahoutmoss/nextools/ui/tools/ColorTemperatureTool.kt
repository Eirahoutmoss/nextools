package com.eirahoutmoss.nextools.ui.tools

import android.Manifest
import android.hardware.camera2.CaptureRequest
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.ImageAnalysis
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.measure.Cct
import com.eirahoutmoss.nextools.records.Column as DataColumn
import com.eirahoutmoss.nextools.ui.Fmt
import com.eirahoutmoss.nextools.ui.components.BadgeLevel
import com.eirahoutmoss.nextools.ui.components.BigReadout
import com.eirahoutmoss.nextools.ui.components.CameraPreview
import com.eirahoutmoss.nextools.ui.components.KeyValue
import com.eirahoutmoss.nextools.ui.components.NoticeBanner
import com.eirahoutmoss.nextools.ui.components.RecordingSpec
import com.eirahoutmoss.nextools.ui.components.SectionCard
import com.eirahoutmoss.nextools.ui.components.StatRow
import com.eirahoutmoss.nextools.ui.components.ToolScaffold
import com.eirahoutmoss.nextools.ui.components.centerRgb
import com.eirahoutmoss.nextools.ui.components.rememberRecording
import kotlinx.coroutines.flow.MutableStateFlow

/** Beyaz dengesi "gün ışığı"na sabitlenir: beyaz kâğıdın rengi doğrudan ışığın rengini gösterir. */
@OptIn(ExperimentalCamera2Interop::class)
private fun daylightWb(b: ImageAnalysis.Builder) {
    Camera2Interop.Extender(b).setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT)
}

private val TYPICAL = listOf(
    1900 to R.string.cct_candle, 2700 to R.string.cct_incandescent, 3000 to R.string.cct_warm_led,
    4000 to R.string.cct_neutral, 5500 to R.string.cct_daylight, 6500 to R.string.cct_cool, 8000 to R.string.cct_sky,
)

/** Renk Sıcaklığı: beyaz yüzeyden yansıyan ışığın tahmini Kelvin değeri. */
@Composable
fun ColorTemperatureTool(onBack: () -> Unit, onOpenRecord: (String) -> Unit) {
    val context = LocalContext.current
    val rec = rememberRecording(ToolId.COLOR_TEMPERATURE)
    val flow = remember { MutableStateFlow<Cct.Result?>(null) }
    val rgbFlow = remember { MutableStateFlow<IntArray?>(null) }
    val live by flow.collectAsStateWithLifecycle()
    val rgb by rgbFlow.collectAsStateWithLifecycle()
    var frozen by remember { mutableStateOf(false) }
    var held by remember { mutableStateOf<Cct.Result?>(null) }
    val lastRec = remember { longArrayOf(0L) }

    ToolScaffold(
        tool = ToolId.COLOR_TEMPERATURE, onBack = onBack, onOpenRecord = onOpenRecord,
        recording = rec,
        onStartRecording = {
            val note = context.getString(R.string.cct_note)
            rec.start(
                RecordingSpec(
                    columns = listOf(DataColumn("cct_k", "K", 0), DataColumn("cie_x", "", 4), DataColumn("cie_y", "", 4)),
                    headerComments = csvHeader(context, ToolId.COLOR_TEMPERATURE, "camera (back), WB daylight", note),
                    calibrated = false, calibrationNote = note, sensorName = context.getString(R.string.hw_camera),
                ),
            )
        },
        frozen = frozen, onFreezeToggle = { frozen = !frozen; held = if (frozen) live else null }, onCalibrate = null,
    ) {
        PermissionGate(Manifest.permission.CAMERA, stringResource(R.string.camera_rationale)) {
            Box(Modifier.fillMaxWidth().aspectRatio(1.3f).clip(RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                CameraPreview(Modifier.fillMaxWidth().aspectRatio(1.3f), configure = ::daylightWb, onFrame = { img ->
                    val c = centerRgb(img, box = 48)
                    rgbFlow.value = c
                    val r = Cct.fromSrgb(c[0].toDouble(), c[1].toDouble(), c[2].toDouble())
                    flow.value = r
                    val now = System.nanoTime()
                    if (r != null && rec.isRecording && now - lastRec[0] > 250_000_000L) { lastRec[0] = now; rec.add(doubleArrayOf(r.kelvin, r.x, r.y)) }
                })
                Box(Modifier.size(40.dp).border(2.dp, Color.White, CircleShape))
            }
            val r = if (frozen) held else live
            BigReadout(
                value = r?.let { Fmt.num((it.kelvin / 50).toInt() * 50.0, 0) } ?: "—", unit = "K",
                caption = r?.let { k -> stringResource(TYPICAL.minBy { kotlin.math.abs(it.first - k.kelvin) }.second) } ?: stringResource(R.string.cct_caption),
                badge = stringResource(R.string.cct_badge), badgeLevel = BadgeLevel.APPROX,
            )
            r?.let { StatRow(listOf("x" to Fmt.num(it.x, 4), "y" to Fmt.num(it.y, 4))) }
            rgb?.let { c -> if (maxOf(c[0], c[1], c[2]) >= 250) NoticeBanner(stringResource(R.string.cct_saturated)) else if (maxOf(c[0], c[1], c[2]) < 40) NoticeBanner(stringResource(R.string.spec_dark), BadgeLevel.NEUTRAL) }
            SectionCard(stringResource(R.string.cct_typical)) { TYPICAL.forEach { (k, s) -> KeyValue(stringResource(s), "≈ $k K") } }
            Text(stringResource(R.string.cct_tip), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
