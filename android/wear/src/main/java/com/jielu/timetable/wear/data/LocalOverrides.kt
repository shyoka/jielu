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
         * 把基线和覆盖层合成为一份可解析的课表 JSON。
         * 基线为空但本地已有课程时同样成立，这样没手机也能用。
         */
        fun compose(base: String?, overrides: LocalOverrides): ScheduleData? {
            if (base.isNullOrBlank() && overrides.courses.isEmpty() && overrides.periods == null) {
                return null
            }
            val root = base?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()

            val term = root.optJSONObject("term") ?: JSONObject()
            if (term.optString("start").length < 10) term.put("start", DEFAULT_TERM_START)
            if (term.optInt("weeks", 0) <= 0) term.put("weeks", DEFAULT_TERM_WEEKS)
            overrides.termStart?.let { term.put("start", it) }
            overrides.termWeeks?.let { term.put("weeks", it) }
            root.put("term", term)

            val periods = overrides.periods
                ?: root.optJSONArray("periods")?.let { array ->
                    (0 until array.length()).mapNotNull { index ->
                        array.optJSONObject(index)?.let { item ->
                            val start = item.optString("start")
                            val end = item.optString("end")
                            if (start.isBlank() || end.isBlank()) null else Period(start, end)
                        }
                    }
                }?.takeIf { it.isNotEmpty() }
                ?: DEFAULT_PERIODS
            root.put("periods", periodsToJson(periods))

            val courses = JSONArray()
            root.optJSONArray("courses")?.let { array ->
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    if (item.optString("id") in overrides.deletedCourses) continue
                    courses.put(item)
                }
            }
            overrides.courses.forEach { courses.put(it.toJson()) }
            root.put("courses", courses)

            val adjustments = JSONArray()
            root.optJSONArray("adjustments")?.let { array ->
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    if (item.optString("id") in overrides.suppressedAdjustments) continue
                    adjustments.put(item)
                }
            }
            overrides.adjustments.forEach { adjustments.put(it.toJson()) }
            root.put("adjustments", adjustments)

            return ScheduleData.parse(root.toString())
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
