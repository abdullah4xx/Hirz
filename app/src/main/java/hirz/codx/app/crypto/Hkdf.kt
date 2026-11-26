package hirz.codx.app.crypto

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HKDF (RFC 5869) with HMAC-SHA256.
 *
 * This is the standard construction described in the RFC; nothing custom.
 */
object Hkdf {

    private const val HASH_LEN = 32

    fun derive(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int = 32): ByteArray {
        require(length > 0 && length <= 255 * HASH_LEN) { "invalid HKDF length" }
        val prk = extract(salt, ikm)
        val okm = expand(prk, info, length)
        Bytes.wipe(prk)
        return okm
    }

    private fun extract(salt: ByteArray, ikm: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val key = if (salt.isEmpty()) ByteArray(HASH_LEN) else salt
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(ikm)
    }

    private fun expand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        val out = ByteArray(length)
        var previous = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < length) {
            mac.reset()
            mac.update(previous)
            mac.update(info)
            mac.update(counter.toByte())
            previous = mac.doFinal()
            val chunk = minOf(HASH_LEN, length - offset)
            System.arraycopy(previous, 0, out, offset, chunk)
            offset += chunk
            counter++
        }
        Bytes.wipe(previous)
        return out
    }
}
