package com.eirahoutmoss.nextools.core.update

/** GitHub sürümündeki bir dosya. */
data class ReleaseAsset(val name: String, val downloadUrl: String, val sizeBytes: Long)

/** GitHub "latest release" yanıtının bize gereken kısmı. */
data class ReleaseInfo(
    val tagName: String,
    val title: String,
    val notes: String,
    val isPreRelease: Boolean,
    val isDraft: Boolean,
    val assets: List<ReleaseAsset>,
)

/** Kurulabilir bir güncelleme: APK ve (zorunlu) SHA-256 özeti. */
data class UpdateCandidate(
    val version: SemVer,
    val apk: ReleaseAsset,
    val sha256Asset: ReleaseAsset,
    val notes: String,
)

/** Sürüm var ama kurulamıyor; metin uygulamada dil dosyasından seçilir. */
enum class UnusableReason { BAD_TAG, NO_APK, MULTIPLE_APKS, NO_CHECKSUM }

sealed interface UpdateDecision {
    data object UpToDate : UpdateDecision
    data class Available(val candidate: UpdateCandidate) : UpdateDecision
    data class Unusable(val reason: UnusableReason, val detail: String) : UpdateDecision
}

/** Nex standardı (NexYapı ile aynı kurallar). */
object ReleaseSelector {

    /**
     * Kurallar:
     * - Taslak ve ön sürümler yok sayılır.
     * - Tam olarak bir .apk dosyası olmalı.
     * - Yanında "<apk adı>.sha256" dosyası olmalı; yoksa güncelleme KURULMAZ.
     */
    fun decide(current: SemVer, release: ReleaseInfo): UpdateDecision {
        if (release.isDraft || release.isPreRelease) return UpdateDecision.UpToDate
        val remote = SemVer.parse(release.tagName)
            ?: return UpdateDecision.Unusable(UnusableReason.BAD_TAG, release.tagName)
        if (remote.isPreRelease || remote <= current) return UpdateDecision.UpToDate

        val apks = release.assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        val apk = when (apks.size) {
            0 -> return UpdateDecision.Unusable(UnusableReason.NO_APK, "")
            1 -> apks.single()
            else -> return UpdateDecision.Unusable(UnusableReason.MULTIPLE_APKS, apks.joinToString { it.name })
        }
        val sha = release.assets.firstOrNull { it.name.equals(apk.name + ".sha256", ignoreCase = true) }
            ?: return UpdateDecision.Unusable(UnusableReason.NO_CHECKSUM, apk.name + ".sha256")
        return UpdateDecision.Available(UpdateCandidate(remote, apk, sha, release.notes))
    }

    private val HEX64 = Regex("""\b([0-9a-fA-F]{64})\b""")

    /** "sha256sum" çıktısını ya da yalnızca özeti kabul eder; küçük harfli hex döner. */
    fun parseSha256File(content: String): String? =
        HEX64.find(content)?.groupValues?.get(1)?.lowercase()
}
