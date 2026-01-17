package com.pnr.tv.ui.player.component

import java.util.concurrent.TimeUnit

/**
 * Zaman formatlama işlemlerini yöneten utility sınıfı.
 * Milisaniye cinsinden zamanı kullanıcı dostu string formatına çevirir.
 */
object PlayerTimeFormatter {
    /**
     * Zaman formatını döndürür (ms cinsinden).
     * @param timeMs Zaman (milisaniye cinsinden)
     * @return Formatlanmış zaman string'i (HH:MM:SS veya MM:SS)
     */
    fun format(timeMs: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(timeMs)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(timeMs) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(timeMs) % 60

        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }
}
