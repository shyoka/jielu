package com.jielu.timetable.wear.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.rememberScalingLazyListState
import com.jielu.timetable.wear.data.Course
import com.jielu.timetable.wear.data.CourseState
import com.jielu.timetable.wear.data.DayAdjustment
import com.jielu.timetable.wear.data.ScheduleData
import com.jielu.timetable.wear.data.stateAt
import com.jielu.timetable.wear.data.weekdayCN
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/* ---------- 此刻 ---------- */

/** M3 Expressive 的「强调减速」：起手快、收尾稳，用来转圆环正合适。 */
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

@Composable
fun NowScreen(
    data: ScheduleData?,
    loading: Boolean,
    showRing: Boolean,
    visible: Boolean = true
) {
    val now by rememberNowState()
    val date = now.toLocalDate()
    val minutes = now.hour * 60 + now.minute
    // 只在「换了一天」或「过了一分钟」时重算，配色切换之类的重组不重复扫课表
    val state = remember(data, date, minutes) { data?.stateAt(date, minutes) }

    // 首次加载时数据还没到，别先把「还没有课表」亮出来 —— 那是另一种说法
    val showLoading = loading && data == null

    // 动画只认「阶段」。直接拿 state 当 key 的话，上课中每分钟的剩余时间一变
    // 就会判成新内容，界面跟着闪一次。
    val phase = when (state) {
        is CourseState.Active -> "active-${state.course.id}"
        is CourseState.Upcoming -> "upcoming-${state.course.id}"
        is CourseState.Done -> "done"
        else -> if (showLoading) "loading" else "empty"
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // 上课中 / 下一节 / 已结束之间切换时，新内容淡入并轻微放大，
        // 旧内容快速淡出 —— M3E 的 fade through，比硬切换自然。
        AnimatedContent(
            targetState = phase,
            transitionSpec = {
                (
                    fadeIn(tween(240)) + scaleIn(
                        initialScale = 0.92f,
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f)
                    )
                    ).togetherWith(fadeOut(tween(120)))
            },
            contentAlignment = Alignment.Center,
            label = "now-state"
        ) { _ ->
            // 内容始终按最新的 state 渲染；targetState 只负责决定「要不要播动画」
            when (state) {
                is CourseState.Active -> ActiveContent(state, showRing, visible)
                is CourseState.Upcoming -> UpcomingContent(state)
                is CourseState.Done -> MessageContent(
                    title = "今天的课结束啦",
                    big = state.lastEnd,
                    hint = "最后一节 ${state.lastEnd} 下课"
                )
                CourseState.Empty, null -> if (showLoading) {
                    LoadingBlock("正在载入课表")
                } else {
                    MessageContent(
                        title = "还没有课表",
                        big = "—",
                        hint = "设置页可以自己建课，也能粘贴课表或从手机同步"
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveContent(state: CourseState.Active, showRing: Boolean, visible: Boolean) {
    val color = JieluColors.course(state.course.tone)
    val target by rememberUpdatedState(state.progress.coerceIn(0f, 1f))
    val ring = remember { Animatable(0f) }

    // 每次「进入这一页」都从 0 转到当前进度；转完以后每分钟的微调只做小幅补间，
    // 不重播。visible 是 Pager 停稳后当前页的判断，滑走再回来会重新来一遍。
    LaunchedEffect(state.course.id, visible, showRing) {
        if (!visible || !showRing) return@LaunchedEffect
        ring.snapTo(0f)
        ring.animateTo(target, tween(durationMillis = 1150, easing = EmphasizedDecelerate))
        snapshotFlow { target }.collect { value ->
            ring.animateTo(value, tween(durationMillis = 520, easing = EmphasizedDecelerate))
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (showRing) {
            ProgressRing(
                progress = { ring.value },
                color = color,
                modifier = Modifier.fillMaxSize()
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
fun AgendaScreen(
    data: ScheduleData?,
    loading: Boolean,
    hideFinished: Boolean,
    onPickCourse: (Course) -> Unit
) {
    val now by rememberNowState()

    val date = now.toLocalDate()
    val minutes = now.hour * 60 + now.minute
    val today = remember(data, date) { data?.coursesOn(date).orEmpty() }
    val adjustment = data?.adjustmentOn(date)
    val courses = if (hideFinished) {
        today.filter { minutes < ScheduleData.minutesOf(it.end) }
    } else {
        today
    }

    ScrollColumn(
        state = rememberScalingLazyListState(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 24.dp)
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
                    text = when {
                        data == null && loading -> "今日"
                        courses.isEmpty() -> "没有课"
                        else -> "${courses.size} 节课"
                    },
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
        if (data == null) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (loading) {
                        LoadingBlock("正在载入课表")
                    } else {
                        Text(
                            text = "还没有课表",
                            style = MaterialTheme.typography.caption2,
                            color = JieluColors.Muted
                        )
                    }
                }
            }
        } else {
            items(courses.size) { index ->
                val course = courses[index]
                val start = ScheduleData.minutesOf(course.start)
                val end = ScheduleData.minutesOf(course.end)
                val isNow = minutes in start until end
                val isPast = minutes >= end
                CourseChip(course, isNow, isPast) { onPickCourse(course) }
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
}

@Composable
fun CourseChip(course: Course, isNow: Boolean, isPast: Boolean, onClick: () -> Unit) {
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
fun CourseDetailScreen(course: Course, onBack: () -> Unit) {
    ScrollColumn(
        state = rememberScalingLazyListState(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 24.dp)
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

/* ---------- 本周 ---------- */

data class WeekDay(val date: LocalDate, val courses: List<Course>)

@Composable
fun WeekScreen(
    data: ScheduleData?,
    loading: Boolean,
    onPickDay: (LocalDate) -> Unit
) {
    val now by rememberNowState()

    val today = now.toLocalDate()
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val week = data?.weekOf(today) ?: 0
    val parity = when {
        week <= 0 -> ""
        week % 2 == 1 -> "单周"
        else -> "双周"
    }
    // 只有真的翻到这一页才算七天：第一次是后台算，别挡着这一页的入场
    val days by produceState<List<WeekDay>?>(initialValue = null, data, monday) {
        value = if (data == null) {
            emptyList()
        } else {
            withContext(Dispatchers.Default) {
                (0..6).map { index ->
                    val date = monday.plusDays(index.toLong())
                    WeekDay(date, data.coursesOn(date))
                }
            }
        }
    }

    // 七天也就几毫秒，圆环晚一点再亮，正常看不到闪
    var ringVisible by remember { mutableStateOf(false) }
    LaunchedEffect(days) {
        ringVisible = false
        if (days == null) {
            delay(140)
            ringVisible = true
        }
    }
    val weekDays = days

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
                Text(text = "本周", style = MaterialTheme.typography.title3)
                Text(
                    text = when {
                        data == null && loading -> "正在载入"
                        data == null -> "还没有课表"
                        else -> "第 $week 周${if (parity.isNotEmpty()) " · $parity" else ""}"
                    },
                    style = MaterialTheme.typography.caption2,
                    color = JieluColors.Muted
                )
            }
        }
        when {
            data == null -> item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // 文案已经在标题里说过了，这里只留一个环
                    if (loading) LoadingRing()
                }
            }

            weekDays == null -> item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (ringVisible) LoadingRing() else Spacer(modifier = Modifier.height(22.dp))
                }
            }

            else -> {
                items(7) { index ->
                    val day = weekDays[index]
                    WeekDayChip(day, day.date == today) { onPickDay(day.date) }
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
fun DayScreen(
    data: ScheduleData?,
    date: LocalDate,
    onPickCourse: (Course) -> Unit,
    onBack: () -> Unit
) {
    val now by rememberNowState()

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
            CourseChip(course, isNow, isPast) { onPickCourse(course) }
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

fun adjustmentLabel(adjustment: DayAdjustment): String {
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
