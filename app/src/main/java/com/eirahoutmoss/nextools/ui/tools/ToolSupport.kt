package com.eirahoutmoss.nextools.ui.tools

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.eirahoutmoss.nextools.BuildConfig
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.sensors.SensorEventItem
import com.eirahoutmoss.nextools.sensors.SensorStream
import com.eirahoutmoss.nextools.ui.ToolUi
import com.eirahoutmoss.nextools.ui.components.KeyValue
import com.eirahoutmoss.nextools.ui.components.SectionCard
import java.time.Instant

/**
 * Sensörleri yalnızca ekran görünürken dinler (uygulama arka plana geçince durur).
 * [onEvent] ana iş parçacığında çağrılır.
 */
@Composable
fun CollectSensors(sensors: List<Sensor>, delayUs: Int, key: Any? = Unit, onEvent: (SensorEventItem) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val handler by rememberUpdatedState(onEvent)
    LaunchedEffect(sensors, delayUs, key) {
        if (sensors.isEmpty()) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            SensorStream.events(context, sensors, delayUs).collect { handler(it) }
        }
    }
}

/** Ekran güncellemesini seyreltir (sensör 200 Hz gelse de ekran ~25 Hz yeter). */
class Throttle(private val periodNs: Long) {
    private var last = 0L
    fun ready(nowNs: Long): Boolean {
        if (nowNs - last < periodNs) return false
        last = nowNs
        return true
    }
}

/** Uygulama ön planda mı (ör. mikrofon yalnızca görünürken açık kalsın). */
@Composable
fun rememberIsStarted(): Boolean {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var started by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, _ -> started = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    return started
}

fun Context.hasPermission(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

/** İzin yoksa açıklama + düğme gösterir; varsa içeriği. */
@Composable
fun PermissionGate(permission: String, rationale: String, content: @Composable () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasPermission(permission)) }
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok; denied = !ok
    }
    if (granted) {
        content()
    } else {
        SectionCard(null) {
            Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(rationale, style = MaterialTheme.typography.bodyMedium)
                if (denied) Text(stringResource(R.string.perm_denied_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                Button(onClick = { launcher.launch(permission) }) { Text(stringResource(R.string.perm_grant)) }
            }
        }
    }
}

/** Birden çok izin (ör. konum + yakındaki cihazlar). [required] dışındakiler reddedilse de devam edilir. */
@Composable
fun PermissionsGate(permissions: List<String>, rationale: String, required: List<String> = permissions, content: @Composable () -> Unit) {
    val context = LocalContext.current
    fun check() = required.all { context.hasPermission(it) }
    var granted by remember { mutableStateOf(check()) }
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = check(); denied = !granted
    }
    if (granted) {
        content()
    } else {
        SectionCard(null) {
            Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(rationale, style = MaterialTheme.typography.bodyMedium)
                if (denied) Text(stringResource(R.string.perm_denied_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                Button(onClick = { launcher.launch(permissions.toTypedArray()) }) { Text(stringResource(R.string.perm_grant)) }
            }
        }
    }
}

val LOCATION_PERMISSIONS = listOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION)

/** CSV başındaki açıklama satırları (dilden bağımsız anahtarlar). */
fun csvHeader(context: Context, tool: ToolId, sensor: String, calibration: String, extra: List<String> = emptyList()): List<String> = listOf(
    "NexTools ${BuildConfig.VERSION_NAME} — ${context.getString(ToolUi.text(tool).name)}",
    "tool=${tool.key}",
    "started=${Instant.now()}",
    "device=${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE}",
    "sensor=$sensor",
    "calibration=$calibration",
    "t_ms=ms since start",
) + extra

/** Bilgi penceresinde kullanılan donanımı gösterir. */
@Composable
fun SensorInfo(sensor: Sensor?) {
    if (sensor == null) return
    KeyValue(stringResource(R.string.info_sensor), "${sensor.vendor} · ${sensor.name}")
    if (sensor.minDelay > 0) KeyValue(stringResource(R.string.info_max_rate), "${1_000_000 / sensor.minDelay} Hz")
    KeyValue(stringResource(R.string.info_resolution), sensor.resolution.toString())
}
