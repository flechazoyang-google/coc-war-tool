package com.cocwar.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cocwar.ui.theme.cocColors

/** 全局圆角：克制的 12/10/8，拒绝大圆角 */
object CocShape {
    val card = RoundedCornerShape(12.dp)
    val panel = RoundedCornerShape(10.dp)
    val chip = RoundedCornerShape(8.dp)
    val field = RoundedCornerShape(10.dp)
}

// ─── 容器 ──────────────────────────────────────────────────

/** 平面细线卡片：白/深色面 + 1dp 发丝边框，零投影 */
@Composable
fun CocCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val shape = CocShape.card
    Surface(
        modifier = modifier.then(
            if (onClick != null) Modifier.clip(shape).clickable(onClick = onClick) else Modifier
        ),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.cocColors.hairline),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        content = content
    )
}

// ─── 页眉 / 章节 ────────────────────────────────────────────

/**
 * 杂志式页眉：眉题(overline) + 大标题 + 副标题，右侧可放操作。
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    overline: String? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            if (overline != null) {
                Text(
                    overline,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.cocColors.accent,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(3.dp))
            }
            Text(
                title,
                // 压缩标题区域：headlineLarge(32sp) → headlineMedium(28sp)，全站页眉统一缩小约 12%，
                // 减少首屏留白，让内容更早露出
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (subtitle != null) {
                Spacer(Modifier.height(3.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions
        )
    }
}

/** 章节眉题：小号宽字距标签 + 右侧延展细线 */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 22.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.cocColors.hairline)
        )
    }
}

// ─── 数据展示 ──────────────────────────────────────────────

/** label-value 信息行，底部发丝分隔线 */
@Composable
fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 11.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = valueColor
            )
        }
        if (showDivider) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.cocColors.hairline)
            )
        }
    }
}

/**
 * 统计格：平面细边框，表格化大数字 + 小标签。
 * tint=null 时用墨色；highlight=true 时切换为朱砂软底。
 */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    highlight: Boolean = false
) {
    val bg = if (highlight) MaterialTheme.cocColors.dangerSoft
    else MaterialTheme.colorScheme.surface
    val borderColor = if (highlight) MaterialTheme.cocColors.danger.copy(alpha = 0.35f)
    else MaterialTheme.cocColors.hairline
    val labelColor = if (highlight) MaterialTheme.cocColors.danger
    else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = modifier,
        shape = CocShape.panel,
        color = bg,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 14.dp, horizontal = 8.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─── 徽标 / 状态 ────────────────────────────────────────────

/** 类型标签：部落战=松绿软底，联赛=黄铜软底，细边框小方块 */
@Composable
fun TypeBadge(type: String, round: Int) {
    val isWar = type != "league"
    val fg = if (isWar) MaterialTheme.cocColors.accent else MaterialTheme.cocColors.star
    val bg = if (isWar) MaterialTheme.cocColors.accentSoft else MaterialTheme.cocColors.starSoft
    val label = if (isWar) "部落战" else "联赛 · 第${round}轮"

    Surface(
        shape = CocShape.chip,
        color = bg,
        border = BorderStroke(1.dp, fg.copy(alpha = 0.25f)),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        )
    }
}

/** 通用软底标签 */
@Composable
fun SoftTag(
    text: String,
    fg: Color,
    bg: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = CocShape.chip,
        color = bg,
        border = BorderStroke(1.dp, fg.copy(alpha = 0.25f)),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        )
    }
}

/** 职位徽标：仅文字着色，无底色，更克制 */
@Composable
fun RoleBadge(role: String) {
    Text(
        text = role,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** 进攻状态：圆点 + 文字。松绿实点=已出手，空心灰点=未出手 */
@Composable
fun AttackStatusChip(used: Boolean) {
    val dotColor = if (used) MaterialTheme.cocColors.accent
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .then(
                    if (used) Modifier.background(dotColor)
                    else Modifier.border(1.5.dp, dotColor, CircleShape)
                )
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (used) "已出手" else "未出手",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (used) FontWeight.SemiBold else FontWeight.Normal,
            color = if (used) MaterialTheme.cocColors.accent
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 首字圆形头像：软底平面 */
@Composable
fun InitialAvatar(
    name: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.firstOrNull()?.toString() ?: "?",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

// ─── 分段选择器 ────────────────────────────────────────────

/**
 * 编辑风分段 Tab：软底容器 + 墨色选中块（浅色）/纸色块（深色）。
 * 替代 M3 TabRow，去掉下划线指示器。
 */
@Composable
fun SegmentedTabs(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CocShape.panel)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(CocShape.chip)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else Color.Transparent
                    )
                    .clickable { onSelect(index) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 筛选小胶囊：选中=墨块纸字，未选=细线边框 */
@Composable
fun FilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = modifier,
        shape = CocShape.chip,
        color = bg,
        border = BorderStroke(
            1.dp,
            if (selected) Color.Transparent else MaterialTheme.cocColors.hairline
        ),
        onClick = onClick,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = fg,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp)
        )
    }
}

// ─── 图标按钮（细线边框风格） ────────────────────────────────

/** 方形细线图标按钮，用于页眉操作区 */
@Composable
fun CocIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false
) {
    Surface(
        modifier = modifier.size(38.dp),
        shape = CocShape.panel,
        color = if (filled) MaterialTheme.colorScheme.primary else Color.Transparent,
        border = if (filled) null else BorderStroke(1.dp, MaterialTheme.cocColors.hairline),
        onClick = onClick,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = if (filled) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

// ─── 空状态 ────────────────────────────────────────────────

/** 编辑级空状态：大留白 + 眉题 + 说明；可带图标（替代默认细线） */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    icon: ImageVector? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.size(34.dp)
            )
        } else {
            Box(
                Modifier
                    .width(28.dp)
                    .height(1.dp)
                    .background(MaterialTheme.cocColors.hairline)
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        if (body != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.4
            )
        }
    }
}

// ─── 设置/入口行 ────────────────────────────────────────────

/** 设置列表行：图标 + 标题 + 副标题，右侧箭头，整行可点击（工具页/设置页共用）。 */
@Composable
fun ToolsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(1.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                // 长描述最多两行，超出省略：避免窄屏/大字号下异常换行把行高撑得过高
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(18.dp)
        )
    }
}
