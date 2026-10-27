package com.eirahoutmoss.nextools.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.BufferOverflow

/** Bir sensör olayının kopyası (Android olay nesnesini yeniden kullanır; kopyalamak şart). */
class SensorSample(
    val type: Int,
    val values: FloatArray,
    /** Olay zamanı (ns, SystemClock.elapsedRealtimeNanos tabanı). */
    val timestampNs: Long,
    val accuracy: Int,
    /** Olayı üreten sensör (aynı türden birden çok sensör dinlenirken ayırt etmek için). */
    val sensor: Sensor? = null,
)

/** Ölçüm sırasında doğruluk değişimi de bildirilir (ör. pusula kalibrasyonu bozuldu). */
sealed interface SensorEventItem {
    class Sample(val sample: SensorSample) : SensorEventItem
    class Accuracy(val type: Int, val accuracy: Int) : SensorEventItem
}

object SensorStream {

    fun manager(context: Context): SensorManager? =
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    /**
     * Bir ya da birden çok sensörü dinler. Olaylar ayrı bir iş parçacığında alınır; akış toplanmayı
     * bırakınca dinleme kendiliğinden durur (pil boşa harcanmaz).
     *
     * [delayUs]: SensorManager.SENSOR_DELAY_* sabiti ya da mikro saniye.
     */
    fun events(context: Context, sensors: List<Sensor>, delayUs: Int): Flow<SensorEventItem> = callbackFlow {
        val sm = manager(context)
        if (sm == null || sensors.isEmpty()) {
            close()
            return@callbackFlow
        }
        val thread = HandlerThread("nextools-sensor").apply { start() }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                trySend(SensorEventItem.Sample(SensorSample(e.sensor.type, e.values.copyOf(), e.timestamp, e.accuracy, e.sensor)))
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                trySend(SensorEventItem.Accuracy(sensor.type, accuracy))
            }
        }
        val handler = Handler(thread.looper)
        sensors.forEach { s ->
            try {
                sm.registerListener(listener, s, delayUs, handler)
            } catch (e: SecurityException) {
                // Android 12+: 200 Hz üstü için izin gerekir; en hızlı izinli hızla yeniden dene.
                sm.registerListener(listener, s, SensorManager.SENSOR_DELAY_GAME, handler)
            }
        }
        awaitClose {
            sm.unregisterListener(listener)
            thread.quitSafely()
        }
    }.buffer(256, BufferOverflow.DROP_OLDEST)

    /** Yalnızca örnekler (doğruluk olayları atılır). */
    fun samples(context: Context, sensor: Sensor, delayUs: Int): Flow<SensorSample> = callbackFlow {
        val sm = manager(context)
        if (sm == null) { close(); return@callbackFlow }
        val thread = HandlerThread("nextools-sensor").apply { start() }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                trySend(SensorSample(e.sensor.type, e.values.copyOf(), e.timestamp, e.accuracy))
            }
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        try {
            sm.registerListener(listener, sensor, delayUs, Handler(thread.looper))
        } catch (e: SecurityException) {
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME, Handler(thread.looper))
        }
        awaitClose {
            sm.unregisterListener(listener)
            thread.quitSafely()
        }
    }.buffer(256, BufferOverflow.DROP_OLDEST)
}
