package com.eirahoutmoss.nextools.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.Category
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.catalog.ToolStatus
import com.eirahoutmoss.nextools.ui.ToolUi
import com.eirahoutmoss.nextools.ui.appContainer
import com.eirahoutmoss.nextools.ui.components.CardState
import com.eirahoutmoss.nextools.ui.components.InfoDialog
import com.eirahoutmoss.nextools.ui.components.SectionLabel
import com.eirahoutmoss.nextools.ui.components.ToolCard
import com.eirahoutmoss.nextools.ui.components.Wordmark
import com.eirahoutmoss.nextools.ui.components.cardState
import com.eirahoutmoss.nextools.ui.components.missingText
import com.eirahoutmoss.nextools.ui.theme.LocalInstrument
import kotlinx.coroutines.launch

/** Ana ekran (Araçlar): arama, Cihaz Karnesi şeridi, kategori sekmeleri, 2 sütunlu kart ızgarası. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenTool: (ToolId) -> Unit) {
    val c = appContainer()
    val context = LocalContext.current
    val settings by c.settings.state.collectAsStateWithLifecycle()
    val cal by c.calibrations.state.collectAsStateWithLifecycle()
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var dialogFor by remember { mutableStateOf<ToolStatus?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val statuses = c.hardware.statuses
    val names = remember(statuses) { statuses.associate { it.spec.id to context.getString(ToolUi.text(it.spec.id).name) } }
    val descs = remember(statuses) { statuses.associate { it.spec.id to context.getString(ToolUi.text(it.spec.id).desc) } }
    val q = query.trim().lowercase()
    val filtered = statuses.filter { s ->
        (category == null || s.spec.category.id == category) &&
            (q.isEmpty() || names.getValue(s.spec.id).lowercase().contains(q) || descs.getValue(s.spec.id).lowercase().contains(q))
    }
    // Yazılmış araçlar önce (hazır olanlar başta), sonra "Yakında".
    val available = filtered.filter { it !is ToolStatus.Planned }.sortedBy { if (it is ToolStatus.Ready) 0 else 1 }
    val planned = filtered.filterIsInstance<ToolStatus.Planned>()
    val live = rememberLiveValues(available.map { it.spec.id }.toSet(), cal, settings.units, settings.pressureUnit)
    val summary = c.hardware.summary

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        OutlinedTextField(
                            value = query, onValueChange = { query = it }, singleLine = true,
                            placeholder = { Text(stringResource(R.string.search_hint)) },
                            modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                        )
                    } else Wordmark(26.sp)
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
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(2) }) {
                DeviceStrip(summary.hardwareOk, summary.total, summary.usableNow) { onOpenTool(ToolId.DEVICE_REPORT) }
            }
            item(span = { GridItemSpan(2) }) {
                CategoryTabs(category) { category = it }
            }
            items(available, key = { it.spec.id.key }) { s ->
                val state = cardState(s, cal)
                ToolCard(
                    status = s, state = state, live = live[s.spec.id], favorite = s.spec.id in settings.favorites,
                    onClick = { if (s is ToolStatus.Ready) onOpenTool(s.spec.id) else dialogFor = s },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val adding = s.spec.id !in settings.favorites
                        c.settings.toggleFavorite(s.spec.id)
                        scope.launch {
                            snackbar.showSnackbar(context.getString(if (adding) R.string.fav_added else R.string.fav_removed, names.getValue(s.spec.id)))
                        }
                    },
                )
            }
            if (planned.isNotEmpty()) {
                item(span = { GridItemSpan(2) }) { SectionLabel(stringResource(R.string.section_soon)) }
                items(planned, key = { it.spec.id.key }) { s ->
                    ToolCard(
                        status = s, state = CardState.PLANNED, live = null, favorite = false,
                        onClick = { dialogFor = s }, onLongClick = { dialogFor = s },
                    )
                }
            }
            if (available.isEmpty() && planned.isEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    Text(stringResource(R.string.search_empty), modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    dialogFor?.let { s -> UnavailableDialog(s) { dialogFor = null } }
}

@Composable
private fun DeviceStrip(ok: Int, total: Int, ready: Int, onClick: () -> Unit) {
    val inst = LocalInstrument.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.PhoneAndroid, null, tint = inst.accent)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.device_strip_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.device_strip_line, ok, total, ready),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CategoryTabs(selected: String?, onSelect: (String?) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
        item {
            FilterChip(
                selected = selected == null, onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.cat_all)) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary),
            )
        }
        items(Category.entries.toList()) { cat ->
            FilterChip(
                selected = selected == cat.id, onClick = { onSelect(cat.id) },
                label = { Text(stringResource(ToolUi.category(cat))) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary),
            )
        }
    }
}

/** Pasif ya da henüz yazılmamış araca dokununca açılan açıklama. */
@Composable
fun UnavailableDialog(s: ToolStatus, onDismiss: () -> Unit) {
    val text = ToolUi.text(s.spec.id)
    InfoDialog(
        title = stringResource(text.name),
        body = {
            Text(stringResource(text.desc), style = MaterialTheme.typography.bodyMedium)
            when (s) {
                is ToolStatus.MissingHardware -> Text(missingText(s.missing) + "\n" + stringResource(R.string.passive_explain), style = MaterialTheme.typography.bodyMedium)
                is ToolStatus.FallbackPending -> Text(missingText(s.missing) + "\n" + stringResource(R.string.passive_fallback_pending), style = MaterialTheme.typography.bodyMedium)
                is ToolStatus.Planned -> {
                    Text(stringResource(R.string.planned_later), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (s.hardwareOk) stringResource(R.string.planned_supported) else missingText(s.missing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (s.hardwareOk) LocalInstrument.current.good else LocalInstrument.current.passive,
                    )
                }
                is ToolStatus.Ready -> Unit
            }
            Text(stringResource(R.string.info_limits_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(text.limits), style = MaterialTheme.typography.bodySmall)
        },
        onDismiss = onDismiss,
    )
}
