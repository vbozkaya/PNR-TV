package com.pnr.tv.ui.base

import android.content.Context
import androidx.recyclerview.widget.GridLayoutManager

/**
 * Odak yönetimi ViewHolder'a taşındığı için bu sınıf artık standart bir GridLayoutManager'dır.
 * Sadece ileride gerekebilecek özel layout davranışları için yerini koruyoruz.
 */
class CustomGridLayoutManager(
    context: Context,
    spanCount: Int,
) : GridLayoutManager(context, spanCount)
