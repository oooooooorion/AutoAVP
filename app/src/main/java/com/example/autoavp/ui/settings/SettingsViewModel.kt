package com.example.autoavp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.autoavp.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val calibrationX: StateFlow<Float> = settingsRepository.calibrationX
    val calibrationY: StateFlow<Float> = settingsRepository.calibrationY
    val continuousScan: StateFlow<Boolean> = settingsRepository.continuousScan
    val autoDetection: StateFlow<Boolean> = settingsRepository.autoDetection

    fun updateCalibrationX(value: Float) {
        viewModelScope.launch {
            settingsRepository.setCalibrationX(value)
        }
    }

    fun updateCalibrationY(value: Float) {
        viewModelScope.launch {
            settingsRepository.setCalibrationY(value)
        }
    }
    
    fun toggleContinuousScan(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.setContinuousScan(value)
        }
    }

    fun toggleAutoDetection(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoDetection(value)
        }
    }
}
