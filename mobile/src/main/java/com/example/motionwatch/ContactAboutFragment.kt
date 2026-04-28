package com.example.motionwatch

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment

class ContactAboutFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view = inflater.inflate(R.layout.fragment_contact_about, container, false)

        // Back button
        view.findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // Ali
        view.findViewById<LinearLayout>(R.id.cardAli).setOnClickListener {
            showMemberDialog(
                name = "Ali Nizar",
                email = "ali.nizar@cwu.edu",
                intro = "Worked on Settings, FAQ, About page updates, and navigation improvements.",
                photoResId = android.R.drawable.ic_menu_myplaces
            )
        }

        // Kobe
        view.findViewById<LinearLayout>(R.id.cardKobe).setOnClickListener {
            showMemberDialog(
                name = "Kobe Takemura",
                email = "kobe.takemura@cwu.edu",
                intro = "Worked on Firebase backend, data storage, and syncing.",
                photoResId = android.R.drawable.ic_menu_myplaces
            )
        }

        // Calvin
        view.findViewById<LinearLayout>(R.id.cardCalvin).setOnClickListener {
            showMemberDialog(
                name = "Calvin Giles",
                email = "calvin.giles@cwu.edu",
                intro = "Worked on testing, debugging, and improving application performance.",
                photoResId = android.R.drawable.ic_menu_myplaces
            )
        }

        // Tasha
        view.findViewById<LinearLayout>(R.id.cardTasha).setOnClickListener {
            showMemberDialog(
                name = "Tasha Peterson",
                email = "tasha.peterson@cwu.edu",
                intro = "Worked on UI layouts, sign-in pages, and app design.",
                photoResId = android.R.drawable.ic_menu_myplaces
            )
        }

        // Cindy
        view.findViewById<LinearLayout>(R.id.cardCindy).setOnClickListener {
            showMemberDialog(
                name = "Cindy Chitsuwa",
                email = "cindy.chitsuwa@cwu.edu",
                intro = "Worked on documentation, planning, and project requirements.",
                photoResId = android.R.drawable.ic_menu_myplaces
            )
        }

        return view
    }

    private fun showMemberDialog(
        name: String,
        email: String,
        intro: String,
        photoResId: Int
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_team_member, null)

        dialogView.findViewById<ImageView>(R.id.imgMemberPhoto)
            .setImageResource(photoResId)

        dialogView.findViewById<TextView>(R.id.tvMemberName).text = name
        dialogView.findViewById<TextView>(R.id.tvMemberEmail).text = email
        dialogView.findViewById<TextView>(R.id.tvMemberIntro).text = intro

        AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .show()
    }
}