package com.jielu.timetable.wear.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.ScalingLazyListScope
import androidx.wear.compose.material.ScalingLazyListState
import androidx.wear.compose.material.Text
import java.time.LocalDateTime
import kotlin.math.min
import kotlinx.coroutines.delay

/** 每分钟整点刷新一次的「现在」，放在页内读取，避免整棵 Pager 一起重组。 */
@Composable
fun rememberNowState(): State<LocalDateTime> {
    val state = remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            val current = LocalDateTime.now()
            val wait = 60_000L - (current.second * 1000L + current.nano / 1_000_000L) + 40L
            delay(wait)
            state.value = LocalDateTime.now()
        }
    }
    return state
}

/**
 * 圆表上的标准滚动容器。
 *
 * 底部不再用 Material 的 PositionIndicator —— 它滚动结束就淡出，而且每帧都在重组。
 * 这里换成 [ArcScrollIndicator]：常显、贴右侧表盘弧线、只在绘制阶段读滚动状态。
 */
@Composable
fun ScrollColumn(
    state: ScalingLazyListState,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 26.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(4.dp),
    content: ScalingLazyListScope.() -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = state,
            verticalArrangement = verticalArrangement,
            contentPadding = contentPadding,
            content = content
        )
        ArcScrollIndicator(state = state)
    }
}

/**
 * M3 for Wear OS 风格的弧形滚动指示器。
 *
 * - 沿表盘右缘的弧线走，不再是一条直条；
 * - 内容放得下时整条隐藏，需要滚动时一直显示（不淡出）；
 * - 在 draw 阶段直接读 [ScalingLazyListState]，滚动时只触发重绘，不触发重组与重新测量。
 */
@Composable
fun ArcScrollIndicator(state: ScalingLazyListState, modifier: Modifier = Modifier) {
    val trackColor = JieluColors.OnSurface.copy(alpha = 0.14f)
    val thumbColor = MaterialTheme.colors.primary
    Canvas(modifier = modifier.fillMaxSize()) {
        val info = state.layoutInfo
        val total = info.totalItemsCount
        val visible = info.visibleItemsInfo
        if (total <= 0 || visible.isEmpty()) return@Canvas
        val first = visible.first().index
        val last = visible.last().index
        // 一屏就能看全，不需要指示器
        if (first == 0 && last >= total - 1) return@Canvas

        val radius = min(size.width, size.height) / 2f - 9.dp.toPx()
        if (radius <= 0f) return@Canvas
        val center = Offset(size.width / 2f, size.height / 2f)
        val arcStart = -52f
        val arcSweep = 104f

        val scrollable = (total - visible.size).coerceAtLeast(1)
        val progress = (first.toFloat() / scrollable).coerceIn(0f, 1f)
        val thumbFraction = (visible.size.toFloat() / total).coerceIn(0.16f, 0.62f)
        val thumbSweep = arcSweep * thumbFraction
        val thumbStart = arcStart + (arcSweep - thumbSweep) * progress
        val scrolling = state.isScrollInProgress

        drawArc(
            color = trackColor,
            startAngle = arcStart,
            sweepAngle = arcSweep,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
        drawArc(
            color = thumbColor.copy(alpha = if (scrolling) 1f else 0.62f),
            startAngle = thumbStart,
            sweepAngle = thumbSweep,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(width = if (scrolling) 5.dp.toPx() else 4.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

/** 设置页的分组小标题。 */
@Composable
fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption2,
        color = JieluColors.Muted,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, top = 10.dp)
    )
}

/** 一行「标签 — 值」，用于详情与设置摘要。 */
@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.caption2, color = JieluColors.Muted)
        Text(
            text = value,
            style = MaterialTheme.typography.caption1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 小方形步进按钮，用在一行里放三四个（时间加减、周数增减）。
 * 比 Chip 更紧凑，命中区仍有 46dp。
 */
@Composable
fun StepButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    size: Int = 46
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(
                if (accent) MaterialTheme.colors.primary.copy(alpha = 0.20f) else JieluColors.SurfaceHigh
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = if (size <= 40) MaterialTheme.typography.caption2 else MaterialTheme.typography.caption1,
            textAlign = TextAlign.Center,
            maxLines = 1,
            color = if (accent) MaterialTheme.colors.primary else JieluColors.OnSurface
        )
    }
}
