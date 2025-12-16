**Görev:** Kanal Değişikliğinden Sonra Otomatik Oynatma Sorununu Düzeltme

**Amaç:**

`PlayerActivity`'de, TV kumandasının "Page Up" veya "Page Down" tuşlarıyla çalma listesindeki (playlist) bir sonraki veya önceki kanala geçildiğinde, videonun otomatik olarak oynamaya başlamasını sağlamak.

**Mevcut Durum ve Sorun:**

`PlayerViewModel.kt` dosyası içerisindeki `seekToNextChannel` ve `seekToPreviousChannel` fonksiyonları, ExoPlayer'''ın `seekToNextMediaItem()` ve `seekToPreviousMediaItem()` metodlarını çağırıyor. Bu metodlar çalma listesindeki kanalı başarılı bir şekilde değiştiriyor, ancak oynatıcı duraklatılmış (paused) bir durumdaysa oynatmayı otomatik olarak başlatmıyor. Bu nedenle, kullanıcı kanal değiştirdiğinde siyah bir ekran görüyor ve oynatma başlamıyor.

**İstenen Değişiklik:**

Kanal geçişi yapıldıktan hemen sonra oynatmayı manuel olarak tetiklemek.

**Uygulama Adımları:**

1.  **Dosyayı Açın:** `app/src/main/java/com/pnr/tv/ui/player/PlayerViewModel.kt`

2.  **Fonksiyonları Düzenleyin:** Aşağıda belirtilen iki fonksiyonda, `seekTo...` komutundan hemen sonra `player?.play()` satırını ekleyin.

    - **`seekToNextChannel` Fonksiyonu:**
      Mevcut hali:
        ```kotlin
        fun seekToNextChannel(): Boolean {
            val canSeek = player?.hasNextMediaItem() == true
            if (canSeek) {
                player?.seekToNextMediaItem()
                Timber.d("⏭️ Sonraki kanala geçildi")
            } else {
                Timber.d("⚠️ Sonraki kanal yok, playlist sonunda")
            }
            return canSeek
        }
        ```

      Olması gereken hali:
        ```kotlin
        fun seekToNextChannel(): Boolean {
            val canSeek = player?.hasNextMediaItem() == true
            if (canSeek) {
                player?.seekToNextMediaItem()
                player?.play() // <--- BU SATIRI EKLEYİN
                Timber.d("⏭️ Sonraki kanala geçildi")
            } else {
                Timber.d("⚠️ Sonraki kanal yok, playlist sonunda")
            }
            return canSeek
        }
        ```

    - **`seekToPreviousChannel` Fonksiyonu:**
      Mevcut hali:
        ```kotlin
        fun seekToPreviousChannel(): Boolean {
            val canSeek = player?.hasPreviousMediaItem() == true
            if (canSeek) {
                player?.seekToPreviousMediaItem()
                Timber.d("⏮️ Önceki kanala geçildi")
            } else {
                Timber.d("⚠️ Önceki kanal yok, playlist başında")
            }
            return canSeek
        }
        ```

      Olması gereken hali:
        ```kotlin
        fun seekToPreviousChannel(): Boolean {
            val canSeek = player?.hasPreviousMediaItem() == true
            if (canSeek) {
                player?.seekToPreviousMediaItem()
                player?.play() // <--- BU SATIRI EKLEYİN
                Timber.d("⏮️ Önceki kanala geçildi")
            } else {
                Timber.d("⚠️ Önceki kanal yok, playlist başında")
            }
            return canSeek
        }
        ```

Bu basit eklemeler, kanal değiştirildiğinde oynatıcının `play()` metodunu çağırarak videoyu otomatik olarak başlatmasını sağlayacaktır.
