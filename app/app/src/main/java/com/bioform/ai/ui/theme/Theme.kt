package com.bioform.ai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BioColorScheme = lightColorScheme(
    primary = BioBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8FF),
    onPrimaryContainer = BioNavy,
    secondary = BioSky,
    onSecondary = BioNavy,
    background = BioBackground,
    onBackground = BioText,
    surface = BioSurface,
    onSurface = BioText,
    surfaceVariant = Color(0xFFE8EFFA),
    onSurfaceVariant = BioMuted,
    error = BioAlert,
    onError = Color.White,
    errorContainer = Color(0xFFFDE2E2),
    onErrorContainer = Color(0xFF7A1010),
)

/** Tema de BioForm AI: siempre claro, con los colores de la marca. */
@Composable
fun BioFormAITheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = BioColorScheme, typography = Typography, content = content)
}
