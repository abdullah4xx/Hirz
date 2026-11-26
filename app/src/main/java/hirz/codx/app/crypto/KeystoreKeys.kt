package hirz.codx.app.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.security.keystore.UserNotAuthenticatedException
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Android Keystore keys used by Hirz.
 *
 * - [HMAC_ALIAS] never leaves the device and can only be used for HMAC, so a copied vault cannot be
 *   brute-forced on another phone.
 * - [BIOMETRIC_ALIAS] is an AES key that requires a strong biometric per use, and is invalidated
 *   when the enrolled fingerprints change.
 */
object KeystoreKeys {

    const val HMAC_ALIAS = "hirz.pin.hmac.v1"
    const val BIOMETRIC_ALIAS = "hirz.bio.mk.v1"

    private const val PROVIDER = "AndroidKeyStore"

    private fun keyStore(): KeyStore =
        KeyStore.getInstance(PROVIDER).apply { load(null) }

    fun hasKey(alias: String): Boolean = keyStore().containsAlias(alias)

    fun deleteKey(alias: String) {
        val ks = keyStore()
        if (ks.containsAlias(alias)) {
            ks.deleteEntry(alias)
        }
    }

    /** Creates (or returns) the non-exportable HMAC key used to bind the PIN to this phone. */
    fun hmacKey(): SecretKey {
        val ks = keyStore()
        (ks.getEntry(HMAC_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            HMAC_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setDigests(KeyProperties.DIGEST_SHA256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    /** HMAC-SHA256 over the given data using the device-bound key. */
    fun hmac(data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(hmacKey())
        return mac.doFinal(data)
    }

    /**
     * Creates the biometric-bound AES key. Uses StrongBox when the device offers it, otherwise the
     * normal TEE-backed keystore.
     */
    fun createBiometricKey(): SecretKey {
        deleteKey(BIOMETRIC_ALIAS)
        return try {
            generateBiometricKey(strongBox = true)
        } catch (_: StrongBoxUnavailableException) {
            generateBiometricKey(strongBox = false)
        } catch (_: Exception) {
            generateBiometricKey(strongBox = false)
        }
    }

    private fun generateBiometricKey(strongBox: Boolean): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        val builder = KeyGenParameterSpec.Builder(
            BIOMETRIC_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(
                0,
                KeyProperties.AUTH_BIOMETRIC_STRONG
            )
        }
        if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setIsStrongBoxBacked(true)
        }
        generator.init(builder.build())
        return generator.generateKey()
    }

    fun biometricKey(): SecretKey? =
        (keyStore().getEntry(BIOMETRIC_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey

    /** True when the biometric key exists but can no longer be used. */
    fun isBiometricKeyInvalidated(): Boolean {
        val key = biometricKey() ?: return false
        return try {
            javax.crypto.Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(javax.crypto.Cipher.ENCRYPT_MODE, key)
            }
            false
        } catch (_: KeyPermanentlyInvalidatedException) {
            true
        } catch (_: UserNotAuthenticatedException) {
            false
        } catch (_: Exception) {
            false
        }
    }
}
