package com.tej.smartattendance

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class StudentAttendanceAdapter(
    private val originalList: MutableList<StudentAttendance>
) : RecyclerView.Adapter<StudentAttendanceAdapter.ViewHolder>() {

    private var filteredList: MutableList<StudentAttendance> = originalList.toMutableList()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameTV: TextView = view.findViewById(R.id.nameTV)
        val detailsTV: TextView = view.findViewById(R.id.detailsTV)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_student_attendance, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = filteredList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        val student = filteredList[position]

        holder.nameTV.text = student.name

        val percentageText = "${"%.2f".format(student.percentage)}%"

        holder.detailsTV.text =
            "Total: ${student.totalClasses} | Present: ${student.present} | Absent: ${student.absent} | $percentageText"

        if (student.percentage < 75) {
            holder.detailsTV.setTextColor(android.graphics.Color.RED)
            holder.detailsTV.append("  ⚠ Low Attendance")
        } else {
            holder.detailsTV.setTextColor(android.graphics.Color.BLACK)
        }
        holder.itemView.setOnClickListener {

            val context = holder.itemView.context

            val intent = Intent(
                context,
                StudentAttendanceHistoryActivity::class.java
            )

            intent.putExtra("userId", student.userId)

            context.startActivity(intent)
        }
    }

    // 🔹 Search Filter Function
    fun filter(query: String) {

        filteredList = if (query.isEmpty()) {
            originalList.toMutableList()
        } else {
            originalList.filter {
                it.name.lowercase().contains(query.lowercase())
            }.toMutableList()
        }

        notifyDataSetChanged()
    }

    // 🔹 Refresh Data (when reloading attendance)
    fun updateData(newList: List<StudentAttendance>) {

        originalList.clear()
        originalList.addAll(newList)

        filteredList.clear()
        filteredList.addAll(newList)

        notifyDataSetChanged()
    }
}