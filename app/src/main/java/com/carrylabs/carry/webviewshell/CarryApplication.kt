package com.carrylabs.carry.webviewshell

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.carrylabs.carry.webviewshell.webview.CookieHelper

class CarryApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        CookieHelper.setup()
    }

    private fun createNotificationChannel() {
        val channelId = getString(R.string.notification_channel_id)
        val channelName = getString(R.string.notification_channel_name)
        val channelDescription = getString(R.string.notification_channel_description)

        val channel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = channelDescription
        }

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}
