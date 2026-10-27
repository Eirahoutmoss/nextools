package com.eirahoutmoss.nextools.idcard

import android.graphics.Bitmap

/** Okuma adımları (ekranda ilerleme için). */
enum class IdStep { CONNECTING, ACCESS, READING_DATA, READING_PHOTO, VERIFYING }

/** Hata kodları: ekranda anlaşılır metne çevrilir. */
enum class IdError { ACCESS_DENIED, TAG_LOST, NOT_EMRTD, UNKNOWN }

class IdCardException(val error: IdError, cause: Throwable? = null) : Exception(error.name, cause)

/** Pasif doğrulama: çipteki veri gruplarının özetleri ve belge imzalayıcısının imzası. */
data class PassiveAuth(
    /** Veri grubu → özet uyuşuyor mu. */
    val groups: Map<Int, Boolean>,
    /** SOD imzası belge imzalayıcı sertifikasıyla doğrulandı mı (null = denetlenemedi). */
    val signatureOk: Boolean?,
    /** Belge imzalayıcı sertifikasının konusu (ör. "C=TR, O=..., CN=..."). */
    val signer: String?,
    val signerValidUntilMs: Long?,
) {
    val hashesOk: Boolean get() = groups.isNotEmpty() && groups.values.all { it }
}

/** Kimlik kartı / pasaport çipinden okunan bilgiler. Hiçbir yere yazılmaz. */
data class IdCardData(
    val accessMethod: String,
    val documentCode: String,
    val documentNumber: String,
    val issuingState: String,
    val surname: String,
    val givenNames: String,
    val nationality: String,
    /** yyMMdd */
    val birthDate: String,
    val expiryDate: String,
    val gender: String,
    val personalNumber: String?,
    val photo: Bitmap?,
    val photoMime: String?,
    val photoBytes: Int,
    val passive: PassiveAuth,
)

/** "GG.AA.YYYY" ya da "GGAAYYYY" girişini ICAO biçimine (yyMMdd) çevirir; geçersizse null. */
fun toIcaoDate(input: String): String? {
    val d = input.filter { it.isDigit() }
    if (d.length != 8) return null
    val day = d.substring(0, 2).toInt(); val mon = d.substring(2, 4).toInt(); val year = d.substring(4, 8).toInt()
    if (day !in 1..31 || mon !in 1..12 || year !in 1900..2100) return null
    return "%02d%02d%02d".format(year % 100, mon, day)
}

/** ICAO tarihini (yyMMdd) okunur biçime çevirir; yüzyıl, [future] ise ileri, değilse geri yorumlanır. */
fun fromIcaoDate(s: String, future: Boolean): String {
    if (s.length != 6 || !s.all { it.isDigit() }) return s
    val yy = s.substring(0, 2).toInt()
    val nowYy = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) % 100
    val century = if (future) (if (yy < nowYy - 10) 2100 else 2000) else (if (yy > nowYy) 1900 else 2000)
    return "${s.substring(4, 6)}.${s.substring(2, 4)}.${century + yy}"
}
