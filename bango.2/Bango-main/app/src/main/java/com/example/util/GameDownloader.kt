package com.example.util

import android.content.Context
import android.util.Log
import com.example.data.GameEntity
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLException

object GameDownloader {
    private const val TAG = "GameDownloader"
    private val client = OkHttpClient.Builder().build()

    /**
     * YoutubeDL Downloader
     */
    suspend fun downloadWithYoutubeDL(
        context: Context,
        url: String,
        title: String,
        onProgress: (String) -> Unit
    ): GameEntity {
        val gameId = UUID.randomUUID().toString().take(8)
        val safeTitle = title.replace("[^a-zA-Z0-9]".toRegex(), "_")

        val downloadsDir = File(context.filesDir, "downloads")
        if (!downloadsDir.exists()) downloadsDir.mkdirs()

        val request = YoutubeDLRequest(url)
        request.addOption("-o", File(downloadsDir, "${safeTitle}_$gameId.%(ext)s").absolutePath)

        // You can add more options if needed
        // request.addOption("-f", "bestvideo+bestaudio/best")

        onProgress("Initializing YoutubeDL...")

        withContext(Dispatchers.IO) {
            YoutubeDL.getInstance().execute(request) { progress, etaInSeconds, line ->
                val progressStr = String.format("%.2f", progress)
                onProgress("Downloading: $progressStr% (ETA: $etaInSeconds s)\n$line")
            }
        }

        // Find the downloaded file
        var downloadedFile: File? = null
        val files = downloadsDir.listFiles()
        if (files != null) {
            for (f in files) {
                if (f.name.startsWith("${safeTitle}_$gameId")) {
                    downloadedFile = f
                    break
                }
            }
        }

        if (downloadedFile == null || !downloadedFile.exists()) {
            throw IOException("Download failed, output file not found")
        }

        val ext = downloadedFile.extension.uppercase()
        val type = if (ext == "MP4" || ext == "MKV" || ext == "WEBM") "VIDEO" else "AUDIO"

        return GameEntity(
            title = title,
            sourceUrl = url,
            localFolder = downloadedFile.parentFile?.absolutePath ?: downloadsDir.absolutePath,
            zipPath = downloadedFile.absolutePath,
            gameType = type,
            sizeBytes = downloadedFile.length()
        )
    }

    /**
     * Downloads an HTML webpage, parses its scripts/stylesheets/images,
     * downloads them, rewrites references to flat local filenames,
     * saves them to internal storage, and zips the results.
     */
    suspend fun downloadHtmlGameSource(
        context: Context,
        urlString: String,
        title: String,
        onProgress: (String) -> Unit
    ): GameEntity {
        onProgress("Connecting to $urlString...")
        val mainUrl = URL(urlString)

        // 1. Fetch main HTML content
        val request = Request.Builder().url(mainUrl).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("Failed to download main HTML page. Code: ${response.code}")
        }
        var htmlContent = response.body?.string() ?: throw IOException("Empty HTML body")

        // Create unique directories for the game
        val gameId = UUID.randomUUID().toString().take(8)
        val gamesDir = File(context.filesDir, "games")
        if (!gamesDir.exists()) gamesDir.mkdirs()

        val localGameFolder = File(gamesDir, "html_$gameId")
        localGameFolder.mkdirs()

        onProgress("Parsing page resources...")

        // Simple Regexes to extract assets
        val srcRegex = """(?i)src=["']([^"']+)["']""".toRegex()
        val hrefRegex = """(?i)href=["']([^"']+\.css[^"']*)["']""".toRegex()

        val assetsToDownload = mutableSetOf<String>()

        // Extract all scripts, images, etc.
        srcRegex.findAll(htmlContent).forEach { match ->
            val src = match.groupValues[1]
            if (!src.startsWith("data:") && !src.startsWith("javascript:")) {
                assetsToDownload.add(src)
            }
        }

        // Extract CSS files
        hrefRegex.findAll(htmlContent).forEach { match ->
            val href = match.groupValues[1]
            assetsToDownload.add(href)
        }

        var downloadedCount = 0
        var totalBytesDownloaded = 0L

        onProgress("Scraping and downloading ${assetsToDownload.size} assets...")

        for (asset in assetsToDownload) {
            try {
                // Resolve absolute URL
                val assetUrl = resolveUrl(mainUrl, asset)
                val fileName = getFileNameFromUrl(assetUrl)
                if (fileName.isEmpty()) continue

                onProgress("Downloading ($downloadedCount/${assetsToDownload.size}): $fileName")

                val assetRequest = Request.Builder().url(assetUrl).build()
                client.newCall(assetRequest).execute().use { assetResponse ->
                    if (assetResponse.isSuccessful) {
                        val body = assetResponse.body
                        if (body != null) {
                            val targetFile = File(localGameFolder, fileName)
                            val bytes = body.bytes()
                            targetFile.writeBytes(bytes)
                            totalBytesDownloaded += bytes.size

                            // Rewrite HTML reference to local flat filename
                            htmlContent = htmlContent.replace(asset, fileName)
                            downloadedCount++
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download asset: $asset", e)
                // Continue downloading other assets
            }
        }

        // Save rewritten HTML as index.html
        val indexFile = File(localGameFolder, "index.html")
        indexFile.writeText(htmlContent)
        totalBytesDownloaded += htmlContent.toByteArray().size

        // 2. Compress the folder into a single ZIP file
        onProgress("Compressing source code into a ZIP package...")
        val downloadsDir = File(context.filesDir, "downloads")
        if (!downloadsDir.exists()) downloadsDir.mkdirs()

        val safeTitle = title.replace("[^a-zA-Z0-9]".toRegex(), "_")
        val zipFile = File(downloadsDir, "html_${safeTitle}_$gameId.zip")

        zipFolder(localGameFolder, zipFile)
        onProgress("Compression complete! Saved to ${zipFile.name}")

        return GameEntity(
            title = title,
            sourceUrl = urlString,
            localFolder = localGameFolder.absolutePath,
            zipPath = zipFile.absolutePath,
            gameType = "HTML",
            sizeBytes = totalBytesDownloaded + zipFile.length()
        )
    }

    /**
     * Downloads a pre-packaged HTML game in a ZIP, extracts it, and creates a ZIP copy.
     */
    suspend fun downloadDirectZipGame(
        context: Context,
        zipUrlString: String,
        title: String,
        onProgress: (String) -> Unit
    ): GameEntity {
        onProgress("Downloading ZIP archive from $zipUrlString...")

        val gameId = UUID.randomUUID().toString().take(8)
        val downloadsDir = File(context.filesDir, "downloads")
        if (!downloadsDir.exists()) downloadsDir.mkdirs()

        val safeTitle = title.replace("[^a-zA-Z0-9]".toRegex(), "_")
        val localZipFile = File(downloadsDir, "zip_${safeTitle}_$gameId.zip")

        // Download Zip File
        val request = Request.Builder().url(zipUrlString).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Failed to download ZIP file. Code: ${response.code}")
            }
            val body = response.body ?: throw IOException("Empty ZIP file content")
            localZipFile.outputStream().use { fos ->
                body.byteStream().copyTo(fos)
            }
        }

        onProgress("Extracting game files...")
        val gamesDir = File(context.filesDir, "games")
        if (!gamesDir.exists()) gamesDir.mkdirs()

        val localGameFolder = File(gamesDir, "zip_$gameId")
        localGameFolder.mkdirs()

        unzip(localZipFile, localGameFolder)

        // Scan extracted files to check for index.html, if none found in root, we can search deeper
        val indexFile = findIndexHtml(localGameFolder)
        if (indexFile == null) {
            onProgress("Warning: index.html not found, but ZIP extracted successfully")
        } else {
            onProgress("Found game launcher: ${indexFile.name}")
        }

        return GameEntity(
            title = title,
            sourceUrl = zipUrlString,
            localFolder = localGameFolder.absolutePath,
            zipPath = localZipFile.absolutePath,
            gameType = "HTML",
            sizeBytes = localZipFile.length()
        )
    }

    /**
     * Downloads a Flash (.swf) game file.
     * Since Flash games are a single SWF, downloading is direct.
     */
    suspend fun downloadFlashGameSource(
        context: Context,
        swfUrlString: String,
        title: String,
        onProgress: (String) -> Unit
    ): GameEntity {
        onProgress("Downloading Flash SWF from $swfUrlString...")

        val gameId = UUID.randomUUID().toString().take(8)

        val gamesDir = File(context.filesDir, "games")
        if (!gamesDir.exists()) gamesDir.mkdirs()

        val localGameFolder = File(gamesDir, "flash_$gameId")
        localGameFolder.mkdirs()

        val safeTitle = title.replace("[^a-zA-Z0-9]".toRegex(), "_")
        val swfFile = File(localGameFolder, "$safeTitle.swf")

        val request = Request.Builder().url(swfUrlString).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Failed to download SWF. Code: ${response.code}")
            }
            val body = response.body ?: throw IOException("Empty SWF content")
            swfFile.outputStream().use { fos ->
                body.byteStream().copyTo(fos)
            }
        }

        onProgress("Saving SWF file...")
        val downloadsDir = File(context.filesDir, "downloads")
        if (!downloadsDir.exists()) downloadsDir.mkdirs()

        // Copy to a compressed ZIP file as requested
        val zipFile = File(downloadsDir, "flash_${safeTitle}_$gameId.zip")
        onProgress("Creating ZIP backup of SWF package...")
        zipFolder(localGameFolder, zipFile)

        return GameEntity(
            title = title,
            sourceUrl = swfUrlString,
            localFolder = localGameFolder.absolutePath,
            zipPath = zipFile.absolutePath,
            gameType = "FLASH",
            sizeBytes = swfFile.length() + zipFile.length()
        )
    }

    // Extract file extension from URL
    fun getFileExtensionFromUrl(urlString: String): String {
        return try {
            val url = URL(urlString)
            val path = url.path
            if (path.contains(".")) {
                val ext = path.substring(path.lastIndexOf(".") + 1)
                if (ext.contains("?")) ext.substring(0, ext.indexOf("?")) else ext
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec >= 1024 * 1024 -> String.format("%.2f MB/s", bytesPerSec / (1024.0 * 1024.0))
            bytesPerSec >= 1024 -> String.format("%.1f KB/s", bytesPerSec / 1024.0)
            else -> "$bytesPerSec B/s"
        }
    }

    /**
     * IDM Multi-Segment Accelerated & Resumable File/Video/Media Downloader.
     * Splits file into multiple parallel streams (e.g. 8 threads) using HTTP Range headers,
     * updates live transfer speeds, and seamlessly merges chunks without file corruption.
     */
    suspend fun downloadResumableFile(
        context: Context,
        fileUrl: String,
        title: String,
        fileTypeCategory: String, // "VIDEO", "AUDIO", "DOC", "ARCHIVE", "FILE"
        numSegments: Int = 8,
        userAgent: String? = null,
        cookies: String? = null,
        referer: String? = null,
        onProgress: (String, Long, Long, Long, Int) -> Unit
    ): GameEntity {
        val gameId = UUID.randomUUID().toString().take(8)
        val safeTitle = title.replace("[^a-zA-Z0-9]".toRegex(), "_")

        val downloadsDir = File(context.filesDir, "downloads")
        if (!downloadsDir.exists()) downloadsDir.mkdirs()

        var ext = getFileExtensionFromUrl(fileUrl)
        if (ext.isEmpty()) {
            ext = when (fileTypeCategory) {
                "VIDEO" -> "mp4"
                "AUDIO" -> "mp3"
                "DOC" -> "pdf"
                "ARCHIVE" -> "zip"
                else -> "bin"
            }
        }

        val targetFile = File(downloadsDir, "${safeTitle}_$gameId.$ext")
        val tempDir = File(downloadsDir, "parts_$gameId")
        if (!tempDir.exists()) tempDir.mkdirs()

        onProgress("Checking server connection & range support...", 0L, 0L, 0L, 1)

        val defaultUa = if (!userAgent.isNullOrBlank()) userAgent else "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

        fun applyHeaders(builder: Request.Builder) {
            builder.header("User-Agent", defaultUa)
            if (!cookies.isNullOrBlank()) {
                builder.header("Cookie", cookies)
            }
            if (!referer.isNullOrBlank()) {
                builder.header("Referer", referer)
            }
        }

        // 1. Check HTTP headers & range support
        val headReqBuilder = Request.Builder().url(fileUrl)
        applyHeaders(headReqBuilder)
        val headReq = headReqBuilder.build()

        var totalBytes = -1L
        var acceptsRanges = false

        try {
            client.newCall(headReq).execute().use { resp ->
                val rangeHeader = resp.header("Accept-Ranges")
                val lengthHeader = resp.header("Content-Length")
                acceptsRanges = rangeHeader?.contains("bytes", ignoreCase = true) == true || resp.code == 206
                totalBytes = lengthHeader?.toLongOrNull() ?: -1L
            }
        } catch (e: Exception) {
            Log.w(TAG, "HEAD request failed, using single stream fallback", e)
        }

        val activeThreads = if (acceptsRanges && totalBytes > 2 * 1024 * 1024) numSegments else 1

        if (activeThreads > 1) {
            val segmentSize = totalBytes / activeThreads
            val downloadedBytesAtomic = java.util.concurrent.atomic.AtomicLong(0L)
            var lastTime = System.currentTimeMillis()
            var lastBytes = 0L

            val segmentParts = List(activeThreads) { i ->
                File(tempDir, "part_$i.tmp")
            }

            segmentParts.forEach { downloadedBytesAtomic.addAndGet(if (it.exists()) it.length() else 0L) }

            coroutineScope {
                val jobs = segmentParts.mapIndexed { index, partFile ->
                    async(Dispatchers.IO) {
                        val startByte = index * segmentSize
                        val endByte = if (index == activeThreads - 1) totalBytes - 1 else (startByte + segmentSize - 1)
                        val existingLength = if (partFile.exists()) partFile.length() else 0L

                        if (existingLength < (endByte - startByte + 1)) {
                            val currentStart = startByte + existingLength
                            val reqBuilder = Request.Builder()
                                .url(fileUrl)
                                .header("Range", "bytes=$currentStart-$endByte")
                            applyHeaders(reqBuilder)
                            val req = reqBuilder.build()

                            client.newCall(req).execute().use { resp ->
                                if (resp.isSuccessful || resp.code == 206) {
                                    val body = resp.body ?: return@use
                                    val fos = FileOutputStream(partFile, true)
                                    body.byteStream().use { input ->
                                        val buffer = ByteArray(32 * 1024)
                                        var read: Int
                                        while (input.read(buffer).also { read = it } != -1) {
                                            fos.write(buffer, 0, read)
                                            val totalDl = downloadedBytesAtomic.addAndGet(read.toLong())

                                            val now = System.currentTimeMillis()
                                            if (now - lastTime >= 350) {
                                                val timeDiffSec = (now - lastTime) / 1000.0
                                                val speed = if (timeDiffSec > 0) ((totalDl - lastBytes) / timeDiffSec).toLong() else 0L
                                                lastTime = now
                                                lastBytes = totalDl

                                                val speedText = formatSpeed(speed)
                                                val progressMsg = "Accelerated ($activeThreads Streams): $speedText"
                                                onProgress(progressMsg, totalDl, totalBytes, speed, activeThreads)
                                            }
                                        }
                                        fos.flush()
                                    }
                                }
                            }
                        }
                    }
                }
                jobs.awaitAll()
            }

            // Assembly step
            onProgress("Merging $activeThreads chunks into final file...", totalBytes, totalBytes, 0L, 1)
            FileOutputStream(targetFile, false).use { out ->
                segmentParts.forEach { part ->
                    if (part.exists()) {
                        part.inputStream().use { inStream ->
                            inStream.copyTo(out)
                        }
                        part.delete()
                    }
                }
            }
            tempDir.deleteRecursively()
        } else {
            // Single stream fallback with resume
            val reqBuilder = Request.Builder().url(fileUrl)
            applyHeaders(reqBuilder)

            val existingLength = if (targetFile.exists()) targetFile.length() else 0L
            if (existingLength > 0) reqBuilder.header("Range", "bytes=$existingLength-")

            client.newCall(reqBuilder.build()).execute().use { response ->
                if (!response.isSuccessful && response.code != 206) {
                    throw IOException("Server returned HTTP ${response.code}")
                }
                val body = response.body ?: throw IOException("Empty body")
                val isPartial = response.code == 206
                val cLength = body.contentLength()
                if (totalBytes <= 0) totalBytes = if (isPartial) existingLength + cLength else cLength

                val appendMode = isPartial && existingLength > 0
                val fos = FileOutputStream(targetFile, appendMode)

                var downloaded = if (appendMode) existingLength else 0L
                var lastTime = System.currentTimeMillis()
                var lastBytes = downloaded

                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        fos.write(buffer, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastTime >= 350) {
                            val timeDiff = (now - lastTime) / 1000.0
                            val speed = if (timeDiff > 0) ((downloaded - lastBytes) / timeDiff).toLong() else 0L
                            lastTime = now
                            lastBytes = downloaded

                            val speedText = formatSpeed(speed)
                            onProgress("Downloading: $speedText", downloaded, totalBytes, speed, 1)
                        }
                    }
                    fos.flush()
                }
            }
        }

        // Export copy to Public External Storage Downloads folder so user sees real file in phone
        var publicFilePath = targetFile.absolutePath
        try {
            val publicDownloads = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            if (publicDownloads != null) {
                if (!publicDownloads.exists()) publicDownloads.mkdirs()
                val publicFile = File(publicDownloads, "${safeTitle}_$gameId.$ext")
                targetFile.copyTo(publicFile, overwrite = true)
                publicFilePath = publicFile.absolutePath

                // Notify MediaScanner so Android Gallery & Music Player register the file immediately
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(publicFile.absolutePath),
                    arrayOf(if (fileTypeCategory == "VIDEO") "video/mp4" else if (fileTypeCategory == "AUDIO") "audio/mpeg" else null),
                    null
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not copy to public downloads folder", e)
        }

        // Save local folder structure
        val localFolder = File(context.filesDir, "games/media_$gameId")
        localFolder.mkdirs()
        targetFile.copyTo(File(localFolder, targetFile.name), overwrite = true)

        val zipFile = File(downloadsDir, "${safeTitle}_$gameId.zip")
        zipFolder(localFolder, zipFile)

        return GameEntity(
            title = title,
            sourceUrl = fileUrl,
            localFolder = localFolder.absolutePath,
            zipPath = publicFilePath,
            gameType = fileTypeCategory,
            timestamp = System.currentTimeMillis(),
            sizeBytes = targetFile.length(),
            downloadedBytes = targetFile.length(),
            status = "COMPLETED",
            speedInfo = "Accelerated ($activeThreads Streams)",
            numSegments = activeThreads
        )
    }

    /**
     * Native Android DownloadManager trigger for system notification bar downloading.
     */
    fun enqueueSystemDownload(
        context: Context,
        url: String,
        title: String,
        userAgent: String? = null,
        cookies: String? = null,
        referer: String? = null
    ): Long {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
        val uri = android.net.Uri.parse(url)
        val request = android.app.DownloadManager.Request(uri)

        val safeTitle = title.replace("[^a-zA-Z0-9_ -]".toRegex(), "_")
        var ext = getFileExtensionFromUrl(url)
        if (ext.isEmpty()) ext = "mp4"

        request.setTitle(title)
        request.setDescription("Downloading $title...")
        request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        request.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, "$safeTitle.$ext")

        val ua = if (!userAgent.isNullOrBlank()) userAgent else "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36"
        request.addRequestHeader("User-Agent", ua)
        if (!cookies.isNullOrBlank()) {
            request.addRequestHeader("Cookie", cookies)
        }
        if (!referer.isNullOrBlank()) {
            request.addRequestHeader("Referer", referer)
        }

        return downloadManager.enqueue(request)
    }

    // Helper to find index.html recursively
    private fun findIndexHtml(dir: File): File? {
        val files = dir.listFiles() ?: return null
        for (file in files) {
            if (file.isFile && file.name.equals("index.html", ignoreCase = true)) {
                return file
            }
        }
        for (file in files) {
            if (file.isDirectory) {
                val found = findIndexHtml(file)
                if (found != null) return found
            }
        }
        return null
    }

    // Helper to resolve absolute URL from base page URL
    private fun resolveUrl(baseUrl: URL, relativePath: String): String {
        return try {
            URL(baseUrl, relativePath).toString()
        } catch (e: Exception) {
            if (relativePath.startsWith("//")) {
                "https:$relativePath"
            } else if (relativePath.startsWith("/")) {
                "${baseUrl.protocol}://${baseUrl.host}$relativePath"
            } else {
                relativePath
            }
        }
    }

    // Extract file name from URL, removing query parameters
    fun getFileNameFromUrl(urlString: String): String {
        return try {
            val url = URL(urlString)
            var path = url.path
            if (path.contains("/")) {
                path = path.substring(path.lastIndexOf("/") + 1)
            }
            // Sanitize query parameters
            if (path.contains("?")) {
                path = path.substring(0, path.indexOf("?"))
            }
            // Fallback for empty names
            if (path.isEmpty()) {
                "resource_${UUID.randomUUID().toString().take(6)}"
            } else {
                path
            }
        } catch (e: Exception) {
            "resource_${UUID.randomUUID().toString().take(6)}"
        }
    }

    // Helper to zip a directory recursively
    fun zipFolder(sourceFolder: File, zipFile: File) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
            zipFolderContents(sourceFolder, sourceFolder, zos)
        }
    }

    private fun zipFolderContents(rootFolder: File, currentFolder: File, zos: ZipOutputStream) {
        val files = currentFolder.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                zipFolderContents(rootFolder, file, zos)
            } else {
                val relativePath = file.absolutePath.substring(rootFolder.absolutePath.length + 1)
                val entry = ZipEntry(relativePath)
                zos.putNextEntry(entry)
                file.inputStream().use { fis ->
                    fis.copyTo(zos)
                }
                zos.closeEntry()
            }
        }
    }

    // Helper to unzip an archive to a destination directory
    fun unzip(zipFile: File, destDir: File) {
        ZipInputStream(BufferedInputStream(zipFile.inputStream())).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val file = File(destDir, entry.name)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    file.outputStream().use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
