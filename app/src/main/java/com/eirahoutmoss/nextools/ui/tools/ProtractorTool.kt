package com.eirahoutmoss.nextools.ui.tools

import android.Manifest
import android.graphics.Bitmap
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.eirahoutmoss.nextools.ui.components.StatRow
import com.eirahoutmoss.nextools.ui.components.toSquare
import com.eirahoutmoss.nextools.ui.components.ToolScaffold
import com.eirahoutmoss.nextools.ui.theme.LocalInstrument
import kotlin.math.atan2

/** Açı Ölçer: kamera görüntüsünde tepe noktası ve iki kol sürüklenir; aradaki açı. */
@Composable
fun ProtractorTool(onBack: () -> Unit) {
    // 0 = tepe, 1 ve 2 = kol uçları (göreli koordinat)
    val points = remember { mutableStateListOf(Offset(0.5f, 0.65f), Offset(0.85f, 0.65f), Offset(0.7f, 0.3f)) }
    var view by remember { mutableStateOf<PreviewView?>(null) }
    var still by remember { mutableStateOf<Bitmap?>(null) }
    val inst = LocalInstrument.current

    ToolScaffold(ToolId.PROTRACTOR, onBack, {}, null, null, still != null, { still = if (still == null) view?.bitmap else null }, null) {
        PermissionGate(Manifest.permission.CAMERA, stringResource(R.string.camera_rationale)) {
            MeasureOverlay(still, points, onView = { view = it }) { p ->
                val v = p[0]
                drawLine(inst.accent, v, p[1], 3.dp.toPx()); drawLine(inst.accent, v, p[2], 3.dp.toPx())
                val a1 = Math.toDegrees(atan2((p[1].y - v.y).toDouble(), (p[1].x - v.x).toDouble())).toFloat()
                val a2 = Math.toDegrees(atan2((p[2].y - v.y).toDouble(), (p[2].x - v.x).toDouble())).toFloat()
                var sweep = a2 - a1; while (sweep > 180) sweep -= 360; while (sweep < -180) sweep += 360
                val r = 40.dp.toPx()
                drawArc(inst.warn, a1, sweep, false, Offset(v.x - r, v.y - r), androidx.compose.ui.geometry.Size(2 * r, 2 * r), style = Stroke(2.dp.toPx()))
                p.forEachIndexed { i, o ->
                    drawCircle(Color.Black.copy(alpha = 0.4f), 14.dp.toPx(), o)
                    drawCircle(if (i == 0) inst.alarm else inst.accent, 10.dp.toPx(), o, style = Stroke(3.dp.toPx()))
                }
            }
            val s = points.map { it.toSquare() }
            val angle = ImageGeometry.angle(s[0].x.toDouble(), s[0].y.toDouble(), s[1].x.toDouble(), s[1].y.toDouble(), s[2].x.toDouble(), s[2].y.toDouble())
            BigReadout(
                value = Fmt.num(angle, 1), unit = "°",
                caption = stringResource(R.string.prot_caption),
                badge = stringResource(R.string.prot_badge), badgeLevel = BadgeLevel.APPROX,
            )
            StatRow(listOf(stringResource(R.string.prot_supplement) to Fmt.num(180 - angle, 1) + "°", stringResource(R.string.prot_reflex) to Fmt.num(360 - angle, 1) + "°"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { still = if (still == null) view?.bitmap else null }) { Text(stringResource(if (still == null) R.string.prot_capture else R.string.prot_live)) }
                OutlinedButton(onClick = { points[0] = Offset(0.5f, 0.65f); points[1] = Offset(0.85f, 0.65f); points[2] = Offset(0.7f, 0.3f) }) { Text(stringResource(R.string.prot_reset)) }
            }
            Text(stringResource(R.string.prot_tip), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
