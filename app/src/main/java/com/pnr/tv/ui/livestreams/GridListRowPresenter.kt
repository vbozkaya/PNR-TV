package com.pnr.tv.ui.livestreams

import android.view.View
import android.view.ViewGroup
import androidx.leanback.widget.HorizontalGridView
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.RowPresenter

/**
 * Grid layout için özelleştirilmiş ListRowPresenter.
 * Her satırda 7 sütun gösterir.
 * HorizontalGridView'da sütun sayısı yok, bunun yerine kart genişliğini ayarlayarak
 * her satırda 7 kart gösterilmesini sağlıyoruz.
 */
class GridListRowPresenter : ListRowPresenter() {
    init {
        // Shadow (gölge) efektini etkinleştir - kategoriler ve içerikler arasında görsel ayırıcı
        shadowEnabled = true
    }

    override fun createRowViewHolder(parent: ViewGroup): RowPresenter.ViewHolder {
        val viewHolder = super.createRowViewHolder(parent)

        // HorizontalGridView'ı bul ve ayarları yap
        // View oluşturulduktan sonra ayarla
        viewHolder.view.post {
            val gridView = findGridView(viewHolder.view)
            if (gridView != null) {
                // HorizontalGridView'ın padding'ini ayarla
                gridView.setPadding(0, 0, 0, 0)

                // Her öğe için genişlik ayarı CardPresenter'da yapılıyor
            }
        }

        return viewHolder
    }

    /**
     * View hiyerarşisinde HorizontalGridView'ı bulur.
     */
    private fun findGridView(view: View?): HorizontalGridView? {
        if (view == null) return null
        if (view is HorizontalGridView) return view

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                val result = findGridView(child)
                if (result != null) return result
            }
        }

        return null
    }
}
