package com.eirahoutmoss.nextools.ui.tools

import android.Manifest
import android.graphics.Bitmap
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.measure.ImageGeometry
import com.eirahoutmoss.nextools.ui.Fmt
import com.eirahoutmoss.nextools.ui.components.BadgeLevel
import com.eirahoutmoss.nextools.ui.components.BigReadout
import com.eirahoutmoss.nextools.ui.components.MeasureOverlay
import com.eirahoutmoss.nextools.ui.components.NumberInputDialog
import com.eirahoutmoss.nextools.ui.components.SectionCard
import com.eirahoutmoss.nextools.ui.components.ToolScaffold
import com.eirahoutmoss.nextools.ui.components.toSquare
import com.eirahoutmoss.nextools.ui.theme.LocalInstrument

private val DEFAULT_POINTS = listOf(Offset(0.2f, 0.75f), Offset(0.55f, 0.75f), Offset(0.2f, 0.35f), Offset(0.8f, 0.35f))

/**
 * Cetvel: bilinen boyda bir referans (banka kartı, A4 kenarı…) ile aynı düzlemdeki nesnenin uzunluğu.
 * Noktalar 0–1 referans, 2–3 nesne. Kamera düzleme dik tutulmalıdır.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RulerTool(onBack: () -> Unit) {
    val points = remember { mutableStateListOf<Offset>().apply { addAll(DEFAULT_POINTS) } }
    var view by remember { mutableStateOf<PreviewView?>(null) }
    var still by remember { mutableStateOf<Bitmap?>(null) }
    var refMm by rememberSaveable { mutableDoubleStateOf(ImageGeometry.CARD_LONG_MM) }
    var refKind by rememberSaveable { mutableStateOf("card_long") }
    var editing by remember { mutableStateOf(false) }
    val inst = LocalInstrument.current

    ToolScaffold(ToolId.RULER, onBack, {}, null, null, still != null, { still = if (still == null) view?.bitmap else null }, null) {
        PermissionGate(Manifest.permission.CAMERA, stringResource(R.string.camera_rationale)) {
            MeasureOverlay(still, points, onView = { view = it }) { p ->
                drawLine(inst.warn, p[0], p[1], 3.dp.toPx())
                drawLine(inst.accent, p[2], p[3], 3.dp.toPx())
                p.forEachIndexed { i, o ->
                    drawCircle(Color.Black.copy(alpha = 0.4f), 14.dp.toPx(), o)
                    drawCircle(if (i < 2) inst.warn else inst.accent, 10.dp.toPx(), o, style = Stroke(3.dp.toPx()))
                }
            }
            val s = points.map { it.toSquare() }
            val refPx = ImageGeometry.dist(s[0].x.toDouble(), s[0].y.toDouble(), s[1].x.toDouble(), s[1].y.toDouble()) * 1000
            val objPx = ImageGeometry.dist(s[2].x.toDouble(), s[2].y.toDouble(), s[3].x.toDouble(), s[3].y.toDouble()) * 1000
            val mm = ImageGeometry.length(refPx, refMm, objPx)
            BigReadout(
                value = mm?.let { if (it >= 1000) Fmt.num(it / 1000, 3) else if (it >= 100) Fmt.num(it / 10, 1) else Fmt.num(it, 1) } ?: "—",
                unit = mm?.let { if (it >= 1000) "m" else if (it >= 100) "cm" else "mm" } ?: "",
                caption = stringResource(R.string.ruler_caption),
                badge = stringResource(R.string.ruler_badge), badgeLevel = BadgeLevel.APPROX,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { still = if (still == null) view?.bitmap else null }) { Text(stringResource(if (still == null) R.string.prot_capture else R.string.prot_live)) }
                OutlinedButton(onClick = { DEFAULT_POINTS.forEachIndexed { i, o -> points[i] = o } }) { Text(stringResource(R.string.prot_reset)) }
            }
            SectionCard(stringResource(R.string.ruler_reference)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(refKind == "card_long", { refKind = "card_long"; refMm = ImageGeometry.CARD_LONG_MM }, label = { Text(stringResource(R.string.ruler_card_long)) })
                    FilterChip(refKind == "card_short", { refKind = "card_short"; refMm = ImageGeometry.CARD_SHORT_MM }, label = { Text(stringResource(R.string.ruler_card_short)) })
                    FilterChip(refKind == "a4", { refKind = "a4"; refMm = 210.0 }, label = { Text(stringResource(R.string.ruler_a4)) })
                    FilterChip(refKind == "custom", { editing = true }, label = { Text(if (refKind == "custom") Fmt.num(refMm, 1) + " mm" else stringResource(R.string.ruler_custom)) })
                }
                Text(stringResource(R.string.ruler_legend), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(stringResource(R.string.ruler_tip), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (editing) {
        val bad = stringResource(R.string.error_number)
        NumberInputDialog(
            title = stringResource(R.string.ruler_custom),
            message = stringResource(R.string.ruler_custom_msg),
            label = "mm",
            initial = refMm,
            onDismiss = { editing = false },
            onConfirm = { v -> if (v > 1) { refMm = v; refKind = "custom"; editing = false; null } else bad },
        )
    }
}
