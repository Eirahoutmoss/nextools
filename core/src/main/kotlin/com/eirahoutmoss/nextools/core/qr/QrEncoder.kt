package com.eirahoutmoss.nextools.core.qr

/**
 * QR kod üretici (ISO/IEC 18004), bayt kipi, sürüm 1–40, düzey L/M/Q/H.
 * Harici kütüphane gerektirmez; testlerde başvuru kütüphanesiyle birebir karşılaştırılır.
 */
enum class QrEcc(val formatBits: Int, val tableIndex: Int) { L(1, 0), M(0, 1), Q(3, 2), H(2, 3) }

class QrCode internal constructor(val version: Int, val ecc: QrEcc, val mask: Int, private val modules: Array<BooleanArray>) {
    val size: Int get() = modules.size
    /** [y] satır, [x] sütun; true = koyu. */
    operator fun get(x: Int, y: Int): Boolean = modules[y][x]
}

object QrEncoder {

    class TooLongException : IllegalArgumentException("data too long for QR")

    /** [mask] null ise en düşük cezalı maske seçilir. [minVersion] testler içindir. */
    fun encode(data: ByteArray, ecc: QrEcc = QrEcc.M, mask: Int? = null, minVersion: Int = 1): QrCode {
        var version = minVersion
        while (true) {
            if (version > 40) throw TooLongException()
            val capacityBits = dataCodewords(version, ecc) * 8
            val needed = 4 + countBits(version) + data.size * 8
            if (needed <= capacityBits) break
            version++
        }
        val bits = BitBuffer()
        bits.append(0x4, 4)
        bits.append(data.size, countBits(version))
        data.forEach { bits.append(it.toInt() and 0xFF, 8) }
        val capacityBits = dataCodewords(version, ecc) * 8
        bits.append(0, minOf(4, capacityBits - bits.size))
        bits.append(0, (8 - bits.size % 8) % 8)
        var pad = 0xEC
        while (bits.size < capacityBits) { bits.append(pad, 8); pad = pad xor 0xEC xor 0x11 }
        val dataCodewords = ByteArray(bits.size / 8)
        for (i in 0 until bits.size) if (bits[i]) dataCodewords[i ushr 3] = (dataCodewords[i ushr 3].toInt() or (1 shl (7 - (i and 7)))).toByte()

        val all = addEccAndInterleave(dataCodewords, version, ecc)
        val size = version * 4 + 17
        val m = Matrix(size)
        m.drawFunctionPatterns(version, ecc)
        m.drawCodewords(all)
        val chosen = mask ?: (0..7).minBy { k ->
            m.applyMask(k); m.drawFormatBits(ecc, k)
            val p = m.penalty()
            m.applyMask(k) // geri al (XOR)
            p
        }
        m.applyMask(chosen)
        m.drawFormatBits(ecc, chosen)
        return QrCode(version, ecc, chosen, m.modules)
    }

    fun encodeText(text: String, ecc: QrEcc = QrEcc.M): QrCode = encode(text.toByteArray(Charsets.UTF_8), ecc)

    private fun countBits(version: Int) = if (version <= 9) 8 else 16

    private fun blocks(version: Int, ecc: QrEcc) = QrTables.RS_BLOCKS[(version - 1) * 4 + ecc.tableIndex]

    internal fun dataCodewords(version: Int, ecc: QrEcc): Int {
        val b = blocks(version, ecc)
        return b[0] * b[2] + b[3] * b[5]
    }

    private fun addEccAndInterleave(data: ByteArray, version: Int, ecc: QrEcc): ByteArray {
        val t = blocks(version, ecc)
        val eccLen = t[1] - t[2]
        val lens = List(t[0]) { t[2] } + List(t[3]) { t[5] }
        val divisor = rsDivisor(eccLen)
        val dataBlocks = ArrayList<ByteArray>()
        val eccBlocks = ArrayList<ByteArray>()
        var k = 0
        for (len in lens) {
            val d = data.copyOfRange(k, k + len); k += len
            dataBlocks += d
            eccBlocks += rsRemainder(d, divisor)
        }
        val out = ArrayList<Byte>()
        val maxLen = lens.max()
        for (i in 0 until maxLen) for (b in dataBlocks) if (i < b.size) out += b[i]
        for (i in 0 until eccLen) for (b in eccBlocks) out += b[i]
        return out.toByteArray()
    }

    private fun gfMul(x: Int, y: Int): Int {
        var z = 0
        for (i in 7 downTo 0) {
            z = (z shl 1) xor ((z ushr 7) * 0x11D)
            z = z xor (((y ushr i) and 1) * x)
        }
        return z and 0xFF
    }

    private fun rsDivisor(degree: Int): IntArray {
        val result = IntArray(degree); result[degree - 1] = 1
        var root = 1
        repeat(degree) {
            for (j in result.indices) {
                result[j] = gfMul(result[j], root)
                if (j + 1 < result.size) result[j] = result[j] xor result[j + 1]
            }
            root = gfMul(root, 0x02)
        }
        return result
    }

    private fun rsRemainder(data: ByteArray, divisor: IntArray): ByteArray {
        val result = IntArray(divisor.size)
        for (b in data) {
            val factor = (b.toInt() and 0xFF) xor result[0]
            System.arraycopy(result, 1, result, 0, result.size - 1)
            result[result.size - 1] = 0
            for (i in result.indices) result[i] = result[i] xor gfMul(divisor[i], factor)
        }
        return ByteArray(result.size) { result[it].toByte() }
    }

    private class BitBuffer {
        private val bits = ArrayList<Boolean>()
        val size get() = bits.size
        operator fun get(i: Int) = bits[i]
        fun append(v: Int, len: Int) { for (i in len - 1 downTo 0) bits += ((v ushr i) and 1) == 1 }
    }

    private class Matrix(val size: Int) {
        val modules = Array(size) { BooleanArray(size) }
        val isFunction = Array(size) { BooleanArray(size) }

        fun set(x: Int, y: Int, dark: Boolean) { modules[y][x] = dark; isFunction[y][x] = true }

        fun drawFunctionPatterns(version: Int, ecc: QrEcc) {
            for (i in 0 until size) { set(6, i, i % 2 == 0); set(i, 6, i % 2 == 0) }
            finder(3, 3); finder(size - 4, 3); finder(3, size - 4)
            val pos = QrTables.ALIGNMENT[version - 1]
            val n = pos.size
            for (i in 0 until n) for (j in 0 until n) {
                if ((i == 0 && j == 0) || (i == 0 && j == n - 1) || (i == n - 1 && j == 0)) continue
                alignment(pos[i], pos[j])
            }
            drawFormatBits(ecc, 0)
            if (version >= 7) {
                var rem = version
                repeat(12) { rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25) }
                val bits = (version shl 12) or rem
                for (i in 0 until 18) {
                    val bit = ((bits ushr i) and 1) == 1
                    val a = size - 11 + i % 3; val b = i / 3
                    set(a, b, bit); set(b, a, bit)
                }
            }
        }

        private fun finder(cx: Int, cy: Int) {
            for (dy in -4..4) for (dx in -4..4) {
                val d = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                val x = cx + dx; val y = cy + dy
                if (x in 0 until size && y in 0 until size) set(x, y, d != 2 && d != 4)
            }
        }

        private fun alignment(cx: Int, cy: Int) {
            for (dy in -2..2) for (dx in -2..2) set(cx + dx, cy + dy, maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)) != 1)
        }

        fun drawFormatBits(ecc: QrEcc, mask: Int) {
            val data = (ecc.formatBits shl 3) or mask
            var rem = data
            repeat(10) { rem = (rem shl 1) xor ((rem ushr 9) * 0x537) }
            val bits = ((data shl 10) or rem) xor 0x5412
            fun bit(i: Int) = ((bits ushr i) and 1) == 1
            for (i in 0..5) set(8, i, bit(i))
            set(8, 7, bit(6)); set(8, 8, bit(7)); set(7, 8, bit(8))
            for (i in 9 until 15) set(14 - i, 8, bit(i))
            for (i in 0 until 8) set(size - 1 - i, 8, bit(i))
            for (i in 8 until 15) set(8, size - 15 + i, bit(i))
            set(8, size - 8, true)
        }

        fun drawCodewords(data: ByteArray) {
            var i = 0
            var right = size - 1
            while (right >= 1) {
                if (right == 6) right = 5
                for (vert in 0 until size) for (j in 0..1) {
                    val x = right - j
                    val upward = ((right + 1) and 2) == 0
                    val y = if (upward) size - 1 - vert else vert
                    if (!isFunction[y][x] && i < data.size * 8) {
                        modules[y][x] = ((data[i ushr 3].toInt() ushr (7 - (i and 7))) and 1) == 1
                        i++
                    }
                }
                right -= 2
            }
        }

        fun applyMask(mask: Int) {
            for (y in 0 until size) for (x in 0 until size) {
                if (isFunction[y][x]) continue
                val invert = when (mask) {
                    0 -> (x + y) % 2 == 0
                    1 -> y % 2 == 0
                    2 -> x % 3 == 0
                    3 -> (x + y) % 3 == 0
                    4 -> (x / 3 + y / 2) % 2 == 0
                    5 -> x * y % 2 + x * y % 3 == 0
                    6 -> (x * y % 2 + x * y % 3) % 2 == 0
                    else -> ((x + y) % 2 + x * y % 3) % 2 == 0
                }
                if (invert) modules[y][x] = !modules[y][x]
            }
        }

        /** Standart dört ceza kuralı (yalnızca maske seçimi için). */
        fun penalty(): Int {
            var p = 0
            fun line(get: (Int) -> Boolean) {
                var run = 1
                for (i in 1 until size) {
                    if (get(i) == get(i - 1)) run++ else { if (run >= 5) p += run - 2; run = 1 }
                }
                if (run >= 5) p += run - 2
                for (i in 0..size - 7) {
                    val pat = (0 until 7).map { get(i + it) } == listOf(true, false, true, true, true, false, true)
                    if (pat) {
                        val before = (i - 4 until i).all { it < 0 || !get(it) }
                        val after = (i + 7 until i + 11).all { it >= size || !get(it) }
                        if (before || after) p += 40
                    }
                }
            }
            for (y in 0 until size) line { modules[y][it] }
            for (x in 0 until size) line { modules[it][x] }
            for (y in 0 until size - 1) for (x in 0 until size - 1) {
                val c = modules[y][x]
                if (c == modules[y][x + 1] && c == modules[y + 1][x] && c == modules[y + 1][x + 1]) p += 3
            }
            val dark = modules.sumOf { r -> r.count { it } }
            p += (kotlin.math.abs(dark * 20 - size * size * 10) / (size * size)) * 10
            return p
        }
    }
}

/** Wi-Fi QR metni (Android/iOS kamera uygulamalarının tanıdığı biçim). */
object WifiQr {
    enum class Security(val code: String) { WPA("WPA"), WEP("WEP"), NONE("nopass") }

    private fun esc(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:").replace("\"", "\\\"")

    fun text(ssid: String, password: String, security: Security, hidden: Boolean = false): String = buildString {
        append("WIFI:T:").append(security.code).append(";S:").append(esc(ssid)).append(';')
        if (security != Security.NONE) append("P:").append(esc(password)).append(';')
        if (hidden) append("H:true;")
        append(';')
    }
}
