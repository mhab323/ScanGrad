package com.example.scangrad.ui

import android.app.Application
import android.util.Log
import com.example.scangrad.network.FastApiService
import com.google.firebase.firestore.FirebaseFirestore

class ScanGradApp : Application() {

    override fun onCreate() {
        super.onCreate()
        connectToPythonServer()
    }

    private fun connectToPythonServer() {
        val db = FirebaseFirestore.getInstance()

        db.collection("server_config").document("api_settings")
            .get()
            .addOnSuccessListener { document ->
                val dynamicUrl = document?.getString("current_base_url")

                if (dynamicUrl != null) {
                    Log.d("ScanGrad", "Application Class: Found Server at $dynamicUrl")

                    FastApiService.ApiClient.initialize(dynamicUrl)

                    FastApiService.ApiClient.isReady = true
                } else {
                    Log.e("ScanGrad", "Application Class: Firebase URL was empty.")
                }
            }
            .addOnFailureListener {
                Log.e("ScanGrad", "Application Class: Firebase connection failed.")
            }
    }
}