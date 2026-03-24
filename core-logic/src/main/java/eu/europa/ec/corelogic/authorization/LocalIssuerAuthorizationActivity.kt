package eu.europa.ec.corelogic.authorization

import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslCertificate
import android.net.http.SslError
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import eu.europa.ec.eudi.wallet.issue.openid4vci.AuthorizationResponse
import eu.europa.ec.networklogic.R
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLHandshakeException

class LocalIssuerAuthorizationActivity : ComponentActivity() {

    private val sessionId: String by lazy {
        intent.getStringExtra(EXTRA_SESSION_ID)
            ?: error("Missing authorization session id")
    }

    private val authorizationUrl: String by lazy {
        intent.getStringExtra(EXTRA_AUTHORIZATION_URL)
            ?: error("Missing authorization url")
    }

    private val redirectUri: String by lazy {
        intent.getStringExtra(EXTRA_REDIRECT_URI)
            ?: error("Missing redirect uri")
    }

    private val trustedLocalCertificate: X509Certificate by lazy {
        val bytes = resources.openRawResource(R.raw.backend_cert).use { it.readBytes() }
        CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(bytes)) as X509Certificate
    }

    private var completed = false
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = AuthorizationWebViewClient()
        }

        onBackPressedDispatcher.addCallback(this) {
            if (webView.canGoBack()) {
                webView.goBack()
            } else {
                cancelAuthorization("Authorization was cancelled by the user.")
            }
        }

        setContentView(webView)
        webView.loadUrl(authorizationUrl)
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        if (isFinishing && !completed) {
            cancelAuthorization("Authorization was closed before completion.")
        }
        super.onDestroy()
    }

    private fun handleRedirect(uri: Uri): Boolean {
        val value = uri.toString()
        if (!value.startsWith(redirectUri)) {
            return false
        }

        completed = true
        val code = uri.getQueryParameter("code")
        val state = uri.getQueryParameter("state")
        val result = if (code.isNullOrBlank() || state.isNullOrBlank()) {
            Result.failure(IllegalArgumentException("Authorization redirect did not contain code/state."))
        } else {
            Result.success(AuthorizationResponse(code, state))
        }
        LocalIssuerAuthorizationCoordinator.complete(sessionId, result)
        finish()
        return true
    }

    private fun failAuthorization(throwable: Throwable) {
        if (completed) {
            return
        }
        completed = true
        LocalIssuerAuthorizationCoordinator.complete(sessionId, Result.failure(throwable))
        finish()
    }

    private fun cancelAuthorization(message: String) {
        if (completed) {
            finish()
            return
        }
        completed = true
        LocalIssuerAuthorizationCoordinator.complete(
            sessionId,
            Result.failure(IllegalStateException(message)),
        )
        finish()
    }

    private fun canTrust(error: SslError): Boolean {
        val bytes = SslCertificate.saveState(error.certificate)
            ?.getByteArray("x509-certificate")
            ?: return false
        val presentedCertificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(bytes)) as X509Certificate
        return presentedCertificate.encoded.contentEquals(trustedLocalCertificate.encoded)
    }

    private inner class AuthorizationWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val uri = request?.url ?: return false
            return handleRedirect(uri)
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            if (url != null) {
                handleRedirect(Uri.parse(url))
            }
            super.onPageStarted(view, url, favicon)
        }

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?,
        ) {
            if (request?.isForMainFrame == true) {
                failAuthorization(IllegalStateException(error?.description?.toString() ?: "Authorization page failed to load."))
            }
        }

        override fun onReceivedSslError(
            view: WebView?,
            handler: SslErrorHandler?,
            error: SslError?,
        ) {
            if (handler == null || error == null) {
                failAuthorization(SSLHandshakeException("Authorization page TLS validation failed."))
                return
            }

            if (canTrust(error)) {
                handler.proceed()
                return
            }

            handler.cancel()
            failAuthorization(
                SSLHandshakeException("Authorization page TLS validation failed for ${error.url}.")
            )
        }
    }

    companion object {
        const val EXTRA_SESSION_ID = "local.authorization.session_id"
        const val EXTRA_AUTHORIZATION_URL = "local.authorization.url"
        const val EXTRA_REDIRECT_URI = "local.authorization.redirect_uri"
    }
}