package `in`.androidplay.pollingengine

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

private val neonPrimary = Color(0xFF00E5A8)
private val bg = Color(0xFF0B1015)
private val onBg = Color(0xFFE6F1FF)

private val darkScheme = darkColorScheme(
    primary = neonPrimary,
    onPrimary = Color(0xFF00110A),
    background = bg,
    onBackground = onBg,
    surface = Color(0xFF111823),
    onSurface = onBg,
    surfaceVariant = Color(0xFF172232),
    onSurfaceVariant = Color(0xFFB7C4D6),
    outline = Color(0xFF334155),
)

private fun buildTechTypography(
    base: Typography,
    family: FontFamily
): Typography {
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = family),
        displayMedium = base.displayMedium.copy(fontFamily = family),
        displaySmall = base.displaySmall.copy(fontFamily = family),
        headlineLarge = base.headlineLarge.copy(fontFamily = family),
        headlineMedium = base.headlineMedium.copy(fontFamily = family),
        headlineSmall = base.headlineSmall.copy(fontFamily = family),
        titleLarge = base.titleLarge.copy(fontFamily = family),
        titleMedium = base.titleMedium.copy(fontFamily = family),
        titleSmall = base.titleSmall.copy(fontFamily = family),
        bodyLarge = base.bodyLarge.copy(fontFamily = family),
        bodyMedium = base.bodyMedium.copy(fontFamily = family),
        bodySmall = base.bodySmall.copy(fontFamily = family),
        labelLarge = base.labelLarge.copy(fontFamily = family),
        labelMedium = base.labelMedium.copy(fontFamily = family),
        labelSmall = base.labelSmall.copy(fontFamily = family),
    )
}

private val baseTypography = Typography()
private val techTypography = buildTechTypography(baseTypography, FontFamily.SansSerif)

@Composable
internal fun PollingEngineTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = darkScheme,
        typography = techTypography,
        content = content
    )
}
