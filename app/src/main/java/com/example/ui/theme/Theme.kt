package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = GitHubGreenBright,
    onPrimary = GitHubDarkBg,
    primaryContainer = GitHubGreen,
    onPrimaryContainer = GitHubTextPrimary,
    secondary = GitHubBlue,
    onSecondary = GitHubDarkBg,
    secondaryContainer = GitHubBlueDark,
    onSecondaryContainer = GitHubTextPrimary,
    tertiary = GitHubPurple,
    onTertiary = GitHubDarkBg,
    background = GitHubDarkBg,
    onBackground = GitHubTextPrimary,
    surface = GitHubDarkSurface,
    onSurface = GitHubTextPrimary,
    surfaceVariant = GitHubDarkSurfaceVariant,
    onSurfaceVariant = GitHubTextSecondary,
    outline = GitHubBorder,
    outlineVariant = GitHubBorderLight,
    error = GitHubRed,
    onError = GitHubTextPrimary
)

@Composable
fun Zip2GitTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
