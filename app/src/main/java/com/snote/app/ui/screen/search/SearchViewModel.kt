package com.snote.app.ui.screen.search

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import com.snote.app.data.repository.DataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@Stable
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: DataRepository
) : ViewModel() {

    // 搜索关键词
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    // 搜索结果
    private val _results = MutableStateFlow<List<DataRepository.SearchResult>>(emptyList())
    val results: StateFlow<List<DataRepository.SearchResult>> = _results.asStateFlow()

    // 是否已搜索
    private val _hasSearched = MutableStateFlow(false)
    val hasSearched: StateFlow<Boolean> = _hasSearched.asStateFlow()

    // 筛选类型（多选），默认全部选中
    private val _activeFilters = MutableStateFlow(setOf("title", "content", "file"))
    val activeFilters: StateFlow<Set<String>> = _activeFilters.asStateFlow()

    /**
     * 更新搜索关键词
     */
    fun updateQuery(newQuery: String) {
        _query.value = newQuery
    }

    /**
     * 执行搜索
     */
    fun search() {
        val q = _query.value.trim()
        if (q.isBlank()) {
            _results.value = emptyList()
            _hasSearched.value = false
            return
        }
        _results.value = repository.search(q)
        _hasSearched.value = true
    }

    /**
     * 清空搜索
     */
    fun clearSearch() {
        _query.value = ""
        _results.value = emptyList()
        _hasSearched.value = false
    }

    /**
     * 切换筛选类型
     */
    fun toggleFilter(type: String) {
        val current = _activeFilters.value
        _activeFilters.value = if (type in current) current - type else current + type
    }
}
