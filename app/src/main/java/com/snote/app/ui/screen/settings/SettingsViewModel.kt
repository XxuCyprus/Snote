package com.snote.app.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snote.app.data.repository.DataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 设置页面ViewModel
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: DataRepository
) : ViewModel() {

    // 数据目录路径
    private val _dataDirPath = MutableStateFlow("")
    val dataDirPath: StateFlow<String> = _dataDirPath.asStateFlow()

    // 恢复结果提示
    private val _recoveryResult = MutableStateFlow<String?>(null)
    val recoveryResult: StateFlow<String?> = _recoveryResult.asStateFlow()

    init {
        _dataDirPath.value = repository.getDataDirPath()
    }

    /** 消费恢复结果提示，调用后重置为 null */
    fun consumeRecoveryResult() {
        _recoveryResult.value = null
    }

    fun recoverOrphanedData() {
        viewModelScope.launch {
            val count = repository.scanAndRecoverOrphanedData()
            _recoveryResult.value = if (count > 0) "已恢复 $count 个笔记本"
                                  else "未发现可恢复的数据"
        }
    }
}
