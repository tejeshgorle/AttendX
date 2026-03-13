package com.tej.smartattendance

data class StudentAttendance(
    val userId: String = "",
    val name: String = "",
    val totalClasses: Int = 0,
    val present: Int = 0,
    val absent: Int = 0,
    val percentage: Double = 0.0
)
