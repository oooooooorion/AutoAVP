package com.example.autoavp.ui.preview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.autoavp.data.local.entities.InstanceOfficeEntity
import com.example.autoavp.data.local.entities.MailItemEntity
import com.example.autoavp.data.repository.OfficeRepository
import com.example.autoavp.data.repository.ScanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PrintPreviewViewModel @Inject constructor(
    private val scanRepository: ScanRepository,
    private val officeRepository: OfficeRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val sessionId: Long = checkNotNull(savedStateHandle["sessionId"])
    private val officeId: Long = checkNotNull(savedStateHandle["officeId"])

    val mailItems: StateFlow<List<MailItemEntity>> = scanRepository.getItemsForSession(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _office = MutableStateFlow<InstanceOfficeEntity?>(null)
    val office = _office.asStateFlow()

    init {
        viewModelScope.launch {
            _office.value = officeRepository.getOfficeById(officeId)
        }
    }
}
