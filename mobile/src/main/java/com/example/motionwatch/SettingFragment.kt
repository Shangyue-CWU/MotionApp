package com.example.motionwatch

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch

class SettingsFragment : Fragment() {

    private val prefsName = "motionwatch_prefs"
    private val keyPrivacy = "privacy_share_data"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        // Profile placeholders (Firebase later)
        view.findViewById<TextView>(R.id.tvWeight).text = "Weight: -- lb"
        view.findViewById<TextView>(R.id.tvHeight).text = "Height: -- in"
        view.findViewById<TextView>(R.id.tvAge).text = "Age: --"
        view.findViewById<TextView>(R.id.tvSex).text = "Sex: --"
        view.findViewById<TextView>(R.id.tvBloodType).text = "Blood Type: --"

        // Version (placeholder for now)
        view.findViewById<TextView>(R.id.tvVersionBottom).text = "Version: 1.0"

        // Privacy toggle (saved locally)
        val prefs = requireContext().getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val switchPrivacy = view.findViewById<MaterialSwitch>(R.id.switchPrivacy)
        val tvPrivacyStatus = view.findViewById<TextView>(R.id.tvPrivacyStatus)

        val saved = prefs.getBoolean(keyPrivacy, false)
        switchPrivacy.isChecked = saved
        tvPrivacyStatus.text = "Share data: " + if (saved) "Yes" else "No"

        switchPrivacy.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(keyPrivacy, isChecked).apply()
            tvPrivacyStatus.text = "Share data: " + if (isChecked) "Yes" else "No"
        }

        // Navigation buttons
        view.findViewById<MaterialButton>(R.id.btnFaq).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host, FaqFragment())
                .addToBackStack(null)
                .commit()
        }

        view.findViewById<MaterialButton>(R.id.btnContactAbout).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host, ContactAboutFragment())
                .addToBackStack(null)
                .commit()
        }

        // Top-right account page
        view.findViewById<ImageButton>(R.id.btnAccount).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host, AccountInfoFragment())
                .addToBackStack(null)
                .commit()
        }

        return view
    }
}
