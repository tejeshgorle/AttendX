package com.tej.smartattendance

import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONObject

class GenerateQRActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_generate_qr)

        val qrImage = findViewById<ImageView>(R.id.qrImage)
        val generateBtn = findViewById<Button>(R.id.generateBtn)

        generateBtn.setOnClickListener {

            val currentTime = System.currentTimeMillis()
            val expiryTime = currentTime + (2 * 60 * 1000) // 5 minutes expiry

            val sessionId = "session_${System.currentTimeMillis()}"

            val qrData = JSONObject().apply {
                put("sessionId", sessionId)
                put("classId", "CSE101")
                put("expiry", expiryTime)
            }

            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(
                qrData.toString(),
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
}