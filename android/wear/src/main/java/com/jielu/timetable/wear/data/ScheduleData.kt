package com.jielu.timetable.wear.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** 一节课。字段与手机端 jielu.data.v2 保持一致。 */
data class Course(
    val id: String,
    val name: String,
    val day: Int,          // 0 = 周日
    val start: String,
    val end: String,
    val room: String,
    val teacher: String,
    val kind: String,
    val tone: String,
    val credits: Double,
    val weekStart: Int,
    val weekEnd: Int,
    val parity: String,    // all | odd | even
    val note: String
)

data class Period(val start: String, val end: String)

data class Term(val start: String, val weeks: Int)

/** 一条临时调整：把某天的课挪到另一天（swap），或让某天休息（off）。 */
data class Adjustment(
    val id: String,
    val kind: String,      // swap | off
    val from: String,      // yyyy-MM-dd
    val to: String,
    val note: String
) {
    val fromDate: LocalDate? get() = runCatching { LocalDate.parse(from) }.getOrNull()
    val toDate: LocalDate? get() = runCatching { LocalDate.parse(to) }.getOrNull()
}

/** 某一天命中的调整。counterpart 是「另一端」的日期。 */
data class DayAdjustment(
    val type: String,      // swap-in | swap-out | off
    val adjustment: Adjustment,
    val counterpart: LocalDate?
)

data class ScheduleData(
    val term: Term,
    val periods: List<Period>,
    val courses: List<Course>,
    val adjustments: List<Adjustment> = emptyList()
) {
    val termStart: LocalDate
        get() = runCatching { LocalDate.parse(term.start) }.getOrDefault(LocalDate.now())

    /** 今天属于第几教学周；开学前返回 0。 */
    fun weekOf(date: LocalDate): Int {
        val days = ChronoUnit.DAYS.between(termStart, date).toLong()
        if (days < 0) return 0
        return (days / 7).toInt() + 1
    }

    fun isActive(course: Course, date: LocalDate): Boolean {
        val week = weekOf(date)
        if (week < course.weekStart || week > course.weekEnd) return false
        return when (course.parity) {
            "odd" -> week % 2 == 1
            "even" -> week % 2 == 0
            else -> true
        }
    }

    fun adjustmentOn(date: LocalDate): DayAdjustment? {
        adjustments.firstOrNull { it.kind == "swap" && it.toDate == date }?.let { adjustment ->
            val source = adjustment.fromDate ?: return null
            return DayAdjustment("swap-in", adjustment, source)
        }
        adjustments.firstOrNull { it.fromDate == date }?.let { adjustment ->
            return if (adjustment.kind == "off") {
                DayAdjustment("off", adjustment, null)
            } else {
                DayAdjustment("swap-out", adjustment, adjustment.toDate)
            }
        }
        return null
    }

    fun coursesOn(date: LocalDate): List<Course> {
        val adjustment = adjustmentOn(date)
        if (adjustment != null && adjustment.type != "swap-in") return emptyList()
        val effective = adjustment?.counterpart ?: date
        val dow = effective.dayOfWeek.value % 7
        return courses
            .filter { it.day == dow && isActive(it, effective) }
            .sortedBy { minutesOf(it.start) }
    }

    fun nextCourseAfter(minutes: Int, date: LocalDate): Course? =
        coursesOn(date).firstOrNull { minutesOf(it.start) > minutes }

    fun currentCourse(minutes: Int, date: LocalDate): Course? =
        coursesOn(date).firstOrNull { minutes >= minutesOf(it.start) && minutes < minutesOf(it.end) }

    fun findCourse(id: String): Course? = courses.firstOrNull { it.id == id }

    companion object {
        fun minutesOf(hhmm: String): Int {
            val parts = hhmm.split(":")
            if (parts.size != 2) return 0
            return (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
        }

        fun nowMinutes(): Int {
            val now = LocalTime.now()
            return now.hour * 60 + now.minute
        }

        fun parse(raw: String): ScheduleData? {
            return try {
                val root = JSONObject(raw)
                val termJson = root.optJSONObject("term")
                val term = Term(
                    start = termJson?.optString("start")?.takeIf { it.length >= 10 } ?: "2026-08-31",
                    weeks = termJson?.optInt("weeks", 18)?.coerceIn(1, 40) ?: 18
                )

                val periods = mutableListOf<Period>()
                val periodArray = root.optJSONArray("periods") ?: JSONArray()
                for (i in 0 until periodArray.length()) {
                    val item = periodArray.optJSONObject(i) ?: continue
                    periods += Period(
                        start = item.optString("start", "08:00"),
                        end = item.optString("end", "08:45")
                    )
                }

                val courses = mutableListOf<Course>()
                val courseArray = root.optJSONArray("courses") ?: JSONArray()
                for (i in 0 until courseArray.length()) {
                    val item = courseArray.optJSONObject(i) ?: continue
                    val name = item.optString("name").trim()
                    if (name.isEmpty()) continue
                    courses += Course(
                        id = item.optString("id").ifEmpty { "c$i" },
                        name = name,
                        day = item.optInt("day", 1).coerceIn(0, 6),
                        start = normalizeTime(item.optString("start"), "08:00"),
                        end = normalizeTime(item.optString("end"), "09:40"),
                        room = item.optString("room"),
                        teacher = item.optString("teacher"),
                        kind = item.optString("kind"),
                        tone = item.optString("tone", "clay"),
                        credits = item.optDouble("credits", 0.0),
                        weekStart = item.optInt("weekStart", 1).coerceAtLeast(1),
                        weekEnd = item.optInt("weekEnd", term.weeks).coerceAtLeast(1),
                        parity = item.optString("parity", "all")
                            .takeIf { it in setOf("all", "odd", "even") } ?: "all",
                        note = item.optString("note")
                    )
                }

                val adjustments = mutableListOf<Adjustment>()
                val adjustmentArray = root.optJSONArray("adjustments") ?: JSONArray()
                for (i in 0 until adjustmentArray.length()) {
                    val item = adjustmentArray.optJSONObject(i) ?: continue
                    val kind = if (item.optString("kind") == "off") "off" else "swap"
                    val fromDate = runCatching { LocalDate.parse(item.optString("from")) }.getOrNull()
                        ?: continue
                    val toDate = runCatching { LocalDate.parse(item.optString("to")) }.getOrNull()
                    if (kind == "swap" && (toDate == null || toDate == fromDate)) continue
                    adjustments += Adjustment(
                        id = item.optString("id").ifEmpty { "adj$i" },
                        kind = kind,
                        from = fromDate.toString(),
                        to = if (kind == "swap") toDate.toString() else "",
                        note = item.optString("note")
                    )
                }

                if (courses.isEmpty()) null else ScheduleData(term, periods, courses, adjustments)
            } catch (error: Exception) {
                null
            }
        }

        private fun normalizeTime(value: String, fallback: String): String {
            val m = Regex("^(\\d{1,2}):(\\d{2})$").find(value.trim()) ?: return fallback
            val hour = m.groupValues[1].toInt().coerceIn(0, 23)
            val minute = m.groupValues[2].toInt().coerceIn(0, 59)
            return "%02d:%02d".format(hour, minute)
        }
    }
}

/** 手表端展示用的课程状态。 */
sealed interface CourseState {
    data class Active(val course: Course, val leftMinutes: Int, val totalMinutes: Int, val progress: Float) : CourseState
    data class Upcoming(val course: Course, val inMinutes: Int) : CourseState
    data class Done(val lastEnd: String) : CourseState
    data object Empty : CourseState
}

fun ScheduleData.stateAt(date: LocalDate, minutes: Int): CourseState {
    val today = coursesOn(date)
    val current = today.firstOrNull {
        minutes >= ScheduleData.minutesOf(it.start) && minutes < ScheduleData.minutesOf(it.end)
    }
    if (current != null) {
        val start = ScheduleData.minutesOf(current.start)
        val end = ScheduleData.minutesOf(current.end)
        val total = (end - start).coerceAtLeast(1)
        val passed = (minutes - start).coerceIn(0, total)
        return CourseState.Active(
            course = current,
            leftMinutes = end - minutes,
            totalMinutes = total,
            progress = passed.toFloat() / total
        )
    }
    val next = today.firstOrNull { minutes < ScheduleData.minutesOf(it.start) }
    if (next != null) {
        return CourseState.Upcoming(next, ScheduleData.minutesOf(next.start) - minutes)
    }
    if (today.isNotEmpty()) {
        return CourseState.Done(today.maxOf { it.end })
    }
    return CourseState.Empty
}

fun dateLabel(date: LocalDate): String {
    val weekdays = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    return "${date.monthValue} 月 ${date.dayOfMonth} 日 · ${weekdays[date.dayOfWeek.value - 1]}"
}

fun weekdayCN(date: LocalDate): String = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[date.dayOfWeek.value - 1]

val CLOCK_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
