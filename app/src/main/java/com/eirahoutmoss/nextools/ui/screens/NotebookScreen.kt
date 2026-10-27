package com.eirahoutmoss.nextools.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.records.RecordMeta
import com.eirahoutmoss.nextools.ui.Fmt
import com.eirahoutmoss.nextools.ui.ToolUi
import com.eirahoutmoss.nextools.ui.appContainer
import com.eirahoutmoss.nextools.ui.components.Sparkline
import com.eirahoutmoss.nextools.ui.theme.LocalInstrument

/** Laboratuvar Defteri: kayıtların zaman çizelgesi; araç, etiket ve metne göre süzme. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookScreen(onOpen: (String) -> Unit) {
    val c = appContainer()
    val records by c.records.records.collectAsStateWithLifecycle()
    var toolFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val tools = records.map { it.tool }.distinct()
    val q = query.trim().lowercase()
    val shown = records.filter { r ->
        (toolFilter == null || r.tool.key == toolFilter) &&
            (q.isEmpty() || r.title.lowercase().contains(q) || r.note.lowercase().contains(q) || r.tags.any { it.lowercase().contains(q) })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searching) OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text(stringResource(R.string.notebook_search_hint)) }, modifier = Modifier.fillMaxWidth().padding(end = 8.dp))
                    else Text(stringResource(R.string.nav_notebook))
                },
                actions = {
                    IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                        Icon(if (searching) Icons.Outlined.Close else Icons.Outlined.Search, stringResource(R.string.action_search))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (records.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.AutoMirrored.Outlined.MenuBook, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(12.dp))
                Text(stringResource(R.string.notebook_empty), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (tools.size > 1) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { FilterChip(selected = toolFilter == null, onClick = { toolFilter = null }, label = { Text(stringResource(R.string.cat_all)) }) }
                        items(tools) { t ->
                            FilterChip(selected = toolFilter == t.key, onClick = { toolFilter = t.key }, label = { Text(stringResource(ToolUi.text(t).name)) })
                        }
                    }
                }
            }
            items(shown, key = { it.id }) { r -> RecordCard(r) { onOpen(r.id) } }
        }
    }
}

@Composable
fun RecordCard(r: RecordMeta, onClick: () -> Unit) {
    val inst = LocalInstrument.current
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(10.dp), color = inst.accent.copy(alpha = 0.14f), modifier = Modifier.size(40.dp)) {
                Icon(ToolUi.icon(r.tool), null, tint = inst.accent, modifier = Modifier.padding(8.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(r.title.ifBlank { stringResource(ToolUi.text(r.tool).name) }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(ToolUi.text(r.tool).name) + " · " + Fmt.shortDateTime(r.startedAtMs) + " · " + Fmt.duration(r.durationMs),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                )
                if (r.tags.isNotEmpty()) {
                    Text(r.tags.joinToString("  ") { "#$it" }, style = MaterialTheme.typography.labelSmall, color = inst.accent, maxLines = 1)
                }
            }
            Spacer(Modifier.width(8.dp))
            Sparkline(r.preview, inst.accent, Modifier.width(72.dp).height(32.dp))
        }
    }
}
