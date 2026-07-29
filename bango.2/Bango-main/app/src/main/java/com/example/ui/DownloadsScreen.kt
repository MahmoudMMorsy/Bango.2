package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.GameEntity
import com.example.util.GameDownloader
import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allGames by viewModel.allGames.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTabFilter by remember { mutableStateOf("ALL") }
    // Tabs: "ALL", "VIDEO", "AUDIO", "DOC", "ARCHIVE", "HTML_FLASH", "FAVORITE"

    var showDirectDownloadDialog by remember { mutableStateOf(false) }

    // Differentiate and filter files correctly
    val filteredGames = remember(allGames, searchQuery, selectedTabFilter) {
        allGames.filter { game ->
            val matchesSearch = game.title.contains(searchQuery, ignoreCase = true) ||
                                game.sourceUrl.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (selectedTabFilter) {
                "FAVORITE" -> game.isFavorite
                "VIDEO" -> game.gameType == "VIDEO"
                "AUDIO" -> game.gameType == "AUDIO"
                "DOC" -> game.gameType == "DOC"
                "ARCHIVE" -> game.gameType == "ARCHIVE"
                "HTML_FLASH" -> game.gameType == "HTML" || game.gameType == "FLASH"
                else -> true
            }
            matchesSearch && matchesFilter
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header with Localized Title & Speed Boost subtitle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "مدير التنزيلات السريع",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Multi-Format Turbo Downloader",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Add Download Button
                Button(
                    onClick = { showDirectDownloadDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("رابط جديد (Add Link)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().testTag("downloads_search_bar"),
                placeholder = { Text("البحث في التنزيلات... (Search downloads...)") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Dynamic Category Filter Tabs in Arabic & English
            ScrollableTabRow(
                selectedTabIndex = when (selectedTabFilter) {
                    "ALL" -> 0
                    "VIDEO" -> 1
                    "AUDIO" -> 2
                    "DOC" -> 3
                    "ARCHIVE" -> 4
                    "HTML_FLASH" -> 5
                    "FAVORITE" -> 6
                    else -> 0
                },
                edgePadding = 0.dp,
                divider = {},
                containerColor = Color.Transparent,
                modifier = Modifier.fillMaxWidth()
            ) {
                val tabs = listOf(
                    "ALL" to "الكل (All)",
                    "VIDEO" to "فيديو (Videos)",
                    "AUDIO" to "صوتيات (Audio)",
                    "DOC" to "مستندات (Docs)",
                    "ARCHIVE" to "برامج (Archives)",
                    "HTML_FLASH" to "ألعاب وويب",
                    "FAVORITE" to "المفضلة"
                )

                tabs.forEachIndexed { index, (filter, title) ->
                    Tab(
                        selected = selectedTabFilter == filter,
                        onClick = { selectedTabFilter = filter },
                        text = {
                            val count = when (filter) {
                                "ALL" -> allGames.size
                                "FAVORITE" -> allGames.count { it.isFavorite }
                                "VIDEO" -> allGames.count { it.gameType == "VIDEO" }
                                "AUDIO" -> allGames.count { it.gameType == "AUDIO" }
                                "DOC" -> allGames.count { it.gameType == "DOC" }
                                "ARCHIVE" -> allGames.count { it.gameType == "ARCHIVE" }
                                "HTML_FLASH" -> allGames.count { it.gameType == "HTML" || it.gameType == "FLASH" }
                                else -> 0
                            }
                            Text("$title ($count)", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    )
                }
            }

            // Downloads List
            if (filteredGames.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "لا توجد تنزيلات في هذا القسم",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "أضف رابط تنزيل مباشر أو تصفح مواقع الويب لتحميل الفيديوهات والملفات بسهولة.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredGames, key = { it.id }) { game ->
                        DetailedDownloadCard(
                            game = game,
                            onPlay = { viewModel.playOfflineGame(game) },
                            onToggleFavorite = { viewModel.toggleFavorite(game) },
                            onDelete = { viewModel.deleteGame(context, game) },
                            onExportZip = { exportFileToPublicDownloads(context, game) },
                            onRestart = {
                                viewModel.downloadResumableMedia(
                                    context = context,
                                    url = game.sourceUrl,
                                    title = game.title,
                                    category = game.gameType,
                                    numSegments = game.numSegments
                                )
                            }
                        )
                    }
                }
            }
        }

        // Direct Download Entry Dialog
        if (showDirectDownloadDialog) {
            DirectDownloadDialog(
                onDismiss = { showDirectDownloadDialog = false },
                onStartDownload = { url, title, category, threads ->
                    showDirectDownloadDialog = false
                    viewModel.downloadResumableMedia(
                        context = context,
                        url = url,
                        title = title,
                        category = category,
                        numSegments = threads
                    )
                }
            )
        }
    }
}

/**
 * Highly detailed and responsive Download Card that handles:
 * - Real-time progress bars and percentage calculation
 * - Active speeds, connection streams/threads count
 * - Status states (DOWNLOADING, COMPLETED, FAILED) with dynamic styling
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DetailedDownloadCard(
    game: GameEntity,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onExportZip: () -> Unit,
    onRestart: () -> Unit
) {
    val dateString = remember(game.timestamp) {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        sdf.format(Date(game.timestamp))
    }

    // Parse progress percentage
    val progress = remember(game.downloadedBytes, game.sizeBytes, game.speedInfo) {
        if (game.sizeBytes > 0) {
            game.downloadedBytes.toFloat() / game.sizeBytes
        } else {
            // Try parsing percent from speedInfo (e.g. "45%")
            val match = """(\d+(?:\.\d+)?)%""".toRegex().find(game.speedInfo)
            if (match != null) {
                (match.groupValues[1].toFloatOrNull() ?: 0f) / 100f
            } else {
                0f
            }
        }
    }

    val sizeString = remember(game.sizeBytes, game.downloadedBytes) {
        val df = DecimalFormat("#.##")
        fun formatSize(bytes: Long): String {
            val kb = bytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> "${df.format(gb)} GB"
                mb >= 1.0 -> "${df.format(mb)} MB"
                kb >= 1.0 -> "${df.format(kb)} KB"
                else -> "$bytes Bytes"
            }
        }

        if (game.status == "DOWNLOADING" && game.sizeBytes > 0) {
            "${formatSize(game.downloadedBytes)} / ${formatSize(game.sizeBytes)}"
        } else {
            formatSize(game.sizeBytes)
        }
    }

    // Map Category to localized badge names and specific color tokens
    val categoryBadge = when (game.gameType) {
        "VIDEO" -> "فيديو (Video)"
        "AUDIO" -> "صوت (Audio)"
        "DOC" -> "مستند (Doc)"
        "ARCHIVE" -> "مضغوط (Archive)"
        "HTML" -> "لعبة ويب (HTML)"
        "FLASH" -> "فلاش (Flash)"
        else -> "ملف (File)"
    }

    val statusColor = when (game.status) {
        "DOWNLOADING" -> MaterialTheme.colorScheme.primary
        "FAILED" -> MaterialTheme.colorScheme.error
        "COMPLETED" -> Color(0xFF4CAF50)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("downloaded_game_item_${game.id}")
            .border(
                width = 1.dp,
                color = when (game.status) {
                    "DOWNLOADING" -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    "FAILED" -> MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                    else -> Color.Transparent
                },
                shape = RoundedCornerShape(14.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon representation of file format
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            when (game.gameType) {
                                "HTML" -> MaterialTheme.colorScheme.primaryContainer
                                "FLASH" -> MaterialTheme.colorScheme.tertiaryContainer
                                "VIDEO" -> Color(0xFFFFEBEE)
                                "AUDIO" -> Color(0xFFE8F5E9)
                                "DOC" -> Color(0xFFE3F2FD)
                                "ARCHIVE" -> Color(0xFFFFF3E0)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (game.gameType) {
                            "HTML" -> Icons.Default.Html
                            "FLASH" -> Icons.Default.FlashOn
                            "VIDEO" -> Icons.Default.Videocam
                            "AUDIO" -> Icons.Default.MusicNote
                            "DOC" -> Icons.AutoMirrored.Filled.InsertDriveFile
                            "ARCHIVE" -> Icons.Default.FolderZip
                            else -> Icons.Default.FilePresent
                        },
                        contentDescription = null,
                        tint = when (game.gameType) {
                            "HTML" -> MaterialTheme.colorScheme.primary
                            "FLASH" -> MaterialTheme.colorScheme.tertiary
                            "VIDEO" -> Color(0xFFD32F2F)
                            "AUDIO" -> Color(0xFF388E3C)
                            "DOC" -> Color(0xFF1976D2)
                            "ARCHIVE" -> Color(0xFFF57C00)
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Metadata Column
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = game.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (game.isFavorite) {
                            Icon(
                                Icons.Default.Favorite,
                                contentDescription = "Favorite",
                                tint = Color.Red,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = categoryBadge,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(text = sizeString, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = dateString, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                }

                // Play / Open Button (for Completed files)
                if (game.status == "COMPLETED") {
                    IconButton(onClick = onPlay) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Play/Open",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Options Menu
                var showMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        if (game.status == "COMPLETED") {
                            DropdownMenuItem(
                                text = { Text("فتح / تشغيل (Play/Open)") },
                                onClick = { showMenu = false; onPlay() },
                                leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) }
                            )
                        }
                        if (game.status == "FAILED") {
                            DropdownMenuItem(
                                text = { Text("إعادة المحاولة (Retry)") },
                                onClick = { showMenu = false; onRestart() },
                                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(if (game.isFavorite) "إزالة من المفضلة" else "إضافة للمفضلة") },
                            onClick = { showMenu = false; onToggleFavorite() },
                            leadingIcon = {
                                Icon(
                                    if (game.isFavorite) Icons.Default.FavoriteBorder else Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = if (game.isFavorite) Color.Gray else Color.Red
                                )
                            }
                        )
                        if (game.status == "COMPLETED") {
                            DropdownMenuItem(
                                text = { Text("حفظ بالهاتف (Export File)") },
                                onClick = { showMenu = false; onExportZip() },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("حذف (Delete)", color = Color.Red) },
                            onClick = { showMenu = false; onDelete() },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) }
                        )
                    }
                }
            }

            // Real-time Progress Display
            if (game.status == "DOWNLOADING") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = game.speedInfo,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        val percentText = (progress * 100).toInt()
                        Text(
                            text = "$percentText%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    )
                }
            } else if (game.status == "FAILED") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    Text(
                        text = "فشل: ${game.speedInfo.ifBlank { "خطأ غير معروف" }}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "إعادة المحاولة",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier
                            .clickable { onRestart() }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

/**
 * Dialog to add a direct download URL task manually.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectDownloadDialog(
    onDismiss: () -> Unit,
    onStartDownload: (url: String, title: String, category: String, threads: Int) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("VIDEO") }
    var threads by remember { mutableStateOf(8) }

    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "إضافة رابط تحميل مباشر",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Right
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("رابط التحميل المباشر (Direct URL)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("اسم الملف (File Title)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Category Chips Selector
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("نوع الملف (File Type):", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    val types = listOf(
                        "VIDEO" to "🎬 فيديو",
                        "AUDIO" to "🎵 صوتي",
                        "DOC" to "📄 مستند",
                        "ARCHIVE" to "📦 مضغوط",
                        "HTML" to "🎮 ألعاب ويب"
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        types.take(3).forEach { (type, label) ->
                            FilterChip(
                                selected = category == type,
                                onClick = { category = type },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        types.drop(3).forEach { (type, label) ->
                            FilterChip(
                                selected = category == type,
                                onClick = { category = type },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                // Turbo Streams Accelerator Selector
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("سرعة تسريع التحميل (Threads Accelerator):", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(4, 8, 16).forEach { numStreams ->
                            FilterChip(
                                selected = threads == numStreams,
                                onClick = { threads = numStreams },
                                label = { Text("$numStreams Streams (سريع)", fontSize = 11.sp) },
                                leadingIcon = if (threads == numStreams) {
                                    { Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(12.dp)) }
                                } else null
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                onClick = {
                    if (url.isBlank()) {
                        Toast.makeText(context, "الرجاء إدخال الرابط", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val finalTitle = title.ifBlank {
                        GameDownloader.getFileNameFromUrl(url).ifBlank { "Download_Task" }
                    }
                    onStartDownload(url, finalTitle, category, threads)
                }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("ابدأ التحميل", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

/**
 * Save file to Android public Downloads directory
 */
private fun exportFileToPublicDownloads(context: Context, game: GameEntity) {
    try {
        val srcFile = File(game.zipPath)
        if (!srcFile.exists()) {
            Toast.makeText(context, "الملف غير موجود بالذاكرة!", Toast.LENGTH_SHORT).show()
            return
        }

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val destFile = File(downloadsDir, srcFile.name)

        srcFile.inputStream().use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        Toast.makeText(
            context,
            "تم الحفظ بنجاح في: Downloads/${srcFile.name}",
            Toast.LENGTH_LONG
        ).show()
    } catch (e: Exception) {
        Toast.makeText(context, "فشل حفظ الملف: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
