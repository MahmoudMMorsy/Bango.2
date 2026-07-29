package com.example.ui

import android.content.Context
import android.view.ViewGroup
import android.webkit.*
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
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
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.screens.applyProfileToWebView
import com.example.ui.screens.generateAntiFingerprintScript
import kotlinx.coroutines.launch

data class MediaLink(
    val url: String,
    val type: String, // "VIDEO", "AUDIO", "FILE", "SWF", "ZIP"
    val resolution: String,
    val title: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    viewModel: GameViewModel,
    mainViewModel: MainViewModel,
    modifier: Modifier = Modifier,
    onWebViewCreated: (WebView) -> Unit = {}
) {
    val context = LocalContext.current
    val currentUrl by viewModel.currentUrl.collectAsState()
    val isBookmarked by viewModel.isCurrentPageBookmarked.collectAsState()
    val searchInput by viewModel.searchInput.collectAsState()
    val activeProfile by mainViewModel.defaultProfile.collectAsState()

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var pageTitle by remember { mutableStateOf("Game Web Browser") }
    var loadProgress by remember { mutableStateOf(0) }
    var isPageLoading by remember { mutableStateOf(false) }

    // SnapTube Media Sniffer auto detection status
    val detectedMediaList = remember { mutableStateListOf<MediaLink>() }
    var showFloatingIdmBanner by remember { mutableStateOf(true) }

    // Download dialog visibility
    var showDownloadDialog by remember { mutableStateOf(false) }

    // Overlays for downloads and bookmarks
    var showDownloadsOverlay by remember { mutableStateOf(false) }
    var showSavedOverlay by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        // Active search automation status banner if active
        if (mainViewModel.isAutomationRunning) {
            Surface(
                color = Color(0xFF1E6B38).copy(alpha = 0.95f),
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Active", tint = Color(0xFF4CAF50))
                            Text(
                                text = "أتمتة وتصفح نشط آلياً 🤖",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 13.sp
                            )
                        }

                        Button(
                            onClick = { mainViewModel.stopLiveAutomation() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text("إيقاف", fontSize = 11.sp, color = Color.White)
                        }
                    }

                    Text(
                        text = "الاستعلام النشط: ${mainViewModel.activeAutomationQueryText}",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Address & Toolbar
        Surface(
            tonalElevation = 8.dp,
            shadowElevation = 4.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.statusBarsPadding()
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
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
                                val homeUrl = "https://www.bing.com"
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

                        // Downloads overlay quick-access button
                        IconButton(
                            onClick = { showDownloadsOverlay = true }
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Downloads list", tint = MaterialTheme.colorScheme.primary)
                        }

                        // Saved overlay quick-access button
                        IconButton(
                            onClick = { showSavedOverlay = true }
                        ) {
                            Icon(Icons.Default.Bookmark, contentDescription = "Saved offline", tint = MaterialTheme.colorScheme.secondary)
                        }

                        // Indicators for auto detected files
                        val videoCount = detectedMediaList.count { it.type == "VIDEO" }
                        val audioCount = detectedMediaList.count { it.type == "AUDIO" }
                        val otherCount = detectedMediaList.count { it.type != "VIDEO" && it.type != "AUDIO" }

                        if (videoCount > 0) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .clickable { showDownloadDialog = true }
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                                    Icon(Icons.Default.Videocam, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("$videoCount Video${if (videoCount > 1) "s" else ""} Detected", color = Color.White, fontSize = 10.sp)
                                }
                            }
                        }
                        if (audioCount > 0) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .clickable { showDownloadDialog = true }
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("$audioCount Audio Detected", color = Color.White, fontSize = 10.sp)
                                }
                            }
                        }
                        if (otherCount > 0) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .clickable { showDownloadDialog = true }
                            ) {
                                Text("$otherCount File(s) Detected", color = Color.White, modifier = Modifier.padding(4.dp), fontSize = 10.sp)
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
                            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                val url = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)
                                val lower = url.lowercase()

                                // Advanced Network Sniffing for Media Streams
                                val isVideo = lower.contains(".m3u8") || lower.contains(".mp4") || lower.contains(".webm") || lower.contains("videoplayback") || lower.contains("video/")
                                val isAudio = lower.contains(".mp3") || lower.contains(".m4a") || lower.contains(".aac") || lower.contains("audioplayback") || lower.contains("audio/")

                                if (isVideo) {
                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        if (detectedMediaList.none { it.url == url }) {
                                            val res = if (lower.contains("1080")) "1080p HD" else if (lower.contains("720")) "720p HD" else if (lower.contains("480")) "480p" else if (lower.contains("360")) "360p" else if (lower.contains(".m3u8")) "m3u8 Playlist (Adaptive)" else "Video Stream"
                                            detectedMediaList.add(MediaLink(url, "VIDEO", res, pageTitle))
                                        }
                                    }
                                } else if (isAudio) {
                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        if (detectedMediaList.none { it.url == url }) {
                                            val res = if (lower.contains("320") || lower.contains("high")) "320 kbps (High)" else if (lower.contains("128")) "128 kbps" else "Audio Track"
                                            detectedMediaList.add(MediaLink(url, "AUDIO", res, pageTitle))
                                        }
                                    }
                                }

                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isPageLoading = true
                                detectedMediaList.clear()
                                showFloatingIdmBanner = true
                                url?.let {
                                    viewModel.updateUrl(it)
                                }
                                // Inject fingerprint protection script
                                val js = generateAntiFingerprintScript(activeProfile)
                                view?.evaluateJavascript(js, null)
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
                                // Inject fingerprint protection script
                                val js = generateAntiFingerprintScript(activeProfile)
                                view?.evaluateJavascript(js, null)

                                // Inject XHR/Fetch Interceptor and DOM Sniffer
                                view?.evaluateJavascript(
                                    """
                                    (function() {
                                        // 1. DOM Sniffing
                                        var res = { swf: null, video: null, audio: null };
                                        var videos = document.getElementsByTagName('video');
                                        for(var i=0; i<videos.length; i++) {
                                            var v = videos[i];
                                            var vsrc = v.currentSrc || v.src;
                                            if(vsrc && !vsrc.startsWith('blob:')) res.video = vsrc;
                                            var sources = v.getElementsByTagName('source');
                                            if(sources.length > 0 && sources[0].src && !sources[0].src.startsWith('blob:')) res.video = sources[0].src;
                                        }
                                        var audios = document.getElementsByTagName('audio');
                                        for(var j=0; j<audios.length; j++) {
                                            var a = audios[j];
                                            var asrc = a.currentSrc || a.src;
                                            if(asrc && !asrc.startsWith('blob:')) res.audio = asrc;
                                        }
                                        var embeds = document.getElementsByTagName('embed');
                                        for(var k=0; k<embeds.length; k++) {
                                            if(embeds[k].src && embeds[k].src.includes('.swf')) res.swf = embeds[k].src;
                                        }

                                        // 2. Fetch/XHR Hooking (Optional advanced sniffing)
                                        if(!window.AndroidSnifferInjected) {
                                            window.AndroidSnifferInjected = true;
                                            var origOpen = XMLHttpRequest.prototype.open;
                                            XMLHttpRequest.prototype.open = function(method, url) {
                                                if(typeof url === 'string') {
                                                    var l = url.toLowerCase();
                                                    if(l.includes('.m3u8') || l.includes('.mp4') || l.includes('videoplayback')) {
                                                        if (window.AndroidMedia) window.AndroidMedia.notifyMedia(url, 'video');
                                                    }
                                                }
                                                return origOpen.apply(this, arguments);
                                            };
                                        }
                                        return JSON.stringify(res);
                                    })()
                                    """.trimIndent()
                                ) { result ->
                                    if (result != "null" && !result.isNullOrEmpty()) {
                                        try {
                                            if (result.contains("\"video\":") && !result.contains("\"video\":null")) {
                                                val vid = result.substringAfter("\"video\":\"").substringBefore("\"")
                                                if (vid.isNotEmpty() && vid != "null" && detectedMediaList.none { it.url == vid }) {
                                                    detectedMediaList.add(MediaLink(vid, "VIDEO", "Video Stream", pageTitle))
                                                }
                                            }
                                            if (result.contains("\"audio\":") && !result.contains("\"audio\":null")) {
                                                val aud = result.substringAfter("\"audio\":\"").substringBefore("\"")
                                                if (aud.isNotEmpty() && aud != "null" && detectedMediaList.none { it.url == aud }) {
                                                    detectedMediaList.add(MediaLink(aud, "AUDIO", "Audio Track", pageTitle))
                                                }
                                            }
                                            if (result.contains("\"swf\":") && !result.contains("\"swf\":null")) {
                                                val swf = result.substringAfter("\"swf\":\"").substringBefore("\"")
                                                if (swf.isNotEmpty() && swf != "null" && detectedMediaList.none { it.url == swf }) {
                                                    detectedMediaList.add(MediaLink(swf, "SWF", "Flash Game", pageTitle))
                                                }
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

                        // Apply security and feature flags with antidetect profile
                        applyProfileToWebView(this, activeProfile, false)

                        addJavascriptInterface(object {
                            @JavascriptInterface
                            fun notifyMedia(url: String, type: String) {
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    if (detectedMediaList.none { it.url == url }) {
                                        val res = if (type == "video") "Video Stream" else "Audio Track"
                                        detectedMediaList.add(MediaLink(url, type.uppercase(), res, pageTitle))
                                    }
                                }
                            }
                        }, "AndroidMedia")

                        setDownloadListener { url, _, _, _, _ ->
                            val lower = url.lowercase()
                            if (lower.endsWith(".swf") && detectedMediaList.none { it.url == url }) {
                                detectedMediaList.add(MediaLink(url, "SWF", "Flash Game", pageTitle))
                            } else if (lower.endsWith(".zip") && detectedMediaList.none { it.url == url }) {
                                detectedMediaList.add(MediaLink(url, "ZIP", "Archive", pageTitle))
                            } else if (lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".mkv") || lower.endsWith(".avi") || lower.endsWith(".mov") || lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".aac")) {
                                if ((lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".aac")) && detectedMediaList.none { it.url == url }) {
                                    detectedMediaList.add(MediaLink(url, "AUDIO", "Audio Track", pageTitle))
                                } else if (detectedMediaList.none { it.url == url }) {
                                    detectedMediaList.add(MediaLink(url, "VIDEO", "Video Stream", pageTitle))
                                }
                            } else if (detectedMediaList.none { it.url == url }) {
                                detectedMediaList.add(MediaLink(url, "FILE", "File", pageTitle))
                            }
                            showDownloadDialog = true
                        }

                        loadUrl(currentUrl)
                        webViewRef = this
                        onWebViewCreated(this)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { webView ->
                    // Make sure webView updates with outer states if needed
                }
            )

            // SnapTube Floating Red Download Button Widget
            val hasMediaDetected = detectedMediaList.isNotEmpty()

            if (hasMediaDetected && showFloatingIdmBanner) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = true,
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 80.dp, end = 16.dp)
                ) {
                    Surface(
                        color = Color(0xFFE53935), // SnapTube Red
                        shape = RoundedCornerShape(28.dp),
                        shadowElevation = 10.dp,
                        modifier = Modifier.clickable { showDownloadDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "SnapTube Download",
                                    tint = Color(0xFFE53935),
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "SnapTube Downloader",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = Color(0xFFFFD600), // SnapTube Yellow
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = detectedMediaList.first().type,
                                            color = Color.Black,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "تم العثور على ${detectedMediaList.size} ملف! إضغط للتحميل",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }

                            IconButton(
                                onClick = { showFloatingIdmBanner = false },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // SnapTube Smart Media Downloader Dialogue (خيارات وإقتراحات التحميل)
        if (showDownloadDialog) {
            var customTitle by remember { mutableStateOf(pageTitle.take(30)) }
            var selectedMediaUrl by remember { mutableStateOf(detectedMediaList.firstOrNull()?.url ?: "") }
            var selectedThreads by remember { mutableStateOf(8) } // 4, 8, 16 parallel threads

            AlertDialog(
                onDismissRequest = { showDownloadDialog = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = Color(0xFFE53935),
                            shape = CircleShape,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                        Column {
                            Text("SnapTube Media Downloader", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("تم اكتشاف ملفات وسائط متعددة", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                text = {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            OutlinedTextField(
                                value = customTitle,
                                onValueChange = { customTitle = it },
                                label = { Text("اسم الملف / العنوان (Title)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Parallel Threads Selector
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("سرعة التنزيل وتسريع الخيوط (Turbo Accelerator):", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    listOf(4, 8, 16).forEach { threads ->
                                        FilterChip(
                                            selected = selectedThreads == threads,
                                            onClick = { selectedThreads = threads },
                                            label = { Text("$threads Streams (سريع)", fontSize = 11.sp) },
                                            leadingIcon = if (selectedThreads == threads) {
                                                { Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                            } else null
                                        )
                                    }
                                }
                            }
                        }

                        val videos = detectedMediaList.filter { it.type == "VIDEO" }
                        val audios = detectedMediaList.filter { it.type == "AUDIO" }
                        val others = detectedMediaList.filter { it.type != "VIDEO" && it.type != "AUDIO" }

                        if (videos.isNotEmpty()) {
                            item {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Videocam, contentDescription = null, tint = Color(0xFFE53935), modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("🎬 فيديوهات (Videos)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }
                            items(videos) { media ->
                                SnapTubeOptionCard(
                                    title = media.title,
                                    subtitle = media.url,
                                    badge = media.resolution,
                                    isSelected = selectedMediaUrl == media.url,
                                    onClick = { selectedMediaUrl = media.url }
                                )
                            }
                        }

                        if (audios.isNotEmpty()) {
                            item {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color(0xFFE53935), modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("🎵 صوتيات (Audio)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }
                            items(audios) { media ->
                                SnapTubeOptionCard(
                                    title = media.title,
                                    subtitle = media.url,
                                    badge = media.resolution,
                                    isSelected = selectedMediaUrl == media.url,
                                    onClick = { selectedMediaUrl = media.url }
                                )
                            }
                        }

                        if (others.isNotEmpty()) {
                            item {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("📦 ملفات أخرى (Files & Games)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }
                            items(others) { media ->
                                SnapTubeOptionCard(
                                    title = media.title,
                                    subtitle = media.url,
                                    badge = media.resolution,
                                    isSelected = selectedMediaUrl == media.url,
                                    onClick = { selectedMediaUrl = media.url }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                        onClick = {
                            if (selectedMediaUrl.isEmpty()) {
                                Toast.makeText(context, "الرجاء تحديد ملف للتحميل", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            showDownloadDialog = false
                            val finalTitle = customTitle.ifEmpty { "Downloaded Media" }
                            val cookies = try { android.webkit.CookieManager.getInstance().getCookie(selectedMediaUrl) } catch(e: Exception) { null }
                            val ua = webViewRef?.settings?.userAgentString
                            val referer = currentUrl

                            val media = detectedMediaList.find { it.url == selectedMediaUrl } ?: return@Button

                            if (media.type == "VIDEO" || media.type == "AUDIO") {
                                viewModel.downloadResumableMedia(
                                    context = context,
                                    url = selectedMediaUrl,
                                    title = "$finalTitle (${media.resolution})",
                                    category = media.type,
                                    numSegments = selectedThreads,
                                    userAgent = ua,
                                    cookies = cookies,
                                    referer = referer
                                )
                            } else if (media.type == "SWF") {
                                viewModel.downloadFlashGame(context, selectedMediaUrl, finalTitle)
                            } else if (media.type == "ZIP") {
                                viewModel.downloadZipGame(context, selectedMediaUrl, finalTitle)
                            } else {
                                viewModel.downloadViaSystemManager(
                                    context = context,
                                    url = selectedMediaUrl,
                                    title = finalTitle,
                                    userAgent = ua,
                                    cookies = cookies,
                                    referer = referer
                                )
                            }
                        }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("⚡ تحميل الآن (Start Download)", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDownloadDialog = false }) {
                        Text("إلغاء")
                    }
                }
            )
        }
    }

    // Overlays of Downloads and Bookmarks
    if (showDownloadsOverlay) {
        AlertDialog(
            onDismissRequest = { showDownloadsOverlay = false },
            title = { Text("سجل التنزيلات والوسائط 📥", fontWeight = FontWeight.Bold) },
            text = {
                Box(modifier = Modifier.fillMaxWidth().height(450.dp)) {
                    DownloadsScreen(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showDownloadsOverlay = false }) {
                    Text("إغلاق", color = Color(0xFF4CAF50))
                }
            }
        )
    }

    if (showSavedOverlay) {
        AlertDialog(
            onDismissRequest = { showSavedOverlay = false },
            title = { Text("المفضلة والألعاب المحفوظة 🔖", fontWeight = FontWeight.Bold) },
            text = {
                Box(modifier = Modifier.fillMaxWidth().height(450.dp)) {
                    SavedScreen(
                        viewModel = viewModel,
                        onNavigateToUrl = { url ->
                            viewModel.updateUrl(url)
                            webViewRef?.loadUrl(url)
                            showSavedOverlay = false
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSavedOverlay = false }) {
                    Text("إغلاق", color = Color(0xFF4CAF50))
                }
            }
        )
    }

    val playingGame by viewModel.playingGame.collectAsState()

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

@Composable
private fun SnapTubeOptionCard(
    title: String,
    subtitle: String,
    badge: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) Color(0xFFFFEBEE) else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) Color(0xFFE53935) else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick = onClick,
                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFE53935))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (isSelected) Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Surface(
                color = if (isSelected) Color(0xFFE53935) else MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = badge,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

// Helpers
private fun formatUrl(input: String): String {
    val trimmed = input.trim()
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        return trimmed
    }
    // Bing search query fallback
    if (trimmed.contains(" ") || !trimmed.contains(".")) {
        val query = trimmed.replace(" ", "+")
        return "https://www.bing.com/search?q=$query"
    }
    return "https://$trimmed"
}
