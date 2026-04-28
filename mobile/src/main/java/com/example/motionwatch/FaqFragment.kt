package com.example.motionwatch

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.fragment.app.Fragment

class FaqFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_faq, container, false)

        view.findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        view.findViewById<LinearLayout>(R.id.faqStartSession).setOnClickListener {
            openFaqDetail(
                "How do I start a session?",
                "To start a session, go to the Sessions page and choose the activity you want to track. After selecting the activity, press the start button. The app will begin collecting motion data from the phone or wearable device."
            )
        }

        view.findViewById<LinearLayout>(R.id.faqDashboard).setOnClickListener {
            openFaqDetail(
                "Where can I view my activity?",
                "You can view activity summaries from the Dashboard page. The dashboard shows recent motion activity, session totals, and basic information about recorded sessions."
            )
        }

        view.findViewById<LinearLayout>(R.id.faqAnalytics).setOnClickListener {
            openFaqDetail(
                "What does the Analytics page show?",
                "The Analytics page is used to display motion-related data and summaries. It helps users review activity trends and understand collected motion information more clearly."
            )
        }

        view.findViewById<LinearLayout>(R.id.faqDataStorage).setOnClickListener {
            openFaqDetail(
                "How is my data stored?",
                "Session and motion data are saved so they can be reviewed later. The app connects recorded sessions to the user account and organizes the data for dashboard and analytics use."
            )
        }

        return view
    }

    private fun openFaqDetail(title: String, answer: String) {
        val fragment = FaqDetailFragment()

        val bundle = Bundle()
        bundle.putString("title", title)
        bundle.putString("answer", answer)
        fragment.arguments = bundle

        parentFragmentManager.beginTransaction()
            .replace(R.id.nav_host, fragment)
            .addToBackStack("faq_detail")
            .commit()
    }
}