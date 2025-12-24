@MovieRepository.kt dosyasında bulunan `refreshMovies` metodunu güncellemeni istiyorum.

Şu anki durumda `refreshMovies` sadece filmleri güncelliyor, ancak kategorilerde değişiklik olduğunda (örneğin bir kategori silindiğinde) bu durum uygulamaya yansımıyor ve boş kategoriler kalıyor.

Yapılacak değişiklik:
`refreshMovies` metodu çalıştığında, işlem başarılı olduktan hemen sonra otomatik olarak `refreshMovieCategories()` metodunu da çağırmalıdır. Böylece filmler güncellendiğinde kategoriler de senkronize edilmiş olur.

Lütfen `refreshMovies` metodunun sonuna (loglamalardan sonra, result dönmeden önce) `refreshMovieCategories()` çağrısını ekle. Eğer `refreshMovieCategories` başarısız olursa, bunu bir hata olarak yansıtma, sadece logla (örneğin `Timber.w("Kategori güncellemesi başarısız oldu")`). Ana işlem (film güncellemesi) başarılıysa Result.Success dönmeye devam etsin.

Aynı mantığı `SeriesRepository` içindeki `refreshSeries` ve `LiveStreamRepository` içindeki `refreshLiveStreams` metodlarına da uygula (Eğer ilgili metodlar varsa).

Özetle: İçerik güncellemeleri tetiklendiğinde, ilgili kategori güncellemeleri de otomatik olarak tetiklenmelidir.