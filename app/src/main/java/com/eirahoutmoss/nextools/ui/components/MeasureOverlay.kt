package com.eirahoutmoss.nextools.ui.components

import android.graphics.Bitmap
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

/**
 * Kamera önizlemesi (ya da dondurulmuş karesi) üzerinde sürüklenebilir noktalar.
 * Noktalar 0..1 göreli koordinatta tutulur; [draw] ekran pikselleriyle çizer.
 */
/** Ölçüm alanının en/boy oranı. Göreli noktaları ekran oranına çevirmek için: y / MEASURE_ASPECT. */
const val MEASURE_ASPECT = 0.75f

/** Göreli noktayı kare ölçekli düzleme çevirir (x ekran genişliği birimi). */
fun Offset.toSquare() = Offset(x, y / MEASURE_ASPECT)

@Composable
fun MeasureOverlay(
    frozen: Bitmap?,
    points: SnapshotStateList<Offset>,
    onView: (PreviewView) -> Unit,
    draw: DrawScope.(List<Offset>) -> Unit,
) {
    Box(Modifier.fillMaxWidth().aspectRatio(MEASURE_ASPECT).clip(RoundedCornerShape(16.dp))) {
        if (frozen != null) {
            Image(frozen.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            CameraPreview(Modifier.fillMaxSize(), onView = onView)
        }
        Canvas(
            Modifier.fillMaxSize().pointerInput(Unit) {
                var active = -1
                detectDragGestures(
                    onDragStart = { start ->
                        val w = this.size.width.toFloat(); val h = this.size.height.toFloat()
                        active = points.indices.minByOrNull { i -> (Offset(points[i].x * w, points[i].y * h) - start).getDistance() }
                            ?.takeIf { i -> (Offset(points[i].x * w, points[i].y * h) - start).getDistance() < 64.dp.toPx() } ?: -1
                    },
                    onDragEnd = { active = -1 },
                    onDrag = { change, delta ->
                        if (active >= 0) {
                            change.consume()
                            val w = this.size.width.toFloat(); val h = this.size.height.toFloat()
                            val p = points[active]
                            points[active] = Offset((p.x + delta.x / w).coerceIn(0f, 1f), (p.y + delta.y / h).coerceIn(0f, 1f))
                        }
                    },
                )
            },
        ) {
            draw(points.map { Offset(it.x * this.size.width, it.y * this.size.height) })
        }
    }
}
