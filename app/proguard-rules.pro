# Keep Sunmi printer SDK
-keep class com.sunmi.** { *; }
-keep interface com.sunmi.** { *; }

# Keep JS interface methods
-keepclassmembers class com.kapdatalabs.recettespro.MainActivity$SunmiPrintBridge {
    @android.webkit.JavascriptInterface <methods>;
}
