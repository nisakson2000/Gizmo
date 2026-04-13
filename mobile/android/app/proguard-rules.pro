# Gizmo Android App — ProGuard/R8 Rules

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Coil
-keep class coil3.** { *; }
-dontwarn coil3.**

# Compose — R8 handles most of this via the Compose compiler, but keep runtime
-keep class androidx.compose.** { *; }

# multiplatform-markdown-renderer
-keep class com.mikepenz.markdown.** { *; }
-keep class org.intellij.markdown.** { *; }

# org.json (built into Android, but keep for clarity)
-keep class org.json.** { *; }

# Data classes used for JSON parsing — keep all fields
-keep class ai.gizmo.app.model.** { *; }
-keep class ai.gizmo.app.network.VideoUploadResult { *; }

# Keep Kotlin metadata for reflection
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# Suppress warnings for missing optional deps
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
