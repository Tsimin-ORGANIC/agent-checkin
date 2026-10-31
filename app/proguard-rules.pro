# Keep JavaScript interface methods used by the Qoder login WebView.
-keepclassmembers class com.agentcheckin.android.ui.QoderJsBridge {
    @android.webkit.JavascriptInterface <methods>;
}
