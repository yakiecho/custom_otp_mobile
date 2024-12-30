package com.emmibot.oxkkmobile

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

class KeysAdapter(
    private var keysList: MutableList<KeyItem>,
    private val onKeyDeleted: (KeyItem) -> Unit
) : RecyclerView.Adapter<KeysAdapter.KeysViewHolder>() {

    class KeysViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val serviceName: TextView = itemView.findViewById(R.id.serviceName)
        val keyCode: TextView = itemView.findViewById(R.id.keyCode)
        val deleteKey: ImageView = itemView.findViewById(R.id.deletekey)

        fun bind(otp: String) {
            keyCode.setOnClickListener {
                val clipboard = keyCode.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Key", otp)
                clipboard.setPrimaryClip(clip)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): KeysViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_key, parent, false)
        return KeysViewHolder(view)
    }

    fun getcurrentOTP(secret_key: String): String {
        val currentTime = SimpleDateFormat("yyyy.MM.dd_HH:mm", Locale.getDefault())
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date())
        val keyHash = sha256("$currentTime${secret_key}").take(12).uppercase()
        return keyHash
    }

    override fun onBindViewHolder(holder: KeysViewHolder, position: Int) {
        val item = keysList[position]
        val otp = getcurrentOTP(item.key)

        val formattedOtp = otp.chunked(4).joinToString(" ")

        holder.bind(otp)
        holder.serviceName.text = item.serviceName
        holder.keyCode.text = formattedOtp

        holder.deleteKey.setOnClickListener {
            val removedKey = keysList[position]

            // Создаем диалог подтверждения
            AlertDialog.Builder(holder.itemView.context)
                .setTitle(R.string.confirm_delete_title)
                .setMessage(R.string.confirm_delete_message)
                .setPositiveButton(R.string.yes) { _, _ ->
                    keysList.removeAt(position)
                    onKeyDeleted(removedKey)
                    notifyItemRemoved(position)
                    notifyItemRangeChanged(position, keysList.size)
                    (holder.itemView.context as MainActivity).saveKeys()
                }
                .setNegativeButton(R.string.no) { dialog, _ ->
                    dialog.dismiss()
                }
                .create()
                .show()
        }

    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    override fun getItemCount() = keysList.size
}
