package hirz.codx.app.crypto

import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.subtle.AesGcmHkdfStreaming
import java.security.GeneralSecurityException

/**
 * Creates per-file Streaming AEAD instances bound to a wrapped 256-bit file key.
 *
 * The file key is wrapped by the master key at the DB layer (AAD = item id). This helper only
 * turns the unwrapped raw key bytes into a Tink StreamingAead.
 */
object FileKeys {

    private const val HKDF_ALGO = "HmacSha256"
    private const val KEY_SIZE = 32
    private const val SEGMENT_SIZE = 4096
    private const val OFFSET = 0

    fun streamingAead(rawFileKey: ByteArray): StreamingAead {
        if (rawFileKey.size != KEY_SIZE) {
            throw GeneralSecurityException("file key must be 32 bytes")
        }
        return AesGcmHkdfStreaming(rawFileKey, HKDF_ALGO, KEY_SIZE, SEGMENT_SIZE, OFFSET)
    }

    fun aadForItem(itemId: String): ByteArray = "hirz.item.v1:$itemId".toByteArray(Charsets.UTF_8)
}
