package com.jielu.timetable.wear

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ExperimentalWearMaterialApi
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.ScalingLazyListScope
import androidx.wear.compose.material.ScalingLazyListState
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import androidx.wear.compose.material.rememberScalingLazyListState
import com.jielu.timetable.wear.data.Adjustment
import com.jielu.timetable.wear.data.Course
import com.jielu.timetable.wear.data.CourseState
import com.jielu.timetable.wear.data.DayAdjustment
import com.jielu.timetable.wear.data.ScheduleData
import com.jielu.timetable.wear.data.ScheduleStore
import com.jielu.timetable.wear.data.SettingsStore
import com.jielu.timetable.wear.data.WearPalettes
import com.jielu.timetable.wear.data.stateAt
import com.jielu.timetable.wear.data.weekdayCN
import com.jielu.timetable.wear.ui.JieluColors
import com.jielu.timetable.wear.ui.JieluWearTheme
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.delay

private const val PAGE_COUNT = 4

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WearApp() {
    val data by ScheduleStore.data.collectAsState()
    val settings by SettingsStore.settings.collectAsState()
    val now by rememberNow()
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
                    // 只组合当前页，滑动时再即时构建相邻页，减少圆表上的布局压力
                    beyondBoundsPageCount = 0
                ) { page ->
                    when (page) {
                        0 -> NowScreen(data, now, settings.showRing)
                        1 -> AgendaScreen(data, now, settings.hideFinished)
                        2 -> WeekScreen(data, now)
                        else -> SettingsScreen(data)
                    }
                }
            }
            PageDots(
                current = pagerState.currentPage,
                scrolling = pagerState.isScrollInProgress,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
            )
        }
    }
}

/** 页点移到表盘底部，避开顶部 TimeText；首屏且未滑动时隐藏，保持干净。 */
@Composable
private fun PageDots(current: Int, scrolling: Boolean, modifier: Modifier = Modifier) {
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

@Composable
private fun rememberNow(): State<LocalDateTime> {
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

/* ---------- 通用列表 ---------- */

/**
 * 圆表上的标准滚动容器：内容用 ScalingLazyColumn 跟随曲率缩放，
 * 右侧叠加一条只在滚动时浮现的 PositionIndicator，滚动位置一目了然。
 */
@OptIn(ExperimentalWearMaterialApi::class)
@Composable
private fun ScrollColumn(
    state: ScalingLazyListState,
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 26.dp),
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
        PositionIndicator(
            scalingLazyListState = state,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(vertical = 36.dp, horizontal = 2.dp)
        )
    }
}

/* ---------- 此刻 ---------- */

@Composable
private fun NowScreen(data: ScheduleData?, now: LocalDateTime, showRing: Boolean) {
    val date = now.toLocalDate()
    val minutes = now.hour * 60 + now.minute
    val state = data?.stateAt(date, minutes)

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (state) {
            is CourseState.Active -> ActiveContent(state, showRing)
            is CourseState.Upcoming -> UpcomingContent(state)
            is CourseState.Done -> MessageContent(
                title = "今天的课结束啦",
                big = state.lastEnd,
                hint = "最后一节 ${state.lastEnd} 下课"
            )
            CourseState.Empty, null -> MessageContent(
                title = "还没有课表",
                big = "—",
                hint = "设置页可从手机同步，或粘贴课表文本"
            )
        }
    }
}

@Composable
private fun ActiveContent(state: CourseState.Active, showRing: Boolean) {
    val color = JieluColors.course(state.course.tone)
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (showRing) Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 6.dp.toPx()
            val inset = stroke / 2 + 3.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(
                color = JieluColors.SurfaceHigh,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * state.progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }

        Column(
            modifier = Modifier.padding(horizontal = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "正在上课", style = MaterialTheme.typography.caption2, color = color)
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = state.course.name,
                style = MaterialTheme.typography.title1,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = state.course.room,
                style = MaterialTheme.typography.caption1,
                color = JieluColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(7.dp))
            Text(
                text = "还有 ${state.leftMinutes} 分钟",
                style = MaterialTheme.typography.body1,
                color = color
            )
        }
    }
}

@Composable
private fun UpcomingContent(state: CourseState.Upcoming) {
    val color = JieluColors.course(state.course.tone)
    Column(
        modifier = Modifier.padding(horizontal = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "${state.inMinutes} 分钟后",
            style = MaterialTheme.typography.caption1,
            color = color
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = state.course.name,
            style = MaterialTheme.typography.title1,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${state.course.start} · ${state.course.room}",
            style = MaterialTheme.typography.caption1,
            color = JieluColors.Muted,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

@Composable
private fun MessageContent(title: String, big: String, hint: String) {
    Column(
        modifier = Modifier.padding(horizontal = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.caption1,
            color = JieluColors.Muted,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = big, style = MaterialTheme.typography.display2)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = hint,
            style = MaterialTheme.typography.caption2,
            color = JieluColors.Muted,
            textAlign = TextAlign.Center
        )
    }
}

/* ---------- 今日 ---------- */

@Composable
private fun AgendaScreen(data: ScheduleData?, now: LocalDateTime, hideFinished: Boolean) {
    var selected by remember { mutableStateOf<Course?>(null) }
    BackHandler(enabled = selected != null) { selected = null }
    val current = selected

    if (current != null) {
        CourseDetailScreen(current) { selected = null }
        return
    }

    val date = now.toLocalDate()
    val minutes = now.hour * 60 + now.minute
    val today = data?.coursesOn(date).orEmpty()
    val adjustment = data?.adjustmentOn(date)
    val courses = if (hideFinished) {
        today.filter { minutes < ScheduleData.minutesOf(it.end) }
    } else {
        today
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
                Text(
                    text = if (data != null) "${weekdayCN(date)} · 第 ${data.weekOf(date)} 周" else "今天",
                    style = MaterialTheme.typography.caption2,
                    color = JieluColors.Muted
                )
                Text(
                    text = if (courses.isEmpty()) "没有课" else "${courses.size} 节课",
                    style = MaterialTheme.typography.title3
                )
                if (adjustment != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = adjustmentLabel(adjustment),
                        style = MaterialTheme.typography.caption2,
                        color = MaterialTheme.colors.primary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        items(courses.size) { index ->
            val course = courses[index]
            val start = ScheduleData.minutesOf(course.start)
            val end = ScheduleData.minutesOf(course.end)
            val isNow = minutes in start until end
            val isPast = minutes >= end
            CourseChip(course, isNow, isPast) { selected = course }
        }
        if (courses.isEmpty()) {
            item {
                Text(
                    text = if (today.isEmpty()) "今天可以慢一点" else "今天的课都上完了",
                    style = MaterialTheme.typography.caption2,
                    color = JieluColors.Muted,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun CourseChip(course: Course, isNow: Boolean, isPast: Boolean, onClick: () -> Unit) {
    val tone = JieluColors.course(course.tone)
    Chip(
        modifier = Modifier.fillMaxWidth(),
        label = {
            Text(
                text = course.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isPast) JieluColors.Muted else JieluColors.OnSurface
            )
        },
        secondaryLabel = {
            Text(
                text = "${course.start} · ${course.room}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        onClick = onClick,
        colors = if (isNow) {
            ChipDefaults.primaryChipColors(
                backgroundColor = tone,
                contentColor = JieluColors.Background,
                secondaryContentColor = JieluColors.Background
            )
        } else {
            ChipDefaults.secondaryChipColors(
                contentColor = if (isPast) JieluColors.Muted else JieluColors.OnSurface,
                secondaryContentColor = JieluColors.Muted
            )
        }
    )
}

@Composable
private fun CourseDetailScreen(course: Course, onBack: () -> Unit) {
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
                Text(
                    text = "${course.start} – ${course.end}",
                    style = MaterialTheme.typography.caption1,
                    color = JieluColors.course(course.tone)
                )
                Text(
                    text = course.name,
                    style = MaterialTheme.typography.title2,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (course.room.isNotBlank()) {
            item { DetailRow("地点", course.room) }
        }
        if (course.teacher.isNotBlank()) {
            item { DetailRow("教师", course.teacher) }
        }
        if (course.kind.isNotBlank()) {
            item { DetailRow("类型", course.kind) }
        }
        item { DetailRow("周次", "第 ${course.weekStart}–${course.weekEnd} 周") }
        if (course.note.isNotBlank()) {
            item {
                Text(
                    text = course.note,
                    style = MaterialTheme.typography.caption2,
                    color = JieluColors.Muted,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                )
            }
        }
        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = { Text("返回") },
                onClick = onBack,
                colors = ChipDefaults.secondaryChipColors()
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
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

/* ---------- 本周 ---------- */

private data class WeekDay(val date: LocalDate, val courses: List<Course>)

@Composable
private fun WeekScreen(data: ScheduleData?, now: LocalDateTime) {
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    BackHandler(enabled = selected != null) { selected = null }

    val picked = selected
    if (picked != null) {
        DayScreen(data, picked, now) { selected = null }
        return
    }

    val today = now.toLocalDate()
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val week = data?.weekOf(today) ?: 0
    val parity = when {
        week <= 0 -> ""
        week % 2 == 1 -> "单周"
        else -> "双周"
    }
    // 七天只算一次，避免每次重组重复扫描整份课表
    val days = remember(data, monday) {
        (0..6).map { index ->
            val date = monday.plusDays(index.toLong())
            WeekDay(date, data?.coursesOn(date).orEmpty())
        }
    }

    ScrollColumn(
        state = rememberScalingLazyListState(),
        verticalArrangement = Arrangement.spacedBy(5.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 24.dp)
    ) {
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "本周", style = MaterialTheme.typography.title3)
                Text(
                    text = if (data == null) {
                        "还没有课表"
                    } else {
                        "第 $week 周${if (parity.isNotEmpty()) " · $parity" else ""}"
                    },
                    style = MaterialTheme.typography.caption2,
                    color = JieluColors.Muted
                )
            }
        }
        items(7) { index ->
            val day = days[index]
            WeekDayChip(day, day.date == today) { selected = day.date }
        }
        item {
            Text(
                text = "点一天看当天课表",
                style = MaterialTheme.typography.caption2,
                color = JieluColors.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun WeekDayChip(day: WeekDay, isToday: Boolean, onClick: () -> Unit) {
    val label = if (isToday) "今天 · ${weekdayCN(day.date)}" else weekdayCN(day.date)
    val detail = if (day.courses.isEmpty()) "无课" else "${day.courses.size} 节"
    Chip(
        modifier = Modifier.fillMaxWidth(),
        label = {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isToday) MaterialTheme.colors.primary else JieluColors.OnSurface
            )
        },
        secondaryLabel = {
            Text(text = detail, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        onClick = onClick,
        colors = if (isToday) {
            ChipDefaults.primaryChipColors(
                backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.20f),
                contentColor = MaterialTheme.colors.primary,
                secondaryContentColor = MaterialTheme.colors.primary
            )
        } else {
            ChipDefaults.secondaryChipColors(
                contentColor = JieluColors.OnSurface,
                secondaryContentColor = JieluColors.Muted
            )
        }
    )
}

@Composable
private fun DayScreen(data: ScheduleData?, date: LocalDate, now: LocalDateTime, onBack: () -> Unit) {
    var picked by remember { mutableStateOf<Course?>(null) }
    BackHandler(enabled = picked != null) { picked = null }

    val current = picked
    if (current != null) {
        CourseDetailScreen(current) { picked = null }
        return
    }

    val courses = remember(data, date) { data?.coursesOn(date).orEmpty() }
    val adjustment = data?.adjustmentOn(date)
    val week = data?.weekOf(date) ?: 0
    val parity = when {
        week <= 0 -> ""
        week % 2 == 1 -> "单周"
        else -> "双周"
    }
    val today = now.toLocalDate()
    val minutes = now.hour * 60 + now.minute

    ScrollColumn(state = rememberScalingLazyListState()) {
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${date.monthValue} 月 ${date.dayOfMonth} 日 · ${weekdayCN(date)}",
                    style = MaterialTheme.typography.caption1,
                    color = if (date == today) MaterialTheme.colors.primary else JieluColors.Muted
                )
                Text(
                    text = if (courses.isEmpty()) "没有课" else "${courses.size} 节课",
                    style = MaterialTheme.typography.title2
                )
                if (week > 0) {
                    Text(
                        text = "第 $week 周${if (parity.isNotEmpty()) " · $parity" else ""}",
                        style = MaterialTheme.typography.caption2,
                        color = JieluColors.Muted
                    )
                }
                if (adjustment != null) {
                    Text(
                        text = adjustmentLabel(adjustment),
                        style = MaterialTheme.typography.caption2,
                        color = MaterialTheme.colors.primary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        items(courses.size) { index ->
            val course = courses[index]
            val start = ScheduleData.minutesOf(course.start)
            val end = ScheduleData.minutesOf(course.end)
            val isNow = date == today && minutes in start until end
            val isPast = date == today && minutes >= end
            CourseChip(course, isNow, isPast) { picked = course }
        }
        if (courses.isEmpty()) {
            item {
                Text(
                    text = "这天可以慢一点",
                    style = MaterialTheme.typography.caption2,
                    color = JieluColors.Muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = { Text("返回本周", maxLines = 1) },
                onClick = onBack,
                colors = ChipDefaults.secondaryChipColors()
            )
        }
    }
}

private fun adjustmentLabel(adjustment: DayAdjustment): String {
    val adj = adjustment.adjustment
    return when (adjustment.type) {
        "swap-in" -> {
            val source = adjustment.counterpart?.let { weekdayCN(it) } ?: "原来"
            "调休 · 按${source}课表${if (adj.note.isNotBlank()) " · ${adj.note}" else ""}"
        }
        "swap-out" -> {
            val target = adjustment.counterpart
                ?.let { "${it.monthValue}/${it.dayOfMonth} ${weekdayCN(it)}" }
                ?: ""
            "停课 · 已调到 $target"
        }
        else -> "放假${if (adj.note.isNotBlank()) " · ${adj.note}" else ""}"
    }
}

/* ---------- 设置 ---------- */

@Composable
private fun SettingsScreen(data: ScheduleData?) {
    val context = LocalContext.current
    val syncedAt by ScheduleStore.syncedAt.collectAsState()
    val source by ScheduleStore.source.collectAsState()
    val settings by SettingsStore.settings.collectAsState()
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }

    if (editing) {
        ImportScreen(
            draft = draft,
            onDraftChange = { draft = it },
            onCancel = { editing = false },
            onImport = {
                val count = ScheduleData.parse(draft)?.courses?.size ?: 0
                if (ScheduleStore.import(draft, "text")) {
                    notice = "已导入 $count 门课"
                    editing = false
                    draft = ""
                } else {
                    notice = "格式不对，需要含 courses 的 JSON"
                }
            }
        )
        return
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
                Text(text = "设置", style = MaterialTheme.typography.title3)
                Text(
                    text = "${settings.palette.label} · " + when {
                        data == null -> "空"
                        source == "phone" -> "手机同步 ${formatTime(syncedAt)}"
                        source == "text" -> "文本导入 ${formatTime(syncedAt)}"
                        else -> "示例数据"
                    },
                    style = MaterialTheme.typography.caption2,
                    color = JieluColors.Muted
                )
            }
        }

        item { SectionLabel("配色") }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                WearPalettes.all.forEach { palette ->
                    val selected = palette.key == settings.palette.key
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { SettingsStore.setPalette(palette.key) }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(if (selected) 30.dp else 24.dp)
                                .clip(CircleShape)
                                .background(Color(palette.accent))
                        ) {
                            if (selected) {
                                Text(
                                    text = "✓",
                                    style = MaterialTheme.typography.caption2,
                                    color = Color(palette.onAccent)
                                )
                            }
                        }
                    }
                }
            }
        }

        item { SectionLabel("显示") }

        item {
            ToggleChip(
                modifier = Modifier.fillMaxWidth(),
                checked = settings.hideFinished,
                onCheckedChange = { SettingsStore.setHideFinished(it) },
                label = { Text("折叠已结束") },
                secondaryLabel = {
                    Text(if (settings.hideFinished) "今日只看要上的课" else "今日显示全部课程")
                },
                toggleControl = {
                    Icon(
                        imageVector = ToggleChipDefaults.switchIcon(checked = settings.hideFinished),
                        contentDescription = null
                    )
                },
                colors = ToggleChipDefaults.toggleChipColors()
            )
        }

        item {
            ToggleChip(
                modifier = Modifier.fillMaxWidth(),
                checked = settings.showRing,
                onCheckedChange = { SettingsStore.setShowRing(it) },
                label = { Text("表盘进度环") },
                secondaryLabel = {
                    Text(if (settings.showRing) "沿表盘一圈显示进度" else "只留文字，更省电")
                },
                toggleControl = {
                    Icon(
                        imageVector = ToggleChipDefaults.switchIcon(checked = settings.showRing),
                        contentDescription = null
                    )
                },
                colors = ToggleChipDefaults.toggleChipColors()
            )
        }

        item { SectionLabel("调休与调课") }

        val adjustments = data?.adjustments.orEmpty()
        if (adjustments.isEmpty()) {
            item {
                Text(
                    text = "暂无临时调整。手机「设置 → 调休与调课」里可以新建调课或放假。",
                    style = MaterialTheme.typography.caption2,
                    color = JieluColors.Muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        } else {
            items(adjustments.size) { index ->
                val adj = adjustments[index]
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(adjustmentSummary(adj)) },
                    secondaryLabel = { Text("点按重置这条调整") },
                    onClick = {
                        notice = if (ScheduleStore.removeAdjustment(adj.id)) {
                            "已恢复原课表"
                        } else {
                            "重置失败"
                        }
                    },
                    colors = ChipDefaults.secondaryChipColors()
                )
            }
        }

        item { SectionLabel("数据") }

        if (notice.isNotBlank()) {
            item {
                Text(
                    text = notice,
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item {
            Text(
                text = "手机「设置 → 手表」里点发送，课表直接落到表盘上。",
                style = MaterialTheme.typography.caption2,
                color = JieluColors.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = { Text("粘贴课表文本") },
                secondaryLabel = { Text("从手机复制 JSON 后导入") },
                onClick = { editing = true; notice = "" }
            )
        }
        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = { Text("重新载入示例") },
                onClick = {
                    ScheduleStore.clear()
                    ScheduleStore.ensureSample(context)
                    notice = "示例已载入"
                },
                colors = ChipDefaults.secondaryChipColors()
            )
        }

        item { SectionLabel("关于") }

        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .clickable { openProfile(context) }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.avatar),
                    contentDescription = "shyoka 的头像",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "shyoka",
                        style = MaterialTheme.typography.title3,
                        color = MaterialTheme.colors.primary
                    )
                    Text(
                        text = "发起人 · 点开 GitHub",
                        style = MaterialTheme.typography.caption2,
                        color = JieluColors.Muted
                    )
                }
            }
        }

        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "由 WorkBuddy 设计与编码",
                    style = MaterialTheme.typography.caption2,
                    color = JieluColors.Muted
                )
                Text(
                    text = "节律 ${BuildConfig.VERSION_NAME} · MIT",
                    style = MaterialTheme.typography.caption2,
                    color = JieluColors.Muted
                )
            }
        }
    }
}

@Composable
private fun adjustmentSummary(adjustment: Adjustment): String {
    val from = adjustment.fromDate
    val to = adjustment.toDate
    return when {
        adjustment.kind == "off" && from != null ->
            "${from.monthValue}/${from.dayOfMonth} ${weekdayCN(from)} · 放假"
        from != null && to != null ->
            "${from.monthValue}/${from.dayOfMonth} ${weekdayCN(from)} → " +
                "${to.monthValue}/${to.dayOfMonth} ${weekdayCN(to)}"
        else -> "临时调整"
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption2,
        color = JieluColors.Muted,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 8.dp)
    )
}

private fun openProfile(context: android.content.Context) {
    runCatching {
        context.startActivity(
            android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("https://github.com/shyoka")
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

@Composable
private fun ImportScreen(
    draft: String,
    onDraftChange: (String) -> Unit,
    onCancel: () -> Unit,
    onImport: () -> Unit
) {
    ScrollColumn(
        state = rememberScalingLazyListState(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 24.dp)
    ) {
        item {
            Text(
                text = "粘贴课表 JSON",
                style = MaterialTheme.typography.title3,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            BasicTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(74.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(JieluColors.SurfaceHigh)
                    .padding(10.dp),
                textStyle = TextStyle(color = JieluColors.OnSurface, fontSize = 11.sp),
                cursorBrush = SolidColor(MaterialTheme.colors.primary),
                decorationBox = { inner ->
                    if (draft.isBlank()) {
                        Text(
                            text = "在这里粘贴 {\"courses\":[…]}",
                            style = MaterialTheme.typography.caption2,
                            color = JieluColors.Muted
                        )
                    }
                    inner()
                }
            )
        }
        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = { Text("导入") },
                onClick = onImport
            )
        }
        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = { Text("取消") },
                onClick = onCancel,
                colors = ChipDefaults.secondaryChipColors()
            )
        }
    }
}

private fun formatTime(epochMillis: Long): String {
    if (epochMillis <= 0L) return "未同步"
    val date = java.time.Instant.ofEpochMilli(epochMillis)
        .atZone(java.time.ZoneId.systemDefault())
    return "%02d:%02d".format(date.hour, date.minute)
}
