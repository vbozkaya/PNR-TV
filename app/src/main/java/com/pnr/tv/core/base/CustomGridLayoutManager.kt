package com.pnr.tv.core.base

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

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

        when (focusDirection) {
            View.FOCUS_LEFT -> {
                // En soldaki sütundaysak, odağın dışarı çıkmasına izin ver (kategorilere dönsün).
                // Bu, kullanıcının sol panele bilinçli geçiş yapabilmesi için gereklidir.
                // En alt satırda sağa/sola atlama kullanıcının davranışına göre serbest.
                if (isLeftmostColumn) {
                    return super.onFocusSearchFailed(focused, focusDirection, recycler, state)
                }
            }

            View.FOCUS_RIGHT -> {
                // En sağdaki sütundaysak, odağı tüket (mevcut öğede kal).
                // Bu, odağın sağa doğru dışarı kaçmasını engeller.
                // En alt satırda sağa/sola atlama kullanıcının davranışına göre serbest.
                if (isRightmostColumn) {
                    return focused
                }
            }

            View.FOCUS_UP -> {
                // En üst satırdaysak, focus'u navbar'a gönderebiliriz.
                // Android'in doğal focus search mekanizması navbar'a yönlendirebilir
            }

            View.FOCUS_DOWN -> {
                // En alt satırda olup olmadığımızı kontrol et.
                if (isLastRow) {
                    // En alt satırdaysak, odağı tüket (mevcut öğede kal).
                    // Bu, odağın aşağıya doğru kaçmasını engeller.
                    return focused
                }
            }
        }

        // Yukarıdaki özel durumlar dışındaki tüm hallerde,
        // bir sonraki odaklanacak öğeyi bulmak için varsayılan davranışı kullan.
        return super.onFocusSearchFailed(focused, focusDirection, recycler, state)
    }
}
