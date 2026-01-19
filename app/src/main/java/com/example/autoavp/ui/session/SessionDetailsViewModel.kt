package com.example.autoavp.ui.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.autoavp.data.local.entities.MailItemEntity
import com.example.autoavp.data.local.entities.InstanceOfficeEntity
import com.example.autoavp.data.repository.ScanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.example.autoavp.data.repository.OfficeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class SessionDetailsViewModel @Inject constructor(
    private val repository: ScanRepository,
    private val officeRepository: OfficeRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val sessionId: Long = checkNotNull(savedStateHandle["sessionId"])

    fun getSessionId() = sessionId

    val mailItems: StateFlow<List<MailItemEntity>> = repository.getItemsForSession(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Liste des bureaux disponibles
    val offices: StateFlow<List<InstanceOfficeEntity>> = officeRepository.getAllOffices()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Bureau sélectionné pour l'impression
    private val _selectedOffice = MutableStateFlow<InstanceOfficeEntity?>(null)
    val selectedOffice = _selectedOffice.asStateFlow()

    fun selectOffice(office: InstanceOfficeEntity) {
        _selectedOffice.value = office
    }

    fun updateItem(item: MailItemEntity) {
        viewModelScope.launch {
            repository.updateMailItem(item)
        }
    }

    fun deleteItem(item: MailItemEntity) {
        viewModelScope.launch {
            repository.deleteMailItem(item)
        }
    }
}
