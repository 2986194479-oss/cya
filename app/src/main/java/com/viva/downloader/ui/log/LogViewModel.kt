package com.viva.downloader.ui.log

import androidx.lifecycle.ViewModel
import com.viva.downloader.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LogViewModel : ViewModel() {

    private val _log = MutableStateFlow(AppLogger.read())
    val log: StateFlow<String> = _log.asStateFlow()

    fun refresh() {
        _log.value = AppLogger.read()
    }

    fun clear() {
        AppLogger.clear()
        _log.value = AppLogger.read()
    }
}
