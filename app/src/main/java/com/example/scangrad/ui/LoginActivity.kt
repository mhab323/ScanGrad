package com.example.scangrad.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.scangrad.databinding.ActivityLoginBinding
import com.example.scangrad.db.FirebaseManager

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var firebaseManager: FirebaseManager

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        firebaseManager.handleGoogleSignInResult(
            data      = result.data,
            onSuccess = { navigateToApp() },
            onFailed  = { error -> toast("Error: $error") }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseManager = FirebaseManager(this)

        // Skip login screen if the user is already authenticated
        if (firebaseManager.isUserLoggedIn()) {
            navigateToApp()
            return
        }

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnLogin.setOnClickListener {
            val email    = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()

            when {
                email.isEmpty()    -> toast("Please enter your email address")
                password.isEmpty() -> toast("Please enter your password")
                else               -> signIn(email, password)
            }
        }

        binding.btnGoogle.setOnClickListener {
            googleSignInLauncher.launch(firebaseManager.getGoogleSignInClient().signInIntent)
        }

        binding.tvSignUp.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }
    }

    private fun signIn(email: String, password: String) {
        binding.btnLogin.isEnabled = false
        firebaseManager.signInWithEmail(
            email     = email,
            password  = password,
            onSuccess = { navigateToApp() },
            onFailed  = { error ->
                binding.btnLogin.isEnabled = true
                toast("Error: $error")
            }
        )
    }

    private fun navigateToApp() {
        startActivity(Intent(this, HostActivity::class.java))
        finish()
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
