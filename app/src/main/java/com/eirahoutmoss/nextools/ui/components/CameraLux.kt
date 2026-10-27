package com.eirahoutmoss.nextools.ui.components

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.eirahoutmoss.nextools.core.measure.CameraLux
import java.util.concurrent.Executors

/**
 * Ortam ışığı sensörü olmayan telefonlar için: ön kameranın (yoksa arka) otomatik pozlama sonucundan
 * (pozlama süresi, ISO, diyafram) kaba aydınlık. Görüntü işlenmez, saklanmaz.
 */
@OptIn(ExperimentalCamera2Interop::class)
@Composable
fun CameraLuxSource(onLux: (Double) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val cb = rememberUpdatedState(onLux)
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(owner) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            val p = runCatching { future.get() }.getOrNull() ?: return@addListener
            provider = p
            val b = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            Camera2Interop.Extender(b)
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                .setSessionCaptureCallback(object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                        val t = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: return
                        val iso = result.get(CaptureResult.SENSOR_SENSITIVITY) ?: return
                        val n = result.get(CaptureResult.LENS_APERTURE) ?: 2.0f
                        CameraLux.fromExposure(n.toDouble(), t / 1e9, iso.toDouble())?.let { cb.value(it) }
                    }
                })
            val analysis = b.build().also { it.setAnalyzer(executor) { img -> img.close() } }
            val selector = if (runCatching { p.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) }.getOrDefault(false)) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            runCatching { p.unbindAll(); p.bindToLifecycle(owner, selector, analysis) }
        }, ContextCompat.getMainExecutor(context))
        onDispose { provider?.unbindAll() }
    }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }
}
