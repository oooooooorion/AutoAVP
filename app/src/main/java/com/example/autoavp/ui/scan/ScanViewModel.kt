package com.example.autoavp.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.autoavp.data.repository.ScanRepository
import com.example.autoavp.data.repository.SettingsRepository
import com.example.autoavp.domain.model.ScannedData
import com.example.autoavp.domain.model.TrackingType
import com.example.autoavp.domain.model.ValidationStatus
import com.example.autoavp.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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
    private val scanRepository: ScanRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _scanState = MutableStateFlow<ScanUiState>(ScanUiState.Initializing)
    val scanState: StateFlow<ScanUiState> = _scanState.asStateFlow()

    private val _liveTracking = MutableStateFlow<String?>(null)
    val liveTracking: StateFlow<String?> = _liveTracking.asStateFlow()

    private val _liveOcr = MutableStateFlow<String?>(null)
    val liveOcr: StateFlow<String?> = _liveOcr.asStateFlow()

    private val _detectedBlocks = MutableStateFlow<List<android.graphics.RectF>>(emptyList())
    val detectedBlocks: StateFlow<List<android.graphics.RectF>> = _detectedBlocks.asStateFlow()

    val isManualMode: StateFlow<Boolean> = settingsRepository.autoDetection.map { !it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _currentSessionId = MutableStateFlow<Long?>(null)
    val currentSessionId: StateFlow<Long?> = _currentSessionId.asStateFlow()

    // État détaillé pour le HUD
    data class AccumulationStatus(
        val tracking: String? = null,
        val ocrKey: String? = null,
        val address: String? = null,
        val isSmartData: Boolean = false
    )
    private val _accumulationStatus = MutableStateFlow(AccumulationStatus())
    val accumulationStatus: StateFlow<AccumulationStatus> = _accumulationStatus.asStateFlow()

    // --- LOGIQUE D'ACCUMULATION ---
    private var pendingData: ScannedData? = null
    // Plus de timeout automatique : seul le complet ou le manuel déclenche la sauvegarde
    
    private var scanMode: String = "bulk"

    fun setScanMode(mode: String?) {
        scanMode = mode ?: "bulk"
    }

    fun onLiveDetection(tracking: String?, ocr: String?, blocks: List<android.graphics.RectF>?) {
        _liveTracking.value = tracking
        _liveOcr.value = ocr
        _detectedBlocks.value = blocks ?: emptyList()
    }
    
    // Compteur en temps réel des éléments de la session
    @OptIn(ExperimentalCoroutinesApi::class)
    val scannedCount: StateFlow<Int> = _currentSessionId.flatMapLatest { id ->
        if (id == null) kotlinx.coroutines.flow.flowOf(emptyList())
        else scanRepository.getItemsForSession(id)
    }.map { it.size }
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

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

    fun onDataScanned(newData: ScannedData, isManual: Boolean) {
        val sessionId = _currentSessionId.value ?: return

        viewModelScope.launch {
            if (isManual) {
                // Mode Manuel (Bouton Photo) : On sauvegarde tout de suite, quel que soit l'état (Force Add)
                // On fusionne quand même avec ce qu'on avait en mémoire pour ne pas perdre d'infos
                val finalData = if (pendingData != null) mergeData(pendingData!!, newData) else newData
                saveData(sessionId, finalData)
                pendingData = null // Reset après sauvegarde
                return@launch
            }

            // --- Logique d'Accumulation (Mode Auto) ---
            
            // 1. Fusionner avec les données en attente
            val currentPending = pendingData ?: newData
            val mergedData = mergeData(currentPending, newData)
            pendingData = mergedData

            // Mise à jour du HUD
            _accumulationStatus.value = AccumulationStatus(
                tracking = mergedData.trackingNumber,
                ocrKey = mergedData.ocrKey,
                address = mergedData.rawText,
                isSmartData = mergedData.trackingType == TrackingType.SMARTDATA_DATAMATRIX
            )

            // Feedback visuel (via l'état Processing) pour dire "J'ai des infos, mais j'attends la suite..."
            _scanState.value = ScanUiState.Processing(mergedData)

            // 2. Vérifier si l'objet est STRICTEMENT COMPLET
            if (isComplete(mergedData)) {
                // On a tout -> Sauvegarde immédiate
                saveData(sessionId, mergedData)
                pendingData = null
            } 
            // Sinon : On ne fait RIEN. On attend le prochain scan ou le clic manuel.
        }
    }

    /**
     * Fusionne deux jeux de données pour enrichir l'information.
     */
    private fun mergeData(old: ScannedData, new: ScannedData): ScannedData {
        // On garde le Tracking le plus précis (OCR > Barcode si Verified)
        
        val tracking = if (new.confidenceStatus == ValidationStatus.VERIFIED) new.trackingNumber else old.trackingNumber
        val type = new.trackingType ?: old.trackingType
        // Name n'est plus utilisé (fusionné dans address)
        val address = if (!new.rawText.isNullOrBlank() && (new.rawText!!.length > (old.rawText?.length ?: 0))) new.rawText else old.rawText
        
        val iKey = new.isoKey ?: old.isoKey
        val oKey = new.ocrKey ?: old.ocrKey
        
        // Recalcul du statut global après fusion
        var status = ValidationStatus.CALCULATED
        if (oKey != null && oKey == iKey) {
            status = ValidationStatus.VERIFIED
        } else if (oKey != null) {
            status = ValidationStatus.WARNING
        }

        return old.copy(
            trackingNumber = tracking,
            trackingType = type,
            recipientName = null,
            rawText = address,
            confidenceStatus = status,
            isoKey = iKey,
            ocrKey = oKey
        )
    }

    /**
     * Vérifie si toutes les conditions sont réunies pour valider l'objet sans attendre.
     */
    private fun isComplete(data: ScannedData): Boolean {
        // En mode partiel, on est complet dès qu'on a ce qu'on cherche
        if (scanMode == Screen.Scan.MODE_RETURN_TRACKING) {
             return !data.trackingNumber.isNullOrBlank()
        }
        if (scanMode == Screen.Scan.MODE_RETURN_ADDRESS) {
             return !data.rawText.isNullOrBlank()
        }

        val hasTracking = !data.trackingNumber.isNullOrBlank()
        val hasAddress = !data.rawText.isNullOrBlank()
        
        if (!hasTracking || !hasAddress) return false

        // Spécificités SmartData
        if (data.trackingType == TrackingType.SMARTDATA_DATAMATRIX) {
            val hasOcrKey = data.ocrKey != null // Preuve qu'on a lu "SD : ..."
            val isVerified = data.confidenceStatus == ValidationStatus.VERIFIED
            
            // Il faut le code (tracking), la clé OCR, l'adresse (bloc complet)
            return hasOcrKey && isVerified
        }

        // Code Barres classique
        return true
    }

    private suspend fun saveData(sessionId: Long, data: ScannedData) {
        // Modes de retour (Mise à jour) : Pas de sauvegarde DB, pas de check doublon
        if (scanMode == Screen.Scan.MODE_RETURN_TRACKING || 
            scanMode == Screen.Scan.MODE_RETURN_ADDRESS || 
            scanMode == Screen.Scan.MODE_RETURN_ALL) {
            
            // Sécurité : Si on a déjà fini ou réussi, on ignore
            if (_scanState.value is ScanUiState.Success || _scanState.value is ScanUiState.Finished) return

            _scanState.value = ScanUiState.Success(data)
            // Pas de délai pour le mode retour, on veut libérer la caméra vite
            _scanState.value = ScanUiState.Finished
            return
        }

        // Anti-doublon
        val existingItems = scanRepository.getItemsForSession(sessionId).first()
        if (existingItems.any { it.trackingNumber == data.trackingNumber }) {
            _scanState.value = ScanUiState.Duplicate(data.trackingNumber ?: "?")
            delay(1500)
            _scanState.value = ScanUiState.Scanning
            return
        }

        scanRepository.saveScannedItem(sessionId, data)
        _scanState.value = ScanUiState.Success(data)
        _accumulationStatus.value = AccumulationStatus() // RESET HUD

        val isContinuous = settingsRepository.continuousScan.first()

        if (scanMode == Screen.Scan.MODE_SINGLE) {
            delay(1000)
            _scanState.value = ScanUiState.Finished
        } else {
            // Mode Bulk (ou défaut)
            if (isContinuous) {
                delay(1500)
                _scanState.value = ScanUiState.Scanning
            } else {
                delay(1000)
                _scanState.value = ScanUiState.Finished
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
    data class Processing(val partialData: ScannedData) : ScanUiState() // Nouvel état pour l'accumulation
    data class Success(val data: ScannedData) : ScanUiState()
    data class Duplicate(val trackingNumber: String) : ScanUiState()
    data class Error(val message: String) : ScanUiState()
    object Finished : ScanUiState()
}
