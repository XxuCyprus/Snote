package com.snote.app.ui.screen.reader

import android.net.Uri
import android.media.MediaRecorder
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snote.app.data.model.Chapter
import com.snote.app.data.model.ContentItem
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
class ReaderViewModel @Inject constructor(
    private val repository: DataRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val notebookId: String = savedStateHandle.get<String>("notebookId") ?: ""
    private val initialChapterId: String = savedStateHandle.get<String>("chapterId") ?: ""

    // 主题色：与首页 NotebookAdapter 相同的确定性算法，从 notebookId 推导
    val themeColor: Color = run {
        val iconColors = intArrayOf(
            0xFF7965AF.toInt(), // 紫
            0xFF3586D7.toInt(), // 蓝
            0xFF1F978B.toInt(), // 青
            0xFFE9661F.toInt(), // 橙
            0xFFCD4242.toInt(), // 红
            0xFF8B3AAD.toInt(), // 深紫
            0xFF478D4B.toInt(), // 绿
            0xFF515FB5.toInt(), // 靛蓝
        )
        val idx = Math.abs(notebookId.hashCode()) % iconColors.size
        Color(iconColors[idx])
    }

    private val _notebook = MutableStateFlow<Notebook?>(null)
    val notebook: StateFlow<Notebook?> = _notebook.asStateFlow()

    private val _currentChapter = MutableStateFlow<Chapter?>(null)
    val currentChapter: StateFlow<Chapter?> = _currentChapter.asStateFlow()

    private val _showDrawer = MutableStateFlow(false)
    val showDrawer: StateFlow<Boolean> = _showDrawer.asStateFlow()

    private val _showAddContentDialog = MutableStateFlow(false)
    val showAddContentDialog: StateFlow<Boolean> = _showAddContentDialog.asStateFlow()

    private val _showAddChapterDialog = MutableStateFlow(false)
    val showAddChapterDialog: StateFlow<Boolean> = _showAddChapterDialog.asStateFlow()

    private val _parentChapterId = MutableStateFlow<String?>(null)
    val parentChapterId: StateFlow<String?> = _parentChapterId.asStateFlow()

    // 内容列表刷新令牌 - 每次内容变更递增，强制Compose重组
    private val _contentRefreshToken = MutableStateFlow(0L)
    val contentRefreshToken: StateFlow<Long> = _contentRefreshToken.asStateFlow()

    // 章节刷新令牌 - 确保添加/删除章节后目录树实时更新
    private val _chapterRefreshToken = MutableStateFlow(0L)
    val chapterRefreshToken: StateFlow<Long> = _chapterRefreshToken.asStateFlow()

    // 展开的章节 ID 集合 - 存 ViewModel 避免 LazyColumn 滚动/重建时丢失展开状态
    private val _expandedChapterIds = MutableStateFlow<Set<String>>(emptySet())
    val expandedChapterIds: StateFlow<Set<String>> = _expandedChapterIds.asStateFlow()

    // 当前章节的祖先链 [(id, title)] — 用于面包屑导航，根在前、当前在末
    private val _chapterAncestors = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val chapterAncestors: StateFlow<List<Pair<String, String>>> = _chapterAncestors.asStateFlow()

    // 打开抽屉时需滚动到的目标章节索引
    private val _drawerScrollTarget = MutableStateFlow<String?>(null)
    val drawerScrollTarget: StateFlow<String?> = _drawerScrollTarget.asStateFlow()
    fun consumeDrawerScrollTarget() { _drawerScrollTarget.value = null }

    // 标记过滤: false=全部, true=仅已标记章节
    private val _showMarkedOnly = MutableStateFlow(false)
    val showMarkedOnly: StateFlow<Boolean> = _showMarkedOnly.asStateFlow()
    fun setShowMarkedOnly(v: Boolean) {
        _showMarkedOnly.value = v
        if (v) expandMarkedPaths()
    }

    private fun expandMarkedPaths() {
        val nb = _notebook.value ?: return
        val set = _expandedChapterIds.value.toMutableSet()
        collectMarkedPaths(nb.chapters, set)
        _expandedChapterIds.value = set
    }

    private fun collectMarkedPaths(chapters: List<Chapter>, set: MutableSet<String>) {
        for (ch in chapters) {
            if (ch.isMarked || ch.children.any { hasMarkedDescendant(it) }) {
                if (ch.children.isNotEmpty()) set.add(ch.id)
            }
            collectMarkedPaths(ch.children, set)
        }
    }

    private fun hasMarkedDescendant(chapter: Chapter): Boolean {
        if (chapter.isMarked) return true
        return chapter.children.any { hasMarkedDescendant(it) }
    }

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
    private var recordingJob: kotlinx.coroutines.Job? = null
    private var currentRecordingPath: String? = null

    fun showRecorderDialog() {
        _showRecorderDialog.value = true
        _recordingCompleted.value = false
    }
    fun hideRecorderDialog() {
        if (!_isRecording.value) _showRecorderDialog.value = false
    }

    fun switchToRecorder() {
        _showAddContentDialog.value = false
        _showRecorderDialog.value = true
        _recordingCompleted.value = false
    }

    fun startRecording() {
        val chapterId = _currentChapter.value?.id ?: return
        try {
            val nbDir = repository.getNotebookDir(notebookId)
            if (!nbDir.exists()) nbDir.mkdirs()
            val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
            val fileName = "audio_$timestamp.m4a"
            val file = java.io.File(nbDir, fileName)
            currentRecordingPath = "${notebookId}/$fileName"

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
                    kotlinx.coroutines.delay(1000)
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
        // 删除录音文件（录制中取消或录制完成后取消都删除）
        val recordedPath = currentRecordingPath
        if (recordedPath != null) {
            val dir = repository.getAbsolutePath(recordedPath).parent
            val name = recordedPath.substringAfter("/")
            if (dir != null) java.io.File(dir, name).delete()
        }
        currentRecordingPath = null
        _showRecorderDialog.value = false
    }

    fun saveRecording() {
        val path = currentRecordingPath ?: return
        viewModelScope.launch {
            repository.addAudioContentFromFile(
                notebookId,
                _currentChapter.value?.id ?: return@launch,
                path
            )
            refreshAfterContentChange(_currentChapter.value?.id ?: return@launch)
        }
        currentRecordingPath = null
        _showRecorderDialog.value = false
    }

    fun isChapterExpanded(chapterId: String): Boolean = chapterId in _expandedChapterIds.value
    fun toggleChapterExpanded(chapterId: String) {
        val set = _expandedChapterIds.value.toMutableSet()
        if (chapterId in set) set.remove(chapterId) else set.add(chapterId)
        _expandedChapterIds.value = set
    }

    /**
     * 查找当前章节的完整祖先链 [根 → ... → 当前]，用于面包屑导航
     */
    fun findChapterAncestors(chapterId: String): List<Pair<String, String>> {
        val nb = _notebook.value ?: return emptyList()
        val path = mutableListOf<Pair<String, String>>()
        findPath(nb.chapters, chapterId, path)
        return path
    }

    private fun findPath(chapters: List<Chapter>, targetId: String, path: MutableList<Pair<String, String>>): Boolean {
        for (ch in chapters) {
            path.add(ch.id to ch.title)
            if (ch.id == targetId) return true
            if (findPath(ch.children, targetId, path)) return true
            path.removeAt(path.lastIndex)
        }
        return false
    }

    private fun updateAncestors() {
        val id = _currentChapter.value?.id ?: return
        _chapterAncestors.value = findChapterAncestors(id)
    }

    /**
     * 展开祖先链并标记滚动目标 — 打开抽屉时调用
     */
    fun expandAncestorsAndLocate() {
        val currentId = _currentChapter.value?.id ?: return
        val ancestors = findChapterAncestors(currentId)
        if (ancestors.isEmpty()) return
        val allAncestorIds = ancestors.map { it.first }.toSet()
        val set = _expandedChapterIds.value.toMutableSet()
        set.addAll(allAncestorIds)
        _expandedChapterIds.value = set
        _drawerScrollTarget.value = currentId
    }

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            repository.initializeDataDir()
            val nb = repository.getNotebookById(notebookId)
            _notebook.value = nb

            if (nb != null) {
                // 默认展开顶级章节
                _expandedChapterIds.value = nb.chapters.map { it.id }.toSet()

                val chapterId = if (initialChapterId.isNotEmpty()) {
                    initialChapterId
                } else {
                    nb.lastReadChapterId
                }
                if (chapterId != null) {
                    _currentChapter.value = findChapter(nb.chapters, chapterId)
                } else {
                    // 无历史位置 → 打开目录供用户选择
                    _showDrawer.value = true
                    _currentChapter.value = nb.chapters.firstOrNull()
                }
                updateAncestors()
            }
        }
    }

    fun switchChapter(chapterId: String) {
        val nb = _notebook.value ?: return
        val chapter = findChapter(nb.chapters, chapterId)
        _currentChapter.value = chapter
        _showDrawer.value = false
        updateAncestors()
        viewModelScope.launch {
            repository.updateLastReadChapterId(notebookId, chapterId)
        }
    }

    fun toggleDrawer() {
        val opening = !_showDrawer.value
        _showDrawer.value = opening
        if (opening) expandAncestorsAndLocate()
    }

    fun closeDrawer() {
        _showDrawer.value = false
    }

    fun showAddContentDialog() { _showAddContentDialog.value = true }
    fun hideAddContentDialog() { _showAddContentDialog.value = false }

    fun showAddChapterDialog(parentId: String? = null) {
        _parentChapterId.value = parentId
        _showAddChapterDialog.value = true
    }
    fun hideAddChapterDialog() { _showAddChapterDialog.value = false }

    fun editContentText(itemId: String, newText: String) {
        val chapterId = _currentChapter.value?.id ?: return
        viewModelScope.launch {
            repository.updateTextContent(notebookId, chapterId, itemId, newText)
            refreshAfterContentChange(chapterId)
        }
    }

    fun updateImageContent(itemId: String, newPath: String) {
        val chapterId = _currentChapter.value?.id ?: return
        viewModelScope.launch {
            repository.updateImageContentPath(notebookId, chapterId, itemId, newPath)
            refreshAfterContentChange(chapterId)
        }
    }

    fun toggleContentMarked(itemId: String) {
        val chapterId = _currentChapter.value?.id ?: return
        viewModelScope.launch {
            repository.toggleContentMarked(notebookId, chapterId, itemId)
            refreshAfterContentChange(chapterId)
        }
    }

    fun toggleChapterMarked(chapterId: String) {
        viewModelScope.launch {
            repository.toggleChapterMarked(notebookId, chapterId)
            fullRefresh()
        }
    }

    fun addChapter(title: String) {
        viewModelScope.launch {
            val newChapter = repository.addChapter(notebookId, title)
            fullRefresh()
            if (newChapter != null) {
                _currentChapter.value = findChapter(_notebook.value?.chapters ?: emptyList(), newChapter.id)
            }
            _showAddChapterDialog.value = false
        }
    }

    fun addChildChapter(parentId: String, title: String) {
        viewModelScope.launch {
            val newChapter = repository.addChildChapter(notebookId, parentId, title)
            fullRefresh()
            if (newChapter != null) {
                _currentChapter.value = findChapter(_notebook.value?.chapters ?: emptyList(), newChapter.id)
            }
            _showAddChapterDialog.value = false
        }
    }

    fun deleteChapter(chapterId: String) {
        viewModelScope.launch {
            repository.deleteChapter(notebookId, chapterId)
            fullRefresh()
            if (_currentChapter.value?.id == chapterId) {
                _currentChapter.value = _notebook.value?.chapters?.firstOrNull()
            }
        }
    }

    fun renameChapter(chapterId: String, newTitle: String) {
        viewModelScope.launch {
            val chapter = repository.findChapterInNotebook(notebookId, chapterId) ?: return@launch
            repository.updateChapter(notebookId, chapter.copy(title = newTitle))
            fullRefresh()
        }
    }

    fun swapChapters(chapterId1: String, chapterId2: String) {
        viewModelScope.launch {
            repository.swapChapters(notebookId, chapterId1, chapterId2)
            fullRefresh()
        }
    }

    fun addTextContent(text: String) {
        val chapterId = _currentChapter.value?.id ?: return
        viewModelScope.launch {
            repository.addTextContent(notebookId, chapterId, text)
            refreshAfterContentChange(chapterId)
            _showAddContentDialog.value = false
        }
    }

    fun addImageContent(uri: Uri) {
        val chapterId = _currentChapter.value?.id ?: return
        viewModelScope.launch {
            repository.addImageContent(notebookId, chapterId, uri)
            refreshAfterContentChange(chapterId)
        }
    }

    fun addVideoContent(uri: Uri) {
        val chapterId = _currentChapter.value?.id ?: return
        viewModelScope.launch {
            repository.addVideoContent(notebookId, chapterId, uri)
            refreshAfterContentChange(chapterId)
        }
    }

    fun addAudioContent(uri: Uri) {
        val chapterId = _currentChapter.value?.id ?: return
        viewModelScope.launch {
            repository.addAudioContent(notebookId, chapterId, uri)
            refreshAfterContentChange(chapterId)
        }
    }

    fun deleteContentItem(itemId: String) {
        val chapterId = _currentChapter.value?.id ?: return
        viewModelScope.launch {
            repository.deleteContentItem(notebookId, chapterId, itemId)
            refreshAfterContentChange(chapterId)
        }
    }

    fun swapContentItems(itemId1: String, itemId2: String) {
        val chapterId = _currentChapter.value?.id ?: return
        viewModelScope.launch {
            repository.swapContentItems(notebookId, chapterId, itemId1, itemId2)
            refreshAfterContentChange(chapterId)
        }
    }

    fun getAbsolutePath(relativePath: String): String {
        return repository.getAbsolutePath(relativePath).absolutePath
    }

    private fun fullRefresh() {
        val nb = repository.getNotebookById(notebookId) ?: return
        _notebook.value = nb
        _chapterRefreshToken.value++
    }

    private fun refreshAfterContentChange(chapterId: String) {
        val nb = repository.getNotebookById(notebookId) ?: return
        _notebook.value = nb
        val newChapter = findChapter(nb.chapters, chapterId)
        _currentChapter.value = newChapter
        _contentRefreshToken.value++
    }

    private fun findChapter(chapters: List<Chapter>, chapterId: String): Chapter? {
        for (chapter in chapters) {
            if (chapter.id == chapterId) return chapter
            val found = findChapter(chapter.children, chapterId)
            if (found != null) return found
        }
        return null
    }
}
