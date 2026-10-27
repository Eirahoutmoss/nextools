package com.eirahoutmoss.nextools.ui.components

import android.content.Context
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors

/**
 * Arka kamera önizlemesi (CameraX). [onFrame] verilirse her kare arka plan iş parçacığında
 * çözümlenir; görüntü saklanmaz. [onCamera] zum, ışık ve odak denetimi için kamerayı verir.
 */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onCamera: (Camera) -> Unit = {},
    onView: (PreviewView) -> Unit = {},
    onFrame: ((ImageProxy) -> Unit)? = null,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val frame = rememberUpdatedState(onFrame)
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(owner) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            val p = runCatching { future.get() }.getOrNull() ?: return@addListener
            provider = p
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val useCases = mutableListOf<androidx.camera.core.UseCase>(preview)
            if (onFrame != null) {
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { img -> try { frame.value?.invoke(img) } finally { img.close() } }
                useCases += analysis
            }
            runCatching {
                p.unbindAll()
                val cam = p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, *useCases.toTypedArray())
                onCamera(cam)
                onView(previewView)
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            provider?.unbindAll()
        }
    }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }
    AndroidView(factory = { previewView }, modifier = modifier)
}

/**
 * YUV_420_888 karesinin merkezindeki [box]×[box] alanın ortalama rengi (sRGB 0..255).
 * Kamera beyaz dengesi ve pozlaması otomatiktir; değer ışığa bağlıdır.
 */
fun centerRgb(img: ImageProxy, box: Int = 16): IntArray {
    val y = img.planes[0]; val u = img.planes[1]; val v = img.planes[2]
    val cx = img.width / 2; val cy = img.height / 2
    var rs = 0L; var gs = 0L; var bs = 0L; var n = 0
    for (j in cy - box / 2 until cy + box / 2) for (i in cx - box / 2 until cx + box / 2) {
        val yy = (y.buffer.get(j * y.rowStride + i * y.pixelStride).toInt() and 0xFF)
        val uvIndex = (j / 2) * u.rowStride + (i / 2) * u.pixelStride
        val uu = (u.buffer.get(uvIndex).toInt() and 0xFF) - 128
        val vv = (v.buffer.get((j / 2) * v.rowStride + (i / 2) * v.pixelStride).toInt() and 0xFF) - 128
        // BT.601 tam aralık
        val r = yy + 1.402 * vv
        val g = yy - 0.344136 * uu - 0.714136 * vv
        val b = yy + 1.772 * uu
        rs += r.toInt().coerceIn(0, 255); gs += g.toInt().coerceIn(0, 255); bs += b.toInt().coerceIn(0, 255); n++
    }
    return intArrayOf((rs / n).toInt(), (gs / n).toInt(), (bs / n).toInt())
}

fun Context.hasCamera(): Boolean = packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY)

/** Bir çizgi boyunca örneklenen renkler: kırmızı, yeşil, mavi ve parlaklık dizileri (0..255). */
class LineProfile(val r: DoubleArray, val g: DoubleArray, val b: DoubleArray) {
    val size get() = r.size
    val intensity: DoubleArray get() = DoubleArray(size) { (r[it] + g[it] + b[it]) / 3 }
    val max: Double get() = maxOf(r.max(), g.max(), b.max())
}

/**
 * Ekranda yatay görünen orta çizgi boyunca (±[halfBand] piksel kalınlık) renk profili.
 * Kare sensör yönünde gelir; [ImageProxy.getImageInfo] dönüş açısı hesaba katılır.
 */
fun screenLineProfile(img: ImageProxy, halfBand: Int = 6): LineProfile {
    val rot = img.imageInfo.rotationDegrees
    val alongX = rot % 180 == 0
    val len = if (alongX) img.width else img.height
    val cross = if (alongX) img.height else img.width
    val reverse = rot == 90 || rot == 180
    val y = img.planes[0]; val u = img.planes[1]; val v = img.planes[2]
    val rr = DoubleArray(len); val gg = DoubleArray(len); val bb = DoubleArray(len)
    val c0 = cross / 2
    for (k in 0 until len) {
        val along = if (reverse) len - 1 - k else k
        var rs = 0.0; var gs = 0.0; var bs = 0.0; var n = 0
        for (d in -halfBand..halfBand) {
            val c = (c0 + d).coerceIn(0, cross - 1)
            val i = if (alongX) along else c
            val j = if (alongX) c else along
            val yy = (y.buffer.get(j * y.rowStride + i * y.pixelStride).toInt() and 0xFF).toDouble()
            val uu = (u.buffer.get((j / 2) * u.rowStride + (i / 2) * u.pixelStride).toInt() and 0xFF) - 128.0
            val vv = (v.buffer.get((j / 2) * v.rowStride + (i / 2) * v.pixelStride).toInt() and 0xFF) - 128.0
            rs += (yy + 1.402 * vv).coerceIn(0.0, 255.0)
            gs += (yy - 0.344136 * uu - 0.714136 * vv).coerceIn(0.0, 255.0)
            bs += (yy + 1.772 * uu).coerceIn(0.0, 255.0)
            n++
        }
        rr[k] = rs / n; gg[k] = gs / n; bb[k] = bs / n
    }
    return LineProfile(rr, gg, bb)
}
