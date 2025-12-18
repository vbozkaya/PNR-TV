package com.pnr.tv.ui.base

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import timber.log.Timber

/**
 * TV cihazları için odak yönetimi ve pürüzsüz kaydırma sağlayan özel GridLayoutManager.
 *
 * Bu sınıf, onFocusSearchFailed metodunu override ederek:
 * - Hızlı kaydırma (basılı tutma) sırasında listenin başa/sona atlamasını engeller
 * - Grid'in sınırlarında (en üst/en alt satır, en sol/en sağ sütun) odağın dışarı kaçmasını engeller
 */
class CustomGridLayoutManager(
    context: Context,
    spanCount: Int,
) : GridLayoutManager(context, spanCount) {
    override fun onFocusSearchFailed(
        focused: View,
        focusDirection: Int,
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State,
    ): View? {
        val fromPos = getPosition(focused)
        val itemCount = state.itemCount
        val isLastRow = (fromPos / spanCount) == ((itemCount - 1) / spanCount)
        val isFirstRow = (fromPos / spanCount) == 0
        val isLeftmostColumn = (fromPos % spanCount) == 0
        val isRightmostColumn = ((fromPos + 1) % spanCount) == 0

        val directionName =
            when (focusDirection) {
                View.FOCUS_LEFT -> "LEFT"
                View.FOCUS_RIGHT -> "RIGHT"
                View.FOCUS_UP -> "UP"
                View.FOCUS_DOWN -> "DOWN"
                else -> "UNKNOWN($focusDirection)"
            }

        Timber.tag("ContentGrid").d(
            "🔍 onFocusSearchFailed: pos=$fromPos, direction=$directionName, " +
                "itemCount=$itemCount, spanCount=$spanCount, " +
                "isLastRow=$isLastRow, isFirstRow=$isFirstRow, " +
                "isLeftmost=$isLeftmostColumn, isRightmost=$isRightmostColumn",
        )

        when (focusDirection) {
            View.FOCUS_LEFT -> {
                // En soldaki sütundaysak, odağın dışarı çıkmasına izin ver (kategorilere dönsün).
                // Bu, kullanıcının sol panele bilinçli geçiş yapabilmesi için gereklidir.
                // En alt satırda sağa/sola atlama kullanıcının davranışına göre serbest.
                if (isLeftmostColumn) {
                    Timber.tag("ContentGrid").d("✅ FOCUS_LEFT: En soldaki sütun, dışarı çıkmasına izin veriliyor")
                    return super.onFocusSearchFailed(focused, focusDirection, recycler, state)
                }
            }

            View.FOCUS_RIGHT -> {
                // En sağdaki sütundaysak, odağı tüket (mevcut öğede kal).
                // Bu, odağın sağa doğru dışarı kaçmasını engeller.
                // En alt satırda sağa/sola atlama kullanıcının davranışına göre serbest.
                if (isRightmostColumn) {
                    Timber.tag("ContentGrid").d("🛑 FOCUS_RIGHT: En sağdaki sütun, focus tüketiliyor")
                    return focused
                }
            }

            View.FOCUS_UP -> {
                // En üst satırdaysak, odağın navbar'a (search/filtre butonlarına) çıkmasına izin ver.
                // Bu, kullanıcının üst menüye erişebilmesi için gereklidir.
                // Kontrol yok - varsayılan davranış kullanılacak (super'e gidecek)
                Timber.tag("ContentGrid").d("ℹ️ FOCUS_UP: Varsayılan davranış kullanılıyor")
            }

            View.FOCUS_DOWN -> {
                // En alt satırda olup olmadığımızı kontrol et.
                if (isLastRow) {
                    // En alt satırdaysak, odağı tüket (mevcut öğede kal).
                    // Bu, odağın aşağıya doğru kaçmasını engeller.
                    Timber.tag("ContentGrid").d("🛑 FOCUS_DOWN: En alt satır, focus tüketiliyor (pos=$fromPos)")
                    return focused
                } else {
                    Timber.tag("ContentGrid").d("ℹ️ FOCUS_DOWN: Son satır değil, varsayılan davranış")
                }
            }
        }

        // Yukarıdaki özel durumlar dışındaki tüm hallerde,
        // bir sonraki odaklanacak öğeyi bulmak için varsayılan davranışı kullan.
        Timber.tag("ContentGrid").d("➡️ onFocusSearchFailed: Varsayılan davranışa dönülüyor")
        return super.onFocusSearchFailed(focused, focusDirection, recycler, state)
    }
}
