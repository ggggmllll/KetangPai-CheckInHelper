package com.ketangpai.checkin

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CourseSemesterTest {
    private val autumn = CourseSemester("2026-2027", "1", "第一学期")
    private val spring = CourseSemester("2025-2026", "2", "第二学期")
    private val previousAutumn = CourseSemester("2025-2026", "1")

    @Test fun currentTermWinsEvenWhenNewerOrOlderCoursesExist() {
        assertEquals(autumn, selectCourseSemester(listOf(CourseSemester("2027-2028", "1"), spring, autumn), LocalDate.of(2026, 10, 15)))
    }

    @Test fun januaryStillBelongsToPreviousYearsFirstTerm() {
        assertEquals(previousAutumn, selectCourseSemester(listOf(autumn, spring, previousAutumn), LocalDate.of(2026, 1, 31)))
    }

    @Test fun februaryStartsSecondTermAndAugustStartsNewAcademicYear() {
        val semesters = listOf(autumn, spring, previousAutumn)
        assertEquals(spring, selectCourseSemester(semesters, LocalDate.of(2026, 2, 1)))
        assertEquals(spring, selectCourseSemester(semesters, LocalDate.of(2026, 7, 31)))
        assertEquals(autumn, selectCourseSemester(semesters, LocalDate.of(2026, 8, 1)))
    }

    @Test fun unavailableCurrentTermUsesServersLatestSemester() {
        assertEquals(spring, selectCourseSemester(listOf(spring, previousAutumn), LocalDate.of(2026, 10, 15)))
        assertNull(selectCourseSemester(emptyList(), LocalDate.of(2026, 10, 15)))
    }

    @Test fun labelsUseServersTermTextOrReadableFallback() {
        assertEquals("2026-2027 · 第一学期", autumn.label)
        assertEquals("2025-2026 · 第一学期", previousAutumn.label)
        assertEquals("2025-2026 · 暑期学期", CourseSemester("2025-2026", "3", "暑期学期").label)
    }
}
