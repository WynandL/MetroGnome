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
-keepattributes Exceptions
-keepattributes InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeInvisibleAnnotations
# Keep line numbers so crash reports are readable
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# ── Kotlin Coroutines ─────────────────────────────────────────────────────────
# These two classes are loaded at runtime via ServiceLoader — R8 cannot trace
# the reference statically and will strip them unless explicitly kept.
# Removing either causes an immediate crash: "Module with the Main dispatcher
# is missing. Add dependency providing the Main dispatcher."
-keep class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keep class kotlinx.coroutines.android.AndroidDispatcherFactory { *; }
-keep class kotlinx.coroutines.CoroutineExceptionHandler { *; }
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

# ── Firebase ─────────────────────────────────────────────────────────────────
# Analytics and Crashlytics ship their own consumer rules, but this catches
# anything the Crashlytics plugin needs to upload the mapping file correctly.
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# ── WorkManager + Room (transitive via Firebase) ──────────────────────────────
# Firebase pulls in WorkManager, which uses Room internally. Room accesses its
# generated *_Impl class via reflection — R8 cannot trace the reference and
# strips the class, causing a crash at startup before any app code runs:
#   Room.getGeneratedImplementation() → WorkDatabase_Impl not found
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class androidx.work.impl.** { *; }
-dontwarn androidx.work.**

# ── Jetpack ViewModel ─────────────────────────────────────────────────────────
# Keep ViewModel subclass constructors so the factory can instantiate them.
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
