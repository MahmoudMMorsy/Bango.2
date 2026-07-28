package com.example.ui.screens

import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.ValueCallback
import android.webkit.JsResult
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import android.graphics.Bitmap
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AutomationLog
import com.example.data.model.BrowserProfile
import com.example.data.model.ProxyConfig
import com.example.data.model.SearchQuery
import com.example.data.model.WeeklyTopic
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SecondaryBlue
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceDark
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val activeTopic by viewModel.activeTopic.collectAsStateWithLifecycle()
    val queries by viewModel.activeTopicQueries.collectAsStateWithLifecycle()
    val proxies by viewModel.proxies.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val terminalLogs by viewModel.rollingTerminalLogs.collectAsStateWithLifecycle()
    val defaultProfile by viewModel.defaultProfile.collectAsStateWithLifecycle()

    var activeWebViewReference by remember { mutableStateOf<WebView?>(null) }

    // Enforce RTL Layout Direction for beautiful Arabic-first design
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        if (viewModel.isSplashActive) {
            BingSplashScreen()
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Miniature Glowing Bango Logo Badge
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(Color(0xFF1E6B38), Color(0xFF4CAF50))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "B",
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.offset(y = (-1).dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "برنامج بانجو الآلي v2",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "متصفح التخفي المطور • أمن وحماية الهوية 🛡️",
                                        fontSize = 10.sp,
                                        color = Color.LightGray.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        },
                        actions = {
                            Box(
                                modifier = Modifier
                                    .padding(end = 16.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (viewModel.isAutomationRunning) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFF1E2F4C))
                                    .border(1.dp, if (viewModel.isAutomationRunning) Color(0xFF10B981) else Color.Transparent, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                                    val alpha by infiniteTransition.animateFloat(
                                        initialValue = 0.3f,
                                        targetValue = 1f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(1000, easing = LinearEasing),
                                            repeatMode = RepeatMode.Reverse
                                        ),
                                        label = "pulse"
                                    )

                                    if (viewModel.isAutomationRunning) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .drawBehind {
                                                    drawCircle(
                                                        color = Color(0xFF10B981),
                                                        alpha = alpha
                                                    )
                                                }
                                        )
                                        Text("أتمتة نشطة حية", fontSize = 11.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(Color.Gray)
                                        )
                                        Text("مستعد للخمول", fontSize = 11.sp, color = Color.Gray)
                                    }
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = SurfaceDark
                        )
                    )
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = SurfaceDark,
                        tonalElevation = 8.dp
                    ) {
                        NavigationBarItem(
                            selected = viewModel.selectedTab == 0,
                            onClick = { viewModel.selectedTab = 0 },
                            icon = { Icon(Icons.Default.Home, contentDescription = "الرئيسية") },
                            label = { Text("بينج اليوم") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF4CAF50),
                                selectedTextColor = Color(0xFF4CAF50),
                                indicatorColor = Color(0xFF1E6B38).copy(alpha = 0.25f),
                                unselectedIconColor = Color.LightGray,
                                unselectedTextColor = Color.LightGray
                            )
                        )
                        NavigationBarItem(
                            selected = viewModel.selectedTab == 1,
                            onClick = { viewModel.selectedTab = 1 },
                            icon = { Icon(Icons.Default.PlayArrow, contentDescription = "المتصفح") },
                            label = { Text("المتصفح") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF4CAF50),
                                selectedTextColor = Color(0xFF4CAF50),
                                indicatorColor = Color(0xFF1E6B38).copy(alpha = 0.25f),
                                unselectedIconColor = Color.LightGray,
                                unselectedTextColor = Color.LightGray
                            )
                        )
                        NavigationBarItem(
                            selected = viewModel.selectedTab == 2,
                            onClick = { viewModel.selectedTab = 2 },
                            icon = { Icon(Icons.Default.Build, contentDescription = "لوحة الأوامر") },
                            label = { Text("لوحة الأوامر") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF4CAF50),
                                selectedTextColor = Color(0xFF4CAF50),
                                indicatorColor = Color(0xFF1E6B38).copy(alpha = 0.25f),
                                unselectedIconColor = Color.LightGray,
                                unselectedTextColor = Color.LightGray
                            )
                        )
                        NavigationBarItem(
                            selected = viewModel.selectedTab == 3,
                            onClick = { viewModel.selectedTab = 3 },
                            icon = { Icon(Icons.Default.Lock, contentDescription = "خوادم الوكيل") },
                            label = { Text("الشبكة") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF4CAF50),
                                selectedTextColor = Color(0xFF4CAF50),
                                indicatorColor = Color(0xFF1E6B38).copy(alpha = 0.25f),
                                unselectedIconColor = Color.LightGray,
                                unselectedTextColor = Color.LightGray
                            )
                        )
                        NavigationBarItem(
                            selected = viewModel.selectedTab == 4,
                            onClick = { viewModel.selectedTab = 4 },
                            icon = { Icon(Icons.Default.Person, contentDescription = "البروفايلات") },
                            label = { Text("الهويات") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF4CAF50),
                                selectedTextColor = Color(0xFF4CAF50),
                                indicatorColor = Color(0xFF1E6B38).copy(alpha = 0.25f),
                                unselectedIconColor = Color.LightGray,
                                unselectedTextColor = Color.LightGray
                            )
                        )
                    }
                },
                containerColor = BackgroundDark,
                modifier = modifier
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    when (viewModel.selectedTab) {
                        0 -> DashboardTab(
                            viewModel = viewModel,
                            activeTopic = activeTopic,
                            queries = queries,
                            onStartAutopilot = {
                                viewModel.selectedTab = 1
                                viewModel.startLiveAutomation { url, ua ->
                                    activeWebViewReference?.let { webView ->
                                        try {
                                            webView.settings.userAgentString = ua
                                            webView.loadUrl(url)
                                            // Trigger human scroll action inside WebView
                                            coroutineScope.launch {
                                                try {
                                                    delay(1500)
                                                    webView.evaluateJavascript("window.scrollBy({ top: 350, behavior: 'smooth' });", null)
                                                } catch (t: Throwable) {
                                                    t.printStackTrace()
                                                }
                                            }
                                        } catch (t: Throwable) {
                                            t.printStackTrace()
                                        }
                                    }
                                }
                            }
                        )
                        1 -> AutopilotTab(
                            viewModel = viewModel,
                            defaultProfile = defaultProfile,
                            onWebViewCreated = { webView ->
                                activeWebViewReference = webView
                            }
                        )
                        2 -> TerminalTab(
                            viewModel = viewModel,
                            terminalLogs = terminalLogs,
                            logs = logs
                        )
                        3 -> ProxiesTab(
                            viewModel = viewModel,
                            proxies = proxies
                        )
                        4 -> ProfilesTab(
                            viewModel = viewModel
                        )
                    }
                }
            }
            if (viewModel.isChromeBrowserOpen) {
                ChromeBrowserView(viewModel = viewModel)
            }
            } // end of Box wrapping Scaffold
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChromeBrowserView(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val profile = viewModel.activeChromeProfile ?: return
    val tabs = viewModel.chromeTabsPerProfile[profile.id] ?: remember { mutableStateListOf() }
    val activeTabIdx = viewModel.activeTabIdxPerProfile[profile.id] ?: 0
    val activeTab = if (activeTabIdx in tabs.indices) tabs[activeTabIdx] else null

    var menuExpanded by remember { mutableStateOf(false) }
    var addressBarInput by remember(activeTab?.url) { mutableStateOf(activeTab?.url ?: "") }
    var showBookmarksDialog by remember { mutableStateOf(false) }

    // File choosing launcher
    var uploadMessageCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data?.data
            val uris = if (data != null) arrayOf(data) else null
            uploadMessageCallback?.onReceiveValue(uris)
        } else {
            uploadMessageCallback?.onReceiveValue(null)
        }
        uploadMessageCallback = null
    }

    // JS alerts / confirm dialogs
    var jsAlertMessage by remember { mutableStateOf<String?>(null) }
    var jsAlertResult by remember { mutableStateOf<JsResult?>(null) }
    var jsConfirmMessage by remember { mutableStateOf<String?>(null) }
    var jsConfirmResult by remember { mutableStateOf<JsResult?>(null) }

    // Desktop Site effect
    LaunchedEffect(activeTab, activeTab?.isDesktopMode) {
        activeTab?.let { tab ->
            tab.webView?.let { webView ->
                val userAgent = if (tab.isDesktopMode) {
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                } else {
                    profile.userAgent
                }
                webView.settings.userAgentString = userAgent
                webView.settings.useWideViewPort = tab.isDesktopMode
                webView.settings.loadWithOverviewMode = tab.isDesktopMode
                webView.reload()
            }
        }
    }

    // Bookmarks Dialog
    if (showBookmarksDialog) {
        AlertDialog(
            onDismissRequest = { showBookmarksDialog = false },
            title = { Text("الإشارات المرجعية 🔖", fontWeight = FontWeight.Bold, color = Color.White) },
            containerColor = SurfaceDark,
            confirmButton = {
                TextButton(onClick = { showBookmarksDialog = false }) {
                    Text("إغلاق", color = Color(0xFF4CAF50))
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (viewModel.bookmarks.isEmpty()) {
                        Text("لا توجد إشارات مرجعية حالياً.", color = Color.Gray, fontSize = 12.sp)
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(viewModel.bookmarks.toList()) { b ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(BackgroundDark)
                                        .clickable {
                                            showBookmarksDialog = false
                                            activeTab?.let { tab ->
                                                tab.url = b.second
                                                tab.webView?.loadUrl(b.second)
                                            }
                                        }
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(b.first, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(b.second, color = Color.Gray, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    IconButton(onClick = { viewModel.removeBookmark(b.second) }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Delete, contentDescription = "مسح", tint = Color.Red.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    // JS Alert Dialog
    if (jsAlertMessage != null) {
        AlertDialog(
            onDismissRequest = {
                jsAlertResult?.cancel()
                jsAlertMessage = null
                jsAlertResult = null
            },
            title = { Text("تنبيه الصفحة 🌐", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text(jsAlertMessage ?: "", color = Color.LightGray) },
            containerColor = SurfaceDark,
            confirmButton = {
                Button(
                    onClick = {
                        jsAlertResult?.confirm()
                        jsAlertMessage = null
                        jsAlertResult = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Text("موافق")
                }
            }
        )
    }

    // JS Confirm Dialog
    if (jsConfirmMessage != null) {
        AlertDialog(
            onDismissRequest = {
                jsConfirmResult?.cancel()
                jsConfirmMessage = null
                jsConfirmResult = null
            },
            title = { Text("تأكيد من الصفحة 🌐", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text(jsConfirmMessage ?: "", color = Color.LightGray) },
            containerColor = SurfaceDark,
            confirmButton = {
                Button(
                    onClick = {
                        jsConfirmResult?.confirm()
                        jsConfirmMessage = null
                        jsConfirmResult = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Text("موافق")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        jsConfirmResult?.cancel()
                        jsConfirmMessage = null
                        jsConfirmResult = null
                    }
                ) {
                    Text("إلغاء", color = Color.Gray)
                }
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .clickable(enabled = false) {}
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // --- Chrome Streamlined SINGLE-ROW Header Bar ---
            Surface(
                color = SurfaceDark,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // 1. Back button
                        IconButton(
                            onClick = { activeTab?.webView?.goBack() },
                            enabled = activeTab?.canGoBack == true,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = if (activeTab?.canGoBack == true) Color.White else Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // 2. Forward button
                        IconButton(
                            onClick = { activeTab?.webView?.goForward() },
                            enabled = activeTab?.canGoForward == true,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = "Forward",
                                tint = if (activeTab?.canGoForward == true) Color.White else Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // 3. Home button
                        IconButton(
                            onClick = {
                                activeTab?.let {
                                    it.url = ""
                                    it.title = "علامة تبويب جديدة"
                                    it.webView?.loadUrl("about:blank")
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = "Home",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // 4. Address Bar with Lock, Input and Reload (Weight 1f)
                        val isHttps = addressBarInput.startsWith("https")
                        val isHttp = addressBarInput.startsWith("http:")
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(BackgroundDark)
                                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(18.dp))
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isHttps) Icons.Default.Lock else if (isHttp) Icons.Default.Warning else Icons.Default.Info,
                                    contentDescription = "Security Status",
                                    tint = if (isHttps) Color(0xFF10B981) else if (isHttp) Color(0xFFFBBF24) else Color.Gray,
                                    modifier = Modifier.size(14.dp)
                                )

                                Box(modifier = Modifier.weight(1f)) {
                                    if (addressBarInput.isEmpty()) {
                                        Text(
                                            text = "ابحث أو أدخل عنوان ويب...",
                                            fontSize = 11.sp,
                                            color = Color.Gray,
                                            maxLines = 1
                                        )
                                    }
                                    androidx.compose.foundation.text.BasicTextField(
                                        value = addressBarInput,
                                        onValueChange = { addressBarInput = it },
                                        textStyle = androidx.compose.ui.text.TextStyle(
                                            color = Color.White,
                                            fontSize = 12.sp
                                        ),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = androidx.compose.ui.text.input.ImeAction.Go),
                                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                            onGo = {
                                                activeTab?.let { tab ->
                                                    var target = addressBarInput.trim()
                                                    if (target.isNotEmpty()) {
                                                        if (!target.startsWith("http://") && !target.startsWith("https://")) {
                                                            target = if (target.contains(".") && !target.contains(" ")) {
                                                                "https://$target"
                                                            } else {
                                                                "https://www.bing.com/search?q=" + java.net.URLEncoder.encode(target, "UTF-8")
                                                            }
                                                        }
                                                        tab.url = target
                                                        tab.webView?.loadUrl(target)
                                                    }
                                                }
                                            }
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                if (addressBarInput.isNotEmpty()) {
                                    IconButton(
                                        onClick = { addressBarInput = "" },
                                        modifier = Modifier.size(16.dp)
                                    ) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(12.dp))
                                    }
                                }

                                IconButton(
                                    onClick = { activeTab?.webView?.reload() },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Reload",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        // 5. Tab switcher button
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .border(1.5.dp, Color.White, RoundedCornerShape(6.dp))
                                .clickable { viewModel.isTabSwitcherOpen = !viewModel.isTabSwitcherOpen },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${tabs.size}",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // 6. New tab '+' button
                        IconButton(
                            onClick = { viewModel.openNewTabForActiveChrome() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "New Tab", tint = Color.White, modifier = Modifier.size(20.dp))
                        }

                        // 7. ⋮ Menu
                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                                modifier = Modifier.background(SurfaceDark)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("علامة تبويب جديدة", color = Color.White) },
                                    onClick = {
                                        viewModel.openNewTabForActiveChrome()
                                        menuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("الإشارات المرجعية 🔖", color = Color.White) },
                                    onClick = {
                                        showBookmarksDialog = true
                                        menuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Checkbox(
                                                checked = activeTab?.isDesktopMode == true,
                                                onCheckedChange = { checked ->
                                                    activeTab?.let { it.isDesktopMode = checked }
                                                    menuExpanded = false
                                                },
                                                colors = CheckboxDefaults.colors(checkedColor = Color(0xFF4CAF50))
                                            )
                                            Text("موقع سطح المكتب 💻", color = Color.White)
                                        }
                                    },
                                    onClick = {
                                        activeTab?.let { it.isDesktopMode = !it.isDesktopMode }
                                        menuExpanded = false
                                    }
                                )
                                if (activeTab != null && activeTab.url.isNotEmpty() && activeTab.url != "about:blank") {
                                    DropdownMenuItem(
                                        text = { Text("إضافة للإشارات المرجعية ⭐", color = Color.White) },
                                        onClick = {
                                            viewModel.addBookmark(activeTab.title, activeTab.url)
                                            menuExpanded = false
                                        }
                                    )
                                }
                                if (viewModel.selectedVpnServerId == "vpn_eg" && viewModel.isBuiltInVpnEnabled) {
                                    DropdownMenuItem(
                                        text = { Text("تدوير عنوان IP المصري 🇪🇬", color = Color(0xFF10B981), fontWeight = FontWeight.Bold) },
                                        onClick = {
                                            viewModel.rotateEgyptProxy()
                                            menuExpanded = false
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (viewModel.isAutomationRunning) "إيقاف الأتمتة التلقائية 🛑" else "تشغيل الأتمتة التلقائية 🤖",
                                            color = if (viewModel.isAutomationRunning) Color.Yellow else Color(0xFF4CAF50),
                                            fontWeight = FontWeight.Bold
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        if (viewModel.isAutomationRunning) {
                                            viewModel.stopLiveAutomation()
                                        } else {
                                            viewModel.startLiveAutomation { url, ua ->
                                                // Handled internally!
                                            }
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("مسح ملفات الكوكيز بالكامل 🧹", color = Color.White) },
                                    onClick = {
                                        viewModel.clearAllCookiesForActiveProfile()
                                        menuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("إغلاق التبويب الحالي ✖", color = Color.White) },
                                    onClick = {
                                        activeTab?.let { viewModel.closeTabForActiveChrome(it.id) }
                                        menuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("إغلاق المتصفح المطور 🚪", color = Color.Red) },
                                    onClick = {
                                        viewModel.closeChromeBrowser()
                                        menuExpanded = false
                                    }
                                )
                            }
                        }

                        // 8. Close Browser button
                        IconButton(
                            onClick = { viewModel.closeChromeBrowser() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close Browser", tint = Color.LightGray, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (activeTab == null || activeTab.url.isEmpty() || activeTab.url == "about:blank") {
                    BangoNewTabPage(
                        viewModel = viewModel,
                        onNavigate = { targetUrl ->
                            activeTab?.let { tab ->
                                tab.url = targetUrl
                                tab.webView?.loadUrl(targetUrl)
                            }
                        }
                    )
                } else {
                    key(activeTab.id) {
                        AndroidView(
                            factory = { context ->
                                val webView = activeTab.webView ?: android.webkit.WebView(context).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.databaseEnabled = true
                                    settings.allowFileAccess = true
                                    settings.allowContentAccess = true
                                    settings.loadWithOverviewMode = activeTab.isDesktopMode
                                    settings.useWideViewPort = activeTab.isDesktopMode
                                    settings.javaScriptCanOpenWindowsAutomatically = true
                                    settings.mediaPlaybackRequiresUserGesture = false
                                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                                    webChromeClient = object : android.webkit.WebChromeClient() {
                                        override fun onProgressChanged(view: android.webkit.WebView?, newProgress: Int) {
                                            super.onProgressChanged(view, newProgress)
                                            activeTab.progress = newProgress
                                            activeTab.isLoading = newProgress < 100
                                        }

                                        override fun onShowFileChooser(
                                            webView: android.webkit.WebView?,
                                            filePathCallback: android.webkit.ValueCallback<Array<Uri>>?,
                                            fileChooserParams: android.webkit.WebChromeClient.FileChooserParams?
                                        ): Boolean {
                                            uploadMessageCallback?.onReceiveValue(null)
                                            uploadMessageCallback = filePathCallback
                                            val intent = fileChooserParams?.createIntent() ?: android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).apply {
                                                type = "*/*"
                                                addCategory(android.content.Intent.CATEGORY_OPENABLE)
                                            }
                                            try {
                                                fileChooserLauncher.launch(intent)
                                            } catch (e: Exception) {
                                                uploadMessageCallback?.onReceiveValue(null)
                                                uploadMessageCallback = null
                                                return false
                                            }
                                            return true
                                        }

                                        override fun onJsAlert(view: android.webkit.WebView?, url: String?, message: String?, result: android.webkit.JsResult?): Boolean {
                                            jsAlertMessage = message
                                            jsAlertResult = result
                                            return true
                                        }

                                        override fun onJsConfirm(view: android.webkit.WebView?, url: String?, message: String?, result: android.webkit.JsResult?): Boolean {
                                            jsConfirmMessage = message
                                            jsConfirmResult = result
                                            return true
                                        }
                                    }

                                    webViewClient = object : android.webkit.WebViewClient() {
                                        override fun onPageStarted(view: android.webkit.WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                            super.onPageStarted(view, url, favicon)
                                            activeTab.isLoading = true
                                            val js = generateAntiFingerprintScript(profile)
                                            view?.evaluateJavascript(js, null)
                                        }

                                        override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            activeTab.isLoading = false
                                            activeTab.canGoBack = view?.canGoBack() ?: false
                                            activeTab.canGoForward = view?.canGoForward() ?: false
                                            if (url != null && url != "about:blank") {
                                                activeTab.url = url
                                                activeTab.title = view?.title ?: "صفحة ويب"
                                            }
                                            val js = generateAntiFingerprintScript(profile)
                                            view?.evaluateJavascript(js, null)
                                        }

                                        override fun doUpdateVisitedHistory(view: android.webkit.WebView?, url: String?, isReload: Boolean) {
                                            super.doUpdateVisitedHistory(view, url, isReload)
                                            activeTab.canGoBack = view?.canGoBack() ?: false
                                            activeTab.canGoForward = view?.canGoForward() ?: false
                                            if (url != null && url != "about:blank") {
                                                activeTab.url = url
                                            }
                                        }

                                        override fun shouldOverrideUrlLoading(view: android.webkit.WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                                            val url = request?.url?.toString() ?: return false
                                            if (url.startsWith("http://") || url.startsWith("https://")) {
                                                return false
                                            }
                                            try {
                                                val intent = android.content.Intent.parseUri(url, android.content.Intent.URI_INTENT_SCHEME)
                                                val context = view?.context
                                                context?.startActivity(intent)
                                                return true
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                                return true
                                            }
                                        }
                                    }
                                    applyProfileToWebView(this, profile, activeTab.isDesktopMode)
                                    loadUrl(activeTab.url)
                                }
                                activeTab.webView = webView
                                webView
                            },
                            update = { webView ->
                                if (activeTab.webView != webView) {
                                    activeTab.webView = webView
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                if (activeTab?.isLoading == true) {
                    LinearProgressIndicator(
                        progress = activeTab.progress / 100f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.TopCenter),
                        color = Color(0xFF4CAF50),
                        trackColor = Color.Transparent
                    )
                }
            }
        }

        if (viewModel.isTabSwitcherOpen) {
            TabSwitcherOverlay(viewModel = viewModel)
        }
    }
}

@Composable
fun BangoNewTabPage(
    viewModel: MainViewModel,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQueryInput by remember { mutableStateOf("") }
    var showAddShortcutDialog by remember { mutableStateOf(false) }
    var shortcutTitleInput by remember { mutableStateOf("") }
    var shortcutUrlInput by remember { mutableStateOf("") }
    var shortcutEmojiInput by remember { mutableStateOf("") }

    if (showAddShortcutDialog) {
        AlertDialog(
            onDismissRequest = { showAddShortcutDialog = false },
            title = { Text("إضافة اختصار جديد 🔗", color = Color.White, fontWeight = FontWeight.Bold) },
            containerColor = SurfaceDark,
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addCustomShortcut(shortcutTitleInput, shortcutUrlInput, shortcutEmojiInput)
                        shortcutTitleInput = ""
                        shortcutUrlInput = ""
                        shortcutEmojiInput = ""
                        showAddShortcutDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    enabled = shortcutUrlInput.isNotBlank()
                ) {
                    Text("حفظ")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddShortcutDialog = false }) {
                    Text("إلغاء", color = Color.Gray)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = shortcutTitleInput,
                        onValueChange = { shortcutTitleInput = it },
                        label = { Text("عنوان الموقع", color = Color.LightGray) },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                    OutlinedTextField(
                        value = shortcutUrlInput,
                        onValueChange = { shortcutUrlInput = it },
                        label = { Text("رابط الموقع (URL)", color = Color.LightGray) },
                        placeholder = { Text("example.com") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                    OutlinedTextField(
                        value = shortcutEmojiInput,
                        onValueChange = { shortcutEmojiInput = it },
                        label = { Text("إيموجي كأيقونة (مثل: 📺, 💻)", color = Color.LightGray) },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                }
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF030A05),
                        Color(0xFF07140B),
                        Color(0xFF0A1C10)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF1E6B38), Color(0xFF4CAF50))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "B",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "تبويب مالي منعزل آمن ✨",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "تصفح آمن مجهول البصمات ومحمي بالكامل",
                    fontSize = 10.sp,
                    color = Color.LightGray.copy(alpha = 0.8f)
                )
            }

            // Central Search Bar
            OutlinedTextField(
                value = searchQueryInput,
                onValueChange = { searchQueryInput = it },
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .height(48.dp),
                placeholder = { Text("ابحث في الويب أو أدخل عنوان ويب...", color = Color.Gray, fontSize = 11.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "بحث", tint = Color.Gray, modifier = Modifier.size(16.dp)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSearch = {
                        if (searchQueryInput.isNotBlank()) {
                            var target = searchQueryInput.trim()
                            if (!target.startsWith("http://") && !target.startsWith("https://")) {
                                target = if (target.contains(".") && !target.contains(" ")) {
                                    "https://$target"
                                } else {
                                    "https://www.bing.com/search?q=" + java.net.URLEncoder.encode(target, "UTF-8")
                                }
                            }
                            onNavigate(target)
                        }
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceDark,
                    unfocusedContainerColor = SurfaceDark,
                    focusedBorderColor = Color(0xFF4CAF50),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(24.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "الوصول السريع للمواقع الأكثر شعبية:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50)
                )
                TextButton(onClick = { showAddShortcutDialog = true }) {
                    Text("+ إضافة اختصار", color = Color(0xFF4CAF50), fontSize = 11.sp)
                }
            }

            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
            ) {
                items(viewModel.customShortcuts.size) { index ->
                    val shortcut = viewModel.customShortcuts[index]
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clickable { onNavigate(shortcut.second) }
                                    .padding(8.dp)
                                    .fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(BackgroundDark),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(shortcut.third, fontSize = 14.sp)
                                }
                                Text(
                                    text = shortcut.first,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { viewModel.removeCustomShortcut(shortcut.second) },
                                modifier = Modifier
                                    .size(16.dp)
                                    .align(Alignment.TopEnd)
                                    .padding(2.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "مسح", tint = Color.Red.copy(alpha = 0.7f), modifier = Modifier.size(10.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TabSwitcherOverlay(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val profile = viewModel.activeChromeProfile ?: return
    val tabs = viewModel.chromeTabsPerProfile[profile.id] ?: remember { mutableStateListOf() }
    val activeTabIdx = viewModel.activeTabIdxPerProfile[profile.id] ?: 0

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
            .clickable { viewModel.isTabSwitcherOpen = false }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "مبدل التبويبات النشطة (العدد: ${tabs.size}) 📑",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.openNewTabForActiveChrome() },
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2E7D32))
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "New Tab", tint = Color.White, modifier = Modifier.size(18.dp))
                    }

                    IconButton(
                        onClick = { viewModel.isTabSwitcherOpen = false },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close Tab Switcher", tint = Color.White)
                    }
                }
            }

            Divider(color = Color.White.copy(alpha = 0.15f))

            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(tabs.size) { index ->
                    val tab = tabs[index]
                    val isSelected = index == activeTabIdx
                    Card(
                        onClick = { viewModel.selectTabForActiveChrome(index) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) Color(0xFF132B1B) else SurfaceCard
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .border(
                                width = if (isSelected) 2.dp else 0.5.dp,
                                color = if (isSelected) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(12.dp)
                            ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = tab.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = Color.White,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (tab.url.isEmpty()) "صفحة فارغة" else tab.url,
                                        fontSize = 9.sp,
                                        color = Color.LightGray,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color.White.copy(alpha = 0.05f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "مؤقت ومحفوظ 💾",
                                            fontSize = 8.sp,
                                            color = Color.Gray
                                        )
                                    }
                                    if (tab.isDesktopMode) {
                                        Icon(
                                            Icons.Default.Build,
                                            contentDescription = "Desktop Mode",
                                            tint = Color(0xFF10B981),
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = { viewModel.closeTabForActiveChrome(tab.id) },
                                modifier = Modifier
                                    .size(24.dp)
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Tab",
                                    tint = Color.White,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Add Tab button at bottom
            Button(
                onClick = { viewModel.openNewTabForActiveChrome() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "أضف تبويب")
                Spacer(modifier = Modifier.width(8.dp))
                Text("فتح علامة تبويب جديدة ➕", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun BingSplashScreen() {
    var progress by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        val animationTime = 2500L
        val steps = 100
        for (i in 1..steps) {
            delay(animationTime / steps)
            progress = i / steps.toFloat()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "splash")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF030A05), // Deep Emerald Space
                        Color(0xFF07140B), // Slate Green
                        Color(0xFF0E1F13)  // Edge Dark Green
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Decorative glowing background circles (Aurora effect)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawCircle(
                        color = Color(0xFF4CAF50).copy(alpha = 0.12f),
                        radius = size.minDimension * 0.5f,
                        center = Offset(size.width * 0.2f, size.height * 0.3f)
                    )
                    drawCircle(
                        color = Color(0xFF008272).copy(alpha = 0.12f),
                        radius = size.minDimension * 0.5f,
                        center = Offset(size.width * 0.8f, size.height * 0.7f)
                    )
                }
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            // Stylized Glowing Bango Logo using Canvas
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .drawBehind {
                        // Background glow
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFF4CAF50).copy(alpha = 0.3f), Color.Transparent),
                                radius = size.width * 0.8f
                            ),
                            radius = size.width * 0.8f
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Canvas(
                    modifier = Modifier
                        .size(80.dp)
                        .graphicsLayer {
                            scaleX = pulseScale
                            scaleY = pulseScale
                        }
                ) {
                    val width = size.width
                    val height = size.height

                    // Drawing base loop of the letter 'B'
                    drawArc(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFF4CAF50), Color(0xFF1E6B38))
                        ),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 10.dp.toPx(),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    )

                    // Vertical spine
                    drawLine(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFF008272), Color(0xFF4CAF50))
                        ),
                        start = Offset(x = 12.dp.toPx(), y = -16.dp.toPx()),
                        end = Offset(x = 12.dp.toPx(), y = height - 12.dp.toPx()),
                        strokeWidth = 10.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )

                    // Glowing center dot
                    drawCircle(
                        color = Color(0xFF10B981),
                        radius = 6.dp.toPx(),
                        center = Offset(x = 12.dp.toPx(), y = -16.dp.toPx())
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Brand Titles
            Text(
                text = "برنامج بانجو الآلي v2",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Bango Antidetect Browser",
                fontSize = 14.sp,
                color = Color(0xFF4CAF50),
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            Text(
                text = "حماية كاملة للهوية • بروفايلات منعزلة تماماً • أتمتة بحث ذكية",
                fontSize = 12.sp,
                color = Color.LightGray.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Loading bar
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.width(220.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFF1E6B38),
                                        Color(0xFF4CAF50),
                                        Color(0xFF008272)
                                    )
                                )
                            )
                    )
                }

                Text(
                    text = when {
                        progress < 0.3f -> "جاري عزل وتأمين بروفايلات الهويات..."
                        progress < 0.6f -> "تحميل خوارزميات التزييف والـ Spoofing..."
                        progress < 0.9f -> "تشغيل خزانة الوكلاء والـ VPN المشفر..."
                        else -> "فتح مرصد التصفح السحابي الآمن..."
                    },
                    fontSize = 10.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun DashboardTab(
    viewModel: MainViewModel,
    activeTopic: WeeklyTopic?,
    queries: List<SearchQuery>,
    onStartAutopilot: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Microsoft Bing Hero Daily Wallpaper Banner ---
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF07140B), // Dark Green
                                    Color(0xFF1E6B38), // Bango Green
                                    Color(0xFF008272), // Bango Teal
                                    Color(0xFF2E7D32)  // Forest Green
                                )
                            )
                        )
                        .drawBehind {
                            drawCircle(
                                color = Color.White.copy(alpha = 0.05f),
                                radius = size.minDimension * 0.6f,
                                center = Offset(size.width * 0.8f, size.height * 0.2f)
                            )
                        }
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.2f))
                                    .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("B", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                            }
                            Text(
                                text = "Bango",
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF10B981))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Autopilot", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Text(
                            text = "استكشاف وتصفح مجهول الهوية بالكامل • تدوير بروكسيات",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium
                        )

                        // Search box mock
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.95f)
                                .height(46.dp)
                                .clip(RoundedCornerShape(23.dp))
                                .background(Color.White.copy(alpha = 0.15f))
                                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(23.dp))
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "بحث",
                                        tint = Color.White
                                    )
                                    Text(
                                        text = if (activeTopic != null) activeTopic.topicText else "اسأل الذكاء الاصطناعي لإنشاء خطة استعلام ذكية...",
                                        color = if (activeTopic != null) Color.White else Color.White.copy(alpha = 0.6f),
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (viewModel.isAutomationRunning) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowLeft,
                                        contentDescription = "التالي",
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Google Authentication Card ---
        item {
            var showAccountSelector by remember { mutableStateOf(false) }

            if (showAccountSelector) {
                AlertDialog(
                    onDismissRequest = { showAccountSelector = false },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { showAccountSelector = false }) {
                            Text("إلغاء", color = Color.Gray)
                        }
                    },
                    containerColor = Color(0xFF162A1F),
                    title = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.Bold, fontSize = 24.sp)
                                Text("o", color = Color(0xFFEA4335), fontWeight = FontWeight.Bold, fontSize = 24.sp)
                                Text("o", color = Color(0xFFFBBC05), fontWeight = FontWeight.Bold, fontSize = 24.sp)
                                Text("g", color = Color(0xFF4285F4), fontWeight = FontWeight.Bold, fontSize = 24.sp)
                                Text("l", color = Color(0xFF34A853), fontWeight = FontWeight.Bold, fontSize = 24.sp)
                                Text("e", color = Color(0xFFEA4335), fontWeight = FontWeight.Bold, fontSize = 24.sp)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "تسجيل الدخول باستخدام Google",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                        }
                    },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Card(
                                onClick = {
                                    viewModel.signInWithGoogle("mahmoudmorsy949@gmail.com", "محمود مرسي")
                                    showAccountSelector = false
                                },
                                colors = CardDefaults.cardColors(containerColor = BackgroundDark),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.linearGradient(
                                                    colors = listOf(Color(0xFF4CAF50), Color(0xFF1E6B38))
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "M",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "محمود مرسي",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "mahmoudmorsy949@gmail.com",
                                            color = Color.LightGray,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF10B981))
                                    )
                                }
                            }
                        }
                    }
                )
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (viewModel.isGoogleSignedIn) Color(0xFF0F2618) else SurfaceDark
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = if (viewModel.isGoogleSignedIn) Color(0xFF10B981).copy(alpha = 0.4f) else Color.White.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (viewModel.isGoogleSignedIn) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(Color(0xFF4CAF50), Color(0xFF1E6B38))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "M",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = viewModel.googleUserName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color.White
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF10B981).copy(alpha = 0.2f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "مـمـيـز",
                                            color = Color(0xFF10B981),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Text(
                                    text = viewModel.googleUserEmail,
                                    fontSize = 11.sp,
                                    color = Color.LightGray
                                )
                            }

                            IconButton(
                                onClick = { viewModel.signOutGoogle() }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ExitToApp,
                                    contentDescription = "تسجيل الخروج",
                                    tint = Color.Red.copy(alpha = 0.8f)
                                )
                            }
                        }

                        Divider(color = Color.White.copy(alpha = 0.1f))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "آمن",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "مزامنة سحابية نشطة • وضع التخفي والمزايا الاحترافية مفعلة ✨",
                                fontSize = 11.sp,
                                color = Color(0xFF10B981),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "G",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }
                            Column {
                                Text(
                                    text = "تفعيل المزايا المتقدمة فوراً 🚀",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "سجل دخولك بحساب Google لتفعيل ميزات الهوية السحابية",
                                    fontSize = 10.sp,
                                    color = Color.LightGray
                                )
                            }
                        }

                        Button(
                            onClick = { showAccountSelector = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "G",
                                    color = Color(0xFF4285F4),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "تسجيل الدخول السريع باستخدام Google",
                                    color = Color(0xFF1E293B),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- Gemini Key & Security Advisor Hub ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF4CAF50).copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Security AI Advisor",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "المستشار الأمني والذكاء الاصطناعي لـ Bango 🛡️",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                    }

                    Text(
                        text = "نظام ذكي متكامل لتحليل بصمات المتصفح (Canvas, WebGL, RTC, ClientRects) واقتراح تدوير البروكسيات لفك الكتم.",
                        fontSize = 11.sp,
                        color = Color.LightGray
                    )

                    // Gemini API Key Input
                    if (viewModel.isGoogleSignedIn) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = BackgroundDark),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "نشط", tint = Color(0xFF10B981))
                                Column {
                                    Text("🔑 مفتاح API مُدار سحابياً ونشط", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text("تم ربط الذكاء الاصطناعي بحسابك المميز لتجاوز متطلبات المفتاح.", color = Color.LightGray, fontSize = 10.sp)
                                }
                            }
                        }
                    } else {
                        var apiKeyValue by remember { mutableStateOf(viewModel.customApiKey) }
                        OutlinedTextField(
                            value = apiKeyValue,
                            onValueChange = { apiKeyValue = it },
                            label = { Text("مفتاح API لـ Gemini الذكي", color = Color.LightGray, fontSize = 11.sp) },
                            placeholder = { Text("AIzaSy...", color = Color.Gray) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF4CAF50),
                                unfocusedBorderColor = Color.Gray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = { viewModel.saveCustomApiKey(apiKeyValue) }) {
                                    Icon(Icons.Default.Check, contentDescription = "حفظ", tint = Color(0xFF10B981))
                                }
                            }
                        )
                    }

                    Divider(color = Color.Gray.copy(alpha = 0.2f))

                    Text(
                        text = "💬 استشارات الأمن وتزييف البصمات:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50)
                    )

                    // Chat messages window
                    val chatMsgs by viewModel.chatMessages.collectAsState()
                    val lazyListState = rememberLazyListState()

                    LaunchedEffect(chatMsgs.size) {
                        if (chatMsgs.isNotEmpty()) {
                            lazyListState.animateScrollToItem(chatMsgs.size - 1)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(BackgroundDark.copy(alpha = 0.8f))
                            .padding(8.dp)
                    ) {
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(chatMsgs) { msg ->
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = if (msg.isUser) Alignment.End else Alignment.Start
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(
                                                RoundedCornerShape(
                                                    topStart = 12.dp,
                                                    topEnd = 12.dp,
                                                    bottomStart = if (msg.isUser) 12.dp else 0.dp,
                                                    bottomEnd = if (msg.isUser) 0.dp else 12.dp
                                                )
                                            )
                                            .background(
                                                if (msg.isUser) Color(0xFF1E6B38) else Color(0xFF253D30)
                                            )
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = msg.text,
                                            fontSize = 12.sp,
                                            color = Color.White,
                                            textAlign = if (msg.isUser) TextAlign.Right else TextAlign.Left,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Chat input row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = viewModel.chatInput,
                            onValueChange = { viewModel.chatInput = it },
                            placeholder = { Text("اسأل عن بصمة الكانفاس، جدار الحماية...", color = Color.Gray, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF4CAF50),
                                unfocusedBorderColor = Color.Gray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true,
                            enabled = !viewModel.isAiResponding
                        )

                        IconButton(
                            onClick = { viewModel.sendChatMessage() },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (viewModel.chatInput.isNotBlank()) Color(0xFF4CAF50) else Color.Gray.copy(alpha = 0.5f)),
                            enabled = viewModel.chatInput.isNotBlank() && !viewModel.isAiResponding
                        ) {
                            if (viewModel.isAiResponding) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = "إرسال",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // AI Weekly Topic Configurator Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "🎯 تغذية خطة الاستعلامات الموجهة",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFF4CAF50)
                    )
                    Text(
                        text = "أدخل كلمة بحث أو موضوع معين، وسيتولى الذكاء الاصطناعي توسعتها إلى 8 مسارات بحثية معقدة وممنهجة تلقائياً لتغطية كامل جوانب المحتوى الموجه.",
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )

                    OutlinedTextField(
                        value = viewModel.topicInput,
                        onValueChange = { viewModel.topicInput = it },
                        placeholder = { Text("مثال: ثورة الأمن السيبراني في السحابة") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF4CAF50),
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        enabled = !viewModel.isGeneratingQueries
                    )

                    Button(
                        onClick = { viewModel.submitWeeklyTopic() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = viewModel.topicInput.isNotBlank() && !viewModel.isGeneratingQueries
                    ) {
                        if (viewModel.isGeneratingQueries) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("جاري إنشاء صيغ الاستعلام الذكية...")
                        } else {
                            Text("توليد وتخصيص دورة 8 استعلامات ✨")
                        }
                    }
                }
            }
        }

        // Active Weekly Plan Frame
        if (activeTopic != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "الموضوع النشط الحالي:",
                                    fontSize = 11.sp,
                                    color = Color(0xFF4CAF50)
                                )
                                Text(
                                    text = activeTopic.topicText,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            IconButton(onClick = { viewModel.deleteWeeklyTopic(activeTopic.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color(0xFFEF4444))
                            }
                        }

                        Divider(color = Color.Gray.copy(alpha = 0.3f))

                        Text(
                            text = "صيغ البحث الثمانية المفعلة للتدوير التلقائي:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.LightGray
                        )

                        queries.forEachIndexed { idx, q ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(BackgroundDark.copy(alpha = 0.4f))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF1E6B38)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "${idx + 1}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = q.queryText,
                                            fontSize = 13.sp,
                                            color = Color.White,
                                            overflow = TextOverflow.Ellipsis,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = q.methodType,
                                            fontSize = 10.sp,
                                            color = Color(0xFF4CAF50)
                                        )
                                    }
                                }

                                IconButton(onClick = { viewModel.deleteQuery(q.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "مسح", modifier = Modifier.size(16.dp), tint = Color.Gray)
                                }
                            }
                        }

                        // Search Engine
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(BackgroundDark.copy(alpha = 0.6f))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "🌐 محرك البحث المستهدف للتشغيل:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4CAF50)
                            )

                            var expandedEngine by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedCard(
                                    onClick = { expandedEngine = true },
                                    colors = CardDefaults.outlinedCardColors(containerColor = SurfaceDark),
                                    border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (viewModel.selectedEngineName == "Auto") "تدوير تلقائي ذكي لجميع المحركات 🌀" else "محرك بحث مخصص: ${viewModel.selectedEngineName} 🔍",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Drop", tint = Color.White)
                                    }
                                }
                                DropdownMenu(
                                    expanded = expandedEngine,
                                    onDismissRequest = { expandedEngine = false },
                                    modifier = Modifier
                                        .fillMaxWidth(0.9f)
                                        .background(SurfaceDark)
                                ) {
                                    val engines = listOf("Auto", "Google", "Bing", "DuckDuckGo", "Yahoo", "Baidu", "Yandex")
                                    engines.forEach { eng ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(text = if (eng == "Auto") "🌀 تدوير تلقائي (Auto-Rotate)" else "🔍 $eng", color = Color.White, fontSize = 13.sp)
                                            },
                                            onClick = {
                                                viewModel.selectedEngineName = eng
                                                expandedEngine = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Start Live autopilot
                        Button(
                            onClick = onStartAutopilot,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Start")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("بدء تشغيل الأتمتة والبحث المباشر الآن 🤖")
                        }
                    }
                }
            }
        } else {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .background(SurfaceDark),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Empty", modifier = Modifier.size(48.dp), tint = Color.Gray)
                        Text(
                            text = "بانتظار تدوين موضوع أو استعلام للبدء",
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray
                        )
                        Text(
                            text = "اكتب كلمة بحث أولية بالأعلى وسيقوم النظام بتفصيل دورة الأتمتة الشاملة.",
                            fontSize = 12.sp,
                            color = Color.Gray.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

// Global Profile Application Logic (Desktop Chrome-like Isolation)
fun applyProfileToWebView(webView: WebView, profile: BrowserProfile?, isDesktopMode: Boolean = false) {
    if (profile == null) return

    // Enable high-compatibility browser settings for standard modern websites
    webView.settings.userAgentString = profile.userAgent
    webView.settings.javaScriptEnabled = true
    webView.settings.domStorageEnabled = true
    webView.settings.databaseEnabled = true
    webView.settings.allowFileAccess = true
    webView.settings.allowContentAccess = true
    webView.settings.loadWithOverviewMode = isDesktopMode
    webView.settings.useWideViewPort = isDesktopMode
    webView.settings.javaScriptCanOpenWindowsAutomatically = true
    webView.settings.mediaPlaybackRequiresUserGesture = false

    // Enable Pinch-to-Zoom support (Finger Zooming)
    webView.settings.setSupportZoom(true)
    webView.settings.builtInZoomControls = true
    webView.settings.displayZoomControls = false

    // Allow loading HTTPS/HTTP mixed content for web compatibility
    webView.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

    // Explicitly accept cookies and third-party cookies for logging in & compatibility
    try {
        val cookieManager = android.webkit.CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)
    } catch (e: Throwable) {
        e.printStackTrace()
    }

    // Apply multi-profile isolation if supported on runtime device
    if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.MULTI_PROFILE)) {
        try {
            val profileStore = androidx.webkit.ProfileStore.getInstance()
            profileStore.getOrCreateProfile(profile.dataSuffix)
            androidx.webkit.WebViewCompat.setProfile(webView, profile.dataSuffix)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
}

fun generateAntiFingerprintScript(profile: BrowserProfile?): String {
    val canvasNoise = if (profile?.canvasSpoof != false) "true" else "false"
    val webGlNoise = if (profile?.webGlSpoof != false) "true" else "false"
    val audioNoise = if (profile?.audioSpoof != false) "true" else "false"
    val rectsNoise = if (profile?.clientRectsSpoof != false) "true" else "false"
    val rtcSpoof = profile?.webRtcSpoof ?: "BLOCK" // "BLOCK", "REAL", "CUSTOM"
    val cores = profile?.hardwareConcurrency ?: 8
    val ram = profile?.deviceMemory ?: 8
    val userAgent = profile?.userAgent ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    val platform = profile?.platform ?: "Windows"
    val language = profile?.language ?: "ar-EG,ar;q=0.9,en-US;q=0.8"
    val resolution = profile?.screenResolution ?: "1920x1080"

    val geoSpoof = profile?.geolocationSpoof ?: "AUTO"
    val lat = profile?.customLatitude ?: 24.7136
    val lon = profile?.customLongitude ?: 46.6753

    val timezoneSpoof = profile?.timezoneSpoof ?: "AUTO"
    val customTz = profile?.customTimezone ?: "Asia/Riyadh"

    val dimensions = try {
        val parts = resolution.split("x")
        parts[0].toInt() to parts[1].toInt()
    } catch (e: Exception) {
        1920 to 1080
    }
    val w = dimensions.first
    val h = dimensions.second

    return """
        (function() {
            console.log("🛡️ Anti-Detect Shield Activated: ${profile?.name ?: "Default"}");

            // 1. Spoof Navigator Properties
            try {
                Object.defineProperty(navigator, 'hardwareConcurrency', { get: () => $cores, configurable: true });
                Object.defineProperty(navigator, 'deviceMemory', { get: () => $ram, configurable: true });
                Object.defineProperty(navigator, 'userAgent', { get: () => "$userAgent", configurable: true });
                Object.defineProperty(navigator, 'platform', { get: () => "$platform", configurable: true });
                Object.defineProperty(navigator, 'languages', { get: () => ["${language.split(",")[0]}", "en-US", "en"], configurable: true });
                Object.defineProperty(navigator, 'language', { get: () => "${language.split(",")[0]}", configurable: true });
            } catch(e) { console.error("Error spoofing navigator:", e); }

            // 2. Screen Resolution Spoofing
            try {
                Object.defineProperty(screen, 'width', { get: () => $w, configurable: true });
                Object.defineProperty(screen, 'height', { get: () => $h, configurable: true });
                Object.defineProperty(screen, 'availWidth', { get: () => $w, configurable: true });
                Object.defineProperty(screen, 'availHeight', { get: () => $h, configurable: true });
            } catch(e) {}

            // 3. WebRTC Protection
            if ("$rtcSpoof" === "BLOCK") {
                try {
                    delete window.RTCPeerConnection;
                    delete window.webkitRTCPeerConnection;
                } catch(e) {}
            } else if ("$rtcSpoof" === "CUSTOM") {
                try {
                    const originalRTCPeerConnection = window.RTCPeerConnection;
                    window.RTCPeerConnection = function(config) {
                        const pc = new originalRTCPeerConnection(config);
                        pc.createOffer = function(cb, err) {
                            return new Promise((resolve) => {
                                resolve({ sdp: "v=0\r\no=- 123456 2 IN IP4 127.0.0.1\r\ns=-\r\nt=0 0\r\na=group:BUNDLE rtp\r\na=msid-semantic: WMS" });
                            });
                        };
                        return pc;
                    };
                } catch(e) {}
            }

            // 4. Geolocation Spoofing
            if ("$geoSpoof" === "BLOCK") {
                try {
                    navigator.geolocation.getCurrentPosition = function(success, error) {
                        if (error) error({ code: 1, message: "User denied Geolocation" });
                    };
                } catch(e) {}
            } else if ("$geoSpoof" === "CUSTOM" || "$geoSpoof" === "AUTO") {
                try {
                    navigator.geolocation.getCurrentPosition = function(success, error) {
                        success({
                            coords: {
                                latitude: $lat,
                                longitude: $lon,
                                accuracy: 10,
                                altitude: null,
                                altitudeAccuracy: null,
                                heading: null,
                                speed: null
                            },
                            timestamp: Date.now()
                        });
                    };
                    navigator.geolocation.watchPosition = function(success, error) {
                        navigator.geolocation.getCurrentPosition(success, error);
                        return 12345;
                    };
                } catch(e) {}
            }

            // 5. Timezone Spoofing
            if ("$timezoneSpoof" === "CUSTOM" || "$timezoneSpoof" === "AUTO") {
                try {
                    const resolvedOptions = Intl.DateTimeFormat.prototype.resolvedOptions;
                    Intl.DateTimeFormat.prototype.resolvedOptions = function() {
                        const options = resolvedOptions.call(this);
                        options.timeZone = "$customTz";
                        return options;
                    };
                } catch(e) {}
            }

            // 6. Canvas cryptographic noise spoofing (Robust context and crash prevention)
            if ($canvasNoise) {
                try {
                    const originalToDataURL = HTMLCanvasElement.prototype.toDataURL;
                    HTMLCanvasElement.prototype.toDataURL = function(type, encoderOptions) {
                        try {
                            const isWebGL = this.getContext('webgl') || this.getContext('webgl2');
                            if (!isWebGL) {
                                const ctx = this.getContext('2d');
                                if (ctx) {
                                    const imgData = ctx.getImageData(0, 0, Math.min(this.width, 10) || 1, Math.min(this.height, 10) || 1);
                                    if (imgData && imgData.data && imgData.data.length > 0) {
                                        imgData.data[0] = (imgData.data[0] + 1) % 256;
                                        ctx.putImageData(imgData, 0, 0);
                                    }
                                }
                            }
                        } catch(inner) {}
                        return originalToDataURL.call(this, type, encoderOptions);
                    };
                } catch(e) {}
            }

            // 7. WebGL Fingerprint Spoofing
            if ($webGlNoise) {
                try {
                    const originalGetParameter = WebGLRenderingContext.prototype.getParameter;
                    WebGLRenderingContext.prototype.getParameter = function(pname) {
                        if (pname === 37445) return "Intel Open Source Technology Center";
                        if (pname === 37446) return "Mesa DRI Intel(R) HD Graphics (Skylake GT2)";
                        return originalGetParameter.call(this, pname);
                    };
                    if (window.WebGL2RenderingContext) {
                        const originalGetParameter2 = WebGL2RenderingContext.prototype.getParameter;
                        WebGL2RenderingContext.prototype.getParameter = function(pname) {
                            if (pname === 37445) return "Intel Open Source Technology Center";
                            if (pname === 37446) return "Mesa DRI Intel(R) HD Graphics (Skylake GT2)";
                            return originalGetParameter2.call(this, pname);
                        };
                    }
                } catch(e) {}
            }

            // 8. AudioContext Spoofing
            if ($audioNoise) {
                try {
                    const originalGetChannelData = AudioBuffer.prototype.getChannelData;
                    AudioBuffer.prototype.getChannelData = function(channel) {
                        const data = originalGetChannelData.call(this, channel);
                        for (let i = 0; i < Math.min(data.length, 10); i++) {
                            data[i] += 0.0000001 * Math.sin(i);
                        }
                        return data;
                    };
                } catch(e) {}
            }

            // 9. ClientRects Fingerprinting Defender (Standard-compliant DOMRectList & DOMRect mimicking to prevent TypeError crashes on Bing/Google)
            if ($rectsNoise) {
                try {
                    const originalGetClientRects = Element.prototype.getClientRects;
                    Element.prototype.getClientRects = function() {
                        try {
                            const rects = originalGetClientRects.call(this);
                            if (rects && rects.length > 0) {
                                const clonedRects = [];
                                for (let i = 0; i < rects.length; i++) {
                                    const r = rects[i];
                                    const clonedRect = {
                                        x: r.x + 0.0001,
                                        y: r.y + 0.0001,
                                        width: r.width + 0.0001,
                                        height: r.height + 0.0001,
                                        top: r.top + 0.0001,
                                        right: r.right + 0.0001,
                                        bottom: r.bottom + 0.0001,
                                        left: r.left + 0.0001
                                    };
                                    // Make sure it looks like a standard DOMRect
                                    Object.setPrototypeOf(clonedRect, DOMRect.prototype);
                                    clonedRects.push(clonedRect);
                                }

                                // Mimic standard DOMRectList with .item(index) method and read-only .length property
                                Object.defineProperty(clonedRects, 'item', {
                                    value: function(index) {
                                        return this[index] || null;
                                    },
                                    writable: false,
                                    enumerable: false,
                                    configurable: false
                                });
                                Object.setPrototypeOf(clonedRects, DOMRectList.prototype);
                                return clonedRects;
                            }
                            return rects;
                        } catch(inner) {
                            return originalGetClientRects.call(this);
                        }
                    };
                } catch(e) {}
            }
        })();
    """.trimIndent()
}

@Composable
fun AutopilotTab(
    viewModel: MainViewModel,
    defaultProfile: BrowserProfile?,
    onWebViewCreated: (WebView) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Automation Sandbox Status Header Card
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                            text = "مرصد تصفح الويب المباشر 🔬",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }

                    if (viewModel.isAutomationRunning) {
                        Button(
                            onClick = { viewModel.stopLiveAutomation() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Stop", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("إيقاف دورة الأتمتة", fontSize = 11.sp)
                        }
                    }
                }

                if (viewModel.isAutomationRunning) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceCard)
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "الاستعلام النشط حالياً (${viewModel.activeAutomationQueryIndex + 1} من 8):",
                            fontSize = 11.sp,
                            color = Color(0xFF4CAF50)
                        )
                        Text(
                            text = viewModel.activeAutomationQueryText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("المحرك: ${viewModel.activeAutomationEngineName}", fontSize = 10.sp, color = Color.LightGray)
                            Text("الوكيل: ${viewModel.activeAutomationProxyUsed}", fontSize = 10.sp, color = Color.LightGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                } else {
                    Text(
                        text = "المتصفح في وضع الخمول وجاهز للانطلاق الآمن. انتقل إلى 'بينج اليوم' لبدء تشغيل دورة البحث المباشرة وعرض محاكاة السلوك البشري.",
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )
                }
            }
        }

        // Live Web Client Render Sandbox (WebView) - takes 100% full screen of remaining space!
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.2f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceCard)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFEF4444)))
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFFBBF24)))
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF34D399)))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "نافذة المتصفح المحمي وعزل الهويات الكلي",
                        fontSize = 11.sp,
                        color = Color.LightGray,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                AndroidView(
                    factory = { context ->
                        try {
                            WebView(context).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.loadWithOverviewMode = false
                                settings.useWideViewPort = false
                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        super.onPageStarted(view, url, favicon)
                                        val js = generateAntiFingerprintScript(defaultProfile)
                                        view?.evaluateJavascript(js, null)
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        val js = generateAntiFingerprintScript(defaultProfile)
                                        view?.evaluateJavascript(js, null)
                                    }
                                }

                                // Apply secure isolated browser profile
                                applyProfileToWebView(this, defaultProfile, false)

                                onWebViewCreated(this)
                                loadUrl("https://www.bing.com")
                            }
                        } catch (e: Throwable) {
                            e.printStackTrace()
                            android.widget.TextView(context).apply {
                                text = "🔒 وضع المحاكاة الخلفي الآمن نشط\nيتم تنفيذ عمليات البحث وتفتيش المواقع بنجاح دون الحاجة لمتصفح مرئي ثقيل لمنع أي تعارض."
                                textSize = 12f
                                setTextColor(android.graphics.Color.LTGRAY)
                                setPadding(24, 24, 24, 24)
                                gravity = android.view.Gravity.CENTER
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

// Dedicated Operations & Command Center Terminal Tab (Static & Solid without overlap)
@Composable
fun TerminalTab(
    viewModel: MainViewModel,
    terminalLogs: List<String>,
    logs: List<AutomationLog>
) {
    val terminalListState = rememberLazyListState()

    // Follow scrolling bottom on new logs
    LaunchedEffect(terminalLogs.size) {
        if (terminalLogs.isNotEmpty()) {
            terminalListState.animateScrollToItem(terminalLogs.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Live Rolling Console Log panel (Upper Area)
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF020704)),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.3f)
                .border(1.dp, Color(0xFF4CAF50).copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFEF4444)))
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFFBBF24)))
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF34D399)))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "📟 موجه عمليات الأتمتة المباشر (Bango Terminal)",
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    if (terminalLogs.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.rollingTerminalLogs.value = emptyList() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Divider(color = Color(0xFF1E6B38).copy(alpha = 0.3f))

                LazyColumn(
                    state = terminalListState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (terminalLogs.isEmpty()) {
                        item {
                            Text(
                                "بانتظار انطلاق آلة البحث والتحقيق الآمن لتوثيق الأنشطة الحية هنا...",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    } else {
                        items(terminalLogs) { rowLog ->
                            Text(
                                text = rowLog,
                                color = if (rowLog.contains("🔒") || rowLog.contains("✅") || rowLog.contains("🔐")) Color(0xFF10B981) else Color(0xFF4CAF50),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 13.sp,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // Search History Log Card (Lower Area)
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                        Icon(Icons.Default.List, contentDescription = "History", tint = Color(0xFF4CAF50))
                        Text(
                            text = "📊 سجل العمليات والجرود التاريخية",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                    }

                    if (logs.isNotEmpty()) {
                        Button(
                            onClick = { viewModel.clearLogs() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.15f), contentColor = Color(0xFFEF4444)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("تصفير 🗑️", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Divider(color = Color.Gray.copy(alpha = 0.15f))

                if (logs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(BackgroundDark.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "لا توجد سجلات بحث آلي مخزنة حتى الآن.",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(logs) { log ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = BackgroundDark.copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val sdf = java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.getDefault())
                                        Text(
                                            text = sdf.format(java.util.Date(log.timestamp)),
                                            fontSize = 10.sp,
                                            color = Color.Gray
                                        )

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (log.status == "SUCCESS") Color(0xFF1B3B2B) else Color(0xFF4C0505))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (log.status == "SUCCESS") "مكتمل بنجاح" else "فشل الاتصال",
                                                fontSize = 9.sp,
                                                color = if (log.status == "SUCCESS") Color(0xFF10B981) else Color(0xFFEF4444)
                                            )
                                        }
                                    }

                                    Text(
                                        text = log.queryText,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "طريقة البحث: ${log.methodType}",
                                            fontSize = 9.sp,
                                            color = Color(0xFF4CAF50)
                                        )
                                        Text(
                                            text = "المدة: ${log.durationMs}ms",
                                            fontSize = 9.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProxiesTab(
    viewModel: MainViewModel,
    proxies: List<ProxyConfig>
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Built-in VPN & Cloud Location Emulator Card ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF4CAF50).copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "VPN",
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "شبكة VPN المدمجة والبروكسي الدوّار 🛡️",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "تصفح آمن وخوادم توجيه مشفرة من دول متعددة",
                                    fontSize = 11.sp,
                                    color = Color.LightGray
                                )
                            }
                        }

                        Switch(
                            checked = viewModel.isBuiltInVpnEnabled,
                            onCheckedChange = { viewModel.toggleBuiltInVpn(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF4CAF50),
                                uncheckedThumbColor = Color.LightGray,
                                uncheckedTrackColor = Color.DarkGray
                            )
                        )
                    }

                    if (viewModel.isBuiltInVpnEnabled) {
                        Text(
                            text = "اختر دولة الخادم لتغيير موقعك الجغرافي وبصمتك الرقمية:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.LightGray
                        )

                        viewModel.vpnServers.forEach { server ->
                            val isSelected = viewModel.selectedVpnServerId == server.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) Color(0xFF1E6B38).copy(alpha = 0.2f) else BackgroundDark.copy(alpha = 0.5f)
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) Color(0xFF4CAF50) else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { viewModel.selectVpnServer(server.id) }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = server.flag,
                                        fontSize = 20.sp
                                    )
                                    Column {
                                        Text(
                                            text = server.countryName,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "نوع الاتصال: ${server.type} • خادم سحابي مشفر",
                                            fontSize = 10.sp,
                                            color = Color.LightGray
                                        )
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF10B981))
                                    )
                                    Text(
                                        text = "${server.latency}ms",
                                        fontSize = 11.sp,
                                        color = Color(0xFF10B981)
                                    )
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(BackgroundDark.copy(alpha = 0.4f))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "الشبكة المدمجة متوقفة الآن. سيتم التصفح مباشرة أو بالبروكسي المدخل يدوياً.",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // Add Proxy Form Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "🔒 إضافة سيرفر فك كتم وهوية (Proxy)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF4CAF50)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = viewModel.proxyHostInput,
                            onValueChange = { viewModel.proxyHostInput = it },
                            placeholder = { Text("عنوان IP") },
                            modifier = Modifier.weight(3f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF4CAF50),
                                unfocusedBorderColor = Color.Gray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = viewModel.proxyPortInput,
                            onValueChange = { viewModel.proxyPortInput = it },
                            placeholder = { Text("المجال") },
                            modifier = Modifier.weight(1.2f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF4CAF50),
                                unfocusedBorderColor = Color.Gray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = viewModel.proxyUsernameInput,
                            onValueChange = { viewModel.proxyUsernameInput = it },
                            placeholder = { Text("الاسم (اختياري)") },
                            modifier = Modifier.weight(2f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF4CAF50),
                                unfocusedBorderColor = Color.Gray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = viewModel.proxyPasswordInput,
                            onValueChange = { viewModel.proxyPasswordInput = it },
                            placeholder = { Text("المرور (اختياري)") },
                            modifier = Modifier.weight(2f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF4CAF50),
                                unfocusedBorderColor = Color.Gray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true
                        )
                    }

                    Button(
                        onClick = { viewModel.addProxy() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("حفظ وتوثيق البروكسي في خزانة الهويات 🔒")
                    }
                }
            }
        }

        // Active Proxies List Grid
        item {
            Text(
                text = "الوكلاء المتاحون للتدوير الآلي بالتناوب:",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color.LightGray
            )
        }

        if (proxies.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceDark),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "لا توجد خوادم بروكسي حالياً. سيتم استخدام الاتصال المباشر.",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(proxies) { proxy ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceDark)
                        .border(1.dp, if (proxy.isActive) Color(0xFF4CAF50).copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (proxy.isActive) Color(0xFF1B3B2B) else Color(0xFF2D1616)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                proxy.type,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (proxy.isActive) Color(0xFF10B981) else Color(0xFFEF4444)
                            )
                        }

                        Column {
                            Text(
                                text = "${proxy.host}:${proxy.port}",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 14.sp
                            )
                            Text(
                                text = if (proxy.username != null) "محمي بكلمة مرور" else "بروكسي مفتوح / عام",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Switch(
                            checked = proxy.isActive,
                            onCheckedChange = { viewModel.toggleProxyActive(proxy) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF4CAF50),
                                checkedTrackColor = Color(0xFF1E6B38).copy(alpha = 0.5f)
                            )
                        )

                        IconButton(onClick = { viewModel.deleteProxy(proxy.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "مسح", tint = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfilesTab(
    viewModel: MainViewModel
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val defaultProfile by viewModel.defaultProfile.collectAsStateWithLifecycle()

    var showAddForm by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Explanatory Info Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "👤 إدارة بروفايلات التخفي وعزل البصمات (Anti-Detect)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF4CAF50)
                    )
                    Text(
                        text = "على غرار برامج AdsPower و Dolphin{anty}، يمنحك كل بروفايل هنا حاوية منعزلة تماماً (Sandbox) بملفات تعريف ارتباط (Cookies) منعزلة، وذاكرة تخزين محلية خاصة، وبصمة تصفح (User-Agent) مميزة وبروكسي مخصص لمنع المنصات من ربط حساباتك أو تعقب جهازك الحقيقي.",
                        fontSize = 11.sp,
                        color = Color.LightGray,
                        lineHeight = 16.sp
                    )

                    Button(
                        onClick = { showAddForm = !showAddForm },
                        colors = ButtonDefaults.buttonColors(containerColor = if (showAddForm) Color.Gray else Color(0xFF1E6B38)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (showAddForm) "إغلاق نافذة الإضافة ✖" else "إنشاء بروفايل تخفي جديد ➕")
                    }
                }
            }
        }

        // Add Profile Form
        if (showAddForm) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "🛠 تكوين بصمة البروفايل الجديد",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )

                        // Profile Name & Tag
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = viewModel.profileNameInput,
                                onValueChange = { viewModel.profileNameInput = it },
                                label = { Text("اسم البروفايل") },
                                placeholder = { Text("Facebook_Profile_1") },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF4CAF50),
                                    unfocusedBorderColor = Color.Gray,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = viewModel.profileTagInput,
                                onValueChange = { viewModel.profileTagInput = it },
                                label = { Text("العلامة / التصنيف") },
                                placeholder = { Text("تسويق") },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF4CAF50),
                                    unfocusedBorderColor = Color.Gray,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                singleLine = true
                            )
                        }

                        // Platform Selector Row
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("نظام التشغيل المحاكى (Platform):", fontSize = 11.sp, color = Color.Gray)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val platforms = listOf("Windows", "macOS", "Linux", "Android")
                                platforms.forEach { plat ->
                                    val isSelected = viewModel.profilePlatformInput == plat
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) Color(0xFF1E6B38) else SurfaceDark)
                                            .clickable {
                                                viewModel.profilePlatformInput = plat
                                                viewModel.profileUserAgentInput = when (plat) {
                                                    "Windows" -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                                                    "macOS" -> "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15"
                                                    "Linux" -> "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                                                    "Android" -> "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                                                    else -> viewModel.profileUserAgentInput
                                                }
                                            }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(plat, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                        }

                        // User-Agent input
                        OutlinedTextField(
                            value = viewModel.profileUserAgentInput,
                            onValueChange = { viewModel.profileUserAgentInput = it },
                            label = { Text("معرّف البصمة (User-Agent)") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF4CAF50),
                                unfocusedBorderColor = Color.Gray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
                        )

                        // Proxy settings
                        Text("🛡 بروكسي مخصص للهوية (اختياري):", fontSize = 11.sp, color = Color(0xFF4CAF50))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = viewModel.profileProxyHostInput,
                                onValueChange = { viewModel.profileProxyHostInput = it },
                                label = { Text("الخادم (Proxy Host)") },
                                placeholder = { Text("185.23.4.1") },
                                modifier = Modifier.weight(2f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF4CAF50),
                                    unfocusedBorderColor = Color.Gray,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = viewModel.profileProxyPortInput,
                                onValueChange = { viewModel.profileProxyPortInput = it },
                                label = { Text("المنفذ") },
                                placeholder = { Text("8080") },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF4CAF50),
                                    unfocusedBorderColor = Color.Gray,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = viewModel.profileProxyUserInput,
                                onValueChange = { viewModel.profileProxyUserInput = it },
                                label = { Text("اسم المستخدم") },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF4CAF50),
                                    unfocusedBorderColor = Color.Gray,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = viewModel.profileProxyPassInput,
                                onValueChange = { viewModel.profileProxyPassInput = it },
                                label = { Text("كلمة المرور") },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF4CAF50),
                                    unfocusedBorderColor = Color.Gray,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                singleLine = true
                            )
                        }

                        var showAdvancedConfig by remember { mutableStateOf(false) }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(0.5.dp, if (showAdvancedConfig) Color(0xFF4CAF50) else Color.Gray.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showAdvancedConfig = !showAdvancedConfig },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "🛡️ بصمات الأمن السيبراني المتقدمة",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (showAdvancedConfig) Color(0xFF4CAF50) else Color.White
                                    )
                                    Text(
                                        text = if (showAdvancedConfig) "إخفاء ▲" else "عرض وتخصيص ▼",
                                        fontSize = 10.sp,
                                        color = Color(0xFF4CAF50)
                                    )
                                }

                                if (showAdvancedConfig) {
                                    Spacer(modifier = Modifier.height(12.dp))

                                    Text("🔧 محاكاة عتاد الجهاز الحقيقي:", fontSize = 11.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        OutlinedTextField(
                                            value = viewModel.profileHardwareConcurrency,
                                            onValueChange = { viewModel.profileHardwareConcurrency = it },
                                            label = { Text("نوى المعالج (CPU)") },
                                            placeholder = { Text("8") },
                                            modifier = Modifier.weight(1f),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = Color(0xFF4CAF50),
                                                unfocusedBorderColor = Color.Gray,
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White
                                            ),
                                            singleLine = true
                                        )
                                        OutlinedTextField(
                                            value = viewModel.profileDeviceMemory,
                                            onValueChange = { viewModel.profileDeviceMemory = it },
                                            label = { Text("الذاكرة (RAM)") },
                                            placeholder = { Text("8") },
                                            modifier = Modifier.weight(1f),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = Color(0xFF4CAF50),
                                                unfocusedBorderColor = Color.Gray,
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White
                                            ),
                                            singleLine = true
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Text("🎨 تشويش البصمات الرسومية:", fontSize = 11.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(
                                                    checked = viewModel.profileCanvasSpoof,
                                                    onCheckedChange = { viewModel.profileCanvasSpoof = it },
                                                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFF4CAF50))
                                                )
                                                Text("تشويش Canvas", fontSize = 11.sp, color = Color.White)
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(
                                                    checked = viewModel.profileWebGlSpoof,
                                                    onCheckedChange = { viewModel.profileWebGlSpoof = it },
                                                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFF4CAF50))
                                                )
                                                Text("تشويش WebGL", fontSize = 11.sp, color = Color.White)
                                            }
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(
                                                    checked = viewModel.profileAudioSpoof,
                                                    onCheckedChange = { viewModel.profileAudioSpoof = it },
                                                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFF4CAF50))
                                                )
                                                Text("تشويش الصوت", fontSize = 11.sp, color = Color.White)
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(
                                                    checked = viewModel.profileClientRectsSpoof,
                                                    onCheckedChange = { viewModel.profileClientRectsSpoof = it },
                                                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFF4CAF50))
                                                )
                                                Text("ClientRects", fontSize = 11.sp, color = Color.White)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = viewModel.profileIsDefaultInput,
                                onCheckedChange = { viewModel.profileIsDefaultInput = it },
                                colors = CheckboxDefaults.colors(checkedColor = Color(0xFF4CAF50))
                            )
                            Text("تعيين كملف تعريف نشط حالياً فور الإنشاء", color = Color.White, fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                viewModel.addBrowserProfile()
                                showAddForm = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = viewModel.profileNameInput.isNotBlank()
                        ) {
                            Text("حفظ وبناء الهوية المشفرة 💾", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Active Profile Status Indicator
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1E14)),
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981))
                    )
                    Column {
                        Text(
                            text = "الملف التعريفي النشط حالياً:",
                            fontSize = 11.sp,
                            color = Color(0xFF4CAF50)
                        )
                        Text(
                            text = defaultProfile?.name ?: "البروفايل المالي الآمن (الافتراضي)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "البروكسي النشط: " + (defaultProfile?.let { if (!it.proxyHost.isNullOrEmpty()) "${it.proxyHost}:${it.proxyPort}" else "اتصال مباشر (لا يوجد)" } ?: "اتصال مباشر"),
                            fontSize = 11.sp,
                            color = Color.LightGray
                        )
                    }
                }
            }
        }

        // Title of existing profiles
        item {
            Text(
                text = "📁 ملفات التعريف المهيأة (${profiles.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color.White
            )
        }

        // List of Profiles
        if (profiles.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("جاري تحميل بروفايلات التخفي...", color = Color.Gray)
                }
            }
        } else {
            items(profiles) { profile ->
                val isActive = defaultProfile?.id == profile.id || (defaultProfile == null && profile.isDefault)
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isActive) SurfaceCard else SurfaceDark
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(
                        width = if (isActive) 1.5.dp else 0.5.dp,
                        color = if (isActive) Color(0xFF4CAF50) else Color.Gray.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF4CAF50).copy(alpha = 0.2f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(profile.tag, fontSize = 10.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    text = profile.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color.White
                                )
                            }

                            if (isActive) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF1B3B2B))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("نشط ومثبت 🎯", fontSize = 10.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Divider(color = Color.Gray.copy(alpha = 0.15f))

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("💻 نظام التشغيل:", fontSize = 11.sp, color = Color.Gray)
                                Text(profile.platform, fontSize = 11.sp, color = Color.White)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("🔒 البروكسي:", fontSize = 11.sp, color = Color.Gray)
                                Text(
                                    text = if (!profile.proxyHost.isNullOrEmpty()) "${profile.proxyHost}:${profile.proxyPort} (${profile.proxyType})" else "اتصال مباشر (Direct)",
                                    fontSize = 11.sp,
                                    color = Color.White
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("🌐 معرّف البصمة:", fontSize = 11.sp, color = Color.Gray)
                                Text(
                                    text = profile.userAgent,
                                    fontSize = 10.sp,
                                    color = Color.LightGray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Divider(color = Color.Gray.copy(alpha = 0.15f))

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = { viewModel.launchChromeBrowser(profile) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Run",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("تشغيل المتصفح 🚀", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = { viewModel.toggleProfileActive(profile) },
                                    colors = ButtonDefaults.buttonColors(containerColor = if (isActive) Color.Gray else Color(0xFF10B981)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    enabled = !isActive
                                ) {
                                    Text("تثبيت 🎯", fontSize = 10.sp)
                                }

                                OutlinedButton(
                                    onClick = { viewModel.deleteProfile(profile.id) },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                                    border = BorderStroke(1.dp, Color(0xFFEF4444)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    enabled = !isActive && profiles.size > 1
                                ) {
                                    Text("حذف 🗑", fontSize = 10.sp)
                                }
                            }

                            Text(
                                text = "حاوية: " + profile.dataSuffix.take(12) + "...",
                                fontSize = 9.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}
