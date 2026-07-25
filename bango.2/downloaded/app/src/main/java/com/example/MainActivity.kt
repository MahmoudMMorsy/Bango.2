package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AppDatabase
import com.example.data.GameRepository
import com.example.ui.*
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Room Database and Repository
        val database = AppDatabase.getDatabase(this)
        val repository = GameRepository(database.gameDao())

        setContent {
            MyApplicationTheme {
                // Construct ViewModel with custom factory
                val gameViewModel: GameViewModel = viewModel(
                    factory = GameViewModelFactory(repository)
                )

                MainScreenContainer(viewModel = gameViewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreenContainer(viewModel: GameViewModel) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf("BROWSER") } // "BROWSER", "DOWNLOADS", "SAVED"

    val downloadState by viewModel.downloadState.collectAsState()
    val playingGame by viewModel.playingGame.collectAsState()

    Scaffold(
        bottomBar = {
            // Standard M3 bottom NavigationBar with clear, active indicators
            NavigationBar(
                modifier = Modifier.testTag("bottom_nav_bar")
            ) {
                NavigationBarItem(
                    selected = currentTab == "BROWSER",
                    onClick = { currentTab = "BROWSER" },
                    icon = { Icon(Icons.Default.Explore, contentDescription = "Browser") },
                    label = { Text("Browser", fontSize = 12.sp) },
                    modifier = Modifier.testTag("nav_browser_tab")
                )
                NavigationBarItem(
                    selected = currentTab == "DOWNLOADS",
                    onClick = { currentTab = "DOWNLOADS" },
                    icon = { Icon(Icons.Default.Download, contentDescription = "Downloads") },
                    label = { Text("Downloads", fontSize = 12.sp) },
                    modifier = Modifier.testTag("nav_downloads_tab")
                )
                NavigationBarItem(
                    selected = currentTab == "SAVED",
                    onClick = { currentTab = "SAVED" },
                    icon = { Icon(Icons.Default.Bookmark, contentDescription = "Saved") },
                    label = { Text("Saved", fontSize = 12.sp) },
                    modifier = Modifier.testTag("nav_saved_tab")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Tab contents
            when (currentTab) {
                "BROWSER" -> BrowserScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
                "DOWNLOADS" -> DownloadsScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
                "SAVED" -> SavedScreen(
                    viewModel = viewModel,
                    onNavigateToUrl = { url ->
                        viewModel.updateUrl(url)
                        currentTab = "BROWSER"
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Real-time Download overlay dialog
            when (val state = downloadState) {
                is DownloadState.Progress -> {
                    AlertDialog(
                        onDismissRequest = {},
                        confirmButton = {},
                        title = { Text("Downloading Source Code", fontWeight = FontWeight.Bold) },
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator()
                                Text(text = state.message, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    )
                }
                is DownloadState.Success -> {
                    LaunchedEffect(state) {
                        Toast.makeText(
                            context,
                            "Successfully saved source package for: ${state.gameTitle}!",
                            Toast.LENGTH_LONG
                        ).show()
                        viewModel.resetDownloadState()
                    }
                }
                is DownloadState.Error -> {
                    AlertDialog(
                        onDismissRequest = { viewModel.resetDownloadState() },
                        title = { Text("Download Failed", color = Color.Red, fontWeight = FontWeight.Bold) },
                        text = { Text("Reason: ${state.errorMessage}", fontSize = 14.sp) },
                        confirmButton = {
                            Button(onClick = { viewModel.resetDownloadState() }) {
                                Text("Dismiss")
                            }
                        }
                    )
                }
                DownloadState.Idle -> { /* Do nothing */ }
            }
        }
    }

    // Fullscreen Overlay Offline Game Player
    AnimatedVisibility(
        visible = playingGame != null,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        playingGame?.let { game ->
            GamePlayerScreen(
                game = game,
                onClose = { viewModel.stopPlaying() },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
