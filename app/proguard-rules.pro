# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# ============================================
# Retrofit
# ============================================
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
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

# ============================================
# Moshi
# ============================================
-keep class com.squareup.moshi.** { *; }
-keep @com.squareup.moshi.JsonQualifier interface *
-keepclassmembers class * {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}

# Moshi Kotlin Code Gen için: DTO sınıflarını koru
# @JsonClass anotasyonlu sınıfları koru (TMDB DTO'ları için)
-keep @com.squareup.moshi.JsonClass class * {
    <fields>;
    <init>(...);
}
# @Json anotasyonlu field'ları içeren sınıfları koru (IPTV DTO'ları için)
-keepclassmembers class com.pnr.tv.network.dto.** {
    @com.squareup.moshi.Json <fields>;
}
# Generated adapter'lar için sınıf isimlerini de koru
-keepnames class *$$JsonAdapter

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
# Keep Constants (API Keys)
# ============================================
-keep class com.pnr.tv.Constants { *; }
-keep class com.pnr.tv.Constants$* { *; }

# ============================================
# LeakCanary - TAMAMEN KALDIRILDI
# ============================================
# LeakCanary dependency kaldırıldı, ProGuard kurallarına gerek yok