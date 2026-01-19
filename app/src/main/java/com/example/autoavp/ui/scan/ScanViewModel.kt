package com.example.autoavp.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.autoavp.data.repository.ScanRepository
import com.example.autoavp.domain.model.ScannedData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import javax.inject.Inject

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val scanRepository: ScanRepository
) : ViewModel() {

    private val _scanState = MutableStateFlow<ScanUiState>(ScanUiState.Initializing)
    val scanState: StateFlow<ScanUiState> = _scanState.asStateFlow()

    private val _liveTracking = MutableStateFlow<String?>(null)
    val liveTracking: StateFlow<String?> = _liveTracking.asStateFlow()

    private val _liveOcr = MutableStateFlow<String?>(null)
    val liveOcr: StateFlow<String?> = _liveOcr.asStateFlow()

    private val _isManualMode = MutableStateFlow(false)
    val isManualMode: StateFlow<Boolean> = _isManualMode.asStateFlow()

    private val _currentSessionId = MutableStateFlow<Long?>(null)
    val currentSessionId: StateFlow<Long?> = _currentSessionId.asStateFlow()

    private var scanMode: String = "bulk"

    fun setScanMode(mode: String?) {
        scanMode = mode ?: "bulk"
    }

    fun toggleManualMode() {
        _isManualMode.value = !_isManualMode.value
    }

    fun onLiveDetection(tracking: String?, ocr: String?) {
        _liveTracking.value = tracking
        _liveOcr.value = ocr
    }
    
    // Compteur en temps réel des éléments de la session
    @OptIn(ExperimentalCoroutinesApi::class)
    val scannedCount: StateFlow<Int> = _currentSessionId.flatMapLatest { id ->
        if (id == null) kotlinx.coroutines.flow.flowOf(emptyList())
        else scanRepository.getItemsForSession(id)
    }.map { it.size }
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private var lastScannedTrackingNumber: String? = null
    private var lastScanTime: Long = 0

    init {
        ensureActiveSession()
    }

    private fun ensureActiveSession() {
        viewModelScope.launch {
            try {
                // On essaie de récupérer la dernière session ou on en crée une
                val latest = scanRepository.getLatestSession().first()
                val id = latest?.sessionId ?: scanRepository.createSession()
                _currentSessionId.value = id
                _scanState.value = ScanUiState.Scanning
            } catch (e: Exception) {
                _scanState.value = ScanUiState.Error("Impossible d'accéder à la session: ${e.message}")
            }
        }
    }

    fun onDataScanned(data: ScannedData, isManual: Boolean) {
        val sessionId = _currentSessionId.value ?: return
        
        // 1. Validation Tracking + Adresse (sauf si manuel)
        if (!isManual && (data.trackingNumber == null || data.rawText.isNullOrBlank())) {
            return // On ignore les scans partiels en mode auto
        }

        viewModelScope.launch {
            // 2. Anti-doublon strict (Session)
            if (data.trackingNumber != null) {
                val existingItems = scanRepository.getItemsForSession(sessionId).first()
                if (existingItems.any { it.trackingNumber == data.trackingNumber }) {
                    _scanState.value = ScanUiState.Duplicate(data.trackingNumber)
                    delay(1500)
                    _scanState.value = ScanUiState.Scanning
                    return@launch
                }
            }

            // Sauvegarde
            scanRepository.saveScannedItem(sessionId, data)
            lastScannedTrackingNumber = data.trackingNumber
            
            _scanState.value = ScanUiState.Success(data)
            
            if (scanMode == "single") {
                delay(1000)
                _scanState.value = ScanUiState.Finished
            } else {
                delay(1500)
                _scanState.value = ScanUiState.Scanning
            }
        }
    }

    fun onRetry() {
        _scanState.value = ScanUiState.Scanning
    }
}

sealed class ScanUiState {
    object Initializing : ScanUiState()
    object Scanning : ScanUiState()
    data class Success(val data: ScannedData) : ScanUiState()
    data class Duplicate(val trackingNumber: String) : ScanUiState()
    data class Error(val message: String) : ScanUiState()
    object Finished : ScanUiState()
}
