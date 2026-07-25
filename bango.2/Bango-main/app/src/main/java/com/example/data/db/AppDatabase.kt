package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.AutomationLog
import com.example.data.model.BrowserProfile
import com.example.data.model.ProxyConfig
import com.example.data.model.SearchQuery
import com.example.data.model.WeeklyTopic

@Database(
    entities = [
        WeeklyTopic::class,
        SearchQuery::class,
        ProxyConfig::class,
        AutomationLog::class,
        BrowserProfile::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun searchDao(): SearchDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "autosearch_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
