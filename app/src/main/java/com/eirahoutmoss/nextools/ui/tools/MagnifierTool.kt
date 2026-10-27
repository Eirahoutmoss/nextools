package com.eirahoutmoss.nextools.ui.tools

import android.Manifest
import android.graphics.Bitmap
import androidx.camera.core.Camera
import androidx.camera.core.FocusMeteringAction
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.ui.Fmt
import com.eirahoutmoss.nextools.ui.components.CameraPreview
import com.eirahoutmoss.nextools.ui.components.SectionCard
import com.eirahoutmoss.nextools.ui.components.ToolScaffold

/** Büyüteç: en yüksek yakınlaştırma, ışık (flaş), dokununca odak, dondurma. */
@Composable
fun MagnifierTool(onBack: () -> Unit) {
    var camera by remember { mutableStateOf<Camera?>(null) }
    var view by remember { mutableStateOf<PreviewView?>(null) }
    var zoom by remember { mutableStateOf(0.6f) }
    var torch by remember { mutableStateOf(false) }
    var frozenBmp by remember { mutableStateOf<Bitmap?>(null) }

    ToolScaffold(
        ToolId.MAGNIFIER, onBack, {}, null, null,
        frozen = frozenBmp != null,
        onFreezeToggle = { frozenBmp = if (frozenBmp == null) view?.bitmap else null },
        onCalibrate = null,
    ) {
        PermissionGate(Manifest.permission.CAMERA, stringResource(R.string.camera_rationale)) {
            Box(Modifier.fillMaxWidth().aspectRatio(0.8f).clip(RoundedCornerShape(16.dp))) {
                CameraPreview(
                    Modifier.fillMaxSize().pointerInput(Unit) {
                        detectTapGestures { p ->
                            val v = view ?: return@detectTapGestures
                            val point = v.meteringPointFactory.createPoint(p.x, p.y)
                            camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
                        }
                    },
                    onCamera = { cam -> camera = cam; cam.cameraControl.setLinearZoom(zoom); cam.cameraControl.enableTorch(torch) },
                    onView = { view = it },
                )
                frozenBmp?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            }
            val ratio = camera?.cameraInfo?.zoomState?.value?.zoomRatio
            SectionCard(stringResource(R.string.mag_zoom, ratio?.let { Fmt.num(it.toDouble(), 1) + "×" } ?: "—")) {
                Slider(value = zoom, onValueChange = { zoom = it; camera?.cameraControl?.setLinearZoom(it) })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (camera?.cameraInfo?.hasFlashUnit() == true) {
                        FilterChip(torch, { torch = !torch; camera?.cameraControl?.enableTorch(torch) }, label = { Text(stringResource(R.string.mag_light)) })
                    }
                }
            }
            Text(stringResource(R.string.mag_tip), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
