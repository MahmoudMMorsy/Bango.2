package com.example.data.repository

import com.example.BuildConfig
import com.example.data.db.SearchDao
import com.example.data.model.AutomationLog
import com.example.data.model.BrowserProfile
import com.example.data.model.ProxyConfig
import com.example.data.model.SearchQuery
import com.example.data.model.WeeklyTopic
import com.example.data.network.Content
import com.example.data.network.GeminiRetrofitClient
import com.example.data.network.GenerateContentRequest
import com.example.data.network.GenerationConfig
import com.example.data.network.Part
import com.example.data.network.ResponseSchema
import com.example.data.network.SchemaProperty
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class SearchRepository(private val searchDao: SearchDao) {

    val allWeeklyTopics: Flow<List<WeeklyTopic>> = searchDao.getAllWeeklyTopics()
    val activeTopic: Flow<WeeklyTopic?> = searchDao.getActiveTopic()
    val allProxies: Flow<List<ProxyConfig>> = searchDao.getAllProxies()
    val allLogs: Flow<List<AutomationLog>> = searchDao.getAllLogs()

    fun getQueriesForTopic(topicId: Int): Flow<List<SearchQuery>> =
        searchDao.getQueriesForTopic(topicId)

    suspend fun getActiveTopicSync(): WeeklyTopic? =
        searchDao.getActiveTopicSync()

    suspend fun insertWeeklyTopic(topicText: String, customApiKey: String? = null): Int = withContext(Dispatchers.IO) {
        searchDao.deactivateAllTopics()
        val newTopic = WeeklyTopic(topicText = topicText, isActive = true)
        val topicId = searchDao.insertWeeklyTopic(newTopic).toInt()

        // Autogenerate search queries
        val generatedQueries = generateQueries(topicText, customApiKey)
        val searchQueries = generatedQueries.map { (text, method) ->
            SearchQuery(topicId = topicId, queryText = text, methodType = method)
        }
        searchDao.insertSearchQueries(searchQueries)
        topicId
    }

    suspend fun selectActiveTopic(topicId: Int) = withContext(Dispatchers.IO) {
        searchDao.deactivateAllTopics()
        searchDao.activateTopic(topicId)
    }

    suspend fun deleteWeeklyTopic(topicId: Int) = withContext(Dispatchers.IO) {
        searchDao.deleteQueriesForTopic(topicId)
        searchDao.deleteWeeklyTopic(topicId)
    }

    suspend fun getQueriesForTopicSync(topicId: Int): List<SearchQuery> = withContext(Dispatchers.IO) {
        searchDao.getQueriesForTopicSync(topicId)
    }

    suspend fun addCustomQuery(topicId: Int, queryText: String, methodType: String) = withContext(Dispatchers.IO) {
        searchDao.insertSearchQuery(
            SearchQuery(topicId = topicId, queryText = queryText, methodType = methodType, isCustom = true)
        )
    }

    suspend fun deleteQuery(queryId: Int) = withContext(Dispatchers.IO) {
        searchDao.deleteSearchQuery(queryId)
    }

    // --- Proxy Management ---
    suspend fun getActiveProxiesSync(): List<ProxyConfig> = withContext(Dispatchers.IO) {
        searchDao.getActiveProxiesSync()
    }

    suspend fun addProxy(host: String, port: Int, type: String, username: String?, password: String?) = withContext(Dispatchers.IO) {
        searchDao.insertProxy(
            ProxyConfig(host = host, port = port, type = type, username = username, password = password)
        )
    }

    suspend fun deleteProxy(proxyId: Int) = withContext(Dispatchers.IO) {
        searchDao.deleteProxy(proxyId)
    }

    suspend fun updateProxy(proxy: ProxyConfig) = withContext(Dispatchers.IO) {
        searchDao.updateProxy(proxy)
    }


    // --- Automation Logs ---
    suspend fun addLog(log: AutomationLog) = withContext(Dispatchers.IO) {
        searchDao.insertLog(log)
    }

    suspend fun clearLogs() = withContext(Dispatchers.IO) {
        searchDao.clearAllLogs()
    }

    // --- Browser Profiles ---
    val allProfiles: Flow<List<BrowserProfile>> = searchDao.getAllProfiles()
    val defaultProfile: Flow<BrowserProfile?> = searchDao.getDefaultProfile()

    suspend fun getAllProfilesSync(): List<BrowserProfile> = withContext(Dispatchers.IO) {
        searchDao.getAllProfilesSync()
    }

    suspend fun getDefaultProfileSync(): BrowserProfile? = withContext(Dispatchers.IO) {
        searchDao.getDefaultProfileSync()
    }

    suspend fun getProfileByIdSync(id: Int): BrowserProfile? = withContext(Dispatchers.IO) {
        searchDao.getProfileByIdSync(id)
    }

    suspend fun addProfile(
        name: String,
        tag: String,
        proxyHost: String?,
        proxyPort: Int?,
        proxyType: String,
        proxyUsername: String?,
        proxyPassword: String?,
        userAgent: String,
        platform: String,
        language: String,
        screenResolution: String,
        dataSuffix: String,
        isDefault: Boolean,
        webRtcSpoof: String = "BLOCK",
        canvasSpoof: Boolean = true,
        webGlSpoof: Boolean = true,
        audioSpoof: Boolean = true,
        timezoneSpoof: String = "AUTO",
        customTimezone: String = "Asia/Riyadh",
        hardwareConcurrency: Int = 8,
        deviceMemory: Int = 8,
        clientRectsSpoof: Boolean = true,
        geolocationSpoof: String = "AUTO",
        customLatitude: Double = 24.7136,
        customLongitude: Double = 46.6753
    ) = withContext(Dispatchers.IO) {
        if (isDefault) {
            searchDao.clearDefaultProfiles()
        }
        val profile = BrowserProfile(
            name = name,
            tag = tag,
            proxyHost = proxyHost,
            proxyPort = proxyPort,
            proxyType = proxyType,
            proxyUsername = proxyUsername,
            proxyPassword = proxyPassword,
            userAgent = userAgent,
            platform = platform,
            language = language,
            screenResolution = screenResolution,
            dataSuffix = dataSuffix,
            isDefault = isDefault,
            webRtcSpoof = webRtcSpoof,
            canvasSpoof = canvasSpoof,
            webGlSpoof = webGlSpoof,
            audioSpoof = audioSpoof,
            timezoneSpoof = timezoneSpoof,
            customTimezone = customTimezone,
            hardwareConcurrency = hardwareConcurrency,
            deviceMemory = deviceMemory,
            clientRectsSpoof = clientRectsSpoof,
            geolocationSpoof = geolocationSpoof,
            customLatitude = customLatitude,
            customLongitude = customLongitude
        )
        searchDao.insertProfile(profile)
    }

    suspend fun updateProfile(profile: BrowserProfile) = withContext(Dispatchers.IO) {
        searchDao.updateProfile(profile)
    }

    suspend fun setDefaultProfile(profileId: Int) = withContext(Dispatchers.IO) {
        searchDao.clearDefaultProfiles()
        searchDao.setDefaultProfile(profileId)
    }

    suspend fun deleteProfile(profileId: Int) = withContext(Dispatchers.IO) {
        searchDao.deleteProfile(profileId)
    }


    // --- AI Query Generation & Fallback ---
    private suspend fun generateQueries(topicText: String, customApiKey: String? = null): List<Pair<String, String>> {
        val apiKey = if (!customApiKey.isNullOrEmpty()) customApiKey else BuildConfig.GEMINI_API_KEY
        val hasApiKey = apiKey.isNotEmpty() && apiKey != "MY_GEMINI_API_KEY"

        if (hasApiKey) {
            try {
                return fetchGeminiQueries(topicText, apiKey)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return generateFallbackQueries(topicText)
    }

    private suspend fun fetchGeminiQueries(topicText: String, apiKey: String): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val prompt = """
            Generate exactly 8 web search queries in Arabic and/or English for a person interested in: '$topicText'.
            Each of the 8 queries must translate to different techniques:
            1. STANDARD (Direct basic query)
            2. COMPARISON (Compare with competitors)
            3. SYNONYMS (Search with variations, terms)
            4. SCHOLARLY (Research papers, PDF, Scholar site limitations)
            5. TRENDS (Latest breakthroughs 2025/2026/future)
            6. CRITIQUE (Disadvantages, problems, criticisms)
            7. CASE_STUDY (Real-world examples, success cases)
            8. FUTURE (State in 2030, outlook, forecasts)
        """.trimIndent()

        val jsonSchema = ResponseSchema(
            type = "OBJECT",
            description = "JSON object containing a list of 8 generated search queries",
            properties = mapOf(
                "queries" to SchemaProperty(type = "ARRAY", description = "List of queries")
            ),
            required = listOf("queries")
        )

        // Request definition using Direct REST models
        val request = GenerateContentRequest(
            contents = listOf(
                Content(parts = listOf(Part(text = prompt)))
            ),
            systemInstruction = Content(
                parts = listOf(Part(text = "You are an automated search assistant. Perform queries in the most natural language matching the topic (usually Arabic or English). Return the exact JSON structure matching the schema, with 8 items representing 8 diverse queries."))
            ),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.5f
            )
        )

        val response = GeminiRetrofitClient.service.generateContent(apiKey, request)
        val rawJson = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text

        if (!rawJson.isNullOrBlank()) {
            val queries = parseQueriesFromJson(rawJson)
            if (queries.size == 8) {
                return@withContext queries
            }
        }
        generateFallbackQueries(topicText)
    }

    private fun parseQueriesFromJson(jsonString: String): List<Pair<String, String>> {
        return try {
            val moshi = Moshi.Builder().build()
            val type = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
            val adapter = moshi.adapter<Map<String, Any>>(type)
            val rootMap = adapter.fromJson(jsonString)
            val queriesList = rootMap?.get("queries") as? List<*>

            queriesList?.mapNotNull { item ->
                val queryMap = item as? Map<*, *>
                val text = queryMap?.get("queryText") as? String ?: queryMap?.get("text") as? String
                val method = queryMap?.get("methodType") as? String ?: "STANDARD"
                if (text != null) {
                    text to method
                } else {
                    null
                }
            } ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun generateFallbackQueries(topicText: String): List<Pair<String, String>> {
        // High quality local query templates to ensure 100% functionality
        val isArabic = topicText.any { it in '\u0600'..'\u06FF' }
        return if (isArabic) {
            listOf(
                "ما هو $topicText وطريقة عمله بالتفصيل" to "STANDARD",
                "مقارنة بين $topicText والتقنيات البديلة الحالية" to "COMPARISON",
                "مصطلحات ومفاهيم $topicText والأنظمة المرتبطة" to "SYNONYMS",
                "دراسات علمية وأبحاث PDF حول $topicText" to "SCHOLARLY",
                "أحدث التطورات والابتكارات في $topicText بعد عام 2025" to "TRENDS",
                "عيوب وتحديات ومشاكل متعلقة بـ $topicText" to "CRITIQUE",
                "دراسة حالة وتطبيقات عملية ناجحة لـ $topicText في الواقع" to "CASE_STUDY",
                "رؤية مستقبلية وتقديرات لـ $topicText بحلول عام 2030" to "FUTURE"
            )
        } else {
            listOf(
                "Complete guide and overview on $topicText" to "STANDARD",
                "$topicText vs traditional methods comparison" to "COMPARISON",
                "Key concepts, acronyms and terminology of $topicText" to "SYNONYMS",
                "Scholarly articles and research papers on $topicText pdf" to "SCHOLARLY",
                "Latest breakthroughs and releases in $topicText 2025 2026" to "TRENDS",
                "Limitations, criticisms, and risks of $topicText" to "CRITIQUE",
                "Real world case studies and implementation examples of $topicText" to "CASE_STUDY",
                "Future prospects, market size and projections of $topicText by 2030" to "FUTURE"
            )
        }
    }
}
