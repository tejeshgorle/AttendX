package com.tej.smartattendance

import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONObject

class GenerateQRActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private lateinit var subjectSpinner: Spinner
    private lateinit var qrImage: ImageView
    private lateinit var generateBtn: android.widget.LinearLayout

    // ── Holds reference to cancel previous timer on new QR ──
    private var countDownTimer: android.os.CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_generate_qr)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        subjectSpinner = findViewById(R.id.subjectSpinner)
        qrImage = findViewById(R.id.qrImage)
        generateBtn = findViewById(R.id.generateBtn)

        // ── Back button ──
        findViewById<TextView>(R.id.backBtn).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        setupSubjectSpinner()

        generateBtn.setOnClickListener {

            val teacherId = auth.currentUser?.uid ?: return@setOnClickListener
            val classId = subjectSpinner.selectedItem.toString()
            val currentTime = System.currentTimeMillis()
            val expiryTime = currentTime + (2 * 60 * 1000)
            val sessionId = "session_${System.currentTimeMillis()}"

            val sessionData = hashMapOf(
                "sessionId" to sessionId,
                "classId" to classId,
                "createdBy" to teacherId,
                "date" to currentTime,
                "expiry" to expiryTime
            )

            db.collection("sessions")
                .document(sessionId)
                .set(sessionData)
                .addOnSuccessListener {
                    Toast.makeText(this, "Session Created", Toast.LENGTH_SHORT).show()

                    val qrData = JSONObject().apply {
                        put("sessionId", sessionId)
                        put("classId", classId)
                        put("expiry", expiryTime)
                    }

                    generateQRCode(qrData.toString())
                    startTimer()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to create session ❌", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun startTimer() {
        val timerBadge = findViewById<android.widget.LinearLayout>(R.id.timerBadge)
        val timerText = findViewById<TextView>(R.id.timerText)

        // ── Cancel any running timer before starting fresh ──
        countDownTimer?.cancel()

        // ── Reset badge to amber (in case it was red from expired session) ──
        timerBadge.visibility = View.VISIBLE
        timerBadge.setBackgroundResource(R.drawable.bg_timer_badge)
        timerText.setTextColor(android.graphics.Color.parseColor("#A16207"))
        timerText.text = "Expires in 2:00"

        countDownTimer = object : android.os.CountDownTimer(2 * 60 * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val mins = millisUntilFinished / 60000
                val secs = (millisUntilFinished % 60000) / 1000
                timerText.text = "Expires in %d:%02d".format(mins, secs)
            }
            override fun onFinish() {
                timerText.text = "Session expired"
                timerBadge.setBackgroundResource(R.drawable.bg_badge_danger)
                timerText.setTextColor(android.graphics.Color.parseColor("#EF4444"))
            }
        }.start()
    }

    private fun setupSubjectSpinner() {
        val subjects = listOf(
            "CSE101",
            "DBMS302",
            "OS204",
            "CN301"
        )

        val adapter = ArrayAdapter(
            this,
            R.layout.spinner_item,
            subjects
        ).apply {
            setDropDownViewResource(R.layout.spinner_item)
        }

        subjectSpinner.adapter = adapter
    }

    private fun generateQRCode(data: String) {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(data, BarcodeFormat.QR_CODE, 512, 512)
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.RGB_565)

        for (x in 0 until 512) {
            for (y in 0 until 512) {
                bitmap.setPixel(
                    x, y,
                    if (bitMatrix.get(x, y))
                        android.graphics.Color.BLACK
                    else
                        android.graphics.Color.WHITE
                )
            }
        }

        qrImage.setImageBitmap(bitmap)
    }

    // ── Cancel timer when activity is destroyed to prevent memory leaks ──
    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}