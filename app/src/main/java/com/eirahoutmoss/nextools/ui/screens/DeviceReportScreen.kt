package com.eirahoutmoss.nextools.ui.screens

import android.hardware.SensorManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.Hardware
import com.eirahoutmoss.nextools.core.catalog.Presence
import com.eirahoutmoss.nextools.core.measure.Noise
import com.eirahoutmoss.nextools.sensors.SensorStream
import com.eirahoutmoss.nextools.ui.Fmt
import com.eirahoutmoss.nextools.ui.ToolUi
import com.eirahoutmoss.nextools.ui.appContainer
import com.eirahoutmoss.nextools.ui.components.InfoDialog
import com.eirahoutmoss.nextools.ui.components.SectionCard
import com.eirahoutmoss.nextools.ui.components.StatusDot
import com.eirahoutmoss.nextools.ui.theme.LocalInstrument
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Cihaz Karnesi: her donanım için yeşil / gri nokta, üretici, en yüksek hız, çözünürlük, gürültü; PDF. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceReportScreen(onBack: () -> Unit) {
    val c = appContainer()
    val context = LocalContext.current
    val probe = c.hardware
    val inst = LocalInstrument.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val noise = remember { mutableStateMapOf<Hardware, Double>() }
    var measuring by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var busyPdf by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    val summary = probe.summary

    fun measureNoise() {
        measuring = true
        progress = 0f
        scope.launch {
            val targets = listOf(Hardware.ACCELEROMETER, Hardware.GYROSCOPE, Hardware.MAGNETOMETER, Hardware.BAROMETER)
                .mapNotNull { h -> probe.sensorFor(h)?.let { h to it } }
            targets.forEachIndexed { i, (h, sensor) ->
                val samples = ArrayList<DoubleArray>()
                withTimeoutOrNull(3_000) {
                    SensorStream.samples(context, sensor, SensorManager.SENSOR_DELAY_GAME).collect { s ->
                        samples += DoubleArray(s.values.size.coerceAtMost(3)) { k -> s.values[k].toDouble() }
                    }
                }
                Noise.rms(samples)?.let { noise[h] = it }
                progress = (i + 1f) / targets.size
            }
            measuring = false
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tool_device_report_name)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
                actions = {
                    IconButton(enabled = !busyPdf, onClick = {
                        busyPdf = true
                        scope.launch {
                            try {
                                val f = c.exporter.deviceReport(probe, noise.toMap())
                                context.startActivity(c.exporter.shareIntent(f, "application/pdf", context.getString(R.string.tool_device_report_name)))
                            } catch (e: Exception) {
                                snackbar.showSnackbar(context.getString(R.string.export_failed, e.message ?: e.javaClass.simpleName))
                            } finally { busyPdf = false }
                        }
                    }) { Icon(Icons.Outlined.PictureAsPdf, stringResource(R.string.report_share_pdf)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionCard(null) {
                Text("${Build.MANUFACTURER} ${Build.MODEL}".trim(), style = MaterialTheme.typography.titleLarge)
                Text("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.report_summary_line, summary.hardwareOk, summary.total), style = MaterialTheme.typography.titleSmall, color = inst.accent)
                Text(stringResource(R.string.report_ready_line, summary.usableNow), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (busyPdf) LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            SectionCard(stringResource(R.string.report_noise_title)) {
                Text(stringResource(R.string.report_noise_hint), style = MaterialTheme.typography.bodySmall)
                if (measuring) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { measureNoise() }) { Text(stringResource(R.string.report_noise_measure)) }
                        OutlinedButton(onClick = { info = true }) { Text(stringResource(R.string.action_info)) }
                    }
                }
            }

            SectionCard(stringResource(R.string.pdf_hardware)) {
                probe.reportOrder.forEachIndexed { i, h ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    val presence = probe.capabilities.presenceOf(h)
                    val sensor = probe.sensorFor(h)?.let { probe.detail(it) }
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                        Spacer(Modifier.size(2.dp))
                        Column(Modifier.padding(top = 6.dp)) {
                            StatusDot(
                                when (presence) {
                                    Presence.PRESENT -> inst.good
                                    Presence.ABSENT -> inst.passive
                                    Presence.UNKNOWN -> inst.warn
                                },
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(ToolUi.hardware(h)), style = MaterialTheme.typography.bodyLarge)
                            val stateText = when (presence) {
                                Presence.PRESENT -> stringResource(R.string.report_present)
                                Presence.ABSENT -> stringResource(R.string.report_absent)
                                Presence.UNKNOWN -> stringResource(R.string.report_unknown)
                            }
                            Text(stateText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (sensor != null) {
                                Text("${sensor.vendor} · ${sensor.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                val parts = buildList {
                                    sensor.maxRateHz?.let { add(stringResource(R.string.report_rate, Fmt.num(it, 0))) }
                                    add(stringResource(R.string.report_resolution, Fmt.num(sensor.resolution.toDouble(), 4)))
                                    noise[h]?.let { add(stringResource(R.string.report_noise, Fmt.num(it, 4))) }
                                }
                                Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            if (measuring) Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp)); Text("  " + stringResource(R.string.report_noise_running)) }
        }
    }
    if (info) InfoDialog(stringResource(R.string.tool_device_report_name), { Text(stringResource(R.string.tool_device_report_info)) }) { info = false }
}
