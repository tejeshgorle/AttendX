package com.tej.smartattendance

import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

class SessionHistoryActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var db: FirebaseFirestore
    private val sessionList = mutableListOf<String>()
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_history)

        listView = findViewById(R.id.listView)
        db = FirebaseFirestore.getInstance()

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, sessionList)
        listView.adapter = adapter

        loadSessions()
    }

    private fun loadSessions() {

        db.collection("sessions")
            .orderBy("date", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { sessions ->

                sessionList.clear()

                if (sessions.isEmpty) {
                    sessionList.add("No sessions found")
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
                        "dd MMM yyyy",
                        Locale.getDefault()
                    ).format(Date(sessionDate))

                    session.reference
                        .collection("attendees")
                        .get()
                        .addOnSuccessListener { attendees ->

                            val count = attendees.size()

                            sessionList.add("$formattedDate — $count students")

                            adapter.notifyDataSetChanged()
                        }
                        .addOnFailureListener {
                            Log.e("SESSION_HISTORY", "Failed to load attendees", it)
                        }
                }
            }
            .addOnFailureListener {

                Toast.makeText(
                    this,
                    "Failed to load sessions",
                    Toast.LENGTH_LONG
                ).show()

                Log.e("SESSION_HISTORY", "Firestore error", it)
            }
    }
}
