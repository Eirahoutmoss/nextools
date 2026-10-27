package com.eirahoutmoss.nextools.core.radio

import kotlin.math.abs
import kotlin.math.pow

enum class Band { GHZ_2_4, GHZ_5, GHZ_6, OTHER }

/** Wi-Fi kanal hesapları ve en boş kanal önerisi. */
object WifiChannels {
    fun band(freqMhz: Int): Band = when (freqMhz) {
        in 2400..2500 -> Band.GHZ_2_4
        in 4900..5899 -> Band.GHZ_5
        in 5925..7125 -> Band.GHZ_6
        else -> Band.OTHER
    }

    fun channel(freqMhz: Int): Int = when (band(freqMhz)) {
        Band.GHZ_2_4 -> if (freqMhz == 2484) 14 else (freqMhz - 2407) / 5
        Band.GHZ_5 -> (freqMhz - 5000) / 5
        Band.GHZ_6 -> (freqMhz - 5950) / 5
        Band.OTHER -> 0
    }

    data class Ap(val freqMhz: Int, val widthMhz: Int, val rssi: Int)

    /** RSSI'yi (dBm) 0..1 ağırlığa çevirir: −30 dBm çok güçlü, −90 dBm önemsiz. */
    private fun weight(rssi: Int) = ((rssi + 95) / 65.0).coerceIn(0.0, 1.0)

    /**
     * Bir merkez frekansındaki doluluk: o frekansla örtüşen erişim noktalarının ağırlık toplamı.
     * 2,4 GHz'de 20 MHz kanallar ±10 MHz kaplar, bu yüzden 1-6-11 dışındakiler komşularını da bozar.
     */
    fun congestion(centerMhz: Int, aps: List<Ap>, ownWidth: Int = 20): Double =
        aps.sumOf { ap ->
            val overlap = (ap.widthMhz / 2 + ownWidth / 2) - abs(ap.freqMhz - centerMhz)
            if (overlap <= 0) 0.0 else weight(ap.rssi) * (overlap.toDouble() / ownWidth).coerceAtMost(1.0)
        }

    /** 2,4 GHz için yalnızca örtüşmeyen 1, 6, 11; 5 GHz için yaygın 20 MHz kanallar. */
    fun recommend(band: Band, aps: List<Ap>): Pair<Int, Double>? {
        val candidates = when (band) {
            Band.GHZ_2_4 -> listOf(2412, 2437, 2462)
            Band.GHZ_5 -> listOf(5180, 5200, 5220, 5240, 5745, 5765, 5785, 5805, 5825)
            else -> return null
        }
        return candidates.map { it to congestion(it, aps.filter { a -> band(a.freqMhz) == band }) }.minBy { it.second }
            .let { channel(it.first) to it.second }
    }
}

/** Bluetooth LE: sinyal gücünden kaba uzaklık (log-uzaklık modeli). */
object BleMath {
    /** [txPower] 1 m'deki RSSI (yayında yoksa −59 dBm varsayılır), n = 2 (açık alan) .. 3 (iç mekân). */
    fun distanceM(rssi: Int, txPower: Int?, n: Double = 2.5): Double =
        10.0.pow(((txPower ?: -59) - rssi) / (10 * n))

    /** Sık görülen üretici kimlikleri (Bluetooth SIG). Ad uygulamada gösterilir; dile bağlı değil. */
    val COMPANIES = mapOf(
        0x004C to "Apple", 0x0006 to "Microsoft", 0x0075 to "Samsung", 0x00E0 to "Google",
        0x038F to "Xiaomi", 0x027D to "Huawei", 0x0157 to "Huami", 0x0087 to "Garmin",
        0x0059 to "Nordic", 0x000F to "Broadcom", 0x0171 to "Amazon", 0x0310 to "Tile",
        0x00D2 to "Sony", 0x0002 to "Intel", 0x0001 to "Nokia", 0x02E5 to "Espressif",
    )
}

/** Ham baytları okunur onaltılık metne çevirir: "04:A2:1F". */
fun ByteArray.toHexId(sep: String = ":"): String = joinToString(sep) { "%02X".format(it.toInt() and 0xFF) }
