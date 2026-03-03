package com.example.motionwatch

import android.os.Bundle
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView
import androidx.activity.OnBackPressedCallback
import androidx.core.view.GravityCompat
import android.view.MenuItem


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
        supportActionBar?.hide()

        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.nav_view)
        toolbar = findViewById(R.id.toolbar)
        bottomNav = findViewById(R.id.bottomNav)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)

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

        navigationView.bringToFront()
        navigationView.setNavigationItemSelectedListener(this)

        bottomNav.setOnItemSelectedListener { item ->
            if (isProgrammaticNavChange) return@setOnItemSelectedListener true
            navigateTo(item.itemId, fromDrawer = false)
            true
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    finish()
                }
            }
        })

        if (savedInstanceState == null) {
            navigateTo(R.id.nav_home, fromDrawer = false)
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
        val fragment = when (itemId) {
            R.id.nav_home, R.id.nav_dashboard -> HomeFragment()
            R.id.nav_sessions, R.id.nav_session -> SessionsFragment()
            R.id.nav_analytics, R.id.nav_analysis -> AnalyticsFragment()
            R.id.nav_settings -> SettingsFragment()
            else -> null
        }

        fragment?.let {
            supportFragmentManager.beginTransaction()
                .replace(R.id.nav_host, it)
                .commit()
        }

        val bottomItemToSelect = when (itemId) {
            R.id.nav_dashboard -> R.id.nav_home
            R.id.nav_session -> R.id.nav_sessions
            R.id.nav_analysis -> R.id.nav_analytics
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