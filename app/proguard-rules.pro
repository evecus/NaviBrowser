-keep class com.navibrowser.** { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class androidx.webkit.** { *; }
-keep class com.bumptech.glide.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
