package com.cocwar.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.pullToRefreshIndicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cocwar.ui.theme.cocColors
import kotlinx.coroutines.delay

/**
 * 带状态反馈的下拉刷新容器。
 *
 * 替代默认的 M3 圆环指示器：悬浮胶囊指示器随下拉位移呈现，
 * 依次展示「下拉刷新 → 松开刷新 → 正在刷新… → 已刷新(✓ 淡出)」四个状态，
 * 刷新完成后给出明确的视觉反馈。
 *
 * @param isRefreshing 刷新进行中（由 ViewModel 控制）
 * @param onRefresh    触发刷新
 * @param doneText     刷新完成时的提示文案，如「已刷新」「统计已更新」
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshableBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    doneText: String = "已刷新",
    content: @Composable BoxScope.() -> Unit
) {
    val state = rememberPullToRefreshState()
    var wasRefreshing by remember { mutableStateOf(false) }
    var showDone by remember { mutableStateOf(false) }

    // 刷新 true→false 边沿触发「完成」反馈（首次组合不触发）
    LaunchedEffect(isRefreshing) {
        if (wasRefreshing && !isRefreshing) showDone = true
        wasRefreshing = isRefreshing
    }
    // 完成反馈展示一段时间后消失
    LaunchedEffect(showDone) {
        if (showDone) {
            delay(1600)
            showDone = false
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = {
            RefreshIndicator(
                modifier = Modifier.align(Alignment.TopCenter),
                state = state,
                isRefreshing = isRefreshing,
                showDone = showDone,
                doneText = doneText
            )
        },
        content = content
    )
}

/** 胶囊刷新指示器：下拉箭头（随距离旋转）→ 松开 → 旋转加载 → ✓ 完成。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RefreshIndicator(
    modifier: Modifier = Modifier,
    state: PullToRefreshState,
    isRefreshing: Boolean,
    showDone: Boolean,
    doneText: String
) {
    val distanceFraction = state.distanceFraction

    // 完成态阶段：0=隐藏 1=可见（淡入淡出由 animateFloatAsState 过渡）
    var donePhase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(showDone) {
        if (showDone) {
            donePhase = 1f
            delay(1300)
            donePhase = 0f
        }
    }

    // 下拉中（未到阈值）不显示；拖过 40% 阈值后按距离淡入
    val pulling = !isRefreshing && !showDone && distanceFraction > 0f
    if (!pulling && !isRefreshing && !showDone) return

    // 胶囊透明度：完成态 / 刷新中 / 下拉距离 三态统一过渡
    val alphaTarget = when {
        showDone -> donePhase
        isRefreshing -> 1f
        else -> ((distanceFraction - 0.4f) * 2.5f).coerceIn(0f, 1f)
    }
    val indicatorAlpha by animateFloatAsState(
        targetValue = alphaTarget,
        animationSpec = tween(160),
        label = "indicatorAlpha"
    )

    // 箭头 0°（向下）→ 180°（向上），随下拉距离平滑旋转
    val arrowRotation by animateFloatAsState(
        targetValue = if (distanceFraction >= 1f) 180f else 0f,
        animationSpec = tween(160),
        label = "arrowRotation"
    )

    val label = when {
        showDone -> doneText
        isRefreshing -> "正在刷新…"
        distanceFraction >= 1f -> "松开刷新"
        else -> "下拉刷新"
    }

    Surface(
        modifier = modifier
            .pullToRefreshIndicator(state = state, isRefreshing = isRefreshing)
            .alpha(indicatorAlpha),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.cocColors.hairline),
        shadowElevation = 6.dp,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                showDone -> Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.cocColors.accent,
                    modifier = Modifier.size(18.dp)
                )
                isRefreshing -> {
                    // 旋转的刷新图标
                    val spin = rememberInfiniteTransition(label = "refreshSpin")
                    val angle by spin.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing)),
                        label = "refreshAngle"
                    )
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = null,
                        tint = MaterialTheme.cocColors.accent,
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(angle)
                    )
                }
                else -> Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(arrowRotation)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
