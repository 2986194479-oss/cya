package com.viva.downloader.ui.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viva.downloader.data.Discussion
import com.viva.downloader.data.FlarumApi
import com.viva.downloader.data.ForumUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class UserUiState(
    val user: ForumUser? = null,
    val discussions: List<Discussion> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = true,
)

class UserViewModel : ViewModel() {

    private val _state = MutableStateFlow(UserUiState())
    val state: StateFlow<UserUiState> = _state.asStateFlow()

    private var userId: String? = null
    private var authorUsername: String? = null
    private var offset = 0
    private val pageSize = 20
    private var detectJob: Job? = null

    fun openUser(discussion: Discussion) {
        val id = discussion.authorId ?: return
        val username = discussion.authorUsername
        if (userId == id) return
        userId = id
        authorUsername = username
        offset = 0
        detectJob?.cancel()
        _state.update { UserUiState(loading = true) }
        viewModelScope.launch {
            try {
                val user = FlarumApi.fetchUser(id)
                _state.update { it.copy(user = user, loading = false, error = null) }
                loadDiscussionsInternal()
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "加载失败") }
            }
        }
    }

    fun refresh() {
        offset = 0
        _state.update { it.copy(loading = true, error = null, discussions = emptyList()) }
        loadDiscussionsInternal()
    }

    fun loadMore() {
        if (_state.value.loading || _state.value.loadingMore || !_state.value.hasMore) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            try {
                val (list, hasMore) = FlarumApi.listDiscussions(offset, pageSize, authorUsername = authorUsername)
                offset += list.size
                _state.update {
                    val existing = it.discussions.map { d -> d.id }.toSet()
                    val newOnes = list.filter { d -> d.id !in existing }
                    it.copy(discussions = it.discussions + newOnes, loadingMore = false, hasMore = hasMore, error = null)
                }
                startDetectVideos(list)
            } catch (e: Exception) {
                _state.update { it.copy(loadingMore = false, error = e.message ?: "加载失败") }
            }
        }
    }

    private fun loadDiscussionsInternal() {
        viewModelScope.launch {
            try {
                val (list, hasMore) = FlarumApi.listDiscussions(0, pageSize, authorUsername = authorUsername)
                offset = list.size
                _state.update {
                    it.copy(discussions = list.distinctBy { d -> d.id }, loading = false, hasMore = hasMore, error = null)
                }
                startDetectVideos(list)
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "加载失败") }
            }
        }
    }

    /** 后台检测评论里的视频（复用列表页逻辑） */
    private fun startDetectVideos(discussions: List<Discussion>) {
        detectJob?.cancel()
        detectJob = viewModelScope.launch(Dispatchers.IO) {
            val semaphore = Semaphore(3)
            coroutineScope {
                discussions
                    .filter { !it.hasVideo }
                    .map { disc ->
                        async {
                            val has = semaphore.withPermit {
                                try {
                                    FlarumApi.hasVideoInDiscussion(disc.id)
                                } catch (e: Exception) {
                                    false
                                }
                            }
                            disc to has
                        }
                    }
                    .forEach { deferred ->
                        val (disc, hasVideo) = deferred.await()
                        if (hasVideo) {
                            _state.update { st ->
                                val updated = st.discussions.map { d ->
                                    if (d.id == disc.id) d.copy(hasVideo = true) else d
                                }
                                st.copy(discussions = updated)
                            }
                        }
                    }
            }
        }
    }
}
