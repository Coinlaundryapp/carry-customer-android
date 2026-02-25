# CarryWebViewShell ProGuard Rules

# Keep JavaScript interface methods
-keepclassmembers class com.carrylabs.carry.webviewshell.bridge.CarryBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep BridgeResult for JSON serialization
-keep class com.carrylabs.carry.webviewshell.bridge.BridgeResult { *; }

# Preserve line number information for debugging stack traces
-keepattributes SourceFile,LineNumberTable

# Hide the original source file name
-renamesourcefileattribute SourceFile

# Firebase
-keep class com.google.firebase.** { *; }
