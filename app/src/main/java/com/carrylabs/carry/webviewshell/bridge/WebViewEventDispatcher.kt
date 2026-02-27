package com.carrylabs.carry.webviewshell.bridge

/**
 * WebView로 이벤트를 전달하는 인터페이스.
 * FCM 서비스 등 외부 컴포넌트가 MainActivity를 직접 참조하지 않도록 추상화한다.
 */
interface WebViewEventDispatcher {
    fun dispatchNativeEvent(eventName: String, dataJson: String): Boolean
    fun dispatchPushNotification(dataJson: String): Boolean
}

/**
 * WebViewEventDispatcher 등록소. Activity가 onCreate/onDestroy에서 등록/해제한다.
 */
object WebViewEventDispatcherRegistry {
    @Volatile
    private var dispatcher: WebViewEventDispatcher? = null

    fun register(dispatcher: WebViewEventDispatcher) {
        this.dispatcher = dispatcher
    }

    fun unregister(dispatcher: WebViewEventDispatcher) {
        if (this.dispatcher === dispatcher) {
            this.dispatcher = null
        }
    }

    fun get(): WebViewEventDispatcher? = dispatcher
}
