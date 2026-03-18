package com.tej.smartattendance

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.cloudinary.android.MediaManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File

class TeacherProfileActivity : AppCompatActivity() {

    private lateinit var profileImage: ImageView
    private lateinit var nameInput: EditText
    private lateinit var deptInput: EditText
    private lateinit var empIdInput: EditText
    private lateinit var summaryText: TextView

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private var selectedImageUri: Uri? = null
    private val IMAGE_PICK = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teacher_profile)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        profileImage = findViewById(R.id.profileImage)
        nameInput = findViewById(R.id.nameInput)
        deptInput = findViewById(R.id.departmentInput)
        empIdInput = findViewById(R.id.empIdInput)
        summaryText = findViewById(R.id.summaryText)

        // ── FIX: changePhotoBtn is TextView in new XML, not Button ──
        val changePhotoBtn = findViewById<TextView>(R.id.changePhotoBtn)
        val updateBtn = findViewById<Button>(R.id.updateBtn)
        val logoutBtn = findViewById<Button>(R.id.logoutBtn)

        // ── Back button ──
        findViewById<TextView>(R.id.backBtn).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        loadProfile()
        loadTeacherSummary()

        changePhotoBtn.setOnClickListener {
            val intent = Intent(
                Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            )
            startActivityForResult(intent, IMAGE_PICK)
        }

        updateBtn.setOnClickListener {
            updateProfile()
        }

        logoutBtn.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun loadProfile() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("users")
            .document(userId)
            .get()
            .addOnSuccessListener { doc ->
                val name = doc.getString("name") ?: ""
                val dept = doc.getString("department") ?: ""

                nameInput.setText(name)
                deptInput.setText(dept)
                empIdInput.setText(doc.getString("employeeId"))

                // ── Update profile card display ──
                findViewById<TextView>(R.id.profileNameDisplay).text = name
                findViewById<TextView>(R.id.profileRoleDisplay).text =
                    if (dept.isNotEmpty()) dept else "Teacher"

                val imageUrl = doc.getString("profileImageUrl")
                if (imageUrl != null) {
                    Glide.with(this)
                        .load(imageUrl)
                        .placeholder(R.drawable.ic_default_avatar)
                        .error(R.drawable.ic_default_avatar)
                        .circleCrop()
                        .into(profileImage)
                }
            }
    }

    private fun updateProfile() {
        val userId = auth.currentUser?.uid ?: return

        val data = mapOf(
            "name" to nameInput.text.toString(),
            "department" to deptInput.text.toString(),
            "employeeId" to empIdInput.text.toString()
        )

        db.collection("users")
            .document(userId)
            .update(data)
            .addOnSuccessListener {
                // ── Refresh profile card display ──
                findViewById<TextView>(R.id.profileNameDisplay).text =
                    nameInput.text.toString()
                findViewById<TextView>(R.id.profileRoleDisplay).text =
                    deptInput.text.toString()

                Toast.makeText(this, "Profile Updated", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadTeacherSummary() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("sessions")
            .whereEqualTo("createdBy", userId)
            .get()
            .addOnSuccessListener { sessions ->
                val count = sessions.size()

                // ── Original summaryText (sessions count) ──
                summaryText.text = "$count"

                // ── New summary chips ──
                loadStudentAndRateStats()
            }
    }

    private fun loadStudentAndRateStats() {
        db.collection("users")
            .whereEqualTo("role", "student")
            .get()
            .addOnSuccessListener { students ->
                val studentCount = students.size()
                findViewById<TextView>(R.id.summaryStudents).text = "$studentCount"

                // Calculate avg attendance rate
                val userId = auth.currentUser?.uid ?: return@addOnSuccessListener
                db.collection("sessions").get()
                    .addOnSuccessListener { sessions ->
                        val totalSessions = sessions.size()
                        if (totalSessions == 0 || studentCount == 0) {
                            findViewById<TextView>(R.id.summaryAvgRate).text = "0%"
                            return@addOnSuccessListener
                        }

                        var processed = 0
                        var totalPresent = 0

                        for (session in sessions) {
                            session.reference.collection("attendees").get()
                                .addOnSuccessListener { attendees ->
                                    totalPresent += attendees.size()
                                    processed++
                                    if (processed == totalSessions) {
                                        val avg = (totalPresent * 100.0) /
                                                (totalSessions * studentCount)
                                        findViewById<TextView>(R.id.summaryAvgRate)
                                            .text = "%.0f%%".format(avg)
                                    }
                                }
                        }
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
        val file = File(cacheDir, "teacher_profile.jpg")
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
                override fun onError(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {}
                override fun onReschedule(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {}
            })
            .dispatch()
    }

    private fun saveProfileImageUrl(imageUrl: String) {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users")
            .document(userId)
            .update("profileImageUrl", imageUrl)
            .addOnSuccessListener {
                Glide.with(this)
                    .load(imageUrl)
                    .circleCrop()
                    .into(profileImage)
                Toast.makeText(this, "Profile photo updated", Toast.LENGTH_SHORT).show()
            }
    }
}