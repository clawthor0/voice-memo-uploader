# Keep OkHttp
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**

# Keep Gson
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**

# Keep our classes
-keep class com.example.voicememouploader.** { *; }

# Keep Kotlin metadata
-keepattributes *Annotation*
