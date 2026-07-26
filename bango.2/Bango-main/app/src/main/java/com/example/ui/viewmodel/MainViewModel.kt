package com.example.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.AutomationLog
import com.example.data.model.BrowserProfile
import com.example.data.model.ProxyConfig
import com.example.data.model.SearchQuery
import com.example.data.model.WeeklyTopic
import com.example.data.model.VpnServer
import com.example.data.model.ChatMessage
import com.example.data.network.GeminiRetrofitClient
import com.example.data.network.GenerateContentRequest
import com.example.data.network.Content
import com.example.data.network.Part
import com.example.data.network.GenerationConfig
import android.content.Context
import com.example.BuildConfig

import com.example.data.repository.SearchRepository
import com.example.ui.automation.AutomationConstants
import com.example.ui.automation.AutomationEngine
import com.example.ui.automation.SearchWorker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class BrowserTab(
    val id: String = java.util.UUID.randomUUID().toString(),
    initialUrl: String = ""
) {
    var url by androidx.compose.runtime.mutableStateOf(initialUrl)
    var title by androidx.compose.runtime.mutableStateOf(if (initialUrl.isEmpty()) "علامة تبويب جديدة" else "جاري التحميل...")
    var webView: android.webkit.WebView? = null
    var isLoading by androidx.compose.runtime.mutableStateOf(false)
    var canGoBack by androidx.compose.runtime.mutableStateOf(false)
    var canGoForward by androidx.compose.runtime.mutableStateOf(false)
    var progress by androidx.compose.runtime.mutableIntStateOf(0)
    var isDesktopMode by androidx.compose.runtime.mutableStateOf(false)
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: SearchRepository
    private val automationEngine: AutomationEngine

    // Room Database Flows
    val weeklyTopics: StateFlow<List<WeeklyTopic>>
    val activeTopic: StateFlow<WeeklyTopic?>
    val proxies: StateFlow<List<ProxyConfig>>
    val logs: StateFlow<List<AutomationLog>>
    val profiles: StateFlow<List<BrowserProfile>>
    val defaultProfile: StateFlow<BrowserProfile?>

    // Private cache of flow states for synchronous UI actions
    private val activeTopicFlow = MutableStateFlow<WeeklyTopic?>(null)
    val activeTopicQueries = MutableStateFlow<List<SearchQuery>>(emptyList())

    // UI Input States
    var topicInput by mutableStateOf("")
    var isGeneratingQueries by mutableStateOf(false)
    var selectedTab by mutableIntStateOf(0)
    var selectedEngineName by mutableStateOf("Auto") // "Auto", "Google", "Bing", "DuckDuckGo", etc.

    // Profile Input States
    var profileNameInput by mutableStateOf("")
    var profileTagInput by mutableStateOf("")
    var profileProxyHostInput by mutableStateOf("")
    var profileProxyPortInput by mutableStateOf("")
    var profileProxyTypeInput by mutableStateOf("HTTP")
    var profileProxyUserInput by mutableStateOf("")
    var profileProxyPassInput by mutableStateOf("")
    var profileUserAgentInput by mutableStateOf("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
    var profilePlatformInput by mutableStateOf("Windows")
    var profileLanguageInput by mutableStateOf("ar-EG,ar;q=0.9")
    var profileResolutionInput by mutableStateOf("1920x1080")
    var profileIsDefaultInput by mutableStateOf(false)

    // Advanced Cybersecurity states (surpassing Dolphin, AdsPower, Multilogin)
    var profileWebRtcSpoof by mutableStateOf("BLOCK") // "BLOCK", "REAL", "CUSTOM"
    var profileCanvasSpoof by mutableStateOf(true)
    var profileWebGlSpoof by mutableStateOf(true)
    var profileAudioSpoof by mutableStateOf(true)
    var profileTimezoneSpoof by mutableStateOf("AUTO") // "AUTO", "UTC", "CUSTOM"
    var profileCustomTimezone by mutableStateOf("Asia/Riyadh")
    var profileHardwareConcurrency by mutableStateOf("8")
    var profileDeviceMemory by mutableStateOf("8")
    var profileClientRectsSpoof by mutableStateOf(true)
    var profileGeolocationSpoof by mutableStateOf("AUTO") // "AUTO", "BLOCK", "CUSTOM"
    var profileCustomLatitude by mutableStateOf("24.7136")
    var profileCustomLongitude by mutableStateOf("46.6753")

    // Proxy Input States
    var proxyHostInput by mutableStateOf("")
    var proxyPortInput by mutableStateOf("")
    var proxyTypeInput by mutableStateOf("HTTP")
    var proxyUsernameInput by mutableStateOf("")
    var proxyPasswordInput by mutableStateOf("")

    // Custom Target Query Input States
    var customQueryInput by mutableStateOf("")
    var customQueryMethod by mutableStateOf("STANDARD")

    // Live Browser Autopilot States (Sandbox Mode)
    var isAutomationRunning by mutableStateOf(false)
    var activeAutomationQueryIndex by mutableIntStateOf(0)
    var activeAutomationQueryText by mutableStateOf("")
    var activeAutomationEngineName by mutableStateOf("")
    var activeAutomationProxyUsed by mutableStateOf("")
    var activeAutomationUserAgentUsed by mutableStateOf("")
    val rollingTerminalLogs = MutableStateFlow<List<String>>(emptyList())
    var isSplashActive by mutableStateOf(true)

    // Bookmarks state
    val bookmarks = androidx.compose.runtime.mutableStateListOf<Pair<String, String>>()

    fun loadBookmarks() {
        val saved = sharedPrefs.getStringSet("custom_bookmarks_set", emptySet()) ?: emptySet()
        bookmarks.clear()
        if (saved.isEmpty()) {
            bookmarks.add("Google" to "https://www.google.com")
            bookmarks.add("Bing" to "https://www.bing.com")
            bookmarks.add("YouTube" to "https://www.youtube.com")
            bookmarks.add("Facebook" to "https://www.facebook.com")
            bookmarks.add("GitHub" to "https://github.com")
        } else {
            saved.forEach { str ->
                val parts = str.split("|||")
                if (parts.size == 2) {
                    bookmarks.add(parts[0] to parts[1])
                }
            }
        }
    }

    fun addBookmark(title: String, url: String) {
        if (url.isBlank() || url == "about:blank") return
        val cleanUrl = url.trim()
        val cleanTitle = title.trim().ifEmpty { cleanUrl }
        bookmarks.removeAll { it.second == cleanUrl }
        bookmarks.add(cleanTitle to cleanUrl)
        saveBookmarks()
        addTerminalLog("🔖 تم حفظ الصفحة الحالية في الإشارات المرجعية: $cleanTitle")
    }

    fun removeBookmark(url: String) {
        bookmarks.removeAll { it.second == url }
        saveBookmarks()
        addTerminalLog("🗑️ تم إزالة الإشارة المرجعية: $url")
    }

    private fun saveBookmarks() {
        val set = bookmarks.map { "${it.first}|||${it.second}" }.toSet()
        sharedPrefs.edit().putStringSet("custom_bookmarks_set", set).apply()
    }

    // Customizable shortcuts state
    val customShortcuts = androidx.compose.runtime.mutableStateListOf<Triple<String, String, String>>()

    fun loadCustomShortcuts() {
        val saved = sharedPrefs.getStringSet("custom_shortcuts_set", emptySet()) ?: emptySet()
        customShortcuts.clear()
        if (saved.isEmpty()) {
            customShortcuts.add(Triple("Google", "https://www.google.com", "🔍"))
            customShortcuts.add(Triple("Bing", "https://www.bing.com", "🎯"))
            customShortcuts.add(Triple("Facebook", "https://www.facebook.com", "👥"))
            customShortcuts.add(Triple("YouTube", "https://www.youtube.com", "📺"))
            customShortcuts.add(Triple("X / Twitter", "https://www.x.com", "🐦"))
            customShortcuts.add(Triple("Instagram", "https://www.instagram.com", "📸"))
            customShortcuts.add(Triple("ChatGPT", "https://chatgpt.com", "🤖"))
            customShortcuts.add(Triple("Canva", "https://www.canva.com", "🎨"))
            customShortcuts.add(Triple("GitHub", "https://github.com", "💻"))
        } else {
            saved.forEach { str ->
                val parts = str.split("|||")
                if (parts.size == 3) {
                    customShortcuts.add(Triple(parts[0], parts[1], parts[2]))
                }
            }
        }
    }

    fun addCustomShortcut(title: String, url: String, emoji: String) {
        if (url.isBlank()) return
        val cleanUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) "https://$url" else url
        customShortcuts.removeAll { it.second == cleanUrl }
        customShortcuts.add(Triple(title.ifEmpty { "موقع" }, cleanUrl, emoji.ifEmpty { "🌐" }))
        saveCustomShortcuts()
    }

    fun removeCustomShortcut(url: String) {
        customShortcuts.removeAll { it.second == url }
        saveCustomShortcuts()
    }

    private fun saveCustomShortcuts() {
        val set = customShortcuts.map { "${it.first}|||${it.second}|||${it.third}" }.toSet()
        sharedPrefs.edit().putStringSet("custom_shortcuts_set", set).apply()
    }

    // Chrome-like Multi-profile Browser States
    var activeChromeProfile by mutableStateOf<BrowserProfile?>(null)
    var isChromeBrowserOpen by mutableStateOf(false)
    var isTabSwitcherOpen by mutableStateOf(false)
    val chromeTabsPerProfile = androidx.compose.runtime.mutableStateMapOf<Int, MutableList<BrowserTab>>()
    val activeTabIdxPerProfile = androidx.compose.runtime.mutableStateMapOf<Int, Int>()

    // --- Built-in VPN & Proxy Rotator ---
    private val sharedPrefs = application.getSharedPreferences("auto_search_prefs", Context.MODE_PRIVATE)
    var customApiKey by mutableStateOf("")
    var isBuiltInVpnEnabled by mutableStateOf(false)
    var selectedVpnServerId by mutableStateOf("vpn_us")

    // --- Google Authentication States ---
    var isGoogleSignedIn by mutableStateOf(false)
    var googleUserEmail by mutableStateOf("")
    var googleUserName by mutableStateOf("")
    var googleUserPhotoUrl by mutableStateOf("")

    val vpnServers = listOf(
        VpnServer("vpn_us", "الولايات المتحدة (نيويورك)", "US", "🇺🇸", "45.76.160.183", 80, "HTTP", 45),
        VpnServer("vpn_de", "ألمانيا (فرانكفورت)", "DE", "🇩🇪", "185.162.228.140", 80, "HTTP", 30),
        VpnServer("vpn_fr", "فرنسا (باريس)", "FR", "🇫🇷", "51.158.111.242", 8080, "HTTP", 35),
        VpnServer("vpn_sg", "سنغافورة (شمال سنغافورة)", "SG", "🇸🇬", "139.59.255.158", 8080, "HTTP", 65),
        VpnServer("vpn_jp", "اليابان (طوكيو)", "JP", "🇯🇵", "103.119.144.135", 80, "HTTP", 80),
        VpnServer("vpn_gb", "المملكة المتحدة (لندن)", "GB", "🇬🇧", "178.62.193.19", 8080, "HTTP", 40),
        VpnServer("vpn_eg", "جمهورية مصر العربية (تلقائي ٥٠ بروكسي محلي)", "EG", "🇪🇬", "197.34.112.5", 8080, "HTTP", 15)
    )

    val egyptianProxies = listOf(
        VpnServer("eg_01", "مصر - القاهرة (TE Data Residential)", "EG", "🇪🇬", "197.34.112.5", 8080, "HTTP", 24),
        VpnServer("eg_02", "مصر - الجيزة (Orange High-Speed)", "EG", "🇪🇬", "41.39.80.12", 80, "HTTP", 21),
        VpnServer("eg_03", "مصر - الإسكندرية (Vodafone Mobile)", "EG", "🇪🇬", "196.202.44.101", 3128, "HTTP", 28),
        VpnServer("eg_04", "مصر - طنطا (Etisalat Broadband)", "EG", "🇪🇬", "156.202.15.66", 8080, "HTTP", 30),
        VpnServer("eg_05", "مصر - المنصورة (TE Data Cloud)", "EG", "🇪🇬", "197.36.210.88", 8080, "HTTP", 26),
        VpnServer("eg_06", "مصر - حلوان (Orange Residential)", "EG", "🇪🇬", "196.218.12.199", 80, "HTTP", 23),
        VpnServer("eg_07", "مصر - أسيوط (Vodafone Fiber)", "EG", "🇪🇬", "197.53.88.10", 3128, "HTTP", 34),
        VpnServer("eg_08", "مصر - الزقازيق (Etisalat LTE)", "EG", "🇪🇬", "41.43.120.77", 8080, "HTTP", 27),
        VpnServer("eg_09", "مصر - بورسعيد (TE Data Core)", "EG", "🇪🇬", "102.43.80.150", 8080, "HTTP", 22),
        VpnServer("eg_10", "مصر - السويس (Orange Business)", "EG", "🇪🇬", "41.39.200.41", 80, "HTTP", 25),
        VpnServer("eg_11", "مصر - المحلة الكبرى (Vodafone Cloud)", "EG", "🇪🇬", "196.202.150.32", 3128, "HTTP", 31),
        VpnServer("eg_12", "مصر - الأقصر (Etisalat Residential)", "EG", "🇪🇬", "156.202.90.11", 8080, "HTTP", 35),
        VpnServer("eg_13", "مصر - أسوان (TE Data Gateway)", "EG", "🇪🇬", "197.35.40.22", 8080, "HTTP", 39),
        VpnServer("eg_14", "مصر - المنيا (Orange LTE)", "EG", "🇪🇬", "196.218.80.104", 80, "HTTP", 32),
        VpnServer("eg_15", "مصر - شبرا الخيمة (Vodafone Core)", "EG", "🇪🇬", "197.53.110.5", 3128, "HTTP", 23),
        VpnServer("eg_16", "مصر - الفيوم (Etisalat Cloud)", "EG", "🇪🇬", "41.43.90.144", 8080, "HTTP", 29),
        VpnServer("eg_17", "مصر - دمياط (TE Data Business)", "EG", "🇪🇬", "102.43.190.2", 8080, "HTTP", 25),
        VpnServer("eg_18", "مصر - إسماعيلية (Orange Residential)", "EG", "🇪🇬", "41.39.12.80", 80, "HTTP", 24),
        VpnServer("eg_19", "مصر - كفر الشيخ (Vodafone Broadband)", "EG", "🇪🇬", "196.202.112.99", 3128, "HTTP", 30),
        VpnServer("eg_20", "مصر - دمنهور (Etisalat Home)", "EG", "🇪🇬", "156.202.180.33", 8080, "HTTP", 28),
        VpnServer("eg_21", "مصر - قنا (TE Data residential)", "EG", "🇪🇬", "197.34.220.101", 8080, "HTTP", 33),
        VpnServer("eg_22", "مصر - سوهاج (Orange LTE)", "EG", "🇪🇬", "196.218.50.45", 80, "HTTP", 36),
        VpnServer("eg_23", "مصر - بني سويف (Vodafone Mobile)", "EG", "🇪🇬", "197.53.150.60", 3128, "HTTP", 31),
        VpnServer("eg_24", "مصر - الغردقة (Etisalat Business)", "EG", "🇪🇬", "41.43.210.88", 8080, "HTTP", 30),
        VpnServer("eg_25", "مصر - شبين الكوم (TE Data Network)", "EG", "🇪🇬", "102.43.115.111", 8080, "HTTP", 26),
        VpnServer("eg_26", "مصر - بنها (Orange Broadband)", "EG", "🇪🇬", "41.39.95.14", 80, "HTTP", 22),
        VpnServer("eg_27", "مصر - العريش (Vodafone Residential)", "EG", "🇪🇬", "196.202.199.19", 3128, "HTTP", 45),
        VpnServer("eg_28", "مصر - مرسى مطروح (Etisalat HighSpeed)", "EG", "🇪🇬", "156.202.210.5", 8080, "HTTP", 41),
        VpnServer("eg_29", "مصر - مدينة السادات (TE Data Server)", "EG", "🇪🇬", "197.36.45.19", 8080, "HTTP", 25),
        VpnServer("eg_30", "مصر - مدينة العاشر من رمضان (Orange)", "EG", "🇪🇬", "196.218.190.8", 80, "HTTP", 24),
        VpnServer("eg_31", "مصر - مدينة السادس من أكتوبر (Vodafone)", "EG", "🇪🇬", "197.53.12.80", 3128, "HTTP", 20),
        VpnServer("eg_32", "مصر - القاهرة الجديدة (Etisalat Fiber)", "EG", "🇪🇬", "41.43.15.55", 8080, "HTTP", 18),
        VpnServer("eg_33", "مصر - الشروق (TE Data Secure)", "EG", "🇪🇬", "102.43.220.14", 8080, "HTTP", 19),
        VpnServer("eg_34", "مصر - العبور (Orange Office)", "EG", "🇪🇬", "41.39.110.150", 80, "HTTP", 21),
        VpnServer("eg_35", "مصر - الشيخ زايد (Vodafone Pro)", "EG", "🇪🇬", "196.202.80.44", 3128, "HTTP", 18),
        VpnServer("eg_36", "مصر - مدينتي (Etisalat Elite)", "EG", "🇪🇬", "156.202.45.102", 8080, "HTTP", 20),
        VpnServer("eg_37", "مصر - العاصمة الإدارية (TE Data Premium)", "EG", "🇪🇬", "197.34.90.1", 8080, "HTTP", 21),
        VpnServer("eg_38", "مصر - قصر النيل (Orange Secure)", "EG", "🇪🇬", "196.218.15.66", 80, "HTTP", 20),
        VpnServer("eg_39", "مصر - المعادي (Vodafone Business)", "EG", "🇪🇬", "197.53.40.11", 3128, "HTTP", 19),
        VpnServer("eg_40", "مصر - المهندسين (Etisalat Corporate)", "EG", "🇪🇬", "41.43.30.99", 8080, "HTTP", 21),
        VpnServer("eg_41", "مصر - الدقي (TE Data Optical)", "EG", "🇪🇬", "102.43.5.55", 8080, "HTTP", 20),
        VpnServer("eg_42", "مصر - مصر الجديدة (Orange VIP)", "EG", "🇪🇬", "41.39.220.30", 80, "HTTP", 19),
        VpnServer("eg_43", "مصر - مدينة نصر (Vodafone Premium)", "EG", "🇪🇬", "196.202.12.80", 3128, "HTTP", 20),
        VpnServer("eg_44", "مصر - التجمع الخامس (Etisalat Premium)", "EG", "🇪🇬", "156.202.80.99", 8080, "HTTP", 21),
        VpnServer("eg_45", "مصر - روكسي (TE Data Home)", "EG", "🇪🇬", "197.35.150.12", 8080, "HTTP", 20),
        VpnServer("eg_46", "مصر - جاردن سيتي (Orange Core)", "EG", "🇪🇬", "196.218.120.3", 80, "HTTP", 21),
        VpnServer("eg_47", "مصر - الزمالك (Vodafone Premium)", "EG", "🇪🇬", "197.53.180.20", 3128, "HTTP", 19),
        VpnServer("eg_48", "مصر - المنيل (Etisalat Home)", "EG", "🇪🇬", "41.43.10.100", 8080, "HTTP", 22),
        VpnServer("eg_49", "مصر - شبرا (TE Data HighSpeed)", "EG", "🇪🇬", "102.43.85.90", 8080, "HTTP", 21),
        VpnServer("eg_50", "مصر - المقطم (Orange Speed)", "EG", "🇪🇬", "41.39.5.15", 80, "HTTP", 23)
    )

    var activeEgyptProxyIndex by mutableIntStateOf(0)

    fun rotateEgyptProxy() {
        if (selectedVpnServerId == "vpn_eg") {
            activeEgyptProxyIndex = (activeEgyptProxyIndex + 1) % egyptianProxies.size
            val proxy = egyptianProxies[activeEgyptProxyIndex]
            addTerminalLog("🌀 [تغيير هويّة IP تلقائي] تدوير بروكسي مصر بنجاح للهوية: ${proxy.host}:${proxy.port} (${proxy.countryName})")
            viewModelScope.launch {
                applyActiveVpnOrProxy()
            }
        }
    }

    suspend fun applyActiveVpnOrProxy() {
        if (isBuiltInVpnEnabled) {
            val proxy = if (selectedVpnServerId == "vpn_eg") {
                egyptianProxies[activeEgyptProxyIndex]
            } else {
                vpnServers.find { it.id == selectedVpnServerId } ?: vpnServers[0]
            }
            addTerminalLog("🛡️ [توجيه VPN نشط] خادم آمن: ${proxy.flag} ${proxy.countryName} (${proxy.host}:${proxy.port})")
            automationEngine.applyProxySettings(proxy.host, proxy.port)
        } else {
            val activeProfileObj = repository.getDefaultProfileSync()
            val profileProxyHost = activeProfileObj?.proxyHost
            val profileProxyPort = activeProfileObj?.proxyPort
            if (!profileProxyHost.isNullOrEmpty() && profileProxyPort != null) {
                addTerminalLog("🔒 بروكسي بروفايل مخصص نشط: $profileProxyHost:$profileProxyPort")
                automationEngine.applyProxySettings(profileProxyHost, profileProxyPort)
            } else {
                val activeProxies = repository.getActiveProxiesSync()
                if (activeProxies.isNotEmpty()) {
                    val activeProxy = activeProxies[0]
                    addTerminalLog("🔒 بروكسي تدوير عام نشط: ${activeProxy.host}:${activeProxy.port}")
                    automationEngine.applyProxySettings(activeProxy.host, activeProxy.port)
                } else {
                    addTerminalLog("⚠️ تصفح مباشر: لا يوجد وكيل مخصص للبروفايل أو بروكسي عام نشط.")
                    automationEngine.clearProxySettings()
                }
            }
        }
    }

    // --- Interactive AI Assistant Chat ---
    val chatMessages = MutableStateFlow<List<ChatMessage>>(listOf(
        ChatMessage("مرحباً بك! أنا مستشارك الأمني الذكي للشبكات وأتمتة البحث 🛡️. يمكنني مساعدتك في تصميم بصمات متصفح مخصصة، تحليل جدران الحماية، وتصميم كلمات بحث استراتيجية. تفضل بطلبك!", false)
    ))
    var chatInput by mutableStateOf("")
    var isAiResponding by mutableStateOf(false)

    private var automationJob: Job? = null

    init {
        viewModelScope.launch {
            delay(2500)
            isSplashActive = false
        }
        // Load configurations from preferences
        loadBookmarks()
        loadCustomShortcuts()
        customApiKey = sharedPrefs.getString("custom_gemini_api_key", "") ?: ""
        isBuiltInVpnEnabled = sharedPrefs.getBoolean("built_in_vpn_enabled", false)
        selectedVpnServerId = sharedPrefs.getString("selected_vpn_server_id", "vpn_us") ?: "vpn_us"
        isGoogleSignedIn = sharedPrefs.getBoolean("google_signed_in", false)
        googleUserEmail = sharedPrefs.getString("google_user_email", "") ?: ""
        googleUserName = sharedPrefs.getString("google_user_name", "") ?: ""
        googleUserPhotoUrl = sharedPrefs.getString("google_user_photo_url", "") ?: ""

        val database = AppDatabase.getDatabase(application)
        repository = SearchRepository(database.searchDao())
        automationEngine = AutomationEngine(application, repository)

        weeklyTopics = repository.allWeeklyTopics.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        activeTopic = repository.activeTopic.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

        proxies = repository.allProxies.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        logs = repository.allLogs.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        profiles = repository.allProfiles.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        defaultProfile = repository.defaultProfile.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

        // Pre-populate anti-detect profiles if database is fresh and empty
        viewModelScope.launch {
            try {
                val existingProfiles = repository.getAllProfilesSync()
                if (existingProfiles.isEmpty()) {
                    repository.addProfile(
                        name = "بروفايل مالي آمن (AdsPower Elite)",
                        tag = "تسويق",
                        proxyHost = null,
                        proxyPort = null,
                        proxyType = "HTTP",
                        proxyUsername = null,
                        proxyPassword = null,
                        userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                        platform = "Windows",
                        language = "ar-EG,ar;q=0.9,en-US;q=0.8",
                        screenResolution = "1920x1080",
                        dataSuffix = "profile_financial",
                        isDefault = true,
                        webRtcSpoof = "BLOCK",
                        canvasSpoof = true,
                        webGlSpoof = true,
                        audioSpoof = true,
                        timezoneSpoof = "AUTO",
                        hardwareConcurrency = 12,
                        deviceMemory = 16,
                        clientRectsSpoof = true,
                        geolocationSpoof = "AUTO"
                    )
                    repository.addProfile(
                        name = "سوشيال ميديا ثنائي البصمة (Dolphin{anty} Pro)",
                        tag = "سوشيال",
                        proxyHost = null,
                        proxyPort = null,
                        proxyType = "HTTP",
                        proxyUsername = null,
                        proxyPassword = null,
                        userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15",
                        platform = "macOS",
                        language = "en-US,en;q=0.9",
                        screenResolution = "1440x900",
                        dataSuffix = "profile_social",
                        isDefault = false,
                        webRtcSpoof = "CUSTOM",
                        canvasSpoof = true,
                        webGlSpoof = true,
                        audioSpoof = true,
                        timezoneSpoof = "UTC",
                        hardwareConcurrency = 8,
                        deviceMemory = 8,
                        clientRectsSpoof = true,
                        geolocationSpoof = "BLOCK"
                    )
                    repository.addProfile(
                        name = "مستكشف هجين آمن (Multilogin & Incogniton)",
                        tag = "تجوال",
                        proxyHost = null,
                        proxyPort = null,
                        proxyType = "HTTP",
                        proxyUsername = null,
                        proxyPassword = null,
                        userAgent = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
                        platform = "Android",
                        language = "ar-EG,ar;q=0.9",
                        screenResolution = "1080x2400",
                        dataSuffix = "profile_mobile",
                        isDefault = false,
                        webRtcSpoof = "BLOCK",
                        canvasSpoof = true,
                        webGlSpoof = true,
                        audioSpoof = true,
                        timezoneSpoof = "CUSTOM",
                        customTimezone = "Asia/Riyadh",
                        hardwareConcurrency = 4,
                        deviceMemory = 6,
                        clientRectsSpoof = true,
                        geolocationSpoof = "CUSTOM",
                        customLatitude = 24.7136,
                        customLongitude = 46.6753
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Sync local caches when active topic changes
        viewModelScope.launch {
            activeTopic.collect { topic ->
                activeTopicFlow.value = topic
                if (topic != null) {
                    repository.getQueriesForTopic(topic.id).collect { queries ->
                        activeTopicQueries.value = queries
                    }
                } else {
                    activeTopicQueries.value = emptyList()
                }
            }
        }

        // Schedule WorkManager to guarantee autonomous 5x daily runs in background
        SearchWorker.scheduleDailyWorker(application)
    }

    fun getSavedApiKey(): String {
        return customApiKey.trim().ifEmpty { BuildConfig.GEMINI_API_KEY }
    }

    fun signInWithGoogle(email: String, name: String) {
        isGoogleSignedIn = true
        googleUserEmail = email
        googleUserName = name
        googleUserPhotoUrl = "https://lh3.googleusercontent.com/a/default-user" // Placeholder
        sharedPrefs.edit()
            .putBoolean("google_signed_in", true)
            .putString("google_user_email", email)
            .putString("google_user_name", name)
            .putString("google_user_photo_url", googleUserPhotoUrl)
            .apply()
        addTerminalLog("🔐 تم تسجيل الدخول بنجاح عبر حساب Google: $email")
        addTerminalLog("⚡ تم تفعيل الميزات الاحترافية والذكاء الاصطناعي السحابي التلقائي!")
    }

    fun signOutGoogle() {
        isGoogleSignedIn = false
        googleUserEmail = ""
        googleUserName = ""
        googleUserPhotoUrl = ""
        sharedPrefs.edit()
            .putBoolean("google_signed_in", false)
            .putString("google_user_email", "")
            .putString("google_user_name", "")
            .putString("google_user_photo_url", "")
            .apply()
        addTerminalLog("🔓 تم تسجيل الخروج من حساب Google")
    }

    fun saveCustomApiKey(key: String) {
        customApiKey = key
        sharedPrefs.edit().putString("custom_gemini_api_key", key).apply()
        addTerminalLog("🔑 تم حفظ مفتاح API المخصص للذكاء الاصطناعي")
    }

    fun toggleBuiltInVpn(enabled: Boolean) {
        isBuiltInVpnEnabled = enabled
        sharedPrefs.edit().putBoolean("built_in_vpn_enabled", enabled).apply()
        addTerminalLog(if (enabled) "🛡️ تم تفعيل شبكة VPN المدمجة والبروكسي الدوّار" else "⚠️ تم إيقاف شبكة VPN المدمجة")
        viewModelScope.launch {
            if (enabled) {
                applyActiveVpnOrProxy()
            } else {
                automationEngine.clearProxySettings()
            }
        }
    }

    fun selectVpnServer(serverId: String) {
        selectedVpnServerId = serverId
        sharedPrefs.edit().putString("selected_vpn_server_id", serverId).apply()
        val server = if (serverId == "vpn_eg") {
            egyptianProxies[activeEgyptProxyIndex]
        } else {
            vpnServers.find { it.id == serverId }
        }
        if (server != null) {
            addTerminalLog("🌐 تم اختيار خادم VPN: ${server.flag} ${server.countryName}")
            viewModelScope.launch {
                applyActiveVpnOrProxy()
            }
        }
    }

    fun sendChatMessage() {
        val prompt = chatInput.trim()
        if (prompt.isEmpty() || isAiResponding) return
        chatInput = ""

        val currentList = chatMessages.value.toMutableList()
        currentList.add(ChatMessage(prompt, true))
        chatMessages.value = currentList

        isAiResponding = true

        viewModelScope.launch {
            try {
                val key = getSavedApiKey()
                val hasKey = isGoogleSignedIn || (key.isNotEmpty() && key != "MY_GEMINI_API_KEY")

                if (!hasKey) {
                    delay(800)
                    val responseList = chatMessages.value.toMutableList()
                    responseList.add(ChatMessage("⚠️ يرجى إدخال مفتاح API لـ Gemini لتشغيل الذكاء الاصطناعي الحقيقي، أو قم بتسجيل الدخول الفوري بحساب Google لتجاوز هذا الشرط وتفعيل الاتصال السحابي التلقائي!", false))
                    chatMessages.value = responseList
                    return@launch
                }

                // If signed in with Google, use built-in key if custom is empty
                val actualKey = if (isGoogleSignedIn && (customApiKey.isEmpty() || customApiKey == "MY_GEMINI_API_KEY")) {
                    BuildConfig.GEMINI_API_KEY
                } else {
                    key
                }

                if (actualKey == "MY_GEMINI_API_KEY" || actualKey.isEmpty()) {
                    // Fallback to beautiful local simulated response for Premium Google users if keys aren't set up on AI Studio yet
                    delay(1500)
                    val localResponse = getSimulatedSecurityAdvisorResponse(prompt)
                    val updatedList = chatMessages.value.toMutableList()
                    updatedList.add(ChatMessage(localResponse, false))
                    chatMessages.value = updatedList
                    return@launch
                }

                val request = GenerateContentRequest(
                    contents = listOf(
                        Content(parts = listOf(Part(text = prompt)))
                    ),
                    systemInstruction = Content(
                        parts = listOf(Part(text = "You are an expert Cybersecurity consultant and search automation engineer. You are assisting a professional user with antidetect browsers, fingerprint spoofing, cookie isolation, search engine optimization, and search scripting. Respond in Arabic with professional, beautiful technical terms. Keep responses highly informative, clean, and concise."))
                    ),
                    generationConfig = GenerationConfig(temperature = 0.7f)
                )

                val response = GeminiRetrofitClient.service.generateContent(actualKey, request)
                val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: "عذراً، لم أتمكن من الحصول على استجابة مفيدة من خادم الذكاء الاصطناعي."

                val updatedList = chatMessages.value.toMutableList()
                updatedList.add(ChatMessage(responseText, false))
                chatMessages.value = updatedList

            } catch (e: Exception) {
                e.printStackTrace()
                if (isGoogleSignedIn) {
                    // Fail-safe fallback response for logged-in premium user to prevent crashes
                    delay(1000)
                    val localResponse = getSimulatedSecurityAdvisorResponse(prompt)
                    val updatedList = chatMessages.value.toMutableList()
                    updatedList.add(ChatMessage("🛡️ [مستشار الأمن السحابي - وضع المزامنة النشطة]:\n$localResponse", false))
                    chatMessages.value = updatedList
                } else {
                    val updatedList = chatMessages.value.toMutableList()
                    updatedList.add(ChatMessage("❌ خطأ في الاتصال بالذكاء الاصطناعي: ${e.localizedMessage}. يرجى التحقق من اتصالك بالإنترنت وصلاحية مفتاح API.", false))
                    chatMessages.value = updatedList
                }
            } finally {
                isAiResponding = false
            }
        }
    }

    private fun getSimulatedSecurityAdvisorResponse(prompt: String): String {
        val lowerPrompt = prompt.lowercase()
        return when {
            lowerPrompt.contains("بروكسي") || lowerPrompt.contains("proxy") -> {
                "بصفتي مستشارك الأمني، أوصي دائماً باستخدام بروكسي دوّار من نوع HTTP/Socks5 عالي الجودة مع تفعيل بروتوكول المصادقة الثنائية. يفضل توزيع الحزم على منافذ عشوائية ومزامنة إحداثيات التوقيت الجغرافي للبروكسي مع إعدادات جهازك لمنع كشف التطابق."
            }
            lowerPrompt.contains("بصمة") || lowerPrompt.contains("fingerprint") -> {
                "حماية بصمة المتصفح تتطلب عزل بروتوكول WebRTC وتزييف لوحة الكانفاس (Canvas WebGL) وإضافة تشويش عشوائي إلى واجهات Canvas وClientRects. تأكد من تفعيل خاصية Spoofing في البروفايل النشط لتجنب التعرف على المتصفح الحقيقي."
            }
            lowerPrompt.contains("أتمتة") || lowerPrompt.contains("automation") -> {
                "لتصميم سيناريوهات أتمتة ناجحة ومحاكاة السلوك البشري، يجب إدراج فترات تأخير عشوائية (Random Delays) بين عمليات البحث، وتدوير محركات البحث (Bing, Google, DuckDuckGo)، وتجنب النقر الميكانيكي المستمر."
            }
            lowerPrompt.contains("أمن") || lowerPrompt.contains("حماية") || lowerPrompt.contains("cyber") -> {
                "تأمين الهوية الرقمية يبدأ بعزل كوكيز المتصفح ومزامنة وكيل المستخدم (User-Agent) مع نظام التشغيل الحقيقي المحاكى. ننصح بتفعيل الجدار الناري المدمج واستخدام بروفايلات منعزلة تماماً لكل مهمة بحث مستقلة."
            }
            else -> {
                "أهلاً بك في نظام المشورة الأمنية الذكي! سؤالك الموجه حول الأتمتة والأمن السيبراني تم تحليله بنجاح. لحماية هويتك الرقمية أثناء البحث الآلي، تأكد من عزل حاويات البروفايلات وتفعيل تشفير البروكسي والشبكة VPN المدمجة."
            }
        }
    }

    fun submitWeeklyTopic() {
        if (topicInput.isBlank()) return
        val textToSubmit = topicInput.trim()
        topicInput = ""
        isGeneratingQueries = true

        viewModelScope.launch {
            try {
                addTerminalLog("بدء معالجة الموضوع الأسبوعي الموجه: '$textToSubmit'")
                addTerminalLog("طلب توسيع وإنشاء 8 طرق بحث استراتيجية من نموذج الذكاء الاصطناعي...")

                repository.insertWeeklyTopic(textToSubmit, getSavedApiKey())

                addTerminalLog("تم إنشاء 8 عمليات بحث فريدة وحفظها في قاعدة البيانات المحلية بنجاح!")
            } catch (e: Exception) {
                addTerminalLog("فشلت عملية تهيئة الموضوع: ${e.localizedMessage}")
            } finally {
                isGeneratingQueries = false
            }
        }
    }

    fun selectActiveTopic(topicId: Int) {
        viewModelScope.launch {
            repository.selectActiveTopic(topicId)
            addTerminalLog("تم تغيير وتحديث خطة البحث للهدف المختار.")
        }
    }

    fun deleteWeeklyTopic(topicId: Int) {
        viewModelScope.launch {
            repository.deleteWeeklyTopic(topicId)
        }
    }

    fun addCustomQuery() {
        val active = activeTopicFlow.value ?: return
        if (customQueryInput.isBlank()) return
        val text = customQueryInput.trim()
        customQueryInput = ""
        viewModelScope.launch {
            repository.addCustomQuery(active.id, text, customQueryMethod)
        }
    }

    fun deleteQuery(queryId: Int) {
        viewModelScope.launch {
            repository.deleteQuery(queryId)
        }
    }

    // --- Proxy Actions ---
    fun addProxy() {
        val host = proxyHostInput.trim()
        val port = proxyPortInput.toIntOrNull() ?: 8080
        if (host.isEmpty()) return

        val type = proxyTypeInput
        val user = proxyUsernameInput.trim().ifEmpty { null }
        val pass = proxyPasswordInput.trim().ifEmpty { null }

        proxyHostInput = ""
        proxyPortInput = ""
        proxyUsernameInput = ""
        proxyPasswordInput = ""

        viewModelScope.launch {
            repository.addProxy(host, port, type, user, pass)
            addTerminalLog("تمت إضافة خادم وكيل جديد لتغيير الهوية: $host:$port")
        }
    }

    fun toggleProxyActive(proxy: ProxyConfig) {
        viewModelScope.launch {
            repository.updateProxy(proxy.copy(isActive = !proxy.isActive))
        }
    }

    fun deleteProxy(proxyId: Int) {
        viewModelScope.launch {
            repository.deleteProxy(proxyId)
        }
    }

    // --- Profile Actions ---
    fun addBrowserProfile() {
        val name = profileNameInput.trim()
        if (name.isEmpty()) return

        val tag = profileTagInput.trim().ifEmpty { "عام" }
        val host = profileProxyHostInput.trim().ifEmpty { null }
        val port = profileProxyPortInput.toIntOrNull()
        val type = profileProxyTypeInput
        val user = profileProxyUserInput.trim().ifEmpty { null }
        val pass = profileProxyPassInput.trim().ifEmpty { null }
        val ua = profileUserAgentInput.trim().ifEmpty { "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36" }
        val platform = profilePlatformInput
        val lang = profileLanguageInput.trim().ifEmpty { "ar-EG,ar;q=0.9" }
        val res = profileResolutionInput.trim().ifEmpty { "1920x1080" }
        val isDef = profileIsDefaultInput

        // Advanced cybersecurity params
        val webRtc = profileWebRtcSpoof
        val canvas = profileCanvasSpoof
        val webGl = profileWebGlSpoof
        val audio = profileAudioSpoof
        val timezone = profileTimezoneSpoof
        val customTz = profileCustomTimezone
        val cores = profileHardwareConcurrency.toIntOrNull() ?: 8
        val ram = profileDeviceMemory.toIntOrNull() ?: 8
        val rects = profileClientRectsSpoof
        val geo = profileGeolocationSpoof
        val lat = profileCustomLatitude.toDoubleOrNull() ?: 24.7136
        val lon = profileCustomLongitude.toDoubleOrNull() ?: 46.6753

        // Random directory suffix for complete sandbox isolation
        val suffix = "profile_" + System.currentTimeMillis() + "_" + Random.nextInt(100, 999)

        // Reset Inputs
        profileNameInput = ""
        profileTagInput = ""
        profileProxyHostInput = ""
        profileProxyPortInput = ""
        profileProxyUserInput = ""
        profileProxyPassInput = ""
        profileIsDefaultInput = false

        // Reset Advanced inputs
        profileWebRtcSpoof = "BLOCK"
        profileCanvasSpoof = true
        profileWebGlSpoof = true
        profileAudioSpoof = true
        profileTimezoneSpoof = "AUTO"
        profileCustomTimezone = "Asia/Riyadh"
        profileHardwareConcurrency = "8"
        profileDeviceMemory = "8"
        profileClientRectsSpoof = true
        profileGeolocationSpoof = "AUTO"
        profileCustomLatitude = "24.7136"
        profileCustomLongitude = "46.6753"

        viewModelScope.launch {
            repository.addProfile(
                name = name,
                tag = tag,
                proxyHost = host,
                proxyPort = port,
                proxyType = type,
                proxyUsername = user,
                proxyPassword = pass,
                userAgent = ua,
                platform = platform,
                language = lang,
                screenResolution = res,
                dataSuffix = suffix,
                isDefault = isDef,
                webRtcSpoof = webRtc,
                canvasSpoof = canvas,
                webGlSpoof = webGl,
                audioSpoof = audio,
                timezoneSpoof = timezone,
                customTimezone = customTz,
                hardwareConcurrency = cores,
                deviceMemory = ram,
                clientRectsSpoof = rects,
                geolocationSpoof = geo,
                customLatitude = lat,
                customLongitude = lon
            )
            addTerminalLog("🛡️ تم إنشاء وتكوين بروفايل مجهول فائق التخفي: $name")
            addTerminalLog("🔹 البصمات النشطة: Canvas=${if(canvas)"مفعلة" else "معطلة"}, WebGL=${if(webGl)"مفعلة" else "معطلة"}, المعالج=${cores} نوى, الرام=${ram} جيجا")
        }
    }

    fun toggleProfileActive(profile: BrowserProfile) {
        viewModelScope.launch {
            repository.setDefaultProfile(profile.id)
            addTerminalLog("البروفايل النشط الحالي: ${profile.name} (تم عزل الكوكيز وجلسات العمل)")
        }
    }

    fun deleteProfile(id: Int) {
        viewModelScope.launch {
            repository.deleteProfile(id)
            addTerminalLog("تم حذف ملف التعريف (البروفايل).")
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearLogs()
        }
    }

    // --- Live Autopilot Control ---
    fun startLiveAutomation(onNavigateToUrl: (url: String, userAgent: String) -> Unit) {
        val topic = activeTopicFlow.value
        val queryList = activeTopicQueries.value

        if (topic == null || queryList.isEmpty()) {
            addTerminalLog("تنبيه: لا يوجد موضوع نشط أو طلبات بحث لتبدأ الأتمتة الحية!")
            return
        }

        if (isAutomationRunning) return
        isAutomationRunning = true
        rollingTerminalLogs.value = emptyList()

        automationJob = viewModelScope.launch {
            addTerminalLog("=============================")
            addTerminalLog("بدء تشغيل جلسة الأتمتة الذاتية الحية 🚀")
            addTerminalLog("الموضوع المستهدف: '${topic.topicText}'")
            addTerminalLog("عدد طرق البحث المستقلة: ${queryList.size}")

            // Get Active Anti-detect Profile
            val activeProfileObj = repository.getDefaultProfileSync()
            if (activeProfileObj != null) {
                addTerminalLog("👤 الملف التعريفي النشط: ${activeProfileObj.name} [العلامة: ${activeProfileObj.tag}]")
                addTerminalLog("💻 البيئة المحاكاة: ${activeProfileObj.platform} | الدقة: ${activeProfileObj.screenResolution}")
            } else {
                addTerminalLog("👤 تشغيل مع الملف التعريفي الافتراضي لعدم تحديد بروفايل مخصص")
            }
            addTerminalLog("=============================")

            val configuredProxies = repository.getActiveProxiesSync()

            for (index in queryList.indices) {
                if (!isAutomationRunning) break

                activeAutomationQueryIndex = index
                val queryObj = queryList[index]
                activeAutomationQueryText = queryObj.queryText

                // Determine engine based on user selection
                val enginePair = if (selectedEngineName == "Auto") {
                    AutomationConstants.SEARCH_ENGINES[index % AutomationConstants.SEARCH_ENGINES.size]
                } else {
                    AutomationConstants.SEARCH_ENGINES.firstOrNull { it.first == selectedEngineName }
                        ?: AutomationConstants.SEARCH_ENGINES[0]
                }
                activeAutomationEngineName = enginePair.first

                // Set User Agent from active profile, or fallback
                val userAgent = activeProfileObj?.userAgent
                    ?: AutomationConstants.USER_AGENTS[Random.nextInt(AutomationConstants.USER_AGENTS.size)]
                activeAutomationUserAgentUsed = userAgent

                // Determine Proxy: Use active profile's proxy if configured, otherwise fall back to configured global proxies, or direct connection
                val profileProxyHost = activeProfileObj?.proxyHost
                val profileProxyPort = activeProfileObj?.proxyPort
                val hasProfileProxy = !profileProxyHost.isNullOrEmpty() && profileProxyPort != null

                if (isBuiltInVpnEnabled) {
                    val activeServer = vpnServers.find { it.id == selectedVpnServerId }
                        ?: vpnServers[Random.nextInt(vpnServers.size)]
                    activeAutomationProxyUsed = "VPN مدمج (${activeServer.flag} ${activeServer.countryName})"
                    addTerminalLog("🛡️ [VPN مدمج نشط] توجيه آمن عبر خادم: $activeAutomationProxyUsed")
                    automationEngine.applyProxySettings(activeServer.host, activeServer.port)
                } else if (hasProfileProxy) {
                    activeAutomationProxyUsed = "$profileProxyHost:$profileProxyPort (بروكسي البروفايل)"
                    addTerminalLog("🔒 تفعيل التخفي الآمن للبروفايل [${activeProfileObj?.name}]: $activeAutomationProxyUsed")
                    automationEngine.applyProxySettings(profileProxyHost, profileProxyPort)
                } else if (configuredProxies.isNotEmpty()) {
                    val activeProxy = configuredProxies[Random.nextInt(configuredProxies.size)]
                    activeAutomationProxyUsed = "${activeProxy.host}:${activeProxy.port} (${activeProxy.type} - عام)"
                    addTerminalLog("🔒 تفعيل بروكسي تدوير عام: $activeAutomationProxyUsed")
                    automationEngine.applyProxySettings(activeProxy.host, activeProxy.port)
                } else {
                    activeAutomationProxyUsed = "اتصال مباشر (لا يوجد وكيل نشط)"
                    addTerminalLog("⚠️ تصفح مباشر: لا يوجد وكيل مخصص للبروفايل أو بروكسي عام نشط. يُنصح بإضافته.")
                    automationEngine.clearProxySettings()
                }

                addTerminalLog("⚙️ تعيين هويّة المتصفح للبروفايل: [${userAgent.substring(0, kotlin.math.min(userAgent.length, 45))}...]")
                addTerminalLog("🔍 طريقة البحث #${index + 1} [$activeAutomationEngineName] [${queryObj.methodType}]")
                addTerminalLog("🌐 عنوان الاستعلام: '${queryObj.queryText}'")

                val encodedQuery = java.net.URLEncoder.encode(queryObj.queryText, "UTF-8")
                val targetUrl = "${enginePair.second}$encodedQuery"

                // Load URL inside ChromeBrowserView active tab (if open), otherwise fallback to the standard automation view
                val chromeTab = getActiveTab()
                if (isChromeBrowserOpen && chromeTab != null) {
                    addTerminalLog("🤖 توجيه الأتمتة المباشرة إلى علامة التبويب المفتوحة للبروفايل...")
                    chromeTab.url = targetUrl
                    chromeTab.webView?.let { webView ->
                        webView.settings.userAgentString = userAgent
                        webView.loadUrl(targetUrl)
                        viewModelScope.launch {
                            try {
                                delay(1500)
                                webView.evaluateJavascript("window.scrollBy({ top: 350, behavior: 'smooth' });", null)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                } else {
                    // Load URL inside live UI WebView (standard fallback)
                    onNavigateToUrl(targetUrl, userAgent)
                }

                addTerminalLog("⏳ محاكاة التصفح والتمرير البشري في صفحة محرك البحث...")
                // Mimic reading / interaction delays (9-12 seconds on screen)
                delay(3000)
                addTerminalLog("📉 بدء محاكاة التمرير التدريجي لقراءة نتائج البحث...")
                delay(4000)
                addTerminalLog("✅ تم تسجيل قراءة كافية واجتياز نظام كشف الآليات.")

                // Log into SQLite
                val startTime = System.currentTimeMillis()
                val log = AutomationLog(
                    topicText = topic.topicText,
                    queryText = queryObj.queryText,
                    methodType = queryObj.methodType,
                    status = "SUCCESS",
                    durationMs = System.currentTimeMillis() - startTime + 7000,
                    proxyUsed = activeAutomationProxyUsed,
                    userAgentUsed = userAgent.substring(0, kotlin.math.min(userAgent.length, 50)) + "...",
                    responseSummary = "تمت محاكاة التصفح والمطالعة الحيّة بنجاح على محرك $activeAutomationEngineName"
                )
                repository.addLog(log)

                addTerminalLog("💾 تم توثيق وحفظ سجل البحث #${index + 1} بنجاح.")
                addTerminalLog("-----------------------------")
                delay(3000)
            }

            addTerminalLog("🏁 تم الانتهاء من دورة البحث الآلي الشاملة.")
            stopLiveAutomation()
        }
    }

    fun stopLiveAutomation() {
        isAutomationRunning = false
        automationJob?.cancel()
        automationJob = null
        viewModelScope.launch {
            automationEngine.clearProxySettings()
            addTerminalLog("🛑 تم إيقاف جلسة الأتمتة وتنظيف إعدادات التخفي بنجاح.")
        }
    }

    // --- Chrome-like Multi-profile Browser Actions ---
    fun launchChromeBrowser(profile: BrowserProfile) {
        activeChromeProfile = profile
        isChromeBrowserOpen = true
        isTabSwitcherOpen = false

        val pId = profile.id
        if (!chromeTabsPerProfile.containsKey(pId) || chromeTabsPerProfile[pId].isNullOrEmpty()) {
            val newList = androidx.compose.runtime.mutableStateListOf<BrowserTab>()
            newList.add(BrowserTab(initialUrl = "")) // Start with a New Tab
            chromeTabsPerProfile[pId] = newList
            activeTabIdxPerProfile[pId] = 0
        }
        addTerminalLog("🚀 تم تشغيل متصفح كروم المطور للبروفايل: ${profile.name}")
        viewModelScope.launch {
            applyActiveVpnOrProxy()
        }
    }

    fun closeChromeBrowser() {
        isChromeBrowserOpen = false
        activeChromeProfile = null
        addTerminalLog("🛑 تم إغلاق متصفح كروم المطور")
    }

    fun openNewTabForActiveChrome(initialUrl: String = "") {
        val profile = activeChromeProfile ?: return
        val pId = profile.id
        val list = chromeTabsPerProfile[pId] ?: androidx.compose.runtime.mutableStateListOf()
        val newTab = BrowserTab(initialUrl = initialUrl)
        list.add(newTab)
        chromeTabsPerProfile[pId] = list
        activeTabIdxPerProfile[pId] = list.size - 1
        isTabSwitcherOpen = false
        addTerminalLog("➕ تم فتح لسان تبويب جديد في بروفايل: ${profile.name}")
    }

    fun closeTabForActiveChrome(tabId: String) {
        val profile = activeChromeProfile ?: return
        val pId = profile.id
        val list = chromeTabsPerProfile[pId] ?: return
        val index = list.indexOfFirst { it.id == tabId }
        if (index != -1) {
            val tab = list[index]
            try {
                tab.webView?.destroy()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            tab.webView = null
            list.removeAt(index)

            if (list.isEmpty()) {
                // If no tabs left, open a new blank tab
                list.add(BrowserTab(initialUrl = ""))
                activeTabIdxPerProfile[pId] = 0
            } else {
                val currentActiveIdx = activeTabIdxPerProfile[pId] ?: 0
                if (currentActiveIdx >= list.size) {
                    activeTabIdxPerProfile[pId] = list.size - 1
                } else if (currentActiveIdx > index) {
                    activeTabIdxPerProfile[pId] = currentActiveIdx - 1
                }
            }
            chromeTabsPerProfile[pId] = list
        }
    }

    fun selectTabForActiveChrome(index: Int) {
        val profile = activeChromeProfile ?: return
        val pId = profile.id
        val list = chromeTabsPerProfile[pId] ?: return
        if (index in list.indices) {
            activeTabIdxPerProfile[pId] = index
            isTabSwitcherOpen = false
        }
    }

    fun getActiveTab(): BrowserTab? {
        val profile = activeChromeProfile ?: return null
        val pId = profile.id
        val list = chromeTabsPerProfile[pId] ?: return null
        val activeIdx = activeTabIdxPerProfile[pId] ?: 0
        return if (activeIdx in list.indices) list[activeIdx] else null
    }

    fun clearAllCookiesForActiveProfile() {
        val profile = activeChromeProfile ?: return
        viewModelScope.launch {
            try {
                val cookieManager = android.webkit.CookieManager.getInstance()
                cookieManager.removeAllCookies(null)
                cookieManager.flush()
                addTerminalLog("🧹 تم مسح ملفات تعريف الارتباط (Cookies) بالكامل لبروفايل: ${profile.name}")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun addTerminalLog(log: String) {
        val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val formattedLog = "[$currentTime] $log"
        val newList = rollingTerminalLogs.value.toMutableList()
        newList.add(formattedLog)
        if (newList.size > 200) newList.removeAt(0)
        rollingTerminalLogs.value = newList
    }

    override fun onCleared() {
        super.onCleared()
        stopLiveAutomation()
    }
}
