package com.example.scangrad.ui

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.example.scangrad.databinding.ActivitySplashBinding

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Animate the progress bar filling up, then move on to the login flow.
        ObjectAnimator.ofInt(binding.progressBar, "progress", 0, 100).apply {
            duration = SPLASH_DURATION_MS
            interpolator = DecelerateInterpolator()
            start()
        }

        binding.root.postDelayed({ navigateToLogin() }, SPLASH_DURATION_MS)
    }

    private fun navigateToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    companion object {
        private const val SPLASH_DURATION_MS = 2000L
    }
}
