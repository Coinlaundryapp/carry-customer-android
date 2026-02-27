package com.carrylabs.carry.webviewshell

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
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
import com.carrylabs.carry.webviewshell.util.InAppUpdateManager
import com.carrylabs.carry.webviewshell.util.NetworkUtils
import com.carrylabs.carry.webviewshell.webview.CarryWebChromeClient
import com.carrylabs.carry.webviewshell.webview.CarryWebViewClient
import com.carrylabs.carry.webviewshell.webview.WebViewSetup
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainActivity : AppCompatActivity(), WebViewEventDispatcher {

    private companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var dispatcher: NativeCallDispatcher
    private lateinit var splashView: SplashLoadingView
    private lateinit var errorView: ErrorView
    private lateinit var networkUtils: NetworkUtils
    private lateinit var permissionHandler: PermissionHandler

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private lateinit var inAppUpdateManager: InAppUpdateManager

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
        inAppUpdateManager = InAppUpdateManager(this)

        setupWebView()
        setupSplash()
        setupErrorView()
        setupBackNavigation()
        setupSwipeRefresh()
        setupNetworkListener()

        requestNotificationPermissionOnStartup()
        checkForAppUpdate()

        if (savedInstanceState == null) {
            handleIntent(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::dispatcher.isInitialized) {
            dispatcher.dispatchAppResume()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onDestroy() {
        WebViewEventDispatcherRegistry.unregister(this)
        locationHandler.cleanup()
        mediaHandler.cleanup()
        loginHandler.cleanup()
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
            handleAsyncBridgeRequest(method, requestId, args)
        }
        binding.webView.addJavascriptInterface(bridge, "AndroidBridge")
        binding.webView.addJavascriptInterface(bridge, "CarryNative")

        binding.webView.webViewClient = CarryWebViewClient(
            onPageStarted = {
                binding.progressBar.visibility = View.VISIBLE
                locationHandler.invalidateGeolocationCallback()
            },
            onPageFinished = {
                binding.progressBar.visibility = View.GONE
                splashView.dismiss()
                binding.swipeRefresh.isRefreshing = false
            },
            onError = { errorType ->
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
                errorView.show(errorType)
            },
            onRendererCrash = {
                Log.e(TAG, "WebView renderer crashed — recreating")
                recreate()
            }
        )

        binding.webView.webChromeClient = CarryWebChromeClient(
            onProgressChanged = { progress ->
                binding.progressBar.progress = progress
                if (progress >= 100) binding.progressBar.visibility = View.GONE
            },
            onFileChooser = { callback, params -> mediaHandler.handleFileChooser(callback, params) },
            onGeolocationPermission = { origin, callback ->
                lifecycleScope.launch {
                    locationHandler.handleGeolocationPermission(origin, callback)
                }
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
        networkCallback = networkUtils.registerNetworkCallback { status ->
            runOnUiThread {
                dispatchNativeEvent("connectivityChanged", status.toString())
            }
        }
    }

    // ── Intent / Deep Link ───────────────────────────────────────────

    private fun handleIntent(intent: Intent?) {
        if (intent == null) { loadBaseUrl(); return }

        val deepLink = intent.getStringExtra("deepLink")
        if (deepLink != null) {
            binding.webView.loadUrl(deepLink)
            dispatchNativeEvent("deepLink", JSONObject().put("url", deepLink).toString())
            return
        }

        val data = intent.data
        if (data != null) {
            val url = resolveDeepLink(data)
            binding.webView.loadUrl(url)
            dispatchNativeEvent("deepLink", JSONObject().put("url", url).toString())
            return
        }

        loadBaseUrl()
    }

    private fun resolveDeepLink(uri: Uri): String {
        if (uri.scheme == "carry") return BuildConfig.BASE_URL + (uri.path ?: "")
        return uri.toString()
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
                lifecycleScope.launch {
                    permissionHandler.requestSingle(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    // ── Async Bridge Request Routing ─────────────────────────────────

    private fun handleAsyncBridgeRequest(method: String, requestId: String, args: JSONObject) {
        lifecycleScope.launch {
            when (method) {
                CarryBridge.REQUEST_BIOMETRIC -> biometricHandler.handle(requestId, args, dispatcher)
                CarryBridge.REQUEST_LOCATION -> locationHandler.handle(requestId, dispatcher)
                CarryBridge.REQUEST_CAMERA -> {
                    applyCompressionOptions(args)
                    mediaHandler.handleCamera(requestId)
                }
                CarryBridge.OPEN_GALLERY -> {
                    applyCompressionOptions(args)
                    mediaHandler.handleGallery(requestId)
                }
                CarryBridge.REQUEST_LOGIN -> loginHandler.handle(requestId, dispatcher)
                CarryBridge.REQUEST_NOTIFICATION_PERMISSION -> handleNotificationPermission(requestId)
                CarryBridge.CHECK_APP_UPDATE -> handleCheckAppUpdate(requestId)
                CarryBridge.OPEN_EXTERNAL_BROWSER -> handleOpenExternalBrowser(requestId, args)
                CarryBridge.CLOSE_APP -> handleCloseApp(requestId)
            }
        }
    }

    // ── In-App Update ─────────────────────────────────────────────────

    private fun checkForAppUpdate() {
        lifecycleScope.launch {
            val info = inAppUpdateManager.checkForUpdate()
            if (info != null) {
                val data = JSONObject().apply {
                    put("available", true)
                    put("storeVersion", info.availableVersionCode())
                }
                dispatchNativeEvent("appUpdateAvailable", data.toString())
            }
        }
    }

    private suspend fun handleCheckAppUpdate(requestId: String) {
        val info = inAppUpdateManager.checkForUpdate()
        val data = JSONObject().apply {
            put("available", info != null)
            put("storeVersion", info?.availableVersionCode() ?: 0)
        }
        dispatcher.sendCallback(BridgeResult(requestId, true, data))
    }

    private fun applyCompressionOptions(args: JSONObject) {
        val maxWidth = args.optInt("maxWidth", MediaRequestHandler.DEFAULT_MAX_WIDTH)
        val maxHeight = args.optInt("maxHeight", MediaRequestHandler.DEFAULT_MAX_HEIGHT)
        val quality = args.optInt("quality", MediaRequestHandler.DEFAULT_QUALITY)
        mediaHandler.setCompressionOptions(maxWidth, maxHeight, quality)
    }

    // ── Simple handlers (추출 불필요) ────────────────────────────────

    private suspend fun handleNotificationPermission(requestId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED
            ) {
                dispatcher.sendCallback(
                    BridgeResult(requestId, true, JSONObject().put("granted", true))
                )
                return
            }
            val granted = permissionHandler.requestSingle(Manifest.permission.POST_NOTIFICATIONS)
            dispatcher.sendCallback(
                BridgeResult(requestId, true, JSONObject().put("granted", granted))
            )
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
