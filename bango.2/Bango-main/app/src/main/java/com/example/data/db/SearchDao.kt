package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.AutomationLog
import com.example.data.model.BrowserProfile
import com.example.data.model.ProxyConfig
import com.example.data.model.SearchQuery
import com.example.data.model.WeeklyTopic
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchDao {

    // --- Weekly Topics ---
    @Query("SELECT * FROM weekly_topics ORDER BY dateCreated DESC")
    fun getAllWeeklyTopics(): Flow<List<WeeklyTopic>>

    @Query("SELECT * FROM weekly_topics WHERE isActive = 1 LIMIT 1")
    fun getActiveTopic(): Flow<WeeklyTopic?>

    @Query("SELECT * FROM weekly_topics WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveTopicSync(): WeeklyTopic?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeeklyTopic(topic: WeeklyTopic): Long

    @Query("UPDATE weekly_topics SET isActive = 0")
    suspend fun deactivateAllTopics()

    @Query("UPDATE weekly_topics SET isActive = 1 WHERE id = :id")
    suspend fun activateTopic(id: Int)

    @Query("DELETE FROM weekly_topics WHERE id = :id")
    suspend fun deleteWeeklyTopic(id: Int)


    // --- Search Queries ---
    @Query("SELECT * FROM search_queries WHERE topicId = :topicId")
    fun getQueriesForTopic(topicId: Int): Flow<List<SearchQuery>>

    @Query("SELECT * FROM search_queries WHERE topicId = :topicId")
    suspend fun getQueriesForTopicSync(topicId: Int): List<SearchQuery>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchQueries(queries: List<SearchQuery>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchQuery(query: SearchQuery): Long

    @Query("DELETE FROM search_queries WHERE id = :id")
    suspend fun deleteSearchQuery(id: Int)

    @Query("DELETE FROM search_queries WHERE topicId = :topicId")
    suspend fun deleteQueriesForTopic(topicId: Int)


    // --- Proxy Configs ---
    @Query("SELECT * FROM proxy_configs ORDER BY id DESC")
    fun getAllProxies(): Flow<List<ProxyConfig>>

    @Query("SELECT * FROM proxy_configs WHERE isActive = 1")
    suspend fun getActiveProxiesSync(): List<ProxyConfig>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProxy(proxy: ProxyConfig): Long

    @Update
    suspend fun updateProxy(proxy: ProxyConfig)

    @Query("DELETE FROM proxy_configs WHERE id = :id")
    suspend fun deleteProxy(id: Int)


    // --- Automation Logs ---
    @Query("SELECT * FROM automation_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<AutomationLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: AutomationLog): Long

    @Query("DELETE FROM automation_logs")
    suspend fun clearAllLogs()

    // --- Browser Profiles ---
    @Query("SELECT * FROM browser_profiles ORDER BY id DESC")
    fun getAllProfiles(): Flow<List<BrowserProfile>>

    @Query("SELECT * FROM browser_profiles ORDER BY id DESC")
    suspend fun getAllProfilesSync(): List<BrowserProfile>

    @Query("SELECT * FROM browser_profiles WHERE isDefault = 1 LIMIT 1")
    fun getDefaultProfile(): Flow<BrowserProfile?>

    @Query("SELECT * FROM browser_profiles WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultProfileSync(): BrowserProfile?

    @Query("SELECT * FROM browser_profiles WHERE id = :id LIMIT 1")
    suspend fun getProfileByIdSync(id: Int): BrowserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: BrowserProfile): Long

    @Update
    suspend fun updateProfile(profile: BrowserProfile)

    @Query("UPDATE browser_profiles SET isDefault = 0")
    suspend fun clearDefaultProfiles()

    @Query("UPDATE browser_profiles SET isDefault = 1 WHERE id = :id")
    suspend fun setDefaultProfile(id: Int)

    @Query("DELETE FROM browser_profiles WHERE id = :id")
    suspend fun deleteProfile(id: Int)
}
