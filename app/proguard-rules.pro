# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# ============================================
# Retrofit
# ============================================
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keepattributes Exceptions
# Generic type bilgilerini koru - Moshi reflection için gerekli
-keepattributes Signature,*Annotation*

# Retrofit interfaces - API service'ler için
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Retrofit classes
-keep class retrofit2.Retrofit { *; }
-keep class retrofit2.Retrofit$* { *; }
-keep class retrofit2.Retrofit$Builder { *; }

# Retrofit Converter
-keep class retrofit2.converter.moshi.MoshiConverterFactory { *; }

# API Services - NetworkModule'de kullanılıyor
-keep interface com.pnr.tv.network.ApiService { *; }
-keep interface com.pnr.tv.network.TmdbApiService { *; }

-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# ============================================
# OkHttp
# ============================================
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# OkHttp Interceptors - Reflection ile kullanılıyor
-keep class okhttp3.Interceptor { *; }
-keep class okhttp3.Interceptor$* { *; }
-keep class * implements okhttp3.Interceptor { *; }

# OkHttp CertificatePinner - Certificate Pinning için gerekli
-keep class okhttp3.CertificatePinner { *; }
-keep class okhttp3.CertificatePinner$* { *; }
-keep class okhttp3.CertificatePinner$Builder { *; }

# OkHttpClient - NetworkModule'de kullanılıyor
-keep class okhttp3.OkHttpClient { *; }
-keep class okhttp3.OkHttpClient$* { *; }
-keep class okhttp3.OkHttpClient$Builder { *; }

# HttpLoggingInterceptor
-keep class okhttp3.logging.HttpLoggingInterceptor { *; }
-keep class okhttp3.logging.HttpLoggingInterceptor$* { *; }

# Custom Interceptors
-keep class com.pnr.tv.network.RateLimiterInterceptor { *; }
-keep class com.pnr.tv.network.RateLimiterInterceptor$* { *; }

# ============================================
# Moshi
# ============================================
-keep class com.squareup.moshi.** { *; }
-keep @com.squareup.moshi.JsonQualifier interface *
-keepclassmembers class * {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}

# Moshi Kotlin Reflection için - Constructor parametre isimlerini koru
-keepclassmembers,allowobfuscation class * {
    @com.squareup.moshi.Json <fields>;
}

# Moshi reflection için - Kotlin data class constructor parametrelerini koru
-keepclassmembers,allowobfuscation class com.pnr.tv.network.dto.** {
    <init>(...);
}

# Moshi Kotlin Code Gen için: DTO sınıflarını koru
# @JsonClass anotasyonlu sınıfları koru (TMDB DTO'ları için)
-keep @com.squareup.moshi.JsonClass class * {
    <fields>;
    <init>(...);
}

# IPTV DTO'ları için - Reflection kullanıldığı için constructor parametrelerini koru
# Tüm DTO sınıflarını ve constructor parametrelerini koru
-keep class com.pnr.tv.network.dto.** {
    <fields>;
    <init>(...);
}

# Moshi reflection için - Constructor parametre isimlerini koru
-keepclassmembers class com.pnr.tv.network.dto.** {
    <init>(...);
}

# @Json anotasyonlu field'ları içeren sınıfları koru
-keepclassmembers class com.pnr.tv.network.dto.** {
    @com.squareup.moshi.Json <fields>;
}

# Nested Map/List yapıları için - EpisodeDto, SeasonDto, vb. için özel koruma
-keep class com.pnr.tv.network.dto.EpisodeDto { *; }
-keep class com.pnr.tv.network.dto.SeasonDto { *; }
-keep class com.pnr.tv.network.dto.EpisodeInfoDto { *; }
-keep class com.pnr.tv.network.dto.SeriesInfoDto { *; }
-keep class com.pnr.tv.network.dto.SeriesDetailInfoDto { *; }

# Map ve List generic type'ları için - Moshi reflection için gerekli
# SeriesInfoDto'nun episodes field'ı Map<String, List<EpisodeDto>> olduğu için
# generic type bilgilerini korumak kritik
-keepclassmembers class com.pnr.tv.network.dto.SeriesInfoDto {
    <fields>;
    <init>(...);
}

# Generated adapter'lar için sınıf isimlerini de koru
-keepnames class *$$JsonAdapter

# Moshi Kotlin Reflection için - Constructor parametre isimlerini koru
-keepclassmembers,allowobfuscation class com.pnr.tv.network.dto.** {
    <init>(...);
}

# ============================================
# Room Database
# ============================================
-keep class * extends androidx.room.RoomDatabase
# Sadece @Entity anotasyonlu sınıfları koru (genel paket kuralı yerine)
-keep @androidx.room.Entity class * {
    <fields>;
    <init>(...);
}
-keep @androidx.room.Dao class *
-dontwarn androidx.room.paging.**

# ============================================
# Hilt (Dagger)
# ============================================
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-dontwarn dagger.hilt.**

# Dagger/Hilt Qualifiers - NetworkModule'de kullanılıyor
-keep @javax.inject.Qualifier interface * { *; }
-keep @javax.inject.Qualifier @interface * { *; }
-keep class com.pnr.tv.di.IptvRetrofit { *; }
-keep class com.pnr.tv.di.TmdbRetrofit { *; }

# NetworkModule - Dependency Injection için gerekli
-keep class com.pnr.tv.di.NetworkModule { *; }
-keep class com.pnr.tv.di.NetworkModule$* { *; }

# ============================================
# Hilt GeneratedInjector - CRITICAL: NoClassDefFoundError önleme
# ============================================
# Application sınıfını koru
-keep public class * extends android.app.Application

# Hilt tarafından oluşturulan Application wrapper sınıfını koru
-keep public class com.pnr.tv.core.base.Hilt_PnrTvApplication { *; }

# GeneratedInjector interface'ini koru - Hilt dependency injection için kritik
-keep interface com.pnr.tv.core.base.PnrTvApplication_GeneratedInjector { *; }

# Tüm Hilt generated sınıflarını koru (güvenlik için)
-keep class * extends dagger.hilt.android.internal.managers.ComponentSupplier { *; }
-keep class * implements dagger.hilt.internal.GeneratedComponent { *; }
-keep class * implements dagger.hilt.internal.GeneratedComponentManager { *; }
-keep class dagger.hilt.internal.GeneratedComponentManagerHolder { *; }

# Hilt EntryPoint'leri koru
-keep @dagger.hilt.android.scopes.ActivityRetainedScoped class * { *; }
-keep @dagger.hilt.android.scopes.ActivityScoped class * { *; }
-keep @dagger.hilt.android.scopes.FragmentScoped class * { *; }
-keep @dagger.hilt.android.scopes.ServiceScoped class * { *; }
-keep @dagger.hilt.android.scopes.ViewScoped class * { *; }
-keep @dagger.hilt.android.scopes.ViewModelScoped class * { *; }

# Hilt Module'lerini koru
-keep @dagger.hilt.InstallIn class * { *; }
-keep @dagger.Module class * { *; }

# ============================================
# Parcelable
# ============================================
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# ============================================
# Model Classes (Interfaces, Enums, Sealed Classes)
# ============================================
# Model paketindeki interface, enum ve sealed class'ları koru
# (Reflection ile kullanıldıkları için gerekli)
-keep interface com.pnr.tv.model.** { *; }
-keep enum com.pnr.tv.model.** { *; }
-keep class com.pnr.tv.model.** { *; }
# Not: Model paketi sadece interface, enum ve sealed class içerdiği için
# genel class kuralı gerekli değil, ancak sealed class'lar için class kuralı gerekli

# ============================================
# Kotlin
# ============================================
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# ============================================
# Coil (Image Loading)
# ============================================
-keep class coil.** { *; }
-dontwarn coil.**

# ============================================
# ExoPlayer (Media3)
# ============================================
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ============================================
# Timber (Logging)
# ============================================
-keep class timber.log.** { *; }
-dontwarn timber.log.**

# ============================================
# Firebase Crashlytics
# ============================================
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception
-keep class com.google.firebase.crashlytics.** { *; }
-dontwarn com.google.firebase.crashlytics.**

# ============================================
# Keep Application Class
# ============================================
-keep class com.pnr.tv.PnrTvApplication { *; }

# ============================================
# Keep ViewModels
# ============================================
-keep class * extends androidx.lifecycle.ViewModel { *; }

# ============================================
# Keep Activities and Fragments
# ============================================
-keep class * extends androidx.fragment.app.Fragment { *; }
-keep class * extends androidx.appcompat.app.AppCompatActivity { *; }

# ============================================
# Keep BuildConfig
# ============================================
-keep class com.pnr.tv.BuildConfig { *; }

# ============================================
# Certificate Pinning
# ============================================
-keep class com.pnr.tv.security.CertificatePinningConfig { *; }
-keep class com.pnr.tv.security.CertificatePinningConfig$* { *; }

# ============================================
# Keep Constants (API Keys)
# ============================================
-keep class com.pnr.tv.Constants { *; }
-keep class com.pnr.tv.Constants$* { *; }

# ============================================
# AdMob (Google Mobile Ads SDK)
# ============================================
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# AdMob Interstitial Ad için
-keep class com.google.android.gms.ads.interstitial.** { *; }

# AdMob Banner Ad için
-keep class com.google.android.gms.ads.AdView { *; }
-keep class com.google.android.gms.ads.BaseAdView { *; }

# AdMob Mediation için (opsiyonel)
-keep class com.google.android.gms.ads.mediation.** { *; }
-dontwarn com.google.android.gms.ads.mediation.**

# AdMob Request Configuration için
-keep class com.google.android.gms.ads.RequestConfiguration { *; }
-keep class com.google.android.gms.ads.RequestConfiguration$* { *; }

# AdMob AdRequest için
-keep class com.google.android.gms.ads.AdRequest { *; }
-keep class com.google.android.gms.ads.AdRequest$* { *; }

# AdMob FullScreenContentCallback için
-keep class com.google.android.gms.ads.FullScreenContentCallback { *; }

# AdMob AdListener için
-keep class com.google.android.gms.ads.AdListener { *; }

# AdMob LoadAdError için
-keep class com.google.android.gms.ads.LoadAdError { *; }

# AdMob AdError için
-keep class com.google.android.gms.ads.AdError { *; }

# AdMob MobileAds için
-keep class com.google.android.gms.ads.MobileAds { *; }
-keep class com.google.android.gms.ads.MobileAds$* { *; }

# ============================================
# LeakCanary - TAMAMEN KALDIRILDI
# ============================================
# LeakCanary dependency kaldırıldı, ProGuard kurallarına gerek yok