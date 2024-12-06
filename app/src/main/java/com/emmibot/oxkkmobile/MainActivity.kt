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
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.text.SimpleDateFormat
import java.util.*
import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher


class MainActivity : AppCompatActivity() {

    private lateinit var hideShowButton: Button
    private lateinit var textView: TextView
    private lateinit var keyInput: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var copyButton: Button
    private lateinit var copyhashButton: Button
    private lateinit var decryptButton: Button
    private lateinit var encryptedInput: EditText
    private var isPasswordVisible = false
    private var ultKey: String? = null
    private var rsaPublicKey: PublicKey? = null
    private var rsaPrivateKey: PrivateKey? = null
    private val handler = Handler()
    private var currentMinute: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        generateRSAKeys() // Генерация ключей

        textView = findViewById(R.id.textView)
        keyInput = findViewById(R.id.keyInput)
        progressBar = findViewById(R.id.progressBar)
        copyButton = findViewById(R.id.copyButton)
        copyhashButton = findViewById(R.id.copyhashButton)
        decryptButton = findViewById(R.id.decryptButton)
        encryptedInput = findViewById(R.id.encryptedInput)

        keyInput.transformationMethod = android.text.method.PasswordTransformationMethod.getInstance() // Скроем ввод

        copyhashButton.setOnClickListener { copyHashToClipboard() }
        copyButton.setOnClickListener { copyPublicKeyToClipboard() }
        decryptButton.setOnClickListener { decryptText() }

        keyInput = findViewById(R.id.keyInput)
        hideShowButton = findViewById(R.id.hideShowButton)

        keyInput.transformationMethod = android.text.method.PasswordTransformationMethod.getInstance()

        hideShowButton.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                keyInput.transformationMethod = null
                hideShowButton.text = "Скрыть"
            } else {
                keyInput.transformationMethod = android.text.method.PasswordTransformationMethod.getInstance()
                hideShowButton.text = "Показать"
            }
            keyInput.setSelection(keyInput.text.length)
        }

        load()
        startUpdating()
    }

    private fun generateRSAKeys() {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048)
        val keyPair: KeyPair = keyPairGenerator.generateKeyPair()
        rsaPublicKey = keyPair.public
        rsaPrivateKey = keyPair.private
    }

    private fun copyHashToClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val hash = textView.text
        if (hash != null) {
            val clip = ClipData.newPlainText("Copied Hash", hash)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Хэш скопирован: $hash", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Хэш ещё не сгенерирован!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun publicKeyToBase64(): String {
        return Base64.encodeToString(rsaPublicKey?.encoded, Base64.DEFAULT)
    }

    private fun copyPublicKeyToClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val publicKeyBase64 = publicKeyToBase64()
        val clip = ClipData.newPlainText("Public Key", publicKeyBase64)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Открытый ключ скопирован", Toast.LENGTH_SHORT).show()
    }

    private fun decryptText() {
        val encryptedTextBase64 = encryptedInput.text.toString()

        if (encryptedTextBase64.isNotBlank()) {
            try {
                val encryptedBytes = Base64.decode(encryptedTextBase64, Base64.DEFAULT)
                val decryptedText = decryptRSA(encryptedBytes)

                save(decryptedText)

                encryptedInput.setText("")
                keyInput.setText(decryptedText)

                Toast.makeText(this, "Ключ успешно сохранён", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Ошибка при расшифровке ключа!", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Введите зашифрованный текст", Toast.LENGTH_SHORT).show()
        }
    }

    private fun decryptRSA(encryptedBytes: ByteArray): String {
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.DECRYPT_MODE, rsaPrivateKey)
        val decryptedBytes = cipher.doFinal(encryptedBytes)
        return String(decryptedBytes, Charsets.UTF_8)
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

        if ((utcMinute != currentMinute && utcSecond == 0) || doInstant) {
            currentMinute = utcMinute
            if (key.isNotBlank()) {
                ultKey = key
                save(key)
                val currentTime = SimpleDateFormat("yyyy.MM.dd_HH:mm", Locale.getDefault())
                    .apply { timeZone = TimeZone.getTimeZone("UTC") }
                    .format(calendar.time)
                textView.text  = sha256("$currentTime$key").take(12).uppercase()
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

    private fun save(key: String) {
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString("ULT_KEY", key)
        editor.apply()
    }

    private fun load() {
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val savedKey = sharedPreferences.getString("ULT_KEY", null)
        if (!savedKey.isNullOrEmpty()) {
            keyInput.setText(savedKey)
            Toast.makeText(this, "Ключ загружен", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
