package hirz.codx.app.crypto

import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2KtResult
import com.lambdapioneer.argon2kt.Argon2Mode

/**
 * Argon2id wrapper.
 *
 * Parameters are stored next to the wrapped key so they can evolve without breaking existing
 * vaults. The chosen values are calibrated once on the target phone and recorded in DECISIONS.md.
 */
object Argon2 {

    data class Params(
        val iterations: Int = 3,
        val memoryKib: Int = 65536,
        val parallelism: Int = 2,
        val hashLength: Int = 32
    ) {
        fun toMap(): Map<String, Int> = mapOf(
            "t" to iterations,
            "m" to memoryKib,
            "p" to parallelism,
            "l" to hashLength
        )

        companion object {
            fun fromMap(map: Map<String, Int>): Params = Params(
                iterations = map["t"] ?: 3,
                memoryKib = map["m"] ?: 65536,
                parallelism = map["p"] ?: 2,
                hashLength = map["l"] ?: 32
            )
        }
    }

    private val engine by lazy { Argon2Kt() }

    /** Derives a 32-byte key from the PIN (or recovery key material) and a random salt. */
    fun derive(secret: ByteArray, salt: ByteArray, params: Params): ByteArray {
        val result: Argon2KtResult = engine.hash(
            mode = Argon2Mode.ARGON2_ID,
            password = secret,
            salt = salt,
            tCostInIterations = params.iterations,
            mCostInKibibyte = params.memoryKib,
            parallelism = params.parallelism,
            hashLengthInBytes = params.hashLength
        )
        return result.rawHashAsByteArray()
    }

    /**
     * Measures the wall-clock cost of one derivation so setup can pick parameters that land in the
     * 300-500 ms window on this device.
     */
    fun calibrate(targetMillis: Long = 400): Params {
        val secret = ByteArray(16) { 1 }
        val salt = ByteArray(16) { 2 }
        var params = Params(iterations = 3, memoryKib = 65536, parallelism = 2)
        repeat(3) {
            val started = System.nanoTime()
            Bytes.wipe(derive(secret, salt, params))
            val elapsed = (System.nanoTime() - started) / 1_000_000
            when {
                elapsed < 250L -> params = params.copy(memoryKib = (params.memoryKib * 2).coerceAtMost(262144))
                elapsed > 700L && params.memoryKib > 16384 -> params =
                    params.copy(memoryKib = params.memoryKib / 2)
                else -> return params
            }
        }
        Bytes.wipe(secret)
        return params
    }
}
