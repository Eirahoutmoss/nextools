package com.eirahoutmoss.nextools.ui.tools

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.measure.ColorMath
import com.eirahoutmoss.nextools.records.Column as DataColumn
import com.eirahoutmoss.nextools.ui.Fmt
import com.eirahoutmoss.nextools.ui.components.BadgeLevel
import com.eirahoutmoss.nextools.ui.components.CameraPreview
import com.eirahoutmoss.nextools.ui.components.KeyValue
import com.eirahoutmoss.nextools.ui.components.RecordingSpec
import com.eirahoutmoss.nextools.ui.components.SectionCard
import com.eirahoutmoss.nextools.ui.components.AccuracyBadge
import com.eirahoutmoss.nextools.ui.components.ToolScaffold
import com.eirahoutmoss.nextools.ui.components.centerRgb
import com.eirahoutmoss.nextools.ui.components.rememberRecording
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun colorName(key: String): String {
    val ctx = LocalContext.current
    val id = ctx.resources.getIdentifier("color_$key", "string", ctx.packageName)
    return if (id != 0) ctx.getString(id) else key
}

/** Renk Ölçer: kameranın ortasındaki rengi HEX/RGB/Lab olarak okur; iki renk farkı (ΔE2000); beyaz dengesi. */
@Composable
fun ColorPickerTool(onBack: () -> Unit, onOpenRecord: (String) -> Unit) {
    val context = LocalContext.current
    val rec = rememberRecording(ToolId.COLOR_PICKER)
    val rgbFlow = remember { MutableStateFlow<IntArray?>(null) }
    val live by rgbFlow.collectAsStateWithLifecycle()
    var frozen by remember { mutableStateOf(false) }
    var held by remember { mutableStateOf<IntArray?>(null) }
    var reference by remember { mutableStateOf<IntArray?>(null) }
    var white by remember { mutableStateOf<IntArray?>(null) }
    val clipboard = LocalClipboardManager.current
    val lastRec = remember { longArrayOf(0L) }

    fun balanced(c: IntArray): IntArray {
        val w = white ?: return c
        return IntArray(3) { (c[it] * 255.0 / w[it].coerceAtLeast(1)).toInt().coerceIn(0, 255) }
    }

    ToolScaffold(
        tool = ToolId.COLOR_PICKER, onBack = onBack, onOpenRecord = onOpenRecord,
        recording = rec,
        onStartRecording = {
            val note = context.getString(if (white != null) R.string.color_wb_on else R.string.color_wb_off)
            rec.start(
                RecordingSpec(
                    columns = listOf(DataColumn("r", "", 0), DataColumn("g", "", 0), DataColumn("b", "", 0), DataColumn("lab_l", "", 1), DataColumn("lab_a", "", 1), DataColumn("lab_b", "", 1)),
                    headerComments = csvHeader(context, ToolId.COLOR_PICKER, "camera (back)", note),
                    calibrated = white != null, calibrationNote = note, sensorName = context.getString(R.string.hw_camera),
                ),
            )
        },
        frozen = frozen, onFreezeToggle = { frozen = !frozen; held = if (frozen) live else null },
        onCalibrate = null,
    ) {
        PermissionGate(Manifest.permission.CAMERA, stringResource(R.string.camera_rationale)) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                CameraPreview(Modifier.fillMaxWidth().aspectRatio(1f), onFrame = { img ->
                    val c = centerRgb(img)
                    rgbFlow.value = c
                    val now = System.nanoTime()
                    if (rec.isRecording && now - lastRec[0] > 200_000_000L) {
                        lastRec[0] = now
                        val b = balanced(c); val l = ColorMath.lab(b[0], b[1], b[2])
                        rec.add(doubleArrayOf(b[0].toDouble(), b[1].toDouble(), b[2].toDouble(), l.l, l.a, l.b))
                    }
                })
                Box(Modifier.size(28.dp).border(2.dp, Color.White, CircleShape))
            }
            val raw = (if (frozen) held else live)
            if (raw == null) { Text(stringResource(R.string.waiting_sensor)); return@PermissionGate }
            val c = balanced(raw)
            val lab = ColorMath.lab(c[0], c[1], c[2])
            val (named, de) = ColorMath.nearest(c[0], c[1], c[2])
            val hex = ColorMath.hex(c[0], c[1], c[2])
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(72.dp).clip(RoundedCornerShape(14.dp)).background(Color(c[0], c[1], c[2])))
                Column {
                    Text(hex, style = com.eirahoutmoss.nextools.ui.theme.NumberStyles.Big)
                    Text(stringResource(R.string.color_nearest, colorName(named.key), Fmt.num(de, 1)), style = MaterialTheme.typography.bodyMedium)
                }
            }
            AccuracyBadge(stringResource(if (white != null) R.string.color_wb_on else R.string.color_wb_off), if (white != null) BadgeLevel.GOOD else BadgeLevel.APPROX)
            SectionCard(null) {
                KeyValue("RGB", "${c[0]}, ${c[1]}, ${c[2]}")
                KeyValue("CIE Lab", "${Fmt.num(lab.l, 1)}, ${Fmt.num(lab.a, 1)}, ${Fmt.num(lab.b, 1)}")
                reference?.let { r ->
                    val d = ColorMath.deltaE2000(ColorMath.lab(r[0], r[1], r[2]), lab)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(20.dp).clip(CircleShape).background(Color(r[0], r[1], r[2])))
                        Text("  " + stringResource(R.string.color_delta, Fmt.num(d, 2), stringResource(deltaText(d))), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(hex)) }) { Text(stringResource(R.string.color_copy)) }
                    OutlinedButton(onClick = { reference = c }) { Text(stringResource(R.string.color_set_ref)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { white = raw }) { Text(stringResource(R.string.color_white_card)) }
                    if (white != null) OutlinedButton(onClick = { white = null }) { Text(stringResource(R.string.color_white_reset)) }
                }
            }
            Text(stringResource(R.string.color_tip), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun deltaText(d: Double): Int = when {
    d < 1 -> R.string.de_same
    d < 2 -> R.string.de_barely
    d < 5 -> R.string.de_noticeable
    d < 10 -> R.string.de_clear
    else -> R.string.de_different
}
