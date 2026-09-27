package com.viva.downloader

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.request.CachePolicy
import com.viva.downloader.data.FlarumApi

class VivaApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
        AppLogger.i("App", "应用启动")
    }

    // 配置 Coil 使用带登录 Cookie 的 OkHttpClient，加载需要登录态的图片
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient(FlarumApi.okHttpClient)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }
}
