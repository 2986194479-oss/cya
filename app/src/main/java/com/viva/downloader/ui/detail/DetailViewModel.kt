package com.viva.downloader.ui.detail

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viva.downloader.data.Attachment
import com.viva.downloader.data.AttachmentParser
import com.viva.downloader.data.FlarumApi
import com.viva.downloader.data.NotLoggedInException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DetailUiState(
    val inputUrl: String = "",
    val discussionId: String? = null,
    val title: String = "",
    val videos: List<Attachment> = emptyList(),
    val selected: Set<String> = emptySet(),
    val loading: Boolean = false,
    val downloading: Boolean = false,
    val progress: Int = 0,
    val doneCount: Int = 0,
    val error: String? = null,
    val message: String? = null,
    val playingAttachment: Attachment? = null,
)

class DetailViewModel : ViewModel() {

    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    fun onInputChange(text: String) {
        _state.update { it.copy(inputUrl = text, error = null) }
    }

    fun onOpenDiscussion(discussionId: String, title: String) {
        _state.update {
            it.copy(
                discussionId = discussionId,
                title = title,
                inputUrl = "",
            )
        }
        scan()
    }

    fun scan() {
        val id = _state.value.discussionId
            ?: AttachmentParser.extractDiscussionId(_state.value.inputUrl.trim())
        if (id == null) {
            _state.update { it.copy(error = "无效的帖子链接或 ID") }
            return
        }
        _state.update { it.copy(discussionId = id, loading = true, error = null, message = null, videos = emptyList(), selected = emptySet()) }
        viewModelScope.launch {
            try {
                val videos = FlarumApi.listVideos(id)
                _state.update {
                    it.copy(
                        loading = false,
                        videos = videos,
                        selected = videos.map { a -> a.uuid }.toSet(),
                        message = if (videos.isEmpty()) "该帖子未发现视频附件" else null,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "识别失败") }
            }
        }
    }

    fun toggleSelect(uuid: String) {
        _state.update {
            val newSet = if (uuid in it.selected) it.selected - uuid else it.selected + uuid
            it.copy(selected = newSet)
        }
    }

    fun playVideo(attachment: Attachment) {
        _state.update { it.copy(playingAttachment = attachment) }
    }

    fun stopPlaying() {
        _state.update { it.copy(playingAttachment = null) }
    }

    fun selectAll() {
        _state.update { it.copy(selected = it.videos.map { a -> a.uuid }.toSet()) }
    }

    fun selectNone() {
        _state.update { it.copy(selected = emptySet()) }
    }

    fun downloadSelected(context: Context) {
        val toDownload = _state.value.videos.filter { it.uuid in _state.value.selected }
        if (toDownload.isEmpty()) return
        if (_state.value.downloading) return

        _state.update { it.copy(downloading = true, progress = 0, doneCount = 0, error = null, message = null) }
        viewModelScope.launch {
            var done = 0
            for (attachment in toDownload) {
                try {
                    val bytes = FlarumApi.downloadAttachment(attachment)
                    val fileName = saveToMediaStore(context, attachment, bytes)
                    done++
                    _state.update {
                        it.copy(
                            progress = (done * 100) / toDownload.size,
                            doneCount = done,
                            message = "已下载 $fileName",
                        )
                    }
                } catch (e: NotLoggedInException) {
                    _state.update { it.copy(downloading = false, error = "未登录或登录已过期，请先登录") }
                    return@launch
                } catch (e: Exception) {
                    _state.update {
                        it.copy(
                            downloading = false,
                            error = "${attachment.filename}: ${e.message ?: "下载失败"}",
                            progress = (done * 100) / toDownload.size,
                            doneCount = done,
                        )
                    }
                    return@launch
                }
            }
            _state.update {
                it.copy(
                    downloading = false,
                    progress = 100,
                    doneCount = done,
                    message = "全部下载完成（$done 个）",
                )
            }
        }
    }

    private suspend fun saveToMediaStore(context: Context, attachment: Attachment, bytes: ByteArray): String =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val mime = when (attachment.ext) {
                "mp4" -> "video/mp4"
                "mov" -> "video/quicktime"
                "webm" -> "video/webm"
                "m4v" -> "video/x-m4v"
                "avi" -> "video/x-msvideo"
                "mkv" -> "video/x-matroska"
                else -> "video/*"
            }
            val displayName = attachment.filename

            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, mime)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/VivaDownloader")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            val uri = resolver.insert(collection, values)
                ?: throw Exception("无法创建媒体文件")

            resolver.openOutputStream(uri)?.use { out ->
                out.write(bytes)
            } ?: throw Exception("无法写入文件")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            displayName
        }
}
