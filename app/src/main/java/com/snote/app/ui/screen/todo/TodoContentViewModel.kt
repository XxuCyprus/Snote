package com.snote.app.ui.screen.todo

import android.media.MediaRecorder
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snote.app.data.model.ContentItem
import com.snote.app.data.model.ContentType
import com.snote.app.data.repository.TodoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    // ==================== 录音 ====================

    private val _showRecorderDialog = MutableStateFlow(false)
    val showRecorderDialog: StateFlow<Boolean> = _showRecorderDialog.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingSeconds = MutableStateFlow(0)
    val recordingSeconds: StateFlow<Int> = _recordingSeconds.asStateFlow()

    private val _recorderError = MutableStateFlow<String?>(null)
    val recorderError: StateFlow<String?> = _recorderError.asStateFlow()
    fun clearRecorderError() { _recorderError.value = null }

    private val _recordingCompleted = MutableStateFlow(false)
    val recordingCompleted: StateFlow<Boolean> = _recordingCompleted.asStateFlow()

    private var mediaRecorder: MediaRecorder? = null
    private var recordingJob: Job? = null
    private var currentRecordingPath: String? = null

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

    fun switchToRecorder() {
        _showAddContentDialog.value = false
        _showRecorderDialog.value = true
        _recordingCompleted.value = false
    }

    fun startRecording() {
        try {
            val dataDir = java.io.File(todoRepository.getDataDirPath())
            if (!dataDir.exists()) dataDir.mkdirs()
            val todoDir = java.io.File(dataDir, "todos")
            if (!todoDir.exists()) todoDir.mkdirs()
            val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
            val fileName = "audio_$timestamp.m4a"
            val file = java.io.File(todoDir, fileName)
            currentRecordingPath = "todos/$fileName"

            @Suppress("DEPRECATION")
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(96000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            _isRecording.value = true
            _recordingSeconds.value = 0
            recordingJob = viewModelScope.launch {
                while (true) {
                    delay(1000)
                    _recordingSeconds.value++
                }
            }
        } catch (e: Exception) {
            _isRecording.value = false
            mediaRecorder?.release()
            mediaRecorder = null
            _recorderError.value = "录音启动失败: ${e.message ?: "未知错误"}"
        }
    }

    fun stopRecording() {
        if (!_isRecording.value) return
        try {
            mediaRecorder?.apply { stop(); release() }
            mediaRecorder = null
            recordingJob?.cancel()
            recordingJob = null
            _isRecording.value = false
            _recordingCompleted.value = true
        } catch (_: Exception) {
            mediaRecorder?.release()
            mediaRecorder = null
            _isRecording.value = false
        }
    }

    fun cancelRecording() {
        if (_isRecording.value) {
            try { mediaRecorder?.apply { stop(); release() } } catch (_: Exception) {}
            mediaRecorder = null
            recordingJob?.cancel()
            recordingJob = null
            _isRecording.value = false
        }
        val recordedPath = currentRecordingPath
        if (recordedPath != null) {
            java.io.File(todoRepository.getDataDirPath(), recordedPath).delete()
        }
        currentRecordingPath = null
        _showRecorderDialog.value = false
    }

    fun saveRecording() {
        val path = currentRecordingPath ?: return
        viewModelScope.launch {
            val item = ContentItem(type = ContentType.AUDIO, content = path, order = _items.value.size)
            todoRepository.addTodoItem(sectionId, item)
            refreshItems()
        }
        currentRecordingPath = null
        _showRecorderDialog.value = false
    }

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

    fun addFileContent(uri: Uri) {
        viewModelScope.launch {
            todoRepository.addFileContent(sectionId, uri)
            refreshItems()
        }
    }

    fun requestComplete(item: ContentItem) { _showConfirmDialog.value = item }
    fun dismissConfirm() { _showConfirmDialog.value = null }

    fun confirmComplete() {
        val item = _showConfirmDialog.value ?: return
        viewModelScope.launch {
            if (sectionId == "unfinished") todoRepository.markItemDone(item.id)
            else todoRepository.undoComplete(item.id)
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
