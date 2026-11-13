package com.jielu.timetable.wear

import com.jielu.timetable.wear.data.CourseState
import com.jielu.timetable.wear.data.ScheduleData
import com.jielu.timetable.wear.data.stateAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class ScheduleDataTest {

    private fun loadSample(): ScheduleData {
        val file = File("src/main/assets/sample.json")
        assertTrue("sample.json 不存在", file.exists())
        val parsed = ScheduleData.parse(file.readText())
        assertNotNull("sample.json 解析失败", parsed)
        return parsed!!
    }

    @Test
    fun `sample parses all courses`() {
        val data = loadSample()
        assertEquals(17, data.courses.size)
        assertEquals(10, data.periods.size)
        assertEquals("2026-08-31", data.term.start)
    }

    @Test
    fun `week number follows term start`() {
        val data = loadSample()
        assertEquals(1, data.weekOf(LocalDate.parse("2026-08-31")))
        assertEquals(1, data.weekOf(LocalDate.parse("2026-09-06")))
        assertEquals(2, data.weekOf(LocalDate.parse("2026-09-07")))
        assertEquals(11, data.weekOf(LocalDate.parse("2026-11-13")))
        assertEquals(0, data.weekOf(LocalDate.parse("2026-08-30")))
    }

    @Test
    fun `odd parity only shows on odd weeks`() {
        val raw = """
            {"term":{"start":"2026-08-31","weeks":18},
             "courses":[{"id":"a","name":"单周课","day":3,"start":"08:00","end":"09:40",
                         "weekStart":1,"weekEnd":16,"parity":"odd"}]}
        """.trimIndent()
        val data = ScheduleData.parse(raw)!!
        assertEquals(1, data.coursesOn(LocalDate.parse("2026-11-11")).size) // 第 11 周
        assertEquals(0, data.coursesOn(LocalDate.parse("2026-11-18")).size) // 第 12 周
    }

    @Test
    fun `active course detected at friday afternoon`() {
        val data = loadSample()
        val friday = LocalDate.parse("2026-11-13")
        val state = data.stateAt(friday, 14 * 60 + 30)
        assertTrue("14:30 应该是正在上课", state is CourseState.Active)
        val active = state as CourseState.Active
        assertEquals("信息可视化", active.course.name)
        assertEquals(70, active.leftMinutes)
        assertTrue(active.progress > 0.1f && active.progress < 0.5f)
    }

    @Test
    fun `before first class shows upcoming`() {
        val data = loadSample()
        val friday = LocalDate.parse("2026-11-13")
        val state = data.stateAt(friday, 7 * 60)
        assertTrue(state is CourseState.Upcoming)
        assertEquals("设计伦理", (state as CourseState.Upcoming).course.name)
        assertEquals(60, state.inMinutes)
    }

    @Test
    fun `after last class shows done`() {
        val data = loadSample()
        val friday = LocalDate.parse("2026-11-13")
        val state = data.stateAt(friday, 22 * 60)
        assertTrue(state is CourseState.Done)
        assertEquals("17:40", (state as CourseState.Done).lastEnd)
    }

    @Test
    fun `sunday has no courses`() {
        val data = loadSample()
        val sunday = LocalDate.parse("2026-11-15")
        assertEquals(0, data.coursesOn(sunday).size)
        assertTrue(data.stateAt(sunday, 10 * 60) is CourseState.Empty)
    }

    @Test
    fun `broken json returns null`() {
        assertNull(ScheduleData.parse("not json"))
        assertNull(ScheduleData.parse("""{"courses": []}"""))
    }

    @Test
    fun `phone payload with extra fields still parses`() {
        val raw = """
            {"term":{"start":"2026-02-23","weeks":20},
             "periods":[{"id":"p1","start":"08:00","end":"08:45"}],
             "courses":[{"id":"x","name":"测试课","day":3,"start":"9:05","end":"10:40",
                         "room":"A101","teacher":"李","kind":"必修","tone":"iris","credits":2,
                         "weekStart":2,"weekEnd":8,"parity":"even","note":"带笔","unknown":1}]}
        """.trimIndent()
        val data = ScheduleData.parse(raw)
        assertNotNull(data)
        val course = data!!.courses.single()
        assertEquals("09:05", course.start)
        assertEquals("even", course.parity)
        assertEquals(2, course.weekStart)
        assertEquals("A101", course.room)
        // 第 4 周（双周）周三应有课，第 3 周没有
        assertEquals(1, data.coursesOn(LocalDate.parse("2026-03-18")).size)
        assertEquals(0, data.coursesOn(LocalDate.parse("2026-03-11")).size)
    }
}
