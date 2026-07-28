package com.snote.app.ui.screen.settings

import androidx.lifecycle.ViewModel
import com.snote.app.data.repository.DataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    init {
        _dataDirPath.value = repository.getDataDirPath()
    }
}
