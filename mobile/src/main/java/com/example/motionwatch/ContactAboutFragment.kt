package com.example.motionwatch

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment

class ContactAboutFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_contact_about, container, false)

        view.findViewById<View>(R.id.aliMember).setOnClickListener {
            showMemberDialog(
                "Ali Nizar",
                "ali.nizar@cwu.edu",
                "Senior Computer Science major. Worked on Settings, FAQ, About page updates, and navigation improvements."
            )
        }

        view.findViewById<View>(R.id.kobeMember).setOnClickListener {
            showMemberDialog(
                "Kobe Takemura",
                "kobe.takemura@cwu.edu",
                "Senior Computer Science major. Worked on profile settings, data sync, and measurement unit updates."
            )
        }

        view.findViewById<View>(R.id.calvinMember).setOnClickListener {
            showMemberDialog(
                "Calvin Giles",
                "calvin.giles@cwu.edu",
                "Senior Computer Science major. Worked on app testing, live session features, and project support."
            )
        }

        view.findViewById<View>(R.id.tashaMember).setOnClickListener {
            showMemberDialog(
                "Tasha Peterson",
                "tasha.peterson@cwu.edu",
                "Computer Science major with one year left. Worked on documentation, UI support, and project management."
            )
        }

        view.findViewById<View>(R.id.cindyMember).setOnClickListener {
            showMemberDialog(
                "Cindy Chitsuwa",
                "cindy.chitsuwa@cwu.edu",
                "Senior Computer Science major. Worked on About page updates, icons, detection totals, and UI improvements."
            )
        }

        return view
    }

    private fun showMemberDialog(name: String, email: String, description: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(name)
            .setMessage("$email\n\n$description")
            .setPositiveButton("CLOSE", null)
            .show()
    }
}