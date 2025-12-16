package com.pnr.tv.ui.livestreams

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

        // findViewById direkt olarak burada ve daha güvenli bir şekilde çağrılabilir.
        // Leanback kütüphanesinin ListRow'u için HorizontalGridView'ın ID'si her zaman
        // androidx.leanback.R.id.row_content'dir.
        val gridView = viewHolder.view.findViewById<HorizontalGridView>(androidx.leanback.R.id.row_content)

        // view.post'a gerek kalmadan doğrudan ayarlanabilir, çünkü view holder oluşturuldu.
        if (gridView != null) {
            // HorizontalGridView'ın padding'ini ayarla
            gridView.setPadding(0, 0, 0, 0)

            // View cache ayarları - daha akıcı scroll için
            // RecycledViewPool kullanarak view'ları yeniden kullan
            // Bu, scroll sırasında view oluşturma maliyetini azaltır
            gridView.setItemViewCacheSize(10) // Önceden oluşturulmuş view'ları cache'le
        }

        return viewHolder
    }
}
