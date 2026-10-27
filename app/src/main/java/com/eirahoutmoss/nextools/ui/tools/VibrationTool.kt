package com.eirahoutmoss.nextools.ui.tools

import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.measure.Fft
import com.eirahoutmoss.nextools.core.measure.LowPass
import com.eirahoutmoss.nextools.core.measure.RateMeter
import com.eirahoutmoss.nextools.core.measure.Spectra
import com.eirahoutmoss.nextools.core.measure.VibrationMath
import com.eirahoutmoss.nextools.records.Column as DataColumn
import com.eirahoutmoss.nextools.sensors.SensorEventItem
import com.eirahoutmoss.nextools.ui.Fmt
import com.eirahoutmoss.nextools.ui.components.BadgeLevel
import com.eirahoutmoss.nextools.ui.components.BigReadout
import com.eirahoutmoss.nextools.ui.components.LiveChart
import com.eirahoutmoss.nextools.ui.components.RecordingSpec
import com.eirahoutmoss.nextools.ui.components.RollingSeries
import com.eirahoutmoss.nextools.ui.components.SectionCard
import com.eirahoutmoss.nextools.ui.components.SpectrumChart
import com.eirahoutmoss.nextools.ui.components.StatRow
import com.eirahoutmoss.nextools.ui.components.ToolScaffold
import com.eirahoutmoss.nextools.ui.components.rememberRecording
import com.eirahoutmoss.nextools.ui.theme.LocalInstrument
import kotlin.math.sqrt

private const val WINDOW = 512

/** Titreşim Ölçer: ivmeölçerin en yüksek hızıyla RMS, tepe ve FFT ile baskın frekans. */
@Composable
fun VibrationTool(onBack: () -> Unit, onOpenRecord: (String) -> Unit) {
    val context = LocalContext.current
    val sm = remember { context.getSystemService(android.content.Context.SENSOR_SERVICE) as SensorManager }
    val sensor = remember { sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
    val rec = rememberRecording(ToolId.VIBRATION)
    val rate = remember { RateMeter() }
    val gravity = remember { LowPass(1.0) }
    val buf = remember { DoubleArray(WINDOW) }
    val count = remember { intArrayOf(0) }
    val series = remember { RollingSeries(240) }
    var result by remember { mutableStateOf<VibrationMath.Result?>(null) }
    var spectrum by remember { mutableStateOf<FloatArray?>(null) }
    var fs by remember { mutableStateOf(0.0) }
    var frozen by remember { mutableStateOf(false) }
    val analyzeT = remember { Throttle(250_000_000L) }

    CollectSensors(listOfNotNull(sensor), SensorManager.SENSOR_DELAY_FASTEST) { item ->
        if (item !is SensorEventItem.Sample) return@CollectSensors
        val s = item.sample
        rate.add(s.timestampNs)
        val mag = sqrt((s.values[0] * s.values[0] + s.values[1] * s.values[1] + s.values[2] * s.values[2]).toDouble())
        val hp = mag - gravity.update(mag, s.timestampNs) // yerçekimi ve yavaş değişimler çıkarılır
        System.arraycopy(buf, 1, buf, 0, WINDOW - 1); buf[WINDOW - 1] = hp
        count[0]++
        if (count[0] >= WINDOW && analyzeT.ready(s.timestampNs)) {
            val hz = rate.hz ?: return@CollectSensors
            val r = VibrationMath.analyze(buf, hz)
            if (rec.isRecording) rec.add(doubleArrayOf(r.rms, r.peak, r.dominant?.hz ?: Double.NaN))
            if (!frozen) {
                fs = hz; result = r; series.add(r.rms.toFloat())
                spectrum = Fft.amplitudeSpectrum(buf).map { Spectra.toDb(it, 1e-5).toFloat() }.toFloatArray()
            }
        }
    }

    ToolScaffold(
        tool = ToolId.VIBRATION, onBack = onBack, onOpenRecord = onOpenRecord,
        recording = rec,
        onStartRecording = {
            val note = context.getString(R.string.vib_note, Fmt.num(fs, 0))
            rec.start(
                RecordingSpec(
                    columns = listOf(DataColumn("rms_ms2", "m/s²", 4), DataColumn("peak_ms2", "m/s²", 4), DataColumn("dom_hz", "Hz", 1)),
                    headerComments = csvHeader(context, ToolId.VIBRATION, sensor?.name.orEmpty(), note, listOf("window=$WINDOW samples, gravity removed")),
                    calibrated = false, calibrationNote = note, sensorName = sensor?.name.orEmpty(),
                ),
            )
        },
        frozen = frozen, onFreezeToggle = { frozen = !frozen }, onCalibrate = null,
        infoExtra = { SensorInfo(sensor) },
    ) {
        val r = result
        if (r == null) { Text(stringResource(R.string.vib_collecting)); return@ToolScaffold }
        val inst = LocalInstrument.current
        BigReadout(
            value = Fmt.num(r.rms, 3), unit = "m/s²", caption = stringResource(R.string.vib_rms),
            badge = stringResource(R.string.vib_badge, Fmt.num(fs, 0), Fmt.num(fs / 2, 0)), badgeLevel = BadgeLevel.NEUTRAL,
        )
        StatRow(
            listOf(
                stringResource(R.string.vib_peak) to Fmt.num(r.peak, 3),
                stringResource(R.string.vib_dominant) to (r.dominant?.let { Fmt.num(it.hz, 1) + " Hz" } ?: "—"),
                stringResource(R.string.vib_rpm) to (r.dominant?.let { Fmt.num(it.hz * 60, 0) } ?: "—"),
            ),
        )
        SectionCard(stringResource(R.string.vib_trend)) { LiveChart(listOf(series to inst.accent), fixedMin = 0f, minSpan = 0.05f) }
        spectrum?.let { sp ->
            SectionCard(stringResource(R.string.vib_spectrum)) {
                SpectrumChart(sp, binHz = fs / WINDOW, minHz = 1.0, maxHz = fs / 2, minDb = -100f, maxDb = 0f, logX = false, markHz = r.dominant?.hz)
            }
        }
        Text(stringResource(R.string.vib_tip), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
