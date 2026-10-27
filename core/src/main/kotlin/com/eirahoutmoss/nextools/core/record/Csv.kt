package com.eirahoutmoss.nextools.core.record

import java.util.Locale

/**
 * Laboratuvar Defteri CSV biçimi.
 *  - Ayırıcı virgül, ondalık ayırıcı nokta (dilden bağımsız; Excel/LibreOffice/Python okur).
 *  - İlk sütun her zaman t_ms: kayıt başlangıcından geçen süre (milisaniye).
 *  - '#' ile başlayan baştaki satırlar açıklamadır (araç, cihaz, kalibrasyon).
 */
object Csv {

    fun escape(field: String): String {
        val needs = field.any { it == ',' || it == '"' || it == '\n' || it == '\r' } || field.startsWith(" ") || field.endsWith(" ")
        return if (needs) "\"" + field.replace("\"", "\"\"") + "\"" else field
    }

    fun header(columns: List<String>): String = columns.joinToString(",") { escape(it) }

    /** Sayıyı kısa ve dilden bağımsız yazar; NaN boş hücre olur. */
    fun number(v: Double, decimals: Int = 4): String {
        if (v.isNaN() || v.isInfinite()) return ""
        val s = String.format(Locale.ROOT, "%.${decimals}f", v)
        // Sondaki gereksiz sıfırları at: 12.3400 -> 12.34, 5.0000 -> 5
        return if (s.contains('.')) s.trimEnd('0').trimEnd('.').let { if (it == "-0") "0" else it } else s
    }

    fun row(tMs: Long, values: DoubleArray, decimals: Int = 4): String = buildString {
        append(tMs)
        for (v in values) { append(','); append(number(v, decimals)) }
    }

    fun comment(text: String): String = "# " + text.replace('\n', ' ').replace('\r', ' ')

    /** Okuma: açıklama satırlarını atlar, başlığı ve sayısal satırları döner. Bozuk satırlar atlanır. */
    data class Table(val columns: List<String>, val rows: List<DoubleArray>)

    fun parse(lines: Sequence<String>, maxRows: Int = Int.MAX_VALUE): Table {
        var columns: List<String>? = null
        val rows = ArrayList<DoubleArray>()
        for (line in lines) {
            if (line.isBlank() || line.startsWith("#")) continue
            if (columns == null) {
                columns = splitLine(line)
                continue
            }
            if (rows.size >= maxRows) break
            val parts = line.split(',')
            if (parts.size != columns.size) continue
            rows += DoubleArray(parts.size) { i -> parts[i].toDoubleOrNull() ?: Double.NaN }
        }
        return Table(columns ?: emptyList(), rows)
    }

    private fun splitLine(line: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                quoted && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { out += sb.toString(); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        out += sb.toString()
        return out
    }
}

/** Grafik için seyreltme: her kovada en küçük ve en büyük değer korunur (tepeler kaybolmaz). */
object Downsample {
    fun minMax(x: DoubleArray, y: DoubleArray, maxPoints: Int): Pair<DoubleArray, DoubleArray> {
        require(x.size == y.size)
        if (x.size <= maxPoints || maxPoints < 4) return x to y
        val buckets = maxPoints / 2
        val per = x.size.toDouble() / buckets
        val ox = ArrayList<Double>(maxPoints)
        val oy = ArrayList<Double>(maxPoints)
        for (b in 0 until buckets) {
            val from = (b * per).toInt()
            val to = minOf(x.size, ((b + 1) * per).toInt())
            if (from >= to) continue
            var iMin = from; var iMax = from
            for (i in from until to) {
                if (y[i].isNaN()) continue
                if (y[iMin].isNaN() || y[i] < y[iMin]) iMin = i
                if (y[iMax].isNaN() || y[i] > y[iMax]) iMax = i
            }
            val first = minOf(iMin, iMax); val second = maxOf(iMin, iMax)
            ox += x[first]; oy += y[first]
            if (second != first) { ox += x[second]; oy += y[second] }
        }
        return ox.toDoubleArray() to oy.toDoubleArray()
    }
}
