package com.tej.smartattendance

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.Window
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import de.hdodenhof.circleimageview.CircleImageView
import java.io.File
import java.util.Calendar

class TeacherDashboardActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private val studentList = mutableListOf<StudentAttendance>()
    private lateinit var adapter: StudentAttendanceAdapter
    private lateinit var barChart: BarChart
    private lateinit var searchView: EditText
    private lateinit var filterAllBtn: TextView
    private lateinit var filterTodayBtn: TextView
    private lateinit var headerProfileImage: CircleImageView
    private lateinit var headerName: TextView
    private lateinit var headerDept: TextView
    private lateinit var headerSessions: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teacher_dashboard)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        // ── Professional Exit Dialog on back press ──
        onBackPressedDispatcher.addCallback(this) {
            showExitDialog()
        }

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        barChart = findViewById(R.id.barChart)
        searchView = findViewById(R.id.searchView)
        filterAllBtn = findViewById(R.id.filterAllBtn)
        filterTodayBtn = findViewById(R.id.filterTodayBtn)

        val sessionHistoryBtn = findViewById<LinearLayout>(R.id.sessionHistoryBtn)
        sessionHistoryBtn.setOnClickListener {
            startActivity(Intent(this, SessionHistoryActivity::class.java))
        }

        headerProfileImage = findViewById(R.id.headerProfileImage)
        headerName = findViewById(R.id.headerName)
        headerDept = findViewById(R.id.headerDept)
        headerSessions = findViewById(R.id.headerSessions)

        loadTeacherHeader()

        val generateQrBtn = findViewById<LinearLayout>(R.id.generateQrBtn)
        generateQrBtn.setOnClickListener {
            startActivity(Intent(this, GenerateQRActivity::class.java))
        }

        val exportBtn = findViewById<LinearLayout>(R.id.exportBtn)
        exportBtn.setOnClickListener {
            exportAttendance()
        }

        val profileBtn = findViewById<TextView>(R.id.profileBtn)
        profileBtn.setOnClickListener {
            startActivity(Intent(this, TeacherProfileActivity::class.java))
        }

        adapter = StudentAttendanceAdapter(studentList)
        recyclerView.adapter = adapter

        setupSearch()

        filterAllBtn.setOnClickListener { loadAttendanceData(false) }
        filterTodayBtn.setOnClickListener { loadAttendanceData(true) }

        loadAttendanceData(false)
    }

    // ── Professional custom exit dialog ──────────────────────────
    private fun showExitDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(false)

        val dp = resources.displayMetrics.density

        val view = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_stat_card)
            setPadding((24 * dp).toInt(), (28 * dp).toInt(), (24 * dp).toInt(), (24 * dp).toInt())
            gravity = Gravity.CENTER
        }

        val iconTV = TextView(this).apply {
            text = "👋"
            textSize = 40f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = (12 * dp).toInt() }
        }

        val titleTV = TextView(this).apply {
            text = "Exit AttendX?"
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#0A0F1E"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = (8 * dp).toInt() }
        }

        val msgTV = TextView(this).apply {
            text = "Are you sure you want to\nexit the app?"
            textSize = 13f
            setTextColor(Color.parseColor("#8A96B0"))
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.4f)  // ── fix: use method instead of property
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = (24 * dp).toInt() }
        }

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val stayBtn = TextView(this).apply {
            text = "Stay"
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#4F6EF7"))
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_role_card_active)
            layoutParams = LinearLayout.LayoutParams(0, (48 * dp).toInt(), 1f)
                .also { it.marginEnd = (8 * dp).toInt() }
            setOnClickListener { dialog.dismiss() }
        }

        val exitBtn = TextView(this).apply {
            text = "Exit"
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_exit_btn)
            layoutParams = LinearLayout.LayoutParams(0, (48 * dp).toInt(), 1f)
            setOnClickListener {
                dialog.dismiss()
                finishAffinity()
            }
        }

        btnRow.addView(stayBtn)
        btnRow.addView(exitBtn)
        view.addView(iconTV)
        view.addView(titleTV)
        view.addView(msgTV)
        view.addView(btnRow)

        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (300 * dp).toInt(),
            android.view.WindowManager.LayoutParams.WRAP_CONTENT
        )
        dialog.show()
    }
    // ─────────────────────────────────────────────────────────────

    private fun setupSearch() {
        searchView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun loadTeacherHeader() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("users")
            .document(userId)
            .get()
            .addOnSuccessListener { doc ->
                val name = doc.getString("name") ?: "Teacher"
                val dept = doc.getString("department") ?: "-"
                val imageUrl = doc.getString("profileImageUrl")

                headerName.text = name
                headerDept.text = "Department: $dept"

                if (imageUrl != null) {
                    Glide.with(this)
                        .load(imageUrl)
                        .placeholder(R.drawable.ic_default_avatar)
                        .error(R.drawable.ic_default_avatar)
                        .circleCrop()
                        .into(headerProfileImage)
                }
            }

        db.collection("sessions")
            .whereEqualTo("createdBy", userId)
            .get()
            .addOnSuccessListener { sessions ->
                val sessionCount = sessions.size()
                headerSessions.text = "Sessions Conducted: $sessionCount"
                findViewById<TextView>(R.id.totalSessionsTV).text = "$sessionCount"
            }
    }

    private fun loadAttendanceData(filterToday: Boolean) {
        val query = if (filterToday) {
            val startOfDay = getStartOfDay()
            db.collection("sessions").whereGreaterThanOrEqualTo("date", startOfDay)
        } else {
            db.collection("sessions")
        }

        query.get().addOnSuccessListener { sessions ->
            val totalClasses = sessions.size()
            Log.d("DASHBOARD_DEBUG", "Sessions query returned: $totalClasses sessions")

            val studentPresenceMap = mutableMapOf<String, Int>()

            if (totalClasses == 0) {
                calculatePercentages(0, studentPresenceMap)
                return@addOnSuccessListener
            }

            var processedSessions = 0
            for (session in sessions.documents) {
                db.collection("sessions").document(session.id)
                    .collection("attendees").get()
                    .addOnSuccessListener { attendees ->
                        for (doc in attendees.documents) {
                            val uid = doc.id
                            studentPresenceMap[uid] = (studentPresenceMap[uid] ?: 0) + 1
                        }
                        processedSessions++
                        if (processedSessions == totalClasses) {
                            calculatePercentages(totalClasses, studentPresenceMap)
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("DASHBOARD_ERROR", "Error fetching attendees for ${session.id}", e)
                        processedSessions++
                        if (processedSessions == totalClasses) {
                            calculatePercentages(totalClasses, studentPresenceMap)
                        }
                    }
            }
        }.addOnFailureListener { e ->
            Log.e("DASHBOARD_ERROR", "Failed to load sessions", e)
            Toast.makeText(this, "Error loading sessions", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportAttendance() {
        val csvData = StringBuilder()
        csvData.append("Name,Date,Status\n")

        db.collection("sessions")
            .get()
            .addOnSuccessListener { sessions ->
                var processedSessions = 0
                val totalSessions = sessions.size()

                if (totalSessions == 0) {
                    Toast.makeText(this, "No sessions to export", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                for (session in sessions) {
                    val sessionDate = session.getLong("date") ?: 0L
                    val formattedDate = java.text.SimpleDateFormat(
                        "dd MMM yyyy",
                        java.util.Locale.getDefault()
                    ).format(java.util.Date(sessionDate))

                    session.reference.collection("attendees").get()
                        .addOnSuccessListener { attendees ->
                            for (doc in attendees) {
                                val uid = doc.id
                                db.collection("users").document(uid).get()
                                    .addOnSuccessListener { user ->
                                        val name = user.getString("name") ?: "Unknown"
                                        csvData.append("$name,$formattedDate,Present\n")
                                    }
                            }
                            processedSessions++
                            if (processedSessions == totalSessions) {
                                saveCSV(csvData.toString())
                            }
                        }
                }
            }
    }

    private fun saveCSV(data: String) {
        try {
            val fileName = "attendance_${System.currentTimeMillis()}.csv"
            val file = File(getExternalFilesDir(null), fileName)
            file.writeText(data)
            Toast.makeText(this, "Attendance exported: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed", Toast.LENGTH_LONG).show()
        }
    }

    private fun calculatePercentages(totalClasses: Int, studentPresenceMap: Map<String, Int>) {
        Log.d("DASHBOARD_DEBUG", "Starting calculatePercentages. Total classes: $totalClasses")

        db.collection("users").whereEqualTo("role", "student").get()
            .addOnSuccessListener { users ->
                studentList.clear()

                for (user in users.documents) {
                    val uid = user.id
                    val name = user.getString("name") ?: "Unknown"
                    val profileImageUrl = user.getString("profileImageUrl") ?: ""
                    val present = studentPresenceMap[uid] ?: 0
                    val absent = totalClasses - present
                    val percentage = if (totalClasses == 0) 0.0 else (present * 100.0) / totalClasses
                    studentList.add(
                        StudentAttendance(uid, name, profileImageUrl, totalClasses, present, absent, percentage)
                    )
                }

                Log.d("DASHBOARD_DEBUG", "Processed ${studentList.size} students")

                runOnUiThread {
                    adapter.updateData(ArrayList(studentList))
                    setupChart()

                    findViewById<TextView>(R.id.totalStudentsTV).text = "${studentList.size}"
                    if (studentList.isNotEmpty()) {
                        val avg = studentList.sumOf { it.percentage } / studentList.size
                        findViewById<TextView>(R.id.avgRateTV).text = "%.0f%%".format(avg)
                    }

                    if (studentList.isEmpty()) {
                        Toast.makeText(this, "No student data found", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("DASHBOARD_ERROR", "Error fetching student users", e)
                Toast.makeText(this, "Failed to load students", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupChart() {
        if (studentList.isEmpty()) {
            barChart.clear()
            barChart.invalidate()
            return
        }

        val entries = ArrayList<BarEntry>()
        val labels = ArrayList<String>()

        studentList.forEachIndexed { index, student ->
            entries.add(BarEntry(index.toFloat(), student.percentage.toFloat()))
            labels.add(student.name)
        }

        val dataSet = BarDataSet(entries, "Attendance %").apply {
            val colors = studentList.map { student ->
                when {
                    student.percentage >= 75 -> android.graphics.Color.parseColor("#4F6EF7")
                    student.percentage >= 60 -> android.graphics.Color.parseColor("#F59E0B")
                    else -> android.graphics.Color.parseColor("#EF4444")
                }
            }
            setColors(colors)
            valueTextColor = android.graphics.Color.parseColor("#0A0F1E")
            valueTextSize = 10f
        }

        val data = BarData(dataSet)
        data.barWidth = 0.6f
        barChart.data = data

        barChart.apply {
            xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            xAxis.granularity = 1f
            xAxis.labelRotationAngle = -30f
            xAxis.position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
            xAxis.textColor = android.graphics.Color.parseColor("#4A5568")
            axisLeft.textColor = android.graphics.Color.parseColor("#4A5568")
            axisLeft.axisMinimum = 0f
            axisLeft.axisMaximum = 100f
            axisRight.isEnabled = false
            description.isEnabled = false
            legend.isEnabled = false
            setBackgroundColor(android.graphics.Color.WHITE)
            animateY(1000)
            invalidate()
        }
    }

    private fun getStartOfDay(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}