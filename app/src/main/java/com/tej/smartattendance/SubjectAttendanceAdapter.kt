package com.tej.smartattendance

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SubjectAttendanceAdapter(
    private val subjectList: List<SubjectAttendance>
) : RecyclerView.Adapter<SubjectAttendanceAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val subjectName: TextView = view.findViewById(R.id.subjectName)
        val progressBar: ProgressBar = view.findViewById(R.id.attendanceProgress)
        val percentageText: TextView = view.findViewById(R.id.percentageText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_subject_attendance, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = subjectList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val subject = subjectList[position]
        holder.subjectName.text = subject.subject
        val percent = subject.percentage.toInt()
        holder.progressBar.progress = percent
        holder.percentageText.text = "$percent%"

        // All blue — matching reference image
        holder.percentageText.setTextColor(Color.parseColor("#4F6EF7"))
        holder.progressBar.progressTintList =
            ColorStateList.valueOf(Color.parseColor("#4F6EF7"))
        holder.progressBar.progressBackgroundTintList =
            ColorStateList.valueOf(Color.parseColor("#E2E8F4"))
    }
}