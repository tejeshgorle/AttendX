package com.tej.smartattendance

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class SelfieViewerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_selfie_viewer)

        val userId = intent.getStringExtra("userId")
        val selfieImageView = findViewById<ImageView>(R.id.selfieImageView)

        // ── Back button ──
        findViewById<TextView>(R.id.backBtn).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        if (userId != null) {
            fetchSelfieUrl(userId, selfieImageView)
            loadInfoChips(userId)
        } else {
            Toast.makeText(this, "User ID missing", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchSelfieUrl(userId: String, imageView: ImageView) {
        val db = FirebaseFirestore.getInstance()

        // Note: You'll need to adjust this path based on where you store the latest selfie URL
        db.collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                val selfieUrl = document.getString("profileImageUrl") // Or "lastSelfieUrl"
                if (selfieUrl != null) {
                    Glide.with(this).load(selfieUrl).into(imageView)
                } else {
                    Toast.makeText(this, "No selfie found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
            }
    }

    // ── Populate info chips: student name, date, subject, time ──
    private fun loadInfoChips(userId: String) {
        val db = FirebaseFirestore.getInstance()

        // Load student name
        db.collection("users").document(userId)
            .get()
            .addOnSuccessListener { doc ->
                val name = doc.getString("name") ?: "--"
                findViewById<TextView>(R.id.studentNameText).text = name
            }

        // Load latest attendance session for this student
        db.collection("sessions")
            .get()
            .addOnSuccessListener { sessions ->
                var latestTimestamp = 0L
                var latestSubject = "--"
                var processed = 0
                val total = sessions.size()

                if (total == 0) return@addOnSuccessListener

                for (session in sessions) {
                    val subject = session.getString("classId") ?: "--"
                    val sessionDate = session.getLong("date") ?: 0L

                    session.reference.collection("attendees")
                        .document(userId)
                        .get()
                        .addOnSuccessListener { doc ->
                            if (doc.exists() && sessionDate > latestTimestamp) {
                                latestTimestamp = sessionDate
                                latestSubject = subject
                            }
                            processed++
                            if (processed == total && latestTimestamp > 0L) {
                                val date = SimpleDateFormat(
                                    "dd MMM yyyy",
                                    Locale.getDefault()
                                ).format(Date(latestTimestamp))

                                val time = SimpleDateFormat(
                                    "hh:mm a",
                                    Locale.getDefault()
                                ).format(Date(latestTimestamp))

                                findViewById<TextView>(R.id.dateText).text = date
                                findViewById<TextView>(R.id.timeText).text = time
                                findViewById<TextView>(R.id.subjectText).text = latestSubject
                            }
                        }
                }
            }
    }
}