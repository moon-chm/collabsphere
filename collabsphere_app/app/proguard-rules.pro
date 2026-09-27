# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Preserve line number information for debugging stack traces on Play Console.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Kotlin Serialization ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,allowobfuscation,allowshrinking class kotlinx.serialization.internal.**
-keep,allowobfuscation,allowshrinking class * extends kotlinx.serialization.internal.GeneratedSerializer

# Keep all data classes used in serialization (assuming they are in data/model packages)
# Note: In an ideal world we keep only @Serializable classes, this is a catch-all if you don't have them organized.
-keep @kotlinx.serialization.Serializable class * {
    <fields>;
    <init>(...);
}

# --- Ktor Client ---
-keep class io.ktor.** { *; }
-dontwarn java.lang.management.**
-dontwarn io.ktor.**

# --- Coroutines ---
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler {}
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory {}

# --- Koin ---
-keep class org.koin.** { *; }

# --- Firebase ---
-keep class com.google.firebase.** { *; }
