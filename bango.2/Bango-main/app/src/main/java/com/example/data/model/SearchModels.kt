package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "weekly_topics")
data class WeeklyTopic(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val topicText: String,
    val dateCreated: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
)

@Entity(tableName = "search_queries")
data class SearchQuery(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val topicId: Int,
    val queryText: String,
    val methodType: String, // e.g., "Standard", "Scholarly", "Trends", etc.
    val isCustom: Boolean = false
)

@Entity(tableName = "proxy_configs")
data class ProxyConfig(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val host: String,
    val port: Int,
    val type: String = "HTTP", // HTTP or SOCKS5
    val username: String? = null,
    val password: String? = null,
    val isActive: Boolean = true
)

@Entity(tableName = "automation_logs")
data class AutomationLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val topicText: String,
    val queryText: String,
    val methodType: String,
    val status: String, // "SUCCESS" or "FAILED" or "RUNNING"
    val durationMs: Long = 0L,
    val proxyUsed: String = "None (Direct Connection)",
    val userAgentUsed: String = "Default Browser",
    val responseSummary: String = ""
)

@Entity(tableName = "browser_profiles")
data class BrowserProfile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val tag: String = "",
    val proxyHost: String? = null,
    val proxyPort: Int? = null,
    val proxyType: String = "HTTP",
    val proxyUsername: String? = null,
    val proxyPassword: String? = null,
    val userAgent: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
    val platform: String = "Windows",
    val language: String = "ar-EG,ar;q=0.9,en-US;q=0.8",
    val screenResolution: String = "1920x1080",
    val dataSuffix: String = "",
    val isDefault: Boolean = false,
    val dateCreated: Long = System.currentTimeMillis(),

    // --- Advanced Cybersecurity Anti-Fingerprint settings (surpassing AdsPower, Dolphin{anty}, Multilogin) ---
    val webRtcSpoof: String = "BLOCK", // "BLOCK", "REAL", "CUSTOM"
    val canvasSpoof: Boolean = true, // Inject random cryptographic canvas noise
    val webGlSpoof: Boolean = true, // Spoof WebGL renderer & inject readback noise
    val audioSpoof: Boolean = true, // Obfuscate audio context signatures
    val timezoneSpoof: String = "AUTO", // "AUTO" (match proxy country), "UTC", "CUSTOM"
    val customTimezone: String = "Asia/Riyadh",
    val hardwareConcurrency: Int = 8, // Override physical CPU core counts
    val deviceMemory: Int = 8, // Override physical RAM reporting
    val clientRectsSpoof: Boolean = true, // Defend against layout bounding boxes fingerprinting
    val geolocationSpoof: String = "AUTO", // "AUTO" (match proxy), "BLOCK", "CUSTOM"
    val customLatitude: Double = 24.7136,
    val customLongitude: Double = 46.6753
)

data class VpnServer(
    val id: String,
    val countryName: String,
    val countryCode: String,
    val flag: String,
    val host: String,
    val port: Int,
    val type: String = "HTTP",
    val latency: Int = 30
)

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
