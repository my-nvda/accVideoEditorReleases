# Keep all app classes, activities, fragments, and models
-keep class com.example.accessiblevideoeditor.** { *; }
-keepclassmembers class com.example.accessiblevideoeditor.** { *; }

# Keep Navigation Component classes
-keep class androidx.navigation.** { *; }
-keepclassmembers class androidx.navigation.** { *; }

# Keep ML Kit Selfie Segmentation
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.** { *; }

# Keep FFmpegKit
-keep class com.arthenica.ffmpegkit.** { *; }

# Keep ExoPlayer / Media3
-keep class androidx.media3.** { *; }
