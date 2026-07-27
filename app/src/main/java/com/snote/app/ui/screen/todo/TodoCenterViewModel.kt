package com.snote.app.ui.screen.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snote.app.data.model.ContentItem
import com.snote.app.data.repository.TodoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TodoCenterViewModel @Inject constructor(
    private val todoRepository: TodoRepository
) : ViewModel() {

    private val _unfinishedCount = MutableStateFlow(0)
    val unfinishedCount: StateFlow<Int> = _unfinishedCount.asStateFlow()

    private val _finishedCount = MutableStateFlow(0)
    val finishedCount: StateFlow<Int> = _finishedCount.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            todoRepository.loadTodos()
            refreshCounts()
        }
    }

    private fun refreshCounts() {
        val board = todoRepository.getBoard()
        _unfinishedCount.value = board.unfinished.size
        _finishedCount.value = board.finished.size
    }
}
