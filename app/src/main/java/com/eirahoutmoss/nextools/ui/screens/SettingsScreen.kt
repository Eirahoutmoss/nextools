package com.eirahoutmoss.nextools.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eirahoutmoss.nextools.BuildConfig
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.units.PressureUnit
import com.eirahoutmoss.nextools.core.units.UnitSystem
import com.eirahoutmoss.nextools.core.update.UpdateCandidate
import com.eirahoutmoss.nextools.diagnostics.CrashLogger
import com.eirahoutmoss.nextools.settings.CalibrationStore
import com.eirahoutmoss.nextools.settings.ThemeMode
import com.eirahoutmoss.nextools.ui.ToolUi
import com.eirahoutmoss.nextools.ui.appContainer
import com.eirahoutmoss.nextools.ui.components.ConfirmDialog
import com.eirahoutmoss.nextools.ui.components.NexFamily
import com.eirahoutmoss.nextools.ui.components.NexToolsLogo
import com.eirahoutmoss.nextools.ui.components.SectionCard
import com.eirahoutmoss.nextools.ui.components.StatusDot
import com.eirahoutmoss.nextools.ui.components.Wordmark
import com.eirahoutmoss.nextools.ui.theme.LocalInstrument
import com.eirahoutmoss.nextools.update.UpdateState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onOpenTool: (ToolId) -> Unit) {
    val c = appContainer()
    val context = LocalContext.current
    val settings by c.settings.state.collectAsStateWithLifecycle()
    val cal by c.calibrations.state.collectAsStateWithLifecycle()
    val update by c.updateManager.state.collectAsStateWithLifecycle()
    var hasCrashLog by remember { mutableStateOf(CrashLogger.hasLog(context)) }
    var resetFor by remember { mutableStateOf<ToolId?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_settings)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ---------- Görünüm ----------
            SectionCard(stringResource(R.string.settings_appearance)) {
                Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.labelLarge)
                Segmented(
                    options = listOf(ThemeMode.DARK to R.string.theme_dark, ThemeMode.LIGHT to R.string.theme_light, ThemeMode.SYSTEM to R.string.theme_system),
                    selected = settings.theme, onSelect = { c.settings.setTheme(it) },
                )
                Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.labelLarge)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Text(stringResource(R.string.settings_language_hint_33), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = {
                        runCatching {
                            context.startActivity(Intent(AndroidSettings.ACTION_APP_LOCALE_SETTINGS, Uri.parse("package:${context.packageName}")))
                        }
                    }) { Text(stringResource(R.string.settings_language_open)) }
                } else {
                    Text(stringResource(R.string.settings_language_hint_old), style = MaterialTheme.typography.bodySmall)
                }
            }

            // ---------- Birimler ----------
            SectionCard(stringResource(R.string.settings_units)) {
                Segmented(
                    options = listOf(UnitSystem.METRIC to R.string.units_metric, UnitSystem.IMPERIAL to R.string.units_imperial),
                    selected = settings.units, onSelect = { c.settings.setUnits(it) },
                )
                Text(stringResource(R.string.settings_pressure_unit), style = MaterialTheme.typography.labelLarge)
                Segmented(
                    options = PressureUnit.entries.map { it to null },
                    labels = PressureUnit.entries.associateWith { it.symbol },
                    selected = settings.pressureUnit, onSelect = { c.settings.setPressureUnit(it) },
                )
            }

            // ---------- Kalibrasyonlar ----------
            SectionCard(stringResource(R.string.settings_calibrations)) {
                Text(stringResource(R.string.settings_calibrations_hint), style = MaterialTheme.typography.bodySmall)
                CalibrationStore.CALIBRATABLE.forEach { tool ->
                    val ok = cal.isCalibrated(tool)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        StatusDot(if (ok) LocalInstrument.current.good else LocalInstrument.current.passive)
                        Spacer(Modifier.size(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(ToolUi.text(tool).name), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(if (ok) R.string.cal_state_set else R.string.cal_state_default),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { onOpenTool(tool) }) { Text(stringResource(R.string.action_open)) }
                        if (ok) TextButton(onClick = { resetFor = tool }) { Text(stringResource(R.string.action_reset)) }
                    }
                }
            }

            // ---------- İzinler ----------
            SectionCard(stringResource(R.string.settings_permissions)) {
                PermissionRow(stringResource(R.string.perm_microphone), stringResource(R.string.perm_microphone_why), Manifest.permission.RECORD_AUDIO)
                PermissionRow(stringResource(R.string.perm_location), stringResource(R.string.perm_location_why), Manifest.permission.ACCESS_COARSE_LOCATION)
                OutlinedButton(onClick = {
                    context.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                }) { Text(stringResource(R.string.perm_open_settings)) }
            }

            // ---------- Cihaz Karnesi ----------
            SectionCard(stringResource(R.string.tool_device_report_name)) {
                Text(stringResource(R.string.tool_device_report_desc), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { onOpenTool(ToolId.DEVICE_REPORT) }) { Text(stringResource(R.string.action_open)) }
            }

            // ---------- Güncelleme (Nex standardı) ----------
            SectionCard(stringResource(R.string.settings_update)) {
                Text(stringResource(R.string.update_installed, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
                UpdateBody(
                    state = update,
                    onCheck = { c.updateManager.check() },
                    onInstall = { c.updateManager.downloadAndInstall(it) },
                    onOpenPermission = { context.startActivity(c.updateManager.installPermissionIntent()) },
                )
                Text(stringResource(R.string.update_security_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // ---------- Hata kaydı ----------
            SectionCard(stringResource(R.string.settings_crash_log)) {
                Text(
                    stringResource(if (hasCrashLog) R.string.crash_log_present else R.string.crash_log_none),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (hasCrashLog) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            val f = CrashLogger.file(context)
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                                .putExtra(Intent.EXTRA_STREAM, uri)
                                .putExtra(Intent.EXTRA_SUBJECT, "NexTools crash log ${BuildConfig.VERSION_NAME}")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            context.startActivity(Intent.createChooser(send, context.getString(R.string.share_via)))
                        }) { Text(stringResource(R.string.action_share)) }
                        TextButton(onClick = { CrashLogger.clear(context); hasCrashLog = false }) { Text(stringResource(R.string.action_clear)) }
                    }
                }
            }

            // ---------- Hakkında ----------
            SectionCard(stringResource(R.string.settings_about)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NexToolsLogo(52.dp)
                    Column(Modifier.padding(start = 12.dp)) {
                        Wordmark(24.sp)
                        Text(stringResource(R.string.tagline), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.about_free), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.about_privacy), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.about_licenses), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.about_author), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.nex_family), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
                NexFamily()
            }
        }
    }

    resetFor?.let { tool ->
        ConfirmDialog(
            title = stringResource(R.string.cal_reset_title),
            message = stringResource(R.string.cal_reset_message, stringResource(ToolUi.text(tool).name)),
            confirm = stringResource(R.string.action_reset),
            onConfirm = { c.calibrations.reset(tool) },
            onDismiss = { resetFor = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Segmented(options: List<Pair<T, Int?>>, selected: T, onSelect: (T) -> Unit, labels: Map<T, String> = emptyMap()) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
            ) { Text(label?.let { stringResource(it) } ?: labels[value].orEmpty(), maxLines = 1) }
        }
    }
}

@Composable
private fun PermissionRow(name: String, why: String, permission: String) {
    val context = LocalContext.current
    val granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    val inst = LocalInstrument.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusDot(if (granted) inst.good else inst.passive)
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(name + " · " + stringResource(if (granted) R.string.perm_granted else R.string.perm_not_granted), style = MaterialTheme.typography.bodyMedium)
            Text(why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun UpdateBody(
    state: UpdateState,
    onCheck: () -> Unit,
    onInstall: (UpdateCandidate) -> Unit,
    onOpenPermission: () -> Unit,
) {
    when (state) {
        UpdateState.Idle -> CheckButton(onCheck)
        UpdateState.Checking -> {
            Text(stringResource(R.string.update_checking), style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        UpdateState.UpToDate -> {
            Text(stringResource(R.string.update_up_to_date), style = MaterialTheme.typography.bodySmall)
            CheckButton(onCheck)
        }
        is UpdateState.Available -> {
            Text(stringResource(R.string.update_available, state.candidate.version.toString()), style = MaterialTheme.typography.bodyMedium)
            if (state.candidate.notes.isNotBlank()) Text(state.candidate.notes.take(600), style = MaterialTheme.typography.bodySmall)
            Button(onClick = { onInstall(state.candidate) }) { Text(stringResource(R.string.update_install)) }
        }
        is UpdateState.Downloading -> {
            Text(stringResource(R.string.update_downloading, (state.progress * 100).toInt()), style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
        }
        UpdateState.Verifying -> {
            Text(stringResource(R.string.update_verifying), style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        UpdateState.Installing -> Text(stringResource(R.string.update_installing), style = MaterialTheme.typography.bodySmall)
        is UpdateState.NeedsInstallPermission -> {
            Text(stringResource(R.string.update_needs_permission), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenPermission) { Text(stringResource(R.string.update_open_permission)) }
                OutlinedButton(onClick = { onInstall(state.candidate) }) { Text(stringResource(R.string.action_retry)) }
            }
        }
        is UpdateState.Failed -> {
            Text(state.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            CheckButton(onCheck)
        }
    }
}

@Composable
private fun CheckButton(onCheck: () -> Unit) {
    Button(onClick = onCheck) {
        Icon(Icons.Outlined.SystemUpdate, contentDescription = null)
        Spacer(Modifier.size(8.dp))
        Text(stringResource(R.string.update_check))
    }
}
