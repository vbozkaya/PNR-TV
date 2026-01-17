package com.pnr.tv.ui.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.pnr.tv.R
import com.pnr.tv.util.ui.LocaleHelper

/**
 * Dil seçimi için RecyclerView Adapter
 */
class LanguageAdapter(
    private val languages: List<LocaleHelper.SupportedLanguage>,
    private val currentLanguageCode: String?,
) : RecyclerView.Adapter<LanguageAdapter.LanguageViewHolder>() {
    // Seçili dil adapter içinde tutuluyor
    private var selectedLanguageCode: String? = null

    fun getSelectedLanguageCode(): String? = selectedLanguageCode

    fun updateSelectedLanguage(newLanguageCode: String?) {
        val oldPosition =
            if (selectedLanguageCode != null) {
                languages.indexOfFirst { it.code == selectedLanguageCode }
            } else {
                -1
            }
        selectedLanguageCode = newLanguageCode
        val newPosition =
            if (selectedLanguageCode != null) {
                languages.indexOfFirst { it.code == selectedLanguageCode }
            } else {
                -1
            }

        // Eski ve yeni pozisyonları güncelle
        if (oldPosition >= 0) notifyItemChanged(oldPosition)
        if (newPosition >= 0) notifyItemChanged(newPosition)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): LanguageViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.item_language, parent, false)
        return LanguageViewHolder(itemView)
    }

    override fun onBindViewHolder(
        holder: LanguageViewHolder,
        position: Int,
    ) {
        val language = languages[position]
        val isCurrentLanguage = language.code == currentLanguageCode
        // Adapter içindeki selectedLanguageCode'u kullan
        val isSelected = this.selectedLanguageCode != null && language.code == this.selectedLanguageCode

        // Dil adını göster
        val languageNameRes =
            when (language) {
                LocaleHelper.SupportedLanguage.TURKISH -> R.string.language_turkish
                LocaleHelper.SupportedLanguage.ENGLISH -> R.string.language_english
                LocaleHelper.SupportedLanguage.SPANISH -> R.string.language_spanish
                LocaleHelper.SupportedLanguage.INDONESIAN -> R.string.language_indonesian
                LocaleHelper.SupportedLanguage.HINDI -> R.string.language_hindi
                LocaleHelper.SupportedLanguage.PORTUGUESE -> R.string.language_portuguese
                LocaleHelper.SupportedLanguage.FRENCH -> R.string.language_french
                LocaleHelper.SupportedLanguage.JAPANESE -> R.string.language_japanese
                LocaleHelper.SupportedLanguage.THAI -> R.string.language_thai
            }

        holder.textView.text = holder.itemView.context.getString(languageNameRes)

        // Seçili dil için görsel işaretleme (sadece kullanıcının seçtiği dil)
        if (isSelected) {
            holder.textView.alpha = 1.0f
            holder.textView.textSize = 20f // Seçili dil için daha büyük
            holder.selectedIndicator.visibility = View.VISIBLE
        } else {
            // Mevcut dil veya seçilmemiş dil
            holder.textView.alpha = if (isCurrentLanguage) 0.9f else 0.7f
            holder.textView.textSize = 18f // Normal boyut
            holder.selectedIndicator.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            // Sadece seçili dili güncelle, dialog kapanmasın
            // Eğer aynı dil tekrar seçilirse, seçimi kaldır
            if (this.selectedLanguageCode == language.code) {
                updateSelectedLanguage(null)
            } else {
                updateSelectedLanguage(language.code)
            }
        }
    }

    override fun getItemCount() = languages.size

    class LanguageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textView = itemView.findViewById<android.widget.TextView>(R.id.tv_language_name)
        val selectedIndicator = itemView.findViewById<android.widget.TextView>(R.id.tv_selected_indicator)
    }
}
