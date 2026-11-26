package hirz.codx.app.crypto

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Persistence for the wrapped master key.
 *
 * Nothing in this file is secret: it holds salts, nonces and ciphertext only. The plaintext master
 * key never reaches the disk.
 */
class VaultStore(private val context: Context) {

    data class Wrap(val salt: ByteArray, val nonce: ByteArray, val ciphertext: ByteArray)

    data class Header(
        val version: Int,
        val argon2: Argon2.Params,
        val pin: Wrap,
        val recovery: Wrap,
        val biometric: Wrap?,
        val biometricEnabled: Boolean
    )

    private val file: File get() = File(context.filesDir, FILE_NAME)

    fun exists(): Boolean = file.exists()

    fun read(): Header? {
        if (!file.exists()) return null
        return try {
            val json = JSONObject(file.readText())
            val argon = json.getJSONObject("argon2")
            Header(
                version = json.getInt("version"),
                argon2 = Argon2.Params(
                    iterations = argon.getInt("t"),
                    memoryKib = argon.getInt("m"),
                    parallelism = argon.getInt("p"),
                    hashLength = argon.getInt("l")
                ),
                pin = json.getJSONObject("pin").toWrap(),
                recovery = json.getJSONObject("recovery").toWrap(),
                biometric = json.optJSONObject("biometric")?.toWrap(),
                biometricEnabled = json.optBoolean("biometricEnabled", false)
            )
        } catch (_: Exception) {
            null
        }
    }

    fun write(header: Header) {
        val json = JSONObject()
        json.put("version", header.version)
        json.put(
            "argon2",
            JSONObject()
                .put("t", header.argon2.iterations)
                .put("m", header.argon2.memoryKib)
                .put("p", header.argon2.parallelism)
                .put("l", header.argon2.hashLength)
        )
        json.put("pin", header.pin.toJson())
        json.put("recovery", header.recovery.toJson())
        header.biometric?.let { json.put("biometric", it.toJson()) }
        json.put("biometricEnabled", header.biometricEnabled)

        val tmp = File(context.filesDir, "$FILE_NAME.tmp")
        tmp.writeText(json.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(json.toString())
            tmp.delete()
        }
    }

    fun delete() {
        file.delete()
        File(context.filesDir, "$FILE_NAME.tmp").delete()
    }

    private fun JSONObject.toWrap(): Wrap = Wrap(
        salt = Bytes.unb64(getString("salt")),
        nonce = Bytes.unb64(getString("nonce")),
        ciphertext = Bytes.unb64(getString("ct"))
    )

    private fun Wrap.toJson(): JSONObject = JSONObject()
        .put("salt", Bytes.b64(salt))
        .put("nonce", Bytes.b64(nonce))
        .put("ct", Bytes.b64(ciphertext))

    companion object {
        private const val FILE_NAME = "vault.json"
    }
}
