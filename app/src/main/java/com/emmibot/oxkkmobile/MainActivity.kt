package com.emmibot.oxkkmobile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.lang.Integer
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var textView: TextView
    private lateinit var keyInput: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var copyButton: Button
    private lateinit var lenghtoutput: EditText
    private var ultKey: String? = null
    private val handler = Handler()
    private var currentHash: String? = null
    private var currentMinute: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textView = findViewById(R.id.textView)
        keyInput = findViewById(R.id.keyInput)
        progressBar = findViewById(R.id.progressBar)
        lenghtoutput = findViewById(R.id.editTextNumberPassword)
        copyButton = findViewById(R.id.copyButton)

        keyInput.transformationMethod = android.text.method.PasswordTransformationMethod.getInstance() // Скроем ввод

        copyButton.setOnClickListener { copyHashToClipboard() }

        load()
        startUpdating()
    }

    private fun startUpdating() {
        updateText(true)
        startProgressBar()
    }

    private fun updateText(doInstant: Boolean) {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val utcMinute = calendar.get(Calendar.MINUTE)
        val utcSecond = calendar.get(Calendar.SECOND)
        val key = keyInput.text.toString()
        var length = lenghtoutput.text.toString().toIntOrNull() ?: 8

        if ((utcMinute != currentMinute && utcSecond == 0) || doInstant) {
            currentMinute = utcMinute
            if (key.isNotBlank()) {
                ultKey = key
                save(key, length)
                val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault())
                    .apply { timeZone = TimeZone.getTimeZone("UTC") }
                    .format(calendar.time)
                textView.text  = sha256("$currentTime$key").take(length).uppercase()
            } else {
                Toast.makeText(this, "Введите ключ!", Toast.LENGTH_SHORT).show()
            }
        }
        scheduleNextUpdate()
    }

    private fun scheduleNextUpdate() {
        handler.postDelayed({
            updateText(false)
        }, 1000) // Запуск обновления каждую секунду
    }

    private fun startProgressBar() {
        progressBar.max = 60
        val progressUpdateRunnable = object : Runnable {
            override fun run() {
                val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                val secondsElapsed = calendar.get(Calendar.SECOND)
                progressBar.progress = secondsElapsed

                handler.postDelayed(this, 1000)
            }
        }
        handler.post(progressUpdateRunnable)
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun copyHashToClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val hash = currentHash
        if (hash != null) {
            val clip = ClipData.newPlainText("Copied Hash", hash)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Хэш скопирован: $hash", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Хэш ещё не сгенерирован!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun save(key: String, lenghtkey: Int) {
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString("ULT_KEY", key)
        editor.putString("KEY_LENGHT", lenghtkey.toString())
        editor.apply()
    }

    private fun load() {
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val savedKey = sharedPreferences.getString("ULT_KEY", null)
        val savedKeylenght = sharedPreferences.getString("KEY_LENGHT", "8")
        lenghtoutput.setText(savedKeylenght)
        if (!savedKey.isNullOrEmpty()) {
            keyInput.setText(savedKey)
            ultKey = savedKey
            Toast.makeText(this, "Ключ загружен", Toast.LENGTH_SHORT).show()
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
