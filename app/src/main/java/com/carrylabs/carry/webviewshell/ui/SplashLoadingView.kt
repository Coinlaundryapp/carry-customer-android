package com.carrylabs.carry.webviewshell.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.View

class SplashLoadingView(private val splashContainer: View) {

    private var dismissed = false

    fun show() {
        splashContainer.visibility = View.VISIBLE
        splashContainer.alpha = 1f
    }

    fun dismiss() {
        if (dismissed) return
        dismissed = true

        splashContainer.animate()
            .alpha(0f)
            .setDuration(300)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    splashContainer.visibility = View.GONE
                }
            })
            .start()
    }

    fun isShowing(): Boolean = !dismissed && splashContainer.visibility == View.VISIBLE
}
