package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val themeManager = remember { ThemeManager.getInstance(context) }
    val activeTheme by themeManager.currentTheme.collectAsState()

    val dynamicColorScheme = darkColorScheme(
        primary = activeTheme.primary,
        secondary = activeTheme.primaryVariant,
        tertiary = activeTheme.accent,
        background = activeTheme.background,
        surface = activeTheme.surface,
        onPrimary = activeTheme.textPrimary,
        onSecondary = activeTheme.textPrimary,
        onTertiary = activeTheme.textPrimary,
        onBackground = activeTheme.textPrimary,
        onSurface = activeTheme.textPrimary
    )

    CompositionLocalProvider(LocalAppTheme provides activeTheme) {
        MaterialTheme(
            colorScheme = dynamicColorScheme,
            typography = Typography,
            content = content
        )
    }
}

