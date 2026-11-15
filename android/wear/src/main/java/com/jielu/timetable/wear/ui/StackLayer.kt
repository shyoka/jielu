package com.jielu.timetable.wear.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/**
 * 二级及以下的页面栈，作为独立的一层盖在 Pager 之上。
 *
 * 为什么不在 Pager 里：Pager 把每一页都当同一层级的东西，二级页一旦也在里面，
 * 左右一划就滑到「此刻」去了。抬成覆盖层之后，栈非空时 Pager 的手势直接关掉。
 *
 * 层级之间用一个进度 [progress] 驱动：
 * - `1` 当前页完全覆盖，上一级缩在后面（0.92）；
 * - `0` 当前页整体移出到右侧，上一级复位。
 *
 * 同一套渲染既能表达「进入」（0→1，新页从右滑入、旧页缩小做视差），
 * 也能表达「返回」（1→0，当前页滑出、上一级放大归位）—— 拖到一半松手时
 * 画面已经停在正确的位置，这就是预测性返回要的效果。
 */
@Composable
fun <T : Any> StackLayer(
    stack: List<T>,
    progress: Animatable<Float, AnimationVector1D>,
    onPop: () -> Unit,
    onClear: () -> Unit,
    content: @Composable (value: T, back: () -> Unit, exit: () -> Unit) -> Unit
) {
    val top = stack.lastOrNull() ?: return
    val below = stack.getOrNull(stack.lastIndex - 1)
    val scope = rememberCoroutineScope()

    // 记住上一次的层数，用来判断这次是「进」还是「原地换」
    var lastSize by remember { mutableIntStateOf(stack.size) }
    // 退场动画途中挡住重复触发
    var leaving by remember { mutableStateOf(false) }

    LaunchedEffect(stack.size, top) {
        val grew = stack.size > lastSize
        lastSize = stack.size
        if (grew) {
            // 进下一级：先摆到屏幕外，再滑进来
            progress.snapTo(0f)
            progress.animateTo(1f, LevelEnter)
        } else if (progress.value < 0.999f) {
            // 首次打开，或拖动被打断后复位
            progress.animateTo(1f, LevelEnter)
        }
    }

    // 页面里的返回按钮、硬件返回键、右滑手势最终都走这里，
    // 退出也带一段动画，而不是「啪」地消失。
    val requestBack: () -> Unit = {
        if (!leaving) {
            leaving = true
            scope.launch {
                progress.animateTo(0f, LevelExit)
                // 只剩这一级时，退回后 Pager 就是背景，进度留在 0 正好
                if (stack.size > 1) progress.snapTo(1f)
                onPop()
                leaving = false
            }
        }
    }

    val requestExit: () -> Unit = {
        if (!leaving) {
            leaving = true
            scope.launch {
                progress.animateTo(0f, LevelExit)
                onClear()
                leaving = false
            }
        }
    }

    BackHandler(enabled = true) { requestBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 手势挂在不会位移的外层，拖动时坐标才稳定
            .pointerInput(stack.size) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (progress.value < 0.62f) {
                            requestBack()
                        } else {
                            scope.launch { progress.animateTo(1f, LevelEnter) }
                        }
                    },
                    onDragCancel = {
                        scope.launch { progress.animateTo(1f, LevelEnter) }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        // 向右拖 → 进度减小 → 页面右移、露出上一级
                        val delta = -dragAmount / size.width.toFloat()
                        // UNDISPATCHED 让 snapTo 当场生效，拖动跟手且不会排到下一帧
                        scope.launch(start = CoroutineStart.UNDISPATCHED) {
                            progress.snapTo((progress.value + delta).coerceIn(0f, 1f))
                        }
                    }
                )
            }
    ) {
        if (below != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // 被压在下面时略微缩小，退场时正好放大到 1.0
                        val scale = 1f - 0.10f * progress.value
                        scaleX = scale
                        scaleY = scale
                    }
                    .background(MaterialTheme.colors.background)
            ) {
                // 被盖住的页面点不到，回调给空实现即可
                content(below, {}, {})
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = progress.value
                    translationX = (1f - p) * size.width
                    // 越靠近起点放大得越多，滑到位正好 1.0，进场的「推近」感更足
                    val scale = 1f + 0.04f * (1f - p)
                    scaleX = scale
                    scaleY = scale
                    // 滑开时收成圆角、到位变直角 —— M3 容器切换的形状变化，
                    // 也让「这是一张正在移开的卡片」一眼看得出来
                    val radius = (1f - p) * 32.dp.toPx()
                    shape = RoundedCornerShape(radius)
                    clip = radius > 0.5f
                }
                .background(MaterialTheme.colors.background)
        ) {
            content(top, requestBack, requestExit)
        }
    }
}

/* M3 Expressive 的空间曲线：进场带一点点过冲，退场干脆利落。 */
private val LevelEnter = spring<Float>(dampingRatio = 0.76f, stiffness = 320f)
private val LevelExit = spring<Float>(dampingRatio = 0.9f, stiffness = 460f)
