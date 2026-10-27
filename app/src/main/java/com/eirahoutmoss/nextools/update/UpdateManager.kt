package com.eirahoutmoss.nextools.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.eirahoutmoss.nextools.BuildConfig
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.update.UnusableReason
import com.eirahoutmoss.nextools.core.integrity.Sha256
import com.eirahoutmoss.nextools.core.update.ReleaseSelector
import com.eirahoutmoss.nextools.core.update.SemVer
import com.eirahoutmoss.nextools.core.update.UpdateCandidate
import com.eirahoutmoss.nextools.core.update.UpdateDecision
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** Güncelleme akışının ekranda gösterilen durumu. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val candidate: UpdateCandidate) : UpdateState
    data class Downloading(val candidate: UpdateCandidate, val progress: Float) : UpdateState
    data object Verifying : UpdateState
    /** Sistem kurulum onayını gösteriyor; sonucu InstallResultReceiver bildirir. */
    data object Installing : UpdateState
    /** Kullanıcının "bilinmeyen kaynaklardan kurulum" iznini vermesi gerekiyor. */
    data class NeedsInstallPermission(val candidate: UpdateCandidate) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Düğmeyle tetiklenen GitHub güncellemesi.
 *
 * Güvenlik zinciri:
 *  1. Yalnızca kararlı (ön sürüm olmayan) ve daha yeni sürüm kabul edilir.
 *  2. APK'nın yanında <apk>.sha256 dosyası ZORUNLU; indirilen dosyanın özeti tutmazsa kurulmaz.
 *  3. APK'nın paket adı ve imza sertifikası çalışan uygulamayla aynı olmalı; değilse kurulmaz.
 *     (Android de farklı imzalı güncellemeyi reddeder; biz kullanıcıya erken ve anlaşılır söyleriz.)
 */
class UpdateManager(private val context: Context) {

    private val client = GitHubReleaseClient(
        context = context,
        owner = BuildConfig.UPDATE_REPO_OWNER,
        repo = BuildConfig.UPDATE_REPO_NAME,
        userAgent = "NexTools/${BuildConfig.VERSION_NAME} (Android)",
    )

    private fun str(id: Int, vararg args: Any): String = context.getString(id, *args)

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val updatesDir: File get() = File(context.cacheDir, "updates").apply { mkdirs() }

    val currentVersion: SemVer? = SemVer.parse(BuildConfig.VERSION_NAME.substringBefore("-debug"))

    fun reset() {
        _state.value = UpdateState.Idle
    }

    /**
     * İşler uygulama ömrüne bağlı kendi kapsamında çalışır: kullanıcı ekrandan çıksa bile
     * indirme yarıda kalıp durum "indiriliyor"da takılı kalmaz.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun check() {
        if (_state.value.isBusy()) return
        _state.value = UpdateState.Checking
        scope.launch { doCheck() }
    }

    fun downloadAndInstall(candidate: UpdateCandidate) {
        if (_state.value.isBusy()) return
        if (!canInstallPackages()) {
            _state.value = UpdateState.NeedsInstallPermission(candidate)
            return
        }
        _state.value = UpdateState.Downloading(candidate, 0f)
        scope.launch { doDownloadAndInstall(candidate) }
    }

    private suspend fun doCheck() {
        val current = currentVersion
        if (current == null) {
            _state.value = UpdateState.Failed(str(R.string.update_err_version_unreadable, BuildConfig.VERSION_NAME))
            return
        }
        _state.value = try {
            val release = client.latestRelease()
            if (release == null) {
                UpdateState.UpToDate
            } else {
                when (val d = ReleaseSelector.decide(current, release)) {
                    UpdateDecision.UpToDate -> UpdateState.UpToDate
                    is UpdateDecision.Available -> UpdateState.Available(d.candidate)
                    is UpdateDecision.Unusable -> UpdateState.Failed(unusableText(d.reason, d.detail))
                }
            }
        } catch (e: UpdateNetworkException) {
            UpdateState.Failed(e.message ?: str(R.string.update_err_check_generic, ""))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Kendi kapsamımızda yakalanmayan hata uygulamayı kapatırdı; burada mesaja çevrilir.
            UpdateState.Failed(str(R.string.update_err_check_generic, e.message ?: e.javaClass.simpleName))
        }
    }

    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** "Bilinmeyen uygulamaları yükle" ayarını bu uygulama için açan ekran. */
    fun installPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private suspend fun doDownloadAndInstall(candidate: UpdateCandidate) {
        try {
            withContext(Dispatchers.IO) { cleanOldDownloads() }
            val expected = ReleaseSelector.parseSha256File(client.downloadText(candidate.sha256Asset.downloadUrl))
                ?: throw UpdateNetworkException(str(R.string.update_err_checksum_unreadable))

            val apk = File(updatesDir, "NexTools-${candidate.version}.apk")
            client.downloadFile(candidate.apk.downloadUrl, apk, candidate.apk.sizeBytes) { p ->
                _state.value = UpdateState.Downloading(candidate, p)
            }

            _state.value = UpdateState.Verifying
            val actual = withContext(Dispatchers.IO) { apk.inputStream().use { Sha256.hex(it) } }
            if (actual != expected) {
                apk.delete()
                _state.value = UpdateState.Failed(str(R.string.update_err_hash_mismatch))
                return
            }
            withContext(Dispatchers.IO) { verifySameAppAndSigner(apk) }?.let { problem ->
                apk.delete()
                _state.value = UpdateState.Failed(problem)
                return
            }

            _state.value = UpdateState.Installing
            withContext(Dispatchers.IO) { commitInstall(apk) }
        } catch (e: UpdateNetworkException) {
            _state.value = UpdateState.Failed(e.message ?: str(R.string.update_err_interrupted))
        } catch (e: CancellationException) {
            _state.value = UpdateState.Idle
            throw e
        } catch (e: Exception) {
            _state.value = UpdateState.Failed(str(R.string.update_err_install, e.message ?: e.javaClass.simpleName))
        }
    }

    private fun unusableText(reason: UnusableReason, detail: String): String = when (reason) {
        UnusableReason.BAD_TAG -> str(R.string.update_err_bad_tag, detail)
        UnusableReason.NO_APK -> str(R.string.update_err_no_apk)
        UnusableReason.MULTIPLE_APKS -> str(R.string.update_err_multi_apk)
        UnusableReason.NO_CHECKSUM -> str(R.string.update_err_no_checksum, detail)
    }

    /** InstallResultReceiver'dan gelen sonuç. */
    internal fun onInstallResult(status: Int, message: String?) {
        _state.value = when (status) {
            PackageInstaller.STATUS_SUCCESS -> UpdateState.Idle // uygulama yeniden başlatılır
            PackageInstaller.STATUS_FAILURE_ABORTED -> UpdateState.Failed(str(R.string.install_aborted))
            PackageInstaller.STATUS_FAILURE_CONFLICT,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
            -> UpdateState.Failed(str(R.string.install_incompatible))
            PackageInstaller.STATUS_FAILURE_STORAGE -> UpdateState.Failed(str(R.string.install_storage))
            else -> UpdateState.Failed(if (message != null) str(R.string.install_failed, message) else str(R.string.install_failed_plain))
        }
    }

    private fun verifySameAppAndSigner(apk: File): String? {
        val pm = context.packageManager
        val flags = signingFlags()
        val archive = pm.getArchiveInfoCompat(apk.absolutePath, flags)
            ?: return str(R.string.update_err_not_apk)
        if (archive.packageName != context.packageName) {
            return str(R.string.update_err_wrong_package, archive.packageName)
        }
        val installed = pm.getPackageInfoCompat(context.packageName, flags)
        val a = archive.signerDigests()
        val b = installed.signerDigests()
        // Bazı eski cihazlar paket dosyasının imzasını okuyamaz; o durumda kararı Android'in
        // kendi imza denetimine bırakırız (farklı imzalı güncellemeyi sistem zaten reddeder).
        if (a.isNotEmpty() && b.isNotEmpty() && a != b) {
            return str(R.string.update_err_wrong_signer)
        }
        return null
    }

    private fun commitInstall(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        var committed = false
        val session = installer.openSession(sessionId)
        try {
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out, 64 * 1024) }
                session.fsync(out)
            }
            val intent = Intent(context, InstallResultReceiver::class.java).setAction(InstallResultReceiver.ACTION)
            var piFlags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) piFlags = piFlags or PendingIntent.FLAG_MUTABLE
            val pi = PendingIntent.getBroadcast(context, sessionId, intent, piFlags)
            session.commit(pi.intentSender)
            committed = true
        } finally {
            session.close()
            if (!committed) runCatching { installer.abandonSession(sessionId) }
        }
    }

    private fun cleanOldDownloads() {
        updatesDir.listFiles()?.forEach { it.delete() }
    }

    private fun UpdateState.isBusy(): Boolean =
        // Installing bilinçli olarak yok: kullanıcı onay ekranını geri tuşuyla kapatırsa
        // sistem bazen sonuç bildirmez; o durumda düğme kilitli kalmasın.
        this is UpdateState.Checking || this is UpdateState.Downloading || this is UpdateState.Verifying

    @Suppress("DEPRECATION")
    private fun signingFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES
        else PackageManager.GET_SIGNATURES

    @Suppress("DEPRECATION")
    private fun PackageInfo.signerDigests(): Set<String> {
        val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = signingInfo ?: return emptySet()
            if (info.hasMultipleSigners()) info.apkContentsSigners else info.signingCertificateHistory
        } else {
            signatures
        } ?: return emptySet()
        // Anahtar döndürme (rotation) geçmişinde herhangi bir ortak sertifika yeterli olmasın diye
        // tam küme karşılaştırılır; NexTools tek anahtarla imzalanır.
        return sigs.map { s ->
            MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    @Suppress("DEPRECATION")
    private fun PackageManager.getArchiveInfoCompat(path: String, flags: Int): PackageInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            getPackageArchiveInfo(path, flags)
        }

    @Suppress("DEPRECATION")
    private fun PackageManager.getPackageInfoCompat(name: String, flags: Int): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getPackageInfo(name, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            getPackageInfo(name, flags)
        }
}
