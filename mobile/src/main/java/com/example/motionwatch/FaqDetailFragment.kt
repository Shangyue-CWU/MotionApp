package com.example.motionwatch

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment

class FaqDetailFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_faq_detail, container, false)

        val title = arguments?.getString("title") ?: "FAQ"
        val answer = arguments?.getString("answer") ?: "No answer available."

        view.findViewById<TextView>(R.id.tvFaqTitle).text = title
        view.findViewById<TextView>(R.id.tvFaqAnswer).text = answer

        view.findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        return view
    }
}