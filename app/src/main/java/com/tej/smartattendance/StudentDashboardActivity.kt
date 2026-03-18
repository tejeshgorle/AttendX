package com.tej.smartattendance

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.Window
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import de.hdodenhof.circleimageview.CircleImageView

class StudentDashboardActivity : AppCompatActivity() {

    private lateinit var totalTV: TextView
    private lateinit var presentTV: TextView
    private lateinit var absentTV: TextView
    private lateinit var percentageTV: TextView
    private lateinit var pieChart: PieChart
    private lateinit var pieChartLarge: PieChart
    private lateinit var predictorText: TextView
    private lateinit var subjectSpinner: Spinner

    private val subjectList = mutableListOf<String>()
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private lateinit var headerProfileImage: CircleImageView
    private lateinit var headerName: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_dashboard)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        // ── Professional Exit Dialog on back press ──
        onBackPressedDispatcher.addCallback(this) {
            showExitDialog()
        }

        val scanBtn = findViewById<Button>(R.id.scanQrBtn)
        scanBtn.setOnClickListener {
            startActivity(Intent(this, ScanQRActivity::class.java))
        }

        val profileBtn = findViewById<TextView>(R.id.profileBtn)
        profileBtn.setOnClickListener {
            startActivity(Intent(this, StudentProfileActivity::class.java))
        }

        headerProfileImage = findViewById(R.id.headerProfileImage)
        headerName = findViewById(R.id.headerName)
        loadHeaderProfile()

        totalTV = findViewById(R.id.totalClasses)
        presentTV = findViewById(R.id.presentClasses)
        absentTV = findViewById(R.id.absentClasses)
        percentageTV = findViewById(R.id.percentage)
        pieChart = findViewById(R.id.pieChart)
        pieChartLarge = findViewById(R.id.pieChartLarge)
        predictorText = findViewById(R.id.predictorText)
        subjectSpinner = findViewById(R.id.subjectSpinner)

        loadSubjects()
    }

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
            setLineSpacing(0f, 1.4f)
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

    private fun updateUI(total: Int, present: Int, absent: Int, percentage: Double) {
        totalTV.text = "$total"
        presentTV.text = "$present"
        absentTV.text = "$absent"
        percentageTV.text = "%.1f%%".format(percentage)

        val predictorBadge = findViewById<TextView>(R.id.predictorBadgeText)
        val badgeLayout = findViewById<LinearLayout>(R.id.predictorBadgeLayout)
        val badgeDot = findViewById<View>(R.id.badgeDot)

        if (percentage < 75) {
            percentageTV.setTextColor(Color.parseColor("#EF4444"))
            predictorBadge.text = "Below 75% ⚠"
            predictorBadge.setTextColor(Color.parseColor("#EF4444"))
            badgeDot.setBackgroundResource(R.drawable.bg_dot_red)
            badgeLayout.setBackgroundResource(R.drawable.bg_badge_danger)
        } else {
            percentageTV.setTextColor(Color.parseColor("#0A0F1E"))
            predictorBadge.text = "On track ✓"
            predictorBadge.setTextColor(Color.parseColor("#22C55E"))
            badgeDot.setBackgroundResource(R.drawable.bg_dot_green)
            badgeLayout.setBackgroundResource(R.drawable.bg_badge_green)
        }

        findViewById<TextView>(R.id.legendPresent).text = "Present ($present)"
        findViewById<TextView>(R.id.legendAbsent).text = "Absent ($absent)"

        setupChart(present, absent)
        calculateAttendancePrediction(present, total)
    }

    private fun setupChart(present: Int, absent: Int) {
        if (present == 0 && absent == 0) {
            pieChart.clear()
            pieChartLarge.clear()
            return
        }

        val percentage = present * 100.0 / (present + absent)

        fun configureDonut(chart: PieChart, centerSize: Float, holeSize: Float) {
            chart.apply {
                setUsePercentValues(false)
                description.isEnabled = false
                isDrawHoleEnabled = true
                holeRadius = holeSize
                transparentCircleRadius = holeSize + 2f
                setHoleColor(Color.WHITE)
                centerText = "%.0f%%".format(percentage)
                setCenterTextSize(centerSize)
                setCenterTextColor(Color.parseColor("#0A0F1E"))
                legend.isEnabled = false
                setEntryLabelColor(Color.TRANSPARENT)
                setEntryLabelTextSize(0f)
                setTouchEnabled(false)
                animateY(800)
            }

            val entries = arrayListOf(
                PieEntry(present.toFloat(), "Present"),
                PieEntry(absent.toFloat(), "Absent")
            )

            val dataSet = PieDataSet(entries, "").apply {
                colors = arrayListOf(
                    Color.parseColor("#4F6EF7"),
                    Color.parseColor("#EF4444")
                )
                sliceSpace = 2f
                setDrawValues(false)
            }

            chart.data = PieData(dataSet)
            chart.invalidate()
        }

        configureDonut(pieChart, 11f, 70f)
        configureDonut(pieChartLarge, 16f, 60f)
        pieChartLarge.centerText = "%.0f%%\nAttendance".format(percentage)
    }

    private fun loadHeaderProfile() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                val name = doc.getString("name") ?: "Student"
                val imageUrl = doc.getString("profileImageUrl")
                headerName.text = name
                if (imageUrl != null) {
                    Glide.with(this).load(imageUrl).into(headerProfileImage)
                }
            }
    }

    private fun calculateAttendancePrediction(present: Int, total: Int) {
        if (total == 0) {
            predictorText.text = "No classes conducted yet."
            return
        }

        val currentPercentage = (present.toDouble() / total.toDouble()) * 100.0

        if (currentPercentage >= 75.0) {
            val allowedBunks = ((present - 0.75 * total) / 0.75).toInt()
            predictorText.text = if (allowedBunks > 0) {
                "You can miss $allowedBunks more classes and stay above 75% attendance."
            } else {
                "⚠ You cannot miss any more classes to stay above 75%."
            }
        } else {
            val requiredClasses = kotlin.math.ceil((0.75 * total - present) / 0.25).toInt()
            predictorText.text = "⚠ Attendance below 75%. Attend $requiredClasses more classes to recover."
        }
    }

    private fun loadSubjectAttendance(userId: String, subject: String) {
        db.collection("sessions")
            .whereEqualTo("classId", subject)
            .get()
            .addOnSuccessListener { sessions ->
                val totalClasses = sessions.size()
                var presentCount = 0
                var processed = 0

                if (totalClasses == 0) {
                    updateUI(0, 0, 0, 0.0)
                    return@addOnSuccessListener
                }

                for (session in sessions) {
                    session.reference.collection("attendees").document(userId).get()
                        .addOnSuccessListener { doc ->
                            if (doc.exists()) presentCount++
                            processed++
                            if (processed == totalClasses) {
                                val absent = totalClasses - presentCount
                                val percentage = (presentCount * 100.0) / totalClasses
                                updateUI(totalClasses, presentCount, absent, percentage)
                            }
                        }
                }
            }
    }

    private fun loadAttendanceHistory(userId: String, subject: String) {
        val historyContainer = findViewById<LinearLayout>(R.id.historyContainer)
        historyContainer.removeAllViews()

        db.collection("sessions")
            .whereEqualTo("classId", subject)
            .orderBy("date", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { sessions ->

                Log.d("HISTORY_DEBUG", "Sessions found: ${sessions.size()}")

                if (sessions.isEmpty) {
                    addHistoryCard(historyContainer, subject, "No records yet", false, isPlaceholder = true)
                    return@addOnSuccessListener
                }

                var processed = 0
                val totalSessions = sessions.size()
                val results = Array(totalSessions) { Triple("", "", false) }

                for ((index, session) in sessions.withIndex()) {
                    val date = session.getLong("date") ?: 0L
                    val formattedDate = java.text.SimpleDateFormat(
                        "dd MMM yyyy, hh:mm a",
                        java.util.Locale.getDefault()
                    ).format(java.util.Date(date))

                    session.reference.collection("attendees").document(userId).get()
                        .addOnSuccessListener { doc ->
                            val isPresent = doc.exists()
                            results[index] = Triple(subject, formattedDate, isPresent)
                            processed++
                            if (processed == totalSessions) {
                                historyContainer.removeAllViews()
                                for (result in results) {
                                    addHistoryCard(historyContainer, result.first, result.second, result.third)
                                }
                            }
                        }
                }
            }
    }

    private fun addHistoryCard(
        container: LinearLayout,
        subject: String,
        date: String,
        isPresent: Boolean,
        isPlaceholder: Boolean = false
    ) {
        val dp = resources.displayMetrics.density

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_stat_card)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = (8 * dp).toInt()
            layoutParams = params
            setPadding((14 * dp).toInt(), (12 * dp).toInt(), (14 * dp).toInt(), (12 * dp).toInt())
        }

        if (isPlaceholder) {
            val tv = TextView(this).apply {
                text = date
                textSize = 13f
                setTextColor(Color.parseColor("#8A96B0"))
            }
            card.addView(tv)
            container.addView(card)
            return
        }

        val dot = View(this).apply {
            val size = (10 * dp).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = (10 * dp).toInt()
            }
            setBackgroundResource(if (isPresent) R.drawable.bg_dot_green else R.drawable.bg_dot_red)
        }

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val subjectTV = TextView(this).apply {
            text = subject
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#0A0F1E"))
        }

        val dateTV = TextView(this).apply {
            text = date
            textSize = 11f
            setTextColor(Color.parseColor("#8A96B0"))
            setPadding(0, (2 * dp).toInt(), 0, 0)
        }

        textCol.addView(subjectTV)
        textCol.addView(dateTV)

        val badge = TextView(this).apply {
            text = if (isPresent) "Present" else "Absent"
            textSize = 11f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding((10 * dp).toInt(), (4 * dp).toInt(), (10 * dp).toInt(), (4 * dp).toInt())
            if (isPresent) {
                setTextColor(Color.parseColor("#22C55E"))
                setBackgroundResource(R.drawable.bg_badge_present)
            } else {
                setTextColor(Color.parseColor("#EF4444"))
                setBackgroundResource(R.drawable.bg_badge_absent)
            }
        }

        card.addView(dot)
        card.addView(textCol)
        card.addView(badge)
        container.addView(card)
    }

    private fun refreshDashboard() {
        val userId = auth.currentUser?.uid ?: return
        val subject = subjectSpinner.selectedItem?.toString() ?: subjectList.firstOrNull()
        if (subject != null) {
            loadSubjectAttendance(userId, subject)
            loadAttendanceHistory(userId, subject)
        }
        loadSubjectOverview(userId)
    }

    override fun onResume() {
        super.onResume()
        refreshDashboard()
    }

    private fun loadSubjects() {
        db.collection("sessions")
            .orderBy("date", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { sessions ->
                val subjectSet = mutableSetOf<String>()
                for (session in sessions) {
                    val subject = session.getString("classId") ?: continue
                    subjectSet.add(subject)
                }

                subjectList.clear()
                subjectList.addAll(subjectSet)

                if (subjectList.isNotEmpty()) {
                    val spinnerAdapter = ArrayAdapter(
                        this,
                        R.layout.spinner_item,
                        subjectList
                    ).apply {
                        setDropDownViewResource(R.layout.spinner_item)
                    }
                    subjectSpinner.adapter = spinnerAdapter

                    val userId = auth.currentUser?.uid ?: return@addOnSuccessListener
                    loadSubjectAttendance(userId, subjectList[0])
                    loadAttendanceHistory(userId, subjectList[0])
                    loadSubjectOverview(userId)
                }

                subjectSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                        val userId = auth.currentUser?.uid ?: return
                        val subject = subjectList[position]
                        loadSubjectAttendance(userId, subject)
                        loadAttendanceHistory(userId, subject)
                    }
                    override fun onNothingSelected(parent: AdapterView<*>) {}
                }
            }
    }

    private fun loadSubjectOverview(userId: String) {
        val subjectMap = mutableMapOf<String, Pair<Int, Int>>()

        db.collection("sessions").get()
            .addOnSuccessListener { sessions ->
                var processed = 0
                val totalSessions = sessions.size()
                if (totalSessions == 0) return@addOnSuccessListener

                for (session in sessions) {
                    val subject = session.getString("classId") ?: "Unknown"
                    session.reference.collection("attendees").document(userId).get()
                        .addOnSuccessListener { doc ->
                            val current = subjectMap[subject] ?: Pair(0, 0)
                            val total = current.first + 1
                            val present = if (doc.exists()) current.second + 1 else current.second
                            subjectMap[subject] = Pair(total, present)
                            processed++

                            if (processed == totalSessions) {
                                val overviewList = mutableListOf<SubjectAttendance>()
                                for ((name, pair) in subjectMap) {
                                    val totalClasses = pair.first
                                    val presentClasses = pair.second
                                    val percentage = (presentClasses * 100.0) / totalClasses
                                    overviewList.add(SubjectAttendance(name, totalClasses, presentClasses, percentage))
                                }

                                val recycler = findViewById<RecyclerView>(R.id.subjectRecycler)
                                recycler.layoutManager = LinearLayoutManager(
                                    this,
                                    LinearLayoutManager.HORIZONTAL,
                                    false
                                )
                                recycler.adapter = SubjectAttendanceAdapter(overviewList)
                            }
                        }
                }
            }
    }
}