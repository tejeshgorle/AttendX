package com.tej.smartattendance

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── AUTO-LOGIN: If already authenticated, skip login screen ──
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(currentUser.uid)
                .get()
                .addOnSuccessListener { doc ->
                    val role = doc.getString("role")
                    if (role == "teacher") {
                        startActivity(Intent(this, TeacherDashboardActivity::class.java))
                    } else {
                        startActivity(Intent(this, StudentDashboardActivity::class.java))
                    }
                    finish()
                }
                .addOnFailureListener {
                    // Firestore failed — fall through to login screen
                    showLoginScreen()
                }
            return // ← stop here while Firestore fetches
        }

        // ── No user logged in → show login screen ──
        showLoginScreen()
    }

    private fun showLoginScreen() {
        setContentView(R.layout.activity_login)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Views
        val emailET = findViewById<EditText>(R.id.emailET)
        val passwordET = findViewById<EditText>(R.id.passwordET)
        val loginBtn = findViewById<Button>(R.id.loginBtn)
        val registerText = findViewById<TextView>(R.id.registerText)
        val roleStudent = findViewById<LinearLayout>(R.id.roleStudent)
        val roleTeacher = findViewById<LinearLayout>(R.id.roleTeacher)

        // ── ROLE CARD TOGGLE ──────────────────────────────────────
        roleStudent.setOnClickListener {
            roleStudent.setBackgroundResource(R.drawable.bg_role_card_active)
            (roleStudent.getChildAt(1) as TextView).setTextColor(Color.parseColor("#4F6EF7"))
            roleTeacher.setBackgroundResource(R.drawable.bg_role_card_inactive)
            (roleTeacher.getChildAt(1) as TextView).setTextColor(Color.parseColor("#2D3748"))
        }

        roleTeacher.setOnClickListener {
            roleTeacher.setBackgroundResource(R.drawable.bg_role_card_active)
            (roleTeacher.getChildAt(1) as TextView).setTextColor(Color.parseColor("#4F6EF7"))
            roleStudent.setBackgroundResource(R.drawable.bg_role_card_inactive)
            (roleStudent.getChildAt(1) as TextView).setTextColor(Color.parseColor("#2D3748"))
        }

        // ── LOGIN BUTTON ──────────────────────────────────────────
        loginBtn.setOnClickListener {
            val email = emailET.text.toString().trim()
            val password = passwordET.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val userId = auth.currentUser!!.uid

                        db.collection("users")
                            .document(userId)
                            .get()
                            .addOnSuccessListener { document ->
                                val role = document.getString("role")

                                if (role == "teacher") {
                                    startActivity(
                                        Intent(this, TeacherDashboardActivity::class.java)
                                    )
                                } else {
                                    startActivity(
                                        Intent(this, StudentDashboardActivity::class.java)
                                    )
                                }
                                finish()
                            }
                            .addOnFailureListener {
                                Toast.makeText(
                                    this,
                                    "Failed to fetch user data: ${it.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }

                    } else {
                        Toast.makeText(
                            this,
                            task.exception?.message,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
        }

        // ── REGISTER LINK ─────────────────────────────────────────
        registerText.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }
}