# ProGuard rules for NeoLauncher
-keep class com.neolauncher.** { *; }
-keepclassmembers class * extends android.app.Activity {
    public void *(android.view.View);
}
