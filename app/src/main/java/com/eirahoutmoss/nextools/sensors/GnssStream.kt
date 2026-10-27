package com.eirahoutmoss.nextools.sensors

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssMeasurementsEvent
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Görünen bir uydu. */
data class Satellite(
    val constellation: Int,
    val svid: Int,
    val cn0: Float,
    val elevation: Float,
    val azimuth: Float,
    val usedInFix: Boolean,
    /** Taşıyıcı frekansı (Hz); bilinmiyorsa null. L5/E5a ≈ 1176 MHz → çift frekans işareti. */
    val carrierHz: Float?,
)

/** Ham GNSS ölçüm turundan Sahte GPS denetimi için gereken özet. */
data class RawEpoch(
    /** Alıcı saati süreksizlik sayacı; değişirse saat sıfırlanmış / sıçramıştır. */
    val clockDiscontinuity: Int,
    /** GNSS saatine göre UTC (ms); tam sapma bilinmiyorsa null. */
    val gnssUtcMs: Long?,
    /** Ortalama otomatik kazanç düzeyi (dB); telefon bildirmiyorsa null. */
    val agcDb: Double?,
    val measurementCount: Int,
)

/** GPS konumu ve uydu durumu akışları. İzin çağıran tarafından alınmış olmalıdır. */
object GnssStream {

    @SuppressLint("MissingPermission")
    fun locations(context: Context, intervalMs: Long = 1000, minDistanceM: Float = 0f): Flow<Location> = callbackFlow {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (lm == null || !lm.allProviders.contains(LocationManager.GPS_PROVIDER)) { close(); return@callbackFlow }
        val l = object : LocationListener {
            override fun onLocationChanged(location: Location) { trySend(location) }
            @Deprecated("Android 10 öncesi için") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }
        runCatching { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, intervalMs, minDistanceM, l, Looper.getMainLooper()) }
            .onFailure { close(it) }
        awaitClose { lm.removeUpdates(l) }
    }

    @SuppressLint("MissingPermission")
    fun satellites(context: Context): Flow<List<Satellite>> = callbackFlow {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (lm == null) { close(); return@callbackFlow }
        val cb = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                val list = (0 until status.satelliteCount).map { i ->
                    Satellite(
                        constellation = status.getConstellationType(i),
                        svid = status.getSvid(i),
                        cn0 = status.getCn0DbHz(i),
                        elevation = status.getElevationDegrees(i),
                        azimuth = status.getAzimuthDegrees(i),
                        usedInFix = status.usedInFix(i),
                        carrierHz = if (status.hasCarrierFrequencyHz(i)) status.getCarrierFrequencyHz(i) else null,
                    )
                }
                trySend(list)
            }
        }
        val ok = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) lm.registerGnssStatusCallback(context.mainExecutor, cb)
            else @Suppress("DEPRECATION") lm.registerGnssStatusCallback(cb, Handler(Looper.getMainLooper()))
        }.getOrDefault(false)
        if (!ok) close()
        awaitClose { lm.unregisterGnssStatusCallback(cb) }
    }

    /** GPS dönemi (1980-01-06) ile Unix dönemi arası (ms) ve artık saniye (2017'den beri 18). */
    private const val GPS_EPOCH_MS = 315_964_800_000L
    private const val LEAP_S = 18

    @SuppressLint("MissingPermission")
    fun rawEpochs(context: Context): Flow<RawEpoch> = callbackFlow {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (lm == null) { close(); return@callbackFlow }
        val cb = object : GnssMeasurementsEvent.Callback() {
            override fun onGnssMeasurementsReceived(e: GnssMeasurementsEvent) {
                val c = e.clock
                val utc = if (c.hasFullBiasNanos()) {
                    val gpsNs = c.timeNanos - (c.fullBiasNanos + (if (c.hasBiasNanos()) c.biasNanos else 0.0).toLong())
                    gpsNs / 1_000_000 + GPS_EPOCH_MS - (if (c.hasLeapSecond()) c.leapSecond else LEAP_S) * 1000L
                } else null
                val agc: List<Double> = if (Build.VERSION.SDK_INT >= 33) {
                    e.gnssAutomaticGainControls.map { it.levelDb }
                } else {
                    @Suppress("DEPRECATION")
                    e.measurements.filter { it.hasAutomaticGainControlLevelDb() }.map { it.automaticGainControlLevelDb }
                }
                trySend(RawEpoch(c.hardwareClockDiscontinuityCount, utc, agc.takeIf { it.isNotEmpty() }?.average(), e.measurements.size))
            }
        }
        val ok = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) lm.registerGnssMeasurementsCallback(context.mainExecutor, cb)
            else @Suppress("DEPRECATION") lm.registerGnssMeasurementsCallback(cb, Handler(Looper.getMainLooper()))
        }.getOrDefault(false)
        if (!ok) close()
        awaitClose { lm.unregisterGnssMeasurementsCallback(cb) }
    }

    fun isGpsEnabled(context: Context): Boolean =
        (context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager)?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
}
