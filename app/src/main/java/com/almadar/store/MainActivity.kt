package com.almadar.store

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var progress: ProgressBar
    private lateinit var errorPanel: View
    private lateinit var errorMessage: TextView

    companion object {
        private const val STORE_URL = "https://al-madar.vercel.app"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        swipeRefresh = findViewById(R.id.swipeRefresh)
        webView = findViewById(R.id.webView)
        progress = findViewById(R.id.pageProgress)
        errorPanel = findViewById(R.id.errorPanel)
        errorMessage = findViewById(R.id.errorMessage)
        val retryButton: Button = findViewById(R.id.retryButton)

        swipeRefresh.setColorSchemeResources(R.color.gold)
        swipeRefresh.setOnRefreshListener { loadStore() }
        retryButton.setOnClickListener { loadStore() }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = "$userAgentString AlMadarAndroid/1.0"
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
                if (newProgress == 100) swipeRefresh.isRefreshing = false
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme?.lowercase()
                val host = request.url.host?.lowercase().orEmpty()
                // Keep HTTPS storefront, Shopify checkout and payment pages in this WebView.
                return when {
                    scheme == "https" && (
                        host == "al-madar.vercel.app" ||
                        host == "myshopify.com" || host.endsWith(".myshopify.com") ||
                        host == "shopify.com" || host.endsWith(".shopify.com") ||
                        host == "shop.app" || host.endsWith(".shop.app") ||
                        host == "vercel.app" || host.endsWith(".vercel.app")
                    ) -> false
                    scheme == "https" -> false // Keep secure payment-provider redirects in-app.
                    scheme == "http" -> true // Cleartext is intentionally blocked.
                    else -> true
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                errorPanel.visibility = View.GONE
                progress.visibility = View.VISIBLE
                progress.progress = 0
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                swipeRefresh.isRefreshing = false
                progress.visibility = View.GONE
                errorPanel.visibility = View.GONE
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) showError()
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: android.webkit.WebResourceResponse
            ) {
                if (request.isForMainFrame && errorResponse.statusCode >= 500) showError()
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (errorPanel.visibility == View.VISIBLE) {
                    if (webView.canGoBack()) webView.goBack() else loadStore()
                } else if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })

        if (savedInstanceState != null) webView.restoreState(savedInstanceState)
        else loadStore()
    }

    private fun hasInternet(): Boolean {
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun loadStore() {
        errorPanel.visibility = View.GONE
        if (!hasInternet()) {
            swipeRefresh.isRefreshing = false
            showError(getString(R.string.no_connection))
            return
        }
        webView.loadUrl(if (webView.url.isNullOrBlank()) STORE_URL else webView.url!!)
    }

    private fun showError(message: String = getString(R.string.load_error)) {
        runOnUiThread {
            swipeRefresh.isRefreshing = false
            progress.visibility = View.GONE
            errorMessage.text = message
            errorPanel.visibility = View.VISIBLE
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onDestroy() {
        webView.apply {
            stopLoading()
            webChromeClient = null
            webViewClient = WebViewClient()
            destroy()
        }
        super.onDestroy()
    }
}
