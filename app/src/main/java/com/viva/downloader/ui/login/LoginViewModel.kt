package com.viva.downloader.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viva.downloader.data.FlarumApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class LoginUiState(
    val loggedIn: Boolean = false,
    val identification: String = "",
    val password: String = "",
    val submitting: Boolean = false,
    val error: String? = null,
)

class LoginViewModel : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    fun onIdentificationChange(text: String) {
        _state.update { it.copy(identification = text, error = null) }
    }

    fun onPasswordChange(text: String) {
        _state.update { it.copy(password = text, error = null) }
    }

    /** 原生登录：POST /login */
    fun login() {
        val ident = _state.value.identification.trim()
        val pwd = _state.value.password
        if (ident.isEmpty() || pwd.isEmpty()) {
            _state.update { it.copy(error = "请输入账号和密码") }
            return
        }
        // 原子 check-and-set：避免竞态（getAndUpdate 返回旧状态，判断旧状态是否已 submitting）
        val already = _state.getAndUpdate { if (it.submitting) it else it.copy(submitting = true, error = null) }
        if (!already.submitting) {
            viewModelScope.launch {
                try {
                    val ok = FlarumApi.login(ident, pwd)
                    if (ok) {
                        _state.update { it.copy(loggedIn = true, submitting = false, password = "") }
                    } else {
                        _state.update { it.copy(submitting = false, error = "账号或密码错误") }
                    }
                } catch (e: Exception) {
                    _state.update { it.copy(submitting = false, error = e.message ?: "登录失败") }
                }
            }
        }
    }

    /** 轮询登录态（WebView 方式时使用） */
    fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                val loggedIn = try {
                    SessionChecker.checkLoggedIn()
                } catch (e: Exception) {
                    false
                }
                _state.update { it.copy(loggedIn = loggedIn) }
                if (loggedIn) break
                delay(1500)
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    fun logout() {
        stopPolling()
        SessionChecker.clearSession()
        _state.update { it.copy(loggedIn = false, password = "") }
    }

    override fun onCleared() {
        stopPolling()
        super.onCleared()
    }
}
