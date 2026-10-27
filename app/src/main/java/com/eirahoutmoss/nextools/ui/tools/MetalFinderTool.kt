package com.eirahoutmoss.nextools.ui.tools

import android.hardware.Sensor
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.measure.CompassMath
import com.eirahoutmoss.nextools.core.measure.MetalDetector
import com.eirahoutmoss.nextools.core.measure.Orientation
import com.eirahoutmoss.nextools.records.Column as DataColumn
import com.eirahoutmoss.nextools.sensors.SensorEventItem
import com.eirahoutmoss.nextools.ui.Fmt
import com.eirahoutmoss.nextools.ui.appContainer
import com.eirahoutmoss.nextools.ui.components.ArcGauge
import com.eirahoutmoss.nextools.ui.components.BadgeLevel
import com.eirahoutmoss.nextools.ui.components.BigReadout
import com.eirahoutmoss.nextools.ui.components.NoticeBanner
import com.eirahoutmoss.nextools.ui.components.RecordingSpec
import com.eirahoutmoss.nextools.ui.components.SectionCard
import com.eirahoutmoss.nextools.ui.components.StatRow
import com.eirahoutmoss.nextools.ui.components.ToolScaffold
import com.eirahoutmoss.nextools.ui.components.rememberRecording
import com.eirahoutmoss.nextools.ui.theme.LocalInstrument
import kotlinx.coroutines.delay

/**
 * Metal / Kablo Bulucu. NexYapı "Matkaptan Önce" yöntemi:
 *  - İşlenmemiş manyetometre okunur, telefonun kendi ofseti tarama başında DONDURULUR
 *    (Android'in arka plandaki yeniden kalibrasyonu sıçrama yapmasın).
 *  - Jiroskoplu yön sensörü varsa ölçüm dünya eksenlerine çevrilir: telefon eğilse de metal sanılmaz.
 */
@Composable
fun MetalFinderTool(onBack: () -> Unit, onOpenRecord: (String) -> Unit) {
    val c = appContainer()
    val context = LocalContext.current
    val cal by c.calibrations.state.collectAsStateWithLifecycle()
    val sm = remember { context.getSystemService(android.content.Context.SENSOR_SERVICE) as SensorManager }
    val uncal = remember { sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED) }
    val calSensor = remember { sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) }
    val magSensor = uncal ?: calSensor
    val rotation = remember { sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) }
    val rec = rememberRecording(ToolId.METAL_FINDER)
    val detector = remember { MetalDetector() }
    val recent = remember { ArrayDeque<DoubleArray>() }
    val rot = remember { arrayOfNulls<DoubleArray>(1) }
    val bias = remember { arrayOf(DoubleArray(3), null as DoubleArray?) } // [şimdiki, dondurulmuş]

    var reading by remember { mutableStateOf<MetalDetector.Reading?>(null) }
    var fieldNow by remember { mutableDoubleStateOf(Double.NaN) }
    var message by remember { mutableStateOf<String?>(null) }
    var frozen by remember { mutableStateOf(false) }
    var sound by rememberSaveable { mutableStateOf(true) }
    var vibrate by rememberSaveable { mutableStateOf(false) }
    val ui = remember { Throttle(40_000_000L) }
    val recT = remember { Throttle(50_000_000L) }
    detector.sensitivity = cal.metalSensitivity

    CollectSensors(listOfNotNull(magSensor, rotation), SensorManager.SENSOR_DELAY_GAME) { item ->
        if (item !is SensorEventItem.Sample) return@CollectSensors
        val s = item.sample
        when (s.type) {
            Sensor.TYPE_GAME_ROTATION_VECTOR -> rot[0] = Orientation.matrixFromRotationVector(s.values)
            Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED, Sensor.TYPE_MAGNETIC_FIELD -> {
                val device = if (s.type == Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED) {
                    bias[0] = doubleArrayOf(s.values[3].toDouble(), s.values[4].toDouble(), s.values[5].toDouble())
                    val b = bias[1] ?: bias[0]!!
                    doubleArrayOf(s.values[0] - b[0], s.values[1] - b[1], s.values[2] - b[2])
                } else doubleArrayOf(s.values[0].toDouble(), s.values[1].toDouble(), s.values[2].toDouble())
                val r = rot[0]
                val v = if (r != null) Orientation.toWorld(r, device) else device
                recent.addLast(v); while (recent.size > 80) recent.removeFirst()
                val rd = detector.update(v[0], v[1], v[2])
                val total = CompassMath.magnitude(v[0], v[1], v[2])
                if (rec.isRecording && rd != null && recT.ready(s.timestampNs)) rec.add(doubleArrayOf(rd.anomaly, total))
                if (!frozen && ui.ready(s.timestampNs)) { reading = rd; fieldNow = total }
            }
        }
    }

    // "Sıcak-soğuk" sesli ve titreşimli uyarı
    val level by rememberUpdatedState(reading?.level ?: 0.0)
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 70) }.getOrNull() }
    DisposableEffect(Unit) { onDispose { tone?.release() } }
    val vib = remember { c.hardware.vibrator() }
    LaunchedEffect(sound, vibrate, detector.baseline != null) {
        while (true) {
            val l = level
            if (detector.baseline != null && l > 0.04 && !frozen) {
                if (sound) tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 35)
                if (vibrate && vib?.hasVibrator() == true) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vib.vibrate(VibrationEffect.createOneShot(25, (60 + 190 * l).toInt().coerceIn(1, 255)))
                }
                delay(MetalDetector.beepIntervalMs(l))
            } else delay(150)
        }
    }

    fun startScan() {
        bias[1] = bias[0]?.copyOf()
        val res = detector.calibrate(recent.toList().takeLast(50))
        message = when (res) {
            is MetalDetector.Calibration.Ok -> null
            MetalDetector.Calibration.TooFewSamples -> context.getString(R.string.metal_err_samples)
            MetalDetector.Calibration.Unsteady -> context.getString(R.string.metal_err_unsteady)
            is MetalDetector.Calibration.UnusualField -> context.getString(R.string.metal_err_unusual, res.magnitudeUt.toInt())
        }
        if (res !is MetalDetector.Calibration.Ok) bias[1] = null
    }

    val inst = LocalInstrument.current
    ToolScaffold(
        tool = ToolId.METAL_FINDER, onBack = onBack, onOpenRecord = onOpenRecord,
        recording = rec,
        onStartRecording = if (detector.baseline == null) null else {
            {
                val note = context.getString(R.string.metal_cal_note, Fmt.num(detector.baseline?.magnitude ?: 0.0, 1))
                rec.start(
                    RecordingSpec(
                        columns = listOf(DataColumn("anomaly_ut", "µT", 2), DataColumn("field_ut", "µT", 2)),
                        headerComments = csvHeader(context, ToolId.METAL_FINDER, magSensor?.name.orEmpty(), note),
                        calibrated = true, calibrationNote = note, sensorName = magSensor?.name.orEmpty(),
                    ),
                )
            }
        },
        frozen = frozen, onFreezeToggle = { frozen = !frozen },
        onCalibrate = null,
        infoExtra = { SensorInfo(magSensor) },
    ) {
        NoticeBanner(stringResource(R.string.metal_safety), BadgeLevel.ALARM)
        val r = reading
        if (detector.baseline == null || r == null) {
            SectionCard(null) {
                Text(stringResource(R.string.metal_start_hint), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { startScan() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.metal_start)) }
                message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (!fieldNow.isNaN()) Text(stringResource(R.string.metal_ambient, Fmt.num(fieldNow, 1)), style = MaterialTheme.typography.bodySmall, color = inst.scale)
            }
        } else {
            BigReadout(
                value = Fmt.num(r.anomaly, 1), unit = "µT",
                caption = stringResource(if (r.detected) R.string.metal_detected else R.string.metal_clear),
                badge = stringResource(if (rotation != null) R.string.metal_badge_tilt else R.string.metal_badge_notilt),
                badgeLevel = if (rotation != null) BadgeLevel.GOOD else BadgeLevel.APPROX,
                valueColor = if (r.detected) inst.warn else MaterialTheme.colorScheme.onBackground,
            )
            ArcGauge(
                value = r.level * 100, min = 0.0, max = 100.0, majorStep = 20.0,
                warnFrom = 30.0, alarmFrom = 70.0,
                modifier = Modifier.fillMaxWidth(0.85f).aspectRatio(1.4f),
            )
            StatRow(
                listOf(
                    stringResource(R.string.metal_peak) to Fmt.num(r.peak, 1) + " µT",
                    stringResource(R.string.metal_threshold) to Fmt.num(detector.threshold, 1) + " µT",
                    stringResource(R.string.mag_total) to Fmt.num(fieldNow, 0) + " µT",
                ),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { startScan() }) { Text(stringResource(R.string.metal_rezero)) }
                androidx.compose.material3.OutlinedButton(onClick = { detector.resetPeak() }) { Text(stringResource(R.string.metal_reset_peak)) }
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
        SectionCard(null) {
            Text(stringResource(R.string.metal_sensitivity), style = MaterialTheme.typography.labelLarge)
            Slider(value = cal.metalSensitivity.toFloat(), onValueChange = { c.calibrations.setMetalSensitivity(it.toDouble()) })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.metal_sound), modifier = Modifier.weight(1f)); Switch(sound, { sound = it })
            }
            if (vib?.hasVibrator() == true) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.metal_vibrate), modifier = Modifier.weight(1f)); Switch(vibrate, { vibrate = it })
            }
        }
        Column { Text(stringResource(R.string.metal_how), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
