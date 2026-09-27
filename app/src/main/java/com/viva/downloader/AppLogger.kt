package com.viva.downloader

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 应用日志：写入文件（应用私有目录），全局捕获未处理异常。
 * 出问题时用户可从「日志」页复制全文发给 AI 分析。
 */
object AppLogger {

    private const val LOG_FILE = "logs/app.log"
    private const val MAX_SIZE = 512 * 1024 // 512KB，超出自动截断

    private var logFile: File? = null
    private val lock = Any()

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    fun init(context: Context) {
        synchronized(lock) {
            if (logFile == null) {
                logFile = File(context.filesDir, LOG_FILE).apply {
                    parentFile?.mkdirs()
                }
            }
        }
        // 全局异常捕获
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            e("Crash", "线程=${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun d(tag: String, message: String) = write("D", tag, message)
    fun i(tag: String, message: String) = write("I", tag, message)
    fun w(tag: String, message: String) = write("W", tag, message)
    fun e(tag: String, message: String, throwable: Throwable? = null) =
        write("E", tag, message + (throwable?.let { "\n" + stackTrace(it) } ?: ""))

    private fun stackTrace(t: Throwable): String {
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        return sw.toString()
    }

    private fun write(level: String, tag: String, message: String) {
        synchronized(lock) {
            val file = logFile ?: return
            try {
                val line = "${timeFormat.format(Date())} $level/$tag: $message\n"
                // 超出大小则截断保留后半段
                if (file.exists() && file.length() > MAX_SIZE) {
                    truncate(file)
                }
                file.appendText(line)
            } catch (_: Exception) {
            }
        }
    }

    private fun truncate(file: File) {
        try {
            val content = file.readText()
            val keep = content.takeLast(MAX_SIZE / 2)
            file.writeText(keep)
        } catch (_: Exception) {
        }
    }

    fun read(): String {
        synchronized(lock) {
            val file = logFile ?: return "（日志未初始化）"
            return if (file.exists()) file.readText() else "（暂无日志）"
        }
    }

    fun clear() {
        synchronized(lock) {
            logFile?.delete()
        }
    }
}
