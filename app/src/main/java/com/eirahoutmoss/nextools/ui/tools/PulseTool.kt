package com.eirahoutmoss.nextools.ui.tools

import android.Manifest
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.measure.Pulse
import com.eirahoutmoss.nextools.records.Column as DataColumn
import com.eirahoutmoss.nextools.ui.Fmt
import com.eirahoutmoss.nextools.ui.components.BadgeLevel
import com.eirahoutmoss.nextools.ui.components.BigReadout
import com.eirahoutmoss.nextools.ui.components.CameraPreview
import com.eirahoutmoss.nextools.ui.components.NoticeBanner
import com.eirahoutmoss.nextools.ui.components.RecordingSpec
import com.eirahoutmoss.nextools.ui.components.RollingSeries
import com.eirahoutmoss.nextools.ui.components.SectionCard
import com.eirahoutmoss.nextools.ui.components.StatRow
import com.eirahoutmoss.nextools.ui.components.ToolScaffold
import com.eirahoutmoss.nextools.ui.components.WaveStrip
import com.eirahoutmoss.nextools.ui.components.centerRgb
import com.eirahoutmoss.nextools.ui.components.rememberRecording
import com.eirahoutmoss.nextools.ui.theme.LocalInstrument
import kotlinx.coroutines.flow.MutableStateFlow

private class PulseState(val finger: Boolean, val bpm: Double?, val quality: Double, val seconds: Double)

/** Nabız: parmak arka kamera + flaşın üstünde; kırmızı kanal dalgalanmasından vuruş/dk. Tıbbi cihaz değildir. */
@Composable
fun PulseTool(onBack: () -> Unit, onOpenRecord: (String) -> Unit) {
    val context = LocalContext.current
    val rec = rememberRecording(ToolId.PULSE)
    val state = remember { MutableStateFlow(PulseState(false, null, 0.0, 0.0)) }
    val st by state.collectAsStateWithLifecycle()
    val series = remember { RollingSeries(150) }
    val t = remember { ArrayDeque<Double>() }
    val v = remember { ArrayDeque<Double>() }
    val lastEst = remember { longArrayOf(0L) }
    val bpmRef = remember { doubleArrayOf(Double.NaN) }

    ToolScaffold(
        tool = ToolId.PULSE, onBack = onBack, onOpenRecord = onOpenRecord,
        recording = rec,
        onStartRecording = {
            val note = context.getString(R.string.pulse_disclaimer)
            rec.start(
                RecordingSpec(
                    columns = listOf(DataColumn("bpm", "1/min", 0), DataColumn("red", "", 2)),
                    headerComments = csvHeader(context, ToolId.PULSE, "camera (back) + torch", note, listOf("not a medical device")),
                    calibrated = false, calibrationNote = note, sensorName = context.getString(R.string.hw_camera),
                ),
            )
        },
        frozen = false, onFreezeToggle = null, onCalibrate = null,
    ) {
        PermissionGate(Manifest.permission.CAMERA, stringResource(R.string.camera_rationale)) {
            NoticeBanner(stringResource(R.string.pulse_disclaimer), BadgeLevel.ALARM)
            Box(Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(16.dp))) {
                CameraPreview(
                    Modifier.fillMaxWidth().height(96.dp),
                    onCamera = { it.cameraControl.enableTorch(true) },
                    onFrame = { img ->
                        val c = centerRgb(img, box = 64)
                        val now = img.imageInfo.timestamp / 1e9
                        val finger = Pulse.fingerPresent(c[0], c[1], c[2])
                        if (!finger) {
                            t.clear(); v.clear(); bpmRef[0] = Double.NaN
                            state.value = PulseState(false, null, 0.0, 0.0)
                            return@CameraPreview
                        }
                        // Kırmızı tek başına 255'e doyabilir; yeşil kanal da kan hacmiyle değişir, toplam daha kararlı.
                        val sig = (c[0] + c[1]).toDouble()
                        t.addLast(now); v.addLast(sig)
                        while (t.isNotEmpty() && now - t.first() > 15) { t.removeFirst(); v.removeFirst() }
                        series.add(-sig.toFloat())
                        val span = now - t.first()
                        val nowNs = System.nanoTime()
                        if (nowNs - lastEst[0] > 1_000_000_000L) {
                            lastEst[0] = nowNs
                            val r = Pulse.estimate(t.toDoubleArray(), v.toDoubleArray())
                            bpmRef[0] = if (r != null && r.quality > 0.15) r.bpm else Double.NaN
                            state.value = PulseState(true, bpmRef[0].takeUnless { it.isNaN() }, r?.quality ?: 0.0, span)
                        }
                        if (rec.isRecording) rec.add(doubleArrayOf(bpmRef[0], c[0].toDouble()))
                    },
                )
            }
            BigReadout(
                value = st.bpm?.let { Fmt.num(it, 0) } ?: "—", unit = stringResource(R.string.pulse_unit),
                caption = when {
                    !st.finger -> stringResource(R.string.pulse_place_finger)
                    st.bpm == null -> stringResource(R.string.pulse_hold, (6 - st.seconds).coerceAtLeast(0.0).toInt())
                    else -> stringResource(R.string.pulse_caption)
                },
                badge = stringResource(R.string.pulse_badge), badgeLevel = BadgeLevel.APPROX,
            )
            StatRow(
                listOf(
                    stringResource(R.string.pulse_signal) to (if (st.finger) Fmt.num(st.quality * 100, 0) + " %" else "—"),
                    stringResource(R.string.pulse_window) to Fmt.num(st.seconds, 0) + " s",
                ),
            )
            SectionCard(stringResource(R.string.pulse_wave)) { WaveStrip(series, LocalInstrument.current.alarm, height = 80.dp) }
            Text(stringResource(R.string.pulse_tip), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
