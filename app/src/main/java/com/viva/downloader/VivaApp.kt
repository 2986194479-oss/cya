package com.viva.downloader

import android.app.Application

class VivaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
        AppLogger.i("App", "应用启动")
    }
}
