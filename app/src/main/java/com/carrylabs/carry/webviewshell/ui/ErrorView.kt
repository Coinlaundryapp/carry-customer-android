package com.carrylabs.carry.webviewshell.ui

import android.view.View
import android.widget.Button
import android.widget.TextView
import com.carrylabs.carry.webviewshell.R
import com.carrylabs.carry.webviewshell.webview.CarryWebViewClient.ErrorType

class ErrorView(
    private val errorContainer: View,
    private val onRetry: () -> Unit
) {

    private val titleView: TextView = errorContainer.findViewById(R.id.errorTitle)
    private val messageView: TextView = errorContainer.findViewById(R.id.errorMessage)
    private val retryButton: Button = errorContainer.findViewById(R.id.errorRetryButton)

    init {
        retryButton.setOnClickListener {
            hide()
            onRetry()
        }
    }

    fun show(errorType: ErrorType) {
        val context = errorContainer.context
        val (titleRes, messageRes) = when (errorType) {
            ErrorType.NETWORK -> R.string.error_no_network_title to R.string.error_no_network_message
            ErrorType.SERVER -> R.string.error_server_title to R.string.error_server_message
            ErrorType.SSL -> R.string.error_ssl_title to R.string.error_ssl_message
            ErrorType.SAFE_BROWSING -> R.string.error_safe_browsing_title to R.string.error_safe_browsing_message
        }
        titleView.text = context.getString(titleRes)
        messageView.text = context.getString(messageRes)
        errorContainer.visibility = View.VISIBLE
    }

    fun hide() {
        errorContainer.visibility = View.GONE
    }

    fun isShowing(): Boolean = errorContainer.visibility == View.VISIBLE
}
