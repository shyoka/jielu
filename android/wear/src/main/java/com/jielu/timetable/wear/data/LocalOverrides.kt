package com.jielu.timetable.wear.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * 手表本机的「覆盖层」。
 *
 * 手机推送过来的 JSON 是基线（base），这里存的是用户直接在表盘上做的改动：
 * 学期起始、作息时间、临时调休、自己加的课、删掉的课。
 * 读取时两者合成，所以手机再推一次也不会冲掉手表上的修改；
 * 反过来，完全没配对手表的手也能只用本地这一层把课表建起来。
 */
data class LocalOverrides(
    val termStart: String? = null,
    val termWeeks: Int? = null,
    val periods: List<Period>? = null,
    val adjustments: List<Adjustment> = emptyList(),
    val suppressedAdjustments: Set<String> = emptySet(),
    val courses: List<Course> = emptyList(),
    val deletedCourses: Set<String> = emptySet()
) {
    val isEmpty: Boolean
        get() = termStart == null &&
            termWeeks == null &&
            periods == null &&
            adjustments.isEmpty() &&
            suppressedAdjustments.isEmpty() &&
            courses.isEmpty() &&
            deletedCourses.isEmpty()

    fun toJson(): String = JSONObject().apply {
        termStart?.let { put("termStart", it) }
        termWeeks?.let { put("termWeeks", it) }
        periods?.let { put("periods", periodsToJson(it)) }
        put("adjustments", adjustmentsToJson(adjustments))
        put("suppressedAdjustments", JSONArray(suppressedAdjustments.toList()))
        put("courses", coursesToJson(courses))
        put("deletedCourses", JSONArray(deletedCourses.toList()))
    }.toString()

    companion object {
        const val DEFAULT_TERM_START = "2026-08-31"
        const val DEFAULT_TERM_WEEKS = 20

        /** 手表端从零建课时使用的一套标准作息，可在「作息时间」里逐节改。 */
        val DEFAULT_PERIODS: List<Period> = listOf(
            Period("07:55", "08:40"),
            Period("08:50", "09:35"),
            Period("10:00", "10:45"),
            Period("10:55", "11:40"),
            Period("14:00", "14:45"),
            Period("14:55", "15:40"),
            Period("16:00", "16:45"),
            Period("18:05", "19:00"),
            Period("19:10", "19:55"),
            Period("20:05", "20:50")
        )

        fun parse(raw: String?): LocalOverrides {
            if (raw.isNullOrBlank()) return LocalOverrides()
            val root = runCatching { JSONObject(raw) }.getOrNull() ?: return LocalOverrides()

            val periods = root.optJSONArray("periods")?.let { array ->
                (0 until array.length()).mapNotNull { index ->
                    val item = array.optJSONObject(index) ?: return@mapNotNull null
                    val start = item.optString("start")
                    val end = item.optString("end")
                    if (start.isBlank() || end.isBlank()) null else Period(start, end)
                }
            }?.takeIf { it.isNotEmpty() }

            val adjustments = root.optJSONArray("adjustments")?.let { array ->
                (0 until array.length()).mapNotNull { index ->
                    array.optJSONObject(index)?.let(::adjustmentOf)
                }
            }.orEmpty()

            val courses = root.optJSONArray("courses")?.let { array ->
                (0 until array.length()).mapNotNull { index ->
                    array.optJSONObject(index)?.let(::courseOf)
                }
            }.orEmpty()

            return LocalOverrides(
                termStart = root.optString("termStart").takeIf { it.length >= 10 },
                termWeeks = root.optInt("termWeeks", 0).takeIf { it in 1..60 },
                periods = periods,
                adjustments = adjustments,
                suppressedAdjustments = root.optJSONArray("suppressedAdjustments").toStringSet(),
                courses = courses,
                deletedCourses = root.optJSONArray("deletedCourses").toStringSet()
            )
        }

        /**
         * 把基线和覆盖层合成（保留旧入口，签名不变）。
         * 解析一次基线后交给 [merge] 做对象级合并。
         */
        fun compose(base: String?, overrides: LocalOverrides): ScheduleData? =
            merge(base?.takeIf { it.isNotBlank() }?.let { ScheduleData.parse(it) }, overrides)

        /**
         * 基线 + 覆盖层 = 当前生效的课表。
         *
         * 这里直接在对象上合并，不再「拼 JSON 字符串 → 重新解析」——
         * 首次启动因此只解析一遍，用户在表上改一次课表也只是几次列表拷贝，
         * 不会每次都重新扫一遍全部课程。
         */
        fun merge(base: ScheduleData?, overrides: LocalOverrides): ScheduleData? {
            if (base == null && overrides.courses.isEmpty() && overrides.periods == null) {
                return null
            }
            val term = Term(
                start = overrides.termStart ?: base?.term?.start ?: DEFAULT_TERM_START,
                weeks = overrides.termWeeks ?: base?.term?.weeks ?: DEFAULT_TERM_WEEKS
            )
            val periods = overrides.periods?.takeIf { it.isNotEmpty() }
                ?: base?.periods?.takeIf { it.isNotEmpty() }
                ?: DEFAULT_PERIODS

            val kept = base?.courses.orEmpty().filterNot { it.id in overrides.deletedCourses }
            val courses = if (overrides.courses.isEmpty()) kept else kept + overrides.courses

            // 本地在同一个「源日期」上新建了调整，基线那条让位
            val localFroms = overrides.adjustments.map { it.from }.toSet()
            val baselineAdjustments = base?.adjustments.orEmpty().filterNot {
                it.id in overrides.suppressedAdjustments || it.from in localFroms
            }
            val adjustments = if (overrides.adjustments.isEmpty()) {
                baselineAdjustments
            } else {
                baselineAdjustments + overrides.adjustments
            }

            return ScheduleData(term, periods, courses, adjustments)
        }

        private fun adjustmentOf(item: JSONObject): Adjustment? {
            val kind = if (item.optString("kind") == "off") "off" else "swap"
            val from = runCatching { java.time.LocalDate.parse(item.optString("from")) }.getOrNull()
                ?: return null
            val to = runCatching { java.time.LocalDate.parse(item.optString("to")) }.getOrNull()
            if (kind == "swap" && (to == null || to == from)) return null
            return Adjustment(
                id = item.optString("id").ifEmpty { "adj-$from" },
                kind = kind,
                from = from.toString(),
                to = if (kind == "swap") to.toString() else "",
                note = item.optString("note")
            )
        }

        private fun courseOf(item: JSONObject): Course? {
            val name = item.optString("name").trim()
            if (name.isEmpty()) return null
            return Course(
                id = item.optString("id").ifEmpty { "local-${name.hashCode()}" },
                name = name,
                day = item.optInt("day", 1).coerceIn(0, 6),
                start = item.optString("start", "08:00"),
                end = item.optString("end", "09:40"),
                room = item.optString("room"),
                teacher = item.optString("teacher"),
                kind = item.optString("kind"),
                tone = item.optString("tone", "clay"),
                credits = item.optDouble("credits", 0.0),
                weekStart = item.optInt("weekStart", 1).coerceAtLeast(1),
                weekEnd = item.optInt("weekEnd", DEFAULT_TERM_WEEKS).coerceAtLeast(1),
                parity = item.optString("parity", "all").takeIf { it in setOf("all", "odd", "even") } ?: "all",
                note = item.optString("note")
            )
        }

        private fun JSONArray?.toStringSet(): Set<String> {
            if (this == null) return emptySet()
            val out = LinkedHashSet<String>()
            for (index in 0 until length()) {
                val value = optString(index, "")
                if (value.isNotEmpty()) out += value
            }
            return out
        }
    }
}

fun Period.toJson(): JSONObject = JSONObject().apply {
    put("start", start)
    put("end", end)
}

fun Course.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("day", day)
    put("start", start)
    put("end", end)
    put("room", room)
    put("teacher", teacher)
    put("kind", kind)
    put("tone", tone)
    put("credits", credits)
    put("weekStart", weekStart)
    put("weekEnd", weekEnd)
    put("parity", parity)
    put("note", note)
}

fun Adjustment.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("kind", kind)
    put("from", from)
    put("to", to)
    put("note", note)
}

// 三个 List 扩展在 JVM 上会因类型擦除撞签名，这里用普通函数分开命名。
private fun periodsToJson(items: List<Period>): JSONArray = JSONArray().apply {
    items.forEach { put(it.toJson()) }
}

private fun coursesToJson(items: List<Course>): JSONArray = JSONArray().apply {
    items.forEach { put(it.toJson()) }
}

private fun adjustmentsToJson(items: List<Adjustment>): JSONArray = JSONArray().apply {
    items.forEach { put(it.toJson()) }
}
