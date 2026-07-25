package com.example.ui.automation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.R
import com.example.data.db.AppDatabase
import com.example.data.repository.SearchRepository
import com.example.data.model.VpnServer
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class SearchWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "AutoSearchDailyWorker"
        private const val CHANNEL_ID = "autosearch_channel"
        private const val NOTIFICATION_ID = 2468

        fun scheduleDailyWorker(context: Context) {
            try {
                val workRequest = PeriodicWorkRequestBuilder<SearchWorker>(
                    4, TimeUnit.HOURS, // Minimum supported PeriodicWork interval is 15 minutes, 4.8 hours is safe
                    48, TimeUnit.MINUTES
                )
                .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override suspend fun doWork(): Result {
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = SearchRepository(database.searchDao())
        val activeTopic = repository.getActiveTopicSync() ?: return Result.success()

        val queries = repository.getQueriesForTopicSync(activeTopic.id)
        if (queries.isEmpty()) return Result.success()

        // Read Built-in VPN settings from SharedPreferences
        val sharedPrefs = applicationContext.getSharedPreferences("auto_search_prefs", Context.MODE_PRIVATE)
        val isBuiltInVpnEnabled = sharedPrefs.getBoolean("built_in_vpn_enabled", false)
        val selectedVpnServerId = sharedPrefs.getString("selected_vpn_server_id", "vpn_us") ?: "vpn_us"

        val activeProxies = repository.getActiveProxiesSync()
        val automationEngine = AutomationEngine(applicationContext, repository)

        showNotification(
            "جاري البحث التلقائي اليومي",
            "موجه لموضوع: '${activeTopic.topicText}' (8 عمليات بحث مستهدفة)"
        )

        // Iterate through all 8 queries, rotate engine, user agent, and proxies
        for (i in queries.indices) {
            val queryObj = queries[i]
            val engine = AutomationConstants.SEARCH_ENGINES[i % AutomationConstants.SEARCH_ENGINES.size]
            val userAgent = AutomationConstants.USER_AGENTS[Random.nextInt(AutomationConstants.USER_AGENTS.size)]

            var proxyHost: String? = null
            var proxyPort: Int? = null

            if (isBuiltInVpnEnabled) {
                val vpnServers = listOf(
                    VpnServer("vpn_us", "الولايات المتحدة (نيويورك)", "US", "🇺🇸", "45.76.160.183", 80, "HTTP", 45),
                    VpnServer("vpn_de", "ألمانيا (فرانكفورت)", "DE", "🇩🇪", "185.162.228.140", 80, "HTTP", 30),
                    VpnServer("vpn_fr", "فرنسا (باريس)", "FR", "🇫🇷", "51.158.111.242", 8080, "HTTP", 35),
                    VpnServer("vpn_sg", "سنغافورة (شمال سنغافورة)", "SG", "🇸🇬", "139.59.255.158", 8080, "HTTP", 65),
                    VpnServer("vpn_jp", "اليابان (طوكيو)", "JP", "🇯🇵", "103.119.144.135", 80, "HTTP", 80),
                    VpnServer("vpn_gb", "المملكة المتحدة (لندن)", "GB", "🇬🇧", "178.62.193.19", 8080, "HTTP", 40)
                )
                val activeServer = vpnServers.find { it.id == selectedVpnServerId } ?: vpnServers[i % vpnServers.size]
                proxyHost = activeServer.host
                proxyPort = activeServer.port
            } else if (activeProxies.isNotEmpty()) {
                val proxy = activeProxies[Random.nextInt(activeProxies.size)]
                proxyHost = proxy.host
                proxyPort = proxy.port
            }

            automationEngine.executeBackgroundSearch(
                queryText = queryObj.queryText,
                methodType = queryObj.methodType,
                topicText = activeTopic.topicText,
                engineUrlPrefix = engine.second,
                engineName = engine.first,
                proxyHost = proxyHost,
                proxyPort = proxyPort,
                userAgent = userAgent
            )

            // Random delay to simulate human pauses (between 5000 and 8000 ms)
            delay(Random.nextLong(5000, 8000))
        }

        showNotification(
            "اكتملت دورة البحث التلقائي اليومي",
            "تم تنفيذ 8 عمليات بنجاح وتفعيل إخفاء الهوية."
        )

        return Result.success()
    }

    private fun showNotification(title: String, message: String) {
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "إشعارات البحث التلقائي",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        // Use Android system default icon launcher background as a fallback notification icon safely
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
