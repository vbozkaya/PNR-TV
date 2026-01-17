package com.pnr.tv.ui.player.handler

import com.pnr.tv.db.entity.LiveStreamEntity

/**
 * Kanal listesi paneli olaylarını dinlemek için interface.
 */
interface ChannelListListener {
    /**
     * Kullanıcı bir kanal seçtiğinde çağrılır.
     * @param channel Seçilen kanal
     */
    fun onChannelSelected(channel: LiveStreamEntity)
}
