package com.pnr.tv.extensions

import android.content.Context
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import com.pnr.tv.R

/**
 * Toast için extension functions.
 * Özel Toast layout kullanarak ikon olmadan mesaj gösterir.
 */

/**
 * Özel Toast mesajı gösterir (ikon olmadan).
 * @param message Gösterilecek mesaj
 * @param duration Toast süresi (Toast.LENGTH_SHORT veya Toast.LENGTH_LONG)
 */
fun Context.showCustomToast(
    message: String,
    duration: Int = Toast.LENGTH_SHORT,
) {
    val toast = Toast(this)
    val inflater = LayoutInflater.from(this)
    val view = inflater.inflate(R.layout.custom_toast, null)

    val textView = view.findViewById<TextView>(R.id.toast_text)
    textView.text = message

    toast.view = view
    toast.duration = duration
    toast.show()
}
