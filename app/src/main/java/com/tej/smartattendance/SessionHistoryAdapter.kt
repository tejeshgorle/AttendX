package com.tej.smartattendance

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView

data class SessionHistoryItem(
    val date: String,
    val subject: String,
    val studentCount: Int
)

class SessionHistoryAdapter(
    context: Context,
    private val sessions: List<SessionHistoryItem>
) : ArrayAdapter<SessionHistoryItem>(context, 0, sessions) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_session_history, parent, false)

        val session = sessions[position]

        view.findViewById<TextView>(R.id.sessionDate).text = session.date
        view.findViewById<TextView>(R.id.sessionSubject).text = session.subject
        view.findViewById<TextView>(R.id.studentCount).text = "${session.studentCount} students"

        return view
    }
}