package com.example.scangrad.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scangrad.network.IngestRepository
import com.example.scangrad.network.IngestRequest
import com.example.scangrad.network.IngestResponse
import kotlinx.coroutines.launch

class IngestViewModel : ViewModel() {

    private val repository = IngestRepository()

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _result = MutableLiveData<IngestResponse?>(null)
    val result: LiveData<IngestResponse?> = _result

    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage

    fun ingest(request: IngestRequest) {
        if (_isLoading.value == true) return
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _result.value = null
            val result = repository.ingest(request)
            _isLoading.value = false
            result
                .onSuccess { response ->
                    _result.value = response
                }
                .onFailure { e ->
                    _errorMessage.value = e.localizedMessage ?: "Ingest failed"
                }
        }
    }

    fun clear() {
        _result.value = null
        _errorMessage.value = null
    }
}
