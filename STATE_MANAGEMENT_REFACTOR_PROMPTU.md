# PNR TV Uygulaması Durum Yönetimi (State Management) ve Önbellekleme (Caching) İyileştirme Planı

## 1. Tespit Edilen Sorun

Uygulamanın mevcut mimarisi, her içerik kategorisi (`Canlı TV`, `Filmler`, `Diziler`) için ayrı bir `Fragment` ve bu `Fragment`'ın ömrüne bağlı, kısa ömürlü bir `ViewModel` kullanmaktadır. Kullanıcı bir kategori ekranından ayrılıp ana menüye döndüğünde, o `Fragment` ve dolayısıyla `ViewModel`'i yok edilir. Bu, o `ViewModel` içinde tutulan tüm verilerin (kanal listeleri, film listeleri vb.) hafızadan silinmesine neden olur.

Sonuç olarak, kullanıcı aynı kategoriye tekrar girdiğinde, tüm veriler ağdan (API) yeniden çekilmek zorunda kalır. Bu durum aşağıdaki olumsuzluklara yol açar:

*   **Gereksiz Yükleme Süreleri:** Kullanıcı, daha önce yüklenmiş içerikler için tekrar tekrar beklemek zorunda kalır.
*   **Kötü Kullanıcı Deneyimi:** Sürekli yükleme ekranları ve takılmalar, uygulamayı kullanmayı zorlaştırır.
*   **Artan Ağ ve Kaynak Kullanımı:** Aynı verilerin defalarca indirilmesi, kullanıcının internet kotasını ve cihazın pilini israf eder.

## 2. Hedeflenen Çözüm: Merkezi Durum Yönetimi

Çözüm, verileri `Fragment`'ların kısa yaşam döngüsünden kurtarıp, uygulama boyunca hayatta kalan **tek ve merkezi bir yerden** yönetmektir. Bu, **Paylaşılan ViewModel (Shared ViewModel)** deseni ile etkili bir şekilde gerçekleştirilebilir.

Mevcut `MainViewModel`, sahibi `MainActivity` olduğu için bu rol için mükemmel bir adaydır. Tüm içerik `Fragment`'ları (`LiveStreamsBrowseFragment`, `ContentBrowseFragment`), kendi `ViewModel`'lerini oluşturmak yerine, `MainActivity`'ye ait bu tek ve ortak `MainViewModel` örneğine erişecektir.

Bu sayede, `Fragment`'lar yok edilip yeniden yaratılsa bile, veriler `MainViewModel` içinde güvende kalır ve yeniden yüklenmelerine gerek kalmaz.

## 3. Eylem Planı

### Eylem 1: `MainViewModel`'i Merkezi Veri Yöneticisi Haline Getirme

**Amaç:** `LiveStreamsViewModel` ve `ContentBrowseViewModel`'in tüm sorumluluklarını `MainViewModel`'e taşımak.

**Dosya:** `app/src/main/java/com/pnr/tv/MainViewModel.kt`

**Yapılacak Değişiklikler:**

1.  **Veri Akışları (Flows) Ekleme:**
    Canlı yayınlar, filmler ve diziler için veri listelerini, kategori listelerini, yükleme durumlarını ve hata mesajlarını tutacak `StateFlow`'ları ekleyin.

    ```kotlin
    // Örnek: Canlı Yayınlar için
    private val _liveStreamCategories = MutableStateFlow<List<CategoryItem>>(emptyList())
    val liveStreamCategories: StateFlow<List<CategoryItem>> = _liveStreamCategories.asStateFlow()

    private val _liveStreams = MutableStateFlow<List<ContentItem>>(emptyList())
    val liveStreams: StateFlow<List<ContentItem>> = _liveStreams.asStateFlow()

    private val _isLiveStreamsLoading = MutableStateFlow(false)
    val isLiveStreamsLoading: StateFlow<Boolean> = _isLiveStreamsLoading.asStateFlow()

    // Filmler ve Diziler için de benzerlerini ekleyin...
    ```

2.  **Veri Yükleme Fonksiyonları Ekleme:**
    Her içerik türü için veri yükleyecek fonksiyonlar ekleyin. Bu fonksiyonlar, verinin daha önce yüklenip yüklenmediğini kontrol etmeli ve sadece gerekliyse `Repository`'ye gitmelidir.

    ```kotlin
    fun loadLiveStreamsIfNeeded() {
        // Eğer canlı yayınlar zaten yüklendiyse, tekrar yükleme.
        if (_liveStreams.value.isNotEmpty()) return

        viewModelScope.launch {
            _isLiveStreamsLoading.value = true
            // refreshLiveStreams repository'den Result<Unit> döndürür
            val result = liveStreamRepository.refreshLiveStreams()
            if (result is Result.Error) {
                // Hata durumunu yönet
                _errorMessage.value = result.message
            }
            // Başarılı durumda, veriler zaten DAO'dan Flow aracılığıyla gelecektir.
            // Repository'den gelen veriyi doğrudan dinlemek daha iyi bir yaklaşımdır.
            _isLiveStreamsLoading.value = false
        }
    }

    // loadMoviesIfNeeded() ve loadSeriesIfNeeded() için de benzer fonksiyonlar ekleyin.
    ```

3.  **Repository'den Gelen Veriyi Dinleme:**
    `ViewModel`'in `init` bloğu içinde, `Repository`'den gelen `Flow`'ları dinleyerek `StateFlow`'ları güncelleyin.

    ```kotlin
    init {
        // Canlı Yayınları Dinle
        viewModelScope.launch {
            liveStreamRepository.getLiveStreams().collect { streams ->
                _liveStreams.value = streams
            }
        }
        viewModelScope.launch {
            liveStreamRepository.getLiveStreamCategories().collect { categories ->
                // Gerekirse CategoryItem'a dönüştür
                _liveStreamCategories.value = categories.map { CategoryItem(...) }
            }
        }
        // Filmler ve diziler için de benzer `collect` blokları ekleyin.
    }
    ```

---

### Eylem 2: İçerik `Fragment`'larını Paylaşılan `ViewModel`'i Kullanacak Şekilde Düzenleme

**Amaç:** `LiveStreamsBrowseFragment` ve `ContentBrowseFragment`'ın kendi `ViewModel`'lerini yaratmasını engelleyip, `MainActivity`'nin ortak `MainViewModel`'ini kullanmalarını sağlamak.

**Dosya 1:** `app/src/main/java/com/pnr/tv/ui/livestreams/LiveStreamsBrowseFragment.kt`
**Dosya 2:** `app/src/main/java/com/pnr/tv/ui/browse/ContentBrowseFragment.kt`

**Yapılacak Değişiklikler (Her iki dosya için de):**

1.  **ViewModel Tanımını Değiştirme:**
    `by viewModels()` yerine `by activityViewModels()` kullanarak `ViewModel`'i `Activity` kapsamında alın.

    **Eski Hali:**
    ```kotlin
    override val viewModel: LiveStreamsViewModel by viewModels()
    // veya
    private val viewModel: ContentBrowseViewModel by viewModels()
    ```

    **Yeni Hali:**
    ```kotlin
    // ViewModel'in tipini MainViewModel olarak değiştirin.
    private val viewModel: MainViewModel by activityViewModels()
    ```

2.  **Veri Yükleme Çağrısını Güncelleme:**
    `onInitialLoad` veya `onCreate` içindeki veri yükleme çağrısını, `MainViewModel`'deki yeni `...ifNeeded` metodunu kullanacak şekilde güncelleyin.

    ```kotlin
    // LiveStreamsBrowseFragment içinde
    override fun onInitialLoad() {
        viewModel.loadLiveStreamsIfNeeded()
    }

    // ContentBrowseFragment içinde (içerik tipine göre)
    private fun loadData() {
        when (viewModel.contentType) { // viewModel'den alınacak
            ContentType.MOVIES -> viewModel.loadMoviesIfNeeded()
            ContentType.SERIES -> viewModel.loadSeriesIfNeeded()
        }
    }
    ```

3.  **Veri Akışlarını (Flows) Dinleme:**
    `Fragment` içindeki `categoriesFlow`, `contentsFlow` gibi `Flow` referanslarını, artık `MainViewModel`'den gelen `StateFlow`'ları gösterecek şekilde güncelleyin.

    ```kotlin
    // LiveStreamsBrowseFragment içinde
    override val categoriesFlow: Flow<List<CategoryItem>>
        get() = viewModel.liveStreamCategories // MainViewModel'den

    override val contentsFlow: Flow<List<ContentItem>>
        get() = viewModel.liveStreams // MainViewModel'den
    ```

---

### Eylem 3: Gereksiz `ViewModel`'leri Silme

**Amaç:** Projeyi temizlemek ve eski, kullanılmayan sınıfları kaldırmak.

**Yapılacak Değişiklikler:**

1.  `LiveStreamsViewModel.kt` dosyasını projeden tamamen silin.
2.  `ContentBrowseViewModel.kt` dosyasını projeden tamamen silin.

Bu refactor işlemi tamamlandığında, uygulama verileri merkezi bir `ViewModel`'de bir kez yüklenecek ve tüm ekranlar tarafından ortaklaşa kullanılacaktır. Bu, sürekli yükleme sorununu tamamen ortadan kaldıracak, uygulama performansını ve kullanıcı deneyimini önemli ölçüde iyileştirecektir.
