package com.tej.smartattendance

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
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
import com.google.firebase.firestore.Query
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.*
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

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
    private lateinit var subjectText: TextView
    private lateinit var sessionStatus: TextView
    private lateinit var countdownText: TextView
    
    private val countdownHandler = Handler(Looper.getMainLooper())
    private var countdownRunnable: Runnable? = null
    
    private lateinit var loadingOverlay: FrameLayout
    private lateinit var loadingText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan_qr)

        previewView = findViewById(R.id.previewView)
        cameraExecutor = Executors.newSingleThreadExecutor()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        subjectText = findViewById(R.id.subjectText)
        sessionStatus = findViewById(R.id.sessionStatus)
        countdownText = findViewById(R.id.countdownText)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        loadingText = findViewById(R.id.loadingText)

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

    private fun showLoading(message: String) {
        loadingOverlay.visibility = View.VISIBLE
        loadingText.text = message
    }

    private fun hideLoading() {
        loadingOverlay.visibility = View.GONE
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

            subjectText.text = "Subject: $currentClassId"

            if (System.currentTimeMillis() > qrExpiryTime) {
                sessionStatus.text = "Session: Expired"
                sessionStatus.setTextColor(android.graphics.Color.RED)
                Toast.makeText(this, "QR Expired ❌", Toast.LENGTH_LONG).show()
                finish()
                return
            }

            sessionStatus.text = "Session: Active"
            sessionStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
            
            startCountdown()
            checkLocation()

        } catch (e: Exception) {
            Toast.makeText(this, "Invalid QR ❌", Toast.LENGTH_LONG).show()
            finish()
        }
        Log.d("QR_DEBUG", "Scanned QR: $qrData")
    }

    private fun startCountdown() {
        countdownRunnable = object : Runnable {
            override fun run() {
                val remaining = qrExpiryTime - System.currentTimeMillis()
                if (remaining <= 0) {
                    sessionStatus.text = "Session: Expired"
                    sessionStatus.setTextColor(android.graphics.Color.RED)
                    countdownText.text = "Session expired.\nPlease ask the teacher to generate a new QR."
                    return
                }
                val seconds = remaining / 1000
                val minutesPart = seconds / 60
                val secondsPart = seconds % 60
                countdownText.text = "Expires in: %02d:%02d".format(minutesPart, secondsPart)
                countdownHandler.postDelayed(this, 1000)
            }
        }
        countdownHandler.post(countdownRunnable!!)
    }

    override fun onDestroy() {
        super.onDestroy()
        countdownRunnable?.let { countdownHandler.removeCallbacks(it) }
        cameraExecutor.shutdown()
    }

    private fun retrySelfieCapture(message: String) {
        val currentTime = System.currentTimeMillis()
        if (currentTime > qrExpiryTime) {
            Toast.makeText(this, "QR Expired ❌", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        previewView.postDelayed({ openSelfieCamera() }, 1000)
    }

    @SuppressLint("MissingPermission")
    private fun checkLocation() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setMaxUpdates(1).build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    val location = locationResult.lastLocation
                    if (location != null) {
                        val results = FloatArray(1)
                        Location.distanceBetween(
                            location.latitude, location.longitude,
                            classroomLat, classroomLng, results
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
                if (abs(face.headEulerAngleY) > 20 || abs(face.headEulerAngleZ) > 15) {
                    retrySelfieCapture("Keep face straight ❌")
                    return@addOnSuccessListener
                }
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
        showLoading("Verifying face...")
        if (!isInternetAvailable()) {
            hideLoading()
            Toast.makeText(this, "No internet connection ❌", Toast.LENGTH_LONG).show()
            retrySelfieCapture("No internet connection ❌")
            return
        }
        val apiKey = "50OzeA05l1etE77gOx3kg-VrtNxW_Yud"
        val apiSecret = "k8mUC1v5X-4V_f-rVy6Y1PukEnbMVWtI"

        RetrofitClient.instance.compareFaces(apiKey, apiSecret, profileUrl, selfieUrl)
            .enqueue(object : Callback<FaceCompareResponse> {
                override fun onResponse(call: Call<FaceCompareResponse>, response: Response<FaceCompareResponse>) {
                    hideLoading()
                    if (response.isSuccessful) {
                        val confidence = response.body()?.confidence ?: 0.0
                        if (confidence > 70) {
                            saveAttendance(selfieUrl)
                        } else {
                            retrySelfieCapture("Face Mismatch ❌ (Confidence: $confidence)")
                        }
                    } else {
                        retrySelfieCapture("Face API Error ❌")
                    }
                }
                override fun onFailure(call: Call<FaceCompareResponse>, t: Throwable) {
                    hideLoading()
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

//    private fun uploadSelfie(bitmap: Bitmap) {
//        showLoading("Uploading selfie...")
//        if (!isInternetAvailable()) {
//
//            Toast.makeText(
//                this,
//                "Network error. Please check your internet connection.",
//                Toast.LENGTH_LONG
//            ).show()
//
//            retrySelfieCapture("No internet connection ❌")
//            return
//        }
//        val file = File(cacheDir, "selfie_${System.currentTimeMillis()}.jpg")
//        file.outputStream().use {
//            val resized = resizeBitmap(bitmap, 600)
//            resized.compress(Bitmap.CompressFormat.JPEG, 80, it)
//        }
//
//        MediaManager.get().upload(file.path)
//            .callback(object : com.cloudinary.android.callback.UploadCallback {
//
//                override fun onStart(requestId: String?) {}
//
//                override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
//
//                override fun onSuccess(
//                    requestId: String?,
//                    resultData: MutableMap<Any?, Any?>?
//                ) {
//
//                    val selfieUrl = resultData?.get("secure_url") as String
//
//                    val userId = auth.currentUser!!.uid
//
//                    db.collection("users")
//                        .document(userId)
//                        .get()
//                        .addOnSuccessListener { document ->
//
//                            val profileUrl = document.getString("profileImageUrl")
//
//                            if (profileUrl != null) {
//
//                                // Call Face++ API
//                                compareFacesWithAPI(profileUrl, selfieUrl)
//
//                            } else {
//                                retrySelfieCapture("Profile image missing ❌")
//                            }
//                        }
//                }
//
//                override fun onError(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {
//                    retrySelfieCapture("Selfie upload failed ❌")
//                }
//
//                override fun onReschedule(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {}
//            })
//            .dispatch()
//    }

    private fun uploadSelfie(bitmap: Bitmap) {
        showLoading("Uploading selfie...")
        if (!isInternetAvailable()) {
            hideLoading()
            retrySelfieCapture("No internet connection ❌")
            return
        }
        val file = File(cacheDir, "selfie_${System.currentTimeMillis()}.jpg")
        file.outputStream().use {
            val resized = resizeBitmap(bitmap, 600)
            resized.compress(Bitmap.CompressFormat.JPEG, 80, it)
        }
        MediaManager.get().upload(file.path)
            .callback(object : com.cloudinary.android.callback.UploadCallback {
                override fun onStart(requestId: String?) {}
                override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                    val selfieUrl = resultData?.get("secure_url") as String
                    val userId = auth.currentUser!!.uid
                    db.collection("users").document(userId).get()
                        .addOnSuccessListener { document ->
                            val profileUrl = document.getString("profileImageUrl")
                            if (profileUrl != null) {
                                compareFacesWithAPI(profileUrl, selfieUrl)
                            } else {
                                hideLoading()
                                retrySelfieCapture("Profile image missing ❌")
                            }
                        }
                }
                override fun onError(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {
                    hideLoading()
                    retrySelfieCapture("Selfie upload failed ❌")
                }
                override fun onReschedule(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {}
            }).dispatch()
    }

//    private fun saveAttendance(selfieUrl: String) {
//
//        val userId = auth.currentUser?.uid ?: return
//
//        val docRef = db.collection("sessions")
//            .document(currentSessionId)
//            .collection("attendees")
//            .document(userId)
//
//        docRef.get()
//            .addOnSuccessListener { document ->
//
//                if (document.exists()) {
//
//                    Toast.makeText(
//                        this,
//                        "Attendance already marked for this session ❌",
//                        Toast.LENGTH_LONG
//                    ).show()
//
//                    isScanned = true
//                    finish()
//                    return@addOnSuccessListener
//                }
//
//                val attendanceData = hashMapOf(
//                    "userId" to userId,
//                    "sessionId" to currentSessionId,
//                    "classId" to currentClassId,
//                    "selfieUrl" to selfieUrl,
//                    "timestamp" to System.currentTimeMillis()
//                )
//
//                docRef.set(attendanceData)
//                    .addOnSuccessListener {
//
//                        Toast.makeText(
//                            this,
//                            "Attendance Marked Successfully ✅",
//                            Toast.LENGTH_LONG
//                        ).show()
//
//                        // Stop further scans
//                        isScanned = true
//
//                        finish()
//                    }
//                    .addOnFailureListener {
//
//                        if (!isInternetAvailable()) {
//
//                            Toast.makeText(
//                                this,
//                                "Network error. Attendance could not be saved.",
//                                Toast.LENGTH_LONG
//                            ).show()
//
//                        } else {
//
//                            retrySelfieCapture("Failed to mark attendance ❌")
//                        }
//                    }
//            }
//            .addOnFailureListener {
//
//                Toast.makeText(
//                    this,
//                    "Error checking attendance",
//                    Toast.LENGTH_LONG
//                ).show()
//
//                retrySelfieCapture("Please try again")
//            }
//    }

    private fun saveAttendance(selfieUrl: String) {
        showLoading("Marking attendance...")
        val userId = auth.currentUser?.uid ?: return
        val docRef = db.collection("sessions").document(currentSessionId).collection("attendees").document(userId)
        docRef.get().addOnSuccessListener { document ->
            if (document.exists()) {
                hideLoading()
                Toast.makeText(this, "Attendance already marked ❌", Toast.LENGTH_LONG).show()
                finish()
                return@addOnSuccessListener
            }
            val attendanceData = hashMapOf(
                "userId" to userId,
                "sessionId" to currentSessionId,
                "classId" to currentClassId,
                "selfieUrl" to selfieUrl,
                "timestamp" to System.currentTimeMillis()
            )
            docRef.set(attendanceData).addOnSuccessListener {
                hideLoading()
                Toast.makeText(this, "Attendance Marked ✅", Toast.LENGTH_LONG).show()
                finish()
            }.addOnFailureListener {
                hideLoading()
                retrySelfieCapture("Failed to mark attendance ❌")
            }
        }.addOnFailureListener {
            hideLoading()
            retrySelfieCapture("Error checking attendance")
        }
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
