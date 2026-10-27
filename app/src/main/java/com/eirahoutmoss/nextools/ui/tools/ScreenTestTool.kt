package com.eirahoutmoss.nextools.ui.tools

import android.app.Activity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.ui.components.SectionCard
import com.eirahoutmoss.nextools.ui.components.ToolScaffold

private val TEST_COLORS = listOf(Color.Black, Color.White, Color.Red, Color(0xFF00FF00), Color.Blue, Color(0xFF808080), Color(0xFF202020))

/** Ekran Testi: ölü piksel (düz renkler), yanık izi (gri), dokunmatik ızgara. */
@Composable
fun ScreenTestTool(onBack: () -> Unit) {
    var mode by remember { mutableStateOf<String?>(null) }
    ToolScaffold(ToolId.SCREEN_TEST, onBack, {}, null, null, false, null, null) {
        SectionCard(stringResource(R.string.screen_pixels)) {
            Text(stringResource(R.string.screen_pixels_hint), style = MaterialTheme.typography.bodySmall)
            Button(onClick = { mode = "colors" }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.screen_start)) }
        }
        SectionCard(stringResource(R.string.screen_touch)) {
            Text(stringResource(R.string.screen_touch_hint), style = MaterialTheme.typography.bodySmall)
            Button(onClick = { mode = "touch" }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.screen_start)) }
        }
    }
    when (mode) {
        "colors" -> FullScreen({ mode = null }) { ColorCycle { mode = null } }
        "touch" -> FullScreen({ mode = null }) { TouchGrid() }
    }
}

/** Sistem çubukları gizli tam ekran; geri tuşu kapatır. */
@Composable
private fun FullScreen(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val activity = LocalContext.current as? Activity
        DisposableEffect(Unit) {
            val w = activity?.window
            val c = w?.let { WindowCompat.getInsetsController(it, it.decorView) }
            c?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c?.hide(WindowInsetsCompat.Type.systemBars())
            onDispose { c?.show(WindowInsetsCompat.Type.systemBars()) }
        }
        Box(Modifier.fillMaxSize()) { content() }
    }
}

@Composable
private fun ColorCycle(onDone: () -> Unit) {
    var i by remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize().background(TEST_COLORS[i]).clickable { if (i + 1 >= TEST_COLORS.size) onDone() else i++ }, contentAlignment = Alignment.BottomCenter) {
        if (i == 0) Text(stringResource(R.string.screen_tap_next), color = Color.DarkGray, modifier = Modifier.padding(24.dp))
    }
}

@Composable
private fun TouchGrid() {
    val cols = 8; val rows = 14
    val touched = remember { mutableStateOf(BooleanArray(cols * rows)) }
    var pointers by remember { mutableIntStateOf(0) }
    var maxPointers by remember { mutableIntStateOf(0) }
    val done = touched.value.count { it }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        val down = e.changes.filter { it.pressed }
                        pointers = down.size; if (down.size > maxPointers) maxPointers = down.size
                        if (down.isNotEmpty()) {
                            val arr = touched.value.copyOf()
                            down.forEach { ch ->
                                val cx = (ch.position.x / size.width * cols).toInt().coerceIn(0, cols - 1)
                                val cy = (ch.position.y / size.height * rows).toInt().coerceIn(0, rows - 1)
                                arr[cy * cols + cx] = true
                            }
                            touched.value = arr
                        }
                    }
                }
            },
        ) {
            val cw = size.width / cols; val ch = size.height / rows
            for (y in 0 until rows) for (x in 0 until cols) {
                val on = touched.value[y * cols + x]
                drawRect(if (on) Color(0xFF16A34A) else Color(0xFF1F2937), topLeft = Offset(x * cw + 1, y * ch + 1), size = Size(cw - 2, ch - 2))
            }
        }
        Text(
            stringResource(R.string.screen_touch_status, done, cols * rows, pointers, maxPointers),
            color = Color.White, modifier = Modifier.align(Alignment.TopCenter).padding(top = 32.dp).background(Color(0x99000000)).padding(8.dp),
        )
    }
}
