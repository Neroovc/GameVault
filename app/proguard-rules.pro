# GameVault ProGuard Rules

# Keep JSoup
-keep class org.jsoup.** { *; }

# Keep Room entities
-keep class com.gamevault.app.data.local.entity.** { *; }

# Keep Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
