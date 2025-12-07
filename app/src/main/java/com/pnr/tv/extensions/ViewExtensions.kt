package com.pnr.tv.extensions

import android.view.View

/**
 * View için extension functions.
 * View visibility işlemlerini daha okunabilir hale getirir.
 */

/**
 * View'ı görünür yapar.
 */
fun View.show() {
    visibility = View.VISIBLE
}

/**
 * View'ı gizler.
 */
fun View.hide() {
    visibility = View.GONE
}

/**
 * View'ı görünmez yapar (yer kaplar ama görünmez).
 */
fun View.invisible() {
    visibility = View.INVISIBLE
}

/**
 * View'ın görünür olup olmadığını kontrol eder.
 */
fun View.isVisible(): Boolean = visibility == View.VISIBLE

/**
 * View'ın gizli olup olmadığını kontrol eder.
 */
fun View.isHidden(): Boolean = visibility == View.GONE

/**
 * View'ın görünmez olup olmadığını kontrol eder.
 */
fun View.isInvisible(): Boolean = visibility == View.INVISIBLE



