package com.eirahoutmoss.nextools.ui.tools

import android.Manifest
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.measure.Barometric
import com.eirahoutmoss.nextools.core.measure.LowPass
import com.eirahoutmoss.nextools.core.units.PressureUnit
import com.eirahoutmoss.nextools.core.units.Units
import com.eirahoutmoss.nextools.records.Column as DataColumn
import com.eirahoutmoss.nextools.sensors.LocationHelper
import com.eirahoutmoss.nextools.sensors.SensorEventItem
import com.eirahoutmoss.nextools.ui.Fmt
import com.eirahoutmoss.nextools.ui.appContainer
import com.eirahoutmoss.nextools.ui.components.BadgeLevel
import com.eirahoutmoss.nextools.ui.components.BigReadout
import com.eirahoutmoss.nextools.ui.components.LiveChart
import com.eirahoutmoss.nextools.ui.components.NoticeBanner
import com.eirahoutmoss.nextools.ui.components.NumberInputDialog
import com.eirahoutmoss.nextools.ui.components.RecordChart
import com.eirahoutmoss.nextools.ui.components.RecordingSpec
import com.eirahoutmoss.nextools.ui.components.RollingSeries
import com.eirahoutmoss.nextools.ui.components.SectionCard
import com.eirahoutmoss.nextools.ui.components.StatRow
import com.eirahoutmoss.nextools.ui.components.ToolScaffold
import com.eirahoutmoss.nextools.ui.components.rememberRecording
import com.eirahoutmoss.nextools.ui.theme.LocalInstrument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun PressureUnit.decimals(): Int = when (this) {
    PressureUnit.HPA -> 1
    PressureUnit.MMHG -> 1
    PressureUnit.INHG -> 2
}

/** Barometre: hPa, deniz seviyesine indirgenmiş basınç, 24 saatlik eğilim (isteğe bağlı günlükle). */
@Composable
fun BarometerTool(onBack: () -> Unit, onOpenRecord: (String) -> Unit) {
    val c = appContainer()
    val context = LocalContext.current
    val settings by c.settings.state.collectAsStateWithLifecycle()
    val cal by c.calibrations.state.collectAsStateWithLifecycle()
    val logOn by c.pressureLog.enabled.collectAsStateWithLifecycle()
    val logVersion by c.pressureLog.version.collectAsStateWithLifecycle()
    val sm = remember { context.getSystemService(android.content.Context.SENSOR_SERVICE) as SensorManager }
    val sensor = remember { sm.getDefaultSensor(Sensor.TYPE_PRESSURE) }
    val rec = rememberRecording(ToolId.BAROMETER)
    val filter = remember { LowPass(1.0) }
    var hpa by remember { mutableDoubleStateOf(Double.NaN) }
    var frozen by remember { mutableStateOf(false) }
    var calibrating by remember { mutableStateOf(false) }
    val series = remember { RollingSeries(240) }
    val ui = remember { Throttle(250_000_000L) }
    val chartT = remember { Throttle(500_000_000L) }
    val recT = remember { Throttle(500_000_000L) }
    val scope = rememberCoroutineScope()
    val unit = settings.pressureUnit

    CollectSensors(listOfNotNull(sensor), SensorManager.SENSOR_DELAY_UI) { item ->
        if (item !is SensorEventItem.Sample) return@CollectSensors
        val s = item.sample
        val p = filter.update(s.values[0].toDouble(), s.timestampNs)
        val slp = cal.barometerAltitude?.let { Barometric.seaLevelPressure(p, it) } ?: Double.NaN
        if (rec.isRecording && recT.ready(s.timestampNs)) rec.add(doubleArrayOf(p, slp))
        if (!frozen && ui.ready(s.timestampNs)) hpa = p
        if (!frozen && chartT.ready(s.timestampNs)) series.add(unit.fromHpa(p).toFloat())
    }

    val log by produceState(emptyList<Pair<Long, Double>>(), logVersion, logOn) {
        value = withContext(Dispatchers.IO) { c.pressureLog.read() }
    }

    ToolScaffold(
        tool = ToolId.BAROMETER, onBack = onBack, onOpenRecord = onOpenRecord,
        recording = rec,
        onStartRecording = {
            val note = cal.barometerAltitude?.let { context.getString(R.string.baro_cal_note, Fmt.num(it, 0)) } ?: context.getString(R.string.baro_no_alt)
            rec.start(
                RecordingSpec(
                    columns = listOf(DataColumn("pressure_hpa", "hPa", 2), DataColumn("slp_hpa", "hPa", 2)),
                    headerComments = csvHeader(context, ToolId.BAROMETER, sensor?.name.orEmpty(), note),
                    calibrated = cal.barometerAltitude != null, calibrationNote = note, sensorName = sensor?.name.orEmpty(),
                ),
            )
        },
        frozen = frozen, onFreezeToggle = { frozen = !frozen },
        onCalibrate = { calibrating = true },
        infoExtra = { SensorInfo(sensor) },
    ) {
        if (hpa.isNaN()) { Text(stringResource(R.string.waiting_sensor)); return@ToolScaffold }
        val slp = cal.barometerAltitude?.let { Barometric.seaLevelPressure(hpa, it) }
        BigReadout(
            value = Fmt.num(unit.fromHpa(hpa), unit.decimals()), unit = unit.symbol,
            caption = stringResource(R.string.baro_station),
            badge = stringResource(R.string.baro_badge),
            badgeLevel = BadgeLevel.GOOD,
        )
        StatRow(
            listOf(
                stringResource(R.string.baro_slp) to (slp?.let { Fmt.num(unit.fromHpa(it), unit.decimals()) } ?: "—"),
                stringResource(R.string.baro_std_alt) to Fmt.num(Units.length(Barometric.altitude(hpa), settings.units), 0) + " " + Units.lengthSymbol(settings.units),
            ),
        )
        if (slp == null) NoticeBanner(stringResource(R.string.baro_slp_hint), BadgeLevel.NEUTRAL)
        SectionCard(stringResource(R.string.baro_live)) {
            LiveChart(listOf(series to LocalInstrument.current.accent), minSpan = if (unit == PressureUnit.INHG) 0.01f else 0.3f)
        }
        SectionCard(stringResource(R.string.baro_trend_title)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.baro_log_switch), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(logOn, { c.pressureLog.setEnabled(it) })
            }
            Text(stringResource(R.string.baro_log_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val now = System.currentTimeMillis()
            val day = log.filter { it.first >= now - 24 * Barometric.HOUR_MS }
            if (day.size >= 2) {
                val xs = DoubleArray(day.size) { (day[it].first - now) / 3_600_000.0 } // saat (negatif = geçmiş)
                val ys = DoubleArray(day.size) { unit.fromHpa(day[it].second) }
                RecordChart(listOf(Triple(xs, ys, LocalInstrument.current.accent)), xSuffix = " " + stringResource(R.string.unit_hour_short))
                val d = Barometric.deltaPer3h(day, now)
                if (d != null) {
                    val trendText = when (Barometric.trend(d)) {
                        Barometric.Trend.RISING_FAST -> stringResource(R.string.trend_rising_fast)
                        Barometric.Trend.RISING -> stringResource(R.string.trend_rising)
                        Barometric.Trend.STEADY -> stringResource(R.string.trend_steady)
                        Barometric.Trend.FALLING -> stringResource(R.string.trend_falling)
                        Barometric.Trend.FALLING_FAST -> stringResource(R.string.trend_falling_fast)
                    }
                    Text(stringResource(R.string.baro_trend_line, Fmt.signed(d, 1), trendText), style = MaterialTheme.typography.bodyMedium)
                }
                Text(stringResource(R.string.baro_trend_disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(stringResource(R.string.baro_trend_empty), style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (calibrating) {
        var fetching by remember { mutableStateOf(false) }
        var gpsError by remember { mutableStateOf<String?>(null) }
        val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
        NumberInputDialog(
            title = stringResource(R.string.baro_alt_title),
            message = stringResource(R.string.baro_alt_message),
            label = stringResource(R.string.baro_alt_label, Units.lengthSymbol(settings.units)),
            initial = cal.barometerAltitude?.let { Units.length(it, settings.units) },
            onConfirm = { v ->
                val m = Units.lengthToMeters(v, settings.units)
                if (m < -500 || m > 9000) context.getString(R.string.error_range, "-500", "9000")
                else { c.calibrations.setBarometerAltitude(m); calibrating = false; null }
            },
            onDismiss = { calibrating = false },
            extra = {
                OutlinedButton(enabled = !fetching, onClick = {
                    if (!LocationHelper.hasFine(context)) {
                        perm.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        return@OutlinedButton
                    }
                    fetching = true
                    scope.launch {
                        val loc = LocationHelper.current(context, timeoutMs = 20_000, gpsPreferred = true)
                        fetching = false
                        if (loc != null && loc.hasAltitude()) {
                            c.calibrations.setBarometerAltitude(loc.altitude)
                            calibrating = false
                        } else gpsError = context.getString(R.string.gps_alt_failed)
                    }
                }) { Text(stringResource(if (fetching) R.string.gps_waiting else R.string.gps_alt_use)) }
                gpsError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (cal.barometerAltitude != null) {
                    OutlinedButton(onClick = { c.calibrations.reset(ToolId.BAROMETER); calibrating = false }) { Text(stringResource(R.string.cal_reset_title)) }
                }
            },
        )
    }
}
