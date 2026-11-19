package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Immutable
data class StudioExtendedColors(
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val codeBackground: Color,
    val codeText: Color
)

val LocalStudioColors = staticCompositionLocalOf {
    StudioExtendedColors(
        success = GoogleGreenLight,
        successContainer = GoogleGreenContainerLight,
        onSuccessContainer = OnGoogleGreenContainerLight,
        warning = GoogleBrandYellow,
        codeBackground = CodeBlockBackgroundLight,
        codeText = CodeBlockText
    )
}

private val GoogleDarkColorScheme = darkColorScheme(
    primary = GoogleBlueDark,
    onPrimary = OnGoogleBlueDark,
    primaryContainer = GoogleBlueContainerDark,
    onPrimaryContainer = OnGoogleBlueContainerDark,
    secondary = GoogleSkyDark,
    onSecondary = OnGoogleSkyDark,
    secondaryContainer = GoogleSkyContainerDark,
    onSecondaryContainer = OnGoogleSkyContainerDark,
    tertiary = GoogleGreenDark,
    onTertiary = Color(0xFF0A3818),
    tertiaryContainer = GoogleGreenContainerDark,
    onTertiaryContainer = OnGoogleGreenContainerDark,
    background = GoogleDarkBackground,
    onBackground = GoogleDarkOnSurface,
    surface = GoogleDarkSurface,
    onSurface = GoogleDarkOnSurface,
    surfaceVariant = GoogleDarkSurfaceContainerHigh,
    onSurfaceVariant = GoogleDarkOnSurfaceVariant,
    surfaceContainerLowest = GoogleDarkSurfaceContainerLowest,
    surfaceContainerLow = GoogleDarkSurfaceContainerLow,
    surfaceContainer = GoogleDarkSurfaceContainer,
    surfaceContainerHigh = GoogleDarkSurfaceContainerHigh,
    surfaceContainerHighest = GoogleDarkSurfaceContainerHighest,
    outline = GoogleDarkOutline,
    outlineVariant = GoogleDarkOutlineVariant,
    error = GoogleDarkError,
    onError = Color(0xFF601410)
)

private val GoogleLightColorScheme = lightColorScheme(
    primary = GoogleBlueLight,
    onPrimary = OnGoogleBlueLight,
    primaryContainer = GoogleBlueContainerLight,
    onPrimaryContainer = OnGoogleBlueContainerLight,
    secondary = GoogleSkyLight,
    onSecondary = OnGoogleSkyLight,
    secondaryContainer = GoogleSkyContainerLight,
    onSecondaryContainer = OnGoogleSkyContainerLight,
    tertiary = GoogleGreenLight,
    onTertiary = Color.White,
    tertiaryContainer = GoogleGreenContainerLight,
    onTertiaryContainer = OnGoogleGreenContainerLight,
    background = GoogleLightBackground,
    onBackground = GoogleLightOnSurface,
    surface = GoogleLightSurface,
    onSurface = GoogleLightOnSurface,
    surfaceVariant = GoogleLightSurfaceContainer,
    onSurfaceVariant = GoogleLightOnSurfaceVariant,
    surfaceContainerLowest = GoogleLightSurfaceContainerLowest,
    surfaceContainerLow = GoogleLightSurfaceContainerLow,
    surfaceContainer = GoogleLightSurfaceContainer,
    surfaceContainerHigh = GoogleLightSurfaceContainerHigh,
    surfaceContainerHighest = GoogleLightSurfaceContainerHighest,
    outline = GoogleLightOutline,
    outlineVariant = GoogleLightOutlineVariant,
    error = GoogleLightError,
    onError = Color.White
)

private val GoogleShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun PromptStudioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> GoogleDarkColorScheme
        else -> GoogleLightColorScheme
    }

    val extendedColors = if (darkTheme) {
        StudioExtendedColors(
            success = GoogleGreenDark,
            successContainer = GoogleGreenContainerDark,
            onSuccessContainer = OnGoogleGreenContainerDark,
            warning = GoogleBrandYellow,
            codeBackground = CodeBlockBackgroundDark,
            codeText = CodeBlockText
        )
    } else {
        StudioExtendedColors(
            success = GoogleGreenLight,
            successContainer = GoogleGreenContainerLight,
            onSuccessContainer = OnGoogleGreenContainerLight,
            warning = Color(0xFFB06000),
            codeBackground = CodeBlockBackgroundLight,
            codeText = CodeBlockText
        )
    }

    CompositionLocalProvider(LocalStudioColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = StudioTypography,
            shapes = GoogleShapes,
            content = content
        )
    }
}
