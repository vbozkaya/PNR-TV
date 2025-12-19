# GitHub'a Yayınlama Rehberi - PNR TV

Bu rehber projenizi GitHub'a yayınlamak için gereken tüm adımları içerir.

## 📋 İçindekiler

1. [Git Repository Hazırlığı](#1-git-repository-hazırlığı)
2. [GitHub Repository Oluşturma](#2-github-repository-oluşturma)
3. [GitHub Secrets Ekleme](#3-github-secrets-ekleme)
4. [GitHub Pages Kurulumu](#4-github-pages-kurulumu)
5. [İlk Push](#5-ilk-push)
6. [Doğrulama](#6-doğrulama)

---

## 1. Git Repository Hazırlığı

Projeniz zaten bir Git repository olarak başlatıldı. Şimdi dosyaları commit edelim:

### Adımlar:

1. **Tüm dosyaları staging area'ya ekle:**
   ```powershell
   git add .
   ```

2. **İlk commit'i yap:**
   ```powershell
   git commit -m "Initial commit: PNR TV Android TV Application"
   ```

3. **Branch'i main olarak ayarla:**
   ```powershell
   git branch -M main
   ```

---

## 2. GitHub Repository Oluşturma

### Adımlar:

1. GitHub'da yeni bir repository oluşturun:
   - https://github.com/new adresine gidin
   - Repository adı: `PNR-TV` (veya istediğiniz isim)
   - **ÖNEMLİ:** "Initialize with README" seçeneğini **İŞARETLEMEYİN**
   - "Add .gitignore" seçeneğini **İŞARETLEMEYİN** (zaten var)
   - "Choose a license" seçeneğini isterseniz işaretleyebilirsiniz (MIT önerilir)
   - "Create repository" butonuna tıklayın

2. **Remote repository'yi ekle:**
   ```powershell
   git remote add origin https://github.com/KULLANICI_ADINIZ/REPOSITORY_ADI.git
   ```
   
   Örnek:
   ```powershell
   git remote add origin https://github.com/vbozkaya/PNR-TV.git
   ```

3. **Remote'un doğru eklendiğini kontrol et:**
   ```powershell
   git remote -v
   ```

---

## 3. GitHub Secrets Ekleme

GitHub Actions workflow'unun çalışması için TMDB API anahtarını GitHub Secrets'a eklemeniz gerekir.

### Adımlar:

1. GitHub repository'nize gidin
2. **Settings** sekmesine tıklayın
3. Sol menüden **Secrets and variables** > **Actions** seçeneğine tıklayın
4. **New repository secret** butonuna tıklayın
5. Şu bilgileri girin:
   - **Name:** `TMDB_API_KEY`
   - **Secret:** `local.properties` dosyanızdaki TMDB_API_KEY değeri
6. **Add secret** butonuna tıklayın

### Secret'ı Bulma:

`local.properties` dosyanızı açın ve şu satırı bulun:
```
TMDB_API_KEY=your_api_key_here
```

Bu değeri kopyalayıp GitHub Secrets'a ekleyin.

---

## 4. GitHub Pages Kurulumu

Gizlilik politikası sayfalarını yayınlamak için GitHub Pages'i aktifleştirmeniz gerekir.

### Adımlar:

1. GitHub repository'nize gidin
2. **Settings** sekmesine tıklayın
3. Sol menüden **Pages** seçeneğine tıklayın
4. **Source** bölümünde:
   - **Branch:** `main` seçin
   - **Folder:** `/docs` seçin
5. **Save** butonuna tıklayın

### URL Formatı:

GitHub Pages URL'niz şu formatta olacak:
```
https://KULLANICI_ADINIZ.github.io/REPOSITORY_ADI/privacy-policy.html
```

Örnek:
```
https://vbozkaya.github.io/PNR-TV/privacy-policy.html
```

### Gizlilik Politikası URL'sini Güncelleme:

1. `docs/privacy-policy.html` dosyasını açın
2. Yaklaşık satır 195 civarında şu satırı bulun:
   ```html
   <strong>GitHub:</strong> <a href="https://github.com/yourusername/pnr-tv" target="_blank">Proje Sayfası</a>
   ```
3. `yourusername` ve `pnr-tv` kısımlarını kendi repository bilgilerinizle değiştirin
4. Dosyayı kaydedin ve commit edin

---

## 5. İlk Push

Tüm hazırlıklar tamamlandıktan sonra kodu GitHub'a push edin:

### Adımlar:

1. **Ana branch'e push yap:**
   ```powershell
   git push -u origin main
   ```

2. Eğer GitHub Authentication istenirse:
   - Personal Access Token kullanmanız gerekebilir
   - Veya GitHub Desktop kullanabilirsiniz
   - Veya SSH key kullanabilirsiniz

### GitHub Authentication:

Eğer kimlik doğrulama hatası alırsanız:

**Seçenek 1: Personal Access Token (Önerilen)**
1. GitHub'da Settings > Developer settings > Personal access tokens > Tokens (classic)
2. "Generate new token" butonuna tıklayın
3. "repo" scope'unu işaretleyin
4. Token'ı oluşturun ve kopyalayın
5. Push yaparken password yerine bu token'ı kullanın

**Seçenek 2: GitHub Desktop**
1. GitHub Desktop uygulamasını kullanarak repository'yi açın
2. "Publish repository" butonuna tıklayın

---

## 6. Doğrulama

### GitHub Actions Kontrolü:

1. GitHub repository'nize gidin
2. **Actions** sekmesine tıklayın
3. İlk workflow run'ının başarılı olduğunu kontrol edin
4. Test, lint ve build adımlarının tamamlandığını doğrulayın

### GitHub Pages Kontrolü:

1. Repository Settings > Pages bölümüne gidin
2. URL'in aktif olduğunu kontrol edin (birkaç dakika sürebilir)
3. URL'yi tarayıcıda açarak sayfaların göründüğünü test edin:
   - Ana sayfa: `https://KULLANICI_ADINIZ.github.io/REPOSITORY_ADI/`
   - Türkçe gizlilik politikası: `https://KULLANICI_ADINIZ.github.io/REPOSITORY_ADI/privacy-policy.html`
   - İngilizce gizlilik politikası: `https://KULLANICI_ADINIZ.github.io/REPOSITORY_ADI/privacy-policy-en.html`

### Google Play Console İçin:

1. Google Play Console'a gidin
2. Uygulamanızı seçin
3. **Policy** > **App content** bölümüne gidin
4. **Privacy Policy** bölümünde GitHub Pages URL'nizi ekleyin:
   ```
   https://KULLANICI_ADINIZ.github.io/REPOSITORY_ADI/privacy-policy.html
   ```

---

## ✅ Kontrol Listesi

Push yapmadan önce kontrol edin:

- [ ] `.gitignore` dosyası doğru yapılandırılmış (local.properties, keystore'lar, build klasörleri hariç)
- [ ] Hassas bilgiler (API key'ler, keystore'lar) `.gitignore`'da
- [ ] `docs/` klasöründe gizlilik politikası dosyaları mevcut
- [ ] GitHub repository oluşturuldu
- [ ] GitHub Secrets'a TMDB_API_KEY eklendi
- [ ] Git remote doğru yapılandırıldı
- [ ] Tüm dosyalar commit edildi

Push yaptıktan sonra kontrol edin:

- [ ] GitHub Actions workflow başarıyla çalıştı
- [ ] GitHub Pages aktif ve erişilebilir
- [ ] Gizlilik politikası sayfaları doğru görünüyor
- [ ] Repository'deki dosyalar doğru görünüyor

---

## 🆘 Sorun Giderme

### Push Hatası:

**"remote: Permission denied"**
- GitHub Authentication token'ınızı kontrol edin
- Personal Access Token kullanmayı deneyin

**"fatal: not a git repository"**
- `git init` komutunu çalıştırdığınızdan emin olun

### GitHub Actions Hatası:

**"TMDB_API_KEY bulunamadı"**
- GitHub Secrets'da TMDB_API_KEY'in doğru eklendiğini kontrol edin
- Secret adının tam olarak `TMDB_API_KEY` olduğundan emin olun (büyük/küçük harf duyarlı)

### GitHub Pages Hatası:

**"404 Not Found"**
- GitHub Pages ayarlarında branch ve folder'ın doğru seçildiğini kontrol edin
- Birkaç dakika bekleyin (ilk yayınlama zaman alabilir)
- `docs/` klasörünün repository root'unda olduğunu kontrol edin

**Sayfa görünmüyor**
- GitHub Pages'in aktif olduğunu Settings > Pages'den kontrol edin
- Tarayıcı cache'ini temizleyin

---

## 📚 Ek Kaynaklar

- [GitHub Pages Dokümantasyonu](https://docs.github.com/en/pages)
- [GitHub Actions Dokümantasyonu](https://docs.github.com/en/actions)
- [GitHub Secrets Dokümantasyonu](https://docs.github.com/en/actions/security-guides/encrypted-secrets)
- [Personal Access Token Oluşturma](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/creating-a-personal-access-token)

---

**Hazır olduğunuzda, yukarıdaki adımları sırayla takip ederek projenizi GitHub'a yayınlayabilirsiniz! 🚀**

