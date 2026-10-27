package com.eirahoutmoss.nextools.ui.components

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.ui.Fmt
import com.eirahoutmoss.nextools.ui.theme.LocalInstrument
import com.eirahoutmoss.nextools.ui.theme.NumberStyles

@Composable
fun StatusDot(color: Color, size: Int = 9) {
    Box(Modifier.size(size.dp).background(color, CircleShape))
}

/** Doğruluk rozeti seviyesi: yeşil iyi, amber yaklaşık/kalibre değil, gri bilinmiyor. */
enum class BadgeLevel { GOOD, APPROX, NEUTRAL, ALARM }

@Composable
fun badgeColor(level: BadgeLevel): Color {
    val i = LocalInstrument.current
    return when (level) {
        BadgeLevel.GOOD -> i.good
        BadgeLevel.APPROX -> i.warn
        BadgeLevel.NEUTRAL -> i.passive
        BadgeLevel.ALARM -> i.alarm
    }
}

@Composable
fun AccuracyBadge(text: String, level: BadgeLevel, modifier: Modifier = Modifier) {
    val c = badgeColor(level)
    Surface(shape = RoundedCornerShape(50), color = c.copy(alpha = 0.14f), modifier = modifier) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusDot(c, 7)
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, color = c, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Ölçüm ekranının üstündeki büyük değer: değer + birim + doğruluk rozeti. */
@Composable
fun BigReadout(
    value: String,
    unit: String,
    badge: String?,
    badgeLevel: BadgeLevel,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onBackground,
    caption: String? = null,
) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = NumberStyles.Huge, color = valueColor, maxLines = 1)
            if (unit.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Text(unit, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 10.dp))
            }
        }
        if (caption != null) {
            Text(caption, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (badge != null) {
            Spacer(Modifier.size(6.dp))
            AccuracyBadge(badge, badgeLevel)
        }
    }
}

/** "Min · Ort · Maks" gibi küçük istatistik satırı. */
@Composable
fun StatRow(items: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        items.forEach { (label, value) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = NumberStyles.Medium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
fun SectionCard(title: String?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
fun KeyValue(key: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(key, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.45f))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.55f))
    }
}

/** Uzun bilgi metni penceresi. */
@Composable
fun InfoDialog(title: String, body: @Composable () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) { body() } },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
    )
}

/** Sayı girişi (virgül ya da nokta). [validate] hata metni döndürürse pencere açık kalır. */
@Composable
fun NumberInputDialog(
    title: String,
    message: String,
    label: String,
    initial: Double?,
    onConfirm: (Double) -> String?,
    onDismiss: () -> Unit,
    extra: (@Composable () -> Unit)? = null,
) {
    var text by rememberSaveable { mutableStateOf(initial?.let { com.eirahoutmoss.nextools.core.record.Csv.number(it, 2) } ?: "") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val invalid = stringResource(R.string.error_number)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    label = { Text(label) },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { e -> { Text(e) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                extra?.invoke()
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val v = Fmt.parse(text)
                error = if (v == null) invalid else onConfirm(v)
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun ConfirmDialog(title: String, message: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Ekran açık kalsın (ör. kayıt sürerken). */
@Composable
fun KeepScreenOn(enabled: Boolean) {
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(enabled) {
        if (enabled) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

/** Uyarı şeridi (amber) ya da güvenlik uyarısı (kırmızı). */
@Composable
fun NoticeBanner(text: String, level: BadgeLevel = BadgeLevel.APPROX, modifier: Modifier = Modifier) {
    val c = badgeColor(level)
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), color = c.copy(alpha = 0.13f)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusDot(c, 8)
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }
}
