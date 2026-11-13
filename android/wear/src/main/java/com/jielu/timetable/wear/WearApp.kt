package com.jielu.timetable.wear

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.rememberScalingLazyListState
import com.jielu.timetable.wear.data.Course
import com.jielu.timetable.wear.data.CourseState
import com.jielu.timetable.wear.data.ScheduleData
import com.jielu.timetable.wear.data.ScheduleStore
import com.jielu.timetable.wear.data.stateAt
import com.jielu.timetable.wear.data.weekdayCN
import com.jielu.timetable.wear.ui.JieluColors
import kotlinx.coroutines.delay
import java.time.LocalDateTime

private const val PAGE_COUNT = 3

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WearApp() {
    val data by ScheduleStore.data.collectAsState()
    val now by rememberNow()
    val pagerState = rememberPagerState(initialPage = 0) { PAGE_COUNT }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        timeText = { TimeText() },
        positionIndicator = {
            if (pagerState.currentPage != 0 || pagerState.isScrollInProgress) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(PAGE_COUNT) { index ->
                        Box(
                            modifier = Modifier
                                .size(if (index == pagerState.currentPage) 5.dp else 4.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index == pagerState.currentPage) JieluColors.Accent
                                    else JieluColors.SurfaceHigh
                                )
                        )
                    }
                }
            }
        }
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> NowScreen(data, now)
                1 -> AgendaScreen(data, now)
                else -> DataScreen(data)
            }
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

/* ---------- 此刻 ---------- */

@Composable
private fun NowScreen(data: ScheduleData?, now: LocalDateTime) {
    val date = now.toLocalDate()
    val minutes = now.hour * 60 + now.minute
    val state = data?.stateAt(date, minutes)

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (state) {
            is CourseState.Active -> ActiveContent(state)
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
private fun ActiveContent(state: CourseState.Active) {
    val color = JieluColors.course(state.course.tone)
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
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
private fun AgendaScreen(data: ScheduleData?, now: LocalDateTime) {
    var selected by remember { mutableStateOf<Course?>(null) }
    val current = selected

    if (current != null) {
        CourseDetailScreen(current) { selected = null }
        return
    }

    val date = now.toLocalDate()
    val courses = data?.coursesOn(date).orEmpty()
    val minutes = now.hour * 60 + now.minute

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = rememberScalingLazyListState(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 26.dp)
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
                    text = "今天可以慢一点",
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
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = rememberScalingLazyListState(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 26.dp)
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

/* ---------- 数据 ---------- */

@Composable
private fun DataScreen(data: ScheduleData?) {
    val context = LocalContext.current
    val syncedAt by ScheduleStore.syncedAt.collectAsState()
    val source by ScheduleStore.source.collectAsState()
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

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = rememberScalingLazyListState(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 26.dp)
    ) {
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "课表数据", style = MaterialTheme.typography.title3)
                Text(
                    text = when {
                        data == null -> "空"
                        source == "phone" -> "来自手机 · ${formatTime(syncedAt)}"
                        source == "text" -> "文本导入 · ${formatTime(syncedAt)}"
                        else -> "示例数据"
                    },
                    style = MaterialTheme.typography.caption2,
                    color = JieluColors.Muted
                )
            }
        }
        if (notice.isNotBlank()) {
            item {
                Text(
                    text = notice,
                    style = MaterialTheme.typography.caption2,
                    color = JieluColors.Accent,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        item {
            Text(
                text = "在手机的「设置 → 手表」里点发送，课表会直接落到表盘上。",
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
        item {
            Text(
                text = "节律 ${BuildConfig.VERSION_NAME} · MIT",
                style = MaterialTheme.typography.caption2,
                color = JieluColors.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ImportScreen(
    draft: String,
    onDraftChange: (String) -> Unit,
    onCancel: () -> Unit,
    onImport: () -> Unit
) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = rememberScalingLazyListState(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 26.dp)
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
                cursorBrush = SolidColor(JieluColors.Accent),
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
