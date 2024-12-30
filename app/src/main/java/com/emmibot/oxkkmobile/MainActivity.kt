package com.emmibot.oxkkmobile

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.util.*
import kotlin.collections.ArrayList
import android.content.ClipData
import android.content.ClipboardManager
import android.util.Base64
import android.widget.Button
import javax.crypto.Cipher


class MainActivity : AppCompatActivity() {

    private var rsaPublicKey: PublicKey? = null
    private var rsaPrivateKey: PrivateKey? = null
    private lateinit var textView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var recyclerView: RecyclerView
    private lateinit var addButton: FloatingActionButton
    private lateinit var adapter: KeysAdapter

    private val keysList = ArrayList<KeyItem>()
    private val handler = Handler()

    private var secondsRemaining = 60

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textView = findViewById(R.id.textView)
        progressBar = findViewById(R.id.progressBar)
        recyclerView = findViewById(R.id.recyclerView)
        addButton = findViewById(R.id.addButton)

        adapter = KeysAdapter(keysList) {}
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        loadKeys()
        generateRSAKeys()

        addButton.setOnClickListener {
            showPopupMenu(it)
        }

        startProgressBar()
    }

    private fun startProgressBar() {
        progressBar.max = 60
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val secondsElapsed = calendar.get(Calendar.SECOND)
        secondsRemaining = 60 - secondsElapsed // Начинаем с оставшихся секунд до конца текущей минуты

        val progressUpdateRunnable = object : Runnable {
            override fun run() {
                if (secondsRemaining > 0) {
                    secondsRemaining--
                    textView.text = "Осталось $secondsRemaining сек"
                    progressBar.progress = 60 - secondsRemaining
                    handler.postDelayed(this, 1000)
                } else {
                    val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                    val secondsElapsed = calendar.get(Calendar.SECOND)
                    secondsRemaining = 60 - secondsElapsed

                    textView.text = "Осталось 60 сек"
                    progressBar.progress = 0
                    adapter.notifyDataSetChanged()
                    handler.postDelayed(this, 1000)
                }
            }
        }
        handler.post(progressUpdateRunnable)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun showAddKeyDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_key, null)
        val serviceNameInput = dialogView.findViewById<EditText>(R.id.serviceNameInput)
        val keyInput = dialogView.findViewById<EditText>(R.id.keyInput)

        val alertDialog = AlertDialog.Builder(this)
            .setTitle("Добавить ключ")
            .setView(dialogView)
            .setPositiveButton("Добавить") { _, _ ->
                val serviceName = serviceNameInput.text.toString().trim()
                val key = keyInput.text.toString().trim()

                if (TextUtils.isEmpty(serviceName) || TextUtils.isEmpty(key)) {
                    Toast.makeText(this, "Заполните все поля", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                keysList.add(KeyItem(serviceName, key))
                adapter.notifyDataSetChanged()

                saveKeys()

                Toast.makeText(this, "Ключ добавлен", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Отмена") { dialog, _ -> dialog.dismiss() }
            .create()

        alertDialog.show()
    }

    private fun showAddKeyRSA() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_key_due_rsa, null)

        val RSAcopyButton = dialogView.findViewById<Button>(R.id.RSAcopyButton)
        val RSAdecryptButton = dialogView.findViewById<Button>(R.id.RSAdecryptButton)

        RSAcopyButton.setOnClickListener {
            copyPublicKeyToClipboard()
        }

        val alertDialog = AlertDialog.Builder(this)
            .setTitle("Добавить ключ")
            .setView(dialogView)
            .setNegativeButton("Отмена") { dialog, _ -> dialog.dismiss() }
            .create()

        RSAdecryptButton.setOnClickListener {
            decryptText(dialogView, alertDialog)
        }

        alertDialog.show()
    }


    private fun generateRSAKeys() {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048)
        val keyPair: KeyPair = keyPairGenerator.generateKeyPair()
        rsaPublicKey = keyPair.public
        rsaPrivateKey = keyPair.private
    }

    private fun copyPublicKeyToClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val publicKeyBase64 = Base64.encodeToString(rsaPublicKey?.encoded, Base64.DEFAULT)
        val clip = ClipData.newPlainText("Public Key", publicKeyBase64)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Открытый ключ скопирован", Toast.LENGTH_SHORT).show()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun decryptText(dialogView: View, alertDialog: AlertDialog) {
        val encryptedTextBase64 = dialogView.findViewById<EditText>(R.id.RSAkeyInput).text.toString()
        val serviceNameInput = dialogView.findViewById<EditText>(R.id.RSAserviceNameInput).text.toString()

        if (TextUtils.isEmpty(serviceNameInput) || TextUtils.isEmpty(encryptedTextBase64)) {
            Toast.makeText(this, "Заполните все поля", Toast.LENGTH_SHORT).show()
            alertDialog.dismiss()
        }

        if (encryptedTextBase64.isNotBlank()) {
            try {
                val encryptedBytes = Base64.decode(encryptedTextBase64, Base64.DEFAULT)
                val decryptedText = decryptRSA(encryptedBytes)

                keysList.add(KeyItem(serviceNameInput, decryptedText))
                adapter.notifyDataSetChanged()

                saveKeys()

                alertDialog.dismiss()

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

    private fun showPopupMenu(view: View) {
        val popupMenu = PopupMenu(this, view)
        popupMenu.menuInflater.inflate(R.menu.popup_menu, popupMenu.menu)

        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.add_key -> {
                    showAddKeyDialog()
                    true
                }
                R.id.add_key_rsa -> {
                    showAddKeyRSA()
                    true
                }
                else -> false
            }
        }
        popupMenu.show()
    }

    fun saveKeys() {
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val keysSet = keysList.map { "${it.serviceName}::${it.key}" }.toSet()
        editor.putStringSet("SAVED_KEYS", keysSet)
        editor.apply()
    }

    private fun loadKeys() {
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val savedKeys = sharedPreferences.getStringSet("SAVED_KEYS", emptySet())
        keysList.clear()

        savedKeys?.forEach { savedKey ->
            val parts = savedKey.split("::")
            if (parts.size == 2) {
                keysList.add(KeyItem(parts[0], parts[1]))
            }
        }

        adapter.notifyDataSetChanged()
    }


    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
