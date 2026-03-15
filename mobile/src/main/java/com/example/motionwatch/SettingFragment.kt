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
 */
class SettingsFragment : Fragment() {

    private val prefsName = "motionwatch_prefs"
    private val keyPrivacy = "privacy_share_data"

    // References for refresh
    private var etNameRef: EditText? = null
    private var etAgeRef: EditText? = null
    private var etHeightRef: EditText? = null
    private var etWeightRef: EditText? = null
    private var switchPrivacyRef: SwitchMaterial? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        // UI components
        val btnAccount = view.findViewById<ImageButton>(R.id.btnAccount)
        val switchPrivacy = view.findViewById<SwitchMaterial>(R.id.switchPrivacy)
        val tvPrivacyStatus = view.findViewById<TextView>(R.id.tvPrivacyStatus)
        val btnFaq = view.findViewById<LinearLayout>(R.id.btnFaq)
        val btnContactAbout = view.findViewById<LinearLayout>(R.id.btnContactAbout)

        val spinnerSex = view.findViewById<Spinner>(R.id.spinner_sex)
        val spinnerBloodType = view.findViewById<Spinner>(R.id.spinner_blood_type)
        val spinnerUnits = view.findViewById<Spinner>(R.id.spinner_units)

        val etName = view.findViewById<EditText>(R.id.etName)
        val etAge = view.findViewById<EditText>(R.id.etAge)
        val etHeight = view.findViewById<EditText>(R.id.etHeight)
        val etWeight = view.findViewById<EditText>(R.id.etWeight)
        val btnSaveProfile = view.findViewById<Button>(R.id.btnSaveProfile)

        // Store references for refresh
        etNameRef = etName
        etAgeRef = etAge
        etHeightRef = etHeight
        etWeightRef = etWeight
        switchPrivacyRef = switchPrivacy

        // 1. Account Navigation
        btnAccount?.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host, AccountInfoFragment())
                .addToBackStack("account")
                .commit()
        }

        // 2. Data Privacy Logic
        val prefs = requireContext().getSharedPreferences(prefsName, Context.MODE_PRIVATE)

        val savedPrivacy = prefs.getBoolean(keyPrivacy, false)
        switchPrivacy?.isChecked = savedPrivacy
        tvPrivacyStatus?.text = "Share data: " + if (savedPrivacy) "Yes" else "No"

        switchPrivacy?.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(keyPrivacy, isChecked).apply()
            tvPrivacyStatus?.text = "Share data: " + if (isChecked) "Yes" else "No"
        }

        // 3. Dropdown initialization
        setupSpinner(spinnerSex, R.array.sex_options)
        setupSpinner(spinnerBloodType, R.array.blood_type_options)
        setupSpinner(spinnerUnits, R.array.unit_options)

        // Fetch existing profile
        UserProfileManager.fetchProfile { profile ->
            if (profile != null) {
                profile["name"]?.let { etName.setText(it.toString()) }
                profile["age"]?.let { etAge.setText(it.toString()) }
                profile["height"]?.let { etHeight.setText(it.toString()) }
                profile["weight"]?.let { etWeight.setText(it.toString()) }
                profile["shareData"]?.let { switchPrivacy?.isChecked = it as Boolean }
            }
        }

        // Save profile button
        btnSaveProfile?.setOnClickListener {

            val name = etName.text.toString().trim()
            val ageStr = etAge.text.toString().trim()
            val heightStr = etHeight.text.toString().trim()
            val weightStr = etWeight.text.toString().trim()

            val age = ageStr.toIntOrNull()
            val height = heightStr.toFloatOrNull()
            val weight = weightStr.toFloatOrNull()

            if (name.isEmpty()) {
                Toast.makeText(requireContext(), "Name required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (age == null || age !in 1..120) {
                Toast.makeText(requireContext(), "Age must be 1–120", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (height == null || height !in 24f..96f) {
                Toast.makeText(requireContext(), "Height must be 24–96 in", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (weight == null || weight !in 30f..700f) {
                Toast.makeText(requireContext(), "Weight must be 30–700 lb", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val sex = spinnerSex.selectedItem.toString()
            val bloodType = spinnerBloodType.selectedItem.toString()
            val shareData = switchPrivacy.isChecked
            val units = spinnerUnits.selectedItem.toString()

            UserProfileManager.saveProfile(
                name,
                ageStr,
                heightStr,
                weightStr,
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

    override fun onResume() {
        super.onResume()

        UserProfileManager.fetchProfile { profile ->
            if (profile != null) {
                profile["name"]?.let { etNameRef?.setText(it.toString()) }
                profile["age"]?.let { etAgeRef?.setText(it.toString()) }
                profile["height"]?.let { etHeightRef?.setText(it.toString()) }
                profile["weight"]?.let { etWeightRef?.setText(it.toString()) }
                profile["shareData"]?.let { switchPrivacyRef?.isChecked = it as Boolean }
            }
        }
    }

    /**
     * Setup spinner helper
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
