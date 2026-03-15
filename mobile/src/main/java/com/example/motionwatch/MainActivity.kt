package com.example.motionwatch

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var bottomNav: BottomNavigationView

    private var isProgrammaticNavChange = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Hooks
        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.nav_view)
        toolbar = findViewById(R.id.toolbar)
        bottomNav = findViewById(R.id.bottomNav)

        // Toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)

        // Drawer toggle (hamburger)
        toggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        toggle.drawerArrowDrawable.color = android.graphics.Color.BLACK

        // Drawer listener
        navigationView.bringToFront()
        navigationView.setNavigationItemSelectedListener(this)

        // Bottom nav listener
        bottomNav.setOnItemSelectedListener { item ->
            if (isProgrammaticNavChange) return@setOnItemSelectedListener true
            navigateTo(item.itemId, fromDrawer = false)
            true
        }

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

        // Start at Home
        if (savedInstanceState == null) {
            navigateTo(R.id.nav_home, fromDrawer = false)

            // Highlight Home in Bottom Nav
            isProgrammaticNavChange = true
            bottomNav.selectedItemId = R.id.nav_home
            isProgrammaticNavChange = false
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        navigateTo(item.itemId, fromDrawer = true)
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (toggle.onOptionsItemSelected(item)) return true
        return super.onOptionsItemSelected(item)
    }

    private fun navigateTo(itemId: Int, fromDrawer: Boolean) {

        // Drawer-only actions
        when (itemId) {
            R.id.nav_login -> {
                startActivity(Intent(this, SignInActivity::class.java))
                return
            }

            R.id.nav_logout -> {
                FirebaseAuth.getInstance().signOut()
                startActivity(Intent(this, SignInActivity::class.java))
                return
            }
        }

        // Fragment mapping
        val fragment = when (itemId) {

            // Bottom navigation IDs
            R.id.nav_home -> HomeFragment()
            R.id.nav_sessions -> SessionsFragment()
            R.id.nav_analytics -> AnalyticsFragment()
            R.id.nav_settings -> SettingsFragment()

            // Drawer IDs
            R.id.nav_dashboard -> HomeFragment()
            R.id.nav_session -> SessionsFragment()
            R.id.nav_analysis -> HistoryFragment()

            else -> null
        }

        fragment?.let {
            supportFragmentManager.beginTransaction()
                .replace(R.id.nav_host, it)
                .commit()
        }

        // Sync bottom nav highlight when navigation came from drawer
        if (fromDrawer) {
            val bottomItemToSelect = when (itemId) {
                R.id.nav_dashboard -> R.id.nav_home
                R.id.nav_session -> R.id.nav_sessions
                R.id.nav_analysis -> R.id.nav_analytics
                R.id.nav_settings -> R.id.nav_settings
                else -> itemId
            }

            if (bottomNav.menu.findItem(bottomItemToSelect) != null &&
                bottomNav.selectedItemId != bottomItemToSelect
            ) {
                isProgrammaticNavChange = true
                bottomNav.selectedItemId = bottomItemToSelect
                isProgrammaticNavChange = false
            }
        }
    }
}
