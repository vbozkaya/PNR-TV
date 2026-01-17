package com.pnr.tv.ui.settings

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pnr.tv.ui.main.AboutActivity
import com.pnr.tv.ui.main.MainActivity
import com.pnr.tv.R
import com.pnr.tv.extensions.showCustomToast
import com.pnr.tv.util.ui.LocaleHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Genel ayarlar için Fragment.
 * Dil seçimi, önbellek temizleme ve hakkında butonlarını yönetir.
 */
@AndroidEntryPoint
class GeneralSettingsFragment : Fragment() {
    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_general_settings, container, false)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        setupLanguageButton(view)
        setupClearCacheButton(view)
        setupAboutButton(view)
    }

    /**
     * Dil seçimi butonunu ayarlar
     */
    private fun setupLanguageButton(view: View) {
        view.findViewById<View>(R.id.btn_app_language)?.setOnClickListener {
            showLanguageSelectionDialog()
        }
    }

    /**
     * Önbellek temizleme butonunu ayarlar
     */
    private fun setupClearCacheButton(view: View) {
        view.findViewById<View>(R.id.btn_clear_cache)?.setOnClickListener {
            showClearCacheDialog()
        }
    }

    /**
     * Hakkında butonunu ayarlar
     */
    private fun setupAboutButton(view: View) {
        view.findViewById<View>(R.id.btn_about)?.setOnClickListener {
            startActivity(Intent(requireContext(), AboutActivity::class.java))
        }
    }

    /**
     * Dil seçimi diyalogunu gösterir
     */
    private fun showLanguageSelectionDialog() {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_language_selection, null)
        dialog.setContentView(dialogView)

        val window = dialog.window
        window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
        window?.setBackgroundDrawableResource(android.R.color.transparent)

        val recyclerLanguages = dialogView.findViewById<RecyclerView>(R.id.recycler_languages)
        val btnClose = dialogView.findViewById<View>(R.id.btn_close)

        val languages = LocaleHelper.SupportedLanguage.values().toList()
        val currentLanguageCode = viewModel.getCurrentLanguage()

        val adapter =
            LanguageAdapter(
                languages = languages,
                currentLanguageCode = currentLanguageCode,
            )

        recyclerLanguages.layoutManager = LinearLayoutManager(requireContext())
        recyclerLanguages.adapter = adapter

        btnClose.setOnClickListener {
            // Seçili dil varsa kaydet, yoksa mevcut dili koru
            val languageToSave = adapter.getSelectedLanguageCode() ?: currentLanguageCode
            viewModel.changeLanguage(languageToSave)

            // Tüm uygulamayı yeniden başlat
            val intent = Intent(requireContext(), MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            requireActivity().finishAffinity()

            dialog.dismiss()
        }

        // İlk focus'u ilk dil seçeneğine ver
        recyclerLanguages.post {
            val firstItem = recyclerLanguages.findViewHolderForAdapterPosition(0)
            firstItem?.itemView?.requestFocus() ?: btnClose.requestFocus()
        }

        dialog.show()
    }

    /**
     * Önbellek temizleme diyalogunu gösterir
     */
    private fun showClearCacheDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_confirm, null)
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(dialogView)

        val window = dialog.window
        window?.setLayout(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
        window?.setBackgroundDrawableResource(android.R.color.transparent)

        val titleTextView = dialogView.findViewById<android.widget.TextView>(R.id.tv_dialog_title)
        val messageTextView = dialogView.findViewById<android.widget.TextView>(R.id.tv_dialog_message)
        val btnYes = dialogView.findViewById<android.widget.TextView>(R.id.btn_dialog_yes)
        val btnNo = dialogView.findViewById<android.widget.TextView>(R.id.btn_dialog_no)

        titleTextView.text = getString(R.string.dialog_clear_cache_title)
        messageTextView.text = getString(R.string.dialog_clear_cache_message)

        btnYes.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                when (val result = viewModel.clearCache()) {
                    is com.pnr.tv.repository.Result.Success -> {
                        requireContext().showCustomToast(getString(R.string.toast_cache_cleared))

                        // Ana sayfaya dön
                        val intent = Intent(requireContext(), MainActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                        requireActivity().finish()
                    }
                    is com.pnr.tv.repository.Result.Error -> {
                        requireContext().showCustomToast(
                            getString(R.string.error_with_message, result.message),
                            Toast.LENGTH_LONG,
                        )
                    }
                    else -> {
                        // PartialSuccess durumu burada beklenmiyor
                    }
                }
            }
            dialog.dismiss()
        }

        btnNo.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setCancelable(false)
        dialog.show()

        // Güvenlik için "Hayır" butonuna focus ver
        btnNo.requestFocus()
    }
}
