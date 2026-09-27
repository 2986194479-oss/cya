package com.viva.downloader.data

/**
 * 论坛附件（视频）描述，从帖子 contentHtml 解析得到。
 */
data class Attachment(
    val uuid: String,
    val postId: String,
    val filename: String,
    val sizeLabel: String? = null,
    val postText: String? = null,
) {
    val isVideo: Boolean
        get() = filename.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS

    val ext: String
        get() = filename.substringAfterLast('.', "").lowercase()

    companion object {
        val VIDEO_EXTENSIONS = setOf("mp4", "mov", "webm", "m4v", "avi", "mkv", "flv", "wmv", "ts", "mts", "3gp")
    }
}

/**
 * 讨论（帖子）摘要，用于浏览列表。
 */
data class Discussion(
    val id: String,
    val title: String,
    val slug: String,
    val commentCount: Int,
    val createdAt: String,
    val lastPostedAt: String,
    val hasVideo: Boolean,
    val authorId: String? = null,
    val authorUsername: String? = null,
    val authorDisplayName: String? = null,
) {
    val url: String get() = "$BASE/d/$slug"
    val apiUrl: String get() = "$API_BASE/discussions/$id"

    companion object {
        const val BASE = "https://bbs.viva-la-vita.org"
        const val API_BASE = "$BASE/api"
    }
}

/**
 * 论坛用户（作者）摘要。
 */
data class ForumUser(
    val id: String,
    val username: String,
    val displayName: String?,
    val slug: String?,
    val avatarUrl: String?,
    val discussionCount: Int,
    val commentCount: Int,
)

/**
 * 帖子里的图片（照片）。
 */
data class PostImage(
    val url: String,
    val postId: String,
    val postText: String? = null,
)

/**
 * 论坛标签（分区）。
 */
data class Tag(
    val id: String,
    val slug: String,
    val name: String,
    val description: String,
    val color: String?,
    val discussionCount: Int,
    val isHidden: Boolean,
    val position: Int?,
)
