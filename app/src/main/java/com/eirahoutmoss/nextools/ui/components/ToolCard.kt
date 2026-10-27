package com.eirahoutmoss.nextools.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.Hardware
import com.eirahoutmoss.nextools.core.catalog.ToolStatus
import com.eirahoutmoss.nextools.settings.Calibrations
import com.eirahoutmoss.nextools.ui.ToolUi
import com.eirahoutmoss.nextools.ui.theme.LocalInstrument
import com.eirahoutmoss.nextools.ui.theme.NumberStyles

/** Kart üzerindeki durum: yeşil hazır, amber kalibrasyon önerilir / yaklaşık, gri pasif. */
enum class CardState { READY, ATTENTION, PASSIVE, PLANNED }

fun cardState(status: ToolStatus, cal: Calibrations): CardState = when (status) {
    is ToolStatus.Ready -> if (status.fallback || status.uncertain || (status.spec.calibrationRecommended && !cal.isCalibrated(status.spec.id))) CardState.ATTENTION else CardState.READY
    is ToolStatus.MissingHardware, is ToolStatus.FallbackPending -> CardState.PASSIVE
    is ToolStatus.Planned -> CardState.PLANNED
}

@Composable
fun missingText(missing: Set<Hardware>): String {
    val names = missing.map { stringResource(ToolUi.hardware(it)) }
    return when (names.size) {
        0 -> stringResource(R.string.passive_generic)
        1 -> stringResource(R.string.passive_missing_one, names[0])
        else -> stringResource(R.string.passive_missing_many, names.joinToString(", "))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ToolCard(
    status: ToolStatus,
    state: CardState,
    live: String?,
    favorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val inst = LocalInstrument.current
    val id = status.spec.id
    val text = ToolUi.text(id)
    val passive = state == CardState.PASSIVE
    val planned = state == CardState.PLANNED
    val subtitle = when (status) {
        is ToolStatus.MissingHardware -> missingText(status.missing)
        is ToolStatus.FallbackPending -> missingText(status.missing)
        else -> stringResource(text.desc)
    }
    Card(
        modifier = modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(12.dp).alpha(if (passive) 0.55f else if (planned) 0.8f else 1f)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.size(40.dp)) {
                    Surface(shape = RoundedCornerShape(12.dp), color = (if (passive) inst.passive else inst.accent).copy(alpha = 0.14f), modifier = Modifier.matchParentSize()) {}
                    Icon(
                        ToolUi.icon(id), null,
                        tint = if (passive) inst.passive else inst.accent,
                        modifier = Modifier.align(Alignment.Center).size(24.dp),
                    )
                    if (passive) {
                        // Pasif kart: simge üzerinde çapraz çizgi
                        val strike = inst.passive
                        Canvas(Modifier.matchParentSize()) {
                            drawLine(strike, Offset(size.width * 0.2f, size.height * 0.2f), Offset(size.width * 0.8f, size.height * 0.8f), 2.5.dp.toPx(), cap = StrokeCap.Round)
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                if (favorite) Icon(Icons.Filled.Star, null, tint = inst.warn, modifier = Modifier.size(16.dp).padding(end = 2.dp))
                when (state) {
                    CardState.READY -> StatusDot(inst.good)
                    CardState.ATTENTION -> StatusDot(inst.warn)
                    CardState.PASSIVE -> StatusDot(inst.passive)
                    CardState.PLANNED -> {
                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Text(
                                stringResource(R.string.soon),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    stringResource(text.name), style = MaterialTheme.typography.titleSmall, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                if (live != null && !passive) {
                    Text(live, style = NumberStyles.Small, color = inst.accent, maxLines = 1)
                }
            }
            Text(
                subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 10.dp, bottom = 2.dp),
    )
}

@Composable
fun TinyBar(fraction: Float, modifier: Modifier = Modifier) {
    val inst = LocalInstrument.current
    Box(modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(4.dp).background(inst.accent))
    }
}
