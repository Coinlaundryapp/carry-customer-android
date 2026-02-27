# CarryWebViewShell ProGuard Rules

# Keep JavaScript interface methods
-keepclassmembers class com.carrylabs.carry.webviewshell.bridge.CarryBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep BridgeResult for JSON serialization
-keep class com.carrylabs.carry.webviewshell.bridge.BridgeResult { *; }

# Keep manifest-registered components
-keep public class com.carrylabs.carry.webviewshell.CarryApplication
-keep public class com.carrylabs.carry.webviewshell.fcm.CarryFirebaseMessagingService

# Keep bridge infrastructure
-keep interface com.carrylabs.carry.webviewshell.bridge.WebViewEventDispatcher { *; }
-keep class com.carrylabs.carry.webviewshell.bridge.WebViewEventDispatcherRegistry { *; }

# Keep handler classes (coroutine suspend functions rely on signatures)
-keep class com.carrylabs.carry.webviewshell.bridge.handlers.** { *; }

# Preserve line number information for debugging stack traces
-keepattributes SourceFile,LineNumberTable

# Hide the original source file name
-renamesourcefileattribute SourceFile

# Firebase
-keep class com.google.firebase.** { *; }

# Kakao SDK
-keep class com.kakao.sdk.** { *; }
-keep class com.kakao.util.** { *; }
-dontwarn com.kakao.sdk.**

# Google Play In-App Update
-keep class com.google.android.play.core.** { *; }
