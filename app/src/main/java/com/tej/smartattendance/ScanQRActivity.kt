package com.tej.smartattendance

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import org.json.JSONObject
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@OptIn(ExperimentalGetImage::class)
class ScanQRActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var isScanned = false

    // Classroom Location (Change to your real classroom later)
    private val classroomLat = 17.3850
    private val classroomLng = 78.4867
    private val allowedRadius = 50f // meters

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan_qr)

        previewView = findViewById(R.id.previewView)
        cameraExecutor = Executors.newSingleThreadExecutor()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

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
        ) == PackageManager.PERMISSION_GRANTED
                &&
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

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )
            } catch (e: Exception) {
                Log.e("CameraX", "Camera binding failed", e)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImageProxy(imageProxy: ImageProxy) {

        if (isScanned) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
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
            .addOnFailureListener {
                Log.e("QR", "Scan failed", it)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun validateQR(qrData: String) {

        try {
            val json = JSONObject(qrData)
            val expiry = json.getLong("expiry")
            val currentTime = System.currentTimeMillis()

            if (currentTime > expiry) {
                Toast.makeText(this, "QR Code Expired ❌", Toast.LENGTH_LONG).show()
                isScanned = false
                return
            }

            checkLocationAndMarkAttendance()

        } catch (e: Exception) {
            Toast.makeText(this, "Invalid QR Code ❌", Toast.LENGTH_LONG).show()
            isScanned = false
        }
    }

    private fun checkLocationAndMarkAttendance() {

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                102
            )
            isScanned = false
            return
        }

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

                        val distanceInMeters = results[0]

                        if (distanceInMeters <= allowedRadius) {
                            Toast.makeText(
                                this@ScanQRActivity,
                                "Attendance Marked Successfully ✅",
                                Toast.LENGTH_LONG
                            ).show()

                            finish() // Close scanner after success

                        } else {
                            Toast.makeText(
                                this@ScanQRActivity,
                                "You are not inside classroom ❌",
                                Toast.LENGTH_LONG
                            ).show()
                            isScanned = false
                        }

                    } else {
                        Toast.makeText(
                            this@ScanQRActivity,
                            "Unable to get location ❌",
                            Toast.LENGTH_LONG
                        ).show()
                        isScanned = false
                    }

                    fusedLocationClient.removeLocationUpdates(this)
                }
            },
            mainLooper
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}