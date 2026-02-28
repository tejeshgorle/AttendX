package com.tej.smartattendance

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.cloudinary.android.MediaManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private val imagePickCode = 100
    private var profileBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val nameET = findViewById<EditText>(R.id.nameET)
        val emailET = findViewById<EditText>(R.id.emailET)
        val passwordET = findViewById<EditText>(R.id.passwordET)
        val roleGroup = findViewById<RadioGroup>(R.id.roleGroup)
        val uploadImageBtn = findViewById<Button>(R.id.uploadImageBtn)
        val registerBtn = findViewById<Button>(R.id.registerBtn)
        val profileImageView = findViewById<ImageView>(R.id.profileImage)

        // Image Picker
        uploadImageBtn.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            startActivityForResult(intent, imagePickCode)
        }

        // Register Button
        registerBtn.setOnClickListener {
            val name = nameET.text.toString().trim()
            val email = emailET.text.toString().trim()
            val password = passwordET.text.toString().trim()

            if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (profileBitmap == null) {
                Toast.makeText(this, "Please upload profile image", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedRoleId = roleGroup.checkedRadioButtonId
            val role = if (selectedRoleId == R.id.teacherRadio) "teacher" else "student"

            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val userId = auth.currentUser!!.uid
                        uploadToCloudinary(profileBitmap!!, userId, name, email, role)
                    } else {
                        Toast.makeText(this, task.exception?.message, Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == imagePickCode && resultCode == RESULT_OK) {
            val uri = data?.data
            uri?.let {
                profileBitmap = MediaStore.Images.Media.getBitmap(contentResolver, it)
                findViewById<ImageView>(R.id.profileImage).setImageBitmap(profileBitmap)
            }
        }
    }

    private fun resizeBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val ratio = width.toFloat() / height.toFloat()
        return if (ratio > 1) {
            Bitmap.createScaledBitmap(bitmap, maxSize, (maxSize / ratio).toInt(), true)
        } else {
            Bitmap.createScaledBitmap(bitmap, (maxSize * ratio).toInt(), maxSize, true)
        }
    }

    private fun uploadToCloudinary(bitmap: Bitmap, userId: String, name: String, email: String, role: String) {
        val resizedBitmap = resizeBitmap(bitmap, 600)
        val file = File(cacheDir, "profile_${System.currentTimeMillis()}.jpg")

        file.outputStream().use {
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, it)
        }

        MediaManager.get().upload(file.path)
            .callback(object : com.cloudinary.android.callback.UploadCallback {
                override fun onStart(requestId: String?) {}
                override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}

                override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                    val profileUrl = resultData?.get("secure_url") as String

                    val userMap = hashMapOf(
                        "name" to name,
                        "email" to email,
                        "role" to role,
                        "profileImageUrl" to profileUrl
                    )

                    db.collection("users").document(userId).set(userMap)
                        .addOnSuccessListener {
                            Toast.makeText(this@RegisterActivity, "Registration Successful", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this@RegisterActivity, LoginActivity::class.java))
                            finish()
                        }
                }

                override fun onError(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {
                    Toast.makeText(this@RegisterActivity, "Image upload failed ❌", Toast.LENGTH_LONG).show()
                }

                override fun onReschedule(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {}
            }).dispatch()
    }
}
