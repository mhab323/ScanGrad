package com.example.scangrad.db

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.scangrad.R
import com.example.scangrad.data.Submission
import com.example.scangrad.data.UserSession
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.storage.FirebaseStorage

class FirebaseManager(private val activity: Activity) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    // ── Email / Password ──────────────────────────────────────────────────────

    /**
     * Signs in an existing user with [email] and [password].
     * On success, saves/updates the user profile in Firestore, populates
     * [UserSession], then calls [onSuccess].
     */
    fun signInWithEmail(
        email: String,
        password: String,
        onSuccess: () -> Unit,
        onFailed: (String) -> Unit
    ) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(activity) { task ->
                if (task.isSuccessful) {
                    val firebaseUser = auth.currentUser
                    if (firebaseUser != null) {
                        saveUserToFirestore(firebaseUser, onSuccess, onFailed)
                    } else {
                        onFailed("Sign-in succeeded but user is null")
                    }
                } else {
                    onFailed(task.exception?.localizedMessage ?: "Login failed")
                }
            }
    }

    /**
     * Creates a new Firebase Auth account with [email] and [password], sets
     * [name] as the display name, saves the profile to Firestore, populates
     * [UserSession], then calls [onSuccess].
     */
    fun signUpWithEmail(
        name: String,
        email: String,
        password: String,
        onSuccess: () -> Unit,
        onFailed: (String) -> Unit
    ) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(activity) { task ->
                if (!task.isSuccessful) {
                    onFailed(task.exception?.localizedMessage ?: "Sign-up failed")
                    return@addOnCompleteListener
                }
                val firebaseUser = auth.currentUser ?: run {
                    onFailed("Account created but user object is null")
                    return@addOnCompleteListener
                }
                val profileUpdate = UserProfileChangeRequest.Builder()
                    .setDisplayName(name)
                    .build()
                firebaseUser.updateProfile(profileUpdate)
                    .addOnCompleteListener { profileTask ->
                        if (!profileTask.isSuccessful) {
                            onFailed(profileTask.exception?.localizedMessage ?: "Profile update failed")
                            return@addOnCompleteListener
                        }
                        // auth.currentUser now carries the updated displayName
                        saveUserToFirestore(auth.currentUser!!, onSuccess, onFailed)
                    }
            }
    }

    // ── Google Sign-In ────────────────────────────────────────────────────────

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
                            saveUserToFirestore(firebaseUser, onSuccess, onFailed)
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

    // ── Session ───────────────────────────────────────────────────────────────

    /**
     * Returns true and populates [UserSession] from the locally cached Firebase
     * user — no network call needed just to check auth state on app launch.
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

    // ── Firestore — user profile ──────────────────────────────────────────────

    /**
     * Saves (or merges) the authenticated user's profile into `users/{uid}`.
     *
     * [SetOptions.merge] ensures fields written by other processes are never
     * overwritten. [UserSession] is populated and [onSuccess] is called only
     * after Firestore confirms the write, so the local session always reflects
     * a persisted record.
     */
    private fun saveUserToFirestore(
        firebaseUser: FirebaseUser,
        onSuccess: () -> Unit,
        onFailed: (String) -> Unit
    ) {
        val userData = hashMapOf(
            "uid"         to firebaseUser.uid,
            "displayName" to firebaseUser.displayName.orEmpty(),
            "email"       to firebaseUser.email.orEmpty(),
            "photoUrl"    to firebaseUser.photoUrl?.toString().orEmpty(),
            "phoneNumber" to firebaseUser.phoneNumber.orEmpty(),
            "lastSignIn"  to FieldValue.serverTimestamp()
        )

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(firebaseUser.uid)
            .set(userData, SetOptions.merge())
            .addOnSuccessListener {
                UserSession.populate(firebaseUser)
                onSuccess()
            }
            .addOnFailureListener { e ->
                Log.e("FirebaseManager", "saveUserToFirestore failed", e)
                onFailed(e.localizedMessage ?: "Failed to save user profile")
            }
    }

    // ── Firestore — submissions ───────────────────────────────────────────────

    fun fetchRecentSubmissions(
        userId: String,
        limit: Long = 3,
        onSuccess: (List<Submission>) -> Unit,
        onFailed: (String) -> Unit
    ) {
        FirebaseFirestore.getInstance()
            .collection("submissions")
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

    /**
     * Fetches every submission for [userId], newest first.
     * The `whereEqualTo + orderBy` combo requires a composite Firestore index;
     * if missing, the exception message will contain the Firebase Console link
     * to create it.
     */
    fun fetchUserHistory(
        userId: String,
        onSuccess: (List<Submission>) -> Unit,
        onFailed: (String) -> Unit
    ) {
        FirebaseFirestore.getInstance()
            .collection("submissions")
            .whereEqualTo("userId", userId)
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                val list = snapshot.documents.mapNotNull { it.toObject(Submission::class.java) }
                onSuccess(list)
            }
            .addOnFailureListener { e ->
                Log.e("FirebaseManager", "fetchUserHistory failed", e)
                onFailed(e.localizedMessage ?: "Failed to fetch history")
            }
    }

    fun uploadFileToStorage(
        fileUri: Uri,
        userId: String,
        onSuccess: (downloadUrl: String) -> Unit,
        onFailed: (String) -> Unit
    ) {
        val fileName = "submissions/${userId}/${System.currentTimeMillis()}.pdf"
        val storageRef = FirebaseStorage.getInstance().reference.child(fileName)

        storageRef.putFile(fileUri)
            .continueWithTask { task ->
                if (!task.isSuccessful) throw task.exception!!
                storageRef.downloadUrl
            }
            .addOnSuccessListener { uri ->
                onSuccess(uri.toString())
            }
            .addOnFailureListener { e ->
                Log.e("FirebaseManager", "uploadFileToStorage failed", e)
                onFailed(e.localizedMessage ?: "Upload failed")
            }
    }

    fun saveSubmission(
        submission: Submission,
        onSuccess: (String) -> Unit,
        onFailed: (String) -> Unit
    ) {
        val db = FirebaseFirestore.getInstance()
        val docRef = db.collection("submissions").document()
        val submissionWithId = submission.copy(id = docRef.id)

        docRef.set(submissionWithId)
            .addOnSuccessListener { onSuccess(docRef.id) }
            .addOnFailureListener { e ->
                Log.e("FirebaseManager", "saveSubmission failed", e)
                onFailed(e.localizedMessage ?: "Failed to save submission")
            }
    }

    fun updateSubmissionGraded(
        submissionId: String,
        score: Int,
        feedback: String,
        confidenceLevel: String,
        onSuccess: () -> Unit = {},
        onFailed: (String) -> Unit = {}
    ) {
        FirebaseFirestore.getInstance()
            .collection("submissions")
            .document(submissionId)
            .update(
                mapOf(
                    "status" to "GRADED",
                    "score" to score,
                    "feedback" to feedback,
                    "confidenceLevel" to confidenceLevel
                )
            )
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e ->
                Log.e("FirebaseManager", "updateSubmissionGraded failed", e)
                onFailed(e.localizedMessage ?: "Failed to update submission")
            }
    }
}
