package xyz.lilsus.raylsuite.feature.paymenthub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import xyz.lilsus.raylsuite.core.ui.format.rememberAmountFormatter
import xyz.lilsus.raylsuite.feature.paymenthub.HubAccent
import xyz.lilsus.raylsuite.feature.paymenthub.HubIcon
import xyz.lilsus.raylsuite.feature.paymenthub.render.HubItemDetail
import xyz.lilsus.raylsuite.feature.paymenthub.render.HubItemRenderModel

fun HubIcon.vector(): ImageVector = when (this) {
    HubIcon.Person -> Icons.Filled.Person
    HubIcon.Group -> Icons.Filled.Group
    HubIcon.Store -> Icons.Filled.Storefront
    HubIcon.Restaurant -> Icons.Filled.Restaurant
    HubIcon.Coffee -> Icons.Filled.LocalCafe
    HubIcon.Gift -> Icons.Filled.CardGiftcard
    HubIcon.Heart -> Icons.Filled.Favorite
    HubIcon.Star -> Icons.Filled.Star
    HubIcon.Bolt -> Icons.Filled.Bolt
    HubIcon.Home -> Icons.Filled.Home
    HubIcon.Wallet -> Icons.Filled.Wallet
    HubIcon.Work -> Icons.Filled.Work
}

/** Accent container colors from the suite palette, tuned per theme. */
@Composable
fun HubAccent.containerColor(): Color {
    val dark = isSystemInDarkTheme() || MaterialTheme.colorScheme.background.luminance() < 0.5f
    return when (this) {
        HubAccent.Orange -> if (dark) Color(0xFF5C3700) else Color(0xFFFFE2C2)
        HubAccent.Blue -> if (dark) Color(0xFF254C6B) else Color(0xFFD8E2F0)
        HubAccent.Green -> if (dark) Color(0xFF00513B) else Color(0xFFA6F2D4)
        HubAccent.Purple -> if (dark) Color(0xFF4A2D6B) else Color(0xFFE8DAFF)
        HubAccent.Pink -> if (dark) Color(0xFF6B2A47) else Color(0xFFFFD8E6)
        HubAccent.Teal -> if (dark) Color(0xFF004F58) else Color(0xFFB2EBF2)
        HubAccent.Amber -> if (dark) Color(0xFF5C4500) else Color(0xFFFFECB3)
        HubAccent.Slate -> if (dark) Color(0xFF3A4A56) else Color(0xFFDDE6F0)
    }
}

@Composable
fun HubAccent.contentColor(): Color {
    val dark = isSystemInDarkTheme() || MaterialTheme.colorScheme.background.luminance() < 0.5f
    return when (this) {
        HubAccent.Orange -> if (dark) Color(0xFFFFE2C2) else Color(0xFF4A2600)
        HubAccent.Blue -> if (dark) Color(0xFFD8E2F0) else Color(0xFF0C2035)
        HubAccent.Green -> if (dark) Color(0xFFA6F2D4) else Color(0xFF002117)
        HubAccent.Purple -> if (dark) Color(0xFFE8DAFF) else Color(0xFF2A0A4D)
        HubAccent.Pink -> if (dark) Color(0xFFFFD8E6) else Color(0xFF4D0E2C)
        HubAccent.Teal -> if (dark) Color(0xFFB2EBF2) else Color(0xFF00363B)
        HubAccent.Amber -> if (dark) Color(0xFFFFECB3) else Color(0xFF3F2E00)
        HubAccent.Slate -> if (dark) Color(0xFFDDE6F0) else Color(0xFF1F2A33)
    }
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

/** Round glyph with the item icon (or the first title letter) on its accent. */
@Composable
fun HubItemGlyph(item: HubItemRenderModel, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    HubGlyph(
        icon = item.icon,
        accent = item.accent,
        fallbackText = item.title.take(1).uppercase(),
        modifier = modifier,
        size = size
    )
}

@Composable
fun HubGlyph(
    icon: HubIcon?,
    accent: HubAccent?,
    fallbackText: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    val container = accent?.containerColor() ?: MaterialTheme.colorScheme.surfaceContainerHighest
    val content = accent?.contentColor() ?: MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier =
            modifier
                .size(size)
                .background(container, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon.vector(),
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(size * 0.5f)
            )
        } else {
            Text(
                text = fallbackText,
                color = content,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
        }
    }
}

/** Formatted preset amount, or `null` for ask-every-time targets and groups. */
@Composable
fun HubItemRenderModel.amountBadge(): String? {
    val amount = (detail as? HubItemDetail.Target)?.presetAmount ?: return null
    return rememberAmountFormatter().format(amount)
}
