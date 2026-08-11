package com.cocwar.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cocwar.ui.components.CocCard
import com.cocwar.ui.components.ScreenHeader
import com.cocwar.ui.components.SectionTitle
import com.cocwar.ui.components.SettingsRow
import com.cocwar.ui.theme.cocColors

/**
 * 设置首页：目录式多级导航。
 * 彩色图标分组卡片列出 5 个入口（外观 / 数据管理 / 截图工具 / 通用 / 关于），
 * 点击进入各自子页面。
 */
@Composable
fun SettingsScreen(
    onOpenAppearance: () -> Unit,
    onOpenData: () -> Unit,
    onOpenCapture: () -> Unit,
    onOpenGeneral: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        ScreenHeader(
            title = "设置",
            overline = "偏好与数据",
            subtitle = "外观 · 数据 · 截图 · 通用 · 关于"
        )

        SectionTitleWithPadding("偏好")
        CocCard(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Column {
                SettingsRow(
                    icon = Icons.Filled.Palette,
                    iconColor = MaterialTheme.cocColors.accent,
                    title = "外观",
                    subtitle = "主题风格与配色",
                    onClick = onOpenAppearance
                )
                SettingsRow(
                    icon = Icons.Filled.Backup,
                    iconColor = MaterialTheme.cocColors.roleElder,
                    title = "数据管理",
                    subtitle = "云端同步 · 备份 · 导出导入",
                    onClick = onOpenData
                )
                SettingsRow(
                    icon = Icons.Filled.Screenshot,
                    iconColor = MaterialTheme.cocColors.star,
                    title = "截图工具",
                    subtitle = "悬浮球 · 滑动步长 · 自动清理",
                    onClick = onOpenCapture
                )
                SettingsRow(
                    icon = Icons.Filled.Tune,
                    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    title = "通用",
                    subtitle = "更新 · 清理缓存",
                    onClick = onOpenGeneral,
                    showDivider = false
                )
            }
        }

        SectionTitleWithPadding("关于")
        CocCard(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            SettingsRow(
                icon = Icons.Filled.Info,
                iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                title = "关于",
                subtitle = "版本信息与 App 简介",
                onClick = onOpenAbout,
                showDivider = false
            )
        }

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun SectionTitleWithPadding(text: String) {
    SectionTitle(text, Modifier.padding(horizontal = 20.dp))
}
