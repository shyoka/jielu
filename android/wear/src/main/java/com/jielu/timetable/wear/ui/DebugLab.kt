package com.jielu.timetable.wear.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.rememberScalingLazyListState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 调试模式的跨页面小信号。
 *
 * 页点挂在 WearApp 里、动画实验室在覆盖层深处，中间隔了好几层，
 * 为一条「闪一下」的指令把回调一路传下去不值得。这里就放一个计数器。
 */
object DebugBus {
    val dotsFlash = MutableStateFlow(0)

    fun flashDots() {
        dotsFlash.value += 1
    }
}

private val LabEase = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

/**
 * 动画实验室：只播动画，不动课表。
 *
 * 进度环、加载环、覆盖层转场、滚动条阻尼都能在这儿重播，
 * 不用等到真的上课、真的慢网才看见效果。
 */
@Composable
fun DebugLabScreen(onPush: (SettingsRoute) -> Unit, onBack: () -> Unit) {
    var replay by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    val ring = remember { Animatable(0f) }

    LaunchedEffect(replay) {
        ring.snapTo(0f)
        ring.animateTo(0.72f, tween(durationMillis = 1200, easing = LabEase))
    }
    LaunchedEffect(loading) {
        if (loading) {
            delay(2400)
            loading = false
        }
    }

    ScrollColumn(
        state = rememberScalingLazyListState(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 24.dp)
    ) {
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "动画实验室", style = MaterialTheme.typography.title3)
                Text(
                    text = "只播动画，不改课表",
                    style = MaterialTheme.typography.caption2,
                    color = JieluColors.Muted
                )
            }
        }
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(JieluColors.Surface),
                contentAlignment = Alignment.Center
            ) {
                if (loading) {
                    LoadingBlock("正在载入…")
                } else {
                    ProgressRing(
                        progress = { ring.value },
                        color = MaterialTheme.colors.primary,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp)
                    )
                }
            }
        }
        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = { Text("重播进度环") },
                secondaryLabel = { Text("从 0 转到 72%") },
                onClick = { replay += 1 }
            )
        }
        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = { Text("加载动画") },
                secondaryLabel = { Text("转 2.4 秒后自己停") },
                onClick = { loading = true },
                colors = ChipDefaults.secondaryChipColors()
            )
        }
        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = { Text("覆盖层转场") },
                secondaryLabel = { Text("进一层，再右滑回来") },
                onClick = { onPush(SettingsRoute.DebugOverlay) },
                colors = ChipDefaults.secondaryChipColors()
            )
        }
        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = { Text("滚动条与阻尼") },
                secondaryLabel = { Text("一整页假条目，随便滑") },
                onClick = { onPush(SettingsRoute.DebugScroll) },
                colors = ChipDefaults.secondaryChipColors()
            )
        }
        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = { Text("闪一下页点") },
                secondaryLabel = { Text("退回主界面，看底部三个点") },
                onClick = {
                    // 页点在 Pager 那一层，先退回去再点亮，才看得见
                    onBack()
                    DebugBus.flashDots()
                },
                colors = ChipDefaults.secondaryChipColors()
            )
        }
        item { BackChip("返回设置", onBack) }
    }
}

/** 一层空的覆盖页，专门用来感受进入 / 返回 / 拖动中的预测性返回。 */
@Composable
fun DebugOverlayScreen(onBack: () -> Unit) {
    ScrollColumn(
        state = rememberScalingLazyListState(),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "覆盖层", style = MaterialTheme.typography.title3)
                Text(
                    text = "这一页从右边滑进来",
                    style = MaterialTheme.typography.caption2,
                    color = JieluColors.Muted
                )
            }
        }
        item {
            Text(
                text = "从屏幕左缘往右拖，页面会跟着手指走；\n松手过半就返回，没过半就弹回去。",
                style = MaterialTheme.typography.caption1,
                color = JieluColors.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item { BackChip("返回上一级", onBack) }
    }
}

/** 一页假条目：上下滑动，看右侧弧线的阻尼与自动隐藏。 */
@Composable
fun DebugScrollScreen(onBack: () -> Unit) {
    ScrollColumn(
        state = rememberScalingLazyListState(),
        verticalArrangement = Arrangement.spacedBy(5.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 24.dp)
    ) {
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "滚动条", style = MaterialTheme.typography.title3)
                Text(
                    text = "滑动时才出现 · 停手 1.7 秒淡出",
                    style = MaterialTheme.typography.caption2,
                    color = JieluColors.Muted,
                    textAlign = TextAlign.Center
                )
            }
        }
        items(18) { index ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(JieluColors.SurfaceHigh),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "第 ${index + 1} 行",
                    style = MaterialTheme.typography.caption1,
                    color = JieluColors.OnSurface,
                    modifier = Modifier.padding(horizontal = 14.dp)
                )
            }
        }
        item { BackChip("返回上一级", onBack) }
    }
}

@Composable
private fun BackChip(text: String, onBack: () -> Unit) {
    Chip(
        modifier = Modifier.fillMaxWidth(),
        label = { Text(text, maxLines = 1) },
        onClick = onBack,
        colors = ChipDefaults.secondaryChipColors()
    )
}
