package com.tej.smartattendance

import android.os.Bundle
import android.util.Log
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

class SessionHistoryActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var db: FirebaseFirestore

    // ── Replaced String list + ArrayAdapter with typed list + SessionHistoryAdapter ──
    private val sessionItems = mutableListOf<SessionHistoryItem>()
    private lateinit var adapter: SessionHistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_history)

        listView = findViewById(R.id.listView)
        db = FirebaseFirestore.getInstance()

        // ── Back button ──
        findViewById<TextView>(R.id.backBtn).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        adapter = SessionHistoryAdapter(this, sessionItems)
        listView.adapter = adapter

        loadSessions()
    }

    private fun loadSessions() {
        db.collection("sessions")
            .orderBy("date", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { sessions ->

                sessionItems.clear()

                if (sessions.isEmpty) {
                    sessionItems.add(
                        SessionHistoryItem(
                            date = "No sessions found",
                            subject = "",
                            studentCount = 0
                        )
                    )
                    adapter.notifyDataSetChanged()
                    return@addOnSuccessListener
                }

                for (session in sessions) {

                    val sessionDate = session.getLong("date")

                    if (sessionDate == null) {
                        Log.d("SESSION_HISTORY", "Date missing for session: ${session.id}")
                        continue
                    }

                    val formattedDate = SimpleDateFormat(
                        "dd MMM yyyy, hh:mm a",
                        Locale.getDefault()
                    ).format(Date(sessionDate))

                    // ── Get subject (classId) for display ──
                    val subject = session.getString("classId") ?: "Class session"

                    session.reference
                        .collection("attendees")
                        .get()
                        .addOnSuccessListener { attendees ->
                            val count = attendees.size()

                            sessionItems.add(
                                SessionHistoryItem(
                                    date = formattedDate,
                                    subject = subject,
                                    studentCount = count
                                )
                            )

                            adapter.notifyDataSetChanged()
                        }
                        .addOnFailureListener {
                            Log.e("SESSION_HISTORY", "Failed to load attendees", it)
                        }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load sessions", Toast.LENGTH_LONG).show()
                Log.e("SESSION_HISTORY", "Firestore error", it)
            }
    }
}