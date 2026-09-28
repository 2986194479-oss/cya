package com.viva.downloader.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viva.downloader.data.Discussion
import com.viva.downloader.data.Tag
import com.viva.downloader.ui.browse.BrowseViewModel.Companion.VIDEO_SLUG
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

private const val VIDEO_SLUG_VAL = VIDEO_SLUG

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    viewModel: BrowseViewModel,
    onOpenDiscussion: (Discussion) -> Unit,
    onOpenLogin: () -> Unit,
    onOpenUser: (Discussion) -> Unit,
    onOpenLog: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()

    // 滚动到底部自动加载更多
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .filter { it != null }
            .distinctUntilChanged()
            .collect { lastIndex ->
                if (lastIndex != null && lastIndex >= state.discussions.size - 5 && state.hasMore) {
                    viewModel.loadMore()
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("论坛浏览", fontWeight = FontWeight.Bold, color = Color(0xFF4D698E)) },
                actions = {
                    IconButton(onClick = onOpenLog) {
                        Icon(Icons.Default.Info, contentDescription = "日志")
                    }
                    IconButton(onClick = onOpenLogin) {
                        Icon(Icons.Default.Person, contentDescription = "登录")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF6F1E5)),
            )
        },
        containerColor = Color(0xFFF6F1E5),
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // 搜索框
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::onSearchChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                placeholder = { Text("搜索帖子标题…") },
                singleLine = true,
            )
            // 标签栏
            TagBar(
                tags = state.tags,
                selectedTagSlug = state.selectedTagSlug,
                onSelectTag = { viewModel.selectTag(it) },
            )

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.loading && state.discussions.isEmpty() -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    state.error != null && state.discussions.isEmpty() -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text("加载失败：${state.error}", color = Color(0xFFCC2E0C))
                            androidx.compose.material3.Button(onClick = { viewModel.refresh() }) {
                                Text("重试")
                            }
                        }
                    }
                    else -> {
                        var shown = state.discussions
                        if (state.videoOnly) {
                            shown = shown.filter { it.hasVideo }
                        }
                        if (state.searchQuery.isNotBlank()) {
                            val q = state.searchQuery.trim().lowercase()
                            shown = shown.filter { it.title.lowercase().contains(q) }
                        }
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(shown, key = { it.id }) { discussion ->
                                DiscussionItem(
                                    discussion = discussion,
                                    onClick = { onOpenDiscussion(discussion) },
                                    onAuthorClick = { onOpenUser(discussion) },
                                )
                            }
                            if (state.loadingMore) {
                                item {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TagBar(
    tags: List<Tag>,
    selectedTagSlug: String?,
    onSelectTag: (String?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 「全部」标签
        FilterChip(
            selected = selectedTagSlug == null && !VIDEO_SLUG_VAL.equals(selectedTagSlug),
            onClick = { onSelectTag(null) },
            label = { Text("全部") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = Color(0xFF4D698E),
                selectedLabelColor = Color.White,
            ),
        )

        // 「视频」标签
        FilterChip(
            selected = selectedTagSlug == VIDEO_SLUG_VAL,
            onClick = { onSelectTag(VIDEO_SLUG_VAL) },
            label = { Text("🎬 视频") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = Color(0xFFE0532F),
                selectedLabelColor = Color.White,
            ),
        )

        tags.forEach { tag ->
            TagChip(
                tag = tag,
                selected = selectedTagSlug == tag.slug,
                onClick = { onSelectTag(tag.slug) },
            )
        }
    }
}



@Composable
private fun TagChip(
    tag: Tag,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(tag.name) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Color(0xFF4D698E),
            selectedLabelColor = Color.White,
            containerColor = Color.White,
        ),
    )
}

@Composable
private fun DiscussionItem(
    discussion: Discussion,
    onClick: () -> Unit,
    onAuthorClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = discussion.title,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = Color(0xFF262019),
                )
                // 作者行（可点击进入主页）
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = discussion.authorDisplayName ?: discussion.authorUsername ?: "未知作者",
                        fontSize = 12.sp,
                        color = Color(0xFF4D698E),
                        modifier = Modifier.clickable(onClick = onAuthorClick),
                    )
                    Text(
                        text = " · 评论 ${discussion.commentCount}",
                        fontSize = 12.sp,
                        color = Color(0xFF8A8170),
                    )
                }
            }
            if (discussion.hasVideo) {
                Icon(
                    Icons.Default.VideoLibrary,
                    contentDescription = "含视频",
                    tint = Color(0xFFE0532F),
                )
                Text(
                    " 视频",
                    fontSize = 12.sp,
                    color = Color(0xFFE0532F),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
