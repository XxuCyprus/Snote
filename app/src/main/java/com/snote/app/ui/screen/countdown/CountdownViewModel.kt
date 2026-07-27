package com.snote.app.ui.screen.countdown

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snote.app.data.repository.CountdownRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CountdownViewModel @Inject constructor(
    private val countdownRepository: CountdownRepository
) : ViewModel() {

    private val _items = MutableStateFlow<List<CountdownRepository.CountdownWithDays>>(emptyList())
    val items: StateFlow<List<CountdownRepository.CountdownWithDays>> = _items.asStateFlow()

    private val _showAddDialog = MutableStateFlow(false)
    val showAddDialog: StateFlow<Boolean> = _showAddDialog.asStateFlow()

    private val _editDialogItem = MutableStateFlow<CountdownRepository.CountdownWithDays?>(null)
    val editDialogItem: StateFlow<CountdownRepository.CountdownWithDays?> = _editDialogItem.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            countdownRepository.loadCountdowns()
            _items.value = countdownRepository.getCountdownsWithDays()
        }
    }

    fun showAddDialog() { _showAddDialog.value = true }
    fun hideAddDialog() { _showAddDialog.value = false }

    fun showEditDialog(item: CountdownRepository.CountdownWithDays) { _editDialogItem.value = item }
    fun hideEditDialog() { _editDialogItem.value = null }

    fun addCountdown(title: String, targetDate: Long) {
        viewModelScope.launch {
            countdownRepository.addCountdown(title, targetDate)
            _items.value = countdownRepository.getCountdownsWithDays()
            _showAddDialog.value = false
        }
    }

    fun updateCountdown(id: String, title: String, targetDate: Long) {
        viewModelScope.launch {
            countdownRepository.updateCountdown(id, title, targetDate)
            _items.value = countdownRepository.getCountdownsWithDays()
            _editDialogItem.value = null
        }
    }

    fun deleteCountdown(id: String) {
        viewModelScope.launch {
            countdownRepository.deleteCountdown(id)
            _items.value = countdownRepository.getCountdownsWithDays()
            _editDialogItem.value = null
        }
    }

    fun formatDate(epochMs: Long): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(epochMs))
    }
}
