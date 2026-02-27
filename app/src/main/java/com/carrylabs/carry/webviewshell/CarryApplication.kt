package com.carrylabs.carry.webviewshell

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.content.getSystemService
import com.kakao.sdk.common.KakaoSdk

class CarryApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initKakaoSdk()
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

        getSystemService<NotificationManager>()
            ?.createNotificationChannel(channel)
    }

    private fun initKakaoSdk() {
        val kakaoClientId = BuildConfig.KAKAO_CLIENT_ID
        if (kakaoClientId.isNotEmpty()) {
            KakaoSdk.init(this, kakaoClientId)
        }
    }
}
