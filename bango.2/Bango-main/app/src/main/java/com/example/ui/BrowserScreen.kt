package com.example.ui

import android.content.Context
import android.view.ViewGroup
import android.webkit.*
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentUrl by viewModel.currentUrl.collectAsState()
    val isBookmarked by viewModel.isCurrentPageBookmarked.collectAsState()
    val searchInput by viewModel.searchInput.collectAsState()

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var pageTitle by remember { mutableStateOf("Game Web Browser") }
    var loadProgress by remember { mutableStateOf(0) }
    var isPageLoading by remember { mutableStateOf(false) }

    // Auto detection status
    var detectedSwfUrl by remember { mutableStateOf<String?>(null) }
    var detectedZipUrl by remember { mutableStateOf<String?>(null) }
    var detectedVideoUrl by remember { mutableStateOf<String?>(null) }
    var detectedFileUrl by remember { mutableStateOf<String?>(null) }

    // Download dialog visibility
    var showDownloadDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        // Address & Toolbar with Status Bar padding to avoid any cutout/notch interference
        Surface(
            tonalElevation = 8.dp,
            shadowElevation = 4.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.statusBarsPadding()
        ) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = {
                            webViewRef?.let {
                                if (it.canGoBack()) {
                                    it.goBack()
                                } else {
                                    Toast.makeText(context, "No back history", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.testTag("browser_back_button")
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }

                    IconButton(
                        onClick = {
                            webViewRef?.let {
                                if (it.canGoForward()) {
                                    it.goForward()
                                } else {
                                    Toast.makeText(context, "No forward history", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "Forward")
                    }

                    IconButton(
                        onClick = {
                            webViewRef?.reload()
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }

                    // Search / URL Input
                    OutlinedTextField(
                        value = searchInput,
                        onValueChange = { viewModel.searchInput.value = it },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("browser_address_bar"),
                        placeholder = { Text("Search or enter web game URL...", fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                        ),
                        trailingIcon = {
                            if (searchInput.isNotEmpty()) {
                                IconButton(onClick = { viewModel.searchInput.value = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear text", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        shape = RoundedCornerShape(26.dp)
                    )

                    IconButton(
                        onClick = {
                            val target = formatUrl(searchInput)
                            viewModel.updateUrl(target)
                            webViewRef?.loadUrl(target)
                        }
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Go")
                    }
                }

                // Secondary Toolbar Actions (Bookmark, Home, Scraper Download)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                val homeUrl = "https://www.crazygames.com"
                                viewModel.updateUrl(homeUrl)
                                webViewRef?.loadUrl(homeUrl)
                            }
                        ) {
                            Icon(Icons.Default.Home, contentDescription = "Home Page")
                        }

                        IconButton(
                            onClick = {
                                if (isBookmarked) {
                                    viewModel.removeBookmarkByUrl(currentUrl)
                                    Toast.makeText(context, "Removed from Bookmarks", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.addBookmark(pageTitle, currentUrl)
                                    Toast.makeText(context, "Added to Bookmarks", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = "Bookmark current page",
                                tint = if (isBookmarked) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }

                        // Indicators for auto detected files
                        if (detectedVideoUrl != null) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .clickable { showDownloadDialog = true }
                            ) {
                                Text("Video Detected", color = Color.White, modifier = Modifier.padding(4.dp), fontSize = 10.sp)
                            }
                        }
                        if (detectedFileUrl != null) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .clickable { showDownloadDialog = true }
                            ) {
                                Text("Media File Detected", color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(4.dp), fontSize = 10.sp)
                            }
                        }
                        if (detectedSwfUrl != null) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .clickable {
                                        showDownloadDialog = true
                                    }
                            ) {
                                Text("Flash SWF Detected", color = Color.White, modifier = Modifier.padding(4.dp), fontSize = 10.sp)
                            }
                        }
                        if (detectedZipUrl != null) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .clickable {
                                        showDownloadDialog = true
                                    }
                            ) {
                                Text("HTML ZIP Detected", color = Color.White, modifier = Modifier.padding(4.dp), fontSize = 10.sp)
                            }
                        }
                    }

                    // Main Advanced Download / Crawling Trigger Button
                    Button(
                        onClick = { showDownloadDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("download_source_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Download Source", fontSize = 12.sp)
                    }
                }
            }
        }

        // Progress bar
        AnimatedVisibility(visible = isPageLoading) {
            LinearProgressIndicator(
                progress = { loadProgress / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Core Webview
        Box(modifier = Modifier.weight(1f)) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        // WebView Client configurations
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isPageLoading = true
                                detectedSwfUrl = null
                                detectedZipUrl = null
                                detectedVideoUrl = null
                                detectedFileUrl = null
                                url?.let {
                                    viewModel.updateUrl(it)
                                    val lower = it.lowercase()
                                    if (lower.endsWith(".swf")) {
                                        detectedSwfUrl = it
                                    } else if (lower.endsWith(".zip")) {
                                        detectedZipUrl = it
                                    } else if (lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".mkv") || lower.endsWith(".avi") || lower.endsWith(".mp3")) {
                                        detectedVideoUrl = it
                                    }
                                }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isPageLoading = false
                                view?.title?.let {
                                    pageTitle = it
                                    if (url != null) {
                                        viewModel.addHistoryEntry(it, url)
                                    }
                                }

                                // Inspect DOM for swf, zip, video, audio tags
                                view?.evaluateJavascript(
                                    """
                                    (function() {
                                        var res = { swf: null, video: null };
                                        var embeds = document.getElementsByTagName('embed');
                                        for(var i=0; i<embeds.length; i++) {
                                            if(embeds[i].src && embeds[i].src.includes('.swf')) res.swf = embeds[i].src;
                                        }
                                        var videos = document.getElementsByTagName('video');
                                        for(var i=0; i<videos.length; i++) {
                                            if(videos[i].src) res.video = videos[i].src;
                                            var sources = videos[i].getElementsByTagName('source');
                                            if(sources.length > 0 && sources[0].src) res.video = sources[0].src;
                                        }
                                        return JSON.stringify(res);
                                    })()
                                    """.trimIndent()
                                ) { result ->
                                    if (result != "null" && !result.isNullOrEmpty()) {
                                        try {
                                            if (result.contains("\"video\":") && !result.contains("\"video\":null")) {
                                                val vid = result.substringAfter("\"video\":\"").substringBefore("\"")
                                                if (vid.isNotEmpty() && vid != "null") detectedVideoUrl = vid
                                            }
                                            if (result.contains("\"swf\":") && !result.contains("\"swf\":null")) {
                                                val swf = result.substringAfter("\"swf\":\"").substringBefore("\"")
                                                if (swf.isNotEmpty() && swf != "null") detectedSwfUrl = swf
                                            }
                                        } catch(e: Exception) {}
                                    }
                                }
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                super.onProgressChanged(view, newProgress)
                                loadProgress = newProgress
                            }

                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                super.onReceivedTitle(view, title)
                                title?.let { pageTitle = it }
                            }
                        }

                        // Apply security and feature flags
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            allowFileAccess = true
                            allowContentAccess = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        }

                        setDownloadListener { url, _, _, _, _ ->
                            val lower = url.lowercase()
                            if (lower.endsWith(".swf")) {
                                detectedSwfUrl = url
                            } else if (lower.endsWith(".zip")) {
                                detectedZipUrl = url
                            } else if (lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".mkv") || lower.endsWith(".avi") || lower.endsWith(".mov") || lower.endsWith(".mp3")) {
                                detectedVideoUrl = url
                            } else {
                                detectedFileUrl = url
                            }
                            showDownloadDialog = true
                        }

                        loadUrl(currentUrl)
                        webViewRef = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { webView ->
                    // Make sure webView updates with outer states if needed
                }
            )
        }

        // Advanced Custom Download Dialogue (التحكم في صيغ الملفات وإدارة التنزيلات)
        if (showDownloadDialog) {
            var customTitle by remember { mutableStateOf(pageTitle.take(30)) }
            var selectedFormat by remember { mutableStateOf("HTML_SCRAPE") } // "HTML_SCRAPE", "DIRECT_ZIP", "FLASH_SWF", "RESUMABLE_VIDEO", "RESUMABLE_FILE"
            var remoteUrlInput by remember { mutableStateOf(currentUrl) }

            // Auto update inputs if anything detected
            LaunchedEffect(detectedSwfUrl, detectedZipUrl, detectedVideoUrl, detectedFileUrl) {
                if (detectedVideoUrl != null) {
                    selectedFormat = "RESUMABLE_VIDEO"
                    remoteUrlInput = detectedVideoUrl!!
                } else if (detectedFileUrl != null) {
                    selectedFormat = "RESUMABLE_FILE"
                    remoteUrlInput = detectedFileUrl!!
                } else if (detectedSwfUrl != null) {
                    selectedFormat = "FLASH_SWF"
                    remoteUrlInput = detectedSwfUrl!!
                } else if (detectedZipUrl != null) {
                    selectedFormat = "DIRECT_ZIP"
                    remoteUrlInput = detectedZipUrl!!
                }
            }

            AlertDialog(
                onDismissRequest = { showDownloadDialog = false },
                title = { Text("Universal Downloader Wizard", fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Download videos, games, and files with HTTP Range resume support:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        OutlinedTextField(
                            value = customTitle,
                            onValueChange = { customTitle = it },
                            label = { Text("Title / Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = remoteUrlInput,
                            onValueChange = { remoteUrlInput = it },
                            label = { Text("Source Download URL") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                        )

                        Text("Download Format & Engine (صيغة الملف):", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Video / Audio (Resumable)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedFormat = "RESUMABLE_VIDEO" }
                                    .padding(vertical = 2.dp)
                            ) {
                                RadioButton(selected = selectedFormat == "RESUMABLE_VIDEO", onClick = { selectedFormat = "RESUMABLE_VIDEO" })
                                Column(modifier = Modifier.padding(start = 8.dp)) {
                                    Text("Resumable Video / Audio Media", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Supports HTTP Range resume for MP4, WEBM, MKV, MP3 without corruption", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            // All Files & Documents (Resumable)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedFormat = "RESUMABLE_FILE" }
                                    .padding(vertical = 2.dp)
                            ) {
                                RadioButton(selected = selectedFormat == "RESUMABLE_FILE", onClick = { selectedFormat = "RESUMABLE_FILE" })
                                Column(modifier = Modifier.padding(start = 8.dp)) {
                                    Text("Resumable File / Package", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Downloads APK, PDF, RAR, 7Z, and general files with resume support", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            // HTML Scraper
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedFormat = "HTML_SCRAPE" }
                                    .padding(vertical = 2.dp)
                            ) {
                                RadioButton(selected = selectedFormat == "HTML_SCRAPE", onClick = { selectedFormat = "HTML_SCRAPE" })
                                Column(modifier = Modifier.padding(start = 8.dp)) {
                                    Text("HTML Webpage Game Scraper (.zip)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Crawls and packages all web assets into a playable ZIP", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            // Direct ZIP
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedFormat = "DIRECT_ZIP" }
                                    .padding(vertical = 2.dp)
                            ) {
                                RadioButton(selected = selectedFormat == "DIRECT_ZIP", onClick = { selectedFormat = "DIRECT_ZIP" })
                                Column(modifier = Modifier.padding(start = 8.dp)) {
                                    Text("Direct ZIP Game Installer (.zip)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Downloads offline pre-packaged game ZIP directly", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            // Flash SWF
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedFormat = "FLASH_SWF" }
                                    .padding(vertical = 2.dp)
                            ) {
                                RadioButton(selected = selectedFormat == "FLASH_SWF", onClick = { selectedFormat = "FLASH_SWF" })
                                Column(modifier = Modifier.padding(start = 8.dp)) {
                                    Text("Flash Game SWF File (.swf)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Downloads SWF to run with embedded Ruffle emulator", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showDownloadDialog = false
                            val finalTitle = customTitle.ifEmpty { "Downloaded File" }
                            when (selectedFormat) {
                                "RESUMABLE_VIDEO" -> viewModel.downloadResumableMedia(context, remoteUrlInput, finalTitle, "VIDEO")
                                "RESUMABLE_FILE" -> viewModel.downloadResumableMedia(context, remoteUrlInput, finalTitle, "FILE")
                                "HTML_SCRAPE" -> viewModel.downloadHtmlGame(context, remoteUrlInput, finalTitle)
                                "DIRECT_ZIP" -> viewModel.downloadZipGame(context, remoteUrlInput, finalTitle)
                                "FLASH_SWF" -> viewModel.downloadFlashGame(context, remoteUrlInput, finalTitle)
                            }
                        }
                    ) {
                        Text("Start Download")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDownloadDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

// Helpers
private fun formatUrl(input: String): String {
    val trimmed = input.trim()
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        return trimmed
    }
    // Simple Google search query fallback
    if (trimmed.contains(" ") || !trimmed.contains(".")) {
        val query = trimmed.replace(" ", "+")
        return "https://www.google.com/search?q=$query"
    }
    return "https://$trimmed"
}
