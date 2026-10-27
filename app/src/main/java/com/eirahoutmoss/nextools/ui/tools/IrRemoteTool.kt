package com.eirahoutmoss.nextools.ui.tools

import android.content.Context
import android.hardware.ConsumerIrManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.measure.IrEncoder
import com.eirahoutmoss.nextools.ui.components.BadgeLevel
import com.eirahoutmoss.nextools.ui.components.KeyValue
import com.eirahoutmoss.nextools.ui.components.NoticeBanner
import com.eirahoutmoss.nextools.ui.components.SectionCard
import com.eirahoutmoss.nextools.ui.components.ToolScaffold

private enum class IrProto { NEC, SAMSUNG, SONY, RC5 }

private enum class IrKey { POWER, VOL_UP, VOL_DOWN, MUTE, CH_UP, CH_DOWN }

/** Hazır TV kodları (yaygın kod tabloları; model ve yıla göre farklı olabilir). */
private class IrBrand(val nameRes: Int, val proto: IrProto, val address: Int, val keys: Map<IrKey, Int>)

private val BRANDS = listOf(
    IrBrand(R.string.ir_samsung, IrProto.SAMSUNG, 0x07, mapOf(IrKey.POWER to 0x02, IrKey.VOL_UP to 0x07, IrKey.VOL_DOWN to 0x0B, IrKey.MUTE to 0x0F, IrKey.CH_UP to 0x12, IrKey.CH_DOWN to 0x10)),
    IrBrand(R.string.ir_lg, IrProto.NEC, 0x04, mapOf(IrKey.POWER to 0x08, IrKey.VOL_UP to 0x02, IrKey.VOL_DOWN to 0x03, IrKey.MUTE to 0x09, IrKey.CH_UP to 0x00, IrKey.CH_DOWN to 0x01)),
    IrBrand(R.string.ir_sony, IrProto.SONY, 0x01, mapOf(IrKey.POWER to 21, IrKey.VOL_UP to 18, IrKey.VOL_DOWN to 19, IrKey.MUTE to 20, IrKey.CH_UP to 16, IrKey.CH_DOWN to 17)),
    IrBrand(R.string.ir_philips, IrProto.RC5, 0x00, mapOf(IrKey.POWER to 12, IrKey.VOL_UP to 16, IrKey.VOL_DOWN to 17, IrKey.MUTE to 13, IrKey.CH_UP to 32, IrKey.CH_DOWN to 33)),
)

private fun keyText(k: IrKey) = when (k) {
    IrKey.POWER -> R.string.ir_power
    IrKey.VOL_UP -> R.string.ir_vol_up
    IrKey.VOL_DOWN -> R.string.ir_vol_down
    IrKey.MUTE -> R.string.ir_mute
    IrKey.CH_UP -> R.string.ir_ch_up
    IrKey.CH_DOWN -> R.string.ir_ch_down
}

private fun encode(p: IrProto, address: Int, command: Int, toggle: Boolean): IrEncoder.Signal = when (p) {
    IrProto.NEC -> IrEncoder.nec(address, command)
    IrProto.SAMSUNG -> IrEncoder.samsung32(address, command)
    IrProto.SONY -> IrEncoder.sirc12(address, command)
    IrProto.RC5 -> IrEncoder.rc5(address, command, toggle)
}

/** IR Kumanda: kızılötesi vericisi olan telefonlarda TV kumandası ve elle kod gönderme. Klima kodları desteklenmez. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IrRemoteTool(onBack: () -> Unit) {
    val context = LocalContext.current
    val ir = remember { context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager }
    var brand by rememberSaveable { mutableStateOf(0) }
    var proto by rememberSaveable { mutableStateOf(IrProto.NEC) }
    var addr by rememberSaveable { mutableStateOf("04") }
    var cmd by rememberSaveable { mutableStateOf("08") }
    var status by remember { mutableStateOf<String?>(null) }
    val toggle = remember { booleanArrayOf(false) }

    fun send(p: IrProto, a: Int, c: Int) {
        val m = ir ?: return
        toggle[0] = !toggle[0]
        val s = encode(p, a, c, toggle[0])
        val supported = runCatching { m.carrierFrequencies }.getOrNull()?.any { s.carrierHz in it.minFrequency..it.maxFrequency } ?: true
        status = try {
            m.transmit(if (supported) s.carrierHz else 38_000, s.pattern)
            context.getString(R.string.ir_sent, p.name, "%02X".format(a), "%02X".format(c))
        } catch (e: Exception) { context.getString(R.string.ir_failed, e.message ?: e.javaClass.simpleName) }
    }

    ToolScaffold(ToolId.IR_REMOTE, onBack, {}, null, null, false, null, null) {
        if (ir == null || !ir.hasIrEmitter()) { NoticeBanner(stringResource(R.string.ir_none), BadgeLevel.ALARM); return@ToolScaffold }
        SectionCard(stringResource(R.string.ir_tv)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BRANDS.forEachIndexed { i, b -> FilterChip(brand == i, { brand = i }, label = { Text(stringResource(b.nameRes)) }) }
            }
            val b = BRANDS[brand]
            Button(onClick = { send(b.proto, b.address, b.keys.getValue(IrKey.POWER)) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ir_power)) }
            listOf(listOf(IrKey.VOL_UP, IrKey.CH_UP), listOf(IrKey.VOL_DOWN, IrKey.CH_DOWN), listOf(IrKey.MUTE)).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { k -> FilledTonalButton(onClick = { send(b.proto, b.address, b.keys.getValue(k)) }, modifier = Modifier.weight(1f)) { Text(stringResource(keyText(k))) } }
                }
            }
            Text(stringResource(R.string.ir_codes_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SectionCard(stringResource(R.string.ir_custom)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IrProto.entries.forEach { p -> FilterChip(proto == p, { proto = p }, label = { Text(p.name) }) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(addr, { addr = it.uppercase().filter { c -> c in "0123456789ABCDEF" }.take(2) }, label = { Text(stringResource(R.string.ir_address)) }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(cmd, { cmd = it.uppercase().filter { c -> c in "0123456789ABCDEF" }.take(2) }, label = { Text(stringResource(R.string.ir_command)) }, singleLine = true, modifier = Modifier.weight(1f))
            }
            val a = addr.toIntOrNull(16); val c = cmd.toIntOrNull(16)
            Button(onClick = { if (a != null && c != null) send(proto, a, c) }, enabled = a != null && c != null, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ir_send)) }
            Text(stringResource(R.string.ir_hex_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        status?.let { KeyValue(stringResource(R.string.ir_last), it) }
        runCatching { ir.carrierFrequencies }.getOrNull()?.let { r ->
            if (r.isNotEmpty()) KeyValue(stringResource(R.string.ir_freqs), r.joinToString { "${it.minFrequency / 1000}–${it.maxFrequency / 1000} kHz" })
        }
        NoticeBanner(stringResource(R.string.ir_ac_note), BadgeLevel.NEUTRAL)
    }
}
