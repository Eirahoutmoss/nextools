package com.eirahoutmoss.nextools.ui.screens

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.record.Csv
import com.eirahoutmoss.nextools.records.RecordMeta
import com.eirahoutmoss.nextools.ui.Fmt
import com.eirahoutmoss.nextools.ui.ToolUi
import com.eirahoutmoss.nextools.ui.appContainer
import com.eirahoutmoss.nextools.ui.components.AccuracyBadge
import com.eirahoutmoss.nextools.ui.components.BadgeLevel
import com.eirahoutmoss.nextools.ui.components.ConfirmDialog
import com.eirahoutmoss.nextools.ui.components.KeyValue
import com.eirahoutmoss.nextools.ui.components.RecordChart
import com.eirahoutmoss.nextools.ui.components.SectionCard
import com.eirahoutmoss.nextools.ui.theme.LocalInstrument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordDetailScreen(id: String, onBack: () -> Unit, onCompare: (String, String) -> Unit) {
    val c = appContainer()
    val context = LocalContext.current
    val records by c.records.records.collectAsStateWithLifecycle()
    val meta = records.firstOrNull { it.id == id }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { c.records.ensureLoaded() }
    if (meta == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val toolName = stringResource(ToolUi.text(meta.tool).name)
    val table by produceState<Csv.Table?>(null, meta.id) { value = c.records.readTable(meta.id) }

    fun export(kind: String) {
        busy = true
        scope.launch {
            try {
                val (file, mime) = when (kind) {
                    "csv" -> c.exporter.csv(meta) to "text/csv"
                    "json" -> c.exporter.json(meta) to "application/json"
                    "gpx" -> c.exporter.gpx(meta) to "application/gpx+xml"
                    else -> c.exporter.pdf(meta) to "application/pdf"
                }
                context.startActivity(c.exporter.shareIntent(file, mime, "NexTools · ${meta.title}"))
            } catch (e: Exception) {
                snackbar.showSnackbar(context.getString(R.string.export_failed, e.message ?: e.javaClass.simpleName))
            } finally { busy = false }
        }
    }

    // Fotoğraf: kamera (izin gerektirmeyen sistem kamerası) ya da galeri.
    val captureFile = remember { File(File(context.cacheDir, "capture").apply { mkdirs() }, "photo.jpg") }
    val captureUri: Uri = remember { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", captureFile) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) scope.launch { runCatching { c.records.attachPhoto(meta, captureUri) }.onFailure { snackbar.showSnackbar(context.getString(R.string.photo_failed)) } }
    }
    // Uygulama CAMERA iznini bildirdiğinden, sistem kamerası çağrısı da bu izni gerektirir.
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) takePicture.launch(captureUri)
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { runCatching { c.records.attachPhoto(meta, uri) }.onFailure { snackbar.showSnackbar(context.getString(R.string.photo_failed)) } }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(meta.title.ifBlank { toolName }, maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
                actions = {
                    IconButton(onClick = { editing = true }) { Icon(Icons.Outlined.Edit, stringResource(R.string.action_edit)) }
                    Box {
                        IconButton(onClick = { menu = true }, enabled = !busy) { Icon(Icons.Outlined.Share, stringResource(R.string.action_export)) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.export_pdf)) }, onClick = { menu = false; export("pdf") })
                            DropdownMenuItem(text = { Text(stringResource(R.string.export_csv)) }, onClick = { menu = false; export("csv") })
                            DropdownMenuItem(text = { Text(stringResource(R.string.export_json)) }, onClick = { menu = false; export("json") })
                            if (c.exporter.hasGeo(meta)) DropdownMenuItem(text = { Text(stringResource(R.string.export_gpx)) }, onClick = { menu = false; export("gpx") })
                        }
                    }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete)) }
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
            Text("$toolName · ${Fmt.dateTime(meta.startedAtMs)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AccuracyBadge(
                    stringResource(if (meta.calibrated) R.string.badge_calibrated else R.string.badge_not_calibrated),
                    if (meta.calibrated) BadgeLevel.GOOD else BadgeLevel.APPROX,
                )
                Text(
                    stringResource(R.string.record_meta_line, Fmt.duration(meta.durationMs), meta.sampleCount),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (busy) Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.padding(end = 8.dp)); Text(stringResource(R.string.exporting)) }

            ChartSection(meta, table)

            SectionCard(stringResource(R.string.pdf_summary)) {
                meta.summary.forEach { s ->
                    KeyValue(
                        stringResource(ToolUi.summaryKind(s.kind)) + " · " + ToolUi.columnText(s.column),
                        (Fmt.num(s.value, s.decimals) + " " + s.unit).trim(),
                    )
                }
            }

            if (meta.note.isNotBlank() || meta.tags.isNotEmpty()) {
                SectionCard(stringResource(R.string.pdf_note)) {
                    if (meta.note.isNotBlank()) Text(meta.note, style = MaterialTheme.typography.bodyMedium)
                    if (meta.tags.isNotEmpty()) Text(meta.tags.joinToString("  ") { "#$it" }, color = LocalInstrument.current.accent, style = MaterialTheme.typography.labelLarge)
                }
            }

            SectionCard(stringResource(R.string.pdf_photo)) {
                val photo = c.records.photoFile(meta)
                if (photo != null) {
                    val bmp by produceState<ImageBitmap?>(null, photo.lastModified()) {
                        value = withContext(Dispatchers.IO) {
                            BitmapFactory.decodeFile(photo.absolutePath, BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap()
                        }
                    }
                    bmp?.let { Image(it, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp).clip(RoundedCornerShape(10.dp))) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) takePicture.launch(captureUri)
                        else cameraPermission.launch(android.Manifest.permission.CAMERA)
                    }) { Text(stringResource(R.string.photo_take)) }
                    OutlinedButton(onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text(stringResource(R.string.photo_pick)) }
                    if (photo != null) TextButton(onClick = { scope.launch { c.records.removePhoto(meta) } }) { Text(stringResource(R.string.action_remove)) }
                }
            }

            SectionCard(stringResource(R.string.pdf_conditions)) {
                KeyValue(stringResource(R.string.pdf_device), "${meta.deviceModel} · Android ${meta.androidVersion}")
                KeyValue(stringResource(R.string.pdf_sensor), meta.sensorName.ifBlank { "—" })
                KeyValue(stringResource(R.string.pdf_calibration), meta.calibrationNote.ifBlank { stringResource(if (meta.calibrated) R.string.pdf_calibrated else R.string.pdf_not_calibrated) })
                KeyValue(stringResource(R.string.pdf_app), "NexTools ${meta.appVersion}")
                meta.location?.let { l ->
                    val txt = "%.5f, %.5f".format(java.util.Locale.ROOT, l.lat, l.lon)
                    Text(
                        stringResource(R.string.pdf_location) + ": " + txt,
                        color = LocalInstrument.current.accent,
                        modifier = Modifier.clickable {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${l.lat},${l.lon}?q=${l.lat},${l.lon}"))) }
                        },
                    )
                }
            }

            val sameTool = records.filter { it.tool == meta.tool && it.id != meta.id }
            if (sameTool.isNotEmpty()) {
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.compare_with)) }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.delete_title),
            message = stringResource(R.string.delete_message),
            confirm = stringResource(R.string.action_delete),
            onConfirm = { scope.launch { c.records.delete(meta.id); onBack() } },
            onDismiss = { confirmDelete = false },
        )
    }
    if (editing) EditRecordDialog(meta, onDismiss = { editing = false }) { updated -> scope.launch { c.records.update(updated) }; editing = false }
    if (picking) {
        val others = records.filter { it.tool == meta.tool && it.id != meta.id }
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(stringResource(R.string.compare_pick)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    others.forEach { o ->
                        Text(
                            o.title.ifBlank { toolName } + "\n" + Fmt.dateTime(o.startedAtMs),
                            modifier = Modifier.fillMaxWidth().clickable { picking = false; onCompare(meta.id, o.id) }.padding(vertical = 10.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun ChartSection(meta: RecordMeta, table: Csv.Table?) {
    val inst = LocalInstrument.current
    var col by rememberSaveable { mutableIntStateOf(0) }
    SectionCard(null) {
        if (table == null) {
            CircularProgressIndicator()
            return@SectionCard
        }
        if (table.rows.isEmpty() || meta.columns.isEmpty()) {
            Text(stringResource(R.string.record_no_data))
            return@SectionCard
        }
        if (meta.columns.size > 1) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(meta.columns) { i, cdef ->
                    FilterChip(selected = col == i, onClick = { col = i }, label = { Text(ToolUi.columnText(cdef.name)) })
                }
            }
        }
        val cdef = meta.columns[col.coerceIn(0, meta.columns.lastIndex)]
        val idx = table.columns.indexOf(cdef.name)
        if (idx < 0) return@SectionCard
        // Tek seferlik kayıtlarda (bant listesi gibi) t_ms anlamsızdır: x ekseni satır sırası olur.
        val oneShot = table.rows.size > 1 && table.rows.last()[0] - table.rows.first()[0] < 50.0
        val xs = DoubleArray(table.rows.size) { if (oneShot) it.toDouble() else table.rows[it][0] / 1000.0 }
        val ys = DoubleArray(table.rows.size) { table.rows[it][idx] }
        Text(ToolUi.columnText(cdef.name) + (if (cdef.unit.isNotEmpty()) " (${cdef.unit})" else ""), style = MaterialTheme.typography.labelLarge)
        RecordChart(listOf(Triple(xs, ys, inst.accent)))
    }
}

@Composable
private fun EditRecordDialog(meta: RecordMeta, onDismiss: () -> Unit, onSave: (RecordMeta) -> Unit) {
    var title by rememberSaveable { mutableStateOf(meta.title) }
    var note by rememberSaveable { mutableStateOf(meta.note) }
    var tags by rememberSaveable { mutableStateOf(meta.tags.joinToString(", ")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it.take(120) }, label = { Text(stringResource(R.string.field_title)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it.take(2000) }, label = { Text(stringResource(R.string.field_note)) }, minLines = 2, maxLines = 6, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(tags, { tags = it.take(200) }, label = { Text(stringResource(R.string.field_tags)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(meta.copy(title = title.trim(), note = note.trim(), tags = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()))
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
