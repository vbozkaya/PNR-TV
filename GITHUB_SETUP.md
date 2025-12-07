# GitHub Repository Kurulum Rehberi

Bu rehber, projenizi GitHub'a bağlamak için gerekli adımları açıklar.

## Adım 1: Git Repository Oluştur

Proje klasöründe terminal açın ve şu komutları çalıştırın:

```powershell
# Git repository başlat
git init

# Tüm dosyaları staging area'ya ekle
git add .

# İlk commit'i yap
git commit -m "Initial commit: PNR TV Android TV Application"
```

## Adım 2: GitHub'da Repository Oluştur

1. GitHub'a gidin: https://github.com
2. Sağ üst köşede **+** butonuna tıklayın
3. **New repository** seçin
4. Repository bilgilerini girin:
   - **Repository name**: `PNR-TV` (veya istediğiniz isim)
   - **Description**: "PNR TV - Android TV IPTV Application"
   - **Visibility**: Public veya Private (istediğinizi seçin)
   - **⚠️ ÖNEMLİ**: "Initialize this repository with a README" seçeneğini **İŞARETLEMEYİN** (zaten dosyalarımız var)
5. **Create repository** butonuna tıklayın

## Adım 3: GitHub Repository'ye Bağla

GitHub'da repository oluşturduktan sonra, size bir URL verilecek. Şu komutları çalıştırın:

```powershell
# GitHub repository URL'ini ekle (kendi URL'inizi kullanın)
git remote add origin https://github.com/vbozkaya/PNR-TV.git

# Veya SSH kullanıyorsanız:
# git remote add origin git@github.com:vbozkaya/PNR-TV.git

# Remote'un doğru eklendiğini kontrol et
git remote -v
```

## Adım 4: Dosyaları GitHub'a Gönder

```powershell
# Main branch'i oluştur (eğer yoksa)
git branch -M main

# GitHub'a push et
git push -u origin main
```

## Adım 5: GitHub Secret'ları Ekle

CI/CD pipeline'ının çalışması için GitHub Secrets eklemeniz gerekir:

1. GitHub repository > **Settings** > **Secrets and variables** > **Actions**
2. **New repository secret** butonuna tıklayın
3. Secret ekleyin:
   - **Name**: `TMDB_API_KEY`
   - **Secret**: `local.properties` dosyanızdaki TMDB_API_KEY değeri
4. **Add secret** butonuna tıklayın

## Sonraki Commit'ler İçin

Projede değişiklik yaptıktan sonra:

```powershell
# Değişiklikleri kontrol et
git status

# Değişiklikleri ekle
git add .

# Commit yap
git commit -m "Açıklayıcı commit mesajı"

# GitHub'a gönder
git push
```

## Sorun Giderme

### "fatal: not a git repository" hatası
- Proje klasöründe olduğunuzdan emin olun
- `git init` komutunu çalıştırın

### "remote origin already exists" hatası
- Mevcut remote'u kaldırın: `git remote remove origin`
- Tekrar ekleyin: `git remote add origin <URL>`

### "Permission denied" hatası
- GitHub token'ınızı kontrol edin
- SSH key'inizi GitHub'a eklediğinizden emin olun
- HTTPS kullanıyorsanız, token ile authentication yapın

### Push sırasında hata
- `git pull origin main --allow-unrelated-histories` komutunu deneyin
- Sonra tekrar `git push` yapın

## Güvenlik Notları

⚠️ **ÖNEMLİ:**
- `local.properties` dosyası `.gitignore`'da olduğu için commit edilmeyecek ✅
- `google-services.json` dosyası `.gitignore`'da olduğu için commit edilmeyecek ✅
- API key'ler ve secret'lar asla commit edilmemeli ✅
- GitHub Secrets kullanarak güvenli şekilde saklayın ✅

