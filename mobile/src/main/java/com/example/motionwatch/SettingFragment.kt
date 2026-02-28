package com.example.motionwatch

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.materialswitch.MaterialSwitch

class SettingsFragment : Fragment() {

    private val prefsName = "motionwatch_prefs"
    private val keyPrivacy = "privacy_share_data"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        // These are LABEL TextViews in your current XML.
        // Keeping placeholders so you can see something, but you may want to remove later.
        view.findViewById<TextView>(R.id.tvWeight)?.text = getString(R.string.weight_lb)
        view.findViewById<TextView>(R.id.tvHeight)?.text = getString(R.string.height_in)
        view.findViewById<TextView>(R.id.tvAge)?.text = getString(R.string.age)
        view.findViewById<TextView>(R.id.tvSex)?.text = getString(R.string.sex)
        view.findViewById<TextView>(R.id.tvBloodType)?.text = getString(R.string.blood_type)

        // Version (placeholder)
        view.findViewById<TextView>(R.id.tvVersionBottom)?.text = "Version: 1.0"

        // Privacy toggle (saved locally)
        val prefs = requireContext().getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val switchPrivacy = view.findViewById<MaterialSwitch>(R.id.switchPrivacy)
        val tvPrivacyStatus = view.findViewById<TextView>(R.id.tvPrivacyStatus)

        val saved = prefs.getBoolean(keyPrivacy, false)
        switchPrivacy?.isChecked = saved
        tvPrivacyStatus?.text = "Share data: " + if (saved) "Yes" else "No"

        switchPrivacy?.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(keyPrivacy, isChecked).apply()
            tvPrivacyStatus?.text = "Share data: " + if (isChecked) "Yes" else "No"
        }

        // Navigation rows (UPDATED IDs)
        view.findViewById<View>(R.id.rowFaq)?.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host, FaqFragment())
                .addToBackStack(null)
                .commit()
        }

        view.findViewById<View>(R.id.rowContactAbout)?.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host, ContactAboutFragment())
                .addToBackStack(null)
                .commit()
        }

        // Top-right account page
        view.findViewById<ImageButton>(R.id.btnAccount)?.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host, AccountInfoFragment())
                .addToBackStack(null)
                .commit()
        }

        return view
    }
}