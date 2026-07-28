package com.example.ui

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.GameEntity
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
    var selectedTabFilter by remember { mutableStateOf("ALL") } // "ALL", "FAVORITE", "HTML", "FLASH", "VIDEO", "FILE"

    // Filtered lists
    val filteredGames = remember(allGames, searchQuery, selectedTabFilter) {
        allGames.filter { game ->
            val matchesSearch = game.title.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (selectedTabFilter) {
                "FAVORITE" -> game.isFavorite
                "HTML" -> game.gameType == "HTML"
                "FLASH" -> game.gameType == "FLASH"
                "VIDEO" -> game.gameType == "VIDEO"
                "FILE" -> game.gameType == "FILE"
                else -> true
            }
            matchesSearch && matchesFilter
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Downloaded Source Codes",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().testTag("downloads_search_bar"),
            placeholder = { Text("Search downloaded games...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        // Filter chips row
        ScrollableTabRow(
            selectedTabIndex = when (selectedTabFilter) {
                "ALL" -> 0
                "FAVORITE" -> 1
                "HTML" -> 2
                "FLASH" -> 3
                "VIDEO" -> 4
                "FILE" -> 5
                else -> 0
            },
            edgePadding = 0.dp,
            divider = {},
            containerColor = Color.Transparent,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = selectedTabFilter == "ALL",
                onClick = { selectedTabFilter = "ALL" },
                text = { Text("All (${allGames.size})") }
            )
            Tab(
                selected = selectedTabFilter == "FAVORITE",
                onClick = { selectedTabFilter = "FAVORITE" },
                text = { Text("Favorites (${allGames.count { it.isFavorite }})") }
            )
            Tab(
                selected = selectedTabFilter == "HTML",
                onClick = { selectedTabFilter = "HTML" },
                text = { Text("HTML Games (${allGames.count { it.gameType == "HTML" }})") }
            )
            Tab(
                selected = selectedTabFilter == "FLASH",
                onClick = { selectedTabFilter = "FLASH" },
                text = { Text("Flash (${allGames.count { it.gameType == "FLASH" }})") }
            )
            Tab(
                selected = selectedTabFilter == "VIDEO",
                onClick = { selectedTabFilter = "VIDEO" },
                text = { Text("Videos (${allGames.count { it.gameType == "VIDEO" }})") }
            )
            Tab(
                selected = selectedTabFilter == "FILE",
                onClick = { selectedTabFilter = "FILE" },
                text = { Text("Files (${allGames.count { it.gameType == "FILE" }})") }
            )
        }

        // List
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
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "No games downloaded yet.",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Browse websites in the Browser tab, and click 'Download Source' to download HTML or Flash games offline.",
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredGames, key = { it.id }) { game ->
                    GameDownloadCard(
                        game = game,
                        onPlay = { viewModel.playOfflineGame(game) },
                        onToggleFavorite = { viewModel.toggleFavorite(game) },
                        onDelete = { viewModel.deleteGame(context, game) },
                        onExportZip = { exportZipToPublicDownloads(context, game) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GameDownloadCard(
    game: GameEntity,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onExportZip: () -> Unit
) {
    val dateString = remember(game.timestamp) {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        sdf.format(Date(game.timestamp))
    }

    val sizeString = remember(game.sizeBytes) {
        val df = DecimalFormat("#.##")
        val kb = game.sizeBytes / 1024.0
        val mb = kb / 1024.0
        if (mb >= 1.0) {
            "${df.format(mb)} MB"
        } else {
            "${df.format(kb)} KB"
        }
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("downloaded_game_item_${game.id}")
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon according to Game / Media Type
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        when (game.gameType) {
                            "HTML" -> MaterialTheme.colorScheme.primaryContainer
                            "FLASH" -> MaterialTheme.colorScheme.tertiaryContainer
                            "VIDEO" -> MaterialTheme.colorScheme.secondaryContainer
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
                        else -> Icons.Default.InsertDriveFile
                    },
                    contentDescription = null,
                    tint = when (game.gameType) {
                        "HTML" -> MaterialTheme.colorScheme.primary
                        "FLASH" -> MaterialTheme.colorScheme.tertiary
                        "VIDEO" -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Text Metadata
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = game.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    if (game.isFavorite) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = "Favorite",
                            tint = Color.Red,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = game.gameType, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(text = sizeString, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = dateString, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
            }

            // Quick play Button
            IconButton(onClick = onPlay) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Play offline",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Action dropdown menu
            var showMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Play Game") },
                        onClick = {
                            showMenu = false
                            onPlay()
                        },
                        leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text(if (game.isFavorite) "Unfavorite" else "Favorite") },
                        onClick = {
                            showMenu = false
                            onToggleFavorite()
                        },
                        leadingIcon = {
                            Icon(
                                if (game.isFavorite) Icons.Default.FavoriteBorder else Icons.Default.Favorite,
                                contentDescription = null,
                                tint = if (game.isFavorite) Color.Gray else Color.Red
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Export Source (.zip)") },
                        onClick = {
                            showMenu = false
                            onExportZip()
                        },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete Package", color = Color.Red) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) }
                    )
                }
            }
        }
    }
}

/**
 * Copies the game's compiled ZIP file to the device's public Downloads directory.
 */
private fun exportZipToPublicDownloads(context: Context, game: GameEntity) {
    try {
        val srcFile = File(game.zipPath)
        if (!srcFile.exists()) {
            Toast.makeText(context, "Source package not found!", Toast.LENGTH_SHORT).show()
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
            "Saved successfully to: Downloads/${srcFile.name}",
            Toast.LENGTH_LONG
        ).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
