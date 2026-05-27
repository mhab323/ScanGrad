package com.example.scangrad.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scangrad.network.EvaluationRepository
import com.example.scangrad.network.EvaluationRequest
import com.example.scangrad.network.EvaluationResponse
import kotlinx.coroutines.launch

class EvaluationViewModel : ViewModel() {

    private val repository = EvaluationRepository()

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _evaluationResult = MutableLiveData<EvaluationResponse?>(null)
    val evaluationResult: LiveData<EvaluationResponse?> = _evaluationResult

    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage

    fun evaluate(request: EvaluationRequest) {
        if (_isLoading.value == true) return
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _evaluationResult.value = null
            val result = repository.sendForGrading(request)
            _isLoading.value = false
            result
                .onSuccess { response ->
                    _evaluationResult.value = response
                }
                .onFailure { e ->
                    _errorMessage.value = e.localizedMessage ?: "Grading failed"
                }
        }
    }

    fun clearResult() {
        _evaluationResult.value = null
        _errorMessage.value = null
    }
}
