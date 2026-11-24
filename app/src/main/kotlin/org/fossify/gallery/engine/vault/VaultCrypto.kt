package org.fossify.gallery.engine.vault

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.ProviderException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.min

enum class VaultAuthError {
    NONE_ENROLLED,
    HARDWARE_UNAVAILABLE,
    KEY_INVALIDATED,
    CANCELLED,
    FAILED
}

sealed interface VaultAuthResult {
    data class Success(val dek: ByteArray, val isStrongBoxBacked: Boolean) : VaultAuthResult
    data class Failure(val error: VaultAuthError, val message: String) : VaultAuthResult
}

data class VaultFileHeader(
    val fileId: Long,
    val plaintextLength: Long,
    val baseNonce: ByteArray,
    val chunkSize: Int
)

object VaultCrypto {
    const val VAULT_SCHEME = "lumina-vault"
    const val CHUNK_SIZE = 65_536
    const val GCM_TAG_BYTES = 16
    const val GCM_TAG_BITS = 128
    const val HEADER_BYTES = 32

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEK_ALIAS = "lumina_vault_strongbox_kek_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private val KEY_MAGIC = byteArrayOf('L'.code.toByte(), 'V'.code.toByte(), 'K'.code.toByte(), '1'.code.toByte())
    private val FILE_MAGIC = byteArrayOf('L'.code.toByte(), 'V'.code.toByte(), 'F'.code.toByte(), '1'.code.toByte())

    private val secureRandom = SecureRandom()

    fun authenticateAndOpenDek(
        context: Context,
        keyFile: File,
        title: String,
        subtitle: String,
        onResult: (VaultAuthResult) -> Unit
    ) {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

        val bm = context.getSystemService(BiometricManager::class.java)
        val status = bm?.canAuthenticate(authenticators) ?: BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
        if (status != BiometricManager.BIOMETRIC_SUCCESS) {
            val err = if (status == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
                VaultAuthError.NONE_ENROLLED
            } else {
                VaultAuthError.HARDWARE_UNAVAILABLE
            }
            val msg = if (err == VaultAuthError.NONE_ENROLLED) {
                "Set a screen lock PIN or fingerprint in Android Settings to use Locked Folder."
            } else {
                "Biometric or device credential security is unavailable on this device."
            }
            onResult(VaultAuthResult.Failure(err, msg))
            return
        }

        val isCreating = !keyFile.exists()
        val kek: SecretKey
        val strongBox: Boolean
        try {
            val resolved = getOrCreateKek(context)
            kek = resolved.first
            strongBox = resolved.second
        } catch (e: Exception) {
            onResult(VaultAuthResult.Failure(VaultAuthError.FAILED, "Failed to initialize hardware key: ${e.message}"))
            return
        }

        var existingWrapped: ByteArray? = null
        val cipher: Cipher
        try {
            cipher = Cipher.getInstance(TRANSFORMATION)
            if (isCreating) {
                cipher.init(Cipher.ENCRYPT_MODE, kek)
            } else {
                val parsed = parseKeyFile(keyFile)
                existingWrapped = parsed.second
                cipher.init(Cipher.DECRYPT_MODE, kek, GCMParameterSpec(GCM_TAG_BITS, parsed.first))
            }
        } catch (_: KeyPermanentlyInvalidatedException) {
            onResult(
                VaultAuthResult.Failure(
                    VaultAuthError.KEY_INVALIDATED,
                    "Hardware vault key was invalidated by the system."
                )
            )
            return
        } catch (e: Exception) {
            onResult(VaultAuthResult.Failure(VaultAuthError.FAILED, "Cipher init failed: ${e.message}"))
            return
        }

        val prompt = BiometricPrompt.Builder(context)
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(authenticators)
            .build()

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val authedCipher = result.cryptoObject?.cipher
                if (authedCipher == null) {
                    onResult(VaultAuthResult.Failure(VaultAuthError.FAILED, "Hardware cipher unavailable."))
                    return
                }
                try {
                    if (isCreating) {
                        val newDek = ByteArray(32)
                        secureRandom.nextBytes(newDek)
                        val wrapped = authedCipher.doFinal(newDek)
                        val iv = authedCipher.iv
                        writeKeyFileAtomically(keyFile, iv, wrapped, strongBox)
                        onResult(VaultAuthResult.Success(newDek, strongBox))
                    } else {
                        val dek = authedCipher.doFinal(existingWrapped!!)
                        onResult(VaultAuthResult.Success(dek, strongBox))
                    }
                } catch (e: Exception) {
                    onResult(VaultAuthResult.Failure(VaultAuthError.FAILED, "Vault key operation failed: ${e.message}"))
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val cancelled = errorCode == BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.BIOMETRIC_ERROR_CANCELED
                onResult(
                    VaultAuthResult.Failure(
                        if (cancelled) VaultAuthError.CANCELLED else VaultAuthError.FAILED,
                        errString.toString()
                    )
                )
            }
        }

        prompt.authenticate(
            BiometricPrompt.CryptoObject(cipher),
            CancellationSignal(),
            context.mainExecutor,
            callback
        )
    }

    private fun getOrCreateKek(context: Context): Pair<SecretKey, Boolean> {
        val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existing = ks.getKey(KEK_ALIAS, null) as? SecretKey
        if (existing != null) {
            return existing to isKeyStrongBoxBacked(existing)
        }

        val hasStrongBox = context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        if (hasStrongBox) {
            try {
                val sbKey = generateKek(useStrongBox = true)
                return sbKey to isKeyStrongBoxBacked(sbKey)
            } catch (_: StrongBoxUnavailableException) {
            } catch (_: ProviderException) {
            }
        }
        val teeKey = generateKek(useStrongBox = false)
        return teeKey to isKeyStrongBoxBacked(teeKey)
    }

    private fun generateKek(useStrongBox: Boolean): SecretKey {
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val builder = KeyGenParameterSpec.Builder(
            KEK_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(
                0,
                KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
            )
            .setInvalidatedByBiometricEnrollment(false)
            .setUnlockedDeviceRequired(true)
            .setIsStrongBoxBacked(useStrongBox)

        kg.init(builder.build())
        return kg.generateKey()
    }

    private fun isKeyStrongBoxBacked(key: SecretKey): Boolean {
        return try {
            val factory = SecretKeyFactory.getInstance(key.algorithm, KEYSTORE_PROVIDER)
            val info = factory.getKeySpec(key, KeyInfo::class.java) as KeyInfo
            info.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX
        } catch (_: Exception) {
            false
        }
    }

    private fun writeKeyFileAtomically(keyFile: File, iv: ByteArray, wrapped: ByteArray, strongBox: Boolean) {
        keyFile.parentFile?.mkdirs()
        val tmp = File(keyFile.parentFile, "${keyFile.name}.tmp")
        FileOutputStream(tmp).use { fos ->
            fos.write(KEY_MAGIC)
            fos.write(if (strongBox) 1 else 0)
            fos.write(iv.size)
            fos.write(iv)
            fos.write(wrapped)
            fos.fd.sync()
        }
        if (!tmp.renameTo(keyFile)) {
            tmp.copyTo(keyFile, overwrite = true)
            tmp.delete()
        }
    }

    private fun parseKeyFile(keyFile: File): Pair<ByteArray, ByteArray> {
        val raw = keyFile.readBytes()
        if (raw.size < 24 || !raw.copyOfRange(0, 4).contentEquals(KEY_MAGIC)) {
            throw IOException("Corrupt vault key header")
        }
        val ivLen = raw[5].toInt() and 0xFF
        val iv = raw.copyOfRange(6, 6 + ivLen)
        val wrapped = raw.copyOfRange(6 + ivLen, raw.size)
        return iv to wrapped
    }

    /**
     * Encrypts an arbitrary stream into fixed 64 KB AES-256-GCM chunks for O(1) random-access playback.
     * Writes exact byte length into the 32-byte header on completion and fsyncs to flash.
     */
    fun encryptStream(
        input: InputStream,
        outputFile: File,
        fileId: Long,
        dek: ByteArray
    ): Long {
        outputFile.parentFile?.mkdirs()
        val tmp = File(outputFile.parentFile, "${outputFile.name}.tmp")
        val baseNonce = ByteArray(8).also { secureRandom.nextBytes(it) }
        val secretKey = SecretKeySpec(dek, "AES")
        val plainBuf = ByteArray(CHUNK_SIZE)
        var totalPlain = 0L
        var chunkIndex = 0

        try {
            RandomAccessFile(tmp, "rw").use { raf ->
                raf.setLength(0L)
                // Reserve 32-byte header
                raf.write(ByteArray(HEADER_BYTES))

                while (true) {
                    val readCount = readFully(input, plainBuf)
                    if (readCount <= 0) break

                    val cipher = Cipher.getInstance(TRANSFORMATION)
                    cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, chunkIv(baseNonce, chunkIndex)))
                    cipher.updateAAD(chunkAad(fileId, chunkIndex, readCount))
                    val encrypted = cipher.doFinal(plainBuf, 0, readCount)
                    raf.write(encrypted)

                    totalPlain += readCount
                    chunkIndex++
                }

                // Patch exact header
                raf.seek(0L)
                raf.write(buildHeaderBytes(fileId, totalPlain, baseNonce, CHUNK_SIZE))
                raf.fd.sync()
            }

            if (!tmp.renameTo(outputFile)) {
                tmp.copyTo(outputFile, overwrite = true)
                tmp.delete()
            }
            return totalPlain
        } catch (e: Exception) {
            tmp.delete()
            throw e
        } finally {
            plainBuf.fill(0)
        }
    }

    fun readHeader(raf: RandomAccessFile): VaultFileHeader {
        val hdr = ByteArray(HEADER_BYTES)
        raf.seek(0L)
        raf.readFully(hdr)
        if (!hdr.copyOfRange(0, 4).contentEquals(FILE_MAGIC)) {
            throw IOException("Invalid vault file magic")
        }
        val bb = ByteBuffer.wrap(hdr, 4, HEADER_BYTES - 4)
        val fileId = bb.long
        val plainLen = bb.long
        val baseNonce = ByteArray(8).also { bb.get(it) }
        val chunkSize = bb.int
        return VaultFileHeader(fileId, plainLen, baseNonce, chunkSize)
    }

    /** Decrypts a single 64 KB chunk at O(1) disk offset for instant video scrubbing. */
    fun decryptChunk(
        raf: RandomAccessFile,
        header: VaultFileHeader,
        chunkIndex: Int,
        dek: ByteArray
    ): ByteArray {
        val chunkPlainOffset = chunkIndex.toLong() * header.chunkSize
        if (chunkPlainOffset >= header.plaintextLength) return ByteArray(0)

        val expectedPlainLen = min(header.chunkSize.toLong(), header.plaintextLength - chunkPlainOffset).toInt()
        val cipherLen = expectedPlainLen + GCM_TAG_BYTES
        val diskOffset = HEADER_BYTES.toLong() + chunkIndex.toLong() * (header.chunkSize + GCM_TAG_BYTES)

        val cipherBuf = ByteArray(cipherLen)
        raf.seek(diskOffset)
        raf.readFully(cipherBuf)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(dek, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, chunkIv(header.baseNonce, chunkIndex))
        )
        cipher.updateAAD(chunkAad(header.fileId, chunkIndex, expectedPlainLen))
        return cipher.doFinal(cipherBuf)
    }

    fun decryptFileToBytes(encryptedFile: File, dek: ByteArray): ByteArray {
        RandomAccessFile(encryptedFile, "r").use { raf ->
            val header = readHeader(raf)
            val out = ByteArrayOutputStream(header.plaintextLength.toInt().coerceAtLeast(0))
            val totalChunks = ((header.plaintextLength + header.chunkSize - 1) / header.chunkSize).toInt()
            for (i in 0 until totalChunks) {
                val chunk = decryptChunk(raf, header, i, dek)
                out.write(chunk)
            }
            return out.toByteArray()
        }
    }

    fun decryptFileToStream(encryptedFile: File, output: OutputStream, dek: ByteArray) {
        RandomAccessFile(encryptedFile, "r").use { raf ->
            val header = readHeader(raf)
            val totalChunks = ((header.plaintextLength + header.chunkSize - 1) / header.chunkSize).toInt()
            for (i in 0 until totalChunks) {
                val chunk = decryptChunk(raf, header, i, dek)
                output.write(chunk)
            }
        }
    }

    fun encryptSmallBlob(plaintext: ByteArray, fileId: Long, dek: ByteArray): ByteArray {
        val iv = ByteArray(12).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(dek, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(chunkAad(fileId, 0, plaintext.size))
        val ct = cipher.doFinal(plaintext)
        return ByteBuffer.allocate(12 + ct.size).put(iv).put(ct).array()
    }

    fun decryptSmallBlob(blob: ByteArray, fileId: Long, dek: ByteArray): ByteArray {
        if (blob.size < 12 + GCM_TAG_BYTES) throw IOException("Corrupt small vault blob")
        val iv = blob.copyOfRange(0, 12)
        val ctLen = blob.size - 12
        val plainLen = ctLen - GCM_TAG_BYTES
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(dek, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(chunkAad(fileId, 0, plainLen))
        return cipher.doFinal(blob, 12, ctLen)
    }

    private fun buildHeaderBytes(fileId: Long, plainLen: Long, baseNonce: ByteArray, chunkSize: Int): ByteArray {
        return ByteBuffer.allocate(HEADER_BYTES)
            .put(FILE_MAGIC)
            .putLong(fileId)
            .putLong(plainLen)
            .put(baseNonce)
            .putInt(chunkSize)
            .array()
    }

    private fun chunkIv(baseNonce: ByteArray, chunkIndex: Int): ByteArray {
        return ByteBuffer.allocate(12).put(baseNonce).putInt(chunkIndex).array()
    }

    private fun chunkAad(fileId: Long, chunkIndex: Int, plainLen: Int): ByteArray {
        return ByteBuffer.allocate(16).putLong(fileId).putInt(chunkIndex).putInt(plainLen).array()
    }

    private fun readFully(input: InputStream, buf: ByteArray): Int {
        var total = 0
        while (total < buf.size) {
            val n = input.read(buf, total, buf.size - total)
            if (n < 0) break
            total += n
        }
        return total
    }
}
