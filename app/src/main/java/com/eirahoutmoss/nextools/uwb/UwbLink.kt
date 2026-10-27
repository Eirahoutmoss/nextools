package com.eirahoutmoss.nextools.uwb

import android.content.Context
import androidx.core.uwb.RangingParameters
import androidx.core.uwb.RangingResult
import androidx.core.uwb.UwbAddress
import androidx.core.uwb.UwbClientSessionScope
import androidx.core.uwb.UwbComplexChannel
import androidx.core.uwb.UwbControllerSessionScope
import androidx.core.uwb.UwbDevice
import androidx.core.uwb.UwbManager
import com.eirahoutmoss.nextools.core.measure.UwbPairing
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.SecureRandom

/**
 * Jetpack UWB (androidx.core.uwb) sarmalayıcısı. Ana cihaz (controller) oturum bilgisini QR ile verir,
 * karşı cihaz (controlee) kendi kısa adresini 4 haneli kod olarak gösterir; ikisi de girilince ölçüm başlar.
 */
class UwbLink(context: Context) {
    private val manager = UwbManager.createInstance(context)
    private var scope: UwbClientSessionScope? = null

    suspend fun available(): Boolean = runCatching { manager.isAvailable() }.getOrDefault(false)

    /** Ana cihaz: oturum açar, QR'da gösterilecek eşleşme bilgisini döndürür. */
    suspend fun startController(): UwbPairing {
        val s = manager.controllerSessionScope()
        scope = s
        val rnd = SecureRandom()
        val key = ByteArray(8).also { rnd.nextBytes(it) }
        val sid = rnd.nextInt(Int.MAX_VALUE - 1) + 1
        return UwbPairing(s.localAddress.address, s.uwbComplexChannel.channel, s.uwbComplexChannel.preambleIndex, sid, key)
    }

    /** Karşı cihaz: oturum açar, kendi adresini döndürür (ana cihaza girilecek). */
    suspend fun startControlee(): ByteArray {
        val s = manager.controleeSessionScope()
        scope = s
        return s.localAddress.address
    }

    /** [pairing]: ana cihazın bilgisi; [peerAddress]: karşı tarafın adresi (ana cihazda controlee'nin, controlee'de ana cihazın). */
    fun range(pairing: UwbPairing, peerAddress: ByteArray): Flow<UwbReading> {
        val s = scope ?: error("session not started")
        val params = RangingParameters(
            uwbConfigType = RangingParameters.CONFIG_UNICAST_DS_TWR,
            sessionId = pairing.sessionId,
            subSessionId = 0,
            sessionKeyInfo = pairing.sessionKey,
            subSessionKeyInfo = null,
            complexChannel = if (s is UwbControllerSessionScope) s.uwbComplexChannel else UwbComplexChannel(pairing.channel, pairing.preamble),
            peerDevices = listOf(UwbDevice(UwbAddress(peerAddress))),
            updateRateType = RangingParameters.RANGING_UPDATE_RATE_AUTOMATIC,
        )
        return s.prepareSession(params).map { r ->
            when (r) {
                is RangingResult.RangingResultPosition -> UwbReading(r.position.distance?.value, r.position.azimuth?.value, r.position.elevation?.value)
                is RangingResult.RangingResultPeerDisconnected -> UwbReading(null, null, null, disconnected = true)
                else -> UwbReading(null, null, null, failure = true)
            }
        }
    }
}
