package com.viva.downloader.ui.login

import android.webkit.CookieManager
import com.viva.downloader.data.FlarumApi

/**
 * 检测登录态。
 * 不能只看是否存在 flarum_session cookie——游客也会拿到该 cookie，
 * 真正的判断依据是 /api 响应里 data.relationships.actor 是否存在。
 */
object SessionChecker {

    const val HOST = "https://bbs.viva-la-vita.org"

    /** 异步权威检查：请求 /api，看 actor 是否存在。 */
    suspend fun checkLoggedIn(): Boolean = FlarumApi.isLoggedIn()

    fun clearSession() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }
}
