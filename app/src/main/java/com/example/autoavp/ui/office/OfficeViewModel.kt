package com.example.autoavp.ui.office

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.autoavp.data.local.entities.InstanceOfficeEntity
import com.example.autoavp.data.repository.OfficeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OfficeViewModel @Inject constructor(
    private val repository: OfficeRepository
) : ViewModel() {

    val offices: StateFlow<List<InstanceOfficeEntity>> = repository.getAllOffices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _editingOffice = MutableStateFlow<InstanceOfficeEntity?>(null)
    val editingOffice: StateFlow<InstanceOfficeEntity?> = _editingOffice.asStateFlow()

    fun onAddOffice() {
        _editingOffice.value = InstanceOfficeEntity(
            name = "",
            address = "",
            openingHours = "",
            colorHex = "#FFCE00" // Jaune par défaut
        )
    }

    fun onEditOffice(office: InstanceOfficeEntity) {
        _editingOffice.value = office
    }

    fun onCancelEdit() {
        _editingOffice.value = null
    }

    fun onSaveOffice(office: InstanceOfficeEntity) {
        viewModelScope.launch {
            repository.saveOffice(office)
            _editingOffice.value = null
        }
    }

    fun onDeleteOffice(office: InstanceOfficeEntity) {
        viewModelScope.launch {
            repository.deleteOffice(office)
            // Si on supprime celui qu'on éditait (peu probable UX mais safe)
            if (_editingOffice.value?.officeId == office.officeId) {
                _editingOffice.value = null
            }
        }
    }
}
