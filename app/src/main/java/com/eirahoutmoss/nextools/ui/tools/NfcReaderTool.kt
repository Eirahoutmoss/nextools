package com.eirahoutmoss.nextools.ui.tools

import android.app.Activity
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.radio.toHexId
import com.eirahoutmoss.nextools.ui.components.BadgeLevel
import com.eirahoutmoss.nextools.ui.components.BigReadout
import com.eirahoutmoss.nextools.ui.components.KeyValue
import com.eirahoutmoss.nextools.ui.components.NoticeBanner
import com.eirahoutmoss.nextools.ui.components.SectionCard
import com.eirahoutmoss.nextools.ui.components.ToolScaffold

data class NfcRead(val id: String, val techs: List<String>, val ndefType: String?, val maxSize: Int?, val writable: Boolean?, val records: List<String>, val writeResult: Int?)

private fun recordText(r: NdefRecord): String {
    r.toUri()?.let { return it.toString() }
    if (r.tnf == NdefRecord.TNF_WELL_KNOWN && r.type.contentEquals(NdefRecord.RTD_TEXT)) {
        val p = r.payload
        val langLen = p[0].toInt() and 0x3F
        val utf16 = (p[0].toInt() and 0x80) != 0
        return String(p, 1 + langLen, p.size - 1 - langLen, if (utf16) Charsets.UTF_16 else Charsets.UTF_8)
    }
    if (r.tnf == NdefRecord.TNF_MIME_MEDIA) return String(r.type, Charsets.US_ASCII) + " · " + r.payload.size + " B"
    return "TNF ${r.tnf} · " + r.payload.size + " B"
}

/** Okuma ve (istenirse) yazma. Arka plan iş parçacığında çalışır. */
private fun handleTag(tag: Tag, pending: NdefMessage?): NfcRead {
    var result: Int? = null
    if (pending != null) {
        result = runCatching {
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                ndef.connect(); try {
                    if (!ndef.isWritable) R.string.nfc_write_readonly
                    else if (ndef.maxSize < pending.toByteArray().size) R.string.nfc_write_too_big
                    else { ndef.writeNdefMessage(pending); R.string.nfc_write_ok }
                } finally { ndef.close() }
            } else {
                val f = NdefFormatable.get(tag) ?: return@runCatching R.string.nfc_write_unsupported
                f.connect(); try { f.format(pending); R.string.nfc_write_ok } finally { f.close() }
            }
        }.getOrDefault(R.string.nfc_write_failed)
    }
    val ndef = Ndef.get(tag)
    val records = runCatching {
        ndef?.let { n -> n.connect(); try { n.ndefMessage?.records?.map { recordText(it) } } finally { n.close() } }
    }.getOrNull() ?: ndef?.cachedNdefMessage?.records?.map { recordText(it) } ?: emptyList()
    return NfcRead(
        id = tag.id.toHexId(),
        techs = tag.techList.map { it.substringAfterLast('.') },
        ndefType = ndef?.type?.substringAfterLast('.'),
        maxSize = ndef?.maxSize,
        writable = ndef?.isWritable,
        records = records,
        writeResult = result,
    )
}

/** NFC Okuyucu: etiket türü, kimliği, içeriği; metin ya da bağlantı yazma (ör. demirbaş etiketi). */
@Composable
fun NfcReaderTool(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val adapter = remember { NfcAdapter.getDefaultAdapter(context) }
    var last by remember { mutableStateOf<NfcRead?>(null) }
    var writeMode by rememberSaveable { mutableStateOf(false) }
    var writeUrl by rememberSaveable { mutableStateOf(false) }
    var writeText by rememberSaveable { mutableStateOf("") }
    val pending = remember { arrayOfNulls<NdefMessage>(1) }
    val clipboard = LocalClipboardManager.current
    val started = rememberIsStarted()

    pending[0] = if (writeMode && writeText.isNotBlank()) NdefMessage(
        if (writeUrl) NdefRecord.createUri(writeText.trim()) else NdefRecord.createTextRecord(java.util.Locale.getDefault().language, writeText),
    ) else null

    DisposableEffect(started, adapter?.isEnabled) {
        if (started && activity != null && adapter?.isEnabled == true) {
            val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V
            adapter.enableReaderMode(activity, { tag ->
                val r = handleTag(tag, pending[0])
                activity.runOnUiThread { last = r; if (r.writeResult == R.string.nfc_write_ok) writeMode = false }
            }, flags, null)
        }
        onDispose { if (activity != null) runCatching { adapter?.disableReaderMode(activity) } }
    }

    ToolScaffold(ToolId.NFC_READER, onBack, {}, null, null, false, null, null) {
        if (adapter == null) { NoticeBanner(stringResource(R.string.passive_generic), BadgeLevel.ALARM); return@ToolScaffold }
        if (!adapter.isEnabled) {
            NoticeBanner(stringResource(R.string.nfc_off), BadgeLevel.ALARM)
            OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) } }) { Text(stringResource(R.string.nfc_open_settings)) }
        }
        val r = last
        BigReadout(
            value = r?.ndefType ?: r?.techs?.firstOrNull() ?: "—", unit = "",
            caption = stringResource(if (writeMode) R.string.nfc_hold_to_write else R.string.nfc_hold),
            badge = r?.let { stringResource(R.string.nfc_id, it.id) }, badgeLevel = BadgeLevel.NEUTRAL,
        )
        r?.writeResult?.let { NoticeBanner(stringResource(it), if (it == R.string.nfc_write_ok) BadgeLevel.GOOD else BadgeLevel.ALARM) }
        r?.let { t ->
            SectionCard(stringResource(R.string.nfc_tag)) {
                KeyValue(stringResource(R.string.nfc_uid), t.id)
                KeyValue(stringResource(R.string.nfc_techs), t.techs.joinToString(", "))
                t.maxSize?.let { KeyValue(stringResource(R.string.nfc_capacity), "$it B") }
                t.writable?.let { KeyValue(stringResource(R.string.nfc_writable), stringResource(if (it) R.string.yes else R.string.no)) }
            }
            SectionCard(stringResource(R.string.nfc_content)) {
                if (t.records.isEmpty()) Text(stringResource(R.string.nfc_empty), style = MaterialTheme.typography.bodySmall)
                t.records.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
                if (t.records.isNotEmpty()) OutlinedButton(onClick = { clipboard.setText(AnnotatedString(t.records.joinToString("\n"))) }) { Text(stringResource(R.string.color_copy)) }
            }
        }
        SectionCard(stringResource(R.string.nfc_write_title)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!writeUrl, { writeUrl = false }, label = { Text(stringResource(R.string.qr_text)) })
                FilterChip(writeUrl, { writeUrl = true }, label = { Text(stringResource(R.string.nfc_url)) })
            }
            OutlinedTextField(writeText, { writeText = it.take(500) }, label = { Text(stringResource(R.string.qr_text_label)) }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { writeMode = !writeMode }, enabled = writeText.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (writeMode) R.string.action_cancel else R.string.nfc_write_start))
            }
            Text(stringResource(R.string.nfc_write_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(stringResource(R.string.nfc_idcard_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
