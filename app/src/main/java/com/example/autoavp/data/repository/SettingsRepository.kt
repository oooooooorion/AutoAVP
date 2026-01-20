package com.example.autoavp.data.repository

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("autoavp_settings", Context.MODE_PRIVATE)

    private val _calibrationX = MutableStateFlow(prefs.getFloat(KEY_CALIB_X, 0f))
    val calibrationX: StateFlow<Float> = _calibrationX.asStateFlow()

    private val _calibrationY = MutableStateFlow(prefs.getFloat(KEY_CALIB_Y, 0f))
    val calibrationY: StateFlow<Float> = _calibrationY.asStateFlow()

    private val _continuousScan = MutableStateFlow(prefs.getBoolean(KEY_CONTINUOUS_SCAN, true))
    val continuousScan: StateFlow<Boolean> = _continuousScan.asStateFlow()

    private val _autoDetection = MutableStateFlow(prefs.getBoolean(KEY_AUTO_DETECTION, true))
    val autoDetection: StateFlow<Boolean> = _autoDetection.asStateFlow()

    suspend fun setCalibrationX(value: Float) = withContext(Dispatchers.IO) {
        prefs.edit().putFloat(KEY_CALIB_X, value).apply()
        _calibrationX.value = value
    }

    suspend fun setCalibrationY(value: Float) = withContext(Dispatchers.IO) {
        prefs.edit().putFloat(KEY_CALIB_Y, value).apply()
        _calibrationY.value = value
    }

    suspend fun setContinuousScan(value: Boolean) = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean(KEY_CONTINUOUS_SCAN, value).apply()
        _continuousScan.value = value
    }

    suspend fun setAutoDetection(value: Boolean) = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean(KEY_AUTO_DETECTION, value).apply()
        _autoDetection.value = value
    }

    companion object {
        private const val KEY_CALIB_X = "calibration_x"
        private const val KEY_CALIB_Y = "calibration_y"
        private const val KEY_CONTINUOUS_SCAN = "continuous_scan"
        private const val KEY_AUTO_DETECTION = "auto_detection"
    }
}
