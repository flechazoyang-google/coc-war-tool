package com.cocwar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 「墨册 Ledger」设计令牌
 * 米纸底 + 墨色字 + 细线边框；松绿/黄铜/朱砂三枚语义色，零渐变零投影。
 */

// ─── 基础色板 ───────────────────────────────────────────────
private val Paper = Color(0xFFF5F4EF)          // 米纸背景
private val PaperDeep = Color(0xFFECEBE3)      // 深一档纸面
private val CardWhite = Color(0xFFFFFFFF)      // 卡片
private val Ink = Color(0xFF1C1B17)            // 墨色正文
private val InkSoft = Color(0xFF6E6A5F)        // 次级文字
private val HairlineLight = Color(0xFFE3E1D7)  // 细线
private val OutlineLight = Color(0xFFC7C4B8)

private val NightBg = Color(0xFF171612)        // 深色背景（暖黑）
private val NightSurface = Color(0xFF201F1A)   // 深色卡片
private val NightSurfaceAlt = Color(0xFF2A2922)
private val NightInk = Color(0xFFECEAE1)
private val NightInkSoft = Color(0xFFA39D8E)
private val HairlineDark = Color(0xFF35332B)
private val OutlineDark = Color(0xFF555246)

private val PineLight = Color(0xFF146B4C)      // 松绿（浅色主题）
private val PineDark = Color(0xFF63C9A4)       // 松绿（深色主题）

// ─── 语义色（随主题切换） ────────────────────────────────────
@Immutable
data class CocColors(
    /** 松绿：成功、已出手、部落战、链接强调 */
    val accent: Color,
    val accentSoft: Color,
    /** 黄铜：星星、联赛 */
    val star: Color,
    val starSoft: Color,
    /** 朱砂：警示、未进攻 */
    val danger: Color,
    val dangerSoft: Color,
    /** 细线边框 */
    val hairline: Color,
    /** 刊头底色（永远偏深，营造编辑感） */
    val masthead: Color,
    val onMasthead: Color,
    val onMastheadSoft: Color,
    /** 职位色 */
    val roleLeader: Color,
    val roleCoLeader: Color,
    val roleElder: Color,
    val roleMember: Color,
)

private val LightCocColors = CocColors(
    accent = Color(0xFF146B4C),
    accentSoft = Color(0xFFE0EDE4),
    star = Color(0xFF9A7412),
    starSoft = Color(0xFFF3ECD6),
    danger = Color(0xFFB03527),
    dangerSoft = Color(0xFFF7E4E0),
    hairline = HairlineLight,
    // 浅色刊头面板（米灰），文字转深墨，零渐变
    masthead = PaperDeep,
    onMasthead = Ink,
    onMastheadSoft = InkSoft,
    roleLeader = Color(0xFFB03527),
    roleCoLeader = Color(0xFF9A7412),
    roleElder = Color(0xFF146B4C),
    roleMember = Color(0xFF6E6A5F),
)

private val DarkCocColors = CocColors(
    accent = Color(0xFF63C9A4),
    accentSoft = Color(0xFF22392F),
    star = Color(0xFFE0B84E),
    starSoft = Color(0xFF39321F),
    danger = Color(0xFFE08D7E),
    dangerSoft = Color(0xFF3D2721),
    hairline = HairlineDark,
    // 深色主题下刊头用略亮的面板，文字保持纸色
    masthead = NightSurfaceAlt,
    onMasthead = NightInk,
    onMastheadSoft = NightInkSoft,
    roleLeader = Color(0xFFE08D7E),
    roleCoLeader = Color(0xFFE0B84E),
    roleElder = Color(0xFF63C9A4),
    roleMember = Color(0xFFA39D8E),
)

val LocalCocColors = staticCompositionLocalOf { LightCocColors }

/** 语义色便捷访问：MaterialTheme.cocColors.accent */
val MaterialTheme.cocColors: CocColors
    @Composable get() = LocalCocColors.current

/** 职位颜色（主题感知）。首领=朱砂，副首领=黄铜，长老=松绿，成员=灰墨 */
@Composable
fun roleColor(role: String): Color {
    val c = LocalCocColors.current
    return when (role.lowercase().replace("-", "").replace("_", "")) {
        "leader" -> c.roleLeader
        "coleader" -> c.roleCoLeader
        "elder" -> c.roleElder
        else -> c.roleMember
    }
}

// ─── Material 配色映射 ──────────────────────────────────────
private val LightColors = lightColorScheme(
    primary = Ink,                       // 主按钮=墨块
    onPrimary = Paper,
    primaryContainer = PaperDeep,
    onPrimaryContainer = Ink,
    secondary = PineLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0EDE4),
    onSecondaryContainer = Color(0xFF0E3B2A),
    tertiary = Color(0xFF9A7412),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF3ECD6),
    onTertiaryContainer = Color(0xFF4A3705),
    error = Color(0xFFB03527),
    onError = Color.White,
    errorContainer = Color(0xFFF7E4E0),
    onErrorContainer = Color(0xFF5C1509),
    background = Paper,
    onBackground = Ink,
    surface = CardWhite,
    onSurface = Ink,
    surfaceVariant = PaperDeep,
    onSurfaceVariant = InkSoft,
    surfaceContainerLowest = CardWhite,
    surfaceContainerLow = Color(0xFFFAF9F5),
    surfaceContainer = Paper,
    surfaceContainerHigh = PaperDeep,
    outline = OutlineLight,
    outlineVariant = HairlineLight,
    surfaceTint = Color.Transparent,     // 关闭 M3 色调叠加，保持纸面纯净
)

private val DarkColors = darkColorScheme(
    primary = NightInk,                  // 深色主题主按钮=纸色块
    onPrimary = Color(0xFF1C1B17),
    primaryContainer = NightSurfaceAlt,
    onPrimaryContainer = NightInk,
    secondary = PineDark,
    onSecondary = Color(0xFF0C2A1E),
    secondaryContainer = Color(0xFF22392F),
    onSecondaryContainer = Color(0xFF9BDFC6),
    tertiary = Color(0xFFE0B84E),
    onTertiary = Color(0xFF2E2403),
    tertiaryContainer = Color(0xFF39321F),
    onTertiaryContainer = Color(0xFFEFD590),
    error = Color(0xFFE08D7E),
    onError = Color(0xFF4A150C),
    errorContainer = Color(0xFF3D2721),
    onErrorContainer = Color(0xFFF0BDB2),
    background = NightBg,
    onBackground = NightInk,
    surface = NightSurface,
    onSurface = NightInk,
    surfaceVariant = NightSurfaceAlt,
    onSurfaceVariant = NightInkSoft,
    surfaceContainerLowest = Color(0xFF131209),
    surfaceContainerLow = NightBg,
    surfaceContainer = NightSurface,
    surfaceContainerHigh = NightSurfaceAlt,
    outline = OutlineDark,
    outlineVariant = HairlineDark,
    surfaceTint = Color.Transparent,
)

@Composable
fun CocWarTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(
        LocalCocColors provides if (dark) DarkCocColors else LightCocColors
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = CocWarTypography,
            content = content
        )
    }
}
