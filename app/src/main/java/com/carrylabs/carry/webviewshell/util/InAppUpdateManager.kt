package com.carrylabs.carry.webviewshell.util

import android.app.Activity
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class InAppUpdateManager(private val activity: Activity) {

    private val appUpdateManager = AppUpdateManagerFactory.create(activity)

    suspend fun checkForUpdate(): AppUpdateInfo? = suspendCoroutine { cont ->
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { info ->
                if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                    && info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
                ) {
                    cont.resume(info)
                } else {
                    cont.resume(null)
                }
            }
            .addOnFailureListener {
                cont.resume(null)
            }
    }

    fun startFlexibleUpdate(
        info: AppUpdateInfo,
        onDownloaded: () -> Unit
    ) {
        val listener = InstallStateUpdatedListener { state ->
            if (state.installStatus() == InstallStatus.DOWNLOADED) {
                onDownloaded()
            }
        }
        appUpdateManager.registerListener(listener)
        appUpdateManager.startUpdateFlowForResult(
            info,
            activity,
            AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
            REQUEST_CODE_UPDATE
        )
    }

    fun completeUpdate() {
        appUpdateManager.completeUpdate()
    }

    companion object {
        const val REQUEST_CODE_UPDATE = 9001
    }
}
