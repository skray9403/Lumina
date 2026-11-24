package org.fossify.gallery.engine.vault

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.fossify.gallery.models.Medium
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.security.SecureRandom
import kotlin.math.min

private const val INDEX_AAD_ID = 0x4C554D494E414958L

private data class CachedVaultThumb(val bitmap: ImageBitmap, val byteCount: Int)

object VaultManager {
    private val ioMutex = Mutex()
    private val random = SecureRandom()

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var activeDek: ByteArray? = null

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private val _isStrongBoxBacked = MutableStateFlow(false)
    val isStrongBoxBacked: StateFlow<Boolean> = _isStrongBoxBacked.asStateFlow()

    private val _vaultMedia = MutableStateFlow<List<Medium>>(emptyList())
    val vaultMedia: StateFlow<List<Medium>> = _vaultMedia.asStateFlow()

    private val thumbCache = object : LruCache<Long, CachedVaultThumb>(12 * 1024 * 1024) {
        override fun sizeOf(key: Long, value: CachedVaultThumb): Int = value.byteCount
    }

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    fun vaultUriFor(id: Long): Uri = Uri.parse("${VaultCrypto.VAULT_SCHEME}://item/$id")

    fun parseVaultId(uri: Uri): Long? {
        if (uri.scheme != VaultCrypto.VAULT_SCHEME) return null
        return uri.lastPathSegment?.toLongOrNull()
    }

    private fun vaultDir(context: Context): File = File(context.noBackupFilesDir, "vault").apply { mkdirs() }
    private fun keyFile(context: Context): File = File(vaultDir(context), "vault.key")
    private fun indexFile(context: Context): File = File(vaultDir(context), "vault.idx")
    private fun itemFile(context: Context, id: Long): File = File(vaultDir(context), "item_$id.enc")
    private fun thumbFile(context: Context, id: Long): File = File(vaultDir(context), "thumb_$id.enc")

    /**
     * Prompts StrongBox-bound BiometricPrompt (or Device Credential) and loads the decrypted vault index.
     */
    fun unlock(
        context: Context,
        title: String = "Unlock Locked Folder",
        subtitle: String = "Verify identity to access encrypted items",
        onResult: (Boolean, String?) -> Unit
    ) {
        init(context)
        if (activeDek != null) {
            onResult(true, null)
            return
        }

        VaultCrypto.authenticateAndOpenDek(
            context = context,
            keyFile = keyFile(context),
            title = title,
            subtitle = subtitle
        ) { authResult ->
            when (authResult) {
                is VaultAuthResult.Success -> {
                    activeDek?.fill(0)
                    activeDek = authResult.dek
                    _isStrongBoxBacked.value = authResult.isStrongBoxBacked
                    _isUnlocked.value = true
                    _vaultMedia.value = readIndexSync(context, authResult.dek)
                    onResult(true, null)
                }

                is VaultAuthResult.Failure -> {
                    onResult(false, if (authResult.error == VaultAuthError.CANCELLED) null else authResult.message)
                }
            }
        }
    }

    /** Zeroizes the in-memory DEK and evicts all decrypted vault thumbnails immediately. */
    fun lock() {
        activeDek?.fill(0)
        activeDek = null
        thumbCache.evictAll()
        _vaultMedia.value = emptyList()
        _isUnlocked.value = false
    }

    fun getCachedThumbnail(id: Long): ImageBitmap? {
        if (activeDek == null) return null
        return thumbCache.get(id)?.bitmap
    }

    suspend fun loadThumbnail(id: Long): ImageBitmap? = withContext(Dispatchers.IO) {
        val ctx = appContext ?: return@withContext null
        val dek = activeDek?.copyOf() ?: return@withContext null
        try {
            thumbCache.get(id)?.let { return@withContext it.bitmap }
            val file = thumbFile(ctx, id)
            if (!file.exists()) return@withContext null
            val plainBytes = VaultCrypto.decryptSmallBlob(file.readBytes(), id, dek)
            val bmp = BitmapFactory.decodeByteArray(plainBytes, 0, plainBytes.size) ?: return@withContext null
            val imageBitmap = bmp.asImageBitmap()
            if (activeDek != null) {
                thumbCache.put(id, CachedVaultThumb(imageBitmap, bmp.allocationByteCount))
            }
            imageBitmap
        } catch (_: Exception) {
            null
        } finally {
            dek.fill(0)
        }
    }

    suspend fun decryptMediaBytes(id: Long): ByteArray? = withContext(Dispatchers.IO) {
        val ctx = appContext ?: return@withContext null
        val dek = activeDek?.copyOf() ?: return@withContext null
        try {
            val file = itemFile(ctx, id)
            if (!file.exists()) return@withContext null
            VaultCrypto.decryptFileToBytes(file, dek)
        } catch (_: Exception) {
            null
        } finally {
            dek.fill(0)
        }
    }

    /**
     * Encrypts selected MediaStore items + 360px thumbnails into the private StrongBox vault.
     * Returns the list of original URIs that encrypted & fsynced cleanly so the caller can request
     * MediaStore.createDeleteRequest() to permanently remove plaintext originals.
     */
    suspend fun importFromMediaStore(context: Context, sources: List<Medium>): List<Uri> = withContext(Dispatchers.IO) {
        init(context)
        ioMutex.withLock {
            val dek = activeDek?.copyOf() ?: return@withLock emptyList()
            try {
                val resolver = context.contentResolver
                val current = readIndexSync(context, dek).toMutableList()
                val succeededOriginals = ArrayList<Uri>(sources.size)

                for (src in sources) {
                    if (src.uri.scheme == VaultCrypto.VAULT_SCHEME) continue
                    val vaultId = generateUniqueId(current)
                    val destFile = itemFile(context, vaultId)
                    val destThumb = thumbFile(context, vaultId)

                    try {
                        val encryptedSize = resolver.openInputStream(src.uri)?.use { input ->
                            VaultCrypto.encryptStream(input, destFile, vaultId, dek)
                        } ?: continue

                        // Generate & encrypt 360px thumbnail for zero-lag vault grid rendering
                        try {
                            val bmp = resolver.loadThumbnail(src.uri, Size(360, 360), null)
                            val baos = ByteArrayOutputStream()
                            bmp.compress(Bitmap.CompressFormat.JPEG, 85, baos)
                            val encThumb = VaultCrypto.encryptSmallBlob(baos.toByteArray(), vaultId, dek)
                            writeBytesAtomically(destThumb, encThumb)
                        } catch (_: Exception) {
                        }

                        current.add(
                            0,
                            src.copy(
                                id = vaultId,
                                uri = vaultUriFor(vaultId),
                                sizeBytes = encryptedSize,
                                bucketId = -2001L,
                                bucketName = "Locked Folder",
                                isFavorite = false
                            )
                        )
                        succeededOriginals.add(src.uri)
                    } catch (_: Exception) {
                        destFile.delete()
                        destThumb.delete()
                    }
                }

                if (succeededOriginals.isNotEmpty()) {
                    writeIndexSync(context, dek, current)
                    _vaultMedia.value = current
                }
                succeededOriginals
            } finally {
                dek.fill(0)
            }
        }
    }

    /**
     * Streams decrypted files directly back to MediaStore and removes encrypted copies on success.
     */
    suspend fun unlockItemsToGallery(context: Context, items: List<Medium>): Int = withContext(Dispatchers.IO) {
        init(context)
        ioMutex.withLock {
            val dek = activeDek?.copyOf() ?: return@withLock 0
            try {
                val resolver = context.contentResolver
                val current = readIndexSync(context, dek).toMutableList()
                val targetIds = items.mapTo(HashSet()) { it.id }
                var restored = 0

                for (item in items) {
                    val encFile = itemFile(context, item.id)
                    if (!encFile.exists()) continue

                    val collection = if (item.isVideo) {
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    } else {
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    }
                    val fallbackDir = if (item.isVideo) {
                        "${Environment.DIRECTORY_MOVIES}/Restored/"
                    } else {
                        "${Environment.DIRECTORY_PICTURES}/Restored/"
                    }
                    val targetPath = item.relativePath.takeIf { it.isNotBlank() } ?: fallbackDir

                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, item.displayName.ifBlank { "restored_${item.id}" })
                        put(MediaStore.MediaColumns.MIME_TYPE, item.mimeType)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, targetPath)
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }

                    val destUri = resolver.insert(collection, values) ?: continue
                    var ok = false
                    try {
                        resolver.openOutputStream(destUri)?.use { out ->
                            VaultCrypto.decryptFileToStream(encFile, out, dek)
                            ok = true
                        }
                    } finally {
                        if (ok) {
                            resolver.update(destUri, ContentValues().apply {
                                put(MediaStore.MediaColumns.IS_PENDING, 0)
                            }, null, null)
                            encFile.delete()
                            thumbFile(context, item.id).delete()
                            thumbCache.remove(item.id)
                            restored++
                        } else {
                            resolver.delete(destUri, null, null)
                            targetIds.remove(item.id)
                        }
                    }
                }

                if (restored > 0) {
                    val updated = current.filterNot { it.id in targetIds }
                    writeIndexSync(context, dek, updated)
                    _vaultMedia.value = updated
                }
                restored
            } finally {
                dek.fill(0)
            }
        }
    }

    suspend fun deletePermanently(context: Context, items: List<Medium>): Int = withContext(Dispatchers.IO) {
        init(context)
        ioMutex.withLock {
            val dek = activeDek?.copyOf() ?: return@withLock 0
            try {
                val current = readIndexSync(context, dek)
                val targetIds = items.mapTo(HashSet()) { it.id }
                var removed = 0
                for (id in targetIds) {
                    itemFile(context, id).delete()
                    thumbFile(context, id).delete()
                    thumbCache.remove(id)
                    removed++
                }
                val updated = current.filterNot { it.id in targetIds }
                writeIndexSync(context, dek, updated)
                _vaultMedia.value = updated
                removed
            } finally {
                dek.fill(0)
            }
        }
    }

    @UnstableApi
    fun createDataSourceFactory(): DataSource.Factory = DataSource.Factory {
        VaultVideoDataSource()
    }

    internal fun openEncryptedFileAndDek(id: Long): Pair<File, ByteArray>? {
        val ctx = appContext ?: return null
        val dek = activeDek?.copyOf() ?: return null
        val file = itemFile(ctx, id)
        if (!file.exists()) {
            dek.fill(0)
            return null
        }
        return file to dek
    }

    private fun generateUniqueId(existing: List<Medium>): Long {
        val used = existing.mapTo(HashSet()) { it.id }
        var id: Long
        do {
            id = (random.nextLong() and Long.MAX_VALUE).coerceAtLeast(1L)
        } while (id in used)
        return id
    }

    private fun readIndexSync(context: Context, dek: ByteArray): List<Medium> {
        val idxFile = indexFile(context)
        if (!idxFile.exists()) return emptyList()
        return try {
            val plain = VaultCrypto.decryptSmallBlob(idxFile.readBytes(), INDEX_AAD_ID, dek)
            val arr = JSONArray(String(plain, Charsets.UTF_8))
            val list = ArrayList<Medium>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.getLong("id")
                list.add(
                    Medium(
                        id = id,
                        uri = vaultUriFor(id),
                        displayName = o.optString("name", ""),
                        mimeType = o.optString("mime", "image/*"),
                        width = o.optInt("w", 0),
                        height = o.optInt("h", 0),
                        orientation = o.optInt("orient", 0),
                        durationMs = o.optLong("dur", 0L),
                        dateTakenMs = o.optLong("taken", 0L),
                        dateModifiedMs = o.optLong("mod", 0L),
                        sizeBytes = o.optLong("size", 0L),
                        bucketId = -2001L,
                        bucketName = "Locked Folder",
                        relativePath = o.optString("path", ""),
                        isFavorite = false,
                        isVideo = o.optBoolean("video", false),
                        isAnimated = o.optBoolean("anim", false)
                    )
                )
            }
            list.sortedByDescending { it.dateTakenMs }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writeIndexSync(context: Context, dek: ByteArray, items: List<Medium>) {
        val arr = JSONArray()
        for (m in items) {
            arr.put(
                JSONObject().apply {
                    put("id", m.id)
                    put("name", m.displayName)
                    put("mime", m.mimeType)
                    put("w", m.width)
                    put("h", m.height)
                    put("orient", m.orientation)
                    put("dur", m.durationMs)
                    put("taken", m.dateTakenMs)
                    put("mod", m.dateModifiedMs)
                    put("size", m.sizeBytes)
                    put("path", m.relativePath)
                    put("video", m.isVideo)
                    put("anim", m.isAnimated)
                }
            )
        }
        val plain = arr.toString().toByteArray(Charsets.UTF_8)
        val enc = VaultCrypto.encryptSmallBlob(plain, INDEX_AAD_ID, dek)
        writeBytesAtomically(indexFile(context), enc)
    }

    private fun writeBytesAtomically(target: File, data: ByteArray) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "${target.name}.tmp")
        FileOutputStream(tmp).use { fos ->
            fos.write(data)
            fos.fd.sync()
        }
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }
}

/**
 * Zero-temp-file ExoPlayer DataSource for O(1) random-access playback of chunked AES-256-GCM vault videos.
 */
@UnstableApi
private class VaultVideoDataSource : BaseDataSource(/* isNetwork = */ false) {
    private var currentUri: Uri? = null
    private var raf: RandomAccessFile? = null
    private var header: VaultFileHeader? = null
    private var dekCopy: ByteArray? = null
    private var readPosition: Long = 0L
    private var bytesRemaining: Long = 0L
    private var cachedChunkIndex: Int = -1
    private var cachedChunk: ByteArray? = null
    private var opened: Boolean = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val uri = dataSpec.uri
        val id = VaultManager.parseVaultId(uri) ?: throw IOException("Invalid vault URI: $uri")
        val (file, dek) = VaultManager.openEncryptedFileAndDek(id)
            ?: throw IOException("Vault is locked or file missing")

        try {
            val openedRaf = RandomAccessFile(file, "r")
            val hdr = VaultCrypto.readHeader(openedRaf)
            if (dataSpec.position > hdr.plaintextLength) {
                openedRaf.close()
                dek.fill(0)
                throw IOException("Seek position out of bounds")
            }

            currentUri = uri
            raf = openedRaf
            header = hdr
            dekCopy = dek
            readPosition = dataSpec.position
            bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                min(dataSpec.length, hdr.plaintextLength - readPosition)
            } else {
                hdr.plaintextLength - readPosition
            }
            cachedChunkIndex = -1
            cachedChunk = null
            opened = true
            transferStarted(dataSpec)
            return bytesRemaining
        } catch (e: Exception) {
            dek.fill(0)
            throw e
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining <= 0L) return C.RESULT_END_OF_INPUT

        val activeRaf = raf ?: return C.RESULT_END_OF_INPUT
        val hdr = header ?: return C.RESULT_END_OF_INPUT
        val dek = dekCopy ?: return C.RESULT_END_OF_INPUT

        val chunkIdx = (readPosition / hdr.chunkSize).toInt()
        val offsetInChunk = (readPosition % hdr.chunkSize).toInt()

        val chunk = if (chunkIdx == cachedChunkIndex && cachedChunk != null) {
            cachedChunk!!
        } else {
            cachedChunk?.fill(0)
            val decrypted = VaultCrypto.decryptChunk(activeRaf, hdr, chunkIdx, dek)
            cachedChunkIndex = chunkIdx
            cachedChunk = decrypted
            decrypted
        }

        val availableInChunk = (chunk.size - offsetInChunk).coerceAtLeast(0)
        if (availableInChunk == 0) return C.RESULT_END_OF_INPUT

        val toCopy = min(length.toLong(), min(bytesRemaining, availableInChunk.toLong())).toInt()
        System.arraycopy(chunk, offsetInChunk, buffer, offset, toCopy)
        readPosition += toCopy
        bytesRemaining -= toCopy
        bytesTransferred(toCopy)
        return toCopy
    }

    override fun getUri(): Uri? = currentUri

    override fun close() {
        currentUri = null
        cachedChunk?.fill(0)
        cachedChunk = null
        cachedChunkIndex = -1
        dekCopy?.fill(0)
        dekCopy = null
        header = null
        try {
            raf?.close()
        } finally {
            raf = null
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }
}
