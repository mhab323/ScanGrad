package com.example.scangrad.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.scangrad.databinding.ActivitySignupBinding
import com.example.scangrad.db.FirebaseManager

class SignupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupBinding
    private lateinit var firebaseManager: FirebaseManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseManager = FirebaseManager(this)

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnCreateAccount.setOnClickListener {
            val name            = binding.etName.text.toString().trim()
            val email           = binding.etEmail.text.toString().trim()
            val password        = binding.etPassword.text.toString()
            val confirmPassword = binding.etConfirmPassword.text.toString()

            when {
                name.isEmpty()                    -> toast("Please enter your full name")
                email.isEmpty()                   -> toast("Please enter your email address")
                password.length < 6               -> toast("Password must be at least 6 characters")
                password != confirmPassword       -> toast("Passwords do not match")
                else                              -> createAccount(name, email, password)
            }
        }

        binding.tvLogIn.setOnClickListener {
            // Return to LoginActivity (already on the back stack)
            finish()
        }
    }

    private fun createAccount(name: String, email: String, password: String) {
        binding.btnCreateAccount.isEnabled = false
        firebaseManager.signUpWithEmail(
            name      = name,
            email     = email,
            password  = password,
            onSuccess = { navigateToApp() },
            onFailed  = { error ->
                binding.btnCreateAccount.isEnabled = true
                toast("Error: $error")
            }
        )
    }

    private fun navigateToApp() {
        // Clear the entire back stack so the user cannot press Back back to auth screens
        val intent = Intent(this, HostActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
