package com.jielu.timetable.wear.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.ScalingLazyListScope
import androidx.wear.compose.material.ScalingLazyListState
import androidx.wear.compose.material.Text
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter

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
 * 这里换成 [ArcScrollIndicator]：贴右侧表盘弧线、平时完全隐身，只在真的滚动时出现。
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
 * 三条规则：
 * - 平时不出现，手指一动才亮，停手 1.7 秒后淡出；
 * - 指示块用「阻尼跟随」追目标位置，不直接把每一帧的像素值画出来 ——
 *   低帧率表上原来那种一跳一跳的感觉就是这么来的；
 * - 弧线沿表盘右缘、以 3 点钟方向为中心左右对称，比之前更粗一点。
 *
 * 位置和透明度都在绘制阶段读取，滚动时只重绘这一小块，不牵动整页重组。
 */
@Composable
fun ArcScrollIndicator(state: ScalingLazyListState, modifier: Modifier = Modifier) {
    val thumbColor = MaterialTheme.colors.primary
    val follow = remember { ScrollFollow() }

    // 滚动时亮，松手后再挂一会儿
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.collectLatest { scrolling ->
            if (scrolling) {
                revealed = true
            } else {
                delay(1700)
                revealed = false
            }
        }
    }
    val alpha = animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(durationMillis = if (revealed) 150 else 380),
        label = "arc-alpha"
    )

    // 只在真的滚动时跑帧循环，松手后补完最后一段就停，不常驻空转
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }
            .filter { it }
            .collect {
                var last = withFrameNanos { it }
                while (true) {
                    val frame = withFrameNanos { it }
                    if (!follow.measure(state)) break
                    if (!follow.initialised) follow.snap()
                    val done = follow.step(frame - last)
                    last = frame
                    if (!state.isScrollInProgress && done) break
                }
            }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val a = alpha.value
        if (a < 0.01f) return@Canvas
        if (state.layoutInfo.totalItemsCount <= 0) return@Canvas
        // 一屏就能看全（前后都滚不动）时不画指示器
        if (!state.canScrollBackward && !state.canScrollForward) return@Canvas

        val radius = min(size.width, size.height) / 2f - 5.dp.toPx()
        if (radius <= 0f) return@Canvas
        val center = Offset(size.width / 2f, size.height / 2f)
        val arcStart = -16f
        val arcSweep = 32f

        val thumbSweep = arcSweep * follow.fraction
        val thumbStart = arcStart + (arcSweep - thumbSweep) * follow.progress
        val topLeft = Offset(center.x - radius, center.y - radius)
        val arcSize = Size(radius * 2f, radius * 2f)

        drawArc(
            color = JieluColors.OnSurface.copy(alpha = 0.16f * a),
            startAngle = arcStart,
            sweepAngle = arcSweep,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
        drawArc(
            color = thumbColor.copy(alpha = 0.92f * a),
            startAngle = thumbStart,
            sweepAngle = thumbSweep,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = 4.6.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

/**
 * 滚动位置 + 阻尼跟随。
 *
 * 位置取「第一个可见条目 + 它在视口上方被卷走的比例」：比例用未缩放的高度来算，
 * 不会因为条目靠近表盘边缘被缩小而抖；条目交接时是连续的，指示块不会再一格一格地跳。
 * 跟随用指数阻尼，目标每帧更新一次，画出来的值慢半拍，手感就软了。
 */
private class ScrollFollow {
    var progress by mutableFloatStateOf(0f)
    var fraction by mutableFloatStateOf(0.36f)
    private var targetProgress = 0f
    private var targetFraction = 0.36f
    var initialised = false

    /** @return 列表里有没有可用的测量结果 */
    fun measure(state: ScalingLazyListState): Boolean {
        val info = state.layoutInfo
        val total = info.totalItemsCount
        val visible = info.visibleItemsInfo
        if (total <= 1 || visible.isEmpty()) return false
        // 自动居中的占位项索引会被报成 -1，跳过它
        val first = visible.minByOrNull { if (it.index < 0) Int.MAX_VALUE else it.index } ?: return false
        if (first.index < 0) return false
        val extent = (if (first.unadjustedSize > 0) first.unadjustedSize else first.size).toFloat()
        if (extent <= 0f) return false
        // unadjustedOffset 是「条目中心到视口中心线」的距离（ScalingLazyColumn 默认
        // 的 ItemCenter 锚点）。减掉半个条目、再加上半屏，就得到条目顶部到内容区顶部的距离。
        val centerLine = (info.viewportStartOffset + info.viewportEndOffset) / 2f
        val top = first.unadjustedOffset - extent / 2f + centerLine - info.beforeContentPadding
        val within = (-top / extent).coerceIn(0f, 0.999f)
        val span = (total - visible.size).coerceAtLeast(1)
        val raw = ((first.index + within) / span).coerceIn(0f, 1f)
        // 两端直接钉死，避免「总数里算了占位项」导致差一点点到不了头
        targetProgress = when {
            !state.canScrollBackward -> 0f
            !state.canScrollForward -> 1f
            else -> raw
        }
        targetFraction = (visible.size.toFloat() / total).coerceIn(0.26f, 0.5f)
        return true
    }

    fun snap() {
        progress = targetProgress
        fraction = targetFraction
        initialised = true
    }

    /** @return 是否已经追上目标（可以停帧了） */
    fun step(dtNanos: Long): Boolean {
        // 时间常数 55ms：停下时软软地收尾，不会拖泥带水
        val k = 1f - exp(-(dtNanos / 1_000_000_000f) / 0.055f)
        progress += (targetProgress - progress) * k
        fraction += (targetFraction - fraction) * k
        val done = abs(targetProgress - progress) < 0.0008f &&
            abs(targetFraction - fraction) < 0.0008f
        if (done) {
            progress = targetProgress
            fraction = targetFraction
        }
        return done
    }
}

/**
 * 表盘式的进度圆环。直径取短边，任何屏幕比例下都是正圆。
 *
 * [progress] 用 lambda 传，读值发生在绘制阶段：动画每一帧只重绘，
 * 不会带着整个「此刻」页一起重组。
 */
@Composable
fun ProgressRing(
    progress: () -> Float,
    color: Color,
    modifier: Modifier = Modifier,
    stroke: Dp = 6.dp,
    trackColor: Color = JieluColors.SurfaceHigh
) {
    Canvas(modifier = modifier) {
        val width = stroke.toPx()
        val inset = width / 2f + 3.dp.toPx()
        val diameter = min(size.width, size.height) - inset * 2f
        if (diameter <= 0f) return@Canvas
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        val arcSize = Size(diameter, diameter)

        drawArc(
            color = trackColor,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = width, cap = StrokeCap.Round)
        )
        val value = progress().coerceIn(0f, 1f)
        if (value <= 0.002f) return@Canvas
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * value,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = width, cap = StrokeCap.Round)
        )
    }
}

/**
 * 小号 indeterminate 圆环，M3 for Wear 里 `CircularProgressIndicator` 的样子：
 * 一圈极淡的轨道加一段跟着转的强调色弧。
 *
 * 只在真的等数据时才出现，而且由调用方延迟一小会儿再亮，
 * 正常几毫秒就算完的页面不会闪一下。
 */
@Composable
fun LoadingRing(
    modifier: Modifier = Modifier,
    diameter: Dp = 22.dp,
    stroke: Dp = 2.5.dp
) {
    val transition = rememberInfiniteTransition(label = "ring")
    // 这里刻意不 by，留成 State 到 draw 阶段再读：转起来只重绘，不重组
    val angle = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
        label = "angle"
    )
    val trackColor = JieluColors.OnSurface.copy(alpha = 0.12f)
    val accent = MaterialTheme.colors.primary
    Canvas(modifier = modifier.size(diameter)) {
        val width = stroke.toPx()
        val inset = width / 2f
        val arcSize = Size(size.width - width, size.height - width)
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = width)
        )
        // 角度在绘制阶段读取，转起来只重绘这一小块，不牵动整页重组
        drawArc(
            color = accent,
            startAngle = angle.value,
            sweepAngle = 80f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = width, cap = StrokeCap.Round)
        )
    }
}

/** 居中的圆环 + 一行说明，用于页面首帧的等待态。 */
@Composable
fun LoadingBlock(text: String = "", modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        LoadingRing()
        if (text.isNotEmpty()) {
            Text(
                text = text,
                style = MaterialTheme.typography.caption2,
                color = JieluColors.Muted
            )
        }
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
