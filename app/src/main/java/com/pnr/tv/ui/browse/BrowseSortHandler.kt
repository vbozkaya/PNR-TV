package com.pnr.tv.ui.browse

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.pnr.tv.R
import com.pnr.tv.model.ContentType
import com.pnr.tv.model.SortOrder
import com.pnr.tv.ui.movies.MovieViewModel
import com.pnr.tv.ui.series.SeriesViewModel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * İçerik tarama sayfasındaki sıralama (Sort) diyaloğu mantığını yöneten handler sınıfı.
 *
 * @param context Dialog oluşturmak için gerekli context
 * @param contentType İçerik türü (MOVIES veya SERIES)
 * @param movieViewModel Filmler için ViewModel
 * @param seriesViewModel Diziler için ViewModel
 * @param lifecycleOwner LifecycleOwner (genellikle viewLifecycleOwner) - coroutine scope için gerekli
 */
class BrowseSortHandler(
    private val context: Context,
    private val contentType: ContentType,
    private val movieViewModel: MovieViewModel,
    private val seriesViewModel: SeriesViewModel,
    private val lifecycleOwner: LifecycleOwner,
) {
    /**
     * Sıralama seçeneklerini gösteren diyaloğu açar.
     * Mevcut sıralama tercihini yükler ve kullanıcının yeni bir seçim yapmasına olanak tanır.
     */
    fun showSortDialog() {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_sort_options, null)
        dialog.setContentView(view)

        val window = dialog.window
        // Dialog'u ortala ve sabit genişlik kullan (CardView kendi genişliğini kullanacak)
        window?.setLayout(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
        // Arka planı şeffaf yap (CardView kendi arka planını kullanacak)
        window?.setBackgroundDrawableResource(android.R.color.transparent)

        val radioGroup = view.findViewById<RadioGroup>(R.id.radio_group_sort)
        val radioAtoZ = view.findViewById<RadioButton>(R.id.radio_a_to_z)
        val radioZtoA = view.findViewById<RadioButton>(R.id.radio_z_to_a)
        val radioRatingHighToLow = view.findViewById<RadioButton>(R.id.radio_rating_high_to_low)
        val radioRatingLowToHigh = view.findViewById<RadioButton>(R.id.radio_rating_low_to_high)
        val radioDateNewToOld = view.findViewById<RadioButton>(R.id.radio_date_new_to_old)
        val radioDateOldToNew = view.findViewById<RadioButton>(R.id.radio_date_old_to_new)
        val btnSave = view.findViewById<Button>(R.id.btn_save_sort)

        // Mevcut sıralama tercihini yükle ve seçili yap
        lifecycleOwner.lifecycleScope.launch {
            val sortOrderFlow =
                when (contentType) {
                    ContentType.MOVIES -> movieViewModel.getCurrentMovieSortOrder()
                    ContentType.SERIES -> seriesViewModel.getCurrentSeriesSortOrder()
                    else -> kotlinx.coroutines.flow.flowOf(null)
                }
            sortOrderFlow.firstOrNull()?.let { sortOrder ->
                when (sortOrder) {
                    SortOrder.A_TO_Z -> radioAtoZ.isChecked = true
                    SortOrder.Z_TO_A -> radioZtoA.isChecked = true
                    SortOrder.RATING_HIGH_TO_LOW -> radioRatingHighToLow.isChecked = true
                    SortOrder.RATING_LOW_TO_HIGH -> radioRatingLowToHigh.isChecked = true
                    SortOrder.DATE_NEW_TO_OLD -> radioDateNewToOld.isChecked = true
                    SortOrder.DATE_OLD_TO_NEW -> radioDateOldToNew.isChecked = true
                }
            } ?: run {
                // Varsayılan olarak A'dan Z'ye seçili
                radioAtoZ.isChecked = true
            }
        }

        btnSave.setOnClickListener {
            val selectedId = radioGroup.checkedRadioButtonId
            val sortOrder =
                when (selectedId) {
                    R.id.radio_a_to_z -> SortOrder.A_TO_Z
                    R.id.radio_z_to_a -> SortOrder.Z_TO_A
                    R.id.radio_rating_high_to_low -> SortOrder.RATING_HIGH_TO_LOW
                    R.id.radio_rating_low_to_high -> SortOrder.RATING_LOW_TO_HIGH
                    R.id.radio_date_new_to_old -> SortOrder.DATE_NEW_TO_OLD
                    R.id.radio_date_old_to_new -> SortOrder.DATE_OLD_TO_NEW
                    else -> SortOrder.A_TO_Z
                }

            when (contentType) {
                ContentType.MOVIES -> movieViewModel.saveMovieSortOrder(sortOrder)
                ContentType.SERIES -> seriesViewModel.saveSeriesSortOrder(sortOrder)
                else -> {}
            }
            dialog.dismiss()
        }

        // İlk focus'u ilk radio button'a ver (TV odak yönetimi)
        radioAtoZ.requestFocus()

        dialog.show()
    }
}
