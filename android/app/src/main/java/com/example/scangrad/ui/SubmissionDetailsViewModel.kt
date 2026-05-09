package com.example.scangrad.ui

import android.app.Activity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.scangrad.data.Submission
import com.example.scangrad.db.FirebaseManager

class SubmissionDetailsViewModel : ViewModel() {

    private val _submission = MutableLiveData<Submission?>()
    val submission: LiveData<Submission?> = _submission

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private var loadedId: String? = null

    fun load(activity: Activity, submissionId: String, force: Boolean = false) {
        if (!force && submissionId == loadedId && _submission.value != null) return
        loadedId = submissionId
        _isLoading.value = true
        FirebaseManager(activity).fetchSubmissionById(
            submissionId = submissionId,
            onSuccess = {
                _submission.value = it
                _isLoading.value = false
            },
            onFailed = { msg ->
                _error.value = msg
                _isLoading.value = false
            }
        )
    }
}
