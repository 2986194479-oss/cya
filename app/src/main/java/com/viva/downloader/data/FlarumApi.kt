package com.viva.downloader.data

import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 桥接 Android WebView 的 CookieManager 与 OkHttp。
 * 用户在 WebView 里登录后，Cookie 自动写入 CookieManager，
 * 这里把它们带进 OkHttp 的下载请求。
 */
object WebViewCookieJar : CookieJar {

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val cookieManager = CookieManager.getInstance()
        val cookieHeader = cookieManager.getCookie(url.toString()) ?: return emptyList()
        val result = mutableListOf<Cookie>()
        for (pair in cookieHeader.split(";")) {
            val trimmed = pair.trim()
            if (trimmed.isEmpty()) continue
            val idx = trimmed.indexOf('=')
            if (idx <= 0) continue
            val name = trimmed.substring(0, idx)
            val value = trimmed.substring(idx + 1)
            val builder = Cookie.Builder()
                .name(name)
                .value(value)
                .domain(url.host)
                .path("/")
            try {
                result.add(builder.build())
            } catch (_: Exception) {
                // 忽略无法解析的 cookie
            }
        }
        return result
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val cookieManager = CookieManager.getInstance()
        for (cookie in cookies) {
            cookieManager.setCookie(url.toString(), cookie.toString())
        }
    }
}

/**
 * Flarum API 封装：浏览列表、读详情、下载附件。
 */
object FlarumApi {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .cookieJar(WebViewCookieJar)
            .build()
    }

    private val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private fun jsonRequest(url: String): JSONObject {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Accept", "application/json")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: "{}"
            if (!resp.isSuccessful) {
                throw Exception("HTTP ${resp.code}")
            }
            return JSONObject(body)
        }
    }

    // ── 登录态 / CSRF ───────────────────────────────────────

    private data class ApiRoot(
        val csrfToken: String,
        val loggedIn: Boolean,
    )

    /**
     * 请求 /api，同时拿到 CSRF token（响应头 X-CSRF-Token）与登录态（actor 是否存在）。
     */
    private fun fetchApiRoot(): ApiRoot {
        val req = Request.Builder()
            .url("${Discussion.API_BASE}")
            .header("User-Agent", UA)
            .header("Accept", "application/json")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            val csrf = resp.header("X-CSRF-Token") ?: ""
            val body = resp.body?.string() ?: "{}"
            if (!resp.isSuccessful) {
                throw Exception("HTTP ${resp.code}")
            }
            val root = JSONObject(body)
            val actor = root.optJSONObject("data")
                ?.optJSONObject("relationships")
                ?.optJSONObject("actor")
                ?.optJSONObject("data")
            val loggedIn = actor != null && actor.optString("id").isNotEmpty()
            return ApiRoot(csrf, loggedIn)
        }
    }

    /** 登录态：/api 响应里 data.relationships.actor 是否存在。 */
    suspend fun isLoggedIn(): Boolean = withContext(Dispatchers.IO) {
        try {
            fetchApiRoot().loggedIn
        } catch (e: Exception) {
            false
        }
    }

    /** 获取当前会话的 CSRF token。 */
    suspend fun fetchCsrfToken(): String = withContext(Dispatchers.IO) {
        fetchApiRoot().csrfToken
    }

    /**
     * 原生登录：POST /login，body 为 identification + password + remember。
     * 成功后 flarum_session cookie 由 WebViewCookieJar 自动写入 CookieManager。
     * 返回 true 表示登录成功。
     */
    suspend fun login(identification: String, password: String, remember: Boolean = true): Boolean =
        withContext(Dispatchers.IO) {
            val csrf = fetchCsrfToken()
            val body = buildString {
                append("identification=").append(java.net.URLEncoder.encode(identification, "UTF-8"))
                append("&password=").append(java.net.URLEncoder.encode(password, "UTF-8"))
                append("&remember=").append(if (remember) "1" else "0")
            }
            val req = Request.Builder()
                .url("${Discussion.BASE}/login")
                .header("User-Agent", UA)
                .header("Accept", "application/json")
                .header("X-CSRF-Token", csrf)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .post(body.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    true
                } else if (resp.code == 401) {
                    false
                } else {
                    throw Exception("登录失败：HTTP ${resp.code}")
                }
            }
        }

    // ── 浏览列表 ────────────────────────────────────────────

    /**
     * 获取论坛全部标签。
     */
    suspend fun fetchTags(): List<Tag> = withContext(Dispatchers.IO) {
        val root = jsonRequest("${Discussion.API_BASE}")
        val included = root.optJSONArray("included") ?: org.json.JSONArray()
        val tags = mutableListOf<Tag>()
        for (i in 0 until included.length()) {
            val item = included.optJSONObject(i) ?: continue
            if (item.optString("type") != "tags") continue
            val a = item.optJSONObject("attributes") ?: continue
            tags.add(
                Tag(
                    id = item.optString("id"),
                    slug = a.optString("slug"),
                    name = a.optString("name"),
                    description = a.optString("description"),
                    color = a.optString("color").takeIf { it.isNotEmpty() && it != "null" },
                    discussionCount = a.optInt("discussionCount"),
                    isHidden = a.optBoolean("isHidden"),
                    position = if (a.has("position") && !a.isNull("position")) a.optInt("position") else null,
                )
            )
        }
        // 按 position 排序（无 position 排后面），保持原论坛标签顺序
        tags.sortedBy { it.position ?: Int.MAX_VALUE }
    }

    /**
     * 获取用户主页信息。
     */
    suspend fun fetchUser(userId: String): ForumUser? = withContext(Dispatchers.IO) {
        val root = jsonRequest("${Discussion.API_BASE}/users/$userId")
        val data = root.optJSONObject("data") ?: return@withContext null
        val attrs = data.optJSONObject("attributes") ?: return@withContext null
        ForumUser(
            id = data.optString("id"),
            username = attrs.optString("username"),
            displayName = attrs.optString("displayName").takeIf { it.isNotEmpty() },
            slug = attrs.optString("slug"),
            avatarUrl = attrs.optString("avatarUrl").takeIf { it.isNotEmpty() },
            discussionCount = attrs.optInt("discussionCount"),
            commentCount = attrs.optInt("commentCount"),
        )
    }

    /**
     * 获取讨论列表（分页，可按标签/作者过滤）。返回 (discussions, hasMore)。
     */
    suspend fun listDiscussions(
        offset: Int,
        limit: Int = 20,
        tagSlug: String? = null,
        authorUsername: String? = null,
    ): Pair<List<Discussion>, Boolean> = withContext(Dispatchers.IO) {
        val base = "${Discussion.API_BASE}/discussions?page%5Boffset%5D=$offset&page%5Blimit%5D=$limit"
        val url = buildString {
            append(base)
            if (!tagSlug.isNullOrBlank()) append("&filter%5Btag%5D=").append(tagSlug)
            if (!authorUsername.isNullOrBlank()) append("&filter%5Bauthor%5D=").append(authorUsername)
        }
        val root = jsonRequest(url)

            val data = root.optJSONArray("data") ?: org.json.JSONArray()
            val included = root.optJSONArray("included") ?: org.json.JSONArray()

            // 建立 postId -> contentHtml 映射，以及 userId -> user 映射
            val postHtml = mutableMapOf<String, String>()
            val userMap = mutableMapOf<String, org.json.JSONObject>()
            for (i in 0 until included.length()) {
                val item = included.optJSONObject(i) ?: continue
                when (item.optString("type")) {
                    "posts" -> {
                        val pid = item.optString("id")
                        val html = item.optJSONObject("attributes")?.optString("contentHtml") ?: ""
                        postHtml[pid] = html
                    }
                    "users" -> {
                        userMap[item.optString("id")] = item
                    }
                }
            }

            val discussions = mutableListOf<Discussion>()
            for (i in 0 until data.length()) {
                val d = data.optJSONObject(i) ?: continue
                val id = d.optString("id")
                val attrs = d.optJSONObject("attributes") ?: continue
                val title = attrs.optString("title")
                val slug = attrs.optString("slug")
                val commentCount = attrs.optInt("commentCount")
                val createdAt = attrs.optString("createdAt")
                val lastPostedAt = attrs.optString("lastPostedAt")

                // 首帖的 post id 在 relationships.firstPost.data 里
                val firstPostId = d.optJSONObject("relationships")
                    ?.optJSONObject("firstPost")
                    ?.optJSONObject("data")
                    ?.optString("id")

                val html = firstPostId?.let { postHtml[it] } ?: ""
                val hasVideo = AttachmentParser.parse(html, firstPostId ?: "").any { it.isVideo }

                // 作者信息
                val authorId = d.optJSONObject("relationships")
                    ?.optJSONObject("user")
                    ?.optJSONObject("data")
                    ?.optString("id")
                val authorObj = authorId?.let { userMap[it] }
                val authorAttrs = authorObj?.optJSONObject("attributes")
                val authorUsername = authorAttrs?.optString("username")
                val authorDisplayName = authorAttrs?.optString("displayName")?.takeIf { it.isNotEmpty() }

                discussions.add(
                    Discussion(
                        id = id,
                        title = title,
                        slug = slug,
                        commentCount = commentCount,
                        createdAt = createdAt,
                        lastPostedAt = lastPostedAt,
                        hasVideo = hasVideo,
                        authorId = authorId,
                        authorUsername = authorUsername,
                        authorDisplayName = authorDisplayName,
                    )
                )
            }

            val next = root.optJSONObject("links")?.has("next") == true
            Pair(discussions, next)
        }

    // ── 详情：解析附件 ──────────────────────────────────────

    /**
     * 判断某个讨论（含全部评论）里是否存在视频附件。
     * 一旦发现视频立即短路返回，避免扫完全部评论。
     */
    suspend fun hasVideoInDiscussion(discussionId: String): Boolean = withContext(Dispatchers.IO) {
        val url = "${Discussion.API_BASE}/discussions/$discussionId"
        val root = jsonRequest(url)

        // 先看默认返回的 included posts（首帖 + 部分评论），快速命中
        val included = root.optJSONArray("included") ?: org.json.JSONArray()
        for (i in 0 until included.length()) {
            val item = included.optJSONObject(i) ?: continue
            if (item.optString("type") != "posts") continue
            val pid = item.optString("id")
            val html = item.optJSONObject("attributes")?.optString("contentHtml") ?: ""
            if (AttachmentParser.parse(html, pid).any { it.isVideo }) return@withContext true
        }

        // 收集全部 post id（去重）
        val postIds = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        val postsRel = root.optJSONObject("data")
            ?.optJSONObject("relationships")
            ?.optJSONObject("posts")
            ?.optJSONArray("data")
        if (postsRel != null) {
            for (i in 0 until postsRel.length()) {
                val pid = postsRel.optJSONObject(i)?.optString("id") ?: continue
                if (seen.add(pid)) postIds.add(pid)
            }
        }

        // 分批并发扫其余评论，发现视频即返回，避免一次性创建上千协程导致 OOM
        val semaphore = Semaphore(8)
        val found = java.util.concurrent.atomic.AtomicBoolean(false)
        val batchSize = 40
        var idx = 0
        while (idx < postIds.size && !found.get()) {
            val batch = postIds.subList(idx, minOf(idx + batchSize, postIds.size))
            coroutineScope {
                val deferreds = batch.map { pid ->
                    async {
                        if (found.get()) return@async false
                        semaphore.withPermit {
                            if (found.get()) return@withPermit false
                            try {
                                val postRoot = jsonRequest("${Discussion.API_BASE}/posts/$pid")
                                val html = postRoot.optJSONObject("data")
                                    ?.optJSONObject("attributes")
                                    ?.optString("contentHtml") ?: ""
                                val has = AttachmentParser.parse(html, pid).any { it.isVideo }
                                if (has) found.set(true)
                                has
                            } catch (e: Exception) {
                                false
                            }
                        }
                    }
                }
                for (d in deferreds) {
                    d.await()
                }
            }
            idx += batchSize
        }
        found.get()
    }

    /**
     * 读取单个讨论的全部帖子（首帖 + 所有评论），解析其中的视频附件。
     *
     * 说明：/api/discussions/{id} 默认只返回部分评论内容（约 20~43 条），
     * 但 relationships.posts.data 里会给出全部 post id，因此需要逐个拉取
     * /api/posts/{id} 才能扫全评论里的视频。
     */
    suspend fun listVideos(
        discussionId: String,
        onProgress: ((Int, Int) -> Unit)? = null,
    ): List<Attachment> = withContext(Dispatchers.IO) {
        val url = "${Discussion.API_BASE}/discussions/$discussionId"
        val root = jsonRequest(url)

        // 收集全部 post id（去重、保序）
        val postIds = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        val postsRel = root.optJSONObject("data")
            ?.optJSONObject("relationships")
            ?.optJSONObject("posts")
            ?.optJSONArray("data")
        if (postsRel != null) {
            for (i in 0 until postsRel.length()) {
                val pid = postsRel.optJSONObject(i)?.optString("id") ?: continue
                if (seen.add(pid)) postIds.add(pid)
            }
        }

        // 默认返回的 included posts 里可能也有内容，但为了保证不漏，统一按 postIds 拉全
        val videos = mutableListOf<Attachment>()
        val total = postIds.size
        var done = 0

        // 分批并发拉取每个 post 的内容并解析，避免一次性创建上千协程导致 OOM
        val semaphore = kotlinx.coroutines.sync.Semaphore(8)
        val batchSize = 40
        var idx = 0
        while (idx < postIds.size) {
            val batch = postIds.subList(idx, minOf(idx + batchSize, postIds.size))
            kotlinx.coroutines.coroutineScope {
                val deferreds = batch.map { pid ->
                    async {
                        semaphore.withPermit {
                            try {
                                val postRoot = jsonRequest("${Discussion.API_BASE}/posts/$pid")
                                val html = postRoot.optJSONObject("data")
                                    ?.optJSONObject("attributes")
                                    ?.optString("contentHtml") ?: ""
                                AttachmentParser.parse(html, pid).filter { it.isVideo }
                            } catch (e: Exception) {
                                emptyList()
                            }
                        }
                    }
                }
                deferreds.forEach { deferred ->
                    val found = deferred.await()
                    videos.addAll(found)
                    done++
                    onProgress?.invoke(done, total)
                }
            }
            idx += batchSize
        }

        videos
    }

    // ── 下载 ────────────────────────────────────────────────

    /**
     * 下载附件，返回二进制数据。
     * 接口：GET /api/fof/download/{uuid}/{postId}/{csrfToken}
     */
    suspend fun downloadAttachment(attachment: Attachment): ByteArray = withContext(Dispatchers.IO) {
        val url = buildStreamUrl(attachment)
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Referer", "${Discussion.BASE}/d/${attachment.postId}")
            .get()
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val code = resp.code
                if (code == 403 || code == 401) {
                    throw NotLoggedInException("需要登录后才能下载")
                }
                throw Exception("下载失败：HTTP $code")
            }
            resp.body?.bytes() ?: throw Exception("下载内容为空")
        }
    }

    /**
     * 构造带登录态的流式播放 URL（含 csrfToken）。
     * ExoPlayer 需配合 WebViewCookieJar 的 Cookie 才能访问。
     */
    suspend fun buildStreamUrl(attachment: Attachment): String = withContext(Dispatchers.IO) {
        val csrf = fetchCsrfToken()
        "${Discussion.API_BASE}/fof/download/${attachment.uuid}/${attachment.postId}/$csrf"
    }
}

class NotLoggedInException(message: String) : Exception(message)
