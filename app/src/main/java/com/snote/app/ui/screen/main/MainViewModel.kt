package com.snote.app.ui.screen.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snote.app.data.repository.StudyTimeRepository
import com.snote.app.data.repository.TodoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val todoRepository: TodoRepository,
    private val studyTimeRepository: StudyTimeRepository
) : ViewModel() {

    private val _unfinishedCount = MutableStateFlow(0)
    val unfinishedCount: StateFlow<Int> = _unfinishedCount.asStateFlow()

    private val _todayStudyMinutes = MutableStateFlow(0L)
    val todayStudyMinutes: StateFlow<Long> = _todayStudyMinutes.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            todoRepository.loadTodos()
            _unfinishedCount.value = todoRepository.getBoard().unfinished.size

            studyTimeRepository.loadRecords()
            val todayRecords = studyTimeRepository.getTodayRecords()
            _todayStudyMinutes.value = todayRecords.sumOf { it.durationSeconds } / 60
        }
    }
}
