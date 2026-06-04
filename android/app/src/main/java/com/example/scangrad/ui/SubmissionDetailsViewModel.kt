package com.example.scangrad.ui

import android.app.Activity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.scangrad.data.Submission
import com.example.scangrad.db.FirebaseManager
import com.google.firebase.firestore.ListenerRegistration

class SubmissionDetailsViewModel : ViewModel() {

    private val _submission = MutableLiveData<Submission?>()
    val submission: LiveData<Submission?> = _submission

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private var loadedId: String? = null
    private var registration: ListenerRegistration? = null

    fun load(activity: Activity, submissionId: String, force: Boolean = false) {
        if (!force && submissionId == loadedId && registration != null) return
        loadedId = submissionId
        _isLoading.value = true
        registration?.remove()
        registration = FirebaseManager(activity).listenSubmissionById(
            submissionId = submissionId,
            onUpdate = {
                _submission.value = it
                _isLoading.value = false
            },
            onFailed = { msg ->
                _error.value = msg
                _isLoading.value = false
            }
        )
    }

    override fun onCleared() {
        super.onCleared()
        registration?.remove()
        registration = null
    }
}
