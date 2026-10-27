package com.eirahoutmoss.nextools.ui.tools

import android.Manifest
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.measure.Barometric
import com.eirahoutmoss.nextools.core.measure.LowPass
import com.eirahoutmoss.nextools.core.measure.RunningStats
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
import com.eirahoutmoss.nextools.ui.components.RecordingSpec
import com.eirahoutmoss.nextools.ui.components.RollingSeries
import com.eirahoutmoss.nextools.ui.components.SectionCard
import com.eirahoutmoss.nextools.ui.components.StatRow
import com.eirahoutmoss.nextools.ui.components.ToolScaffold
import com.eirahoutmoss.nextools.ui.components.rememberRecording
import com.eirahoutmoss.nextools.ui.theme.LocalInstrument
import kotlinx.coroutines.launch

/** Altimetre: barometreyle bağıl yükseklik; sıfırlama (burası 0), bilinen yükseklik ya da GPS ile. */
@Composable
fun AltimeterTool(onBack: () -> Unit, onOpenRecord: (String) -> Unit) {
    val c = appContainer()
    val context = LocalContext.current
    val settings by c.settings.state.collectAsStateWithLifecycle()
    val cal by c.calibrations.state.collectAsStateWithLifecycle()
    val sm = remember { context.getSystemService(android.content.Context.SENSOR_SERVICE) as SensorManager }
    val sensor = remember { sm.getDefaultSensor(Sensor.TYPE_PRESSURE) }
    val rec = rememberRecording(ToolId.ALTIMETER)
    val filter = remember { LowPass(0.8) }
    var hpa by remember { mutableDoubleStateOf(Double.NaN) }
    var frozen by remember { mutableStateOf(false) }
    var calibrating by remember { mutableStateOf(false) }
    val stats = remember { RunningStats() }
    val series = remember { RollingSeries(240) }
    val ui = remember { Throttle(250_000_000L) }
    val chartT = remember { Throttle(500_000_000L) }
    val recT = remember { Throttle(500_000_000L) }
    val units = settings.units

    fun altitudeOf(p: Double): Double = cal.altimeterRef?.let { Barometric.altitudeFrom(it, p) } ?: Barometric.altitude(p)

    CollectSensors(listOfNotNull(sensor), SensorManager.SENSOR_DELAY_UI) { item ->
        if (item !is SensorEventItem.Sample) return@CollectSensors
        val s = item.sample
        val p = filter.update(s.values[0].toDouble(), s.timestampNs)
        val h = altitudeOf(p)
        if (rec.isRecording && recT.ready(s.timestampNs)) rec.add(doubleArrayOf(p, h))
        if (!frozen && ui.ready(s.timestampNs)) { hpa = p; stats.add(h) }
        if (!frozen && chartT.ready(s.timestampNs)) series.add(Units.length(h, units).toFloat())
    }

    val ref = cal.altimeterRef
    ToolScaffold(
        tool = ToolId.ALTIMETER, onBack = onBack, onOpenRecord = onOpenRecord,
        recording = rec,
        onStartRecording = {
            val note = ref?.let { context.getString(R.string.alt_cal_note, Fmt.num(it.altitudeM, 1), Fmt.num(it.pressureHpa, 2), Fmt.dateTime(it.setAtMs)) }
                ?: context.getString(R.string.alt_no_ref)
            rec.start(
                RecordingSpec(
                    columns = listOf(DataColumn("pressure_hpa", "hPa", 2), DataColumn("altitude_m", "m", 1)),
                    headerComments = csvHeader(context, ToolId.ALTIMETER, sensor?.name.orEmpty(), note, listOf("altitude_m always in meters")),
                    calibrated = ref != null, calibrationNote = note, sensorName = sensor?.name.orEmpty(),
                ),
            )
        },
        frozen = frozen, onFreezeToggle = { frozen = !frozen },
        onCalibrate = { calibrating = true },
        infoExtra = { SensorInfo(sensor) },
    ) {
        if (hpa.isNaN()) { Text(stringResource(R.string.waiting_sensor)); return@ToolScaffold }
        val h = altitudeOf(hpa)
        val sym = Units.lengthSymbol(units)
        BigReadout(
            value = Fmt.num(Units.length(h, units), 1), unit = sym,
            caption = stringResource(if (ref != null) R.string.alt_relative else R.string.alt_standard),
            badge = stringResource(if (ref != null) R.string.alt_badge_ref else R.string.alt_badge_noref),
            badgeLevel = if (ref != null) BadgeLevel.GOOD else BadgeLevel.APPROX,
        )
        if (ref == null) {
            NoticeBanner(stringResource(R.string.alt_set_ref_hint))
            Button(onClick = { calibrating = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.alt_set_ref)) }
        } else {
            val hours = (System.currentTimeMillis() - ref.setAtMs) / 3_600_000.0
            Text(
                stringResource(R.string.alt_ref_age, Fmt.num(Units.length(ref.altitudeM, units), 1) + " " + sym, Fmt.num(hours, 1)),
                style = MaterialTheme.typography.bodySmall,
                color = if (hours > 3) LocalInstrument.current.warn else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SectionCard(null) {
            LiveChart(listOf(series to LocalInstrument.current.accent), minSpan = if (units == com.eirahoutmoss.nextools.core.units.UnitSystem.IMPERIAL) 6f else 2f)
        }
        StatRow(
            listOf(
                stringResource(R.string.stat_min) to Fmt.num(Units.length(stats.min, units), 1),
                stringResource(R.string.stat_max) to Fmt.num(Units.length(stats.max, units), 1),
                stringResource(R.string.alt_pressure) to Fmt.num(hpa, 1) + " hPa",
            ),
        )
        OutlinedButton(onClick = { stats.reset(); series.clear() }) { Text(stringResource(R.string.action_reset_stats)) }
    }

    if (calibrating) {
        AltimeterCalibration(
            currentHpa = hpa,
            onDismiss = { calibrating = false },
        )
    }
}

@Composable
private fun AltimeterCalibration(currentHpa: Double, onDismiss: () -> Unit) {
    val c = appContainer()
    val context = LocalContext.current
    val settings by c.settings.state.collectAsStateWithLifecycle()
    val cal by c.calibrations.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var known by remember { mutableStateOf(false) }
    var fetching by remember { mutableStateOf(false) }
    var gpsError by remember { mutableStateOf<String?>(null) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    val canSet = !currentHpa.isNaN()

    if (known) {
        NumberInputDialog(
            title = stringResource(R.string.alt_known_title),
            message = stringResource(R.string.alt_known_message),
            label = stringResource(R.string.baro_alt_label, Units.lengthSymbol(settings.units)),
            initial = null,
            onConfirm = { v ->
                val m = Units.lengthToMeters(v, settings.units)
                if (m < -500 || m > 9000) context.getString(R.string.error_range, "-500", "9000")
                else { c.calibrations.setAltimeterRef(Barometric.Reference(currentHpa, m, System.currentTimeMillis())); onDismiss(); null }
            },
            onDismiss = onDismiss,
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.alt_set_ref)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.alt_cal_explain), style = MaterialTheme.typography.bodyMedium)
                Button(enabled = canSet, onClick = {
                    c.calibrations.setAltimeterRef(Barometric.Reference(currentHpa, 0.0, System.currentTimeMillis())); onDismiss()
                }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.alt_zero_here)) }
                OutlinedButton(enabled = canSet, onClick = { known = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.alt_known)) }
                OutlinedButton(enabled = canSet && !fetching, onClick = {
                    if (!LocationHelper.hasFine(context)) {
                        perm.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        return@OutlinedButton
                    }
                    fetching = true
                    scope.launch {
                        val loc = LocationHelper.current(context, timeoutMs = 20_000, gpsPreferred = true)
                        fetching = false
                        if (loc != null && loc.hasAltitude()) {
                            c.calibrations.setAltimeterRef(Barometric.Reference(currentHpa, loc.altitude, System.currentTimeMillis()))
                            onDismiss()
                        } else gpsError = context.getString(R.string.gps_alt_failed)
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(if (fetching) R.string.gps_waiting else R.string.gps_alt_use)) }
                gpsError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Text(stringResource(R.string.gps_alt_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (cal.altimeterRef != null) {
                    TextButton(onClick = { c.calibrations.reset(ToolId.ALTIMETER); onDismiss() }) { Text(stringResource(R.string.cal_reset_title)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}
