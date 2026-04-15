package com.example.scangrad.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.scangrad.databinding.ActivityLoginBinding
import com.example.scangrad.db.FirebaseManager
import com.google.android.gms.auth.api.signin.GoogleSignIn

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var firebaseManager: FirebaseManager

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        firebaseManager.handleGoogleSignInResult(
            data      = result.data,
            onSuccess = { navigateToDashboard() },
            onFailed  = { error ->
                Toast.makeText(this, "Error: $error", Toast.LENGTH_LONG).show()
            }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseManager = FirebaseManager(this)

        if (firebaseManager.isUserLoggedIn()) {
            navigateToDashboard()
            return
        }

        setupClickListeners()
    }

    private fun navigateToDashboard() {
        startActivity(Intent(this, HostActivity::class.java))
        finish()
    }

    private fun sendOTP(phoneNumber: String) {
        firebaseManager.sendPhoneOtp(
            phoneNumber         = phoneNumber,
            onCodeSent          = {
                Toast.makeText(this, "SMS Sent! Check your messages.", Toast.LENGTH_SHORT).show()
                // TODO: Change UI to show an EditText for the 6-digit code
            },
            onVerificationFailed = { errorMessage ->
                Toast.makeText(this, "Error: $errorMessage", Toast.LENGTH_LONG).show()
            },
            onLoginSuccess      = { navigateToDashboard() }
        )
    }

    private fun setupClickListeners() {
        binding.btnLogin.setOnClickListener {
            val phoneNumber = binding.etPhone.text.toString().trim()
            if (phoneNumber.isNotEmpty()) {
                sendOTP(phoneNumber)
            } else {
                Toast.makeText(this, "Please enter a valid phone number", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnGoogle.setOnClickListener {
            val signInIntent = firebaseManager.getGoogleSignInClient().signInIntent
            googleSignInLauncher.launch(signInIntent)
        }

        binding.tvSignUp.setOnClickListener {
            Toast.makeText(this, "Navigate to Sign Up", Toast.LENGTH_SHORT).show()
        }
    }
}
