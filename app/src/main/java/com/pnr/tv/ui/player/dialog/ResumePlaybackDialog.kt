package com.pnr.tv.ui.player.dialog

import android.content.Context
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import com.pnr.tv.R
import com.pnr.tv.databinding.DialogResumePlaybackBinding
import com.pnr.tv.db.entity.PlaybackPositionEntity

/**
 * Kaldığı yerden devam etme dialog'u.
 * Android TV için uyumlu, focus yönetimi ile.
 */
class ResumePlaybackDialog(
    private val context: Context,
    private val playbackPosition: PlaybackPositionEntity,
    private val onResumeFromPosition: () -> Unit,
    private val onStartFromBeginning: () -> Unit,
) {
    fun show() {
        val binding = DialogResumePlaybackBinding.inflate(LayoutInflater.from(context))

        val dialog =
            AlertDialog.Builder(context)
                .setView(binding.root)
                .setCancelable(true)
                .create()

        // Buton click listener'ları
        binding.btnResumeFromPosition.setOnClickListener {
            onResumeFromPosition()
            dialog.dismiss()
        }

        binding.btnStartFromBeginning.setOnClickListener {
            onStartFromBeginning()
            dialog.dismiss()
        }

        val window = dialog.window
        // Dialog'u ortala ve sabit genişlik kullan
        window?.setLayout(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
        // Arka planı şeffaf yap (CardView kendi arka planını kullanacak)
        window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialog.show()

        // Dialog açıldığında "Kaldığı Yerden Devam Et" butonuna focus ver (varsayılan seçenek)
        binding.btnResumeFromPosition.post {
            binding.btnResumeFromPosition.requestFocus()
        }
    }
}
