package com.tej.smartattendance

import android.content.Intent
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import de.hdodenhof.circleimageview.CircleImageView

class StudentAttendanceAdapter(
    private var originalList: MutableList<StudentAttendance>
) : RecyclerView.Adapter<StudentAttendanceAdapter.ViewHolder>() {

    private var filteredList: MutableList<StudentAttendance> = originalList.toMutableList()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameTV: TextView = view.findViewById(R.id.nameTV)
        val detailsTV: TextView = view.findViewById(R.id.detailsTV)
        val percentageBadge: TextView = view.findViewById(R.id.percentageBadge)
        val studentAvatar: CircleImageView = view.findViewById(R.id.studentAvatar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_student_attendance, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = filteredList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val student = filteredList[position]
        val context = holder.itemView.context

        // ── Load profile photo ──
        Glide.with(context)
            .load(student.profileImageUrl)
            .placeholder(R.drawable.ic_default_avatar)
            .error(R.drawable.ic_default_avatar)
            .circleCrop()
            .into(holder.studentAvatar)

        // ── Name ──
        holder.nameTV.text = student.name

        // ── Details using String Resource ──
        holder.detailsTV.text = context.getString(
            R.string.attendance_details,
            student.totalClasses,
            student.present,
            student.absent
        )

        // ── Percentage badge with color coding ──
        val pct = student.percentage
        holder.percentageBadge.text = context.getString(R.string.percentage_format, pct)

        when {
            pct >= 75 -> {
                holder.percentageBadge.setTextColor("#22C55E".toColorInt())
                holder.percentageBadge.setBackgroundResource(R.drawable.bg_badge_present)
            }
            pct >= 60 -> {
                holder.percentageBadge.setTextColor("#F59E0B".toColorInt())
                holder.percentageBadge.setBackgroundResource(R.drawable.bg_badge_warning)
            }
            else -> {
                holder.percentageBadge.setTextColor("#EF4444".toColorInt())
                holder.percentageBadge.setBackgroundResource(R.drawable.bg_badge_absent)
            }
        }

        // ── Click to view student attendance history ──
        holder.itemView.setOnClickListener {
            val intent = Intent(context, StudentAttendanceHistoryActivity::class.java)
            intent.putExtra("userId", student.userId)
            context.startActivity(intent)
        }
    }

    fun filter(query: String) {
        filteredList = if (query.isEmpty()) {
            originalList.toMutableList()
        } else {
            originalList.filter {
                it.name.contains(query, ignoreCase = true)
            }.toMutableList()
        }
        notifyDataSetChanged()
    }

    fun updateData(newList: List<StudentAttendance>) {
        originalList = newList.toMutableList()
        filteredList = newList.toMutableList()
        notifyDataSetChanged()
    }
}
