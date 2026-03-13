package com.tej.smartattendance

import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONObject

class GenerateQRActivity : AppCompatActivity() {


    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_generate_qr)

        val qrImage = findViewById<ImageView>(R.id.qrImage)
        val generateBtn = findViewById<Button>(R.id.generateBtn)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        generateBtn.setOnClickListener {

            val teacherId = auth.currentUser?.uid ?: return@setOnClickListener

            val currentTime = System.currentTimeMillis()
            val expiryTime = currentTime + (2 * 60 * 1000) // 2 minutes expiry

            val sessionId = "session_${System.currentTimeMillis()}"

            val classId = "CSE101"

            // 🔥 Create session data
            val sessionData = hashMapOf(
                "sessionId" to sessionId,
                "classId" to classId,
                "createdBy" to teacherId,
                "date" to currentTime,      // 🔥 IMPORTANT for filtering
                "expiry" to expiryTime
            )

            // 🔥 Save session to Firestore
            db.collection("sessions")
                .document(sessionId)
                .set(sessionData)
                .addOnSuccessListener {

                    Toast.makeText(this, "Session Created", Toast.LENGTH_SHORT).show()

                    // Generate QR after session is saved
                    val qrData = JSONObject().apply {
                        put("sessionId", sessionId)
                        put("classId", classId)
                        put("expiry", expiryTime)
                    }

                    generateQRCode(qrData.toString(), qrImage)

                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to create session ❌", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun generateQRCode(data: String, qrImage: ImageView) {

        val writer = QRCodeWriter()

        val bitMatrix = writer.encode(
            data,
            BarcodeFormat.QR_CODE,
            512,
            512
        )

        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.RGB_565)

        for (x in 0 until 512) {
            for (y in 0 until 512) {
                bitmap.setPixel(
                    x,
                    y,
                    if (bitMatrix.get(x, y)) android.graphics.Color.BLACK
                    else android.graphics.Color.WHITE
                )
            }
        }

        qrImage.setImageBitmap(bitmap)
    }
}