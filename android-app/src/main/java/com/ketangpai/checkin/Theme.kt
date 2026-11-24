package com.ketangpai.checkin

import android.app.Activity
import android.content.Context
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
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

/**
 * Material3 的 colorScheme 槽位不够表达 iOS 的分组背景、分隔线和三级文字色，
 * 这些额外语义色单独走一个 CompositionLocal。
 */
data class IosColors(
    /** 页面底色，iOS 的 systemGroupedBackground。 */
    val groupedBackground: Color,
    /** 分组卡片的底色，iOS 的 secondarySystemGroupedBackground。 */
    val card: Color,
    /** 卡片内更淡的一层，用于输入框与次级按钮。 */
    val fill: Color,
    /** 0.5dp 分隔线。 */
    val separator: Color,
    /** 主要文字。 */
    val label: Color,
    /** 次要文字，iOS 的 secondaryLabel。 */
    val secondaryLabel: Color,
    /** 三级文字与 chevron，iOS 的 tertiaryLabel。 */
    val tertiaryLabel: Color,
    val blue: Color,
    val green: Color,
    val red: Color,
    val orange: Color,
)

private val LightIos = IosColors(
    groupedBackground = Color(0xFFF2F2F7),
    card = Color(0xFFFFFFFF),
    fill = Color(0xFF767680).copy(alpha = 0.12f),
    separator = Color(0xFF3C3C43).copy(alpha = 0.29f),
    label = Color(0xFF000000),
    secondaryLabel = Color(0xFF3C3C43).copy(alpha = 0.60f),
    tertiaryLabel = Color(0xFF3C3C43).copy(alpha = 0.30f),
    blue = Color(0xFF007AFF),
    green = Color(0xFF248A3D),
    red = Color(0xFFD70015),
    orange = Color(0xFFC93400),
)

private val DarkIos = IosColors(
    groupedBackground = Color(0xFF000000),
    card = Color(0xFF1C1C1E),
    fill = Color(0xFF767680).copy(alpha = 0.24f),
    separator = Color(0xFF545458).copy(alpha = 0.65f),
    label = Color(0xFFFFFFFF),
    secondaryLabel = Color(0xFFEBEBF5).copy(alpha = 0.60f),
    tertiaryLabel = Color(0xFFEBEBF5).copy(alpha = 0.30f),
    blue = Color(0xFF0A84FF),
    green = Color(0xFF30D158),
    red = Color(0xFFFF453A),
    orange = Color(0xFFFF9F0A),
)

val LocalIosColors = staticCompositionLocalOf { LightIos }

/** 当前生效的 iOS 扩展配色。 */
val iosColors: IosColors
    @Composable get() = LocalIosColors.current

/** 把 iOS 语义色映射到 Material3 槽位，让 Material 组件也跟随同一套配色。 */
private fun materialScheme(c: IosColors, dark: Boolean) = if (dark) darkColorScheme(
    primary = c.blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0A2540),
    onPrimaryContainer = c.blue,
    secondary = c.green,
    onSecondary = Color.Black,
    tertiary = c.green,
    onTertiary = Color.Black,
    background = c.groupedBackground,
    onBackground = c.label,
    surface = c.card,
    onSurface = c.label,
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = c.secondaryLabel,
    outline = c.tertiaryLabel,
    outlineVariant = c.separator,
    error = c.red,
    onError = Color.White,
    errorContainer = Color(0xFF3B0A0A),
    onErrorContainer = c.red,
) else lightColorScheme(
    primary = c.blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5F0FF),
    onPrimaryContainer = c.blue,
    secondary = c.green,
    onSecondary = Color.White,
    tertiary = c.green,
    onTertiary = Color.White,
    background = c.groupedBackground,
    onBackground = c.label,
    surface = c.card,
    onSurface = c.label,
    surfaceVariant = Color(0xFFEFEFF4),
    onSurfaceVariant = c.secondaryLabel,
    outline = c.tertiaryLabel,
    outlineVariant = c.separator,
    error = c.red,
    onError = Color.White,
    errorContainer = Color(0xFFFCE8E8),
    onErrorContainer = c.red,
)

/**
 * 字号按 iOS HIG 的层级：正文 17，说明 15，脚注 13。
 * 原来大量界面用 11-12sp 的小字，中文在手机上读起来偏吃力。
 */
private val IosTypography = Typography(
    displayLarge = TextStyle(fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 15.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
)

@Composable
fun CheckInTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = mode.isDark(isSystemInDarkTheme())
    val ios = if (dark) DarkIos else LightIos
    val view = LocalView.current

    // 让状态栏、导航栏和系统手势区域跟随配色，深色下使用浅色图标。
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        @Suppress("DEPRECATION")
        run {
            window.statusBarColor = ios.groupedBackground.toArgb()
            window.navigationBarColor = ios.groupedBackground.toArgb()
        }
        window.setBackgroundDrawable(ColorDrawable(ios.groupedBackground.toArgb()))
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    CompositionLocalProvider(LocalIosColors provides ios) {
        MaterialTheme(colorScheme = materialScheme(ios, dark), typography = IosTypography, content = content)
    }
}
