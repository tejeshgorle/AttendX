package com.tej.smartattendance

data class SubjectAttendance(
    val subject: String = "",
    val totalClasses: Int = 0,
    val present: Int = 0,
    val percentage: Double = 0.0
)
