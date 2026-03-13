package com.tej.smartattendance

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class StudentAttendanceHistoryActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private val historyList = mutableListOf<String>()
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_attendance_history)

        listView = findViewById(R.id.historyListView)

        db = FirebaseFirestore.getInstance()

        val userId = intent.getStringExtra("userId") ?: return

        loadHistory(userId)
    }

    private fun loadHistory(userId: String) {

        db.collection("sessions")
            .get()
            .addOnSuccessListener { sessions ->

                for (session in sessions) {

                    val sessionId = session.id

                    session.reference
                        .collection("attendees")
                        .document(userId)
                        .get()
                        .addOnSuccessListener { doc ->

                            if (doc.exists()) {

                                val timestamp = doc.getLong("timestamp") ?: 0

                                val date = SimpleDateFormat(
                                    "dd MMM yyyy",
                                    Locale.getDefault()
                                ).format(Date(timestamp))

                                historyList.add("$date   —   Present")
                            }

                            listView.adapter = ArrayAdapter(
                                this,
                                android.R.layout.simple_list_item_1,
                                historyList
                            )
                        }
                }
            }
    }
}