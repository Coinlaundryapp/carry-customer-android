package com.carrylabs.carry.webviewshell.fcm

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.carrylabs.carry.webviewshell.MainActivity
import com.carrylabs.carry.webviewshell.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class CarryFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Log.d(TAG, "FCM token refreshed: $token")
        PushTokenManager.saveToken(this, token)
        // Notify the WebView via event
        MainActivity.instance?.get()?.dispatchNativeEvent(
            "pushTokenRefreshed",
            """{"token":"$token"}"""
        )
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Log.d(TAG, "FCM message received: ${message.data}")

        val dataJson = org.json.JSONObject(message.data as Map<*, *>).toString()

        // 명세서 콜백: window.onPushNotification(data)
        val dispatched = MainActivity.instance?.get()?.dispatchPushNotification(dataJson) ?: false

        // 기존 이벤트도 유지
        if (dispatched) {
            MainActivity.instance?.get()?.dispatchNativeEvent("pushReceived", dataJson)
        }

        // If app is in background or WebView not available, show notification
        if (!dispatched) {
            showNotification(message)
        }
    }

    private fun showNotification(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: return
        val body = message.notification?.body ?: message.data["body"] ?: ""
        val deepLink = message.data["deepLink"]

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (deepLink != null) {
                putExtra("deepLink", deepLink)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = getString(R.string.notification_channel_id)
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    companion object {
        private const val TAG = "CarryFCM"
    }
}
