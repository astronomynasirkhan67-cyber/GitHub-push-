package com.nasir.zip2git.data.zip

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.nasir.zip2git.data.model.FileEntryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

data class ZipExtractResult(
    val extractDir: File,
    val totalExtractedFiles: Int,
    val totalBytes: Long,
    val singleTopLevelFolder: String?,
    val fileTree: List<FileEntryItem>
)

class ZipExtractor {

    companion object {
        const val MAX_GITHUB_FILE_SIZE = 100L * 1024L * 1024L // 100 MB GitHub limit
    }

    fun getZipMetadata(context: Context, uri: Uri): Pair<String, Long> {
        var name = "archive.zip"
        var size = 0L
        val doc = DocumentFile.fromSingleUri(context, uri)
        if (doc != null) {
            name = doc.name ?: name
            size = doc.length()
        }
        return Pair(name, size)
    }

    suspend fun extractZip(
        context: Context,
        zipUri: Uri,
        onProgress: (count: Int, currentName: String) -> Unit
    ): Result<ZipExtractResult> = withContext(Dispatchers.IO) {
        try {
            val targetDir = File(context.cacheDir, "extracted_zip").apply {
                if (exists()) {
                    deleteRecursively()
                }
                mkdirs()
            }

            val targetCanonicalPath = targetDir.canonicalPath
            var extractedCount = 0
            var totalBytes = 0L

            val inputStream: InputStream = context.contentResolver.openInputStream(zipUri)
                ?: return@withContext Result.failure(Exception("Cannot open ZIP input stream"))

            val extractedRelativePaths = mutableListOf<String>()

            ZipInputStream(inputStream).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                val buffer = ByteArray(64 * 1024)

                while (entry != null) {
                    val rawName = entry.name.replace('\\', '/')

                    // Automatically skip .git metadata folder
                    if (rawName.startsWith(".git/") || rawName == ".git") {
                        zis.closeEntry()
                        entry = zis.nextEntry
                        continue
                    }

                    // Zip-Slip protection
                    val destinationFile = File(targetDir, rawName)
                    val destinationCanonical = destinationFile.canonicalPath
                    if (!destinationCanonical.startsWith(targetCanonicalPath + File.separator) &&
                        destinationCanonical != targetCanonicalPath
                    ) {
                        throw SecurityException("Zip Slip detected: $rawName escapes target directory")
                    }

                    if (entry.isDirectory) {
                        destinationFile.mkdirs()
                    } else {
                        destinationFile.parentFile?.mkdirs()
                        FileOutputStream(destinationFile).use { fos ->
                            var bytesRead: Int
                            while (zis.read(buffer).also { bytesRead = it } != -1) {
                                fos.write(buffer, 0, bytesRead)
                                totalBytes += bytesRead
                            }
                        }
                        extractedCount++
                        extractedRelativePaths.add(rawName)
                        onProgress(extractedCount, destinationFile.name)
                    }

                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            val singleTopFolder = detectSingleTopFolder(targetDir)
            val tree = buildFileTree(targetDir)

            Result.success(
                ZipExtractResult(
                    extractDir = targetDir,
                    totalExtractedFiles = extractedCount,
                    totalBytes = totalBytes,
                    singleTopLevelFolder = singleTopFolder,
                    fileTree = tree
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun detectSingleTopFolder(targetDir: File): String? {
        val rootFiles = targetDir.listFiles() ?: return null
        if (rootFiles.size == 1 && rootFiles[0].isDirectory) {
            return rootFiles[0].name
        }
        return null
    }

    fun buildFileTree(baseDir: File): List<FileEntryItem> {
        val rootItems = mutableListOf<FileEntryItem>()
        val files = baseDir.listFiles()?.sortedWith(
            compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() }
        ) ?: return emptyList()

        for (file in files) {
            if (file.name == ".git") continue
            rootItems.add(createNode(file, baseDir))
        }
        return rootItems
    }

    private fun createNode(file: File, baseDir: File): FileEntryItem {
        val relative = file.relativeTo(baseDir).path.replace('\\', '/')
        val isDir = file.isDirectory
        val size = if (isDir) 0L else file.length()
        val exceedsLimit = size > MAX_GITHUB_FILE_SIZE

        val node = FileEntryItem(
            relativePath = relative,
            name = file.name,
            isDirectory = isDir,
            sizeBytes = size,
            isSelected = !exceedsLimit,
            isExcluded = exceedsLimit,
            exclusionReason = if (exceedsLimit) "Exceeds GitHub's 100 MB limit" else null
        )

        if (isDir) {
            val childrenFiles = file.listFiles()?.sortedWith(
                compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() }
            ) ?: emptyList()

            for (child in childrenFiles) {
                if (child.name == ".git") continue
                node.children.add(createNode(child, baseDir))
            }
        }

        return node
    }

    fun flattenSelectedFiles(
        items: List<FileEntryItem>,
        baseDir: File,
        stripSingleTopFolder: String? = null
    ): List<Pair<String, File>> {
        val result = mutableListOf<Pair<String, File>>()

        fun traverse(item: FileEntryItem) {
            if (!item.isSelected || item.isExcluded) return

            if (item.isDirectory) {
                for (child in item.children) {
                    traverse(child)
                }
            } else {
                val physicalFile = File(baseDir, item.relativePath)
                if (physicalFile.exists() && physicalFile.isFile) {
                    var gitPath = item.relativePath
                    if (stripSingleTopFolder != null && gitPath.startsWith("$stripSingleTopFolder/")) {
                        gitPath = gitPath.removePrefix("$stripSingleTopFolder/")
                    }
                    if (gitPath.isNotBlank()) {
                        result.add(Pair(gitPath, physicalFile))
                    }
                }
            }
        }

        for (item in items) {
            traverse(item)
        }
        return result
    }
}
