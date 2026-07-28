package com.snote.app.ui.screen.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snote.app.data.repository.StudyTimeRepository
import com.snote.app.data.repository.TodoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
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

    private val _currentQuote = MutableStateFlow(quotes.random())
    val currentQuote: StateFlow<String> = _currentQuote.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            todoRepository.loadTodos()
            _unfinishedCount.value = todoRepository.getBoard().unfinished.size

            studyTimeRepository.loadRecords()
            val todayRecords = studyTimeRepository.getTodayRecords()
            _todayStudyMinutes.value = todayRecords.sumOf { (it.durationSeconds + 59) / 60 }
        }
    }

    fun prepareNextQuote() {
        viewModelScope.launch {
            delay(500)
            _currentQuote.value = quotes.random()
        }
    }

    companion object {
        private val quotes = listOf(
            "不必急于求成，坚持自有回响",
            "把浮躁收起，用行动靠近理想",
            "自律很难，但收获永远值得",
            "别躺平，机遇只留给准备好的人",
            "默默沉淀，时间会见证所有付出",
            "眼界拓宽，人生道路才会更广",
            "拒绝虚度，每一天都要有收获",
            "不怕起步晚，只怕永远不肯开始",
            "所学皆底气，知识永远不会背叛你",
            "稳住心态，稳步奔赴想要的生活",
            "少空想多实干，行动破除焦虑",
            "前路漫漫，努力便是最好底牌",
            "向阳前行，一切美好正在赶来"
        )
    }
}
