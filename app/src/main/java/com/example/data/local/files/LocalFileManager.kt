package com.example.data.local.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

class LocalFileManager(private val context: Context) {

    private val baseDir: File get() = File(context.filesDir, "home_ai").apply { if (!exists()) mkdirs() }
    private val itemsDir: File get() = File(baseDir, "items").apply { if (!exists()) mkdirs() }
    private val locationsDir: File get() = File(baseDir, "locations").apply { if (!exists()) mkdirs() }
    private val sweepsDir: File get() = File(baseDir, "sweeps").apply { if (!exists()) mkdirs() }
    private val exportsDir: File get() = File(baseDir, "exports").apply { if (!exists()) mkdirs() }
    private val tempDir: File get() = File(baseDir, "temporary").apply { if (!exists()) mkdirs() }

    fun getItemDir(itemId: String): File {
        return File(itemsDir, itemId).apply { if (!exists()) mkdirs() }
    }

    fun getItemImagesDir(itemId: String): File {
        return File(getItemDir(itemId), "images").apply { if (!exists()) mkdirs() }
    }

    fun getItemThumbnailsDir(itemId: String): File {
        return File(getItemDir(itemId), "thumbnails").apply { if (!exists()) mkdirs() }
    }

    fun getItemDocumentsDir(itemId: String): File {
        return File(getItemDir(itemId), "documents").apply { if (!exists()) mkdirs() }
    }

    fun getSweepDir(sweepId: String): File {
        return File(sweepsDir, sweepId).apply { if (!exists()) mkdirs() }
    }

    suspend fun saveBitmap(bitmap: Bitmap, targetFile: File, quality: Int = 85): String = withContext(Dispatchers.IO) {
        targetFile.parentFile?.mkdirs()
        FileOutputStream(targetFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
        targetFile.absolutePath
    }

    suspend fun saveImageForItem(itemId: String, bitmap: Bitmap, isPrimary: Boolean = false): Pair<String, String?> = withContext(Dispatchers.IO) {
        val fileId = UUID.randomUUID().toString()
        val imageFile = File(getItemImagesDir(itemId), "img_${fileId}.jpg")
        saveBitmap(bitmap, imageFile, quality = 85)

        // Save thumbnail
        val thumbFile = File(getItemThumbnailsDir(itemId), "thumb_${fileId}.jpg")
        val thumbWidth = 240
        val thumbHeight = (bitmap.height * (thumbWidth.toFloat() / bitmap.width)).toInt()
        val scaledThumb = Bitmap.createScaledBitmap(bitmap, thumbWidth, thumbHeight.coerceAtLeast(1), true)
        saveBitmap(scaledThumb, thumbFile, quality = 70)

        Pair(imageFile.absolutePath, thumbFile.absolutePath)
    }

    suspend fun copyUriToFile(uri: Uri, targetFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            targetFile.parentFile?.mkdirs()
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun saveImportedImageForItem(itemId: String, uri: Uri): Pair<String, String?> = withContext(Dispatchers.IO) {
        val fileId = UUID.randomUUID().toString()
        val imageFile = File(getItemImagesDir(itemId), "img_${fileId}.jpg")
        copyUriToFile(uri, imageFile)

        // Generate thumbnail
        val thumbFile = File(getItemThumbnailsDir(itemId), "thumb_${fileId}.jpg")
        val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
        if (bitmap != null) {
            val thumbWidth = 240
            val thumbHeight = (bitmap.height * (thumbWidth.toFloat() / bitmap.width)).toInt()
            val scaledThumb = Bitmap.createScaledBitmap(bitmap, thumbWidth, thumbHeight.coerceAtLeast(1), true)
            saveBitmap(scaledThumb, thumbFile, quality = 70)
            Pair(imageFile.absolutePath, thumbFile.absolutePath)
        } else {
            Pair(imageFile.absolutePath, null)
        }
    }

    suspend fun saveDocumentForItem(itemId: String, fileName: String, inputStream: InputStream): String = withContext(Dispatchers.IO) {
        val docFile = File(getItemDocumentsDir(itemId), fileName)
        FileOutputStream(docFile).use { out ->
            inputStream.copyTo(out)
        }
        docFile.absolutePath
    }

    fun createTempFile(prefix: String, suffix: String): File {
        return File.createTempFile(prefix, suffix, tempDir)
    }

    fun clearTemporaryFiles() {
        tempDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    fun calculateChecksum(file: File): String {
        if (!file.exists()) return ""
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead = fis.read(buffer)
            while (bytesRead != -1) {
                md.update(buffer, 0, bytesRead)
                bytesRead = fis.read(buffer)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    data class StorageUsage(
        val totalBytes: Long,
        val photosBytes: Long,
        val videosBytes: Long,
        val documentsBytes: Long,
        val databaseBytes: Long,
        val temporaryBytes: Long
    )

    fun calculateStorageUsage(): StorageUsage {
        val photosBytes = getFolderSize(itemsDir)
        val videosBytes = getFolderSize(sweepsDir)
        val documentsBytes = itemsDir.walkTopDown().filter { it.name.endsWith(".pdf") || it.name.endsWith(".txt") }.sumOf { it.length() }
        val temporaryBytes = getFolderSize(tempDir)
        val dbFile = context.getDatabasePath("home_ai.db")
        val dbBytes = if (dbFile.exists()) dbFile.length() else 0L
        val totalBytes = photosBytes + videosBytes + temporaryBytes + dbBytes

        return StorageUsage(
            totalBytes = totalBytes,
            photosBytes = photosBytes,
            videosBytes = videosBytes,
            documentsBytes = documentsBytes,
            databaseBytes = dbBytes,
            temporaryBytes = temporaryBytes
        )
    }

    private fun getFolderSize(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
