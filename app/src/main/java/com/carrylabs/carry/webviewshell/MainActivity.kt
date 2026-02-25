package com.carrylabs.carry.webviewshell

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.carrylabs.carry.webviewshell.bridge.BridgeResult
import com.carrylabs.carry.webviewshell.bridge.CarryBridge
import com.carrylabs.carry.webviewshell.bridge.NativeCallDispatcher
import com.carrylabs.carry.webviewshell.databinding.ActivityMainBinding
import com.carrylabs.carry.webviewshell.permission.PermissionHandler
import com.carrylabs.carry.webviewshell.ui.ErrorView
import com.carrylabs.carry.webviewshell.ui.SplashLoadingView
import com.carrylabs.carry.webviewshell.util.BiometricHelper
import com.carrylabs.carry.webviewshell.util.FileProviderHelper
import com.carrylabs.carry.webviewshell.util.NetworkUtils
import com.carrylabs.carry.webviewshell.webview.CarryWebChromeClient
import com.carrylabs.carry.webviewshell.webview.CarryWebViewClient
import com.carrylabs.carry.webviewshell.webview.WebViewSetup
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import org.json.JSONObject
import java.lang.ref.WeakReference

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var dispatcher: NativeCallDispatcher
    private lateinit var splashView: SplashLoadingView
    private lateinit var errorView: ErrorView
    private lateinit var networkUtils: NetworkUtils
    private lateinit var permissionHandler: PermissionHandler
    private lateinit var biometricHelper: BiometricHelper

    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private var cameraImageUri: Uri? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // Pending async bridge request state
    private var pendingRequestId: String? = null
    private var pendingMethod: String? = null

    companion object {
        var instance: WeakReference<MainActivity>? = null
    }

    // ── Activity Result Launchers ────────────────────────────────────

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        fileUploadCallback?.onReceiveValue(if (uri != null) arrayOf(uri) else null)
        fileUploadCallback = null
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        handleCameraResult(success)
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        handleGalleryResult(uri)
    }

    // ── Lifecycle ────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = WeakReference(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        permissionHandler = PermissionHandler(this)
        biometricHelper = BiometricHelper(this)
        networkUtils = NetworkUtils(this)

        setupWebView()
        setupSplash()
        setupErrorView()
        setupBackNavigation()
        setupSwipeRefresh()
        setupNetworkListener()

        // Request notification permission on Android 13+
        requestNotificationPermission()

        // Load initial URL or handle deep link
        if (savedInstanceState == null) {
            handleIntent(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        dispatchNativeEvent("appResumed", "{}")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onDestroy() {
        networkCallback?.let { networkUtils.unregisterNetworkCallback(it) }
        binding.webView.destroy()
        instance = null
        super.onDestroy()
    }

    // ── WebView Setup ────────────────────────────────────────────────

    private fun setupWebView() {
        WebViewSetup.configure(binding.webView)

        // Bridge
        dispatcher = NativeCallDispatcher(binding.webView)
        val bridge = CarryBridge(this) { method, requestId, args ->
            runOnUiThread { handleAsyncBridgeRequest(method, requestId, args) }
        }
        binding.webView.addJavascriptInterface(bridge, "CarryNative")

        // WebViewClient
        binding.webView.webViewClient = CarryWebViewClient(
            onPageStarted = {
                binding.progressBar.visibility = View.VISIBLE
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
            }
        )

        // WebChromeClient
        binding.webView.webChromeClient = CarryWebChromeClient(
            onProgressChanged = { progress ->
                binding.progressBar.progress = progress
                if (progress >= 100) {
                    binding.progressBar.visibility = View.GONE
                }
            },
            onFileChooser = { callback, params ->
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = callback
                try {
                    val intent = params.createIntent()
                    fileChooserLauncher.launch(intent.type ?: "*/*")
                    true
                } catch (_: Exception) {
                    fileUploadCallback?.onReceiveValue(null)
                    fileUploadCallback = null
                    false
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
        errorView = ErrorView(binding.errorView.root) {
            loadBaseUrl()
        }
    }

    // ── Back Navigation ──────────────────────────────────────────────

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (errorView.isShowing()) {
                    errorView.hide()
                    return
                }
                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    // ── Swipe Refresh ────────────────────────────────────────────────

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            if (errorView.isShowing()) {
                errorView.hide()
            }
            binding.webView.reload()
        }
    }

    // ── Network Listener ─────────────────────────────────────────────

    private fun setupNetworkListener() {
        networkCallback = networkUtils.registerNetworkCallback { isConnected ->
            runOnUiThread {
                dispatchNativeEvent(
                    "connectivityChanged",
                    """{"isConnected":$isConnected}"""
                )
            }
        }
    }

    // ── Intent / Deep Link Handling ──────────────────────────────────

    private fun handleIntent(intent: Intent?) {
        if (intent == null) {
            loadBaseUrl()
            return
        }

        // Deep link from push notification
        val deepLink = intent.getStringExtra("deepLink")
        if (deepLink != null) {
            binding.webView.loadUrl(deepLink)
            dispatchNativeEvent("deepLink", """{"url":"$deepLink"}""")
            return
        }

        // Deep link from intent filter
        val data = intent.data
        if (data != null) {
            val url = resolveDeepLink(data)
            binding.webView.loadUrl(url)
            dispatchNativeEvent("deepLink", """{"url":"$url"}""")
            return
        }

        loadBaseUrl()
    }

    private fun resolveDeepLink(uri: Uri): String {
        // Custom scheme: carry://path → BASE_URL/path
        if (uri.scheme == "carry") {
            val path = uri.path ?: ""
            return BuildConfig.BASE_URL + path
        }
        // App Links: https://app.carry.com/path → BASE_URL/path
        return BuildConfig.BASE_URL + (uri.path ?: "")
    }

    private fun loadBaseUrl() {
        binding.webView.loadUrl(BuildConfig.BASE_URL)
    }

    // ── Notification Permission ──────────────────────────────────────

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionHandler.requestSingle(Manifest.permission.POST_NOTIFICATIONS) { _ -> }
            }
        }
    }

    // ── Async Bridge Request Handling ────────────────────────────────

    private fun handleAsyncBridgeRequest(method: String, requestId: String, args: JSONObject) {
        when (method) {
            "requestBiometric" -> handleBiometric(requestId, args)
            "requestLocation" -> handleLocation(requestId)
            "requestCamera" -> handleCamera(requestId)
            "openGallery" -> handleGallery(requestId)
        }
    }

    private fun handleBiometric(requestId: String, args: JSONObject) {
        val title = args.optString("title", "Authentication")
        val description = args.optString("description", "")

        if (!biometricHelper.canAuthenticate()) {
            dispatcher.sendCallback(
                BridgeResult(requestId, false, error = "Biometric not available")
            )
            return
        }

        biometricHelper.authenticate(
            title = title,
            description = description,
            onSuccess = {
                dispatcher.sendCallback(
                    BridgeResult(requestId, true, JSONObject().put("authenticated", true))
                )
            },
            onError = { code, message ->
                dispatcher.sendCallback(
                    BridgeResult(requestId, false, error = "Biometric error ($code): $message")
                )
            }
        )
    }

    private fun handleLocation(requestId: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            pendingRequestId = requestId
            pendingMethod = "requestLocation"
            permissionHandler.requestSingle(Manifest.permission.ACCESS_FINE_LOCATION) { granted ->
                if (granted) {
                    fetchLocation(requestId)
                } else {
                    dispatcher.sendCallback(
                        BridgeResult(requestId, false, error = "Location permission denied")
                    )
                }
                pendingRequestId = null
                pendingMethod = null
            }
            return
        }
        fetchLocation(requestId)
    }

    private fun fetchLocation(requestId: String) {
        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(this)
            val cancellationToken = CancellationTokenSource()

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                dispatcher.sendCallback(
                    BridgeResult(requestId, false, error = "Location permission not granted")
                )
                return
            }

            fusedClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationToken.token
            ).addOnSuccessListener { location ->
                if (location != null) {
                    val data = JSONObject().apply {
                        put("latitude", location.latitude)
                        put("longitude", location.longitude)
                        put("accuracy", location.accuracy.toDouble())
                    }
                    dispatcher.sendCallback(BridgeResult(requestId, true, data))
                } else {
                    dispatcher.sendCallback(
                        BridgeResult(requestId, false, error = "Location unavailable")
                    )
                }
            }.addOnFailureListener { e ->
                dispatcher.sendCallback(
                    BridgeResult(requestId, false, error = "Location error: ${e.message}")
                )
            }
        } catch (e: Exception) {
            dispatcher.sendCallback(
                BridgeResult(requestId, false, error = "Location error: ${e.message}")
            )
        }
    }

    private fun handleCamera(requestId: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            pendingRequestId = requestId
            pendingMethod = "requestCamera"
            permissionHandler.requestSingle(Manifest.permission.CAMERA) { granted ->
                if (granted) {
                    launchCamera(requestId)
                } else {
                    dispatcher.sendCallback(
                        BridgeResult(requestId, false, error = "Camera permission denied")
                    )
                }
                pendingRequestId = null
                pendingMethod = null
            }
            return
        }
        launchCamera(requestId)
    }

    private fun launchCamera(requestId: String) {
        pendingRequestId = requestId
        pendingMethod = "requestCamera"
        cameraImageUri = FileProviderHelper.createImageUri(this)
        cameraLauncher.launch(cameraImageUri!!)
    }

    private fun handleCameraResult(success: Boolean) {
        val reqId = pendingRequestId ?: return
        if (success && cameraImageUri != null) {
            val data = JSONObject().put("uri", cameraImageUri.toString())
            dispatcher.sendCallback(BridgeResult(reqId, true, data))
        } else {
            dispatcher.sendCallback(BridgeResult(reqId, false, error = "Camera cancelled"))
        }
        pendingRequestId = null
        pendingMethod = null
        cameraImageUri = null
    }

    private fun handleGallery(requestId: String) {
        pendingRequestId = requestId
        pendingMethod = "openGallery"
        galleryLauncher.launch("image/*")
    }

    private fun handleGalleryResult(uri: Uri?) {
        val reqId = pendingRequestId ?: return
        if (uri != null) {
            val data = JSONObject().put("uri", uri.toString())
            dispatcher.sendCallback(BridgeResult(reqId, true, data))
        } else {
            dispatcher.sendCallback(BridgeResult(reqId, false, error = "Gallery cancelled"))
        }
        pendingRequestId = null
        pendingMethod = null
    }

    // ── Native→JS Event Dispatch (public for FCM service) ───────────

    fun dispatchNativeEvent(eventName: String, dataJson: String): Boolean {
        if (!::dispatcher.isInitialized) return false
        dispatcher.sendEvent(eventName, dataJson)
        return true
    }
}
