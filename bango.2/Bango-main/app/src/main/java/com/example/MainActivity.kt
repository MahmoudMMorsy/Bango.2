package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.MainScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MainViewModel

import com.example.data.db.AppDatabase
import com.example.data.GameRepository
import com.example.ui.GameViewModel
import com.example.ui.GameViewModelFactory
import com.yausername.youtubedl_android.YoutubeDL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize YoutubeDL for premium media downloading capability
        try {
            YoutubeDL.getInstance().init(application)
        } catch (e: Exception) {
            android.util.Log.e("YoutubeDL", "Failed to initialize youtubedl-android", e)
        }

        // Initialize Room Database and Repository
        val database = AppDatabase.getDatabase(this)
        val repository = GameRepository(database.gameDao())

        setContent {
            MyApplicationTheme {
                val mainViewModel: MainViewModel = viewModel()
                val gameViewModel: GameViewModel = viewModel(
                    factory = GameViewModelFactory(repository)
                )
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        viewModel = mainViewModel,
                        gameViewModel = gameViewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    androidx.compose.material3.Text(text = "Hello $name!", modifier = modifier)
}
