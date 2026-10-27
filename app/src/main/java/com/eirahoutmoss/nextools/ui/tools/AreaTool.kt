package com.eirahoutmoss.nextools.ui.tools

import android.location.Location
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.geo.Geo
import com.eirahoutmoss.nextools.core.geo.LatLon
import com.eirahoutmoss.nextools.core.units.UnitSystem
import com.eirahoutmoss.nextools.records.Column as DataColumn
import com.eirahoutmoss.nextools.records.SummaryItem
import com.eirahoutmoss.nextools.ui.Fmt
import com.eirahoutmoss.nextools.ui.appContainer
import com.eirahoutmoss.nextools.ui.components.BadgeLevel
import com.eirahoutmoss.nextools.ui.components.BigReadout
import com.eirahoutmoss.nextools.ui.components.KeyValue
import com.eirahoutmoss.nextools.ui.components.NoticeBanner
import com.eirahoutmoss.nextools.ui.components.RecordingSpec
import com.eirahoutmoss.nextools.ui.components.SectionCard
import com.eirahoutmoss.nextools.ui.components.ToolScaffold
import com.eirahoutmoss.nextools.ui.components.rememberRecording

/** Alan / Çevre Ölçer: köşelerde durup nokta ekleyerek ya da yürürken otomatik; m², dönüm, hektar. */
@Composable
fun AreaTool(onBack: () -> Unit, onOpenRecord: (String) -> Unit) {
    val c = appContainer()
    val context = LocalContext.current
    val settings by c.settings.state.collectAsStateWithLifecycle()
    val rec = rememberRecording(ToolId.AREA)
    val points = remember { mutableStateListOf<LatLon>() }
    var fix by remember { mutableStateOf<Location?>(null) }
    var auto by remember { mutableStateOf(false) }

    fun addPoint(l: Location) {
        val p = LatLon(l.latitude, l.longitude)
        points += p
        val area = Geo.area(points)
        if (rec.isRecording) rec.add(doubleArrayOf(p.lat, p.lon, if (l.hasAccuracy()) l.accuracy.toDouble() else Double.NaN, area, Geo.perimeter(points)))
    }

    ToolScaffold(
        tool = ToolId.AREA, onBack = onBack, onOpenRecord = onOpenRecord,
        recording = rec,
        onStartRecording = {
            val note = context.getString(R.string.area_note)
            rec.start(
                RecordingSpec(
                    columns = listOf(DataColumn("lat", "°", 7), DataColumn("lon", "°", 7), DataColumn("accuracy_m", "m", 1),
                        DataColumn("area_m2", "m²", 1), DataColumn("perimeter_m", "m", 1)),
                    headerComments = csvHeader(context, ToolId.AREA, "GNSS", note, listOf("each row = one corner; area/perimeter of the polygon so far")),
                    calibrated = false, calibrationNote = note, sensorName = "GNSS",
                    summarize = { _, _ ->
                        val a = Geo.area(points)
                        listOf(
                            SummaryItem("total", "area_m2", a, "m²", 1),
                            SummaryItem("total", "area_donum", a / Geo.M2_PER_DONUM, context.getString(R.string.unit_donum), 3),
                            SummaryItem("total", "perimeter_m", Geo.perimeter(points), "m", 1),
                            SummaryItem("count", "corners", points.size.toDouble(), "", 0),
                        )
                    },
                ),
            )
            // Kayıt başlamadan eklenen köşeler de kayda girsin
            val existing = points.toList(); points.clear()
            existing.forEach { p ->
                points += p
                rec.add(doubleArrayOf(p.lat, p.lon, Double.NaN, Geo.area(points), Geo.perimeter(points)))
            }
        },
        frozen = false, onFreezeToggle = null, onCalibrate = null,
    ) {
        PermissionsGate(LOCATION_PERMISSIONS, stringResource(R.string.gps_rationale), required = listOf(android.Manifest.permission.ACCESS_FINE_LOCATION)) {
            CollectGnss(onLocation = { l ->
                fix = l
                if (auto && (!l.hasAccuracy() || l.accuracy <= 15)) {
                    val last = points.lastOrNull()
                    if (last == null || Geo.distance(last, LatLon(l.latitude, l.longitude)) >= 3.0) addPoint(l)
                }
            }, withSatellites = false)
            GpsOffBanner()
            val area = Geo.area(points)
            val imperial = settings.units == UnitSystem.IMPERIAL
            BigReadout(
                value = if (imperial) Fmt.num(area / Geo.M2_PER_ACRE, 3) else Fmt.num(area, 0), unit = if (imperial) "acre" else "m²",
                caption = stringResource(R.string.area_corners, points.size),
                badge = fix?.let { if (it.hasAccuracy()) stringResource(R.string.gps_accuracy, Fmt.num(it.accuracy.toDouble(), 0)) else null } ?: stringResource(R.string.gps_searching),
                badgeLevel = if ((fix?.accuracy ?: 99f) <= 5f) BadgeLevel.GOOD else BadgeLevel.APPROX,
            )
            SectionCard(null) {
                KeyValue(stringResource(R.string.unit_donum_long), Fmt.num(area / Geo.M2_PER_DONUM, 3))
                KeyValue(stringResource(R.string.area_hectare), Fmt.num(area / Geo.M2_PER_HECTARE, 4))
                KeyValue(stringResource(R.string.area_perimeter), Fmt.num(Geo.perimeter(points), 1) + " m")
            }
            ShapeCanvas(points.toList(), closed = true)
            Button(onClick = { fix?.let { addPoint(it) } }, enabled = fix != null, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.area_add_corner)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { if (points.isNotEmpty()) points.removeAt(points.lastIndex) }, enabled = points.isNotEmpty() && !rec.isRecording) { Text(stringResource(R.string.area_undo)) }
                OutlinedButton(onClick = { points.clear() }, enabled = points.isNotEmpty() && !rec.isRecording) { Text(stringResource(R.string.action_clear)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.area_auto), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(auto, { auto = it })
            }
            NoticeBanner(stringResource(R.string.area_disclaimer), BadgeLevel.NEUTRAL)
        }
    }
}
