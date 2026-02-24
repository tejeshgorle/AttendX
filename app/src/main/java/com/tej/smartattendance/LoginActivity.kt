package com.tej.smartattendance

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
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
        setContentView(R.layout.activity_login)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val emailET = findViewById<EditText>(R.id.emailET)
        val passwordET = findViewById<EditText>(R.id.passwordET)
        val loginBtn = findViewById<Button>(R.id.loginBtn)
        val registerText = findViewById<TextView>(R.id.registerText)

        loginBtn.setOnClickListener {

            val email = findViewById<EditText>(R.id.emailET).text.toString()
            val password = findViewById<EditText>(R.id.passwordET).text.toString()

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->

                    if (task.isSuccessful) {

                        val userId = auth.currentUser!!.uid

                        db.collection("users").document(userId)
                            .get()
                            .addOnSuccessListener { document ->

                                val role = document.getString("role")

                                if (role == "teacher") {
                                    startActivity(Intent(this, TeacherDashboardActivity::class.java))
                                } else {
                                    startActivity(Intent(this, StudentDashboardActivity::class.java))
                                }

                                finish()
                            }

                    } else {
                        Toast.makeText(this, task.exception?.message, Toast.LENGTH_SHORT).show()
                    }
                }
        }

        registerText.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }
}