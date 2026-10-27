package com.eirahoutmoss.nextools.ui

import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Ekrandaki sayılar telefonun diline göre (Türkçede ondalık virgül). CSV her zaman noktalıdır. */
object Fmt {
    private fun locale(): Locale = Locale.getDefault()

    fun num(v: Double, decimals: Int): String {
        if (v.isNaN() || v.isInfinite()) return "—"
        val nf = NumberFormat.getNumberInstance(locale())
        nf.minimumFractionDigits = decimals
        nf.maximumFractionDigits = decimals
        nf.isGroupingUsed = true
        val s = nf.format(v)
        // "-0,0" yerine "0,0"
        return if (s.startsWith("-") && s.drop(1).all { it == '0' || !it.isDigit() }) s.drop(1) else s
    }

    fun signed(v: Double, decimals: Int): String =
        if (v.isNaN()) "—" else (if (v > 0) "+" else "") + num(v, decimals)

    /** 00:02:15 biçiminde süre. */
    fun duration(ms: Long): String {
        val s = ms / 1000
        return "%02d:%02d:%02d".format(Locale.ROOT, s / 3600, (s % 3600) / 60, s % 60)
    }

    fun dateTime(ms: Long): String =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale())
            .format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

    fun date(ms: Long): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale())
            .format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

    fun shortDateTime(ms: Long): String =
        DateTimeFormatter.ofPattern("d MMM HH:mm", locale()).format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

    /** Dosya adı için: 20261026-1403 */
    fun fileStamp(ms: Long): String =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmm", Locale.ROOT).format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

    /** Kullanıcının yazdığı sayıyı virgül ya da noktayla kabul eder. */
    fun parse(text: String): Double? = text.trim().replace(" ", "").replace(',', '.').toDoubleOrNull()
}
