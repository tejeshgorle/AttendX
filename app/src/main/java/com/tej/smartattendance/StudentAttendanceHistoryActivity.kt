package com.tej.smartattendance

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

// ── Data class for each history item ──
data class AttendanceHistoryItem(
    val date: String,
    val subject: String,
    val isPresent: Boolean
)

// ── Custom adapter using item_attendance_history layout ──
class AttendanceHistoryAdapter(
    private val context: android.content.Context,
    private val items: List<AttendanceHistoryItem>
) : ArrayAdapter<AttendanceHistoryItem>(context, 0, items) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_attendance_history, parent, false)

        val item = items[position]

        view.findViewById<TextView>(R.id.itemDate).text = item.date
        view.findViewById<TextView>(R.id.itemSubject).text = item.subject

        val statusDot = view.findViewById<View>(R.id.statusDot)
        val statusBadge = view.findViewById<TextView>(R.id.statusBadge)

        if (item.isPresent) {
            statusDot.setBackgroundResource(R.drawable.bg_dot_green)
            statusBadge.text = "Present"
            statusBadge.setTextColor(Color.parseColor("#22C55E"))
            statusBadge.setBackgroundResource(R.drawable.bg_badge_present)
        } else {
            statusDot.setBackgroundResource(R.drawable.bg_dot_red)
            statusBadge.text = "Absent"
            statusBadge.setTextColor(Color.parseColor("#EF4444"))
            statusBadge.setBackgroundResource(R.drawable.bg_badge_absent)
        }

        return view
    }
}

class StudentAttendanceHistoryActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private val historyItems = mutableListOf<AttendanceHistoryItem>()
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_attendance_history)

        listView = findViewById(R.id.historyListView)
        db = FirebaseFirestore.getInstance()

        // ── Back button ──
        findViewById<TextView>(R.id.backBtn).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        val userId = intent.getStringExtra("userId") ?: return

        // ── Load student name for subtitle ──
        db.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                val name = doc.getString("name") ?: "Student"
                findViewById<TextView>(R.id.historySubtitle).text =
                    "$name · Full attendance record"
            }

        loadHistory(userId)
    }

    private fun loadHistory(userId: String) {
        db.collection("sessions")
            .get()
            .addOnSuccessListener { sessions ->

                val totalSessions = sessions.size()
                var processed = 0
                var presentCount = 0

                if (totalSessions == 0) {
                    updateStats(0, 0, 0)
                    return@addOnSuccessListener
                }

                // Collect all results then sort by date
                val results = mutableListOf<AttendanceHistoryItem>()

                for (session in sessions) {
                    val subject = session.getString("classId") ?: "Unknown"
                    val sessionDate = session.getLong("date") ?: 0L

                    session.reference
                        .collection("attendees")
                        .document(userId)
                        .get()
                        .addOnSuccessListener { doc ->
                            val isPresent = doc.exists()

                            val formattedDate = SimpleDateFormat(
                                "dd MMM yyyy, hh:mm a",
                                Locale.getDefault()
                            ).format(Date(sessionDate))

                            results.add(
                                AttendanceHistoryItem(
                                    date = formattedDate,
                                    subject = subject,
                                    isPresent = isPresent
                                )
                            )

                            if (isPresent) presentCount++
                            processed++

                            if (processed == totalSessions) {
                                // Sort newest first
                                results.sortByDescending { it.date }

                                historyItems.clear()
                                historyItems.addAll(results)

                                val adapter = AttendanceHistoryAdapter(this, historyItems)
                                listView.adapter = adapter

                                // ── Update stat chips ──
                                updateStats(totalSessions, presentCount, totalSessions - presentCount)
                            }
                        }
                }
            }
    }

    private fun updateStats(total: Int, present: Int, absent: Int) {
        findViewById<TextView>(R.id.statTotal).text = "$total"
        findViewById<TextView>(R.id.statPresent).text = "$present"
        findViewById<TextView>(R.id.statAbsent).text = "$absent"
    }
}