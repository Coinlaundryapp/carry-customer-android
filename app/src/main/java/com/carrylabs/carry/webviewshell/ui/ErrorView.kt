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
        when (errorType) {
            ErrorType.NETWORK -> {
                titleView.text = context.getString(R.string.error_no_network_title)
                messageView.text = context.getString(R.string.error_no_network_message)
            }
            ErrorType.SERVER -> {
                titleView.text = context.getString(R.string.error_server_title)
                messageView.text = context.getString(R.string.error_server_message)
            }
            ErrorType.SSL -> {
                titleView.text = context.getString(R.string.error_ssl_title)
                messageView.text = context.getString(R.string.error_ssl_message)
            }
        }
        errorContainer.visibility = View.VISIBLE
    }

    fun hide() {
        errorContainer.visibility = View.GONE
    }

    fun isShowing(): Boolean = errorContainer.visibility == View.VISIBLE
}
