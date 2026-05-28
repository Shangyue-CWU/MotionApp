package com.example.motionwatch

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment

class FaqFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_faq, container, false)

        view.findViewById<View>(R.id.faqStartSession).setOnClickListener {
            showFaqDialog(
                "How do I start a session?",
                "Go to the Live Session screen and press the Start Session button. The app will begin collecting movement data from the phone or wearable device."
            )
        }

        view.findViewById<View>(R.id.faqActivity).setOnClickListener {
            showFaqDialog(
                "Where can I view my activity?",
                "You can view previous activity sessions from the Sessions or History page. These pages help you review past movement data."
            )
        }

        view.findViewById<View>(R.id.faqAnalytics).setOnClickListener {
            showFaqDialog(
                "What does the Analytics page show?",
                "The Analytics page summarizes activity information, motion totals, and patterns from recorded sessions."
            )
        }

        view.findViewById<View>(R.id.faqData).setOnClickListener {
            showFaqDialog(
                "How is my data stored?",
                "Session and motion data are saved so they can be reviewed later. The app connects recorded sessions to the user account and organizes the data for dashboard and analytics use."
            )
        }

        view.findViewById<View>(R.id.faqSensors).setOnClickListener {
            showFaqDialog(
                "What sensors does the app use?",
                "MotionWatch uses phone and wearable sensor data, including accelerometer and gyroscope readings, to track movement activity."
            )
        }

        view.findViewById<View>(R.id.faqReset).setOnClickListener {
            showFaqDialog(
                "How do I reset a live session?",
                "Use the Reset button on the Live Session screen to clear the current session data and return the screen to its starting state."
            )
        }

        return view
    }

    private fun showFaqDialog(title: String, message: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("CLOSE", null)
            .show()
    }
}