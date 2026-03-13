package com.tej.smartattendance

import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore

class SelfieViewerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_selfie_viewer)

        val userId = intent.getStringExtra("userId")
        val selfieImageView = findViewById<ImageView>(R.id.selfieImageView)

        if (userId != null) {
            fetchSelfieUrl(userId, selfieImageView)
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
}
