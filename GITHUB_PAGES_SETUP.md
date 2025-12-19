# GitHub Pages Gizlilik Politikası Kurulum Rehberi

## ✅ Oluşturulan Dosyalar

Aşağıdaki dosyalar `docs/` klasöründe oluşturuldu:

- ✅ `index.html` - Ana sayfa (dil seçimi)
- ✅ `privacy-policy.html` - Türkçe gizlilik politikası
- ✅ `privacy-policy-en.html` - İngilizce gizlilik politikası
- ✅ `README.md` - Kurulum talimatları

## 🚀 GitHub Pages Kurulum Adımları

### 1. Dosyaları GitHub'a Push Edin

```bash
git add docs/
git commit -m "Add privacy policy pages for GitHub Pages"
git push origin main
```

### 2. GitHub Repository Ayarlarını Yapın

1. GitHub repository'nize gidin
2. **Settings** sekmesine tıklayın
3. Sol menüden **Pages** seçeneğine tıklayın
4. **Source** bölümünde:
   - **Branch:** `main` (veya `master`) seçin
   - **Folder:** `/docs` seçin
5. **Save** butonuna tıklayın

### 3. URL'nizi Alın

GitHub Pages URL'niz şu formatta olacak:
```
https://yourusername.github.io/repository-name/privacy-policy.html
```

Örnek:
```
https://github.com/username/pnr-tv → https://username.github.io/pnr-tv/privacy-policy.html
```

### 4. Gizlilik Politikası URL'sini Güncelleyin

`docs/privacy-policy.html` dosyasını açın ve şu satırı bulun (yaklaşık satır 195):
```html
<strong>GitHub:</strong> <a href="https://github.com/yourusername/pnr-tv" target="_blank">Proje Sayfası</a>
```

`yourusername` ve `pnr-tv` kısımlarını kendi repository bilgilerinizle değiştirin.

### 5. Google Play Console'a Ekleyin

1. Google Play Console'a giriş yapın
2. Uygulamanızı seçin
3. **Policy** > **App content** bölümüne gidin
4. **Privacy Policy** bölümünde URL'nizi ekleyin:
   ```
   https://yourusername.github.io/pnr-tv/privacy-policy.html
   ```

## 📝 Önemli Notlar

- GitHub Pages'in aktif olması birkaç dakika sürebilir
- İlk yayınlandıktan sonra URL'nizi tarayıcıda test edin
- HTTPS otomatik olarak etkinleştirilir (Google Play için gerekli)
- Custom domain kullanmak isterseniz GitHub Pages ayarlarından ekleyebilirsiniz

## 🔍 Test Etme

1. GitHub Pages URL'nizi tarayıcıda açın
2. Sayfanın doğru göründüğünü kontrol edin
3. Dil değiştirme linklerinin çalıştığını test edin
4. Mobil cihazlarda görünümü kontrol edin

## ✅ Kontrol Listesi

- [ ] Dosyalar GitHub'a push edildi
- [ ] GitHub Pages ayarları yapıldı
- [ ] URL çalışıyor ve erişilebilir
- [ ] Gizlilik politikası HTML'deki GitHub linki güncellendi
- [ ] Google Play Console'a URL eklendi
- [ ] Her iki dil versiyonu test edildi

## 🆘 Sorun Giderme

**Sayfa görünmüyor:**
- GitHub Pages ayarlarını kontrol edin
- Branch ve folder seçimlerinin doğru olduğundan emin olun
- Birkaç dakika bekleyin (ilk yayınlama biraz zaman alabilir)

**404 Hatası:**
- URL'deki repository adının doğru olduğundan emin olun
- `docs/` klasörünün repository root'unda olduğunu kontrol edin

**Stil görünmüyor:**
- HTML dosyalarındaki CSS'in inline olduğundan emin olun (zaten öyle)
- Tarayıcı cache'ini temizleyin

