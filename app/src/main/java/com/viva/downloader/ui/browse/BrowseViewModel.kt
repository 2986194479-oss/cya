package com.viva.downloader.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viva.downloader.data.Discussion
import com.viva.downloader.data.FlarumApi
import com.viva.downloader.data.Tag
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
import kotlinx.coroutines.withContext

data class BrowseUiState(
    val tags: List<Tag> = emptyList(),
    val selectedTagSlug: String? = null,
    val discussions: List<Discussion> = emptyList(),
    val searchQuery: String = "",
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = true,
    val videoOnly: Boolean = false,
)

class BrowseViewModel : ViewModel() {

    private val _state = MutableStateFlow(BrowseUiState())
    val state: StateFlow<BrowseUiState> = _state.asStateFlow()

    private var offset = 0
    private val pageSize = 20
    private var detectJob: Job? = null
    private var searchJob: Job? = null

    init {
        loadTags()
        refresh()
    }

    private fun loadTags() {
        viewModelScope.launch {
            try {
                val tags = FlarumApi.fetchTags()
                _state.update { it.copy(tags = tags) }
            } catch (_: Exception) { }
        }
    }

    fun selectTag(tagSlug: String?) {
        if (tagSlug == VIDEO_SLUG) {
            _state.update { it.copy(selectedTagSlug = tagSlug, videoOnly = true, searchQuery = "") }
            startLoadingMoreForVideo()
        } else {
            if (_state.value.selectedTagSlug == tagSlug) return
            _state.update { it.copy(selectedTagSlug = tagSlug, videoOnly = false, searchQuery = "") }
            refresh()
        }
    }

    fun onSearchChange(query: String) {
        val trimmed = query.trim()
        _state.update { it.copy(searchQuery = trimmed) }
        searchJob?.cancel()
        if (trimmed.isBlank()) return

        // 先清空列表，再从服务端翻页加载并匹配
        offset = 0
        _state.update { it.copy(loading = true, loadingMore = false, discussions = emptyList(), hasMore = true, videoOnly = false) }
        searchJob = viewModelScope.launch {
            var page = 0
            while (page < 10) {  // 最多翻 10 页
                if (_state.value.searchQuery.isBlank()) break
                val (list, more) = FlarumApi.listDiscussions(page * pageSize, pageSize, tagSlug = null)
                _state.update {
                    val existing = it.discussions.map { d -> d.id }.toSet()
                    val newOnes = list.filterNot { d -> d.id in existing }
                    it.copy(
                        discussions = it.discussions + newOnes,
                        loading = false,
                        loadingMore = false,
                        hasMore = more,
                    )
                }
                startDetectVideos(list)
                page++
                if (!more) break
            }
        }
    }

    companion object {
        const val VIDEO_SLUG = "__video__"
    }

    fun refresh() {
        offset = 0
        detectJob?.cancel()
        searchJob?.cancel()
        _state.update { it.copy(loading = true, error = null, discussions = emptyList()) }
        viewModelScope.launch {
            try {
                val tag = _state.value.selectedTagSlug?.takeUnless { it == VIDEO_SLUG }
                val (list, hasMore) = FlarumApi.listDiscussions(0, pageSize, tag)
                offset = list.size
                _state.update {
                    it.copy(
                        discussions = list.distinctBy { d -> d.id },
                        loading = false,
                        hasMore = hasMore,
                        error = null,
                    )
                }
                startDetectVideos(list)
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
                val tag = _state.value.selectedTagSlug?.takeUnless { it == VIDEO_SLUG }
                val (list, hasMore) = FlarumApi.listDiscussions(offset, pageSize, tag)
                offset += list.size
                _state.update {
                    val existing = it.discussions.map { d -> d.id }.toSet()
                    val newOnes = list.filter { d -> d.id !in existing }
                    it.copy(
                        discussions = it.discussions + newOnes,
                        loadingMore = false,
                        hasMore = hasMore,
                        error = null,
                    )
                }
                startDetectVideos(list)
            } catch (e: Exception) {
                _state.update { it.copy(loadingMore = false, error = e.message ?: "加载失败") }
            }
        }
    }

    /** 视频模式：自动翻页拉取全部数据，前台筛选 hasVideo */
    private fun startLoadingMoreForVideo() {
        searchJob?.cancel()
        offset = 0
        _state.update { it.copy(loading = true, loadingMore = false, discussions = emptyList(), hasMore = true) }
        searchJob = viewModelScope.launch {
            var page = 0
            while (page < 10) {
                val (list, more) = FlarumApi.listDiscussions(page * pageSize, pageSize, tagSlug = null)
                _state.update {
                    val existing = it.discussions.map { d -> d.id }.toSet()
                    val newOnes = list.filterNot { d -> d.id in existing }
                    it.copy(
                        discussions = it.discussions + newOnes,
                        loading = false,
                        loadingMore = false,
                        hasMore = more,
                    )
                }
                startDetectVideos(list)
                page++
                if (!more) break
            }
        }
    }

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
                                try { FlarumApi.hasVideoInDiscussion(disc.id) }
                                catch (e: Exception) { false }
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