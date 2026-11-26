package hirz.codx.app.data.store

import com.google.crypto.tink.StreamingAead
import hirz.codx.app.crypto.Bytes
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.FileChannel
import java.nio.channels.SeekableByteChannel
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

/**
 * Writes and reads vault blobs as Tink Streaming AEAD ciphertext.
 *
 * Files are stored under random names. Plaintext never touches disk.
 */
class EncryptedFileStore(private val rootDir: File) {

    init {
        if (!rootDir.exists()) rootDir.mkdirs()
    }

    fun fileFor(randomName: String): File = File(rootDir, randomName)

    fun randomName(): String = Bytes.toHex(ByteArray(16).also { java.security.SecureRandom().nextBytes(it) })

    /** Encrypts [source] into a new random-named file and returns its name and SHA-256 of plaintext. */
    fun write(source: InputStream, aead: StreamingAead, aad: ByteArray): WriteResult {
        val name = randomName()
        val target = fileFor(name)
        val tmp = File(rootDir, "$name.tmp")
        val digest = MessageDigest.getInstance("SHA-256")
        tmp.outputStream().use { rawOut ->
            aead.newEncryptingStream(rawOut, aad).use { cipherOut ->
                val buffer = ByteArray(DEFAULT_BUFFER)
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                    cipherOut.write(buffer, 0, read)
                }
                cipherOut.flush()
            }
        }
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
        return WriteResult(name = name, sha256Hex = Bytes.toHex(digest.digest()), sizeBytes = target.length())
    }

    /** Opens a seekable decrypting channel for random access playback. Caller must close it. */
    fun openSeekable(randomName: String, aead: StreamingAead, aad: ByteArray): SeekableByteChannel {
        val channel = FileChannel.open(fileFor(randomName).toPath(), StandardOpenOption.READ)
        return aead.newSeekableDecryptingChannel(channel, aad)
    }

    /** Decrypts the full file to [sink]. Returns SHA-256 of plaintext. */
    fun readAll(randomName: String, aead: StreamingAead, aad: ByteArray, sink: OutputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fileFor(randomName).inputStream().use { rawIn ->
            aead.newDecryptingStream(rawIn, aad).use { cipherIn ->
                val buffer = ByteArray(DEFAULT_BUFFER)
                while (true) {
                    val read = cipherIn.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                    sink.write(buffer, 0, read)
                }
            }
        }
        return Bytes.toHex(digest.digest())
    }

    fun delete(randomName: String): Boolean {
        val file = fileFor(randomName)
        return !file.exists() || file.delete()
    }

    data class WriteResult(val name: String, val sha256Hex: String, val sizeBytes: Long)

    companion object {
        private const val DEFAULT_BUFFER = 64 * 1024
    }
}
