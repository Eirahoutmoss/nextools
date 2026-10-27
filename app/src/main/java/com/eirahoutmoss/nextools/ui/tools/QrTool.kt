package com.eirahoutmoss.nextools.ui.tools

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.qr.QrCode
import com.eirahoutmoss.nextools.core.qr.QrEcc
import com.eirahoutmoss.nextools.core.qr.QrEncoder
import com.eirahoutmoss.nextools.core.qr.WifiQr
import com.eirahoutmoss.nextools.ui.components.KeyValue
import com.eirahoutmoss.nextools.ui.components.NoticeBanner
import com.eirahoutmoss.nextools.ui.components.BadgeLevel
import com.eirahoutmoss.nextools.ui.components.SectionCard
import com.eirahoutmoss.nextools.ui.components.ToolScaffold
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.io.File

private class ScanResult(val raw: String, val type: Int, val wifiSsid: String?, val wifiPassword: String?)

fun QrCode.toBitmap(scale: Int = 12, border: Int = 4): Bitmap {
    val n = size + border * 2
    val bmp = Bitmap.createBitmap(n * scale, n * scale, Bitmap.Config.ARGB_8888)
    bmp.eraseColor(android.graphics.Color.WHITE)
    for (y in 0 until size) for (x in 0 until size) if (this[x, y]) {
        for (dy in 0 until scale) for (dx in 0 until scale) bmp.setPixel((x + border) * scale + dx, (y + border) * scale + dy, android.graphics.Color.BLACK)
    }
    return bmp
}

/** QR / Barkod: Google kod tarayıcıyla okuma (kamera izni gerekmez), cihazda QR üretme (metin, Wi-Fi). */
@Composable
fun QrTool(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var tab by rememberSaveable { mutableStateOf(0) }
    var result by remember { mutableStateOf<ScanResult?>(null) }
    var scanError by remember { mutableStateOf<String?>(null) }

    ToolScaffold(ToolId.QR, onBack, {}, null, null, false, null, null) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(tab == 0, { tab = 0 }, SegmentedButtonDefaults.itemShape(0, 2)) { Text(stringResource(R.string.qr_tab_scan)) }
            SegmentedButton(tab == 1, { tab = 1 }, SegmentedButtonDefaults.itemShape(1, 2)) { Text(stringResource(R.string.qr_tab_create)) }
        }
        if (tab == 0) {
            Button(onClick = {
                scanError = null
                GmsBarcodeScanning.getClient(context).startScan()
                    .addOnSuccessListener { b: Barcode ->
                        result = ScanResult(b.rawValue.orEmpty(), b.valueType, b.wifi?.ssid, b.wifi?.password)
                    }
                    .addOnFailureListener { e: Exception -> scanError = context.getString(R.string.qr_scan_failed, e.message ?: e.javaClass.simpleName) }
            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.qr_scan)) }
            Text(stringResource(R.string.qr_scan_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            scanError?.let { NoticeBanner(it, BadgeLevel.ALARM) }
            result?.let { r ->
                SectionCard(stringResource(R.string.qr_result)) {
                    Text(r.raw, style = MaterialTheme.typography.bodyLarge)
                    if (r.type == Barcode.TYPE_WIFI) {
                        KeyValue(stringResource(R.string.qr_wifi_ssid), r.wifiSsid.orEmpty())
                        KeyValue(stringResource(R.string.qr_wifi_password), r.wifiPassword.orEmpty())
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { clipboard.setText(AnnotatedString(if (r.type == Barcode.TYPE_WIFI) r.wifiPassword.orEmpty() else r.raw)) }) {
                            Text(stringResource(if (r.type == Barcode.TYPE_WIFI) R.string.qr_copy_password else R.string.color_copy))
                        }
                        if (r.type == Barcode.TYPE_URL || r.raw.startsWith("http://") || r.raw.startsWith("https://")) {
                            OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.raw))) } }) { Text(stringResource(R.string.qr_open_link)) }
                        }
                    }
                    if (r.raw.startsWith("http://")) NoticeBanner(stringResource(R.string.qr_http_warning))
                }
            }
        } else {
            QrCreator()
        }
    }
}

@Composable
private fun QrCreator() {
    val context = LocalContext.current
    var mode by rememberSaveable { mutableStateOf(0) }
    var text by rememberSaveable { mutableStateOf("") }
    var ssid by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var security by rememberSaveable { mutableStateOf(WifiQr.Security.WPA) }
    var ecc by rememberSaveable { mutableStateOf(QrEcc.M) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(mode == 0, { mode = 0 }, label = { Text(stringResource(R.string.qr_text)) })
        FilterChip(mode == 1, { mode = 1 }, label = { Text(stringResource(R.string.qr_wifi)) })
    }
    val content = if (mode == 0) text else if (ssid.isBlank()) "" else WifiQr.text(ssid, password, security)
    if (mode == 0) {
        OutlinedTextField(text, { text = it.take(1500) }, label = { Text(stringResource(R.string.qr_text_label)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
    } else {
        OutlinedTextField(ssid, { ssid = it.take(64) }, label = { Text(stringResource(R.string.qr_wifi_ssid)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WifiQr.Security.entries.forEach { s ->
                FilterChip(security == s, { security = s }, label = { Text(if (s == WifiQr.Security.NONE) stringResource(R.string.qr_wifi_open) else s.code) })
            }
        }
        if (security != WifiQr.Security.NONE) {
            OutlinedTextField(password, { password = it.take(64) }, label = { Text(stringResource(R.string.qr_wifi_password)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.qr_ecc), style = MaterialTheme.typography.labelLarge)
        QrEcc.entries.forEach { e -> FilterChip(ecc == e, { ecc = e }, label = { Text(e.name) }) }
    }
    if (content.isBlank()) return
    val code = remember(content, ecc) { runCatching { QrEncoder.encodeText(content, ecc) }.getOrNull() }
    if (code == null) { NoticeBanner(stringResource(R.string.qr_too_long), BadgeLevel.ALARM); return }
    val bmp = remember(code) { code.toBitmap() }
    Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp).aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(Color.White)) {
        Image(bmp.asImageBitmap(), null, contentScale = ContentScale.Fit, filterQuality = FilterQuality.None, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
    }
    Text(stringResource(R.string.qr_info, code.version, code.ecc.name, code.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Button(onClick = {
        val f = File(File(context.cacheDir, "export").apply { mkdirs() }, "NexTools_QR.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
        val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, context.getString(R.string.share_via)))
    }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.qr_share_png)) }
}
