package com.jielu.timetable.wear

import com.jielu.timetable.wear.data.Adjustment
import com.jielu.timetable.wear.data.Course
import com.jielu.timetable.wear.data.LocalOverrides
import com.jielu.timetable.wear.data.Period
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 覆盖层是「手表不依赖手机」的地基，这里把合成的每条规则钉住。 */
class LocalOverridesTest {

    private val base = """
        {"term":{"start":"2026-08-31","weeks":20},
         "periods":[{"start":"08:00","end":"08:45"},{"start":"08:55","end":"09:40"}],
         "courses":[{"id":"c1","name":"高数","day":1,"start":"08:00","end":"09:40",
                     "weekStart":1,"weekEnd":20,"parity":"all"},
                    {"id":"c2","name":"英语","day":3,"start":"08:00","end":"09:40",
                     "weekStart":1,"weekEnd":20,"parity":"all"}],
         "adjustments":[{"id":"adj1","kind":"off","from":"2026-09-07"}]}
    """.trimIndent()

    private fun course(
        id: String,
        name: String,
        day: Int,
        start: String = "08:00",
        end: String = "09:40"
    ) = Course(
        id = id, name = name, day = day, start = start, end = end,
        room = "A101", teacher = "", kind = "", tone = "clay", credits = 0.0,
        weekStart = 1, weekEnd = 20, parity = "all", note = ""
    )

    @Test
    fun `empty overrides keep base intact`() {
        val data = LocalOverrides.compose(base, LocalOverrides())
        assertNotNull(data)
        assertEquals(2, data!!.courses.size)
        assertEquals(1, data.adjustments.size)
        assertEquals(2, data.periods.size)
    }

    @Test
    fun `empty base and empty overrides stay empty`() {
        assertNull(LocalOverrides.compose(null, LocalOverrides()))
        assertNull(LocalOverrides.compose("", LocalOverrides()))
    }

    @Test
    fun `local course works without any phone data`() {
        val overrides = LocalOverrides(courses = listOf(course("local-course-1", "手建课", 3)))
        val data = LocalOverrides.compose(null, overrides)
        assertNotNull("没有手机推送也要能建出课表", data)
        assertEquals(1, data!!.courses.size)
        assertEquals("手建课", data.courses.single().name)
        assertEquals(LocalOverrides.DEFAULT_TERM_START, data.term.start)
        assertEquals(LocalOverrides.DEFAULT_TERM_WEEKS, data.term.weeks)
        assertEquals(LocalOverrides.DEFAULT_PERIODS.size, data.periods.size)
        // 2026-09-02 是周三，能看到这节手建的课
        assertEquals(1, data.coursesOn(LocalDate.parse("2026-09-02")).size)
    }

    @Test
    fun `deleted course disappears but others stay`() {
        val data = LocalOverrides.compose(base, LocalOverrides(deletedCourses = setOf("c1")))
        assertEquals(1, data!!.courses.size)
        assertEquals("英语", data.courses.single().name)
        assertEquals(0, data.coursesOn(LocalDate.parse("2026-09-07")).size)
    }

    @Test
    fun `local adjustment replaces base adjustment on the same day`() {
        val overrides = LocalOverrides(
            adjustments = listOf(
                Adjustment("local-adj-9", "swap", "2026-09-07", "2026-09-12", "")
            ),
            suppressedAdjustments = setOf("adj1")
        )
        val data = LocalOverrides.compose(base, overrides)!!
        assertEquals(1, data.adjustments.size)
        // 周一停课、周六按周一课表上课
        assertEquals(0, data.coursesOn(LocalDate.parse("2026-09-07")).size)
        val saturday = data.coursesOn(LocalDate.parse("2026-09-12"))
        assertEquals(1, saturday.size)
        assertEquals("高数", saturday.single().name)
    }

    @Test
    fun `suppressed base adjustment is hidden`() {
        val data = LocalOverrides.compose(base, LocalOverrides(suppressedAdjustments = setOf("adj1")))!!
        assertEquals(0, data.adjustments.size)
        assertEquals(1, data.coursesOn(LocalDate.parse("2026-09-07")).size)
    }

    @Test
    fun `term override moves the week grid`() {
        val data = LocalOverrides.compose(base, LocalOverrides(termStart = "2026-09-07", termWeeks = 18))!!
        assertEquals("2026-09-07", data.term.start)
        assertEquals(18, data.term.weeks)
        assertEquals(1, data.weekOf(LocalDate.parse("2026-09-07")))
        assertEquals(2, data.weekOf(LocalDate.parse("2026-09-14")))
    }

    @Test
    fun `period override replaces the timetable`() {
        val periods = listOf(Period("07:55", "08:40"), Period("08:50", "09:35"), Period("10:00", "10:45"))
        val data = LocalOverrides.compose(base, LocalOverrides(periods = periods))!!
        assertEquals(3, data.periods.size)
        assertEquals("07:55", data.periods.first().start)
    }

    @Test
    fun `overrides survive a json round trip`() {
        val original = LocalOverrides(
            termStart = "2026-09-07",
            termWeeks = 18,
            periods = listOf(Period("07:55", "08:40")),
            adjustments = listOf(Adjustment("local-adj-1", "off", "2026-10-01", "", "国庆")),
            suppressedAdjustments = setOf("adj1"),
            courses = listOf(course("local-course-1", "手建课", 2)),
            deletedCourses = setOf("c2")
        )
        val restored = LocalOverrides.parse(original.toJson())
        assertEquals(original.termStart, restored.termStart)
        assertEquals(original.termWeeks, restored.termWeeks)
        assertEquals(1, restored.periods?.size)
        assertEquals("07:55", restored.periods?.first()?.start)
        assertEquals(1, restored.adjustments.size)
        assertEquals("国庆", restored.adjustments.single().note)
        assertTrue(restored.suppressedAdjustments.contains("adj1"))
        assertEquals(1, restored.courses.size)
        assertEquals("手建课", restored.courses.single().name)
        assertTrue(restored.deletedCourses.contains("c2"))
    }

    @Test
    fun `broken overlay json falls back to empty`() {
        assertTrue(LocalOverrides.parse("not json").isEmpty)
        assertTrue(LocalOverrides.parse(null).isEmpty)
        assertTrue(LocalOverrides.parse("{}").isEmpty)
    }

    @Test
    fun `deleting every course leaves a valid empty schedule`() {
        val single = """
            {"term":{"start":"2026-08-31","weeks":20},
             "courses":[{"id":"c1","name":"唯一一门","day":1,"start":"08:00","end":"08:45"}]}
        """.trimIndent()
        val data = LocalOverrides.compose(single, LocalOverrides(deletedCourses = setOf("c1")))
        // null 表示「从没有过课表」，删光只是课程为空；学期和作息还得留着
        assertNotNull(data)
        assertTrue(data!!.courses.isEmpty())
        assertEquals("2026-08-31", data.term.start)
        assertTrue(data.periods.isNotEmpty())
    }

    @Test
    fun `sample json still parses with overrides`() {
        val sample = java.io.File("src/main/assets/sample.json").readText()
        val data = LocalOverrides.compose(sample, LocalOverrides())
        assertNotNull(data)
        assertEquals(17, data!!.courses.size)
        assertEquals("2026-08-31", data.term.start)
    }

    /* ---------- 对象级合并（merge）：启动路径不再拼字符串重解析 ---------- */

    @Test
    fun `merge keeps both baseline and local courses`() {
        val parsed = LocalOverrides.compose(base, LocalOverrides())!!
        val data = LocalOverrides.merge(parsed, LocalOverrides(courses = listOf(course("local-course-1", "手建课", 4))))
        assertNotNull(data)
        assertEquals(3, data!!.courses.size)
        assertEquals("手建课", data.courses.last().name)
        // 学期、作息、基线调休原样带过来
        assertEquals("2026-08-31", data.term.start)
        assertEquals(2, data.periods.size)
        assertEquals(1, data.adjustments.size)
    }

    @Test
    fun `merge lets local adjustment win on the same day`() {
        val parsed = LocalOverrides.compose(base, LocalOverrides())!!
        val local = Adjustment(
            id = "local-adj-1", kind = "off", from = "2026-09-07", to = "", note = ""
        )
        val data = LocalOverrides.merge(parsed, LocalOverrides(adjustments = listOf(local)))
        assertNotNull(data)
        assertEquals(1, data!!.adjustments.size)
        assertEquals("local-adj-1", data.adjustments.first().id)
        assertTrue(data.coursesOn(LocalDate.parse("2026-09-07")).isEmpty())
    }

    @Test
    fun `merge applies term and periods overrides over the baseline`() {
        val parsed = LocalOverrides.compose(base, LocalOverrides())!!
        val data = LocalOverrides.merge(
            parsed,
            LocalOverrides(
                termStart = "2026-09-07",
                termWeeks = 16,
                periods = listOf(Period("09:00", "09:45"))
            )
        )
        assertNotNull(data)
        assertEquals("2026-09-07", data!!.term.start)
        assertEquals(16, data.term.weeks)
        assertEquals(1, data.periods.size)
        assertEquals("09:00", data.periods.first().start)
    }

    @Test
    fun `merge without baseline only uses local courses`() {
        val data = LocalOverrides.merge(null, LocalOverrides(courses = listOf(course("local-course-1", "唯一一门", 2))))
        assertNotNull(data)
        assertEquals(1, data!!.courses.size)
        assertEquals(LocalOverrides.DEFAULT_TERM_START, data.term.start)
        assertEquals(LocalOverrides.DEFAULT_PERIODS.size, data.periods.size)
    }

    @Test
    fun `merge returns null when there is nothing at all`() {
        assertNull(LocalOverrides.merge(null, LocalOverrides()))
    }

}
