# ── App classes ───────────────────────────────────────────────────────────────
# Keep everything in the app package — safe catch-all for a small app.
# Prevents R8 renaming the Application, ViewModels, or any other class
# that the Android framework or Compose instantiates by name.
-keep class com.example.metrognome.** { *; }

# ── Kotlin ────────────────────────────────────────────────────────────────────
# Annotations are required by Compose, coroutines, and the Kotlin reflection
# used internally by Jetpack libraries.
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeInvisibleAnnotations
# Keep line numbers so crash reports are readable
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# ── Kotlin Coroutines ─────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
# Atomics used by coroutines internals
-keepclassmembernames class kotlinx.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# ── Google Mobile Ads (AdMob) ─────────────────────────────────────────────────
# The AAR ships its own consumer rules, but these ensure nothing slips through.
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.android.gms.internal.ads.** { *; }
-keep class com.google.ads.** { *; }
-keep class com.google.android.gms.common.** { *; }
-dontwarn com.google.android.gms.**

# ── Jetpack Compose ───────────────────────────────────────────────────────────
# Compose libraries ship their own consumer-rules.pro, so nothing extra needed.
# Suppress spurious warnings from the toolchain.
-dontwarn androidx.compose.**

# ── Jetpack ViewModel ─────────────────────────────────────────────────────────
# Keep ViewModel subclass constructors so the factory can instantiate them.
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
