package com.example.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppThemePalette(
    val id: String,
    val nameTr: String,
    val nameEn: String,
    val primary: Color,
    val primaryVariant: Color,
    val accent: Color,
    val background: Color,
    val surface: Color,
    val surfaceHover: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val liveBadge: Color = Color(0xFFFF2E4D),
    val accentGradient: List<Color> = listOf(primaryVariant, primary, accent)
)

val StavioRoseGoldTheme = AppThemePalette(
    id = "stavio_rose_gold",
    nameTr = "Stavio Dark (Varsayılan)",
    nameEn = "Stavio Dark (Default)",
    primary = Color(0xFF3B82F6),
    primaryVariant = Color(0xFF2563EB),
    accent = Color(0xFFFFB36B),
    background = Color(0xFF000000),
    surface = Color(0xFF121214),
    surfaceHover = Color(0xFF1E1E22),
    border = Color(0x1AFFFFFF),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFF8E8E93),
    accentGradient = listOf(Color(0xFF3B82F6), Color(0xFF8B5CF6), Color(0xFFFFB36B))
)

val StavioGoldTheme = AppThemePalette(
    id = "stavio_gold",
    nameTr = "Stavio Gold (Altın Işıltı)",
    nameEn = "Stavio Gold (Golden Glow)",
    primary = Color(0xFFE8A91C),
    primaryVariant = Color(0xFF9A6200),
    accent = Color(0xFFFFD45C),
    background = Color(0xFF0F0E0B),
    surface = Color(0xFF1D1A14),
    surfaceHover = Color(0xFF2A241A),
    border = Color(0xFF382F1E),
    textPrimary = Color(0xFFF5F7F8),
    textSecondary = Color(0xFFB8BEC5),
    accentGradient = listOf(Color(0xFF8A5700), Color(0xFFE8A91C), Color(0xFFFFD45C))
)

val StavioArcticBlueTheme = AppThemePalette(
    id = "stavio_arctic_blue",
    nameTr = "Stavio Arctic Blue (Buzul Mavisi)",
    nameEn = "Stavio Arctic Blue (Glacier)",
    primary = Color(0xFF3185F5),
    primaryVariant = Color(0xFF4D55E8),
    accent = Color(0xFF4DE3FF),
    background = Color(0xFF0B0E14),
    surface = Color(0xFF141A24),
    surfaceHover = Color(0xFF1E2634),
    border = Color(0xFF283446),
    textPrimary = Color(0xFFF5F7F8),
    textSecondary = Color(0xFF969CA3),
    accentGradient = listOf(Color(0xFF4DE3FF), Color(0xFF3185F5), Color(0xFF4D55E8))
)

val StavioJadeTheme = AppThemePalette(
    id = "stavio_jade",
    nameTr = "Stavio Jade (Zümrüt Yeşili)",
    nameEn = "Stavio Jade (Neon Emerald)",
    primary = Color(0xFF22D37C),
    primaryVariant = Color(0xFF0BBF9A),
    accent = Color(0xFF7BF08D),
    background = Color(0xFF0B0F0D),
    surface = Color(0xFF141D18),
    surfaceHover = Color(0xFF1C2A22),
    border = Color(0xFF24382C),
    textPrimary = Color(0xFFF5F7F8),
    textSecondary = Color(0xFF969CA3),
    accentGradient = listOf(Color(0xFF7BF08D), Color(0xFF22D37C), Color(0xFF0BBF9A))
)

val StavioAmoledTheme = AppThemePalette(
    id = "stavio_amoled",
    nameTr = "Stavio AMOLED (Saf Siyah & Neon)",
    nameEn = "Stavio AMOLED (Pure Black)",
    primary = Color(0xFFEC70A9),
    primaryVariant = Color(0xFFB75AFF),
    accent = Color(0xFFFFB37A),
    background = Color(0xFF000000),
    surface = Color(0xFF101012),
    surfaceHover = Color(0xFF1C1C20),
    border = Color(0xFF24242A),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFA1A1AA),
    accentGradient = listOf(Color(0xFFB75AFF), Color(0xFFEC70A9), Color(0xFFFFB37A))
)

val StavioGraphiteTheme = AppThemePalette(
    id = "stavio_graphite",
    nameTr = "Stavio Graphite (Monokrom)",
    nameEn = "Stavio Graphite (Monochrome)",
    primary = Color(0xFFAAB2BE),
    primaryVariant = Color(0xFF687381),
    accent = Color(0xFFF3F5F7),
    background = Color(0xFF0C0D0F),
    surface = Color(0xFF17191D),
    surfaceHover = Color(0xFF24272D),
    border = Color(0xFF2E323A),
    textPrimary = Color(0xFFF5F7F8),
    textSecondary = Color(0xFFAAB2BE),
    accentGradient = listOf(Color(0xFFF3F5F7), Color(0xFFAAB2BE), Color(0xFF687381))
)

// Legacy aliases
val ObsidianTheme = StavioRoseGoldTheme
val AmoledTheme = StavioAmoledTheme
val MidnightTheme = StavioArcticBlueTheme
val EmeraldTheme = StavioJadeTheme

val AllThemes = listOf(
    StavioRoseGoldTheme,
    StavioGoldTheme,
    StavioArcticBlueTheme,
    StavioJadeTheme,
    StavioAmoledTheme,
    StavioGraphiteTheme
)

val LocalAppTheme = compositionLocalOf { StavioRoseGoldTheme }

class ThemeManager private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("stavio_prefs", Context.MODE_PRIVATE)

    private val _currentTheme = MutableStateFlow(
        AllThemes.find { it.id == prefs.getString("app_theme", "stavio_rose_gold") }
            ?: AllThemes.find { it.id == prefs.getString("app_theme", "obsidian") }
            ?: StavioRoseGoldTheme
    )
    val currentTheme: StateFlow<AppThemePalette> = _currentTheme.asStateFlow()

    fun setTheme(theme: AppThemePalette) {
        prefs.edit().putString("app_theme", theme.id).apply()
        _currentTheme.value = theme
    }

    companion object {
        @Volatile
        private var INSTANCE: ThemeManager? = null

        fun getInstance(context: Context): ThemeManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ThemeManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
