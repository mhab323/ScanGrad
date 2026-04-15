package com.example.scangrad.db

import android.app.Activity
import android.content.Intent
import android.util.Log
import com.example.scangrad.R
import com.example.scangrad.data.Submission
import com.example.scangrad.data.UserSession
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

class FirebaseManager(private val activity: Activity) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private var storedVerificationId: String? = null


    fun getGoogleSignInClient(): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(activity.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(activity, gso)
    }

    fun handleGoogleSignInResult(
        data: Intent?,
        onSuccess: () -> Unit,
        onFailed: (String) -> Unit
    ) {
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(data)
                .getResult(ApiException::class.java)

            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            auth.signInWithCredential(credential)
                .addOnCompleteListener(activity) { task ->
                    if (task.isSuccessful) {
                        val firebaseUser = auth.currentUser
                        if (firebaseUser != null) {
                            UserSession.populate(firebaseUser)
                            onSuccess()
                        } else {
                            onFailed("Sign-in succeeded but user is null")
                        }
                    } else {
                        onFailed(task.exception?.localizedMessage ?: "Google sign-in failed")
                    }
                }
        } catch (e: ApiException) {
            Log.e("FirebaseManager", "Google sign-in failed", e)
            onFailed("Google sign-in failed: ${e.localizedMessage}")
        }
    }


    fun sendPhoneOtp(
        phoneNumber: String,
        onCodeSent: () -> Unit,
        onVerificationFailed: (String) -> Unit,
        onLoginSuccess: () -> Unit
    ) {
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                signInWithCredential(credential, onLoginSuccess, onVerificationFailed)
            }

            override fun onVerificationFailed(e: FirebaseException) {
                Log.e("FirebaseManager", "Verification Failed", e)
                onVerificationFailed(e.localizedMessage ?: "Verification Failed")
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                storedVerificationId = verificationId
                onCodeSent()
            }
        }

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun verifyEnteredOtp(
        code: String,
        onLoginSuccess: () -> Unit,
        onLoginFailed: (String) -> Unit
    ) {
        val verificationId = storedVerificationId
        if (verificationId == null) {
            onLoginFailed("Verification ID is missing. Request a new code.")
            return
        }

        val credential = PhoneAuthProvider.getCredential(verificationId, code)
        signInWithCredential(credential, onLoginSuccess, onLoginFailed)
    }

    private fun signInWithCredential(
        credential: PhoneAuthCredential,
        onSuccess: () -> Unit,
        onFailed: (String) -> Unit
    ) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener(activity) { task ->
                if (task.isSuccessful) {
                    val firebaseUser = auth.currentUser
                    if (firebaseUser != null) UserSession.populate(firebaseUser)
                    onSuccess()
                } else {
                    onFailed(task.exception?.localizedMessage ?: "Login failed")
                }
            }
    }

    // ── Session ───────────────────────────────────────────────────────────────

    /**
     * Returns true and populates [UserSession] if a Firebase user is already
     * cached locally, so the app never hits the network just to check auth state.
     */
    fun isUserLoggedIn(): Boolean {
        val user = auth.currentUser ?: return false
        UserSession.populate(user)
        return true
    }

    fun signOut() {
        auth.signOut()
        getGoogleSignInClient().signOut()
        UserSession.clear()
    }

    // ── Firestore ─────────────────────────────────────────────────────────────

    fun fetchRecentSubmissions(
        userId: String,
        limit: Long = 3,
        onSuccess: (List<Submission>) -> Unit,
        onFailed: (String) -> Unit
    ) {
        val db = FirebaseFirestore.getInstance()
        db.collection("submissions")
            .whereEqualTo("userId", userId)
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(limit)
            .get()
            .addOnSuccessListener { snapshot ->
                val list = snapshot.documents.mapNotNull { it.toObject(Submission::class.java) }
                onSuccess(list)
            }
            .addOnFailureListener { e ->
                Log.e("FirebaseManager", "fetchRecentSubmissions failed", e)
                onFailed(e.localizedMessage ?: "Failed to fetch submissions")
            }
    }

    fun saveSubmission(
        submission: Submission,
        onSuccess: () -> Unit,
        onFailed: (String) -> Unit
    ) {
        val db = FirebaseFirestore.getInstance()
        val docRef = db.collection("submissions").document()
        val submissionWithId = submission.copy(id = docRef.id)

        docRef.set(submissionWithId)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e ->
                Log.e("FirebaseManager", "saveSubmission failed", e)
                onFailed(e.localizedMessage ?: "Failed to save submission")
            }
    }
}
