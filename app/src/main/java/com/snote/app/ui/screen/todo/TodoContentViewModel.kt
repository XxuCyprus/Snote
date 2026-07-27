package com.snote.app.ui.screen.todo

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
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
class TodoContentViewModel @Inject constructor(
    private val todoRepository: TodoRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val sectionId: String = savedStateHandle.get<String>("sectionId") ?: "unfinished"

    private val _items = MutableStateFlow<List<ContentItem>>(emptyList())
    val items: StateFlow<List<ContentItem>> = _items.asStateFlow()

    val sectionTitle: String = if (sectionId == "unfinished") "未完成" else "已完成"

    private val _showAddContentDialog = MutableStateFlow(false)
    val showAddContentDialog: StateFlow<Boolean> = _showAddContentDialog.asStateFlow()

    private val _showConfirmDialog = MutableStateFlow<ContentItem?>(null)
    val showConfirmDialog: StateFlow<ContentItem?> = _showConfirmDialog.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            todoRepository.loadTodos()
            refreshItems()
        }
    }

    private fun refreshItems() {
        val board = todoRepository.getBoard()
        _items.value = if (sectionId == "unfinished") board.unfinished else board.finished
    }

    fun showAddContentDialog() { _showAddContentDialog.value = true }
    fun hideAddContentDialog() { _showAddContentDialog.value = false }

    fun addTextContent(text: String) {
        viewModelScope.launch {
            todoRepository.addTextContent(sectionId, text)
            refreshItems()
            _showAddContentDialog.value = false
        }
    }

    fun addImageContent(uri: Uri) {
        viewModelScope.launch {
            todoRepository.addImageContent(sectionId, uri)
            refreshItems()
        }
    }

    fun addVideoContent(uri: Uri) {
        viewModelScope.launch {
            todoRepository.addVideoContent(sectionId, uri)
            refreshItems()
        }
    }

    fun addAudioContent(uri: Uri) {
        viewModelScope.launch {
            todoRepository.addAudioContent(sectionId, uri)
            refreshItems()
        }
    }

    fun addFileContent(uri: Uri) {
        viewModelScope.launch {
            todoRepository.addFileContent(sectionId, uri)
            refreshItems()
        }
    }

    fun requestComplete(item: ContentItem) {
        _showConfirmDialog.value = item
    }

    fun dismissConfirm() {
        _showConfirmDialog.value = null
    }

    fun confirmComplete() {
        val item = _showConfirmDialog.value ?: return
        viewModelScope.launch {
            if (sectionId == "unfinished") {
                todoRepository.markItemDone(item.id)
            } else {
                todoRepository.undoComplete(item.id)
            }
            refreshItems()
            _showConfirmDialog.value = null
        }
    }

    fun deleteItem(itemId: String) {
        viewModelScope.launch {
            todoRepository.deleteTodoItem(itemId)
            refreshItems()
        }
    }

    fun swapItems(itemId1: String, itemId2: String) {
        viewModelScope.launch {
            todoRepository.swapTodoItems(itemId1, itemId2)
            refreshItems()
        }
    }

    fun renameTodoItem(itemId: String, newName: String) {
        viewModelScope.launch {
            todoRepository.renameTodoItem(itemId, newName)
            refreshItems()
        }
    }

    fun getAbsolutePath(relativePath: String): String {
        return "${todoRepository.getDataDirPath()}/$relativePath"
    }

    val isCompleted: Boolean get() = sectionId == "finished"
}
