package com.example.motionwatch

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsFragment : Fragment() {

    private val prefsName = "motionwatch_prefs"
    private val keyPrivacy = "privacy_share_data"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        fun <T : View> req(id: Int, name: String): T? {
            val v = view.findViewById<T>(id)
            if (v == null) Log.e("SettingsFragment", "Missing view id in fragment_settings.xml: $name")
            return v
        }

        // Top-right account button
        req<ImageButton>(R.id.btnAccount, "btnAccount")?.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host, AccountInfoFragment())
                .addToBackStack("account")
                .commit()
        }

        // Profile placeholders
        req<TextView>(R.id.tvWeight, "tvWeight")?.text = "Weight: -- lb"
        req<TextView>(R.id.tvHeight, "tvHeight")?.text = "Height: -- in"
        req<TextView>(R.id.tvAge, "tvAge")?.text = "Age: --"
        req<TextView>(R.id.tvSex, "tvSex")?.text = "Sex: --"
        req<TextView>(R.id.tvBloodType, "tvBloodType")?.text = "Blood Type: --"
        req<TextView>(R.id.tvVersionBottom, "tvVersionBottom")?.text = "Version: 1.0"

        // Privacy toggle
        val prefs = requireContext().getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val switchPrivacy = req<SwitchMaterial>(R.id.switchPrivacy, "switchPrivacy")
        val tvPrivacyStatus = req<TextView>(R.id.tvPrivacyStatus, "tvPrivacyStatus")

        if (switchPrivacy != null && tvPrivacyStatus != null) {
            val savedPrivacy = prefs.getBoolean(keyPrivacy, false)
            switchPrivacy.isChecked = savedPrivacy
            tvPrivacyStatus.text = "Share data: " + if (savedPrivacy) "Yes" else "No"

            switchPrivacy.setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean(keyPrivacy, isChecked).apply()
                tvPrivacyStatus.text = "Share data: " + if (isChecked) "Yes" else "No"
            }
        }

        // Buttons
        req<MaterialButton>(R.id.btnFaq, "btnFaq")?.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host, FaqFragment())
                .addToBackStack("faq")
                .commit()
        }

        req<MaterialButton>(R.id.btnHistoryFromSettings, "btnHistoryFromSettings")?.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host, HistoryPageFragment())
                .addToBackStack("history")
                .commit()
        }

        req<MaterialButton>(R.id.btnContactAbout, "btnContactAbout")?.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host, ContactAboutFragment())
                .addToBackStack("about")
                .commit()
        }

        return view
    }
}