package com.cocwar.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * COC War Tool 多主题设计令牌。
 *
 * 每套主题保持同一套结构：底色 + 墨色文字 + 细线边框 + 三枚语义色
 * （accent 强调 / star 星色 / danger 警示），并自带浅色与深色两套色板，
 * 由 [CocWarTheme] 根据系统明暗自动切换。
 */

// ─── 色板 ────────────────────────────────────────────────────
@Immutable
data class ThemePalette(
    /** 页面底色 */
    val background: Color,
    /** 深一档底色（刊头 / surfaceVariant / 浅色容器） */
    val backgroundAlt: Color,
    /** 卡片面 */
    val surface: Color,
    val surfaceLow: Color,
    val surfaceLowest: Color,
    /** 正文墨色 */
    val ink: Color,
    val inkSoft: Color,
    /** 细线 */
    val hairline: Color,
    val outline: Color,
    /** 强调色（部落战 / 成功 / 已出手） */
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val onAccentSoft: Color,
    /** 星色（联赛 / 三星） */
    val star: Color,
    val onStar: Color,
    val starSoft: Color,
    val onStarSoft: Color,
    /** 警示色（未进攻） */
    val danger: Color,
    val onDanger: Color,
    val dangerSoft: Color,
    val onDangerSoft: Color,
    /** 职位色 */
    val roleLeader: Color,
    val roleCoLeader: Color,
    val roleElder: Color,
    val roleMember: Color,
)

// ─── 墨册（默认）：米纸 + 墨 + 松绿/黄铜/朱砂 ─────────────────
private val LedgerLight = ThemePalette(
    background = Color(0xFFF5F4EF),
    backgroundAlt = Color(0xFFECEBE3),
    surface = Color(0xFFFFFFFF),
    surfaceLow = Color(0xFFFAF9F5),
    surfaceLowest = Color(0xFFFFFFFF),
    ink = Color(0xFF1C1B17),
    inkSoft = Color(0xFF6E6A5F),
    hairline = Color(0xFFE3E1D7),
    outline = Color(0xFFC7C4B8),
    accent = Color(0xFF146B4C),
    onAccent = Color(0xFFFFFFFF),
    accentSoft = Color(0xFFE0EDE4),
    onAccentSoft = Color(0xFF0E3B2A),
    star = Color(0xFF9A7412),
    onStar = Color(0xFFFFFFFF),
    starSoft = Color(0xFFF3ECD6),
    onStarSoft = Color(0xFF4A3705),
    danger = Color(0xFFB03527),
    onDanger = Color(0xFFFFFFFF),
    dangerSoft = Color(0xFFF7E4E0),
    onDangerSoft = Color(0xFF5C1509),
    roleLeader = Color(0xFFB03527),
    roleCoLeader = Color(0xFF9A7412),
    roleElder = Color(0xFF146B4C),
    roleMember = Color(0xFF6E6A5F),
)

private val LedgerDark = ThemePalette(
    background = Color(0xFF171612),
    backgroundAlt = Color(0xFF2A2922),
    surface = Color(0xFF201F1A),
    surfaceLow = Color(0xFF171612),
    surfaceLowest = Color(0xFF131209),
    ink = Color(0xFFECEAE1),
    inkSoft = Color(0xFFA39D8E),
    hairline = Color(0xFF35332B),
    outline = Color(0xFF555246),
    accent = Color(0xFF63C9A4),
    onAccent = Color(0xFF0C2A1E),
    accentSoft = Color(0xFF22392F),
    onAccentSoft = Color(0xFF9BDFC6),
    star = Color(0xFFE0B84E),
    onStar = Color(0xFF2E2403),
    starSoft = Color(0xFF39321F),
    onStarSoft = Color(0xFFEFD590),
    danger = Color(0xFFE08D7E),
    onDanger = Color(0xFF4A150C),
    dangerSoft = Color(0xFF3D2721),
    onDangerSoft = Color(0xFFF0BDB2),
    roleLeader = Color(0xFFE08D7E),
    roleCoLeader = Color(0xFFE0B84E),
    roleElder = Color(0xFF63C9A4),
    roleMember = Color(0xFFA39D8E),
)

// ─── 星夜：深蓝夜空 + 鎏金星光 ────────────────────────────────
private val NebulaLight = ThemePalette(
    background = Color(0xFFF2F5FA),
    backgroundAlt = Color(0xFFE7EDF5),
    surface = Color(0xFFFFFFFF),
    surfaceLow = Color(0xFFF8FAFD),
    surfaceLowest = Color(0xFFFFFFFF),
    ink = Color(0xFF182231),
    inkSoft = Color(0xFF5D6B80),
    hairline = Color(0xFFDCE4EF),
    outline = Color(0xFFB8C5D6),
    accent = Color(0xFF2E6FD8),
    onAccent = Color(0xFFFFFFFF),
    accentSoft = Color(0xFFE1EAFB),
    onAccentSoft = Color(0xFF163B7A),
    star = Color(0xFFB8860B),
    onStar = Color(0xFFFFFFFF),
    starSoft = Color(0xFFF7EDD6),
    onStarSoft = Color(0xFF5A4005),
    danger = Color(0xFFC0392B),
    onDanger = Color(0xFFFFFFFF),
    dangerSoft = Color(0xFFF9E3DE),
    onDangerSoft = Color(0xFF64140A),
    roleLeader = Color(0xFFC0392B),
    roleCoLeader = Color(0xFFB8860B),
    roleElder = Color(0xFF2E6FD8),
    roleMember = Color(0xFF5D6B80),
)

private val NebulaDark = ThemePalette(
    background = Color(0xFF0C1424),
    backgroundAlt = Color(0xFF1C2940),
    surface = Color(0xFF131E31),
    surfaceLow = Color(0xFF0C1424),
    surfaceLowest = Color(0xFF0A101C),
    ink = Color(0xFFE8EEF8),
    inkSoft = Color(0xFF97A5BB),
    hairline = Color(0xFF26344D),
    outline = Color(0xFF3D4E6C),
    accent = Color(0xFF82A9FF),
    onAccent = Color(0xFF0B1B3A),
    accentSoft = Color(0xFF1B2B4A),
    onAccentSoft = Color(0xFFB9D0FF),
    star = Color(0xFFF0C14E),
    onStar = Color(0xFF2E2403),
    starSoft = Color(0xFF3A3119),
    onStarSoft = Color(0xFFF5D98F),
    danger = Color(0xFFEF8F7F),
    onDanger = Color(0xFF481009),
    dangerSoft = Color(0xFF3C2520),
    onDangerSoft = Color(0xFFF5B9AC),
    roleLeader = Color(0xFFEF8F7F),
    roleCoLeader = Color(0xFFF0C14E),
    roleElder = Color(0xFF82A9FF),
    roleMember = Color(0xFF97A5BB),
)

// ─── 烈焰：暖橙热血 + 部落战火 ────────────────────────────────
private val EmberLight = ThemePalette(
    background = Color(0xFFFBF5EF),
    backgroundAlt = Color(0xFFF6EBE0),
    surface = Color(0xFFFFFFFF),
    surfaceLow = Color(0xFFFEFBF7),
    surfaceLowest = Color(0xFFFFFFFF),
    ink = Color(0xFF2B1F16),
    inkSoft = Color(0xFF7D6A59),
    hairline = Color(0xFFEFE0D1),
    outline = Color(0xFFD8BFA9),
    accent = Color(0xFFC24A1F),
    onAccent = Color(0xFFFFFFFF),
    accentSoft = Color(0xFFFAE3D5),
    onAccentSoft = Color(0xFF6E2406),
    star = Color(0xFFC08A1E),
    onStar = Color(0xFFFFFFFF),
    starSoft = Color(0xFFF7ECD3),
    onStarSoft = Color(0xFF5A4005),
    danger = Color(0xFFB3261E),
    onDanger = Color(0xFFFFFFFF),
    dangerSoft = Color(0xFFF8DDD9),
    onDangerSoft = Color(0xFF641009),
    roleLeader = Color(0xFFB3261E),
    roleCoLeader = Color(0xFFC08A1E),
    roleElder = Color(0xFFC24A1F),
    roleMember = Color(0xFF7D6A59),
)

private val EmberDark = ThemePalette(
    background = Color(0xFF1B100B),
    backgroundAlt = Color(0xFF2F2017),
    surface = Color(0xFF251710),
    surfaceLow = Color(0xFF1B100B),
    surfaceLowest = Color(0xFF140B07),
    ink = Color(0xFFF7EBE1),
    inkSoft = Color(0xFFB29C87),
    hairline = Color(0xFF3C2B20),
    outline = Color(0xFF5A4534),
    accent = Color(0xFFFF8A5C),
    onAccent = Color(0xFF3A1703),
    accentSoft = Color(0xFF3D2416),
    onAccentSoft = Color(0xFFFFC3A6),
    star = Color(0xFFF0C14E),
    onStar = Color(0xFF2E2403),
    starSoft = Color(0xFF3A3119),
    onStarSoft = Color(0xFFF5D98F),
    danger = Color(0xFFFF7A6E),
    onDanger = Color(0xFF481009),
    dangerSoft = Color(0xFF402019),
    onDangerSoft = Color(0xFFF8B4A9),
    roleLeader = Color(0xFFFF7A6E),
    roleCoLeader = Color(0xFFF0C14E),
    roleElder = Color(0xFFFF8A5C),
    roleMember = Color(0xFFB29C87),
)

// ─── 翡翠：青绿清新 + 竹石雅韵 ────────────────────────────────
private val JadeLight = ThemePalette(
    background = Color(0xFFF0F6F2),
    backgroundAlt = Color(0xFFE3EFE8),
    surface = Color(0xFFFFFFFF),
    surfaceLow = Color(0xFFF8FBF9),
    surfaceLowest = Color(0xFFFFFFFF),
    ink = Color(0xFF173129),
    inkSoft = Color(0xFF5C756A),
    hairline = Color(0xFFDCEAE1),
    outline = Color(0xFFB7CDC0),
    accent = Color(0xFF0E8A5E),
    onAccent = Color(0xFFFFFFFF),
    accentSoft = Color(0xFFDDF1E7),
    onAccentSoft = Color(0xFF0B452E),
    star = Color(0xFFB8860B),
    onStar = Color(0xFFFFFFFF),
    starSoft = Color(0xFFF7EDD6),
    onStarSoft = Color(0xFF5A4005),
    danger = Color(0xFFC0392B),
    onDanger = Color(0xFFFFFFFF),
    dangerSoft = Color(0xFFF9E3DE),
    onDangerSoft = Color(0xFF64140A),
    roleLeader = Color(0xFFC0392B),
    roleCoLeader = Color(0xFFB8860B),
    roleElder = Color(0xFF0E8A5E),
    roleMember = Color(0xFF5C756A),
)

private val JadeDark = ThemePalette(
    background = Color(0xFF0C1712),
    backgroundAlt = Color(0xFF1B2C23),
    surface = Color(0xFF122019),
    surfaceLow = Color(0xFF0C1712),
    surfaceLowest = Color(0xFF09100C),
    ink = Color(0xFFE3EFE8),
    inkSoft = Color(0xFF90A89B),
    hairline = Color(0xFF243B30),
    outline = Color(0xFF3A5749),
    accent = Color(0xFF4FD8A1),
    onAccent = Color(0xFF06291D),
    accentSoft = Color(0xFF17392C),
    onAccentSoft = Color(0xFFA9EDCF),
    star = Color(0xFFEFC04C),
    onStar = Color(0xFF2E2403),
    starSoft = Color(0xFF393017),
    onStarSoft = Color(0xFFF4D88C),
    danger = Color(0xFFEF8F7F),
    onDanger = Color(0xFF481009),
    dangerSoft = Color(0xFF3C2520),
    onDangerSoft = Color(0xFFF5B9AC),
    roleLeader = Color(0xFFEF8F7F),
    roleCoLeader = Color(0xFFEFC04C),
    roleElder = Color(0xFF4FD8A1),
    roleMember = Color(0xFF90A89B),
)

// ─── 樱花：粉黛温柔 + 落樱缤纷 ────────────────────────────────
private val SakuraLight = ThemePalette(
    background = Color(0xFFFBF4F6),
    backgroundAlt = Color(0xFFF5E8EC),
    surface = Color(0xFFFFFFFF),
    surfaceLow = Color(0xFFFEF9FB),
    surfaceLowest = Color(0xFFFFFFFF),
    ink = Color(0xFF32242B),
    inkSoft = Color(0xFF7E6971),
    hairline = Color(0xFFEFDEE4),
    outline = Color(0xFFD7BCC6),
    accent = Color(0xFFC2437E),
    onAccent = Color(0xFFFFFFFF),
    accentSoft = Color(0xFFF8E2ED),
    onAccentSoft = Color(0xFF6C1740),
    star = Color(0xFFB8860B),
    onStar = Color(0xFFFFFFFF),
    starSoft = Color(0xFFF7EDD6),
    onStarSoft = Color(0xFF5A4005),
    danger = Color(0xFFC0392B),
    onDanger = Color(0xFFFFFFFF),
    dangerSoft = Color(0xFFF9E3DE),
    onDangerSoft = Color(0xFF64140A),
    roleLeader = Color(0xFFC0392B),
    roleCoLeader = Color(0xFFB8860B),
    roleElder = Color(0xFFC2437E),
    roleMember = Color(0xFF7E6971),
)

private val SakuraDark = ThemePalette(
    background = Color(0xFF1B1117),
    backgroundAlt = Color(0xFF2E2129),
    surface = Color(0xFF241820),
    surfaceLow = Color(0xFF1B1117),
    surfaceLowest = Color(0xFF150D12),
    ink = Color(0xFFF8EAF0),
    inkSoft = Color(0xFFB49AA6),
    hairline = Color(0xFF3D2B34),
    outline = Color(0xFF594451),
    accent = Color(0xFFF48CB9),
    onAccent = Color(0xFF48172F),
    accentSoft = Color(0xFF412334),
    onAccentSoft = Color(0xFFFBC4DC),
    star = Color(0xFFEFC04C),
    onStar = Color(0xFF2E2403),
    starSoft = Color(0xFF393017),
    onStarSoft = Color(0xFFF4D88C),
    danger = Color(0xFFEF8F7F),
    onDanger = Color(0xFF481009),
    dangerSoft = Color(0xFF3C2520),
    onDangerSoft = Color(0xFFF5B9AC),
    roleLeader = Color(0xFFEF8F7F),
    roleCoLeader = Color(0xFFEFC04C),
    roleElder = Color(0xFFF48CB9),
    roleMember = Color(0xFFB49AA6),
)

/** 主题风格：五套色系，每套含浅色/深色两套色板。 */
enum class ThemeStyle(
    val id: String,
    val label: String,
    val tagline: String,
    val light: ThemePalette,
    val dark: ThemePalette
) {
    LEDGER("ledger", "墨册", "米纸墨色 · 松绿黄铜", LedgerLight, LedgerDark),
    NEBULA("nebula", "星夜", "深蓝夜空 · 鎏金星光", NebulaLight, NebulaDark),
    EMBER("ember", "烈焰", "暖橙热血 · 部落战火", EmberLight, EmberDark),
    JADE("jade", "翡翠", "青绿清新 · 竹石雅韵", JadeLight, JadeDark),
    SAKURA("sakura", "樱花", "粉黛温柔 · 落樱缤纷", SakuraLight, SakuraDark);

    /** 取指定明暗模式的色板 */
    fun palette(dark: Boolean): ThemePalette = if (dark) this.dark else this.light

    companion object {
        fun fromId(id: String?): ThemeStyle = entries.firstOrNull { it.id == id } ?: LEDGER
    }
}

// ─── 语义色（随主题切换） ────────────────────────────────────
@Immutable
data class CocColors(
    /** 强调：成功、已出手、部落战、链接强调 */
    val accent: Color,
    val accentSoft: Color,
    /** 星色：星星、联赛 */
    val star: Color,
    val starSoft: Color,
    /** 警示：未进攻 */
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

private fun cocColorsOf(p: ThemePalette) = CocColors(
    accent = p.accent,
    accentSoft = p.accentSoft,
    star = p.star,
    starSoft = p.starSoft,
    danger = p.danger,
    dangerSoft = p.dangerSoft,
    hairline = p.hairline,
    masthead = p.backgroundAlt,
    onMasthead = p.ink,
    onMastheadSoft = p.inkSoft,
    roleLeader = p.roleLeader,
    roleCoLeader = p.roleCoLeader,
    roleElder = p.roleElder,
    roleMember = p.roleMember,
)

val LocalCocColors = staticCompositionLocalOf { cocColorsOf(LedgerLight) }

/** 语义色便捷访问：MaterialTheme.cocColors.accent */
val MaterialTheme.cocColors: CocColors
    @Composable get() = LocalCocColors.current

/** 职位颜色（主题感知）。首领=警示色，副首领=星色，长老=强调色，成员=次级文字色 */
@Composable
fun roleColor(role: String): Color {
    val c = LocalCocColors.current
    return when (role.lowercase().replace("-", "").replace("_", "")) {
        "leader" -> c.roleLeader
        "coleader", "viceleader" -> c.roleCoLeader
        "elder" -> c.roleElder
        else -> c.roleMember
    }
}

// ─── Material 配色映射 ──────────────────────────────────────
private fun colorSchemeOf(p: ThemePalette, dark: Boolean): ColorScheme =
    (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = p.ink,
        onPrimary = p.background,
        primaryContainer = p.backgroundAlt,
        onPrimaryContainer = p.ink,
        secondary = p.accent,
        onSecondary = p.onAccent,
        secondaryContainer = p.accentSoft,
        onSecondaryContainer = p.onAccentSoft,
        tertiary = p.star,
        onTertiary = p.onStar,
        tertiaryContainer = p.starSoft,
        onTertiaryContainer = p.onStarSoft,
        error = p.danger,
        onError = p.onDanger,
        errorContainer = p.dangerSoft,
        onErrorContainer = p.onDangerSoft,
        background = p.background,
        onBackground = p.ink,
        surface = p.surface,
        onSurface = p.ink,
        surfaceVariant = p.backgroundAlt,
        onSurfaceVariant = p.inkSoft,
        surfaceContainerLowest = p.surfaceLowest,
        surfaceContainerLow = p.surfaceLow,
        surfaceContainer = p.background,
        surfaceContainerHigh = p.backgroundAlt,
        outline = p.outline,
        outlineVariant = p.hairline,
        surfaceTint = Color.Transparent,     // 关闭 M3 色调叠加，保持纸面纯净
    )

@Composable
fun CocWarTheme(
    style: ThemeStyle = ThemeStyle.LEDGER,
    content: @Composable () -> Unit
) {
    val dark = isSystemInDarkTheme()
    val palette = style.palette(dark)
    CompositionLocalProvider(
        LocalCocColors provides cocColorsOf(palette)
    ) {
        MaterialTheme(
            colorScheme = colorSchemeOf(palette, dark),
            typography = CocWarTypography,
            content = content
        )
    }
}

// ─── 主题偏好持久化 ──────────────────────────────────────────
object ThemePrefs {
    private const val PREFS_NAME = "cocwar_theme"
    private const val KEY_STYLE = "theme_style"

    fun load(context: Context): ThemeStyle =
        ThemeStyle.fromId(
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_STYLE, null)
        )

    fun save(context: Context, style: ThemeStyle) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STYLE, style.id)
            .apply()
    }
}
