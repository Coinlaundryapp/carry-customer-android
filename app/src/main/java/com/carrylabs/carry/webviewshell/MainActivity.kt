package com.carrylabs.carry.webviewshell

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import com.carrylabs.carry.webviewshell.bridge.BridgeResult
import com.carrylabs.carry.webviewshell.bridge.CarryBridge
import com.carrylabs.carry.webviewshell.bridge.NativeCallDispatcher
import com.carrylabs.carry.webviewshell.bridge.WebViewEventDispatcher
import com.carrylabs.carry.webviewshell.bridge.WebViewEventDispatcherRegistry
import com.carrylabs.carry.webviewshell.bridge.handlers.BiometricRequestHandler
import com.carrylabs.carry.webviewshell.bridge.handlers.LocationRequestHandler
import com.carrylabs.carry.webviewshell.bridge.handlers.LoginRequestHandler
import com.carrylabs.carry.webviewshell.bridge.handlers.MediaRequestHandler
import com.carrylabs.carry.webviewshell.databinding.ActivityMainBinding
import com.carrylabs.carry.webviewshell.permission.PermissionHandler
import com.carrylabs.carry.webviewshell.ui.ErrorView
import com.carrylabs.carry.webviewshell.ui.SplashLoadingView
import com.carrylabs.carry.webviewshell.util.BiometricHelper
import com.carrylabs.carry.webviewshell.util.NetworkUtils
import com.carrylabs.carry.webviewshell.webview.CarryWebChromeClient
import com.carrylabs.carry.webviewshell.webview.CarryWebViewClient
import com.carrylabs.carry.webviewshell.webview.WebViewSetup
import org.json.JSONObject

class MainActivity : AppCompatActivity(), WebViewEventDispatcher {

    private lateinit var binding: ActivityMainBinding
    private lateinit var dispatcher: NativeCallDispatcher
    private lateinit var splashView: SplashLoadingView
    private lateinit var errorView: ErrorView
    private lateinit var networkUtils: NetworkUtils
    private lateinit var permissionHandler: PermissionHandler

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // ── Handlers (Activity 프로퍼티로 생성 — registerForActivityResult 보장) ──

    private val mediaHandler by lazy { MediaRequestHandler(this, permissionHandler) }
    private lateinit var biometricHandler: BiometricRequestHandler
    private lateinit var locationHandler: LocationRequestHandler
    private lateinit var loginHandler: LoginRequestHandler

    // ── Lifecycle ────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WebViewEventDispatcherRegistry.register(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        permissionHandler = PermissionHandler(this)
        biometricHandler = BiometricRequestHandler(BiometricHelper(this))
        locationHandler = LocationRequestHandler(this, permissionHandler)
        loginHandler = LoginRequestHandler(this)
        networkUtils = NetworkUtils(this)

        setupWebView()
        setupSplash()
        setupErrorView()
        setupBackNavigation()
        setupSwipeRefresh()
        setupNetworkListener()

        requestNotificationPermissionOnStartup()

        if (savedInstanceState == null) {
            handleIntent(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::dispatcher.isInitialized) {
            dispatcher.dispatchAppResume()
        }
        dispatchNativeEvent("appResumed", "{}")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onDestroy() {
        WebViewEventDispatcherRegistry.unregister(this)
        networkCallback?.let { networkUtils.unregisterNetworkCallback(it) }
        binding.webView.destroy()
        super.onDestroy()
    }

    // ── WebView Setup ────────────────────────────────────────────────

    private fun setupWebView() {
        WebViewSetup.configure(binding.webView)

        dispatcher = NativeCallDispatcher(binding.webView)
        mediaHandler.initialize(dispatcher)

        val bridge = CarryBridge(this) { method, requestId, args ->
            runOnUiThread { handleAsyncBridgeRequest(method, requestId, args) }
        }
        binding.webView.addJavascriptInterface(bridge, "AndroidBridge")
        binding.webView.addJavascriptInterface(bridge, "CarryNative")

        binding.webView.webViewClient = CarryWebViewClient(
            onPageStarted = { binding.progressBar.visibility = View.VISIBLE },
            onPageFinished = {
                binding.progressBar.visibility = View.GONE
                splashView.dismiss()
                binding.swipeRefresh.isRefreshing = false
            },
            onError = { errorType ->
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
                errorView.show(errorType)
            }
        )

        binding.webView.webChromeClient = CarryWebChromeClient(
            onProgressChanged = { progress ->
                binding.progressBar.progress = progress
                if (progress >= 100) binding.progressBar.visibility = View.GONE
            },
            onFileChooser = { callback, params -> mediaHandler.handleFileChooser(callback, params) },
            onGeolocationPermission = { origin, callback ->
                locationHandler.handleGeolocationPermission(origin, callback)
            }
        )
    }

    // ── Splash & Error ───────────────────────────────────────────────

    private fun setupSplash() {
        splashView = SplashLoadingView(binding.splashView.root)
        splashView.show()
    }

    private fun setupErrorView() {
        errorView = ErrorView(binding.errorView.root) { loadBaseUrl() }
    }

    // ── Back Navigation ──────────────────────────────────────────────

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (errorView.isShowing()) {
                    errorView.hide()
                    return
                }
                dispatcher.checkAndDispatchBackPressed { handled ->
                    if (!handled) {
                        if (binding.webView.canGoBack()) {
                            binding.webView.goBack()
                        } else {
                            isEnabled = false
                            onBackPressedDispatcher.onBackPressed()
                        }
                    }
                }
            }
        })
    }

    // ── Swipe Refresh ────────────────────────────────────────────────

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            if (errorView.isShowing()) errorView.hide()
            binding.webView.reload()
        }
    }

    // ── Network Listener ─────────────────────────────────────────────

    private fun setupNetworkListener() {
        networkCallback = networkUtils.registerNetworkCallback { isConnected ->
            runOnUiThread {
                dispatchNativeEvent("connectivityChanged", """{"isConnected":$isConnected}""")
            }
        }
    }

    // ── Intent / Deep Link ───────────────────────────────────────────

    private fun handleIntent(intent: Intent?) {
        if (intent == null) { loadBaseUrl(); return }

        val deepLink = intent.getStringExtra("deepLink")
        if (deepLink != null) {
            binding.webView.loadUrl(deepLink)
            dispatchNativeEvent("deepLink", """{"url":"$deepLink"}""")
            return
        }

        val data = intent.data
        if (data != null) {
            if (data.scheme == "carry" && data.host == "oauth" && data.path == "/kakao") {
                data.getQueryParameter("code")?.let {
                    loginHandler.handleKakaoLoginResult(it, dispatcher)
                    return
                }
            }
            val url = resolveDeepLink(data)
            binding.webView.loadUrl(url)
            dispatchNativeEvent("deepLink", """{"url":"$url"}""")
            return
        }

        loadBaseUrl()
    }

    private fun resolveDeepLink(uri: Uri): String {
        if (uri.scheme == "carry") return BuildConfig.BASE_URL + (uri.path ?: "")
        return BuildConfig.BASE_URL + (uri.path ?: "")
    }

    private fun loadBaseUrl() {
        binding.webView.loadUrl(BuildConfig.BASE_URL)
    }

    // ── Notification Permission (startup) ────────────────────────────

    private fun requestNotificationPermissionOnStartup() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionHandler.requestSingle(Manifest.permission.POST_NOTIFICATIONS) { _ -> }
            }
        }
    }

    // ── Async Bridge Request Routing ─────────────────────────────────

    private fun handleAsyncBridgeRequest(method: String, requestId: String, args: JSONObject) {
        when (method) {
            "requestBiometric" -> biometricHandler.handle(requestId, args, dispatcher)
            "requestLocation" -> locationHandler.handle(requestId, dispatcher)
            "requestCamera" -> mediaHandler.handleCamera(requestId)
            "openGallery" -> mediaHandler.handleGallery(requestId)
            "requestLogin" -> loginHandler.handle(requestId, dispatcher)
            "requestNotificationPermission" -> handleNotificationPermission(requestId)
            "openExternalBrowser" -> handleOpenExternalBrowser(requestId, args)
            "closeApp" -> handleCloseApp(requestId)
        }
    }

    // ── Simple handlers (추출 불필요) ────────────────────────────────

    private fun handleNotificationPermission(requestId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED
            ) {
                dispatcher.sendCallback(
                    BridgeResult(requestId, true, JSONObject().put("granted", true))
                )
                return
            }
            permissionHandler.requestSingle(Manifest.permission.POST_NOTIFICATIONS) { granted ->
                dispatcher.sendCallback(
                    BridgeResult(requestId, true, JSONObject().put("granted", granted))
                )
            }
        } else {
            dispatcher.sendCallback(
                BridgeResult(requestId, true, JSONObject().put("granted", true))
            )
        }
    }

    private fun handleOpenExternalBrowser(requestId: String, args: JSONObject) {
        val url = args.optString("url", "")
        if (url.isEmpty()) {
            dispatcher.sendCallback(BridgeResult(requestId, false, error = "URL is empty"))
            return
        }
        try {
            CustomTabsIntent.Builder().build().launchUrl(this, Uri.parse(url))
            dispatcher.sendCallback(BridgeResult(requestId, true))
        } catch (e: Exception) {
            dispatcher.sendCallback(
                BridgeResult(requestId, false, error = "Failed to open browser: ${e.message}")
            )
        }
    }

    private fun handleCloseApp(requestId: String) {
        dispatcher.sendCallback(BridgeResult(requestId, true))
        finish()
    }

    // ── Native→JS Event Dispatch (public for FCM service) ───────────

    override fun dispatchNativeEvent(eventName: String, dataJson: String): Boolean {
        if (!::dispatcher.isInitialized) return false
        dispatcher.sendEvent(eventName, dataJson)
        return true
    }

    override fun dispatchPushNotification(dataJson: String): Boolean {
        if (!::dispatcher.isInitialized) return false
        dispatcher.dispatchPushNotification(dataJson)
        return true
    }
}
