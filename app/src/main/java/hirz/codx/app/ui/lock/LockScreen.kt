package hirz.codx.app.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import hirz.codx.app.R
import hirz.codx.app.crypto.HirzVault
import hirz.codx.app.crypto.RecoveryKey
import hirz.codx.app.ui.biometric.BiometricGate
import hirz.codx.app.ui.components.PinDots
import hirz.codx.app.ui.components.PinPad
import kotlinx.coroutines.launch

private enum class LockMode { PIN, FORGOT_KEY, FORGOT_NEW_PIN }

@Composable
fun LockScreen(vault: HirzVault, activity: FragmentActivity, onUnlocked: () -> Unit) {
    var mode by remember { mutableStateOf(LockMode.PIN) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var recoveryInput by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var pendingRecovery by remember { mutableStateOf<ByteArray?>(null) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val state by vault.state.collectAsState()

    fun tryBiometric() {
        val cipher = vault.prepareBiometricDecryptCipher()
        if (cipher == null) {
            error = context.getString(R.string.biometric_key_invalid)
            return
        }
        BiometricGate.prompt(
            activity = activity,
            cipher = cipher,
            title = context.getString(R.string.lock_biometric_prompt_title),
            subtitle = context.getString(R.string.lock_biometric_prompt_subtitle),
            negativeLabel = context.getString(R.string.action_cancel),
            onSuccess = { authenticated ->
                when (vault.completeBiometricUnlock(authenticated)) {
                    HirzVault.UnlockResult.SUCCESS -> onUnlocked()
                    else -> error = context.getString(R.string.biometric_failed)
                }
            },
            onFailure = { error = context.getString(R.string.biometric_failed) },
            onUnavailable = { }
        )
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (mode) {
                LockMode.PIN -> {
                    Spacer(Modifier.height(32.dp))
                    Text(
                        text = stringResource(R.string.lock_title),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = stringResource(R.string.lock_enter_pin),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    PinDots(count = pin.length)
                    error?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    PinPad(
                        enabled = !busy,
                        onDigit = { if (pin.length < 12) { pin += it; error = null } },
                        onDelete = { if (pin.isNotEmpty()) pin = pin.dropLast(1) }
                    )
                    Button(
                        onClick = {
                            if (pin.isEmpty()) return@Button
                            busy = true
                            scope.launch {
                                val result = vault.unlockWithPin(pin)
                                busy = false
                                when (result) {
                                    HirzVault.UnlockResult.SUCCESS -> onUnlocked()
                                    HirzVault.UnlockResult.WRONG_SECRET -> {
                                        error = context.getString(R.string.lock_wrong_pin)
                                        pin = ""
                                    }
                                    else -> {
                                        error = context.getString(R.string.error_generic)
                                        pin = ""
                                    }
                                }
                            }
                        },
                        enabled = pin.isNotEmpty() && !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (busy) {
                            CircularProgressIndicator(modifier = Modifier.height(18.dp))
                        } else {
                            Text(stringResource(R.string.lock_unlock))
                        }
                    }
                    if (state.biometricEnabled && !state.biometricInvalidated) {
                        OutlinedButton(onClick = ::tryBiometric, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.lock_use_fingerprint))
                        }
                    }
                    if (state.biometricInvalidated) {
                        Text(
                            text = stringResource(R.string.biometric_key_invalid),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                    TextButton(onClick = { mode = LockMode.FORGOT_KEY; error = null }) {
                        Text(stringResource(R.string.lock_forgot_pin))
                    }
                }

                LockMode.FORGOT_KEY -> {
                    Text(
                        text = stringResource(R.string.forgot_title),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = stringResource(R.string.forgot_body),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    OutlinedTextField(
                        value = recoveryInput,
                        onValueChange = { recoveryInput = it; error = null },
                        singleLine = false,
                        minLines = 2,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    error?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { mode = LockMode.PIN; error = null }) {
                            Text(stringResource(R.string.action_back))
                        }
                        Button(
                            onClick = {
                                val parsed = RecoveryKey.parse(recoveryInput)
                                if (parsed == null) {
                                    error = context.getString(R.string.forgot_wrong_key)
                                } else {
                                    pendingRecovery = parsed
                                    newPin = ""
                                    confirmPin = ""
                                    error = null
                                    mode = LockMode.FORGOT_NEW_PIN
                                }
                            }
                        ) {
                            Text(stringResource(R.string.forgot_continue))
                        }
                    }
                }

                LockMode.FORGOT_NEW_PIN -> {
                    Text(
                        text = stringResource(R.string.forgot_new_pin_title),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = stringResource(R.string.setup_pin_body),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { if (it.length <= 12 && it.all(Char::isDigit)) newPin = it },
                        label = { Text(stringResource(R.string.setup_pin_title)) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Next
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = {
                            if (it.length <= 12 && it.all(Char::isDigit)) confirmPin = it
                        },
                        label = { Text(stringResource(R.string.setup_pin_confirm_title)) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    error?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { mode = LockMode.FORGOT_KEY; error = null }) {
                            Text(stringResource(R.string.action_back))
                        }
                        Button(
                            enabled = !busy,
                            onClick = {
                                val key = pendingRecovery
                                when {
                                    key == null -> error = context.getString(R.string.error_generic)
                                    newPin.length < 4 ->
                                        error = context.getString(R.string.setup_pin_too_short)
                                    newPin != confirmPin ->
                                        error = context.getString(R.string.setup_pin_mismatch)
                                    else -> {
                                        busy = true
                                        scope.launch {
                                            val result = vault.resetPinWithRecoveryKey(key, newPin)
                                            busy = false
                                            when (result) {
                                                HirzVault.UnlockResult.SUCCESS -> onUnlocked()
                                                HirzVault.UnlockResult.WRONG_SECRET ->
                                                    error = context.getString(R.string.forgot_wrong_key)
                                                else -> error = context.getString(R.string.error_generic)
                                            }
                                        }
                                    }
                                }
                            }
                        ) {
                            if (busy) {
                                CircularProgressIndicator(modifier = Modifier.height(18.dp))
                            } else {
                                Text(stringResource(R.string.action_continue))
                            }
                        }
                    }
                }
            }
        }
    }
}
