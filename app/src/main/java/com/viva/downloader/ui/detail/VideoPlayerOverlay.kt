package com.viva.downloader.ui.detail

import android.annotation.SuppressLint
import android.webkit.CookieManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.viva.downloader.data.Attachment
import com.viva.downloader.data.FlarumApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 全屏视频播放器（覆盖层），用于在线观看后决定是否下载。
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerOverlay(
    attachment: Attachment,
    onClose: () -> Unit,
) {
    var streamUrl by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(attachment) {
        try {
            val url = withContext(Dispatchers.IO) { FlarumApi.buildStreamUrl(attachment) }
            streamUrl = url
        } catch (e: Exception) {
            error = e.message ?: "无法获取播放地址"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        when {
            error != null -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("播放失败：$error", color = Color.White)
                }
            }
            streamUrl != null -> {
                VideoPlayer(url = streamUrl!!)
            }
            else -> {
                Text("加载中…", color = Color.White, modifier = Modifier.align(Alignment.Center))
            }
        }

        // 顶部关闭按钮 + 文件名
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.5f), androidx.compose.foundation.shape.CircleShape),
        ) {
            Icon(Icons.Default.Close, contentDescription = "关闭", tint = Color.White)
        }

        Text(
            attachment.filename,
            color = Color.White,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp),
        )
    }
}

@SuppressLint("UnsafeOptInUsageError")
@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(url: String) {
    val context = LocalContext.current

    val player = remember {
        val dataSourceFactory = DataSource.Factory {
            val http = DefaultHttpDataSource.Factory().createDataSource()
            val cookie = CookieManager.getInstance().getCookie(url) ?: ""
            if (cookie.isNotEmpty()) {
                http.setRequestProperty("Cookie", cookie)
            }
            http.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            http
        }

        ExoPlayer.Builder(context).build().apply {
            val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(url))
            setMediaSource(mediaSource)
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}
