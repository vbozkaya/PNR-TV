**Görev: ExoPlayer Kanal Geçişlerini Playlist Kullanarak Yeniden Yapılandırma**

**Amaç:**

Mevcut video oynatıcı (player) ekranında, TV kumandasının "Page Up" ve "Page Down" tuşlarıyla yapılan kanal geçişi mantığını, ExoPlayer'''ın dahili playlist (çalma listesi) özelliğini kullanacak şekilde yeniden düzenleyin. Bu, daha verimli, akıcı ve temiz bir kod yapısı sağlayacaktır.

**Mevcut Durum (Tahmini):**

- Kullanıcı bir kanalı izlemeye başladığında `PlayerActivity` (veya benzeri bir aktivite) açılıyor.
- `onKeyDown` metodu içinde "Page Up" (`KEYCODE_PAGE_UP`) ve "Page Down" (`KEYCODE_PAGE_DOWN`) tuşları dinleniyor.
- Bu tuşlara basıldığında, muhtemelen bir `Cursor` veya liste üzerinden bir sonraki/önceki kanalın bilgisi (URL'''i) manuel olarak alınıyor.
- Alınan yeni URL ile `player.setMediaItem()` çağrılıyor ve oynatıcı yeniden başlatılıyor.

**İstenen Değişiklikler:**

1.  **Playlist Oluşturma:**
    - Player aktivitesi başladığında, sadece izlenecek olan tek bir kanalın `MediaItem`'ını oluşturmak yerine, `Cursor`'daki **tüm kanalları** içeren bir `List<MediaItem>` oluşturun.
    - `Cursor` üzerinde bir `while (cursor.moveToNext())` döngüsü kurarak her bir satırdaki kanal URL'''ini ve adını alın.
    - Her kanal için bir `MediaItem` oluşturun. Kanal adını `MediaMetadata` kullanarak `MediaItem`'a eklemek, kullanıcı arayüzünde göstermek için faydalı olacaktır.
        ```kotlin
        val mediaMetadata = MediaMetadata.Builder()
            .setTitle("Kanal Adı")
            .build()
        
        val mediaItem = MediaItem.Builder()
            .setUri("kanal_url")
            .setMediaMetadata(mediaMetadata)
            .build()
        ```
2.  **Player'''ı Playlist ile Başlatma:**
    - Oluşturduğunuz `List<MediaItem>`'ı kullanarak ExoPlayer'''ı başlatın.
    - `player.setMediaItems(mediaItemList, startWindowIndex, startPositionMs)` metodunu kullanın.
    - `startWindowIndex` parametresi, kullanıcının izlemeye başladığı kanalın listedeki indeksi olmalıdır. Bu, oynatıcının doğru kanaldan başlamasını sağlar.
3.  **Tuş Komutlarını Güncelleme:**
    - `onKeyDown` metodu içindeki `KEYCODE_PAGE_UP` ve `KEYCODE_PAGE_DOWN` mantığını basitleştirin.
    - "Page Down" tuşuna basıldığında, `player.seekToNextMediaItem()` metodunu çağırın.
    - "Page Up" tuşuna basıldığında, `player.seekToPreviousMediaItem()` metodunu çağırın.
    - Artık manuel olarak `Cursor`'u yönetmenize veya `setMediaItem` çağırmanıza gerek kalmayacak. ExoPlayer çalma listesindeki geçişleri kendisi yönetecektir.

**Kodun Uygulanacağı Tahmini Dosya:** `PlayerActivity.kt` veya `ExoPlayer` örneğinin oluşturulup yönetildiği Activity/Fragment.

Bu değişiklikler sonucunda kanal geçişleri daha performanslı olacak ve kodunuz önemli ölçüde sadeleşecektir.
