package com.eirahoutmoss.nextools.ui.tools

import android.app.Activity
import android.content.Intent
import android.nfc.NfcAdapter
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.idcard.IdCardData
import com.eirahoutmoss.nextools.idcard.IdCardException
import com.eirahoutmoss.nextools.idcard.IdError
import com.eirahoutmoss.nextools.idcard.IdStep
import com.eirahoutmoss.nextools.idcard.JmrtdReader
import com.eirahoutmoss.nextools.idcard.fromIcaoDate
import com.eirahoutmoss.nextools.idcard.toIcaoDate
import com.eirahoutmoss.nextools.ui.Fmt
import com.eirahoutmoss.nextools.ui.components.BadgeLevel
import com.eirahoutmoss.nextools.ui.components.BigReadout
import com.eirahoutmoss.nextools.ui.components.KeyValue
import com.eirahoutmoss.nextools.ui.components.NoticeBanner
import com.eirahoutmoss.nextools.ui.components.SectionCard
import com.eirahoutmoss.nextools.ui.components.StatusDot
import com.eirahoutmoss.nextools.ui.components.ToolScaffold
import com.eirahoutmoss.nextools.ui.theme.LocalInstrument

private fun stepText(s: IdStep) = when (s) {
    IdStep.CONNECTING -> R.string.id_step_connect
    IdStep.ACCESS -> R.string.id_step_access
    IdStep.READING_DATA -> R.string.id_step_data
    IdStep.READING_PHOTO -> R.string.id_step_photo
    IdStep.VERIFYING -> R.string.id_step_verify
}

private fun errorText(e: IdError) = when (e) {
    IdError.ACCESS_DENIED -> R.string.id_err_access
    IdError.TAG_LOST -> R.string.id_err_lost
    IdError.NOT_EMRTD -> R.string.id_err_not_emrtd
    IdError.UNKNOWN -> R.string.id_err_unknown
}

/**
 * Kimlik Kartı Okuyucu: çipli kimlik kartı / e-pasaport (ICAO 9303). Erişim anahtarı kartın üzerindeki
 * belge numarası + doğum + son geçerlilik tarihidir. Veriler yalnızca ekranda gösterilir; kaydedilmez,
 * ekran görüntüsü alınamaz (FLAG_SECURE), araçtan çıkınca bellekten silinir.
 */
@Composable
fun IdCardTool(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val adapter = remember { NfcAdapter.getDefaultAdapter(context) }
    val started = rememberIsStarted()
    var docNo by remember { mutableStateOf("") }
    var birth by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf("") }
    var step by remember { mutableStateOf<IdStep?>(null) }
    var data by remember { mutableStateOf<IdCardData?>(null) }
    var error by remember { mutableStateOf<IdError?>(null) }
    val birthIcao = toIcaoDate(birth); val expiryIcao = toIcaoDate(expiry)
    val ready = docNo.trim().length >= 6 && birthIcao != null && expiryIcao != null
    val key = rememberUpdatedState(Triple(docNo, birthIcao, expiryIcao))

    // Bu ekrandayken ekran görüntüsü ve son uygulamalar önizlemesi engellenir.
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE); data = null }
    }

    DisposableEffect(started, adapter?.isEnabled, ready) {
        if (started && ready && activity != null && adapter?.isEnabled == true) {
            adapter.enableReaderMode(activity, { tag ->
                val (n, b, e) = key.value
                if (b == null || e == null) return@enableReaderMode
                activity.runOnUiThread { error = null; data = null; step = IdStep.CONNECTING }
                try {
                    val d = JmrtdReader.read(tag, n, b, e) { s -> activity.runOnUiThread { step = s } }
                    activity.runOnUiThread { data = d; step = null }
                } catch (x: IdCardException) {
                    activity.runOnUiThread { error = x.error; step = null }
                } catch (x: Exception) {
                    activity.runOnUiThread { error = IdError.UNKNOWN; step = null }
                }
            }, NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null)
        }
        onDispose { if (activity != null) runCatching { adapter?.disableReaderMode(activity) } }
    }

    ToolScaffold(ToolId.ID_CARD, onBack, {}, null, null, false, null, null) {
        if (adapter == null) { NoticeBanner(stringResource(R.string.passive_generic), BadgeLevel.ALARM); return@ToolScaffold }
        if (!adapter.isEnabled) {
            NoticeBanner(stringResource(R.string.nfc_off), BadgeLevel.ALARM)
            OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) } }) { Text(stringResource(R.string.nfc_open_settings)) }
        }
        NoticeBanner(stringResource(R.string.id_privacy), BadgeLevel.GOOD)
        val d = data
        if (d == null) {
            SectionCard(stringResource(R.string.id_access_title)) {
                Text(stringResource(R.string.id_access_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(docNo, { docNo = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(9) }, label = { Text(stringResource(R.string.id_doc_no)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false))
                OutlinedTextField(birth, { birth = it.take(10) }, label = { Text(stringResource(R.string.id_birth)) }, placeholder = { Text("GG.AA.YYYY") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), isError = birth.length >= 8 && birthIcao == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(expiry, { expiry = it.take(10) }, label = { Text(stringResource(R.string.id_expiry)) }, placeholder = { Text("GG.AA.YYYY") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), isError = expiry.length >= 8 && expiryIcao == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            BigReadout(
                value = "—", unit = "",
                caption = stringResource(if (ready) R.string.id_hold else R.string.id_fill_first),
                badge = null, badgeLevel = BadgeLevel.NEUTRAL,
            )
            step?.let { s ->
                Text(stringResource(stepText(s)), style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(progress = { (s.ordinal + 1) / IdStep.entries.size.toFloat() }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.id_keep_still), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            error?.let { NoticeBanner(stringResource(errorText(it)), BadgeLevel.ALARM) }
            Text(stringResource(R.string.id_tip), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            CardView(d)
            OutlinedButton(onClick = { data = null; error = null }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.id_clear)) }
        }
    }
}

@Composable
private fun CardView(d: IdCardData) {
    val inst = LocalInstrument.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        if (d.photo != null) {
            Image(d.photo.asImageBitmap(), null, Modifier.width(110.dp).clip(RoundedCornerShape(8.dp)))
        }
        Column(Modifier.weight(1f)) {
            Text(d.surname, style = MaterialTheme.typography.titleLarge)
            Text(d.givenNames, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(d.documentCode + " · " + d.issuingState, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (d.photo == null && d.photoBytes > 0) NoticeBanner(stringResource(R.string.id_photo_unsupported, d.photoMime ?: "?"), BadgeLevel.NEUTRAL)
    SectionCard(stringResource(R.string.id_fields)) {
        KeyValue(stringResource(R.string.id_doc_no), d.documentNumber)
        d.personalNumber?.let { KeyValue(stringResource(R.string.id_personal_no), it) }
        KeyValue(stringResource(R.string.id_nationality), d.nationality)
        KeyValue(stringResource(R.string.id_gender), d.gender)
        KeyValue(stringResource(R.string.id_birth), fromIcaoDate(d.birthDate, future = false))
        KeyValue(stringResource(R.string.id_expiry), fromIcaoDate(d.expiryDate, future = true))
        KeyValue(stringResource(R.string.id_access_method), d.accessMethod)
    }
    SectionCard(stringResource(R.string.id_verify_title)) {
        val p = d.passive
        @Composable fun line(ok: Boolean?, text: String) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(when (ok) { true -> inst.good; false -> inst.alarm; null -> inst.passive })
                Spacer(Modifier.width(8.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
        }
        p.groups.toSortedMap().forEach { (dg, ok) -> line(ok, stringResource(if (ok) R.string.id_dg_ok else R.string.id_dg_bad, dg)) }
        if (p.groups.isEmpty()) line(null, stringResource(R.string.id_no_sod))
        line(p.signatureOk, stringResource(when (p.signatureOk) { true -> R.string.id_sig_ok; false -> R.string.id_sig_bad; null -> R.string.id_sig_unknown }))
        p.signer?.let { KeyValue(stringResource(R.string.id_signer), it) }
        p.signerValidUntilMs?.let { KeyValue(stringResource(R.string.id_signer_until), Fmt.date(it)) }
        Text(stringResource(R.string.id_csca_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
