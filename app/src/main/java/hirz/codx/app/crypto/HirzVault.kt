package hirz.codx.app.crypto

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

/**
 * Owns the master key for the lifetime of an unlocked session.
 *
 * The master key only ever exists in memory, and is wiped on [lock].
 */
class HirzVault(private val context: Context) {

    enum class UnlockResult { SUCCESS, WRONG_SECRET, INVALIDATED, ERROR }

    data class UiState(
        val configured: Boolean = false,
        val unlocked: Boolean = false,
        val biometricEnabled: Boolean = false,
        val biometricAvailable: Boolean = false,
        val biometricInvalidated: Boolean = false
    )

    private val store = VaultStore(context)
    private val random = SecureRandom()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    @Volatile
    private var masterKey: ByteArray? = null

    @Volatile
    private var metadataKey: ByteArray? = null

    init {
        refresh()
    }

    fun refresh() {
        val header = store.read()
        _state.value = UiState(
            configured = header != null,
            unlocked = masterKey != null,
            biometricEnabled = header?.biometricEnabled == true,
            biometricAvailable = KeystoreKeys.hasKey(KeystoreKeys.BIOMETRIC_ALIAS),
            biometricInvalidated = header?.biometricEnabled == true &&
                KeystoreKeys.isBiometricKeyInvalidated()
        )
    }

    fun isUnlocked(): Boolean = masterKey != null

    fun currentMasterKey(): ByteArray? = masterKey?.copyOf()

    fun currentMetadataKey(): ByteArray? = metadataKey?.copyOf()

    fun lock() {
        Bytes.wipe(masterKey, metadataKey)
        masterKey = null
        metadataKey = null
        refresh()
    }

    // ---------------------------------------------------------------- setup

    /** Creates a brand new vault. Returns false when a vault already exists. */
    suspend fun setup(
        pin: String,
        recoveryKeyBytes: ByteArray,
        enableBiometrics: Boolean,
        params: Argon2.Params = Argon2.Params()
    ): Boolean = withContext(Dispatchers.Default) {
        if (store.exists()) return@withContext false

        val mk = ByteArray(32).also { random.nextBytes(it) }
        val pinSalt = ByteArray(16).also { random.nextBytes(it) }
        val recSalt = ByteArray(16).also { random.nextBytes(it) }

        val pinWrap = wrapWithPin(mk, pin, pinSalt, params)
        val recWrap = wrapWithRecoveryKey(mk, recoveryKeyBytes, recSalt, params)

        store.write(
            VaultStore.Header(
                version = HEADER_VERSION,
                argon2 = params,
                pin = pinWrap,
                recovery = recWrap,
                biometric = null,
                biometricEnabled = false
            )
        )

        unlock(mk)
        if (enableBiometrics) {
            // The UI performs the biometric enrolment right after setup so the Keystore key is
            // created under user authentication.
            markBiometricPending()
        }
        Bytes.wipe(mk)
        true
    }

    private var biometricPending = false

    fun markBiometricPending() {
        biometricPending = true
    }

    fun isBiometricPending(): Boolean = biometricPending

    fun clearBiometricPending() {
        biometricPending = false
    }

    // --------------------------------------------------------------- unlock

    suspend fun unlockWithPin(pin: String): UnlockResult = withContext(Dispatchers.Default) {
        val header = store.read() ?: return@withContext UnlockResult.ERROR
        val kek = pinKek(pin, header.pin.salt, header.argon2)
        val mk = Aead.open(kek, Aead.Blob(header.pin.nonce, header.pin.ciphertext), AAD_PIN)
        Bytes.wipe(kek)
        if (mk == null) {
            UnlockResult.WRONG_SECRET
        } else {
            unlock(mk)
            Bytes.wipe(mk)
            UnlockResult.SUCCESS
        }
    }

    /** Builds the cipher the biometric prompt will authorise. */
    fun prepareBiometricDecryptCipher(): Cipher? {
        val header = store.read() ?: return null
        val wrap = header.biometric ?: return null
        val key = KeystoreKeys.biometricKey() ?: return null
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, wrap.nonce))
                updateAAD(AAD_BIO.toByteArray(Charsets.UTF_8))
            }
        } catch (_: Exception) {
            null
        }
    }

    fun prepareBiometricEncryptCipher(): Cipher? {
        val key = KeystoreKeys.biometricKey() ?: return null
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, key)
                updateAAD(AAD_BIO.toByteArray(Charsets.UTF_8))
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Called with the cipher after a successful biometric prompt. */
    fun completeBiometricUnlock(cipher: Cipher): UnlockResult {
        val header = store.read() ?: return UnlockResult.ERROR
        val wrap = header.biometric ?: return UnlockResult.ERROR
        val mk = Aead.openWithCipher(cipher, wrap.ciphertext) ?: return UnlockResult.WRONG_SECRET
        unlock(mk)
        Bytes.wipe(mk)
        return UnlockResult.SUCCESS
    }

    /** Called with the cipher after a successful biometric prompt during enrolment. */
    fun completeBiometricEnrolment(cipher: Cipher): Boolean {
        val mk = masterKey ?: return false
        val header = store.read() ?: return false
        return try {
            val ct = cipher.doFinal(mk)
            store.write(
                header.copy(
                    biometric = VaultStore.Wrap(ByteArray(0), cipher.iv, ct),
                    biometricEnabled = true
                )
            )
            biometricPending = false
            refresh()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun disableBiometrics() {
        val header = store.read() ?: return
        KeystoreKeys.deleteKey(KeystoreKeys.BIOMETRIC_ALIAS)
        store.write(header.copy(biometric = null, biometricEnabled = false))
        refresh()
    }

    // ---------------------------------------------------------- forgot PIN

    /** Verifies the recovery key, then re-wraps the master key with a new PIN. */
    suspend fun resetPinWithRecoveryKey(
        recoveryKeyBytes: ByteArray,
        newPin: String
    ): UnlockResult = withContext(Dispatchers.Default) {
        val header = store.read() ?: return@withContext UnlockResult.ERROR
        val kek = recoveryKek(recoveryKeyBytes, header.recovery.salt, header.argon2)
        val mk = Aead.open(kek, Aead.Blob(header.recovery.nonce, header.recovery.ciphertext), AAD_REC)
        Bytes.wipe(kek)
        if (mk == null) return@withContext UnlockResult.WRONG_SECRET

        val newSalt = ByteArray(16).also { random.nextBytes(it) }
        val pinWrap = wrapWithPin(mk, newPin, newSalt, header.argon2)
        store.write(header.copy(pin = pinWrap))

        unlock(mk)
        Bytes.wipe(mk)
        UnlockResult.SUCCESS
    }

    /** Changes the PIN after verifying the current one. */
    suspend fun changePin(currentPin: String, newPin: String): UnlockResult =
        withContext(Dispatchers.Default) {
            val header = store.read() ?: return@withContext UnlockResult.ERROR
            val oldKek = pinKek(currentPin, header.pin.salt, header.argon2)
            val mk = Aead.open(oldKek, Aead.Blob(header.pin.nonce, header.pin.ciphertext), AAD_PIN)
            Bytes.wipe(oldKek)
            if (mk == null) return@withContext UnlockResult.WRONG_SECRET

            val newSalt = ByteArray(16).also { random.nextBytes(it) }
            store.write(header.copy(pin = wrapWithPin(mk, newPin, newSalt, header.argon2)))
            Bytes.wipe(mk)
            UnlockResult.SUCCESS
        }

    /** Returns the recovery key that wraps the current master key, after verifying the PIN. */
    suspend fun rewrapRecoveryKey(currentPin: String, newRecoveryKey: ByteArray): UnlockResult =
        withContext(Dispatchers.Default) {
            val header = store.read() ?: return@withContext UnlockResult.ERROR
            val kek = pinKek(currentPin, header.pin.salt, header.argon2)
            val mk = Aead.open(kek, Aead.Blob(header.pin.nonce, header.pin.ciphertext), AAD_PIN)
            Bytes.wipe(kek)
            if (mk == null) return@withContext UnlockResult.WRONG_SECRET

            val newSalt = ByteArray(16).also { random.nextBytes(it) }
            store.write(
                header.copy(
                    recovery = wrapWithRecoveryKey(mk, newRecoveryKey, newSalt, header.argon2)
                )
            )
            Bytes.wipe(mk)
            UnlockResult.SUCCESS
        }

    // ------------------------------------------------------------- internals

    private fun unlock(mk: ByteArray) {
        Bytes.wipe(masterKey, metadataKey)
        masterKey = mk.copyOf()
        metadataKey = Hkdf.derive(mk, ByteArray(0), INFO_META, 32)
        refresh()
    }

    private fun wrapWithPin(
        mk: ByteArray,
        pin: String,
        salt: ByteArray,
        params: Argon2.Params
    ): VaultStore.Wrap {
        val kek = pinKek(pin, salt, params)
        val blob = Aead.seal(kek, mk, AAD_PIN)
        Bytes.wipe(kek)
        return VaultStore.Wrap(salt, blob.nonce, blob.ciphertext)
    }

    private fun wrapWithRecoveryKey(
        mk: ByteArray,
        recoveryKeyBytes: ByteArray,
        salt: ByteArray,
        params: Argon2.Params
    ): VaultStore.Wrap {
        val kek = recoveryKek(recoveryKeyBytes, salt, params)
        val blob = Aead.seal(kek, mk, AAD_REC)
        Bytes.wipe(kek)
        return VaultStore.Wrap(salt, blob.nonce, blob.ciphertext)
    }

    /**
     * PIN key: HKDF( Argon2id(PIN, salt) || HMAC(device key, salt) ).
     *
     * The device-bound HMAC makes offline guessing of a copied vault impossible.
     */
    private fun pinKek(pin: String, salt: ByteArray, params: Argon2.Params): ByteArray {
        val argon = Argon2.derive(pin.toByteArray(Charsets.UTF_8), salt, params)
        val device = KeystoreKeys.hmac(salt)
        val combined = argon + device
        val kek = Hkdf.derive(combined, salt, INFO_PIN_KEK.toByteArray(Charsets.UTF_8), 32)
        Bytes.wipe(argon, device, combined)
        return kek
    }

    private fun recoveryKek(
        recoveryKeyBytes: ByteArray,
        salt: ByteArray,
        params: Argon2.Params
    ): ByteArray {
        val argon = Argon2.derive(recoveryKeyBytes, salt, params)
        val kek = Hkdf.derive(argon, salt, INFO_REC_KEK.toByteArray(Charsets.UTF_8), 32)
        Bytes.wipe(argon)
        return kek
    }

    companion object {
        private const val HEADER_VERSION = 1

        private const val AAD_PIN = "hirz.mk.pin.v1"
        private const val AAD_BIO = "hirz.mk.bio.v1"
        private const val AAD_REC = "hirz.mk.rec.v1"
        private val INFO_META = "hirz.meta.v1".toByteArray(Charsets.UTF_8)
        private const val INFO_PIN_KEK = "hirz.pin.kek.v1"
        private const val INFO_REC_KEK = "hirz.rec.kek.v1"

        @Volatile
        private var instance: HirzVault? = null

        fun get(context: Context): HirzVault = instance ?: synchronized(this) {
            instance ?: HirzVault(context.applicationContext).also { instance = it }
        }
    }
}
