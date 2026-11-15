package com.jielu.timetable.wear.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import androidx.wear.compose.material.rememberScalingLazyListState
import com.jielu.timetable.wear.BuildConfig
import com.jielu.timetable.wear.R
import com.jielu.timetable.wear.data.Course
import com.jielu.timetable.wear.data.LocalOverrides
import com.jielu.timetable.wear.data.Period
import com.jielu.timetable.wear.data.ScheduleData
import com.jielu.timetable.wear.data.ScheduleStore
import com.jielu.timetable.wear.data.SettingsStore
import com.jielu.timetable.wear.data.WearPalettes
import com.jielu.timetable.wear.data.weekdayCN
import java.time.LocalDate

private sealed interface SettingsRoute {
    data object Root : SettingsRoute
    data object AdjustList : SettingsRoute
    data object AdjustNew : SettingsRoute
    data object Term : SettingsRoute
    data object Periods : SettingsRoute
    data class PeriodEdit(val index: Int) : SettingsRoute
    data object Courses : SettingsRoute
    data class CourseDay(val date: LocalDate) : SettingsRoute
    data class CourseNew(val fromDay: LocalDate? = null) : SettingsRoute
    data object Import : SettingsRoute
}

private fun parentOf(route: SettingsRoute): SettingsRoute = when (route) {
    SettingsRoute.AdjustNew -> SettingsRoute.AdjustList
    is SettingsRoute.PeriodEdit -> SettingsRoute.Periods
    is SettingsRoute.CourseDay -> SettingsRoute.Courses
    is SettingsRoute.CourseNew -> route.fromDay?.let { SettingsRoute.CourseDay(it) } ?: SettingsRoute.Courses
    SettingsRoute.Import -> SettingsRoute.Root
    else -> SettingsRoute.Root
}

@Composable
fun SettingsScreen(data: ScheduleData?) {
    var route by remember { mutableStateOf<SettingsRoute>(SettingsRoute.Root) }
    var notice by remember { mutableStateOf("") }

    BackHandler(enabled = route != SettingsRoute.Root) {
        route = parentOf(route)
        notice = ""
    }

    when (val current = route) {
        SettingsRoute.Root -> SettingsHome(
            data = data,
            notice = notice,
            onOpen = { route = it; notice = "" }
        )
        SettingsRoute.AdjustList -> AdjustmentListScreen(
            data = data,
            notice = notice,
            onNew = { route = SettingsRoute.AdjustNew; notice = "" },
            onNotice = { notice = it },
            onBack = { route = SettingsRoute.Root }
        )
        SettingsRoute.AdjustNew -> AdjustmentWizard(
            onSaved = {
                route = SettingsRoute.AdjustList
                notice = it
            },
            onBack = { route = SettingsRoute.AdjustList }
        )
        SettingsRoute.Term -> TermScreen(
            data = data,
            onBack = { route = SettingsRoute.Root }
        )
        SettingsRoute.Periods -> PeriodsScreen(
            data = data,
            onEdit = { route = SettingsRoute.PeriodEdit(it) },
            onBack = { route = SettingsRoute.Root }
        )
        is SettingsRoute.PeriodEdit -> PeriodEditScreen(
            data = data,
            index = current.index,
            onDone = { route = SettingsRoute.Periods },
            onBack = { route = SettingsRoute.Periods }
        )
        SettingsRoute.Courses -> CoursesScreen(
            data = data,
            notice = notice,
            onPick = { route = SettingsRoute.CourseDay(it); notice = "" },
            onNew = { route = SettingsRoute.CourseNew(); notice = "" },
            onBack = { route = SettingsRoute.Root }
        )
        is SettingsRoute.CourseDay -> CourseDayScreen(
            data = data,
            date = current.date,
            notice = notice,
            onNotice = { notice = it },
            onNew = { date -> route = SettingsRoute.CourseNew(date) },
            onBack = { route = SettingsRoute.Courses }
        )
        is SettingsRoute.CourseNew -> CourseNewScreen(
            data = data,
            presetDay = current.fromDay?.let(::weekdayIndex),
            onSaved = { message, day ->
                // 存完就落在那一天的列表上，方便接着加下一节
                route = SettingsRoute.CourseDay(weekDateOf(day))
                notice = message
            },
            onBack = {
                route = current.fromDay?.let { SettingsRoute.CourseDay(it) }
                    ?: SettingsRoute.Courses
            }
        )
        SettingsRoute.Import -> ImportScreen(
            onBack = { route = SettingsRoute.Root },
            onImported = {
                route = SettingsRoute.Root
                notice = it
            }
        )
    }
}

/* ---------- 设置首页 ---------- */

@Composable
private fun SettingsHome(
    data: ScheduleData?,
    notice: String,
    onOpen: (SettingsRoute) -> Unit
) {
    val context = LocalContext.current
    val syncedAt by ScheduleStore.syncedAt.collectAsState()
    val source by ScheduleStore.source.collectAsState()
    val localEdited by ScheduleStore.localEdited.collectAsState()
    val settings by SettingsStore.settings.collectAsState()

    val sourceLabel = when {
        data == null -> "还没有课表"
        source == "phone" -> "手机同步 ${formatTime(syncedAt)}"
        source == "text" -> "文本导入 ${formatTime(syncedAt)}"
        source == "sample" -> "示例课表"
        else -> "本机课表"
    } + if (localEdited) " · 本机已改" else ""

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
                    text = "${settings.palette.label} · $sourceLabel",
                    style = MaterialTheme.typography.caption2,
                    color = JieluColors.Muted,
                    textAlign = TextAlign.Center
                )
            }
        }

        item { SectionLabel("配色") }
        item { PaletteRow(settings.palette.key) }

        item { SectionLabel("课表") }
        item {
            NavChip(
                title = "调休与调课",
                detail = if (data == null) "先建一份课表" else "${data.adjustments.size} 条调整",
                onClick = { onOpen(SettingsRoute.AdjustList) }
            )
        }
        item {
            NavChip(
                title = "学期与周次",
                detail = if (data == null) {
                    "开学日与总周数"
                } else {
                    "开学 ${monthDay(data.termStart)} · 共 ${data.term.weeks} 周"
                },
                onClick = { onOpen(SettingsRoute.Term) }
            )
        }
        item {
            NavChip(
                title = "作息时间",
                detail = data?.periods?.firstOrNull()?.let { "第 1 节 ${it.start} 开始" } ?: "上下课时间",
                onClick = { onOpen(SettingsRoute.Periods) }
            )
        }
        item {
            NavChip(
                title = "课程管理",
                detail = if (data == null) "在表上直接加课" else "${data.courses.size} 门课 · 可增删",
                onClick = { onOpen(SettingsRoute.Courses) }
            )
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
                }
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
                }
            )
        }

        item { SectionLabel("数据（手机同步是可选的）") }

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

        if (localEdited) {
            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("清除本机改动") },
                    secondaryLabel = { Text("回到手机推送或导入的原样") },
                    onClick = {
                        ScheduleStore.resetLocalEdits()
                        onOpen(SettingsRoute.Root)
                    },
                    colors = ChipDefaults.secondaryChipColors()
                )
            }
        }

        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = { Text("粘贴课表文本") },
                secondaryLabel = { Text("没手机也能导入 JSON") },
                onClick = { onOpen(SettingsRoute.Import) }
            )
        }
        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = { Text("重新载入示例") },
                onClick = {
                    ScheduleStore.clear()
                    ScheduleStore.ensureSample(context)
                    onOpen(SettingsRoute.Root)
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
                    text = "手表可独立使用，不强制配对手机",
                    style = MaterialTheme.typography.caption2,
                    color = JieluColors.Muted,
                    textAlign = TextAlign.Center
                )
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
private fun PaletteRow(selectedKey: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        WearPalettes.all.forEach { palette ->
            val selected = palette.key == selectedKey
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable { SettingsStore.setPalette(palette.key) }
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(if (selected) 26.dp else 21.dp)
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

@Composable
private fun NavChip(title: String, detail: String, onClick: () -> Unit) {
    Chip(
        modifier = Modifier.fillMaxWidth(),
        label = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        secondaryLabel = { Text(detail, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        onClick = onClick,
        colors = ChipDefaults.secondaryChipColors()
    )
}

/* ---------- 调休与调课 ---------- */

@Composable
private fun AdjustmentListScreen(
    data: ScheduleData?,
    notice: String,
    onNew: () -> Unit,
    onNotice: (String) -> Unit,
    onBack: () -> Unit
) {
    val adjustments = data?.adjustments.orEmpty()
    var armed by remember { mutableStateOf<String?>(null) }
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
                Text(text = "调休与调课", style = MaterialTheme.typography.title3)
                Text(
                    text = "在表上直接改，手机再推送也不覆盖",
                    style = MaterialTheme.typography.caption2,
                    color = JieluColors.Muted,
                    textAlign = TextAlign.Center
                )
            }
        }
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
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = { Text("新建调整") },
                secondaryLabel = { Text("放假一天，或把课挪到另一天") },
                onClick = onNew
            )
        }
        if (adjustments.isEmpty()) {
            item {
                Text(
                    text = "还没有临时调整",
                    style = MaterialTheme.typography.caption2,
                    color = JieluColors.Muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            items(adjustments.size) { index ->
                val adjustment = adjustments[index]
                val isArmed = armed == adjustment.id
                val from = adjustment.fromDate
                val to = adjustment.toDate
                val title = when {
                    adjustment.kind == "off" && from != null ->
                        "${from.monthValue}/${from.dayOfMonth} · 放假"
                    from != null && to != null ->
                        "${from.monthValue}/${from.dayOfMonth} → ${to.monthValue}/${to.dayOfMonth}"
                    else -> "临时调整"
                }
                val detail = when {
                    isArmed -> "再点一次删除"
                    adjustment.kind == "off" && from != null -> "${weekdayCN(from)} · 点按删除"
                    from != null && to != null -> "${weekdayCN(from)} → ${weekdayCN(to)}"
                    else -> "点按删除"
                }
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    secondaryLabel = { Text(detail, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        if (isArmed) {
                            if (ScheduleStore.removeAdjustment(adjustment.id)) {
                                onNotice("已删除 · $title")
                            }
                            armed = null
                        } else {
                            armed = adjustment.id
                            onNotice("再点一次删除这条")
                        }
                    },
                    colors = if (isArmed) {
                        ChipDefaults.primaryChipColors(
                            backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.22f),
                            contentColor = MaterialTheme.colors.primary,
                            secondaryContentColor = MaterialTheme.colors.primary
                        )
                    } else {
                        ChipDefaults.secondaryChipColors()
                    }
                )
            }
        }
        item { BackChip("返回设置", onBack) }
    }
}

@Composable
private fun AdjustmentWizard(
    onSaved: (String) -> Unit,
    onBack: () -> Unit
) {
    var step by remember { mutableStateOf(0) }
    var kind by remember { mutableStateOf("off") }
    var target by remember { mutableStateOf<LocalDate?>(null) }
    var sourceDate by remember { mutableStateOf<LocalDate?>(null) }
    var weekOffset by remember { mutableStateOf(0) }
    BackHandler(enabled = step > 0) { step -= 1 }

    when (step) {
        0 -> ScrollColumn(
            state = rememberScalingLazyListState(),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "新建调整", style = MaterialTheme.typography.title3)
                    Text(
                        text = "选一种，再选日期",
                        style = MaterialTheme.typography.caption2,
                        color = JieluColors.Muted
                    )
                }
            }
            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("放假一天") },
                    secondaryLabel = { Text("这天不用上课") },
                    onClick = { kind = "off"; step = 1 }
                )
            }
            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("调课到另一天") },
                    secondaryLabel = { Text("把这天的课挪到另一天") },
                    onClick = { kind = "swap"; step = 1 }
                )
            }
            item { BackChip("返回", onBack) }
        }

        1 -> DayPickerScreen(
            title = if (kind == "off") "哪天放假" else "调到哪天",
            hint = if (kind == "off") "放假当天显示无课" else "目标日按源当天的课表上",
            weekOffset = weekOffset,
            onWeekOffsetChange = { weekOffset = it },
            blocked = { false },
            onPick = { date ->
                target = date
                if (kind == "off") {
                    if (ScheduleStore.addAdjustment("off", date, null)) {
                        onSaved("已加 · ${monthDay(date)} 放假")
                    }
                } else {
                    step = 2
                }
            },
            onBack = { step = 0 }
        )

        2 -> DayPickerScreen(
            title = "把哪天的课调过来",
            hint = "选源日期，目标日会按它的课表上",
            weekOffset = weekOffset,
            onWeekOffsetChange = { weekOffset = it },
            blocked = { it == target },
            onPick = { date ->
                sourceDate = date
                step = 3
            },
            onBack = { step = 1 }
        )

        else -> {
            val from = sourceDate
            val to = target
            ScrollColumn(
                state = rememberScalingLazyListState(),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "确认调整", style = MaterialTheme.typography.title3)
                        Text(
                            text = "检查一下再保存",
                            style = MaterialTheme.typography.caption2,
                            color = JieluColors.Muted
                        )
                    }
                }
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(JieluColors.SurfaceHigh)
                            .padding(vertical = 10.dp, horizontal = 8.dp)
                    ) {
                        Text(
                            text = from?.let { "${monthDay(it)} ${weekdayCN(it)}" } ?: "—",
                            style = MaterialTheme.typography.title2
                        )
                        Text(
                            text = "↓ 把课调到这里",
                            style = MaterialTheme.typography.caption2,
                            color = JieluColors.Muted
                        )
                        Text(
                            text = to?.let { "${monthDay(it)} ${weekdayCN(it)}" } ?: "—",
                            style = MaterialTheme.typography.title2,
                            color = MaterialTheme.colors.primary
                        )
                    }
                }
                item {
                    Chip(
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("保存调整") },
                        onClick = {
                            if (from != null && to != null &&
                                ScheduleStore.addAdjustment("swap", from, to)
                            ) {
                                onSaved("已调课 · ${monthDay(from)} → ${monthDay(to)}")
                            }
                        }
                    )
                }
                item {
                    BackChip("重选", { step = 1 })
                }
            }
        }
    }
}

@Composable
private fun DayPickerScreen(
    title: String,
    hint: String,
    weekOffset: Int,
    onWeekOffsetChange: (Int) -> Unit,
    blocked: (LocalDate) -> Boolean,
    onPick: (LocalDate) -> Unit,
    onBack: () -> Unit
) {
    val today = LocalDate.now()
    val thisMonday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val monday = thisMonday.plusWeeks(weekOffset.toLong())
    val sunday = monday.plusDays(6)
    val week = weekOffset

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
                Text(text = title, style = MaterialTheme.typography.title3)
                Text(
                    text = hint,
                    style = MaterialTheme.typography.caption2,
                    color = JieluColors.Muted,
                    textAlign = TextAlign.Center
                )
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                StepButton("‹", { onWeekOffsetChange(weekOffset - 1) })
                Text(
                    text = "${monday.monthValue}/${monday.dayOfMonth}\n– ${sunday.monthValue}/${sunday.dayOfMonth}",
                    style = MaterialTheme.typography.caption2,
                    maxLines = 2,
                    color = if (week == 0) MaterialTheme.colors.primary else JieluColors.OnSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp)
                )
                StepButton("›", { onWeekOffsetChange(weekOffset + 1) })
            }
        }
        items(7) { index ->
            val date = monday.plusDays(index.toLong())
            val disabled = blocked(date)
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(
                        text = if (date == today) "今天 · ${weekdayCN(date)}" else weekdayCN(date),
                        color = when {
                            disabled -> JieluColors.Muted.copy(alpha = 0.45f)
                            date == today -> MaterialTheme.colors.primary
                            else -> JieluColors.OnSurface
                        }
                    )
                },
                secondaryLabel = {
                    Text(
                        text = "${date.monthValue} 月 ${date.dayOfMonth} 日" +
                            if (disabled) " · 与目标日相同" else "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                onClick = { if (!disabled) onPick(date) },
                colors = ChipDefaults.secondaryChipColors()
            )
        }
        item { BackChip("返回", onBack) }
    }
}

/* ---------- 学期 ---------- */

@Composable
private fun TermScreen(data: ScheduleData?, onBack: () -> Unit) {
    val start = data?.termStart ?: LocalDate.parse(LocalOverrides.DEFAULT_TERM_START)
    val weeks = data?.term?.weeks ?: LocalOverrides.DEFAULT_TERM_WEEKS
    val today = LocalDate.now()
    val currentWeek = data?.weekOf(today) ?: 0

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
                Text(text = "学期与周次", style = MaterialTheme.typography.title3)
                Text(
                    text = "开学日决定单双周，改完立即生效",
                    style = MaterialTheme.typography.caption2,
                    color = JieluColors.Muted,
                    textAlign = TextAlign.Center
                )
            }
        }
        item { SectionLabel("开学第一周的周一") }
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${start.year} 年 ${start.monthValue} 月 ${start.dayOfMonth} 日",
                    style = MaterialTheme.typography.title3,
                    maxLines = 1
                )
                Text(
                    text = weekdayCN(start) + if (start.dayOfWeek.value != 1) " · 不是周一" else "",
                    style = MaterialTheme.typography.caption2,
                    color = if (start.dayOfWeek.value == 1) {
                        JieluColors.Muted
                    } else {
                        MaterialTheme.colors.primary
                    }
                )
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StepButton("−1", { ScheduleStore.setTermStart(start.minusDays(1)) }, size = 40)
                Spacer(modifier = Modifier.width(6.dp))
                StepButton(
                    text = "周一",
                    accent = true,
                    size = 40,
                    onClick = {
                        ScheduleStore.setTermStart(
                            start.minusDays((start.dayOfWeek.value - 1).toLong())
                        )
                    }
                )
                Spacer(modifier = Modifier.width(6.dp))
                StepButton("+1", { ScheduleStore.setTermStart(start.plusDays(1)) }, size = 40)
            }
        }
        item { SectionLabel("总周数") }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                StepButton("−", { ScheduleStore.setTermWeeks(weeks - 1) })
                Text(
                    text = "$weeks 周",
                    style = MaterialTheme.typography.title2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp)
                )
                StepButton("+", { ScheduleStore.setTermWeeks(weeks + 1) })
            }
        }
        item {
            Text(
                text = when {
                    currentWeek <= 0 -> "还没开学"
                    currentWeek % 2 == 1 -> "现在是第 $currentWeek 周 · 单周"
                    else -> "现在是第 $currentWeek 周 · 双周"
                },
                style = MaterialTheme.typography.caption2,
                color = JieluColors.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item { BackChip("返回设置", onBack) }
    }
}

/* ---------- 作息时间 ---------- */

@Composable
private fun PeriodsScreen(
    data: ScheduleData?,
    onEdit: (Int) -> Unit,
    onBack: () -> Unit
) {
    val periods = data?.periods?.takeIf { it.isNotEmpty() } ?: LocalOverrides.DEFAULT_PERIODS
    var hint by remember { mutableStateOf("") }
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
                Text(text = "作息时间", style = MaterialTheme.typography.title3)
                Text(
                    text = hint.ifBlank { "点一节改上下课时间" },
                    style = MaterialTheme.typography.caption2,
                    color = if (hint.isBlank()) JieluColors.Muted else MaterialTheme.colors.primary
                )
            }
        }
        items(periods.size) { index ->
            val period = periods[index]
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = { Text("第 ${index + 1} 节") },
                secondaryLabel = { Text("${period.start} – ${period.end}") },
                onClick = { onEdit(index) },
                colors = ChipDefaults.secondaryChipColors()
            )
        }
        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = { Text("恢复默认作息") },
                onClick = {
                    ScheduleStore.setPeriods(LocalOverrides.DEFAULT_PERIODS)
                    hint = "已恢复默认作息"
                },
                colors = ChipDefaults.secondaryChipColors()
            )
        }
        item { BackChip("返回设置", onBack) }
    }
}

@Composable
private fun PeriodEditScreen(
    data: ScheduleData?,
    index: Int,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    val periods = data?.periods?.takeIf { it.isNotEmpty() } ?: LocalOverrides.DEFAULT_PERIODS
    val period = periods.getOrNull(index) ?: Period("08:00", "08:45")
    var start by remember(index, period.start) { mutableStateOf(period.start) }
    var end by remember(index, period.end) { mutableStateOf(period.end) }
    val valid = start < end

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
                Text(text = "第 ${index + 1} 节", style = MaterialTheme.typography.title3)
                Text(
                    text = if (valid) "$start – $end" else "下课要晚于上课",
                    style = MaterialTheme.typography.caption2,
                    color = if (valid) JieluColors.Muted else MaterialTheme.colors.primary
                )
            }
        }
        item { SectionLabel("上课") }
        item { TimeStepper(start) { delta -> start = shift(start, delta) } }
        item { SectionLabel("下课") }
        item { TimeStepper(end) { delta -> end = shift(end, delta) } }
        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = { Text("完成") },
                secondaryLabel = { Text("保存到这一节") },
                onClick = {
                    if (valid) {
                        ScheduleStore.updatePeriod(index, start, end)
                        onDone()
                    }
                },
                colors = ChipDefaults.primaryChipColors(
                    backgroundColor = if (valid) {
                        MaterialTheme.colors.primary
                    } else {
                        JieluColors.SurfaceHigh
                    },
                    contentColor = if (valid) MaterialTheme.colors.onPrimary else JieluColors.Muted,
                    secondaryContentColor = if (valid) MaterialTheme.colors.onPrimary else JieluColors.Muted
                )
            )
        }
        item { BackChip("取消", onBack) }
    }
}

@Composable
private fun TimeStepper(time: String, onShift: (Int) -> Unit) {
    // 两列两行，小屏（约 136dp 可用宽）也放得下
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = time,
            style = MaterialTheme.typography.title3,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                StepButton("−5", { onShift(-5) }, size = 40)
                StepButton("+5", { onShift(5) }, size = 40)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                StepButton("−1", { onShift(-1) }, size = 40)
                StepButton("+1", { onShift(1) }, size = 40)
            }
        }
    }
}

private fun shift(time: String, delta: Int): String {
    val total = (ScheduleData.minutesOf(time) + delta).coerceIn(0, 23 * 60 + 59)
    return "%02d:%02d".format(total / 60, total % 60)
}

/* ---------- 课程管理 ---------- */

@Composable
private fun CoursesScreen(
    data: ScheduleData?,
    notice: String,
    onPick: (LocalDate) -> Unit,
    onNew: () -> Unit,
    onBack: () -> Unit
) {
    val today = LocalDate.now()
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val days = remember(data, monday) {
        (0..6).map { index ->
            val date = monday.plusDays(index.toLong())
            WeekDay(date, data?.coursesOn(date).orEmpty())
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
                Text(text = "课程管理", style = MaterialTheme.typography.title3)
                Text(
                    text = if (data == null) "还没有课程，先加一节" else "点一天查看、增删课程",
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
                    color = MaterialTheme.colors.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = { Text("新增课程") },
                secondaryLabel = { Text("选星期、节次，再起名字") },
                onClick = onNew
            )
        }
        items(7) { index ->
            val day = days[index]
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(
                        text = if (day.date == today) "今天 · ${weekdayCN(day.date)}" else weekdayCN(day.date),
                        color = if (day.date == today) MaterialTheme.colors.primary else JieluColors.OnSurface
                    )
                },
                secondaryLabel = {
                    Text(
                        text = if (day.courses.isEmpty()) {
                            "${day.date.monthValue}/${day.date.dayOfMonth} · 无课"
                        } else {
                            "${day.date.monthValue}/${day.date.dayOfMonth} · ${day.courses.size} 节"
                        }
                    )
                },
                onClick = { onPick(day.date) },
                colors = ChipDefaults.secondaryChipColors()
            )
        }
        item { BackChip("返回设置", onBack) }
    }
}

@Composable
private fun CourseDayScreen(
    data: ScheduleData?,
    date: LocalDate,
    notice: String,
    onNotice: (String) -> Unit,
    onNew: (LocalDate) -> Unit,
    onBack: () -> Unit
) {
    val courses = remember(data, date) { data?.coursesOn(date).orEmpty() }
    var armed by remember { mutableStateOf<String?>(null) }

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
                    text = "${date.monthValue}/${date.dayOfMonth} ${weekdayCN(date)}",
                    style = MaterialTheme.typography.title3
                )
                Text(
                    text = if (courses.isEmpty()) "没有课" else "${courses.size} 节课",
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
                    color = MaterialTheme.colors.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        items(courses.size) { index ->
            val course = courses[index]
            val isArmed = armed == course.id
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = { Text(course.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                secondaryLabel = {
                    Text(
                        text = if (isArmed) "再点一次删除" else "${course.start} · ${course.room}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                onClick = {
                    if (isArmed) {
                        if (ScheduleStore.deleteCourse(course.id)) {
                            onNotice("已删除 ${course.name}")
                        }
                        armed = null
                    } else {
                        armed = course.id
                        onNotice("再点一次就删除 ${course.name}")
                    }
                },
                colors = if (isArmed) {
                    ChipDefaults.primaryChipColors(
                        backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.22f),
                        contentColor = MaterialTheme.colors.primary,
                        secondaryContentColor = MaterialTheme.colors.primary
                    )
                } else {
                    ChipDefaults.secondaryChipColors()
                }
            )
        }
        if (courses.isEmpty()) {
            item {
                Text(
                    text = "这天还没有课",
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
                label = { Text("在这天加一节课") },
                onClick = { onNew(date) }
            )
        }
        item { BackChip("返回课程列表", onBack) }
    }
}

@Composable
private fun CourseNewScreen(
    data: ScheduleData?,
    presetDay: Int?,
    onSaved: (String, Int) -> Unit,
    onBack: () -> Unit
) {
    val periods = data?.periods?.takeIf { it.isNotEmpty() } ?: LocalOverrides.DEFAULT_PERIODS
    // 从某一天进来时直接跳到选节次，少一步
    var step by remember { mutableStateOf(if (presetDay == null) 0 else 1) }
    var day by remember { mutableStateOf(presetDay ?: 1) }
    var periodIndex by remember { mutableStateOf(0) }
    var name by remember { mutableStateOf("") }
    var room by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    BackHandler(enabled = step > 0) {
        if (step == 1 && presetDay != null) onBack() else step -= 1
    }

    when (step) {
        0 -> ScrollColumn(
            state = rememberScalingLazyListState(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "新增课程", style = MaterialTheme.typography.title3)
                    Text(
                        text = "第 1 步 · 选星期",
                        style = MaterialTheme.typography.caption2,
                        color = JieluColors.Muted
                    )
                }
            }
            items(7) { index ->
                val weekday = if (index == 6) 0 else index + 1
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(weekdayLabel(weekday)) },
                    onClick = {
                        day = weekday
                        step = 1
                    },
                    colors = ChipDefaults.secondaryChipColors()
                )
            }
            item { BackChip("取消", onBack) }
        }

        1 -> ScrollColumn(
            state = rememberScalingLazyListState(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 24.dp)
        ) {
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = weekdayLabel(day), style = MaterialTheme.typography.title3)
                    Text(
                        text = if (presetDay == null) "第 2 步 · 选节次" else "选节次",
                        style = MaterialTheme.typography.caption2,
                        color = JieluColors.Muted
                    )
                }
            }
            items(periods.size) { index ->
                val period = periods[index]
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("第 ${index + 1} 节") },
                    secondaryLabel = { Text("${period.start} – ${period.end}") },
                    onClick = {
                        periodIndex = index
                        step = 2
                    },
                    colors = ChipDefaults.secondaryChipColors()
                )
            }
            item { BackChip("返回") { if (presetDay == null) step = 0 else onBack() } }
        }

        else -> ScrollColumn(
            state = rememberScalingLazyListState(),
            verticalArrangement = Arrangement.spacedBy(5.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 24.dp)
        ) {
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = weekdayLabel(day), style = MaterialTheme.typography.title3)
                    Text(
                        text = error.ifBlank { "第 ${periodIndex + 1} 节 · 起个名字" },
                        style = MaterialTheme.typography.caption2,
                        color = if (error.isBlank()) JieluColors.Muted else MaterialTheme.colors.primary
                    )
                }
            }
            item {
                BasicTextField(
                    value = name,
                    onValueChange = { name = it.take(20) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(JieluColors.SurfaceHigh)
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    textStyle = TextStyle(color = JieluColors.OnSurface, fontSize = 14.sp),
                    cursorBrush = SolidColor(MaterialTheme.colors.primary),
                    singleLine = true,
                    decorationBox = { inner ->
                        if (name.isBlank()) {
                            Text(
                                text = "课程名，如 数学",
                                style = MaterialTheme.typography.caption2,
                                color = JieluColors.Muted
                            )
                        }
                        inner()
                    }
                )
            }
            item {
                BasicTextField(
                    value = room,
                    onValueChange = { room = it.take(16) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(JieluColors.SurfaceHigh)
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    textStyle = TextStyle(color = JieluColors.OnSurface, fontSize = 12.sp),
                    cursorBrush = SolidColor(MaterialTheme.colors.primary),
                    singleLine = true,
                    decorationBox = { inner ->
                        if (room.isBlank()) {
                            Text(
                                text = "地点（可不填）",
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
                    label = { Text("保存课程") },
                    onClick = {
                        val period = periods.getOrNull(periodIndex) ?: periods.first()
                        if (name.isBlank()) {
                            error = "先给课程起个名字"
                        } else if (ScheduleStore.addCourse(day, name, period.start, period.end, room)) {
                            onSaved("已添加 ${name.trim()}", day)
                        } else {
                            error = "保存失败，检查一下时间"
                        }
                    },
                    colors = ChipDefaults.primaryChipColors(
                        backgroundColor = if (name.isBlank()) {
                            JieluColors.SurfaceHigh
                        } else {
                            MaterialTheme.colors.primary
                        },
                        contentColor = if (name.isBlank()) JieluColors.Muted else MaterialTheme.colors.onPrimary,
                        secondaryContentColor = JieluColors.Muted
                    )
                )
            }
            item { BackChip("返回", { step = 1 }) }
        }
    }
}

/* ---------- 导入 ---------- */

@Composable
private fun ImportScreen(onBack: () -> Unit, onImported: (String) -> Unit) {
    var draft by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

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
                Text(text = "粘贴课表", style = MaterialTheme.typography.title3)
                Text(
                    text = "从手机复制 JSON 后贴进来",
                    style = MaterialTheme.typography.caption2,
                    color = JieluColors.Muted
                )
            }
        }
        if (error.isNotBlank()) {
            item {
                Text(
                    text = error,
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        item {
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp)
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
                onClick = {
                    val parsed = ScheduleData.parse(draft)
                    if (parsed == null) {
                        error = "格式不对，需要含 courses 的 JSON"
                    } else if (ScheduleStore.import(draft, "text")) {
                        onImported("已导入 ${parsed.courses.size} 门课")
                    } else {
                        error = "导入失败，请检查内容"
                    }
                }
            )
        }
        item { BackChip("取消", onBack) }
    }
}

/* ---------- 小工具 ---------- */

@Composable
private fun BackChip(text: String, onBack: () -> Unit) {
    Chip(
        modifier = Modifier.fillMaxWidth(),
        label = { Text(text, maxLines = 1) },
        onClick = onBack,
        colors = ChipDefaults.secondaryChipColors()
    )
}

private fun monthDay(date: LocalDate): String = "${date.monthValue}/${date.dayOfMonth}"

private fun weekdayIndex(date: LocalDate): Int {
    val value = date.dayOfWeek.value
    return if (value == 7) 0 else value
}

private fun weekDateOf(day: Int): LocalDate {
    val today = LocalDate.now()
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    return monday.plusDays(((day + 6) % 7).toLong())
}

private fun weekdayLabel(day: Int): String = when (day) {
    0 -> "周日"
    1 -> "周一"
    2 -> "周二"
    3 -> "周三"
    4 -> "周四"
    5 -> "周五"
    else -> "周六"
}

private fun formatTime(epochMillis: Long): String {
    if (epochMillis <= 0L) return "未同步"
    val date = java.time.Instant.ofEpochMilli(epochMillis)
        .atZone(java.time.ZoneId.systemDefault())
    return "%02d:%02d".format(date.hour, date.minute)
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
