# ==============================================================================
# Aero Bar — Hardened ProGuard / R8 Obfuscation & Anti-Reverse Engineering Rules
# ==============================================================================

# ------------------------------------------------------------------------------
# 1. Aggressive Obfuscation & Anti-Decompilation
# ------------------------------------------------------------------------------
# Flatten all classes into the root package to erase architecture and package names
-repackageclasses ''

# Allow R8 to widen access modifiers for maximum inlining and member renaming
-allowaccessmodification

# Prevent Windows/case-insensitive filesystem issues while maximizing name entropy
-dontusemixedcaseclassnames

# Strip all source file names, line numbers, and debug metadata from DEX bytecode
-renamesourcefileattribute ''
-keepattributes !SourceFile,!LineNumberTable,!LocalVariableTable,!LocalVariableTypeTable

# Strip developer debug and verbose logs from the compiled binary
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}

# ------------------------------------------------------------------------------
# 2. Android Core Lifecycle Entrypoints
# ------------------------------------------------------------------------------
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.accessibilityservice.AccessibilityService

# Keep native methods if any
-keepclasseswithmembernames class * {
    native <methods>;
}

# ------------------------------------------------------------------------------
# 3. Dagger / Hilt Dependency Injection
# ------------------------------------------------------------------------------
-dontwarn javax.annotation.**
-dontwarn dagger.hilt.android.internal.**

-keep class * extends dagger.hilt.internal.GeneratedComponentManager { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }
-keep @javax.inject.Inject class * { *; }
-keep @javax.inject.Singleton class * { *; }
-keep class androidx.hilt.navigation.compose.** { *; }

# ------------------------------------------------------------------------------
# 4. Kotlin Coroutines & Reflection Support
# ------------------------------------------------------------------------------
-dontwarn kotlinx.coroutines.**
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ------------------------------------------------------------------------------
# 5. Jetpack Compose
# ------------------------------------------------------------------------------
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
