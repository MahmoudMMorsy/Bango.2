package com.example.ui.automation

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import com.example.data.model.AutomationLog
import com.example.data.repository.SearchRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random

object AutomationConstants {
    val USER_AGENTS = listOf(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1",
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36"
    )

    val SEARCH_ENGINES = listOf(
        "Google" to "https://www.google.com/search?q=",
        "Bing" to "https://www.bing.com/search?q=",
        "DuckDuckGo" to "https://duckduckgo.com/?q=",
        "Yahoo" to "https://search.yahoo.com/search?p=",
        "Baidu" to "https://www.baidu.com/s?wd=",
        "Yandex" to "https://yandex.com/search/?text=",
        "Ask" to "https://www.ask.com/web?q=",
        "Ecosia" to "https://www.ecosia.org/search?q="
    )
}

class AutomationEngine(
    private val context: Context,
    private val repository: SearchRepository
) {
    suspend fun applyProxySettings(host: String?, port: Int?): Boolean = withContext(Dispatchers.Main) {
        try {
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                return@withContext false
            }
            val proxyController = ProxyController.getInstance()
            if (host != null && port != null) {
                val proxyConfig = ProxyConfig.Builder()
                    .addProxyRule("$host:$port")
                    .addDirect()
                    .build()

                proxyController.setProxyOverride(
                    proxyConfig,
                    Dispatchers.Main.asExecutor()
                ) {
                    // Proxy applied callback
                }
                true
            } else {
                proxyController.clearProxyOverride(
                    Dispatchers.Main.asExecutor()
                ) {
                    // Proxy cleared callback
                }
                true
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            false
        }
    }

    suspend fun clearProxySettings(): Boolean = applyProxySettings(null, null)

    suspend fun executeBackgroundSearch(
        queryText: String,
        methodType: String,
        topicText: String,
        engineUrlPrefix: String,
        engineName: String,
        proxyHost: String?,
        proxyPort: Int?,
        userAgent: String
    ): AutomationLog = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var status = "SUCCESS"
        var summary = ""
        val fullUrl = "$engineUrlPrefix${java.net.URLEncoder.encode(queryText, "UTF-8")}"

        try {
            // Background HTTP query simulation for WorkManager background execution
            val url = URL(fullUrl)
            val connection = if (proxyHost != null && proxyPort != null) {
                val proxy = java.net.Proxy(
                    java.net.Proxy.Type.HTTP,
                    java.net.InetSocketAddress(proxyHost, proxyPort)
                )
                url.openConnection(proxy) as HttpURLConnection
            } else {
                url.openConnection() as HttpURLConnection
            }

            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", userAgent)
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            val responseCode = connection.responseCode
            if (responseCode == 200) {
                summary = "استجابة هيدر الكود: 200 OK. محاكاة التصفح الخلفي للمحرك $engineName تمت بنجاح."
            } else {
                status = "FAILED"
                summary = "استجابة غير صالحة بكود: $responseCode."
            }
            connection.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
            status = "FAILED"
            summary = "خطأ في الاتصال بالوكيل أو الشبكة: ${e.localizedMessage}"
        }

        val log = AutomationLog(
            topicText = topicText,
            queryText = queryText,
            methodType = methodType,
            status = status,
            durationMs = System.currentTimeMillis() - startTime,
            proxyUsed = if (proxyHost != null) "$proxyHost:$proxyPort" else "None (Direct Connection)",
            userAgentUsed = userAgent.substring(0, kotlin.math.min(userAgent.length, 50)) + "...",
            responseSummary = "$summary (محرك البحث: $engineName)"
        )
        repository.addLog(log)
        log
    }

    // Automates smooth scrolling in WebView
    fun injectAutoScroll(webView: WebView) {
        Handler(Looper.getMainLooper()).post(object : Runnable {
            private var ticks = 0
            private val maxTicks = 45 // Scroll for about 10-15 seconds

            override fun run() {
                try {
                    if (ticks < maxTicks) {
                        // Random down/up scrolls to look human-like
                        val scrollAmount = if (Random.nextFloat() < 0.8f) {
                            Random.nextInt(150, 250) // Scroll down
                        } else {
                            Random.nextInt(-50, -100) // Small backtrack scroll up to mimic scanning list
                        }
                        webView.evaluateJavascript("window.scrollBy({ top: $scrollAmount, behavior: 'smooth' });", null)
                        ticks++
                        Handler(Looper.getMainLooper()).postDelayed(this, 300)
                    }
                } catch (t: Throwable) {
                    t.printStackTrace()
                }
            }
        })
    }
}
