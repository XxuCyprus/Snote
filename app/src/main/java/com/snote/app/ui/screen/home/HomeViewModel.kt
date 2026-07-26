package com.snote.app.ui.screen.home

import android.os.Build
import android.os.Environment
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snote.app.data.model.Notebook
import com.snote.app.data.repository.DataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@Stable
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: DataRepository
) : ViewModel() {

    private val _notebooks = MutableStateFlow<List<Notebook>>(emptyList())
    val notebooks: StateFlow<List<Notebook>> = _notebooks.asStateFlow()

    // 首页滚动位置（companion object 跨 ViewModel 实例持久化）
    var savedScrollPosition
        get() = ScrollState.position
        set(value) { ScrollState.position = value }
    var savedScrollOffset
        get() = ScrollState.offset
        set(value) { ScrollState.offset = value }

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _showCreateDialog = MutableStateFlow(false)
    val showCreateDialog: StateFlow<Boolean> = _showCreateDialog.asStateFlow()

    private val _showEditDialog = MutableStateFlow(false)
    val showEditDialog: StateFlow<Boolean> = _showEditDialog.asStateFlow()

    private val _editingNotebook = MutableStateFlow<Notebook?>(null)
    val editingNotebook: StateFlow<Notebook?> = _editingNotebook.asStateFlow()

    // 是否需要请求存储权限弹窗
    private val _showStoragePermissionDialog = MutableStateFlow(false)
    val showStoragePermissionDialog: StateFlow<Boolean> = _showStoragePermissionDialog.asStateFlow()

    // 记录初始化时的权限状态，用于 ON_RESUME 时检测权限变更
    private var hadStoragePermission = false

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            repository.initializeDataDir()
            _notebooks.value = repository.getAllNotebooks()
            _isLoading.value = false

            hadStoragePermission = repository.hasFullStorageAccess()

            // 检测是否需要存储权限
            if (needsStoragePermission()) {
                _showStoragePermissionDialog.value = true
            }
        }
    }

    /**
     * Activity ON_RESUME 时调用。
     * 检测权限是否在设置页面中被授予，如果是则强制重新加载数据。
     */
    fun onAppResume() {
        val hasPerm = repository.hasFullStorageAccess()
        if (!hadStoragePermission && hasPerm) {
            hadStoragePermission = true
            viewModelScope.launch {
                repository.reinitialize()
                repository.initializeDataDir()
                _notebooks.value = repository.getAllNotebooks()
            }
        }
        hadStoragePermission = hasPerm
    }

    fun refresh() {
        _notebooks.value = repository.getAllNotebooks().sortedByDescending { it.createdAt }
    }

    /**
     * 获取笔记本的实时 lastReadChapterId（绕过 ViewModel 缓存，直接读仓储）
     */
    fun getLastReadChapterId(notebookId: String): String {
        val nb = repository.getNotebookById(notebookId)
        return nb?.lastReadChapterId ?: nb?.chapters?.firstOrNull()?.id ?: ""
    }

    fun showCreateDialog() { _showCreateDialog.value = true }
    fun hideCreateDialog() { _showCreateDialog.value = false }

    fun dismissStoragePermissionDialog() { _showStoragePermissionDialog.value = false }

    fun createNotebook(title: String, description: String) {
        viewModelScope.launch {
            repository.createNotebook(title, description)
            _notebooks.value = repository.getAllNotebooks()
            _showCreateDialog.value = false
        }
    }

    fun deleteNotebook(notebookId: String) {
        viewModelScope.launch {
            repository.deleteNotebook(notebookId)
            _notebooks.value = repository.getAllNotebooks()
        }
    }

    fun showEditDialog(notebook: Notebook) {
        _editingNotebook.value = notebook
        _showEditDialog.value = true
    }

    fun hideEditDialog() {
        _showEditDialog.value = false
        _editingNotebook.value = null
    }

    fun updateNotebook(notebookId: String, title: String, description: String) {
        viewModelScope.launch {
            repository.updateNotebookInfo(notebookId, title, description)
            _notebooks.value = repository.getAllNotebooks()
            _showEditDialog.value = false
            _editingNotebook.value = null
        }
    }

    fun getDataDirPath(): String = repository.getDataDirPath()

    private object ScrollState {
        var position = 0
        var offset = 0
    }

    /**
     * 是否需要请求"所有文件访问"权限（API 30+）
     */
    private fun needsStoragePermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        if (Environment.isExternalStorageManager()) return false
        // 检查是否存在旧安装遗留的数据文件（无权限时读不到，但可判断文件存在性）
        val dataDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val dataFile = java.io.File(java.io.File(dataDir, "Snote"), "snote_data.json")
        if (_notebooks.value.isEmpty() && dataFile.exists()) return true
        return false
    }
}
