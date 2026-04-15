package com.example.scangrad.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.scangrad.R
import com.example.scangrad.databinding.ActivityHostBinding
import com.example.scangrad.db.FirebaseManager

class HostActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHostBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // TODO: re-enable auth gate before release
        // if (!FirebaseManager(this).isUserLoggedIn()) {
        //     startActivity(Intent(this, LoginActivity::class.java))
        //     finish()
        //     return
        // }

        binding = ActivityHostBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupInitialFragment(savedInstanceState)
        setupBottomNavigation()
    }

    private fun setupInitialFragment(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            replaceFragment(DashboardFragment.newInstance())
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    replaceFragment(DashboardFragment.newInstance())
                    true
                }
                R.id.nav_history -> {
                    replaceFragment(HistoryFragment.newInstance())
                    true
                }
                R.id.nav_settings -> {
                    replaceFragment(SettingsFragment.newInstance())
                    true
                }
                else -> false
            }
        }
    }

    /** Push a fragment onto the back stack (used for Camera → Validation flow). */
    fun navigateTo(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    /** Swap root tabs without adding to the back stack. */
    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}