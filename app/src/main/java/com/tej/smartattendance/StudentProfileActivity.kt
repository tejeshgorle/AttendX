package com.tej.smartattendance

import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.widget.*
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.net.Uri
import java.io.File
import com.cloudinary.android.MediaManager

class StudentProfileActivity : AppCompatActivity() {

    private lateinit var profileImage: ImageView
    private lateinit var nameInput: EditText
    private lateinit var rollInput: EditText
    private lateinit var departmentInput: EditText
    private lateinit var attendanceSummary: TextView

    // ── New stat chip TextViews ──
    private lateinit var summaryPresent: TextView
    private lateinit var summaryAbsent: TextView
    private lateinit var summaryTotal: TextView

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private var selectedImageUri: Uri? = null

    private val IMAGE_PICK = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_profile)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        profileImage = findViewById(R.id.profileImage)
        nameInput = findViewById(R.id.nameInput)
        rollInput = findViewById(R.id.rollInput)
        departmentInput = findViewById(R.id.departmentInput)
        attendanceSummary = findViewById(R.id.attendanceSummary)

        // ── Bind new stat chip TextViews ──
        summaryPresent = findViewById(R.id.summaryPresent)
        summaryAbsent = findViewById(R.id.summaryAbsent)
        summaryTotal = findViewById(R.id.summaryTotal)

        val updateBtn = findViewById<Button>(R.id.updateBtn)
        val logoutBtn = findViewById<Button>(R.id.logoutBtn)
        val changePhotoBtn = findViewById<TextView>(R.id.changePhotoBtn)

        loadProfile()
        loadAttendanceSummary()

        updateBtn.setOnClickListener {
            updateProfile()
        }

        logoutBtn.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }


        changePhotoBtn.setOnClickListener {
            val intent = Intent(
                Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            )
            startActivityForResult(intent, IMAGE_PICK)
        }
        findViewById<TextView>(R.id.backBtn).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun loadProfile() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users")
            .document(userId)
            .get()
            .addOnSuccessListener { doc ->
                nameInput.setText(doc.getString("name"))
                rollInput.setText(doc.getString("rollNumber"))
                departmentInput.setText(doc.getString("department"))

                // ── Also update display name on profile card ──
                val name = doc.getString("name") ?: "Student"
                val department = doc.getString("department") ?: "Student"
                findViewById<TextView>(R.id.profileNameDisplay).text = name
                findViewById<TextView>(R.id.profileRoleDisplay).text = department

                val imageUrl = doc.getString("profileImageUrl")
                if (imageUrl != null) {
                    Glide.with(this).load(imageUrl).into(profileImage)
                }
            }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IMAGE_PICK && resultCode == RESULT_OK) {
            selectedImageUri = data?.data
            profileImage.setImageURI(selectedImageUri)
            uploadProfileImage()
        }
    }

    private fun uploadProfileImage() {
        val uri = selectedImageUri ?: return
        val file = File(cacheDir, "profile_${System.currentTimeMillis()}.jpg")
        contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        MediaManager.get().upload(file.path)
            .callback(object : com.cloudinary.android.callback.UploadCallback {
                override fun onStart(requestId: String?) {}
                override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                    val imageUrl = resultData?.get("secure_url") as String
                    saveProfileImageUrl(imageUrl)
                }
                override fun onError(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {
                    Toast.makeText(this@StudentProfileActivity, "Upload failed", Toast.LENGTH_SHORT).show()
                }
                override fun onReschedule(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {}
            })
            .dispatch()
    }

    private fun saveProfileImageUrl(imageUrl: String) {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId)
            .update("profileImageUrl", imageUrl)
            .addOnSuccessListener {
                Glide.with(this).load(imageUrl).into(profileImage)
                Toast.makeText(this, "Profile photo updated", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateProfile() {
        val userId = auth.currentUser?.uid ?: return
        val data = mapOf(
            "name" to nameInput.text.toString(),
            "rollNumber" to rollInput.text.toString(),
            "department" to departmentInput.text.toString()
        )
        db.collection("users").document(userId)
            .update(data)
            .addOnSuccessListener {
                // ── Also refresh profile card display name ──
                findViewById<TextView>(R.id.profileNameDisplay).text = nameInput.text.toString()
                findViewById<TextView>(R.id.profileRoleDisplay).text = departmentInput.text.toString()
                Toast.makeText(this, "Profile Updated", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadAttendanceSummary() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("sessions")
            .get()
            .addOnSuccessListener { sessions ->
                val total = sessions.size()
                var present = 0
                var processed = 0

                if (total == 0) return@addOnSuccessListener

                for (session in sessions) {
                    session.reference
                        .collection("attendees")
                        .document(userId)
                        .get()
                        .addOnSuccessListener { doc ->
                            if (doc.exists()) present++
                            processed++

                            if (processed == total) {
                                val absent = total - present
                                val percentage = (present * 100.0) / total

                                // ── Original TextView — shortened to fit chip ──
                                attendanceSummary.text =
                                    "%.1f%%".format(percentage)

                                // ── New stat chip TextViews ──
                                summaryPresent.text = "$present"
                                summaryAbsent.text = "$absent"
                                summaryTotal.text = "$total"
                            }
                        }
                }
            }
    }
}