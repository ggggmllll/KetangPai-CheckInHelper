package com.ketangpai.checkin

import java.time.LocalDate

internal data class CourseSemester(val year: String, val term: String, val termLabel: String = "") {
    val label: String get() = listOf(year, termLabel.ifBlank {
        when (term) { "1" -> "第一学期"; "2" -> "第二学期"; else -> "学期 $term" }
    }).filter(String::isNotBlank).joinToString(" · ")
}

/** Prefer this academic term; otherwise follow the server's newest-first semester order. */
internal fun selectCourseSemester(semesters: List<CourseSemester>, today: LocalDate = LocalDate.now()): CourseSemester? {
    val startYear = if (today.monthValue >= 8) today.year else today.year - 1
    val year = "$startYear-${startYear + 1}"
    val term = if (today.monthValue in 2..7) "2" else "1"
    return semesters.firstOrNull { it.year == year && it.term == term } ?: semesters.firstOrNull()
}

data class CourseSync(val courses: List<LocalCourse>, val semesterLabel: String)
