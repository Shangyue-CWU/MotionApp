package com.example.motionwatch

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton

class SessionsFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_sessions, container, false)

        view.findViewById<MaterialButton>(R.id.btnHistory).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host, HistoryPageFragment())
                .addToBackStack("history")
                .commit()
        }

        return view
    }
}