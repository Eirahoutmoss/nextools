package com.eirahoutmoss.nextools.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.record.Csv
import com.eirahoutmoss.nextools.ui.Fmt
import com.eirahoutmoss.nextools.ui.ToolUi
import com.eirahoutmoss.nextools.ui.appContainer
import com.eirahoutmoss.nextools.ui.components.RecordChart
import com.eirahoutmoss.nextools.ui.components.SectionCard
import com.eirahoutmoss.nextools.ui.components.StatusDot
import com.eirahoutmoss.nextools.ui.theme.LocalInstrument

/** Aynı aracın iki kaydı yan yana (ör. "makine sağlığı: Ocak ve Haziran"). Zaman ekseni ikisinde de 0'dan başlar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreen(a: String, b: String, onBack: () -> Unit) {
    val c = appContainer()
    val records by c.records.records.collectAsStateWithLifecycle()
    val ma = records.firstOrNull { it.id == a }
    val mb = records.firstOrNull { it.id == b }
    val ta by produceState<Csv.Table?>(null, a) { value = c.records.readTable(a) }
    val tb by produceState<Csv.Table?>(null, b) { value = c.records.readTable(b) }
    val inst = LocalInstrument.current
    val colorB = inst.warn
    var col by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.compare_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (ma == null || mb == null) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Legend(inst.accent, ma.title, Fmt.dateTime(ma.startedAtMs))
            Legend(colorB, mb.title, Fmt.dateTime(mb.startedAtMs))
            if (ma.columns.size > 1) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(ma.columns) { i, cdef -> FilterChip(selected = col == i, onClick = { col = i }, label = { Text(ToolUi.columnText(cdef.name)) }) }
                }
            }
            val cdef = ma.columns.getOrNull(col) ?: ma.columns.firstOrNull()
            val tA = ta; val tB = tb
            if (cdef != null && tA != null && tB != null) {
                fun series(t: Csv.Table, color: Color): Triple<DoubleArray, DoubleArray, Color>? {
                    val idx = t.columns.indexOf(cdef.name)
                    if (idx < 0 || t.rows.isEmpty()) return null
                    return Triple(DoubleArray(t.rows.size) { t.rows[it][0] / 1000.0 }, DoubleArray(t.rows.size) { t.rows[it][idx] }, color)
                }
                SectionCard(ToolUi.columnText(cdef.name) + if (cdef.unit.isNotEmpty()) " (${cdef.unit})" else "") {
                    RecordChart(listOfNotNull(series(tA, inst.accent), series(tB, colorB)), height = 220.dp)
                }
            }
            SectionCard(stringResource(R.string.pdf_summary)) {
                Row { Spacer(Modifier.weight(1f)); StatusDot(inst.accent); Spacer(Modifier.width(60.dp)); StatusDot(colorB); Spacer(Modifier.width(30.dp)) }
                ma.summary.forEach { s ->
                    val other = mb.summary.firstOrNull { it.kind == s.kind && it.column == s.column }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(ToolUi.summaryKind(s.kind)) + " · " + ToolUi.columnText(s.column),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        Text(Fmt.num(s.value, s.decimals), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(76.dp))
                        Text(other?.let { Fmt.num(it.value, it.decimals) } ?: "—", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(64.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Legend(color: Color, title: String, date: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusDot(color, 12)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
