package com.tej.smartattendance

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.SearchView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar

class TeacherDashboardActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var db: FirebaseFirestore
    private val studentList = mutableListOf<StudentAttendance>()
    private lateinit var adapter: StudentAttendanceAdapter
    private lateinit var barChart: BarChart
    private lateinit var searchView: SearchView
    private lateinit var filterAllBtn: Button
    private lateinit var filterTodayBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teacher_dashboard)

        db = FirebaseFirestore.getInstance()

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        barChart = findViewById(R.id.barChart)
        searchView = findViewById(R.id.searchView)

        filterAllBtn = findViewById(R.id.filterAllBtn)
        filterTodayBtn = findViewById(R.id.filterTodayBtn)

        val sessionHistoryBtn = findViewById<Button>(R.id.sessionHistoryBtn)

        sessionHistoryBtn.setOnClickListener {

            val intent = Intent(this, SessionHistoryActivity::class.java)
            startActivity(intent)

        }

        val generateQrBtn = findViewById<Button>(R.id.generateQrBtn)

        generateQrBtn.setOnClickListener {
            startActivity(Intent(this, GenerateQRActivity::class.java))
        }

        adapter = StudentAttendanceAdapter(studentList)
        recyclerView.adapter = adapter

        setupSearch()

        filterAllBtn.setOnClickListener { loadAttendanceData(false) }
        filterTodayBtn.setOnClickListener { loadAttendanceData(true) }

        loadAttendanceData(false)
    }

    private fun setupSearch() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                adapter.filter(newText ?: "")
                return true
            }
        })
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
                            val userId = doc.id
                            studentPresenceMap[userId] = (studentPresenceMap[userId] ?: 0) + 1
                        }
                        processedSessions++
                        if (processedSessions == totalClasses) {
                            calculatePercentages(totalClasses, studentPresenceMap)
                        }
                    }
                    .addOnFailureListener { e ->
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

    private fun calculatePercentages(totalClasses: Int, studentPresenceMap: Map<String, Int>) {
        Log.d("DASHBOARD_DEBUG", "Starting calculatePercentages. Total classes: $totalClasses")
        
        db.collection("users").whereEqualTo("role", "student").get()
            .addOnSuccessListener { users ->
                studentList.clear()
                
                for (user in users.documents) {
                    val userId = user.id
                    val name = user.getString("name") ?: "Unknown"
                    val present = studentPresenceMap[userId] ?: 0
                    val absent = totalClasses - present
                    val percentage = if (totalClasses == 0) 0.0 else (present * 100.0) / totalClasses

                    studentList.add(StudentAttendance(userId, name, totalClasses, present, absent, percentage))
                }

                Log.d("DASHBOARD_DEBUG", "Processed ${studentList.size} students")

                runOnUiThread {
                    // Fix: Pass a COPY of the list to prevent the adapter from clearing the activity's list
                    adapter.updateData(ArrayList(studentList))
                    
                    setupChart()
                    
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

        val dataSet = BarDataSet(entries, "Attendance %")
        val data = BarData(dataSet)
        barChart.data = data

        val xAxis = barChart.xAxis
        xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        xAxis.granularity = 1f
        xAxis.labelRotationAngle = -30f
        xAxis.position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM

        barChart.axisRight.isEnabled = false
        barChart.description.isEnabled = false
        barChart.animateY(1000)
        barChart.invalidate()
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
