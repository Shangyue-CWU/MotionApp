package com.example.motionwatch

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.android.material.switchmaterial.SwitchMaterial
import com.example.motionwatch.firebase.UserProfileManager

/**
 * SettingsFragment: Manages user preferences, profile information, and support links.
 * Updated to handle profile inputs, data sharing toggle, and functional dropdowns.
 */
class SettingsFragment : Fragment() {

    private val prefsName = "motionwatch_prefs"
    private val keyPrivacy = "privacy_share_data"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        // Initialize UI components
        val btnAccount = view.findViewById<ImageButton>(R.id.btnAccount)
        val switchPrivacy = view.findViewById<SwitchMaterial>(R.id.switchPrivacy)
        val tvPrivacyStatus = view.findViewById<TextView>(R.id.tvPrivacyStatus)
        val btnFaq = view.findViewById<LinearLayout>(R.id.btnFaq)
        val btnContactAbout = view.findViewById<LinearLayout>(R.id.btnContactAbout)

        // Spinners (Dropdowns)
        val spinnerSex = view.findViewById<Spinner>(R.id.spinner_sex)
        val spinnerBloodType = view.findViewById<Spinner>(R.id.spinner_blood_type)
        val spinnerUnits = view.findViewById<Spinner>(R.id.spinner_units)

        //Profile input fields + Save button
        val etName = view.findViewById<EditText>(R.id.etName)
        val etAge = view.findViewById<EditText>(R.id.etAge)
        val etHeight = view.findViewById<EditText>(R.id.etHeight)
        val etWeight = view.findViewById<EditText>(R.id.etWeight)
        val btnSaveProfile = view.findViewById<Button>(R.id.btnSaveProfile)

        //Account Navigation
        btnAccount?.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host, AccountInfoFragment())
                .addToBackStack("account")
                .commit()
        }

        //Data Privacy Logic
        val prefs = requireContext().getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        if (switchPrivacy != null && tvPrivacyStatus != null) {
            val savedPrivacy = prefs.getBoolean(keyPrivacy, false)
            switchPrivacy.isChecked = savedPrivacy
            tvPrivacyStatus.text = "Share data: " + if (savedPrivacy) "Yes" else "No"

            switchPrivacy.setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean(keyPrivacy, isChecked).apply()
                tvPrivacyStatus.text = "Share data: " + if (isChecked) "Yes" else "No"
            }
        }

        // 3. Dropdown (Spinner) Initialization
        // We use explicit ArrayAdapters to ensure the dropdowns are functional and styled correctly.
        setupSpinner(spinnerSex, R.array.sex_options)
        setupSpinner(spinnerBloodType, R.array.blood_type_options)
        setupSpinner(spinnerUnits, R.array.unit_options)

        //Fetch profile when entering Settings page
        UserProfileManager.fetchProfile { profile ->
            if (profile != null) {

                profile["name"]?.let { etName.setText(it.toString()) }
                profile["age"]?.let { etAge.setText(it.toString()) }
                profile["height"]?.let { etHeight.setText(it.toString()) }
                profile["weight"]?.let { etWeight.setText(it.toString()) }

                profile["shareData"]?.let {
                    switchPrivacy?.isChecked = it as Boolean
                }
            }
        }

        //Save profile button logic
        btnSaveProfile?.setOnClickListener {

            val name = etName.text.toString()
            val age = etAge.text.toString()
            val height = etHeight.text.toString()
            val weight = etWeight.text.toString()
            val sex = spinnerSex?.selectedItem.toString()
            val bloodType = spinnerBloodType?.selectedItem.toString()
            val shareData = switchPrivacy?.isChecked ?: false
            val units = spinnerUnits?.selectedItem.toString()

            UserProfileManager.saveProfile(
                name,
                age,
                height,
                weight,
                sex,
                bloodType,
                shareData,
                units
            ) { result ->

                if (result != null) {
                    Toast.makeText(requireContext(), "Profile Saved", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Failed to Save", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 4. Support Navigation
        btnFaq?.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host, FaqFragment())
                .addToBackStack("faq")
                .commit()
        }

        btnContactAbout?.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host, ContactAboutFragment())
                .addToBackStack("about")
                .commit()
        }

        return view
    }

    /**
     * setupSpinner: Configures a Spinner with a string array resource.
     */
    private fun setupSpinner(spinner: Spinner?, arrayRes: Int) {
        spinner?.let {
            val adapter = ArrayAdapter.createFromResource(
                requireContext(),
                arrayRes,
                android.R.layout.simple_spinner_item
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            it.adapter = adapter
        }
    }
}