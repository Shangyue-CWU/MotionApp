package com.example.motionwatch


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

class AccountInfoFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_account_info, container, false)

        // Placeholders for Firebase later
        view.findViewById<TextView>(R.id.tvUsername).text = "Username: --"
        view.findViewById<TextView>(R.id.tvPassword).text = "Password: ********"

        return view
    }
}
