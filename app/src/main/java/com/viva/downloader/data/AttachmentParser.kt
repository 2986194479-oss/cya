package com.viva.downloader.data

import java.util.regex.Pattern

/**
 * 从 Flarum 帖子的 contentHtml 中解析附件（视频）。
 *
 * fof-upload 扩展会把每个附件渲染成一个 ButtonGroup 块：
 * ```html
 * <div class="ButtonGroup" data-fof-upload-download-uuid="uuid">
 *   <div class="Button hasIcon ..."><i class="fas fa-download"></i></div>
 *   <div class="Button">filename.mov</div>
 *   <div class="Button">86MB</div>
 * </div>
 * ```
 */
object AttachmentParser {

    private val UUID_PATTERN = Pattern.compile("data-fof-upload-download-uuid=\"([^\"]+)\"")

    // 匹配 <div class="Button">文本</div>，捕获文本
    private val BUTTON_TEXT_PATTERN = Pattern.compile("<div class=\"Button\"[^>]*>([^<]+)</div>")

    private val SIZE_PATTERN = Pattern.compile("\\d+(\\.\\d+)?\\s*(KB|MB|GB)", Pattern.CASE_INSENSITIVE)

    fun parse(contentHtml: String, postId: String): List<Attachment> {
        if (contentHtml.isBlank()) return emptyList()

        // 1. 找出所有 uuid 及其出现位置
        val uuidMatcher = UUID_PATTERN.matcher(contentHtml)
        val anchors = mutableListOf<Pair<Int, String>>() // (startIndex, uuid)
        while (uuidMatcher.find()) {
            anchors.add(uuidMatcher.start() to uuidMatcher.group(1))
        }
        if (anchors.isEmpty()) return emptyList()

        // 2. 每个 uuid 对应的内容段：从该 uuid 起到下一个 uuid 前（或结尾）
        val result = mutableListOf<Attachment>()
        for (i in anchors.indices) {
            val (start, uuid) = anchors[i]
            val end = if (i + 1 < anchors.size) anchors[i + 1].first else contentHtml.length
            val segment = contentHtml.substring(start, end)

            // 3. 在段落内提取所有 Button 文本
            val buttonTexts = mutableListOf<String>()
            val btMatcher = BUTTON_TEXT_PATTERN.matcher(segment)
            while (btMatcher.find()) {
                buttonTexts.add(btMatcher.group(1).trim())
            }

            // 4. 大小标签
            val sizeLabel = buttonTexts.firstOrNull { SIZE_PATTERN.matcher(it).matches() }

            // 5. 文件名：含扩展名、且不是大小、不是图标/下载
            val filename = buttonTexts.firstOrNull { text ->
                text.contains('.') &&
                    !SIZE_PATTERN.matcher(text).matches() &&
                    text != "下载" && text != "Download"
            } ?: continue

            result.add(
                Attachment(
                    uuid = uuid,
                    postId = postId,
                    filename = filename,
                    sizeLabel = sizeLabel,
                )
            )
        }
        return result
    }

    /**
     * 从帖子链接中提取讨论 ID。支持：
     * - https://bbs.viva-la-vita.org/d/12345-slug
     * - https://bbs.viva-la-vita.org/d/12345
     * - 12345
     */
    fun extractDiscussionId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.matches(Regex("\\d+"))) return trimmed
        return Regex("/d/(\\d+)").find(trimmed)?.groupValues?.get(1)
    }
}
