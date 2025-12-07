# GitHub Secrets Kurulum Rehberi

Bu rehber, CI/CD pipeline'ının çalışması için gerekli GitHub Secrets'ların nasıl ekleneceğini açıklar.

## Gerekli Secret'lar

CI/CD pipeline'ının çalışması için aşağıdaki secret'ları eklemeniz gerekir:

### 1. TMDB_API_KEY
- **Açıklama**: TMDB API anahtarı (local.properties'te kullanılan)
- **Nasıl Bulunur**: 
  - `local.properties` dosyanızdaki `TMDB_API_KEY` değerini kopyalayın
  - Veya [TMDB API](https://www.themoviedb.org/settings/api) sayfasından alın

### 2. ANDROID_HOME (Opsiyonel)
- **Açıklama**: Android SDK yolu
- **Varsayılan**: Workflow otomatik olarak bulur, genellikle eklemeye gerek yoktur

## Secret Ekleme Adımları

### Yöntem 1: GitHub Web Arayüzü (Önerilen)

1. GitHub repository'nize gidin
2. **Settings** sekmesine tıklayın
3. Sol menüden **Secrets and variables** > **Actions** seçin
4. **New repository secret** butonuna tıklayın
5. Secret adını girin (örn: `TMDB_API_KEY`)
6. Secret değerini girin
7. **Add secret** butonuna tıklayın

### Yöntem 2: GitHub CLI (gh) ile

Eğer GitHub CLI kuruluysa:

```bash
# TMDB_API_KEY ekle
gh secret set TMDB_API_KEY --repo <kullanıcı-adı>/<repo-adı>

# Değeri girmeniz istenecek
```

### Yöntem 3: GitHub API ile (Token Kullanarak)

GitHub token'ınızı kullanarak API ile secret ekleyebilirsiniz:

```bash
# Token'ı environment variable olarak ayarlayın
export GITHUB_TOKEN="your_github_token_here"

# Repository bilgilerinizi ayarlayın
export REPO_OWNER="vbozkaya"
export REPO_NAME="PNR-TV"  # Repository adınız

# TMDB_API_KEY secret'ını ekle
curl -X PUT \
  -H "Authorization: token $GITHUB_TOKEN" \
  -H "Accept: application/vnd.github.v3+json" \
  https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/actions/secrets/TMDB_API_KEY \
  -d '{
    "encrypted_value": "'$(echo -n "your_tmdb_api_key" | base64)'",
    "key_id": "'$(curl -s -H "Authorization: token $GITHUB_TOKEN" \
      https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/actions/secrets/public-key | jq -r .key_id)'"
  }'
```

**Not**: Bu yöntem daha karmaşık. GitHub'ın public key'ini alıp, secret'ı encrypt etmeniz gerekir.

## Secret'ları Kontrol Etme

Secret'ların doğru eklendiğini kontrol etmek için:

1. GitHub repository > **Settings** > **Secrets and variables** > **Actions**
2. Eklediğiniz secret'ları görebilirsiniz
3. Secret değerleri güvenlik nedeniyle gösterilmez (sadece adları görünür)

## Workflow'da Kullanım

Secret'lar workflow dosyasında (`ci.yml`) şu şekilde kullanılır:

```yaml
${{ secrets.TMDB_API_KEY }}
${{ secrets.ANDROID_HOME }}
```

## Güvenlik Notları

⚠️ **ÖNEMLİ:**
- Secret'ları asla kod içinde hardcode etmeyin
- Secret'ları commit etmeyin
- Secret değerlerini paylaşmayın
- Secret'lar sadece GitHub Actions workflow'larında kullanılabilir

## Test Etme

Secret'ları ekledikten sonra:

1. Bir commit yapın ve push edin
2. GitHub repository > **Actions** sekmesine gidin
3. Workflow'un çalıştığını görebilirsiniz
4. Eğer secret eksikse, workflow hata verecektir

## Sorun Giderme

### Secret bulunamadı hatası
- Secret adının doğru yazıldığından emin olun (büyük/küçük harf duyarlı)
- Secret'ın repository'ye eklendiğini kontrol edin

### Workflow çalışmıyor
- Secret'ların doğru eklendiğini kontrol edin
- Workflow dosyasındaki secret referanslarını kontrol edin
- GitHub Actions log'larını inceleyin

