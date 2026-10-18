package com.ketangpai.checkin

import android.app.Activity
import android.content.Context
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** 界面配色模式：跟随系统、始终浅色、始终深色。 */
enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色");

    /** 结合系统当前状态，得出这一模式实际是否使用深色。 */
    fun isDark(systemInDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemInDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromName(name: String?): ThemeMode = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

/**
 * 主题偏好的本机存储，与账号数据分开保存，
 * 这样清理账号或签到记录时不会把外观设置一并重置。
 */
class ThemePreference(context: Context) {
    private val prefs = context.getSharedPreferences("checkin_theme", Context.MODE_PRIVATE)

    var mode: ThemeMode
        get() = ThemeMode.fromName(prefs.getString(KEY_MODE, null))
        set(value) {
            prefs.edit().putString(KEY_MODE, value.name).apply()
        }

    private companion object {
        const val KEY_MODE = "mode"
    }
}

private val LightPalette = lightColorScheme(
    primary = Color(0xFF146B63),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD0F2EC),
    onPrimaryContainer = Color(0xFF073D37),
    secondary = Color(0xFFE99B45),
    onSecondary = Color(0xFF3A2408),
    secondaryContainer = Color(0xFFFFE2C2),
    onSecondaryContainer = Color(0xFF2B1700),
    tertiary = Color(0xFF16704D),
    onTertiary = Color.White,
    background = Color(0xFFF5F7F6),
    onBackground = Color(0xFF1A1C1B),
    surface = Color.White,
    onSurface = Color(0xFF1A1C1B),
    surfaceVariant = Color(0xFFE9EFED),
    onSurfaceVariant = Color(0xFF414944),
    outline = Color(0xFF717973),
    outlineVariant = Color(0xFFC0C9C3),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val DarkPalette = darkColorScheme(
    primary = Color(0xFF7CD8C9),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF0E4B45),
    onPrimaryContainer = Color(0xFFB4F0E6),
    secondary = Color(0xFFF3B56B),
    onSecondary = Color(0xFF412A00),
    secondaryContainer = Color(0xFF5D3F10),
    onSecondaryContainer = Color(0xFFFFDCB4),
    tertiary = Color(0xFF7BD6B0),
    onTertiary = Color(0xFF003825),
    background = Color(0xFF0E1211),
    onBackground = Color(0xFFE1E7E4),
    surface = Color(0xFF161B1A),
    onSurface = Color(0xFFE1E7E4),
    surfaceVariant = Color(0xFF232B29),
    onSurfaceVariant = Color(0xFFB0BAB5),
    outline = Color(0xFF8A948E),
    outlineVariant = Color(0xFF3F4744),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun CheckInTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = mode.isDark(isSystemInDarkTheme())
    val palette = if (dark) DarkPalette else LightPalette
    val view = LocalView.current

    // 让状态栏、导航栏和系统手势区域跟随配色，深色下使用浅色图标。
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        @Suppress("DEPRECATION")
        run {
            window.statusBarColor = palette.background.toArgb()
            window.navigationBarColor = palette.background.toArgb()
        }
        window.setBackgroundDrawable(ColorDrawable(palette.background.toArgb()))
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    MaterialTheme(colorScheme = palette, content = content)
}
