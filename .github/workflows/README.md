# CI/CD Pipeline

Bu dizin GitHub Actions CI/CD workflow dosyalarını içerir.

## Workflow'lar

### `ci.yml`
Ana CI/CD pipeline workflow'u. Şu işlemleri gerçekleştirir:

1. **Test**: Unit testleri çalıştırır
2. **Lint**: ktlint ile kod analizi yapar
3. **Build**: Debug ve Release APK'ları derler

## Gereksinimler

### GitHub Secrets
Aşağıdaki secret'ları GitHub repository ayarlarına eklemeniz gerekir:

- `TMDB_API_KEY`: TMDB API anahtarı (local.properties'te kullanılan)
- `ANDROID_HOME`: (Opsiyonel) Android SDK yolu

### Workflow Tetikleyicileri
- `push` event: `main` ve `develop` branch'lerine push yapıldığında
- `pull_request` event: `main` ve `develop` branch'lerine PR açıldığında

## Artifact'lar

Workflow sonunda şu artifact'lar oluşturulur:
- **test-report**: Test sonuçları HTML raporu
- **apk-files**: Derlenmiş APK dosyaları (debug ve release)

## Kullanım

Workflow otomatik olarak çalışır. Manuel olarak çalıştırmak için:
1. GitHub repository'ye git
2. Actions sekmesine tıkla
3. "CI/CD Pipeline" workflow'unu seç
4. "Run workflow" butonuna tıkla

