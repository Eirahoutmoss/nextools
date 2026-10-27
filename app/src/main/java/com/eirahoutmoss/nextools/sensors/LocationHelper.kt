package com.eirahoutmoss.nextools.sensors

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * İsteğe bağlı konum: pusulada gerçek kuzey, altimetrede GPS yüksekliği ve kayda konum eklemek için.
 * Konum hiçbir yere gönderilmez; yalnızca kullanıcı isterse kayda yazılır.
 */
object LocationHelper {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasFine(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Bilinen en taze konum (en fazla [maxAgeMs] eski). İzin yoksa null. */
    @SuppressLint("MissingPermission")
    fun lastKnown(context: Context, maxAgeMs: Long = 6 * 3_600_000L): Location? {
        if (!hasPermission(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val now = System.currentTimeMillis()
        return runCatching {
            lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }
                .filter { now - it.time <= maxAgeMs }
                .maxByOrNull { it.time }
        }.getOrNull()
    }

    /** Taze konum ister (Android 11+); olmazsa son bilinen konuma düşer. */
    @SuppressLint("MissingPermission")
    suspend fun current(context: Context, timeoutMs: Long = 15_000, gpsPreferred: Boolean = false): Location? {
        if (!hasPermission(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val provider = when {
            gpsPreferred && hasFine(context) && lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            hasFine(context) && lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> null
        } ?: return lastKnown(context)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return lastKnown(context, maxAgeMs = 30 * 60_000L) ?: lastKnown(context)
        }
        val fresh = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Location?> { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                runCatching {
                    lm.getCurrentLocation(provider, signal, ContextCompat.getMainExecutor(context)) { loc ->
                        if (cont.isActive) cont.resume(loc)
                    }
                }.onFailure { if (cont.isActive) cont.resume(null) }
            }
        }
        return fresh ?: lastKnown(context)
    }

    /** Dünya manyetik modeli (WMM): konuma göre sapma açısı ve beklenen alan şiddeti. */
    data class MagneticModel(val declinationDeg: Double, val fieldUt: Double)

    fun magneticModel(loc: Location): MagneticModel {
        val f = GeomagneticField(loc.latitude.toFloat(), loc.longitude.toFloat(), loc.altitude.toFloat(), System.currentTimeMillis())
        return MagneticModel(f.declination.toDouble(), f.fieldStrength / 1000.0) // nT → µT
    }
}
