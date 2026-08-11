package com.cocwar.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cocwar.ui.components.CocCard
import com.cocwar.ui.components.CocShape
import com.cocwar.ui.components.SectionTitle
import com.cocwar.ui.theme.ThemeStyle
import com.cocwar.ui.theme.cocColors

/**
 * 设置-外观页：主题风格选择（原工具页「外观」区块迁移）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    themeStyle: ThemeStyle = ThemeStyle.LEDGER,
    onThemeChange: (ThemeStyle) -> Unit = {},
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("外观", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(4.dp))
            SectionTitle("主题风格")
            CocCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    ThemeStyle.entries.forEach { style ->
                        ThemeChip(
                            modifier = Modifier.weight(1f),
                            style = style,
                            selected = style == themeStyle,
                            onClick = { onThemeChange(style) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "主题会同时作用于战报、统计、成员与设置页面的配色与纸张质感。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

/** 主题选择 chip：双色徽章 + 名称，横向紧凑排列；选中项徽章描边 + 右上角勾选标记 + 名称高亮 */
@Composable
private fun ThemeChip(
    modifier: Modifier = Modifier,
    style: ThemeStyle,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(34.dp)
                .then(
                    if (selected) Modifier.border(2.dp, MaterialTheme.cocColors.accent, CocShape.panel)
                    else Modifier
                )
                .clip(CocShape.panel)
                .background(style.palette(false).accent),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(style.palette(true).accent)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
            // 选中态：右上角勾选标记（白底衬 + 主色对勾），比单纯描边更醒目
            if (selected) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "已选中",
                    tint = MaterialTheme.cocColors.accent,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(15.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            style.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) MaterialTheme.cocColors.accent
            else MaterialTheme.colorScheme.onSurface
        )
    }
}
