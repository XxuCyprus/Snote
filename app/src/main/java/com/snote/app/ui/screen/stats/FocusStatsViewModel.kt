package com.snote.app.ui.screen.stats

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snote.app.data.model.StudyRecord
import com.snote.app.data.repository.DataRepository
import com.snote.app.data.repository.StudyTimeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class PieSlice(
    val label: String,
    val durationSeconds: Long,
    val color: Color,
    val sweepAngle: Float
)

@HiltViewModel
class FocusStatsViewModel @Inject constructor(
    private val studyTimeRepository: StudyTimeRepository,
    private val dataRepository: DataRepository
) : ViewModel() {

    private val _slices = MutableStateFlow<List<PieSlice>>(emptyList())
    val slices: StateFlow<List<PieSlice>> = _slices.asStateFlow()

    private val _totalDuration = MutableStateFlow(0L)
    val totalDuration: StateFlow<Long> = _totalDuration.asStateFlow()

    private val _isEmpty = MutableStateFlow(true)
    val isEmpty: StateFlow<Boolean> = _isEmpty.asStateFlow()

    private val _selectedDate = MutableStateFlow(LocalDate.now().toString())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()
    val selectedDateDisplay: String get() {
        val d = LocalDate.parse(_selectedDate.value)
        return d.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))
    }

    private val pieColors = listOf(
        Color(0xFF64B5F6), // 天蓝
        Color(0xFF81C784), // 薄荷绿
        Color(0xFFF06292), // 樱花粉
        Color(0xFFFFD54F), // 奶油黄
        Color(0xFFBA68C8), // 薰衣草紫
        Color(0xFF4DD0E1), // 湖水青
        Color(0xFFFF8A65), // 蜜桃橙
        Color(0xFFA1887F), // 奶茶棕
    )

    init {
        loadData()
    }

    fun loadData() {
        loadDataForDate(_selectedDate.value)
    }

    fun setDate(date: String) {
        _selectedDate.value = date
        loadDataForDate(date)
    }

    private fun loadDataForDate(date: String) {
        viewModelScope.launch {
            studyTimeRepository.loadRecords()
            val records = studyTimeRepository.getAllRecords().filter { it.date == date }

            if (records.isEmpty()) {
                _isEmpty.value = true
                _slices.value = emptyList()
                _totalDuration.value = 0
                return@launch
            }

            _isEmpty.value = false
            _totalDuration.value = records.sumOf { it.durationSeconds }

            // 按 notebookId 聚合
            val grouped = records.groupBy { it.notebookId }
                .map { (key, values) -> key to values.sumOf { it.durationSeconds } }
                .sortedByDescending { it.second }

            // 规则: ≤3 展示全部, 4 展示4个, ≥5 展示top3 + 其他
            val displayEntries = when {
                grouped.size <= 3 -> grouped
                grouped.size == 4 -> grouped
                else -> {
                    val top3 = grouped.take(3)
                    val others = grouped.drop(3).sumOf { it.second }
                    top3 + ("__others__" to others)
                }
            }

            val total = displayEntries.sumOf { it.second }.toFloat()
            val result = displayEntries.mapIndexed { index, (notebookId, duration) ->
                val label = if (notebookId == "__others__") "其他"
                    else dataRepository.getNotebookById(notebookId)?.title ?: notebookId.take(8)
                val sweepAngle = if (total > 0) (duration / total * 360f) else 0f
                val color = if (notebookId == "__others__") Color(0xFFCE93D8)
                    else {
                        val idx = kotlin.math.abs(notebookId.hashCode()) % pieColors.size
                        pieColors[idx]
                    }
                PieSlice(label = label, durationSeconds = duration, color = color, sweepAngle = sweepAngle)
            }

            _slices.value = result
        }
    }
}
