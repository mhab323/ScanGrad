package com.example.scangrad.data

import com.google.firebase.auth.FirebaseUser

data class User(
    val uid: String,
    val displayName: String,
    val email: String,
    val photoUrl: String,
    val phoneNumber: String
)

/**
 * In-memory singleton that holds the signed-in user for the entire app lifetime.
 * Call [populate] right after a successful login so every screen can read
 * [current] without touching Firebase again.
 */
object UserSession {

    var current: User? = null
        private set

    fun populate(firebaseUser: FirebaseUser) {
        current = User(
            uid         = firebaseUser.uid,
            displayName = firebaseUser.displayName.orEmpty(),
            email       = firebaseUser.email.orEmpty(),
            photoUrl    = firebaseUser.photoUrl?.toString().orEmpty(),
            phoneNumber = firebaseUser.phoneNumber.orEmpty()
        )
    }

    fun clear() {
        current = null
    }

    val uid: String get() = current?.uid ?: "dev_user"
}
