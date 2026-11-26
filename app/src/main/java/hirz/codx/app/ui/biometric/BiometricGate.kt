package hirz.codx.app.ui.biometric

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher

/**
 * Thin wrapper over [BiometricPrompt] that always requires a [Cipher], so unlocking is bound to the
 * Keystore key rather than to a UI check.
 */
object BiometricGate {

    private const val AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_STRONG

    fun isAvailable(activity: FragmentActivity): Boolean =
        BiometricManager.from(activity).canAuthenticate(AUTHENTICATORS) ==
            BiometricManager.BIOMETRIC_SUCCESS

    fun prompt(
        activity: FragmentActivity,
        cipher: Cipher,
        title: String,
        subtitle: String,
        negativeLabel: String? = null,
        onSuccess: (Cipher) -> Unit,
        onFailure: () -> Unit,
        onUnavailable: () -> Unit
    ) {
        val prompt = BiometricPrompt(
            activity,
            activity.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val authenticated = result.cryptoObject?.cipher
                    if (authenticated != null) {
                        onSuccess(authenticated)
                    } else {
                        onFailure()
                    }
                }

                override fun onAuthenticationFailed() {
                    onFailure()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onUnavailable()
                }
            }
        )

        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .setConfirmationRequired(true)

        if (negativeLabel != null) {
            builder.setNegativeButtonText(negativeLabel)
        }

        prompt.authenticate(builder.build(), BiometricPrompt.CryptoObject(cipher))
    }
}
