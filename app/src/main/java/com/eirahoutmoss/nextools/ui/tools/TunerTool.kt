package com.eirahoutmoss.nextools.ui.tools

import android.Manifest
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.measure.Notes
import com.eirahoutmoss.nextools.core.measure.Yin
import com.eirahoutmoss.nextools.records.Column as DataColumn
import com.eirahoutmoss.nextools.sensors.MicReader
import com.eirahoutmoss.nextools.ui.Fmt
import com.eirahoutmoss.nextools.ui.appContainer
import com.eirahoutmoss.nextools.ui.components.BadgeLevel
import com.eirahoutmoss.nextools.ui.components.BigReadout
import com.eirahoutmoss.nextools.ui.components.NoticeBanner
import com.eirahoutmoss.nextools.ui.components.NumberInputDialog
import com.eirahoutmoss.nextools.ui.components.RecordingSpec
import com.eirahoutmoss.nextools.ui.components.StatRow
import com.eirahoutmoss.nextools.ui.components.ToolScaffold
import com.eirahoutmoss.nextools.ui.components.rememberRecording
import com.eirahoutmoss.nextools.ui.theme.LocalInstrument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val WIN = 2400
private val LETTERS = arrayOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")

/** Akort Aleti: YIN ile perde, nota ve sent sapması; A4 ayarlanabilir. */
@Composable
fun TunerTool(onBack: () -> Unit, onOpenRecord: (String) -> Unit) {
    val context = LocalContext.current
    var ok by remember { mutableStateOf(context.hasPermission(Manifest.permission.RECORD_AUDIO)) }
    if (!ok) {
        ToolScaffold(ToolId.TUNER, onBack, onOpenRecord, null, null, false, null, null) {
            PermissionGate(Manifest.permission.RECORD_AUDIO, stringResource(R.string.mic_rationale_generic)) { LaunchedEffect(Unit) { ok = true } }
        }
        return
    }
    val c = appContainer()
    val cal by c.calibrations.state.collectAsStateWithLifecycle()
    val mic = remember { MicReader(context) }
    val pitch = remember { MutableStateFlow<Double?>(null) }
    val hz by pitch.collectAsStateWithLifecycle()
    val micError by mic.error.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val started = rememberIsStarted()
    val rec = rememberRecording(ToolId.TUNER)
    var frozen by remember { mutableStateOf(false) }
    var calibrating by remember { mutableStateOf(false) }
    val names = stringArrayResource(R.array.note_names)
    val recent = remember { ArrayDeque<Double>() }
    val a4 = cal.tunerA4
    val a4Ref = remember { doubleArrayOf(440.0) }
    a4Ref[0] = a4
    val frozenRef = remember { booleanArrayOf(false) }
    frozenRef[0] = frozen

    LaunchedEffect(started) {
        if (started) {
            var yin: Yin? = null
            mic.start(scope, WIN, WIN / 2) { win, fs ->
                val y = yin ?: Yin(fs.toDouble(), WIN).also { yin = it }
                val r = y.detect(win, minHz = 40.0, maxHz = 2000.0)
                synchronized(recent) {
                    if (r == null || r.probability < 0.8) {
                        if (recent.isNotEmpty()) recent.removeFirst()
                    } else {
                        recent.addLast(r.hz); while (recent.size > 5) recent.removeFirst()
                    }
                    val med = recent.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }
                    if (rec.isRecording) rec.add(doubleArrayOf(med ?: Double.NaN, med?.let { Notes.fromHz(it, a4Ref[0]).cents } ?: Double.NaN))
                    if (!frozenRef[0]) pitch.value = med
                }
            }
        } else mic.stop()
    }
    DisposableEffect(Unit) { onDispose { mic.stop() } }

    ToolScaffold(
        tool = ToolId.TUNER, onBack = onBack, onOpenRecord = onOpenRecord,
        recording = rec,
        onStartRecording = {
            val note = context.getString(R.string.tuner_a4_note, Fmt.num(a4, 1))
            rec.start(
                RecordingSpec(
                    columns = listOf(DataColumn("freq_hz", "Hz", 2), DataColumn("cents", "¢", 1)),
                    headerComments = csvHeader(context, ToolId.TUNER, "microphone", note, listOf("YIN pitch, 12-TET", "audio is not stored")),
                    calibrated = cal.isCalibrated(ToolId.TUNER), calibrationNote = note, sensorName = context.getString(R.string.hw_microphone),
                ),
            )
        },
        frozen = frozen, onFreezeToggle = { frozen = !frozen }, onCalibrate = { calibrating = true },
    ) {
        if (micError != 0) { NoticeBanner(stringResource(R.string.sound_mic_failed, micError), BadgeLevel.ALARM); return@ToolScaffold }
        val inst = LocalInstrument.current
        val f = hz
        val n = f?.let { Notes.fromHz(it, a4) }
        val inTune = n != null && abs(n.cents) <= Notes.IN_TUNE_CENTS
        BigReadout(
            value = n?.let { names[it.pitchClass] } ?: "—", unit = n?.let { "${LETTERS[it.pitchClass]}${it.octave}" } ?: "",
            caption = f?.let { stringResource(R.string.tuner_freq_line, Fmt.num(it, 1), Fmt.num(n!!.targetHz, 1)) } ?: stringResource(R.string.tuner_play),
            badge = stringResource(R.string.tuner_badge, Fmt.num(a4, 1)), badgeLevel = if (cal.isCalibrated(ToolId.TUNER)) BadgeLevel.GOOD else BadgeLevel.NEUTRAL,
            valueColor = if (inTune) inst.good else MaterialTheme.colorScheme.onBackground,
        )
        CentsGauge(n?.cents)
        StatRow(
            listOf(
                stringResource(R.string.tuner_cents) to (n?.let { Fmt.signed(it.cents, 0) + "¢" } ?: "—"),
                stringResource(R.string.tuner_state) to stringResource(
                    when {
                        n == null -> R.string.tuner_state_none
                        inTune -> R.string.tuner_state_ok
                        n.cents < 0 -> R.string.tuner_state_low
                        else -> R.string.tuner_state_high
                    },
                ),
            ),
        )
        Text(stringResource(R.string.tuner_makam_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (calibrating) {
        NumberInputDialog(
            title = stringResource(R.string.tuner_a4_title),
            message = stringResource(R.string.tuner_a4_message),
            label = stringResource(R.string.tuner_a4_label),
            initial = a4,
            onConfirm = { v -> if (v < 400 || v > 480) context.getString(R.string.error_range, "400", "480") else { c.calibrations.setTunerA4(v); calibrating = false; null } },
            onDismiss = { calibrating = false },
            extra = { OutlinedButton(onClick = { c.calibrations.reset(ToolId.TUNER); calibrating = false }) { Text(stringResource(R.string.tuner_a4_default)) } },
        )
    }
}

/** −50..+50 sent ibreli gösterge. */
@Composable
private fun CentsGauge(cents: Double?) {
    val inst = LocalInstrument.current
    val measurer = rememberTextMeasurer()
    val label = TextStyle(fontSize = 10.sp, color = inst.scale)
    Canvas(Modifier.fillMaxWidth().aspectRatio(2f)) {
        val pivot = Offset(size.width / 2, size.height * 0.95f)
        val r = min(size.width / 2, size.height) * 0.9f
        for (c in -50..50 step 5) {
            val a = Math.toRadians(-90.0 + c * 1.2)
            val len = if (c % 25 == 0) 18.dp.toPx() else 8.dp.toPx()
            val color = if (abs(c) <= 5) inst.good else inst.scale
            drawLine(color, Offset(pivot.x + (r * cos(a)).toFloat(), pivot.y + (r * sin(a)).toFloat()),
                Offset(pivot.x + ((r - len) * cos(a)).toFloat(), pivot.y + ((r - len) * sin(a)).toFloat()), if (c % 25 == 0) 3f else 2f)
            if (c % 25 == 0) {
                val l = measurer.measure((if (c > 0) "+" else "") + c, label)
                val rr = r - 30.dp.toPx()
                drawText(l, topLeft = Offset(pivot.x + (rr * cos(a)).toFloat() - l.size.width / 2, pivot.y + (rr * sin(a)).toFloat() - l.size.height / 2))
            }
        }
        if (cents != null) {
            val a = Math.toRadians(-90.0 + cents.coerceIn(-50.0, 50.0) * 1.2)
            val color = if (abs(cents) <= Notes.IN_TUNE_CENTS) inst.good else if (abs(cents) <= 15) inst.accent else inst.warn
            drawLine(color, pivot, Offset(pivot.x + (r * 0.95f * cos(a)).toFloat(), pivot.y + (r * 0.95f * sin(a)).toFloat()), 5.dp.toPx(), cap = StrokeCap.Round)
        }
        drawCircle(inst.scale, 7.dp.toPx(), pivot)
    }
}
