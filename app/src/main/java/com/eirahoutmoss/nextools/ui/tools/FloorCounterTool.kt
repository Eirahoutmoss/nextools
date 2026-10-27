package com.eirahoutmoss.nextools.ui.tools

import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.measure.Barometric
import com.eirahoutmoss.nextools.core.measure.Floors
import com.eirahoutmoss.nextools.core.measure.LowPass
import com.eirahoutmoss.nextools.core.units.Units
import com.eirahoutmoss.nextools.records.Column as DataColumn
import com.eirahoutmoss.nextools.sensors.SensorEventItem
import com.eirahoutmoss.nextools.ui.Fmt
import com.eirahoutmoss.nextools.ui.appContainer
import com.eirahoutmoss.nextools.ui.components.BadgeLevel
import com.eirahoutmoss.nextools.ui.components.BigReadout
import com.eirahoutmoss.nextools.ui.components.LiveChart
import com.eirahoutmoss.nextools.ui.components.NumberInputDialog
import com.eirahoutmoss.nextools.ui.components.RecordingSpec
import com.eirahoutmoss.nextools.ui.components.RollingSeries
import com.eirahoutmoss.nextools.ui.components.SectionCard
import com.eirahoutmoss.nextools.ui.components.StatRow
import com.eirahoutmoss.nextools.ui.components.ToolScaffold
import com.eirahoutmoss.nextools.ui.components.rememberRecording
import com.eirahoutmoss.nextools.ui.theme.LocalInstrument

/** Kat Sayacı: başlangıç noktasına göre barometrik yükseklik farkından kat değişimi. */
@Composable
fun FloorCounterTool(onBack: () -> Unit, onOpenRecord: (String) -> Unit) {
    val c = appContainer()
    val context = LocalContext.current
    val settings by c.settings.state.collectAsStateWithLifecycle()
    val cal by c.calibrations.state.collectAsStateWithLifecycle()
    val sm = remember { context.getSystemService(android.content.Context.SENSOR_SERVICE) as SensorManager }
    val sensor = remember { sm.getDefaultSensor(Sensor.TYPE_PRESSURE) }
    val rec = rememberRecording(ToolId.FLOOR_COUNTER)
    val filter = remember { LowPass(1.0) }
    var hpa by remember { mutableDoubleStateOf(Double.NaN) }
    var ref by remember { mutableStateOf<Double?>(null) }
    var frozen by remember { mutableStateOf(false) }
    var calibrating by remember { mutableStateOf(false) }
    val series = remember { RollingSeries(240) }
    val ui = remember { Throttle(250_000_000L) }
    val recT = remember { Throttle(500_000_000L) }

    CollectSensors(listOfNotNull(sensor), SensorManager.SENSOR_DELAY_UI) { item ->
        if (item !is SensorEventItem.Sample) return@CollectSensors
        val s = item.sample
        val p = filter.update(s.values[0].toDouble(), s.timestampNs)
        if (ref == null) ref = p // açılıştaki kat = 0
        val dh = Barometric.altitude(p) - Barometric.altitude(ref!!)
        if (rec.isRecording && recT.ready(s.timestampNs)) rec.add(doubleArrayOf(dh, Floors.floors(dh, cal.floorHeight).toDouble()))
        if (!frozen && ui.ready(s.timestampNs)) { hpa = p; series.add(dh.toFloat()) }
    }

    ToolScaffold(
        tool = ToolId.FLOOR_COUNTER, onBack = onBack, onOpenRecord = onOpenRecord,
        recording = rec,
        onStartRecording = {
            val note = context.getString(R.string.floor_note, Fmt.num(cal.floorHeight, 2))
            rec.start(
                RecordingSpec(
                    columns = listOf(DataColumn("altitude_m", "m", 2), DataColumn("floor", "", 0)),
                    headerComments = csvHeader(context, ToolId.FLOOR_COUNTER, sensor?.name.orEmpty(), note, listOf("altitude_m relative to start point")),
                    calibrated = cal.isCalibrated(ToolId.FLOOR_COUNTER), calibrationNote = note, sensorName = sensor?.name.orEmpty(),
                ),
            )
        },
        frozen = frozen, onFreezeToggle = { frozen = !frozen }, onCalibrate = { calibrating = true },
        infoExtra = { SensorInfo(sensor) },
    ) {
        val r = ref
        if (hpa.isNaN() || r == null) { Text(stringResource(R.string.waiting_sensor)); return@ToolScaffold }
        val dh = Barometric.altitude(hpa) - Barometric.altitude(r)
        val floors = Floors.floors(dh, cal.floorHeight)
        BigReadout(
            value = (if (floors > 0) "+" else "") + floors, unit = stringResource(R.string.floor_unit),
            caption = stringResource(R.string.floor_caption),
            badge = stringResource(R.string.floor_badge, Fmt.num(cal.floorHeight, 1)), badgeLevel = BadgeLevel.NEUTRAL,
        )
        StatRow(
            listOf(
                stringResource(R.string.floor_height_change) to Fmt.signed(Units.length(dh, settings.units), 1) + " " + Units.lengthSymbol(settings.units),
                stringResource(R.string.alt_pressure) to Fmt.num(hpa, 2) + " hPa",
            ),
        )
        SectionCard(null) { LiveChart(listOf(series to LocalInstrument.current.accent), minSpan = 3f) }
        Button(onClick = { ref = hpa; series.clear() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.floor_reset)) }
        Text(stringResource(R.string.floor_tip), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (calibrating) {
        NumberInputDialog(
            title = stringResource(R.string.floor_height_title),
            message = stringResource(R.string.floor_height_message),
            label = stringResource(R.string.floor_height_label),
            initial = cal.floorHeight,
            onConfirm = { v -> if (v < 2 || v > 8) context.getString(R.string.error_range, "2", "8") else { c.calibrations.setFloorHeight(v); calibrating = false; null } },
            onDismiss = { calibrating = false },
        )
    }
}
