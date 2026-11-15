package com.jielu.timetable.wear

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.TimeText
import com.jielu.timetable.wear.data.ScheduleStore
import com.jielu.timetable.wear.data.SettingsStore
import com.jielu.timetable.wear.ui.AgendaScreen
import com.jielu.timetable.wear.ui.JieluColors
import com.jielu.timetable.wear.ui.JieluWearTheme
import com.jielu.timetable.wear.ui.NowScreen
import com.jielu.timetable.wear.ui.SettingsScreen
import com.jielu.timetable.wear.ui.WeekScreen

private const val PAGE_COUNT = 4

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WearApp() {
    val data by ScheduleStore.data.collectAsState()
    val settings by SettingsStore.settings.collectAsState()
    val pagerState = rememberPagerState(initialPage = 0) { PAGE_COUNT }

    JieluWearTheme(settings.palette) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                timeText = { TimeText() }
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    // 左右各预组合一页：滑动第一帧就有现成的树，避免低端表上「先卡一下再跟手」；
                    // 同时 key 固定，来回滑动不会反复销毁重建整页的滚动状态。
                    beyondBoundsPageCount = 1,
                    key = { index -> index }
                ) { page ->
                    when (page) {
                        0 -> NowScreen(data, settings.showRing)
                        1 -> AgendaScreen(data, settings.hideFinished)
                        2 -> WeekScreen(data)
                        else -> SettingsScreen(data)
                    }
                }
            }
            PageDots(
                pagerState = pagerState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
            )
        }
    }
}

/** 页点贴在表盘底边，避开顶部 TimeText；首屏且没滑动时隐藏，保持干净。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PageDots(pagerState: PagerState, modifier: Modifier = Modifier) {
    // 状态在这里读，翻页时只重组这一行圆点，不牵动 Pager 里的页面
    val current = pagerState.currentPage
    val scrolling = pagerState.isScrollInProgress
    if (current == 0 && !scrolling) return
    Row(
        modifier = modifier,
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
