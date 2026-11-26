package hirz.codx.app.crypto

import android.util.Base64
import java.security.MessageDigest

/** Small binary helpers. No cryptography is invented here. */
object Bytes {

    private val HEX = "0123456789abcdef".toCharArray()

    fun toHex(bytes: ByteArray): String {
        val out = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            out[i * 2] = HEX[v ushr 4]
            out[i * 2 + 1] = HEX[v and 0x0F]
        }
        return String(out)
    }

    fun fromHex(hex: String): ByteArray {
        val clean = hex.trim()
        require(clean.length % 2 == 0) { "hex length must be even" }
        val out = ByteArray(clean.length / 2)
        for (i in out.indices) {
            out[i] = clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return out
    }

    fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    fun unb64(text: String): ByteArray = Base64.decode(text, Base64.NO_WRAP)

    /** Compares two byte arrays without leaking the position of the first difference. */
    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)

    /** Overwrites the array with zeros. */
    fun wipe(vararg arrays: ByteArray?) {
        for (array in arrays) {
            array?.fill(0)
        }
    }
}
