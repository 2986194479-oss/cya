package com.viva.downloader.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viva.downloader.data.Attachment

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("视频提取", fontWeight = FontWeight.Bold, color = Color(0xFF4D698E)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF6F1E5)),
            )
        },
        containerColor = Color(0xFFF6F1E5),
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Spacer(Modifier.height(4.dp))
                // 输入框
                OutlinedTextField(
                    value = state.inputUrl,
                    onValueChange = viewModel::onInputChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("粘贴帖子链接或 ID") },
                    placeholder = { Text("https://bbs.viva-la-vita.org/d/12345") },
                    singleLine = true,
                    enabled = !state.loading && !state.downloading,
                )
            }

            item {
                Button(
                    onClick = { viewModel.scan() },
                    enabled = !state.loading && !state.downloading,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4D698E)),
                ) {
                    if (state.loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                    } else {
                        Text("识别视频", color = Color.White, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // 标题
            if (state.title.isNotEmpty()) {
                item {
                    Text(
                        state.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFF262019),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // 错误
            state.error?.let { err ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF0EE)),
                    ) {
                        Text(err, modifier = Modifier.padding(14.dp), color = Color(0xFFCC2E0C), fontSize = 14.sp)
                    }
                }
            }

            // 消息
            state.message?.let { msg ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF4EA)),
                    ) {
                        Text(msg, modifier = Modifier.padding(14.dp), color = Color(0xFF2E7D32), fontSize = 14.sp)
                    }
                }
            }

            // 视频列表
            if (state.videos.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "发现 ${state.videos.size} 个视频",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF262019),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            androidx.compose.material3.TextButton(onClick = { viewModel.selectAll() }) {
                                Text("全选")
                            }
                            androidx.compose.material3.TextButton(onClick = { viewModel.selectNone() }) {
                                Text("全不选")
                            }
                        }
                    }
                }

                items(state.videos, key = { it.uuid }) { attachment ->
                    VideoItem(
                        attachment = attachment,
                        selected = attachment.uuid in state.selected,
                        onToggle = { viewModel.toggleSelect(attachment.uuid) },
                        onPlay = { viewModel.playVideo(attachment) },
                    )
                }

                // 下载按钮
                item {
                    Button(
                        onClick = { viewModel.downloadSelected(context) },
                        enabled = !state.downloading && state.selected.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE0532F)),
                    ) {
                        if (state.downloading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.White,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("下载中 ${state.progress}%", color = Color.White)
                        } else {
                            Icon(Icons.Default.Download, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("下载所选（${state.selected.size}）", color = Color.White, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                if (state.downloading) {
                    item {
                        LinearProgressIndicator(
                            progress = state.progress / 100f,
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFFE0532F),
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(20.dp)) }
        }

        // 在线播放覆盖层
        state.playingAttachment?.let { attachment ->
            VideoPlayerOverlay(
                attachment = attachment,
                onClose = { viewModel.stopPlaying() },
            )
        }
    }
}
}

@Composable
private fun VideoItem(
    attachment: Attachment,
    selected: Boolean,
    onToggle: () -> Unit,
    onPlay: () -> Unit,
) {
    Card(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Color(0xFFFFF3EC) else Color.White,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = selected, onCheckedChange = { onToggle() })
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    attachment.filename,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color(0xFF262019),
                )
                Text(
                    (attachment.sizeLabel ?: "") + if (attachment.sizeLabel != null) " · " else "" + attachment.ext.uppercase(),
                    fontSize = 12.sp,
                    color = Color(0xFF8A8170),
                )
            }
            IconButton(onClick = onPlay) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "在线观看",
                    tint = Color(0xFF4D698E),
                )
            }
        }
    }
}
