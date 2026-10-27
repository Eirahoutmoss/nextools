package com.eirahoutmoss.nextools.ui.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eirahoutmoss.nextools.BuildConfig
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolCatalog
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.measure.RunningStats
import com.eirahoutmoss.nextools.records.Column
import com.eirahoutmoss.nextools.records.GeoPoint
import com.eirahoutmoss.nextools.records.RecordDraft
import com.eirahoutmoss.nextools.records.RecordMeta
import com.eirahoutmoss.nextools.records.RecordStore
import com.eirahoutmoss.nextools.records.RecordingSession
import com.eirahoutmoss.nextools.records.SummaryItem
import com.eirahoutmoss.nextools.sensors.LocationHelper
import com.eirahoutmoss.nextools.ui.Fmt
import com.eirahoutmoss.nextools.ui.ToolUi
import com.eirahoutmoss.nextools.ui.appContainer
import com.eirahoutmoss.nextools.ui.theme.LocalInstrument
import com.eirahoutmoss.nextools.ui.theme.NumberStyles
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Kayda başlarken araç tarafından verilen bilgiler. */
class RecordingSpec(
    val columns: List<Column>,
    val headerComments: List<String>,
    val calibrated: Boolean,
    val calibrationNote: String,
    val sensorName: String,
    /** Varsayılan: her sütun için en küçük / ortalama / en büyük. */
    val summarize: (Map<String, RunningStats>, List<Column>) -> List<SummaryItem> = ::defaultSummary,
)

fun defaultSummary(stats: Map<String, RunningStats>, columns: List<Column>): List<SummaryItem> =
    columns.flatMap { c ->
        val s = stats[c.name] ?: return@flatMap emptyList()
        listOf(
            SummaryItem("min", c.name, s.min, c.unit, c.decimals),
            SummaryItem("avg", c.name, s.average, c.unit, c.decimals),
            SummaryItem("max", c.name, s.max, c.unit, c.decimals),
        )
    }

/** Bir araç ekranının kayıt durumu. Sensör akışı [add] ile besler. */
@Stable
class RecordingController(private val store: RecordStore, val tool: ToolId) {
    var session by mutableStateOf<RecordingSession?>(null)
        private set
    var spec: RecordingSpec? = null
        private set
    var draft by mutableStateOf<RecordDraft?>(null)
        private set

    val isRecording: Boolean get() = session != null

    fun start(spec: RecordingSpec) {
        if (session != null) return
        this.spec = spec
        session = store.newSession(tool, spec.columns, spec.headerComments)
    }

    fun add(values: DoubleArray) { session?.add(values) }

    fun stop() {
        val s = session ?: return
        session = null
        draft = store.finish(s)
    }

    fun discardDraft() {
        draft?.let { store.discard(it) }
        draft = null
    }

    /** Ekrandan çıkılırken: süren kayıt da taslak da atılır (kaydedilmemiş veri Defter'e girmez). */
    fun disposeAll() {
        session?.let { store.discard(it) }
        session = null
        discardDraft()
    }

    suspend fun save(title: String, note: String, tags: List<String>, location: GeoPoint?, deviceModel: String, androidVersion: String): RecordMeta? {
        val d = draft ?: return null
        val sp = spec ?: return null
        val meta = RecordMeta(
            id = d.session.id,
            tool = tool,
            title = title,
            note = note,
            tags = tags,
            startedAtMs = d.session.startedAtMs,
            durationMs = d.durationMs,
            sampleCount = d.sampleCount,
            columns = sp.columns,
            summary = sp.summarize(d.stats, sp.columns),
            calibrated = sp.calibrated,
            calibrationNote = sp.calibrationNote,
            deviceModel = deviceModel,
            androidVersion = androidVersion,
            sensorName = sp.sensorName,
            appVersion = BuildConfig.VERSION_NAME,
            location = location,
            photo = null,
            preview = d.preview,
        )
        val saved = store.save(d, meta)
        draft = null
        return saved
    }
}

@Composable
fun rememberRecording(tool: ToolId): RecordingController {
    val c = appContainer()
    val rc = remember(tool) { RecordingController(c.records, tool) }
    androidx.compose.runtime.DisposableEffect(rc) { onDispose { rc.disposeAll() } }
    return rc
}

/**
 * Ortak araç şablonu (taslak bölüm 5.2):
 *  üst — büyük canlı değer + birim + doğruluk rozeti (içerikte)
 *  orta — araca özel görsel
 *  alt — Kaydet ●, Dondur ❚❚, Kalibre et ⚙, Bilgi ⓘ
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolScaffold(
    tool: ToolId,
    onBack: () -> Unit,
    onOpenRecord: (String) -> Unit,
    recording: RecordingController?,
    onStartRecording: (() -> Unit)?,
    frozen: Boolean,
    onFreezeToggle: (() -> Unit)?,
    onCalibrate: (() -> Unit)?,
    infoExtra: @Composable () -> Unit = {},
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    scrollable: Boolean = true,
    content: @Composable () -> Unit,
) {
    val c = appContainer()
    val settings by c.settings.state.collectAsStateWithLifecycle()
    val fav = tool in settings.favorites
    val text = ToolUi.text(tool)
    var showInfo by rememberSaveable { mutableStateOf(false) }
    val isRecording = recording?.isRecording == true
    // Kayıt ve bildirim bu kapsamda: pencere kapanınca iş yarıda kesilmesin.
    val scaffoldScope = rememberCoroutineScope()
    KeepScreenOn(isRecording)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(text.name), maxLines = 1) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
                    actions = {
                        IconButton(onClick = { c.settings.toggleFavorite(tool) }) {
                            Icon(
                                if (fav) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                stringResource(if (fav) R.string.fav_remove else R.string.fav_add),
                                tint = if (fav) LocalInstrument.current.warn else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
                if (isRecording) RecordingStrip(recording!!)
            }
        },
        bottomBar = {
            ActionBar(
                recordable = recording != null && onStartRecording != null && ToolCatalog[tool].recordable,
                isRecording = isRecording,
                onRecord = { if (isRecording) recording?.stop() else onStartRecording?.invoke() },
                frozen = frozen,
                onFreeze = onFreezeToggle,
                onCalibrate = onCalibrate,
                onInfo = { showInfo = true },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val mod = Modifier.fillMaxSize().padding(padding)
        Column(
            (if (scrollable) mod.verticalScroll(rememberScrollState()) else mod).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (frozen) NoticeBanner(stringResource(R.string.frozen_notice), BadgeLevel.NEUTRAL)
            content()
            Spacer(Modifier.size(8.dp))
        }
    }

    if (showInfo) {
        InfoDialog(
            title = stringResource(text.name),
            body = {
                ToolUi.info(tool)?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
                Text(stringResource(R.string.info_limits_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(text.limits), style = MaterialTheme.typography.bodyMedium)
                infoExtra()
            },
            onDismiss = { showInfo = false },
        )
    }

    recording?.draft?.let { draft ->
        SaveRecordDialog(
            tool = tool,
            draft = draft,
            controller = recording,
            scope = scaffoldScope,
            onSaved = { meta ->
                scaffoldScope.launch {
                    val r = snackbar.showSnackbar(
                        c.appContext.getString(R.string.saved_to_notebook),
                        actionLabel = c.appContext.getString(R.string.action_open),
                        duration = SnackbarDuration.Short,
                    )
                    if (r == SnackbarResult.ActionPerformed) onOpenRecord(meta.id)
                }
            },
        )
    }
}

@Composable
private fun RecordingStrip(rc: RecordingController) {
    var elapsed by remember { mutableLongStateOf(0L) }
    var count by remember { mutableLongStateOf(0L) }
    LaunchedEffect(rc.session) {
        while (true) {
            val s = rc.session ?: break
            elapsed = s.elapsedMs; count = s.sampleCount
            delay(250)
        }
    }
    val red = LocalInstrument.current.alarm
    Surface(color = red.copy(alpha = 0.16f), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(red, androidx.compose.foundation.shape.CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.recording), color = red, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(12.dp))
            Text(Fmt.duration(elapsed), style = NumberStyles.Small, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.samples_count, count), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ActionBar(
    recordable: Boolean,
    isRecording: Boolean,
    onRecord: () -> Unit,
    frozen: Boolean,
    onFreeze: (() -> Unit)?,
    onCalibrate: (() -> Unit)?,
    onInfo: () -> Unit,
) {
    val inst = LocalInstrument.current
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            if (recordable) {
                ActionButton(
                    icon = if (isRecording) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                    label = stringResource(if (isRecording) R.string.action_stop else R.string.action_record),
                    tint = inst.alarm,
                    onClick = onRecord,
                )
            }
            if (onFreeze != null) {
                ActionButton(
                    icon = if (frozen) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    label = stringResource(if (frozen) R.string.action_resume else R.string.action_freeze),
                    onClick = onFreeze,
                )
            }
            if (onCalibrate != null) {
                ActionButton(Icons.Outlined.Tune, stringResource(R.string.action_calibrate), onClick = onCalibrate, enabled = !isRecording)
            }
            ActionButton(Icons.Outlined.Info, stringResource(R.string.action_info), onClick = onInfo)
        }
    }
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, onClick: () -> Unit, tint: Color = MaterialTheme.colorScheme.onSurface, enabled: Boolean = true) {
    TextButton(onClick = onClick, enabled = enabled, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = if (enabled) tint else tint.copy(alpha = 0.38f))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f))
        }
    }
}

/** Kayıt bitince: ad, not, etiket, isteğe bağlı konum. */
@Composable
private fun SaveRecordDialog(
    tool: ToolId,
    draft: RecordDraft,
    controller: RecordingController,
    scope: kotlinx.coroutines.CoroutineScope,
    onSaved: (RecordMeta) -> Unit,
) {
    val context = LocalContext.current
    val toolName = stringResource(ToolUi.text(tool).name)
    var title by rememberSaveable { mutableStateOf("$toolName · ${Fmt.shortDateTime(draft.session.startedAtMs)}") }
    var note by rememberSaveable { mutableStateOf("") }
    var tags by rememberSaveable { mutableStateOf("") }
    var withLocation by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        withLocation = r.values.any { it }
    }

    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.save_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.save_summary, Fmt.duration(draft.durationMs), draft.sampleCount),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(title, { title = it.take(120) }, label = { Text(stringResource(R.string.field_title)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it.take(2000) }, label = { Text(stringResource(R.string.field_note)) }, minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(tags, { tags = it.take(200) }, label = { Text(stringResource(R.string.field_tags)) }, placeholder = { Text(stringResource(R.string.field_tags_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.field_location), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.field_location_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = withLocation, onCheckedChange = { on ->
                        if (on && !LocationHelper.hasPermission(context)) {
                            permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                        } else withLocation = on
                    })
                }
                if (busy) CircularProgressIndicator(Modifier.size(24.dp))
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    val loc = if (withLocation) LocationHelper.current(context, timeoutMs = 8_000)?.let {
                        GeoPoint(it.latitude, it.longitude, if (it.hasAccuracy()) it.accuracy.toDouble() else null)
                    } else null
                    val tagList = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                    val meta = controller.save(title.trim(), note.trim(), tagList, loc, "${Build.MANUFACTURER} ${Build.MODEL}".trim(), Build.VERSION.RELEASE)
                    busy = false
                    if (meta != null) onSaved(meta)
                }
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = { controller.discardDraft() }) { Text(stringResource(R.string.action_discard)) }
        },
    )
}
