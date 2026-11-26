package hirz.codx.app.crypto

import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM envelope used to wrap the master key.
 *
 * Every wrap uses a fresh random 96-bit nonce and a fixed label as additional authenticated data, so
 * a ciphertext cannot be moved between purposes or between vaults.
 */
object Aead {

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128

    data class Blob(val nonce: ByteArray, val ciphertext: ByteArray)

    fun seal(key: ByteArray, plaintext: ByteArray, aad: String): Blob {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val secretKey = javax.crypto.spec.SecretKeySpec(key, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        cipher.updateAAD(aad.toByteArray(Charsets.UTF_8))
        val nonce = cipher.iv
        require(nonce.size == NONCE_BYTES) { "unexpected GCM nonce length" }
        val ct = cipher.doFinal(plaintext)
        return Blob(nonce, ct)
    }

    /** Returns null when authentication fails (wrong key or tampered data). */
    fun open(key: ByteArray, blob: Blob, aad: String): ByteArray? = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            javax.crypto.spec.SecretKeySpec(key, "AES"),
            GCMParameterSpec(TAG_BITS, blob.nonce)
        )
        cipher.updateAAD(aad.toByteArray(Charsets.UTF_8))
        cipher.doFinal(blob.ciphertext)
    } catch (_: Exception) {
        null
    }

    /** Opens a blob with a cipher that was already authenticated by the Keystore (biometrics). */
    fun openWithCipher(cipher: Cipher, ciphertext: ByteArray): ByteArray? = try {
        cipher.doFinal(ciphertext)
    } catch (_: Exception) {
        null
    }
}
