package com.eirahoutmoss.nextools.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.ui.theme.LocalInstrument
import com.eirahoutmoss.nextools.ui.theme.NexColors
import kotlinx.coroutines.delay

/** Uygulama simgesiyle aynı logo. */
@Composable
fun NexToolsLogo(size: Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.24f))
            .background(Brush.linearGradient(listOf(Color(0xFF16212D), Color(0xFF05080C))))
            .border(1.dp, NexColors.LogoBlue.copy(alpha = 0.35f), RoundedCornerShape(size * 0.24f)),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = "NexTools", modifier = Modifier.fillMaxSize())
    }
}

/** "Nex" ön plan rengi, "Tools" vurgu rengi. */
@Composable
fun Wordmark(fontSize: TextUnit, nexColor: Color = MaterialTheme.colorScheme.onBackground) {
    val accent = LocalInstrument.current.accent
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = nexColor, fontWeight = FontWeight.Black)) { append("Nex") }
            withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Black)) { append("Tools") }
        },
        fontSize = fontSize,
    )
}

/** Açılış ekranı: kısa süre görünür, sonra solarak kaybolur. */
@Composable
fun SplashOverlay(onFinished: () -> Unit) {
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        delay(900)
        alpha.animateTo(0f, tween(300))
        onFinished()
    }
    Box(
        Modifier.fillMaxSize().alpha(alpha.value)
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } }
            .background(Brush.verticalGradient(listOf(NexColors.Night, Color(0xFF0F1A24), NexColors.Night))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            NexToolsLogo(112.dp)
            Spacer(Modifier.size(22.dp))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Black)) { append("Nex") }
                    withStyle(SpanStyle(color = NexColors.Cyan, fontWeight = FontWeight.Black)) { append("Tools") }
                },
                fontSize = 44.sp,
            )
            Spacer(Modifier.size(10.dp))
            Text(
                stringResource(R.string.tagline_upper),
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 3.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }
    }
}

private data class FamilyApp(val name: String, val icon: ImageVector?)

/** Konsept görselindeki "Nex Ailesi" şeridi. */
private val FAMILY = listOf(
    FamilyApp("NexNews", Icons.Outlined.Newspaper),
    FamilyApp("NexDesk", Icons.Outlined.DesktopWindows),
    FamilyApp("NexOrigin", Icons.Outlined.AccountTree),
    FamilyApp("NexView", Icons.Outlined.Image),
    FamilyApp("NexTools", null),
)

@Composable
fun NexFamily() {
    val accent = LocalInstrument.current.accent
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        FAMILY.forEach { app ->
            val current = app.icon == null
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (current) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.weight(1f).then(if (current) Modifier.border(1.5.dp, accent, RoundedCornerShape(12.dp)) else Modifier),
            ) {
                Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (app.icon != null) Icon(app.icon, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(26.dp))
                    else NexToolsLogo(26.dp)
                    Spacer(Modifier.size(4.dp))
                    Text(app.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, fontSize = 10.sp)
                }
            }
        }
    }
}
