package hirz.codx.app.data.db

import hirz.codx.app.crypto.Aead
import hirz.codx.app.crypto.Bytes
import hirz.codx.app.crypto.Hkdf
import java.security.SecureRandom

/**
 * Encrypts sensitive Room columns (names, paths, tags) with a metadata key derived from the master key.
 */
class MetadataCipher(private val metadataKey: ByteArray) {

    data class Enc(val nonce: ByteArray, val ciphertext: ByteArray)

    fun encryptString(value: String, aad: String): Enc {
        val blob = Aead.seal(metadataKey, value.toByteArray(Charsets.UTF_8), aad)
        return Enc(blob.nonce, blob.ciphertext)
    }

    fun decryptString(enc: Enc, aad: String): String? {
        val plain = Aead.open(metadataKey, Aead.Blob(enc.nonce, enc.ciphertext), aad) ?: return null
        return plain.toString(Charsets.UTF_8)
    }

    companion object {
        fun fromMasterKey(masterKey: ByteArray): MetadataCipher {
            val meta = Hkdf.derive(masterKey, ByteArray(0), "hirz.meta.v1".toByteArray(Charsets.UTF_8), 32)
            return MetadataCipher(meta)
        }
    }
}
