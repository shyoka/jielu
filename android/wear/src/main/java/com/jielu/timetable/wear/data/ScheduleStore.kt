package com.jielu.timetable.wear.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.BufferedReader
import java.time.LocalDate

/**
 * 手表端的唯一数据源。
 *
 * 结构分两层：
 *  - **基线**：手机通过 Data Layer 推来的 JSON，或用户粘贴导入的文本；
 *  - **覆盖层**（[LocalOverrides]）：用户在表盘上自己改的学期、作息、调休、课程。
 *
 * 每次读取都把两层合成成一份 [ScheduleData]，因此手机再推一次不会覆盖手表上的改动，
 * 没有配对手机的手表也能只靠覆盖层独立使用。
 */
object ScheduleStore {
    private const val PREFS = "jielu.wear"
    private const val KEY_JSON = "schedule.json"
    private const val KEY_OVERLAY = "local.overlay"
    private const val KEY_SYNCED_AT = "synced.at"
    private const val KEY_SOURCE = "synced.source"

    private const val LOCAL_ADJUSTMENT_PREFIX = "local-adj-"
    private const val LOCAL_COURSE_PREFIX = "local-course-"

    private var appContext: Context? = null
    private var overrides = LocalOverrides()

    private val _data = MutableStateFlow<ScheduleData?>(null)
    val data: StateFlow<ScheduleData?> = _data

    private val _syncedAt = MutableStateFlow(0L)
    val syncedAt: StateFlow<Long> = _syncedAt

    private val _source = MutableStateFlow("")
    val source: StateFlow<String> = _source

    /** 本地是否已经改过东西，设置页用它显示「本机已改」。 */
    private val _localEdited = MutableStateFlow(false)
    val localEdited: StateFlow<Boolean> = _localEdited

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
            reload()
        }
    }

    fun reload() {
        val ctx = appContext ?: return
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        overrides = LocalOverrides.parse(prefs.getString(KEY_OVERLAY, null))
        _data.value = LocalOverrides.compose(prefs.getString(KEY_JSON, null), overrides)
        _syncedAt.value = prefs.getLong(KEY_SYNCED_AT, 0L)
        _source.value = prefs.getString(KEY_SOURCE, "") ?: ""
        _localEdited.value = !overrides.isEmpty
    }

    /** 返回是否解析成功；失败时不覆盖旧数据。 */
    fun import(raw: String, source: String): Boolean {
        val ctx = appContext ?: return false
        if (ScheduleData.parse(raw) == null) return false
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_JSON, raw)
            .putLong(KEY_SYNCED_AT, System.currentTimeMillis())
            .putString(KEY_SOURCE, source)
            .apply()
        _syncedAt.value = System.currentTimeMillis()
        _source.value = source
        recompose()
        return _data.value != null
    }

    fun clear() {
        val ctx = appContext ?: return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        overrides = LocalOverrides()
        _data.value = null
        _syncedAt.value = 0L
        _source.value = ""
        _localEdited.value = false
    }

    /* ---------- 调休 / 调课 ---------- */

    /**
     * 新建一条临时调整。[kind] 为 "off"（放假）或 "swap"（把 [from] 的课挪到 [to]）。
     * 同一天只留一条，旧的本地记录会被替换。
     */
    fun addAdjustment(kind: String, from: LocalDate, to: LocalDate?, note: String = ""): Boolean {
        val normalized = if (kind == "off") "off" else "swap"
        if (normalized == "swap" && (to == null || to == from)) return false
        val adjustment = Adjustment(
            id = "$LOCAL_ADJUSTMENT_PREFIX${System.currentTimeMillis()}",
            kind = normalized,
            from = from.toString(),
            to = if (normalized == "off") "" else to.toString(),
            note = note.trim()
        )
        // 基线里同一天若已有调整，先屏蔽掉，避免两条规则打架
        val conflicts = _data.value?.adjustments
            .orEmpty()
            .filter { it.from == adjustment.from && !it.id.startsWith(LOCAL_ADJUSTMENT_PREFIX) }
            .map { it.id }
        updateOverrides { current ->
            current.copy(
                adjustments = current.adjustments
                    .filterNot { it.from == adjustment.from } + adjustment,
                suppressedAdjustments = current.suppressedAdjustments + conflicts
            )
        }
        return true
    }

    /** 删除一条调整：本机新建的直接删，手机推来的只在本机屏蔽。 */
    fun removeAdjustment(id: String): Boolean {
        val exists = _data.value?.adjustments?.any { it.id == id } == true
        if (!exists) return false
        updateOverrides { current ->
            if (id.startsWith(LOCAL_ADJUSTMENT_PREFIX)) {
                current.copy(adjustments = current.adjustments.filterNot { it.id == id })
            } else {
                current.copy(suppressedAdjustments = current.suppressedAdjustments + id)
            }
        }
        return true
    }

    /* ---------- 学期 ---------- */

    fun setTermStart(date: LocalDate) {
        updateOverrides { it.copy(termStart = date.toString()) }
    }

    fun setTermWeeks(weeks: Int) {
        updateOverrides { it.copy(termWeeks = weeks.coerceIn(1, 60)) }
    }

    /* ---------- 作息 ---------- */

    fun setPeriods(periods: List<Period>) {
        if (periods.isEmpty()) return
        updateOverrides { it.copy(periods = periods) }
    }

    fun updatePeriod(index: Int, start: String, end: String) {
        val current = _data.value?.periods ?: LocalOverrides.DEFAULT_PERIODS
        if (index !in current.indices) return
        if (start >= end) return
        val next = current.toMutableList()
        next[index] = Period(start, end)
        setPeriods(next)
    }

    /* ---------- 课程 ---------- */

    /** 手表上直接加一节课，默认贯穿整个学期。 */
    fun addCourse(
        day: Int,
        name: String,
        start: String,
        end: String,
        room: String = "",
        teacher: String = ""
    ): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || start >= end) return false
        val course = Course(
            id = "$LOCAL_COURSE_PREFIX${System.currentTimeMillis()}",
            name = trimmed,
            day = day.coerceIn(0, 6),
            start = start,
            end = end,
            room = room.trim(),
            teacher = teacher.trim(),
            kind = "",
            tone = nextTone(),
            credits = 0.0,
            weekStart = 1,
            weekEnd = _data.value?.term?.weeks ?: LocalOverrides.DEFAULT_TERM_WEEKS,
            parity = "all",
            note = ""
        )
        updateOverrides { it.copy(courses = it.courses + course) }
        return true
    }

    fun deleteCourse(id: String): Boolean {
        val exists = _data.value?.courses?.any { it.id == id } == true
        if (!exists) return false
        updateOverrides { current ->
            if (id.startsWith(LOCAL_COURSE_PREFIX)) {
                current.copy(courses = current.courses.filterNot { it.id == id })
            } else {
                current.copy(deletedCourses = current.deletedCourses + id)
            }
        }
        return true
    }

    /* ---------- 通用 ---------- */

    /** 清掉全部本地改动，回到手机推送 / 文本导入的原样。 */
    fun resetLocalEdits() {
        if (overrides.isEmpty) return
        overrides = LocalOverrides()
        persistOverrides()
        recompose()
    }

    private fun updateOverrides(block: (LocalOverrides) -> LocalOverrides) {
        overrides = block(overrides)
        persistOverrides()
        recompose()
    }

    private fun persistOverrides() {
        val ctx = appContext ?: return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_OVERLAY, overrides.toJson())
            .apply()
    }

    private fun recompose() {
        val ctx = appContext ?: return
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _data.value = LocalOverrides.compose(prefs.getString(KEY_JSON, null), overrides)
        _localEdited.value = !overrides.isEmpty
    }

    /** 首次打开时给一份示例，避免对着空白表盘。 */
    fun ensureSample(context: Context) {
        init(context)
        if (_data.value != null) return
        runCatching {
            val raw = context.assets.open("sample.json").bufferedReader().use(BufferedReader::readText)
            import(raw, "sample")
        }
    }

    private val TONES = listOf("clay", "sage", "sky", "iris", "amber", "rose")

    /** 新建课程按现有课程数轮转配色，相邻两节不容易撞色。 */
    private fun nextTone(): String = TONES[(_data.value?.courses?.size ?: 0) % TONES.size]
}
