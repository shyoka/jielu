package com.jielu.timetable.wear

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.TimeText
import com.jielu.timetable.wear.data.Course
import com.jielu.timetable.wear.data.ScheduleStore
import com.jielu.timetable.wear.data.SettingsStore
import com.jielu.timetable.wear.ui.AgendaScreen
import com.jielu.timetable.wear.ui.CourseDetailScreen
import com.jielu.timetable.wear.ui.DayScreen
import com.jielu.timetable.wear.ui.JieluColors
import com.jielu.timetable.wear.ui.JieluWearTheme
import com.jielu.timetable.wear.ui.NowScreen
import com.jielu.timetable.wear.ui.SettingsDetailScreen
import com.jielu.timetable.wear.ui.SettingsRoute
import com.jielu.timetable.wear.ui.SettingsScreen
import com.jielu.timetable.wear.ui.StackLayer
import com.jielu.timetable.wear.ui.WeekScreen
import java.time.LocalDate
import kotlin.math.abs
import kotlinx.coroutines.delay

private const val PAGE_COUNT = 4

/**
 * 盖在 Pager 之上的页面。三种来源共用一个栈，
 * 于是「右滑返回」和层叠动画在所有二级页上的手感完全一致。
 */
private sealed interface OverlayScreen {
    data class Setting(val route: SettingsRoute) : OverlayScreen
    data class Day(val date: LocalDate) : OverlayScreen
    data class Lesson(val course: Course) : OverlayScreen
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WearApp() {
    val data by ScheduleStore.data.collectAsState()
    val loading by ScheduleStore.loading.collectAsState()
    val settings by SettingsStore.settings.collectAsState()
    val pagerState = rememberPagerState(initialPage = 0) { PAGE_COUNT }

    // 二级页栈：空就表示停在 Pager 的四页主界面上。
    // 它同时决定 Pager 能不能横滑 —— 二级页开着时锁住，免得一划就把页面甩走。
    val overlay = remember { mutableStateListOf<OverlayScreen>() }
    var settingsNotice by remember { mutableStateOf("") }
    // 覆盖层进度：0 完全露出 Pager，1 完全盖住。
    // 放在这里是因为 Pager 也要跟着缩放，两边共用同一个进度才不脱节。
    val layerProgress = remember { Animatable(0f) }
    val covered = overlay.isNotEmpty()

    // 冷启动最重的一块是「首帧同时建好几页」：今日页十来个 Chip、本周页七行，
    // 全在进入的那一帧里组合，低端表就会先卡半秒。所以首帧只建当前页，
    // 画面出来、且没有正在滑动时，再把邻居页预组合上，后续翻页依旧跟手。
    var prefetch by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        delay(1200)
        while (pagerState.isScrollInProgress) delay(120)
        prefetch = 1
    }

    JieluWearTheme(settings.palette) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            timeText = { TimeText() }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // 二级页压上来时整块 Pager 退到后面，形成层次
                            val scale = 1f - 0.10f * layerProgress.value
                            scaleX = scale
                            scaleY = scale
                        },
                    // 预组合相邻页：滑动第一帧就有现成的树；key 固定，来回滑动不会
                    // 反复销毁重建整页的滚动状态。首帧先取 0，见上面的 prefetch。
                    beyondBoundsPageCount = prefetch,
                    userScrollEnabled = !covered,
                    key = { index -> index }
                ) { page ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            // M3 的 shared axis：翻页时旧页缩小淡出、新页放大进入，
                            // 和 Pager 自身的位移叠在一起就有了纵深。
                            // 状态在绘制阶段读取，滑动中只改图层属性，不重组。
                            .graphicsLayer {
                                val offset = pagerState.currentPage - page +
                                    pagerState.currentPageOffsetFraction
                                val distance = abs(offset).coerceIn(0f, 1f)
                                val scale = 1f - 0.14f * distance
                                scaleX = scale
                                scaleY = scale
                                alpha = 1f - 0.45f * distance
                            }
                    ) {
                        when (page) {
                            0 -> NowScreen(data, loading, settings.showRing)
                            1 -> AgendaScreen(
                                data = data,
                                loading = loading,
                                hideFinished = settings.hideFinished,
                                onPickCourse = { overlay.add(OverlayScreen.Lesson(it)) }
                            )
                            2 -> WeekScreen(
                                data = data,
                                loading = loading,
                                onPickDay = { overlay.add(OverlayScreen.Day(it)) }
                            )
                            else -> SettingsScreen(
                                data = data,
                                notice = settingsNotice,
                                onOpen = {
                                    settingsNotice = ""
                                    overlay.add(OverlayScreen.Setting(it))
                                }
                            )
                        }
                    }
                }

                PageDots(
                    pagerState = pagerState,
                    visible = !covered,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 6.dp)
                )

                // 盖在 Pager 之上、TimeText 之下（同在 Scaffold 内容里，TimeText 后画）
                StackLayer(
                    stack = overlay,
                    progress = layerProgress,
                    onPop = {
                        if (overlay.isNotEmpty()) overlay.removeAt(overlay.lastIndex)
                    },
                    onClear = { overlay.clear() }
                ) { screen, back, exit ->
                    when (screen) {
                        is OverlayScreen.Setting -> SettingsDetailScreen(
                            route = screen.route,
                            data = data,
                            notice = settingsNotice,
                            onNotice = { settingsNotice = it },
                            onPush = { overlay.add(OverlayScreen.Setting(it)) },
                            onReplace = {
                                if (overlay.isNotEmpty()) {
                                    overlay[overlay.lastIndex] = OverlayScreen.Setting(it)
                                }
                            },
                            onBack = back,
                            onExit = exit
                        )
                        is OverlayScreen.Day -> DayScreen(
                            data = data,
                            date = screen.date,
                            onPickCourse = { overlay.add(OverlayScreen.Lesson(it)) },
                            onBack = back
                        )
                        is OverlayScreen.Lesson -> CourseDetailScreen(
                            course = screen.course,
                            onBack = back
                        )
                    }
                }
            }
        }
    }
}

/** 页点贴在表盘底边，避开顶部 TimeText；首屏且没滑动时隐藏，保持干净。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PageDots(
    pagerState: PagerState,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    // 状态在这里读，翻页时只重组这一行圆点，不牵动 Pager 里的页面
    val current = pagerState.currentPage
    val scrolling = pagerState.isScrollInProgress
    AnimatedVisibility(
        visible = visible && (current != 0 || scrolling),
        modifier = modifier,
        enter = fadeIn(tween(160)),
        exit = fadeOut(tween(160))
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(PAGE_COUNT) { index ->
                val active = index == current
                Box(
                    modifier = Modifier
                        .size(if (active) 6.dp else 4.dp)
                        .clip(CircleShape)
                        .background(
                            if (active) MaterialTheme.colors.primary else JieluColors.SurfaceHigh
                        )
                )
            }
        }
    }
}
