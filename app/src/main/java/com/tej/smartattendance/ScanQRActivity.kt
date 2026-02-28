package com.tej.smartattendance

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.location.Location
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.cloudinary.android.MediaManager
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.*
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

@OptIn(ExperimentalGetImage::class)
class ScanQRActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private var isScanned = false
    private val selfieRequestCode = 200

    private var currentSessionId = ""
    private var currentClassId = ""

    private val classroomLat = 29.9457523
    private val classroomLng = 76.8160287
    private val allowedRadius = 500f

    private var qrExpiryTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan_qr)

        previewView = findViewById(R.id.previewView)
        cameraExecutor = Executors.newSingleThreadExecutor()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ),
                101
            )
        }
    }

    private fun allPermissionsGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera() {

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({

            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageProxy(imageProxy)
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalyzer
            )

        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImageProxy(imageProxy: ImageProxy) {

        if (isScanned) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        val scanner = BarcodeScanning.getClient()

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {

                    val qrValue = barcode.rawValue ?: continue
                    isScanned = true
                    validateQR(qrValue)
                    break
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun validateQR(qrData: String) {

        try {
            val json = JSONObject(qrData)
            qrExpiryTime = json.getLong("expiry")
            currentSessionId = json.getString("sessionId")
            currentClassId = json.getString("classId")

            if (System.currentTimeMillis() > qrExpiryTime) {
                Toast.makeText(this, "QR Expired ❌", Toast.LENGTH_LONG).show()
                finish()
                return
            }
            checkLocation()

        }
        catch (e: Exception) {
            Toast.makeText(this, "Invalid QR ❌", Toast.LENGTH_LONG).show()
            finish()
        }

        Log.d("QR_DEBUG", "Scanned QR: $qrData")
    }

    private fun retrySelfieCapture(message: String) {

        val currentTime = System.currentTimeMillis()

        if (currentTime > qrExpiryTime) {
            Toast.makeText(this, "QR Expired ❌", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

        // Retry after short delay
        previewView.postDelayed({
            openSelfieCamera()
        }, 1000)
    }

    @SuppressLint("MissingPermission")
    private fun checkLocation() {

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000
        ).setMaxUpdates(1).build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            object : LocationCallback() {

                override fun onLocationResult(locationResult: LocationResult) {

                    val location = locationResult.lastLocation

                    if (location != null) {

                        val results = FloatArray(1)

                        Location.distanceBetween(
                            location.latitude,
                            location.longitude,
                            classroomLat,
                            classroomLng,
                            results
                        )

                        if (results[0] <= allowedRadius) {
                            openSelfieCamera()
                        } else {
                            retrySelfieCapture("Not inside classroom ❌")
                        }
                    }

                    fusedLocationClient.removeLocationUpdates(this)
                }
            },
            mainLooper
        )
    }

    private fun openSelfieCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        startActivityForResult(intent, selfieRequestCode)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == selfieRequestCode && resultCode == RESULT_OK) {
            val selfieBitmap = data?.extras?.get("data") as? Bitmap
            if (selfieBitmap != null) {
                validateSelfieBeforeAPI(selfieBitmap)
            } else {
                retrySelfieCapture("Selfie capture failed ❌")
            }
        } else if (requestCode == selfieRequestCode) {
            retrySelfieCapture("Selfie cancelled or failed ❌")
        }
    }

    private fun validateSelfieBeforeAPI(selfieBitmap: Bitmap) {

        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .build()

        val detector = FaceDetection.getClient(options)
        val image = InputImage.fromBitmap(selfieBitmap, 0)

        detector.process(image)
            .addOnSuccessListener { faces ->

                if (faces.size != 1) {
                    retrySelfieCapture("Exactly one face required ❌")
                    return@addOnSuccessListener
                }

                val face = faces[0]

                // Angle Check
                if (abs(face.headEulerAngleY) > 20 ||
                    abs(face.headEulerAngleZ) > 15) {

                    retrySelfieCapture("Keep face straight ❌")
                    return@addOnSuccessListener
                }

                // Brightness Check
                val brightness = calculateBrightness(selfieBitmap)
                if (brightness < 40 || brightness > 220) {
                    retrySelfieCapture("Lighting not suitable ❌")
                    return@addOnSuccessListener
                }
                uploadSelfie(selfieBitmap)
            }
    }

    private fun calculateBrightness(bitmap: Bitmap): Double {

        var total = 0L
        var count = 0

        for (x in 0 until bitmap.width step 10) {
            for (y in 0 until bitmap.height step 10) {

                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xff
                val g = (pixel shr 8) and 0xff
                val b = pixel and 0xff

                total += (r + g + b) / 3
                count++
            }
        }

        return total.toDouble() / count
    }
    private fun compareFacesWithAPI(profileUrl: String, selfieUrl: String) {

        val apiKey = "50OzeA05l1etE77gOx3kg-VrtNxW_Yud"
        val apiSecret = "k8mUC1v5X-4V_f-rVy6Y1PukEnbMVWtI"

        RetrofitClient.instance.compareFaces(
            apiKey,
            apiSecret,
            profileUrl,
            selfieUrl
        ).enqueue(object : Callback<FaceCompareResponse> {

            override fun onResponse(
                call: Call<FaceCompareResponse>,
                response: Response<FaceCompareResponse>
            ) {

                Log.d("FACE_API_STATUS", "Code: ${response.code()}")

                if (response.isSuccessful) {

                    val body = response.body()

                    Log.d("FACE_API_BODY", body.toString())

                    val confidence = body?.confidence ?: 0.0

                    Log.d("FACE_API_CONFIDENCE", confidence.toString())

                    if (confidence > 70) {
                        saveAttendance(selfieUrl)
                    } else {
                        retrySelfieCapture("Face Mismatch ❌ (Confidence: $confidence)")
                    }

                } else {

                    val error = response.errorBody()?.string()
                    Log.d("FACE_API_ERROR", error ?: "Unknown error")

                    retrySelfieCapture("Face API Error ❌")
                }
            }

            override fun onFailure(call: Call<FaceCompareResponse>, t: Throwable) {
                retrySelfieCapture("Face API Failed ❌")
            }
        })
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
    private fun uploadSelfie(bitmap: Bitmap) {

        val file = File(cacheDir, "selfie_${System.currentTimeMillis()}.jpg")
        file.outputStream().use {
            val resized = resizeBitmap(bitmap, 600)
            resized.compress(Bitmap.CompressFormat.JPEG, 80, it)
        }

        MediaManager.get().upload(file.path)
            .callback(object : com.cloudinary.android.callback.UploadCallback {

                override fun onStart(requestId: String?) {}

                override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}

                override fun onSuccess(
                    requestId: String?,
                    resultData: MutableMap<Any?, Any?>?
                ) {

                    val selfieUrl = resultData?.get("secure_url") as String

                    val userId = auth.currentUser!!.uid

                    db.collection("users")
                        .document(userId)
                        .get()
                        .addOnSuccessListener { document ->

                            val profileUrl = document.getString("profileImageUrl")

                            if (profileUrl != null) {

                                // Call Face++ API
                                compareFacesWithAPI(profileUrl, selfieUrl)

                            } else {
                                retrySelfieCapture("Profile image missing ❌")
                            }
                        }
                }

                override fun onError(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {
                    retrySelfieCapture("Selfie upload failed ❌")
                }

                override fun onReschedule(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {}
            })
            .dispatch()
    }
    private fun saveAttendance(selfieUrl: String) {

        val userId = auth.currentUser?.uid ?: return

        val docRef = db.collection("sessions")
            .document(currentSessionId)
            .collection("attendees")
            .document(userId)

        docRef.get().addOnSuccessListener { document ->

            if (document.exists()) {
                Toast.makeText(this, "Already Marked ❌", Toast.LENGTH_LONG).show()
                finish()
            } else {

                val attendanceData = hashMapOf(
                    "userId" to userId,
                    "selfieUrl" to selfieUrl,
                    "timestamp" to System.currentTimeMillis()
                )

                docRef.set(attendanceData)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Attendance Marked ✅", Toast.LENGTH_LONG).show()
                        finish()
                    }
                    .addOnFailureListener {
                        retrySelfieCapture("Failed to mark attendance ❌")
                    }
            }
        }
    }
}