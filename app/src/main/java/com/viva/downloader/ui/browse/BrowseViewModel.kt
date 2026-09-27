package com.viva.downloader.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viva.downloader.data.Discussion
import com.viva.downloader.data.FlarumApi
import com.viva.downloader.data.Tag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BrowseUiState(
    val tags: List<Tag> = emptyList(),
    val selectedTagSlug: String? = null,
    val discussions: List<Discussion> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = true,
)

class BrowseViewModel : ViewModel() {

    private val _state = MutableStateFlow(BrowseUiState())
    val state: StateFlow<BrowseUiState> = _state.asStateFlow()

    private var offset = 0
    private val pageSize = 20

    init {
        loadTags()
        refresh()
    }

    private fun loadTags() {
        viewModelScope.launch {
            try {
                val tags = FlarumApi.fetchTags()
                _state.update { it.copy(tags = tags) }
            } catch (_: Exception) {
                // 标签加载失败不影响列表
            }
        }
    }

    fun selectTag(tagSlug: String?) {
        if (_state.value.selectedTagSlug == tagSlug) return
        _state.update { it.copy(selectedTagSlug = tagSlug) }
        refresh()
    }

    fun refresh() {
        offset = 0
        _state.update { it.copy(loading = true, error = null, discussions = emptyList()) }
        viewModelScope.launch {
            try {
                val tag = _state.value.selectedTagSlug
                val (list, hasMore) = FlarumApi.listDiscussions(0, pageSize, tag)
                offset = list.size
                _state.update {
                    it.copy(
                        discussions = list,
                        loading = false,
                        hasMore = hasMore,
                        error = null,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "加载失败") }
            }
        }
    }

    fun loadMore() {
        if (_state.value.loading || _state.value.loadingMore || !_state.value.hasMore) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            try {
                val tag = _state.value.selectedTagSlug
                val (list, hasMore) = FlarumApi.listDiscussions(offset, pageSize, tag)
                offset += list.size
                _state.update {
                    it.copy(
                        discussions = it.discussions + list,
                        loadingMore = false,
                        hasMore = hasMore,
                        error = null,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loadingMore = false, error = e.message ?: "加载失败") }
            }
        }
    }
}
