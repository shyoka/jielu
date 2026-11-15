package com.jielu.timetable.wear.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
 *
 * 性能上也做了两件事，避免「刚点开先卡一下」：
 *  1. 首次加载（读 SharedPreferences、读 assets、解析 JSON）全部在 IO 线程，
 *     界面先画出来，数据到了再刷新；
 *  2. 基线解析结果缓存在内存里，合成走 [LocalOverrides.merge] 的对象级合并，
 *     不再每次「拼字符串 → 重新解析」。
 */
object ScheduleStore {
    private const val PREFS = "jielu.wear"
    private const val KEY_JSON = "schedule.json"
    private const val KEY_OVERLAY = "local.overlay"
    private const val KEY_SYNCED_AT = "synced.at"
    private const val KEY_SOURCE = "synced.source"

    private const val LOCAL_ADJUSTMENT_PREFIX = "local-adj-"
    private const val LOCAL_COURSE_PREFIX = "local-course-"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var appContext: Context? = null

    /** UI 线程改覆盖层、IO 线程读盘，用 volatile 保证可见性。 */
    @Volatile
    private var overrides = LocalOverrides()

    /** 读盘与「手机推来新数据」都可能同时发生，串行化，避免旧数据把新数据盖回去。 */
    private val ioLock = Any()

    /** 已解析的基线，避免每次合成/读取都重新解析整份 JSON。 */
    @Volatile
    private var base: ScheduleData? = null

    private val _data = MutableStateFlow<ScheduleData?>(null)
    val data: StateFlow<ScheduleData?> = _data

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading

    private val _syncedAt = MutableStateFlow(0L)
    val syncedAt: StateFlow<Long> = _syncedAt

    private val _source = MutableStateFlow("")
    val source: StateFlow<String> = _source

    /** 本地是否已经改过东西，设置页用它显示「本机已改」。 */
    private val _localEdited = MutableStateFlow(false)
    val localEdited: StateFlow<Boolean> = _localEdited

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        // 解析放到 IO：界面可以先出来，数据到了再刷新，不必等 JSON 解析完
        scope.launch { loadBlocking() }
    }

    /** 重新从磁盘读一遍（一般用不到，保留给调试与外部刷新）。 */
    fun reload() {
        if (appContext == null) return
        scope.launch { loadBlocking() }
    }

    private fun loadBlocking() {
        val ctx = appContext ?: return
        synchronized(ioLock) {
            val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            overrides = LocalOverrides.parse(prefs.getString(KEY_OVERLAY, null))
            base = prefs.getString(KEY_JSON, null)?.let { ScheduleData.parse(it) }
            _syncedAt.value = prefs.getLong(KEY_SYNCED_AT, 0L)
            _source.value = prefs.getString(KEY_SOURCE, "") ?: ""
            publish()
            // 首次打开给一份示例，避免对着空白表盘
            if (base == null && overrides.courses.isEmpty()) seedSample(ctx)
            _loading.value = false
        }
    }

    private fun seedSample(ctx: Context) {
        val raw = runCatching {
            ctx.assets.open("sample.json").bufferedReader().use(BufferedReader::readText)
        }.getOrNull() ?: return
        val parsed = ScheduleData.parse(raw) ?: return
        base = parsed
        persistBaseline(ctx, raw, "sample")
        publish()
    }

    /**
     * 重新载入内置示例（设置页「重新载入示例」用）。
     * 同样在后台做，界面先回到设置页，数据到了再刷新。
     */
    fun ensureSample(context: Context) {
        val firstLoad = appContext == null
        init(context)
        if (firstLoad) return
        _loading.value = true
        scope.launch { loadBlocking() }
    }

    /** 返回是否解析成功；失败时不覆盖旧数据。 */
    fun import(raw: String, source: String): Boolean {
        val ctx = appContext ?: return false
        val parsed = ScheduleData.parse(raw) ?: return false
        applyBaseline(ctx, raw, parsed, source)
        return true
    }

    /**
     * 导入课表，解析放到 IO 线程。返回课程数，失败返回 -1。
     * 设置页粘贴导入走这个入口，避免一大段 JSON 把界面卡住。
     */
    suspend fun importAsync(raw: String, source: String): Int = withContext(Dispatchers.IO) {
        val ctx = appContext ?: return@withContext -1
        val parsed = ScheduleData.parse(raw) ?: return@withContext -1
        applyBaseline(ctx, raw, parsed, source)
        parsed.courses.size
    }

    private fun applyBaseline(ctx: Context, raw: String, parsed: ScheduleData, source: String) {
        synchronized(ioLock) {
            base = parsed
            persistBaseline(ctx, raw, source)
            publish()
            _loading.value = false
        }
    }

    private fun persistBaseline(ctx: Context, raw: String, source: String) {
        val now = System.currentTimeMillis()
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_JSON, raw)
            .putLong(KEY_SYNCED_AT, now)
            .putString(KEY_SOURCE, source)
            .apply()
        _syncedAt.value = now
        _source.value = source
    }

    fun clear() {
        val ctx = appContext ?: return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        overrides = LocalOverrides()
        base = null
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
        // 同一天只留一条本地规则；基线里同一天的规则由合成阶段自动让位
        updateOverrides { current ->
            current.copy(
                adjustments = current.adjustments
                    .filterNot { it.from == adjustment.from } + adjustment
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
        publish()
    }

    private fun updateOverrides(block: (LocalOverrides) -> LocalOverrides) {
        overrides = block(overrides)
        persistOverrides()
        publish()
    }

    private fun persistOverrides() {
        val ctx = appContext ?: return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_OVERLAY, overrides.toJson())
            .apply()
    }

    /** 合成一次并推给界面。对象级合并只是几次列表拷贝，主线程也够快。 */
    private fun publish() {
        _data.value = LocalOverrides.merge(base, overrides)
        _localEdited.value = !overrides.isEmpty
    }

    private val TONES = listOf("clay", "sage", "sky", "iris", "amber", "rose")

    /** 新建课程按现有课程数轮转配色，相邻两节不容易撞色。 */
    private fun nextTone(): String = TONES[(_data.value?.courses?.size ?: 0) % TONES.size]
}
