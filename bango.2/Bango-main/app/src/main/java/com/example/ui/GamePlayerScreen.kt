package com.example.ui

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.GameEntity
import com.example.util.RuffleGenerator
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GamePlayerScreen(
    game: GameEntity,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Locate local index.html or .swf path
    val playUrlOrData = remember(game) {
        val folder = File(game.localFolder)
        if (!folder.exists()) {
            return@remember Pair("error", "Local game folder not found")
        }

        if (game.gameType == "HTML") {
            val indexFile = findIndexHtmlRecursively(folder)
            if (indexFile != null && indexFile.exists()) {
                Pair("html_url", "file://${indexFile.absolutePath}")
            } else {
                Pair("error", "index.html launcher not found in game folder")
            }
        } else if (game.gameType == "VIDEO" || game.gameType == "AUDIO" || game.gameType == "DOC" || game.gameType == "FILE") {
            val mediaFile = File(game.zipPath)
            if (mediaFile.exists()) {
                val isVideoOrAudio = mediaFile.name.let { name ->
                    name.endsWith(".mp4", true) || name.endsWith(".webm", true) ||
                    name.endsWith(".mkv", true) || name.endsWith(".avi", true) ||
                    name.endsWith(".mov", true) || name.endsWith(".mp3", true) ||
                    name.endsWith(".wav", true) || name.endsWith(".ogg", true) ||
                    name.endsWith(".m4a", true)
                }
                if (isVideoOrAudio) {
                    val mediaHtml = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                          <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
                          <style>
                            body { margin:0; padding:0; background:#000; display:flex; align-items:center; justify-content:center; height:100vh; overflow:hidden; }
                            video, audio { max-width:100%; max-height:100%; width:100%; height:auto; outline:none; }
                          </style>
                        </head>
                        <body>
                          <video controls autoplay playsinline src="file://${mediaFile.absolutePath}"></video>
                        </body>
                        </html>
                    """.trimIndent()
                    Pair("flash_data", mediaHtml)
                } else {
                    Pair("html_url", "file://${mediaFile.absolutePath}")
                }
            } else {
                Pair("error", "Downloaded file not found: ${mediaFile.name}")
            }
        } else {
            // Flash SWF file lookup
            val swfFile = folder.listFiles()?.firstOrNull { it.name.endsWith(".swf", ignoreCase = true) }
            if (swfFile != null && swfFile.exists()) {
                val htmlCode = RuffleGenerator.generate(swfFile.absolutePath, isLocal = true)
                Pair("flash_data", htmlCode)
            } else {
                Pair("error", "SWF file not found in Flash game folder")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = game.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Offline Player (${game.gameType})",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("game_player_close")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Exit Player", tint = Color.Red)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            val (type, content) = playUrlOrData

            if (type == "error") {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "Play Error",
                        color = Color.Red,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = content,
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier.background(Color.DarkGray.copy(alpha=0.5f), RoundedCornerShape(8.dp)).padding(12.dp)
                    )
                    Button(onClick = onClose) {
                        Text("Go Back")
                    }
                }
            } else {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )

                            webViewClient = object : WebViewClient() {
                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    super.onReceivedError(view, request, error)
                                    // Ignore local assets protocol or asset file resolution warnings
                                }
                            }

                            webChromeClient = WebChromeClient()

                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                allowFileAccess = true
                                allowContentAccess = true
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            }

                            webViewRef = this

                            // Load depending on game type
                            if (type == "html_url") {
                                loadUrl(content)
                            } else if (type == "flash_data") {
                                loadDataWithBaseURL("file:///", content, "text/html", "utf-8", null)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

// Private helper to look up index.html recursively
private fun findIndexHtmlRecursively(dir: File): File? {
    val files = dir.listFiles() ?: return null
    for (file in files) {
        if (file.isFile && file.name.equals("index.html", ignoreCase = true)) {
            return file
        }
    }
    for (file in files) {
        if (file.isDirectory) {
            val found = findIndexHtmlRecursively(file)
            if (found != null) return found
        }
    }
    return null
}
