package hirz.codx.app.crypto

import java.security.SecureRandom

/**
 * The recovery key: 160 random bits rendered in Crockford base32, grouped for writing on paper.
 *
 * The key itself is never stored. Only the master key wrapped by a key derived from it is stored.
 */
object RecoveryKey {

    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private const val KEY_BYTES = 20 // 160 bits
    private const val GROUP = 4

    fun generate(): ByteArray = ByteArray(KEY_BYTES).also { SecureRandom().nextBytes(it) }

    /** Renders as `XXXX-XXXX-...` (8 groups of 4). */
    fun format(raw: ByteArray): String {
        val encoded = encode(raw)
        return encoded.chunked(GROUP).joinToString("-")
    }

    /** Parses user input, tolerating lowercase, spaces, dashes and the usual look-alike letters. */
    fun parse(text: String): ByteArray? {
        val normalized = text
            .uppercase()
            .filter { it.isLetterOrDigit() }
            .map {
                when (it) {
                    'I', 'L' -> '1'
                    'O' -> '0'
                    else -> it
                }
            }
            .joinToString("")
        if (normalized.length != KEY_BYTES * 8 / 5) return null
        return try {
            decode(normalized)
        } catch (_: Exception) {
            null
        }
    }

    private fun encode(raw: ByteArray): String {
        val out = StringBuilder()
        var buffer = 0
        var bits = 0
        for (byte in raw) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                bits -= 5
                out.append(ALPHABET[(buffer ushr bits) and 0x1F])
            }
        }
        if (bits > 0) {
            out.append(ALPHABET[(buffer shl (5 - bits)) and 0x1F])
        }
        return out.toString()
    }

    private fun decode(text: String): ByteArray {
        val out = ByteArray(KEY_BYTES)
        var buffer = 0
        var bits = 0
        var index = 0
        for (char in text) {
            val value = ALPHABET.indexOf(char)
            require(value >= 0) { "invalid recovery key character" }
            buffer = (buffer shl 5) or value
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out[index++] = ((buffer ushr bits) and 0xFF).toByte()
            }
        }
        require(index == KEY_BYTES) { "invalid recovery key length" }
        return out
    }
}
