package com.eirahoutmoss.nextools.ui.screens

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.measure.Angles
import com.eirahoutmoss.nextools.core.measure.LevelMath
import com.eirahoutmoss.nextools.core.units.PressureUnit
import com.eirahoutmoss.nextools.core.units.UnitSystem
import com.eirahoutmoss.nextools.core.units.Units
import com.eirahoutmoss.nextools.sensors.SensorEventItem
import com.eirahoutmoss.nextools.sensors.SensorStream
import com.eirahoutmoss.nextools.settings.Calibrations
import com.eirahoutmoss.nextools.ui.Fmt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Ana ekran kartlarındaki küçük canlı değerler (ör. su terazisinde anlık açı). Yalnızca ekran
 * görünürken ve düşük hızla (SENSOR_DELAY_UI) dinlenir; saniyede en fazla 4 kez güncellenir.
 */
@Composable
fun rememberLiveValues(visible: Set<ToolId>, cal: Calibrations, units: UnitSystem, pressureUnit: PressureUnit): Map<ToolId, String> {
    val context = LocalContext.current
    val values = remember { mutableStateMapOf<ToolId, String>() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val wanted = visible.intersect(LIVE_TOOLS)
    LaunchedEffect(wanted, cal, units, pressureUnit) {
        values.keys.retainAll(wanted)
        if (wanted.isEmpty()) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            coroutineScope { collectLive(context, wanted, cal, units, pressureUnit, values) }
        }
    }
    return values
}

private val LIVE_TOOLS = setOf(ToolId.LEVEL, ToolId.COMPASS, ToolId.MAGNETOMETER, ToolId.BAROMETER, ToolId.LUX)

private suspend fun collectLive(
    context: Context,
    wanted: Set<ToolId>,
    cal: Calibrations,
    units: UnitSystem,
    pressureUnit: PressureUnit,
    out: MutableMap<ToolId, String>,
) = coroutineScope {
    val sm = SensorStream.manager(context) ?: return@coroutineScope
    val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    val rotation = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    val mag = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    val sensors = buildList {
        if ((ToolId.LEVEL in wanted || ToolId.COMPASS in wanted) && accel != null) add(accel)
        if (ToolId.COMPASS in wanted && rotation != null) add(rotation)
        if ((ToolId.MAGNETOMETER in wanted || ToolId.COMPASS in wanted) && mag != null) add(mag)
        if (ToolId.BAROMETER in wanted) sm.getDefaultSensor(Sensor.TYPE_PRESSURE)?.let { add(it) }
        if (ToolId.LUX in wanted) sm.getDefaultSensor(Sensor.TYPE_LIGHT)?.let { add(it) }
    }.distinct()
    if (sensors.isEmpty()) return@coroutineScope
    val lastShown = HashMap<ToolId, Long>()
    var gravity: FloatArray? = null
    var geomag: FloatArray? = null
    fun post(id: ToolId, text: String, tNs: Long) {
        val prev = lastShown[id] ?: 0L
        if (tNs - prev < 250_000_000L) return
        lastShown[id] = tNs
        out[id] = text
    }
    launch {
        SensorStream.events(context, sensors, SensorManager.SENSOR_DELAY_UI).collect { item ->
            if (item !is SensorEventItem.Sample) return@collect
            val s = item.sample
            val v = s.values
            when (s.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    gravity = v
                    if (ToolId.LEVEL in wanted) {
                        val tilt = LevelMath.tiltFromFlat(v[0].toDouble(), v[1].toDouble(), v[2].toDouble())
                        post(ToolId.LEVEL, Fmt.num(tilt, 1) + "°", s.timestampNs)
                    }
                    if (ToolId.COMPASS in wanted && rotation == null) {
                        val g = gravity; val m = geomag
                        if (g != null && m != null) {
                            val r = FloatArray(9)
                            if (SensorManager.getRotationMatrix(r, null, g, m)) {
                                val o = FloatArray(3); SensorManager.getOrientation(r, o)
                                post(ToolId.COMPASS, Fmt.num(Angles.normalize360(Math.toDegrees(o[0].toDouble())), 0) + "°", s.timestampNs)
                            }
                        }
                    }
                }
                Sensor.TYPE_ROTATION_VECTOR -> {
                    val r = FloatArray(9)
                    SensorManager.getRotationMatrixFromVector(r, v)
                    val o = FloatArray(3); SensorManager.getOrientation(r, o)
                    post(ToolId.COMPASS, Fmt.num(Angles.normalize360(Math.toDegrees(o[0].toDouble())), 0) + "°", s.timestampNs)
                }
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    geomag = v
                    val t = kotlin.math.sqrt((v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).toDouble())
                    if (ToolId.MAGNETOMETER in wanted) post(ToolId.MAGNETOMETER, Fmt.num(t, 0) + " µT", s.timestampNs)
                }
                Sensor.TYPE_PRESSURE -> post(
                    ToolId.BAROMETER,
                    Fmt.num(pressureUnit.fromHpa(v[0].toDouble()), if (pressureUnit == PressureUnit.INHG) 2 else 0) + " " + pressureUnit.symbol,
                    s.timestampNs,
                )
                Sensor.TYPE_LIGHT -> {
                    val lux = v[0] * (cal.luxFactor ?: 1.0)
                    post(ToolId.LUX, Fmt.num(Units.illuminance(lux, units), 0) + " " + Units.illuminanceSymbol(units), s.timestampNs)
                }
            }
        }
    }
}
