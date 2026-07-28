package com.example.util

import android.content.Context
import android.util.Log
import com.example.data.GameEntity
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object GameDownloader {
    private const val TAG = "GameDownloader"
    private val client = OkHttpClient.Builder().build()

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

    /**
     * Resumable file/video downloader using HTTP Range headers.
     * Supports resuming broken or interrupted downloads without file corruption.
     */
    suspend fun downloadResumableFile(
        context: Context,
        fileUrl: String,
        title: String,
        fileTypeCategory: String, // "VIDEO" or "FILE"
        onProgress: (String, Long, Long) -> Unit
    ): GameEntity {
        val gameId = UUID.randomUUID().toString().take(8)
        val safeTitle = title.replace("[^a-zA-Z0-9]".toRegex(), "_")

        val downloadsDir = File(context.filesDir, "downloads")
        if (!downloadsDir.exists()) downloadsDir.mkdirs()

        var ext = getFileExtensionFromUrl(fileUrl)
        if (ext.isEmpty()) {
            ext = if (fileTypeCategory == "VIDEO") "mp4" else "bin"
        }

        val targetFile = File(downloadsDir, "${safeTitle}_$gameId.$ext")
        val existingLength = if (targetFile.exists()) targetFile.length() else 0L

        onProgress("Connecting to server...", existingLength, 0L)

        val requestBuilder = Request.Builder().url(fileUrl)
        if (existingLength > 0) {
            requestBuilder.addHeader("Range", "bytes=$existingLength-")
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful && response.code != 206) {
                throw IOException("Server returned HTTP ${response.code}: ${response.message}")
            }

            val isPartial = response.code == 206
            val body = response.body ?: throw IOException("Empty response body")
            val contentLength = body.contentLength()
            val totalBytes = if (isPartial) existingLength + contentLength else contentLength

            val appendMode = isPartial && existingLength > 0
            val fos = FileOutputStream(targetFile, appendMode)

            body.byteStream().use { inputStream ->
                val buffer = ByteArray(64 * 1024) // 64KB chunks
                var bytesRead: Int
                var currentDownloaded = if (appendMode) existingLength else 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    fos.write(buffer, 0, bytesRead)
                    currentDownloaded += bytesRead
                    val percent = if (totalBytes > 0) (currentDownloaded * 100 / totalBytes).toInt() else -1
                    val progressMsg = if (percent >= 0) "Downloading: $percent% (${currentDownloaded / 1024} KB / ${totalBytes / 1024} KB)" else "Downloaded ${currentDownloaded / 1024} KB..."
                    onProgress(progressMsg, currentDownloaded, totalBytes)
                }
                fos.flush()
            }
        }

        // Create local folder structure
        val localFolder = File(context.filesDir, "games/media_$gameId")
        localFolder.mkdirs()
        targetFile.copyTo(File(localFolder, targetFile.name), overwrite = true)

        val zipFile = File(downloadsDir, "${safeTitle}_$gameId.zip")
        zipFolder(localFolder, zipFile)

        return GameEntity(
            title = title,
            sourceUrl = fileUrl,
            localFolder = localFolder.absolutePath,
            zipPath = zipFile.absolutePath,
            gameType = fileTypeCategory,
            sizeBytes = targetFile.length()
        )
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
    private fun getFileNameFromUrl(urlString: String): String {
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
