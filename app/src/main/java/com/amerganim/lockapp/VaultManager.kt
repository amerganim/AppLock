package com.amerganim.lockapp

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.File

/** A media item stored (encrypted) in the vault. */
data class VaultEntry(val file: File, val isVideo: Boolean) {
    val id: String get() = file.name
}

/**
 * Encrypted photo/video vault. Imported media is copied into app-private internal
 * storage and encrypted at rest with [EncryptedFile] (AES-256). Originals are never
 * uploaded and are unreadable without the device's keystore-backed master key.
 */
object VaultManager {

    private fun dir(context: Context): File =
        File(context.filesDir, "vault").apply { mkdirs() }

    private fun masterKey(context: Context): MasterKey =
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()

    private fun encryptedFile(context: Context, file: File): EncryptedFile =
        EncryptedFile.Builder(
            context, file, masterKey(context),
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()

    /** Import a picked image/video into the vault. Returns the stored entry, or null. */
    fun importMedia(context: Context, uri: Uri): VaultEntry? {
        val isVideo = context.contentResolver.getType(uri)?.startsWith("video") == true
        val tag = if (isVideo) "vid" else "img"
        val unique = "${System.currentTimeMillis()}-${java.util.UUID.randomUUID().toString().take(8)}"
        val dest = File(dir(context), "${unique}_${tag}.enc")
        return runCatching {
            context.contentResolver.openInputStream(uri)!!.use { input ->
                encryptedFile(context, dest).openFileOutput().use { output ->
                    input.copyTo(output)
                }
            }
            VaultEntry(dest, isVideo)
        }.getOrElse {
            dest.delete()
            null
        }
    }

    /** Vault contents, newest first. */
    fun list(context: Context): List<VaultEntry> =
        dir(context).listFiles()
            ?.filter { it.extension == "enc" }
            ?.sortedByDescending { it.name }
            ?.map { VaultEntry(it, it.nameWithoutExtension.endsWith("_vid")) }
            ?: emptyList()

    fun count(context: Context): Int =
        dir(context).listFiles()?.count { it.extension == "enc" } ?: 0

    private fun decryptBytes(context: Context, entry: VaultEntry): ByteArray =
        encryptedFile(context, entry.file).openFileInput().use { it.readBytes() }

    /** Decrypt to a cache file (for full-screen viewing or external playback). */
    fun decryptToCache(context: Context, entry: VaultEntry): File {
        val ext = if (entry.isVideo) "mp4" else "jpg"
        val out = File(context.cacheDir, "view_${entry.file.nameWithoutExtension}.$ext")
        out.writeBytes(decryptBytes(context, entry))
        return out
    }

    /** A scaled thumbnail bitmap for the grid. */
    fun thumbnail(context: Context, entry: VaultEntry, reqWidth: Int = 400): Bitmap? = runCatching {
        val bytes = decryptBytes(context, entry)
        if (entry.isVideo) {
            val temp = File(context.cacheDir, "thumb_${entry.file.name}.mp4")
            temp.writeBytes(bytes)
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(temp.path)
                retriever.getFrameAtTime(0)
            } finally {
                retriever.release()
                temp.delete()
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / sample > reqWidth) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        }
    }.getOrNull()

    fun delete(entry: VaultEntry) {
        entry.file.delete()
    }

    fun clearViewCache(context: Context) {
        context.cacheDir.listFiles()
            ?.filter { it.name.startsWith("view_") || it.name.startsWith("thumb_") }
            ?.forEach { it.delete() }
    }

    /** Export a vault item back to the public gallery (Pictures/Movies/LockApp). */
    fun exportToGallery(context: Context, entry: VaultEntry): Boolean = runCatching {
        val bytes = decryptBytes(context, entry)
        val resolver = context.contentResolver
        val collection = if (entry.isVideo) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val name = "LockApp_${entry.file.nameWithoutExtension}." + if (entry.isVideo) "mp4" else "jpg"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, if (entry.isVideo) "video/mp4" else "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val sub = if (entry.isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$sub/LockApp")
            }
        }
        val item = resolver.insert(collection, values) ?: return false
        resolver.openOutputStream(item).use { it!!.write(bytes) }
        true
    }.getOrDefault(false)
}
