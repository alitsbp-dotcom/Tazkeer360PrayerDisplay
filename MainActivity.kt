package com.tazkeer360.prayerdisplay

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity() {
    private val green = Color.rgb(5, 91, 64)
    private val gold = Color.rgb(196, 154, 69)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var cityInput: EditText
    private lateinit var countryInput: EditText
    private lateinit var status: TextView
    private lateinit var rowsBox: LinearLayout
    private lateinit var countdown: TextView
    private val prefs by lazy { getSharedPreferences("prayer_data", MODE_PRIVATE) }
    private var prayers: List<Prayer> = emptyList()
    private var nextPrayerTime: String? = null

    private val ticker = object : Runnable {
        override fun run() {
            updateCountdown()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
        cityInput.setText(prefs.getString("city", "Muscat"))
        countryInput.setText(prefs.getString("country", "Oman"))
        loadSaved()
        handler.post(ticker)
        createNotificationChannel()
    }

    private fun buildScreen() {
        val scroll = ScrollView(this)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 28)
            setBackgroundColor(Color.rgb(248, 247, 240))
        }
        scroll.addView(page)

        page.addView(TextView(this).apply {
            text = "تذکیر 360"
            textSize = 29f
            setTextColor(green)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
        })
        page.addView(TextView(this).apply {
            text = "نمازوں کے اوقات اور کاؤنٹ ڈاؤن"
            textSize = 16f
            setTextColor(gold)
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 20)
        })

        cityInput = EditText(this).apply {
            hint = "شہر، مثلاً Muscat"
            singleLine = true
            textSize = 16f
        }
        countryInput = EditText(this).apply {
            hint = "ملک، مثلاً Oman"
            singleLine = true
            textSize = 16f
        }
        page.addView(cityInput)
        page.addView(countryInput)

        page.addView(Button(this).apply {
            text = "نمازوں کے اوقات حاصل کریں"
            setTextColor(Color.WHITE)
            setBackgroundColor(green)
            setOnClickListener { fetchPrayerTimes() }
        })

        status = TextView(this).apply {
            text = "شہر منتخب کریں اور اوقات حاصل کریں۔"
            textSize = 14f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 12)
        }
        page.addView(status)

        countdown = TextView(this).apply {
            text = "اگلی نماز کا وقت معلوم کیا جا رہا ہے"
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(green)
            gravity = Gravity.CENTER
            setPadding(8, 18, 8, 18)
        }
        page.addView(countdown)

        rowsBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        page.addView(rowsBox)

        page.addView(TextView(this).apply {
            text = "چارجنگ ڈسپلے فعال کرنے کے لیے Android Settings میں Display > Screen saver کھولیں اور Tazkeer 360 منتخب کریں۔"
            textSize = 13f
            setTextColor(Color.DKGRAY)
            setPadding(4, 22, 4, 8)
        })
        setContentView(scroll)
    }

    private fun fetchPrayerTimes() {
        val city = cityInput.text.toString().trim()
        val country = countryInput.text.toString().trim()
        if (city.isEmpty() || country.isEmpty()) {
            status.text = "براہ کرم شہر اور ملک دونوں لکھیں۔"
            return
        }
        status.text = "نمازوں کے اوقات حاصل کیے جا رہے ہیں..."
        prefs.edit().putString("city", city).putString("country", country).apply()

        Thread {
            try {
                val c = URLEncoder.encode(city, "UTF-8")
                val co = URLEncoder.encode(country, "UTF-8")
                val url = URL("https://api.aladhan.com/v1/timingsByCity?city=$c&country=$co&method=3")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 15000
                    requestMethod = "GET"
                }
                val text = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                val root = JSONObject(text).getJSONObject("data")
                val times = root.getJSONObject("timings")
                val labels = listOf(
                    Triple("فجر", "Fajr", times.optString("Fajr")),
                    Triple("طلوع آفتاب", "Sunrise", times.optString("Sunrise")),
                    Triple("ظہر", "Dhuhr", times.optString("Dhuhr")),
                    Triple("عصر", "Asr", times.optString("Asr")),
                    Triple("مغرب", "Maghrib", times.optString("Maghrib")),
                    Triple("عشاء", "Isha", times.optString("Isha"))
                )
                val result = labels.map { Prayer(it.first, it.second, it.third.take(5)) }
                runOnUiThread {
                    prayers = result
                    savePrayers()
                    renderRows()
                    status.text = "اوقات اپ ڈیٹ ہوگئے۔ اپنے مقامی مسجد کے اوقات سے تصدیق کرلیں۔"
                    postPrayerNotification()
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "اوقات حاصل نہیں ہو سکے۔ انٹرنیٹ اور شہر کا نام چیک کریں۔" }
            }
        }.start()
    }

    private fun savePrayers() {
        val edit = prefs.edit()
        prayers.forEach { edit.putString(it.key, it.time) }
        edit.apply()
    }

    private fun loadSaved() {
        val keys = listOf("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha")
        val names = listOf("فجر", "طلوع آفتاب", "ظہر", "عصر", "مغرب", "عشاء")
        val saved = keys.mapIndexed { i, key ->
            Prayer(names[i], key, prefs.getString(key, "") ?: "")
        }.filter { it.time.isNotBlank() }
        if (saved.isNotEmpty()) {
            prayers = saved
            renderRows()
        }
    }

    private fun renderRows() {
        rowsBox.removeAllViews()
        prayers.forEach { prayer ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(14, 14, 14, 14)
                setBackgroundColor(Color.WHITE)
                val lp = LinearLayout.LayoutParams(-1, -2)
                lp.bottomMargin = 8
                layoutParams = lp
            }
            val name = TextView(this).apply {
                text = prayer.name
                textSize = 18f
                setTextColor(green)
                typeface = Typeface.DEFAULT_BOLD
            }
            val time = TextView(this).apply {
                text = prayer.time
                textSize = 18f
                setTextColor(Color.DKGRAY)
                gravity = Gravity.RIGHT
            }
            row.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(time, LinearLayout.LayoutParams(0, -2, 1f))
            rowsBox.addView(row)
        }
    }

    private fun updateCountdown() {
        if (prayers.isEmpty()) return
        val now = Calendar.getInstance()
        val candidates = prayers.filter { it.key != "Sunrise" }.mapNotNull { p ->
            try {
                val parts = p.time.split(":")
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, parts[0].toInt())
                    set(Calendar.MINUTE, parts[1].toInt())
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_YEAR, 1)
                }
                p to cal.timeInMillis
            } catch (_: Exception) { null }
        }.sortedBy { it.second }

        val next = candidates.firstOrNull() ?: return
        val diff = (next.second - now.timeInMillis).coerceAtLeast(0) / 1000
        val h = diff / 3600
        val m = (diff % 3600) / 60
        val s = diff % 60
        countdown.text = "اگلی نماز: ${next.first.name}\nباقی وقت: %02d:%02d:%02d".format(h, m, s)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel("prayer", "نماز کی اطلاع", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun postPrayerNotification() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100)
            return
        }
        val text = countdown.text.toString().replace("\n", " — ")
        val builder = if (Build.VERSION.SDK_INT >= 26)
            android.app.Notification.Builder(this, "prayer")
        else
            android.app.Notification.Builder(this)
        val notification = builder
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("تذکیر 360 — نماز کے اوقات")
            .setContentText(text)
            .setOngoing(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(360, notification)
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        super.onDestroy()
    }
}
