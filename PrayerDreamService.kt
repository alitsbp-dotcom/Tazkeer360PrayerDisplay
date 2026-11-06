package com.tazkeer360.prayerdisplay

import android.service.dreams.DreamService
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.os.Handler
import android.os.Looper
import java.util.Calendar

class PrayerDreamService : DreamService() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var clock: TextView
    private lateinit var list: TextView
    private val tick = object : Runnable {
        override fun run() {
            clock.text = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            handler.postDelayed(this, 1000)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = false

        val prefs = getSharedPreferences("prayer_data", MODE_PRIVATE)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(30, 30, 30, 30)
            setBackgroundColor(Color.rgb(8, 25, 20))
        }
        page.addView(TextView(this).apply {
            text = "تذکیر 360"
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(196, 154, 69))
            gravity = Gravity.CENTER
        })
        clock = TextView(this).apply {
            textSize = 48f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 20)
        }
        page.addView(clock)
        val keys = listOf("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha")
        val names = listOf("فجر", "طلوع آفتاب", "ظہر", "عصر", "مغرب", "عشاء")
        val lines = keys.mapIndexedNotNull { i, key ->
            prefs.getString(key, null)?.let { "${names[i]}       $it" }
        }
        list = TextView(this).apply {
            text = if (lines.isEmpty()) "اوقات حاصل کرنے کے لیے ایپ کھولیں" else lines.joinToString("\n\n")
            textSize = 23f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setLineSpacing(12f, 1f)
        }
        page.addView(list)
        setContentView(page)
        handler.post(tick)
    }

    override fun onDreamingStopped() {
        handler.removeCallbacks(tick)
        super.onDreamingStopped()
    }
}
