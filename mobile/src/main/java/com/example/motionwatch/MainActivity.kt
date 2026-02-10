package com.example.motionwatch

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toolbar: Toolbar
    private lateinit var toggle: ActionBarDrawerToggle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

       // Drawer Layout
        setContentView(R.layout.activity_main)

        // Hooks
        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.nav_view)
        toolbar = findViewById(R.id.toolbar)

        // Toolbar
        setSupportActionBar(toolbar)

        // Hide or show items in the drawer
        //navigationView.menu.findItem(R.id.nav_login)?.isVisible = false

        // Drawer toggle (hamburger)
        val toggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        navigationView.bringToFront()
        navigationView.setNavigationItemSelectedListener(this)
        navigationView.setCheckedItem(R.id.nav_session)

        // Back button: close drawer first
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    finish()
                }
            }
        })

        // Insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Bottom nav + fragments
        val bottom = findViewById<BottomNavigationView>(R.id.bottom_nav)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.nav_host, CollectFragment())
                .commit()
        }

        bottom.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_collect -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.nav_host, CollectFragment())
                        .commit()
                    true
                }
                R.id.nav_history -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.nav_host, HistoryFragment())
                        .commit()
                    true
                }
                else -> false
            }
        }
    }


    // Drawer item clicks
    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        drawerLayout.closeDrawer(GravityCompat.START)

        drawerLayout.post {
            when (id) {
                R.id.nav_session -> {
                    startActivity(Intent(this, MainActivity::class.java))
                }


                 R.id.nav_dashboard -> startActivity(Intent(this, DashBoard::class.java))
                 R.id.nav_analysis -> startActivity(Intent(this, Analysis::class.java))
                 R.id.nav_settings -> startActivity(Intent(this, Settings::class.java))


                R.id.nav_share -> {
                    Toast.makeText(this, "Share", Toast.LENGTH_SHORT).show()
                }

                R.id.nav_rate -> {
                    Toast.makeText(this, "Rate", Toast.LENGTH_SHORT).show()
                }

                R.id.nav_login -> {
                    startActivity(Intent(this, SignInActivity::class.java))
                }

                R.id.nav_logout -> {
                    startActivity(Intent(this, SignInActivity::class.java))
                }

                else -> {
                    Toast.makeText(
                        this,
                        "Unhandled: ${resources.getResourceEntryName(id)}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        return true
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (toggle.onOptionsItemSelected(item)) {
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
