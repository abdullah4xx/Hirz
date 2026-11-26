package hirz.codx.app.ui.setup

import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import hirz.codx.app.R
import hirz.codx.app.crypto.Argon2
import hirz.codx.app.crypto.HirzVault
import hirz.codx.app.crypto.KeystoreKeys
import hirz.codx.app.crypto.RecoveryKey
import hirz.codx.app.ui.biometric.BiometricGate
import hirz.codx.app.ui.components.PinDots
import hirz.codx.app.ui.components.PinPad
import kotlinx.coroutines.launch
import kotlin.random.Random

private enum class Step { WELCOME, PIN_CREATE, PIN_CONFIRM, BIOMETRIC, RECOVERY_SHOW, RECOVERY_CONFIRM, DONE }

@Composable
fun SetupFlow(vault: HirzVault, activity: FragmentActivity, onFinished: () -> Unit) {
    var step by remember { mutableStateOf(Step.WELCOME) }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var enableBiometrics by remember { mutableStateOf(false) }
    var recoveryBytes by remember { mutableStateOf<ByteArray?>(null) }
    var recoveryText by remember { mutableStateOf("") }
    var confirmGroups by remember { mutableStateOf(listOf<Int>()) }
    var confirmInput by remember { mutableStateOf(listOf("", "")) }
    var argonParams by remember { mutableStateOf(Argon2.Params()) }
    var calibrationAttempted by remember { mutableIntStateOf(0) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Calibrate Argon2id once so the PIN derivation lands in the 300-500 ms window.
    LaunchedEffect(Unit) {
        if (calibrationAttempted == 0) {
            calibrationAttempted = 1
            argonParams = Argon2.calibrate()
        }
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
            when (step) {
                Step.WELCOME -> Welcome(onStart = { step = Step.PIN_CREATE })

                Step.PIN_CREATE -> PinEntry(
                    title = stringResource(R.string.setup_pin_title),
                    body = stringResource(R.string.setup_pin_body),
                    warning = stringResource(R.string.setup_pin_warning),
                    pin = pin,
                    error = error,
                    onDigit = { if (pin.length < 12) { pin += it; error = null } },
                    onDelete = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
                    onContinue = {
                        if (pin.length < 4) {
                            error = context.getString(R.string.setup_pin_too_short)
                        } else {
                            error = null
                            step = Step.PIN_CONFIRM
                        }
                    }
                )

                Step.PIN_CONFIRM -> PinEntry(
                    title = stringResource(R.string.setup_pin_confirm_title),
                    body = null,
                    warning = null,
                    pin = confirmPin,
                    error = error,
                    onDigit = { if (confirmPin.length < 12) { confirmPin += it; error = null } },
                    onDelete = { if (confirmPin.isNotEmpty()) confirmPin = confirmPin.dropLast(1) },
                    onContinue = {
                        if (confirmPin == pin) {
                            error = null
                            step = Step.BIOMETRIC
                        } else {
                            error = context.getString(R.string.setup_pin_mismatch)
                            confirmPin = ""
                        }
                    }
                )

                Step.BIOMETRIC -> BiometricChoice(
                    available = BiometricGate.isAvailable(activity),
                    onEnable = { enableBiometrics = true; step = Step.RECOVERY_SHOW },
                    onSkip = { enableBiometrics = false; step = Step.RECOVERY_SHOW }
                )

                Step.RECOVERY_SHOW -> {
                    val bytes = remember { recoveryBytes ?: RecoveryKey.generate().also { recoveryBytes = it } }
                    val formatted = remember(bytes) { RecoveryKey.format(bytes) }
                    RecoveryDisplay(
                        formatted = formatted,
                        onContinue = {
                            val groups = formatted.split("-")
                            val first = Random.nextInt(groups.size)
                            var second = Random.nextInt(groups.size)
                            while (second == first) second = Random.nextInt(groups.size)
                            confirmGroups = listOf(first, second)
                            confirmInput = listOf("", "")
                            step = Step.RECOVERY_CONFIRM
                        }
                    )
                }

                Step.RECOVERY_CONFIRM -> {
                    val formatted = RecoveryKey.format(recoveryBytes ?: ByteArray(0))
                    val groups = formatted.split("-")
                    RecoveryConfirm(
                        groups = groups,
                        indices = confirmGroups,
                        values = confirmInput,
                        error = error,
                        busy = busy,
                        onValueChange = { index, value ->
                            confirmInput = confirmInput.toMutableList().also {
                                it[index] = value.uppercase().take(4)
                            }
                            error = null
                        },
                        onBack = { step = Step.RECOVERY_SHOW },
                        onConfirm = {
                            val ok = confirmGroups.indices.all { i ->
                                groups[confirmGroups[i]].equals(confirmInput[i], ignoreCase = true)
                            }
                            if (!ok) {
                                error = context.getString(R.string.setup_recovery_confirm_error)
                            } else {
                                busy = true
                                scope.launch {
                                    val created = vault.setup(
                                        pin = pin,
                                        recoveryKeyBytes = recoveryBytes ?: ByteArray(0),
                                        enableBiometrics = enableBiometrics,
                                        params = argonParams
                                    )
                                    busy = false
                                    if (created) {
                                        if (enableBiometrics) {
                                            KeystoreKeys.createBiometricKey()
                                            val cipher = vault.prepareBiometricEncryptCipher()
                                            if (cipher == null) {
                                                step = Step.DONE
                                            } else {
                                                BiometricGate.prompt(
                                                    activity = activity,
                                                    cipher = cipher,
                                                    title = context.getString(R.string.setup_bio_title),
                                                    subtitle = context.getString(R.string.setup_bio_body),
                                                    negativeLabel = context.getString(R.string.setup_bio_skip),
                                                    onSuccess = { c ->
                                                        vault.completeBiometricEnrolment(c)
                                                        step = Step.DONE
                                                    },
                                                    onFailure = { step = Step.DONE },
                                                    onUnavailable = { step = Step.DONE }
                                                )
                                            }
                                        } else {
                                            step = Step.DONE
                                        }
                                    } else {
                                        error = context.getString(R.string.error_generic)
                                    }
                                }
                            }
                        }
                    )
                }

                Step.DONE -> DoneStep(
                    onOpenBatterySettings = {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                        context.startActivity(intent)
                    },
                    onFinish = onFinished
                )
            }
        }
    }
}

@Composable
private fun Welcome(onStart: () -> Unit) {
    Spacer(Modifier.height(48.dp))
    Text(
        text = stringResource(R.string.setup_welcome_title),
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center
    )
    Text(
        text = stringResource(R.string.setup_welcome_body),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(16.dp))
    Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.setup_welcome_start))
    }
}

@Composable
private fun PinEntry(
    title: String,
    body: String?,
    warning: String?,
    pin: String,
    error: String?,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onContinue: () -> Unit
) {
    Text(text = title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    body?.let {
        Text(text = it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
    PinDots(count = pin.length)
    error?.let {
        Text(
            text = it,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
    PinPad(onDigit = onDigit, onDelete = onDelete)
    Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.action_continue))
    }
    warning?.let {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Composable
private fun BiometricChoice(available: Boolean, onEnable: () -> Unit, onSkip: () -> Unit) {
    Text(
        text = stringResource(R.string.setup_bio_title),
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center
    )
    Text(
        text = stringResource(R.string.setup_bio_body),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center
    )
    if (!available) {
        Text(
            text = stringResource(R.string.biometric_unavailable),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
    }
    Spacer(Modifier.height(8.dp))
    Button(onClick = onEnable, enabled = available, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.setup_bio_enable))
    }
    OutlinedButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.setup_bio_skip))
    }
}

@Composable
private fun RecoveryDisplay(formatted: String, onContinue: () -> Unit) {
    Text(
        text = stringResource(R.string.setup_recovery_title),
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center
    )
    Text(
        text = stringResource(R.string.setup_recovery_body),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center
    )
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    ) {
        Text(
            text = formatted,
            fontFamily = FontFamily.Monospace,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        )
    }
    Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.setup_recovery_continue))
    }
}

@Composable
private fun RecoveryConfirm(
    groups: List<String>,
    indices: List<Int>,
    values: List<String>,
    error: String?,
    busy: Boolean,
    onValueChange: (Int, String) -> Unit,
    onBack: () -> Unit,
    onConfirm: () -> Unit
) {
    Text(
        text = stringResource(R.string.setup_recovery_confirm_title),
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center
    )
    Text(
        text = stringResource(R.string.setup_recovery_confirm_body),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center
    )
    indices.forEachIndexed { position, groupIndex ->
        OutlinedTextField(
            value = values.getOrElse(position) { "" },
            onValueChange = { onValueChange(position, it) },
            label = { Text("${position + 1}") },
            placeholder = { Text("••••") },
            singleLine = true,
            enabled = !busy,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
    error?.let {
        Text(
            text = it,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onBack, enabled = !busy) {
            Text(stringResource(R.string.action_back))
        }
        Button(onClick = onConfirm, enabled = !busy) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.height(18.dp))
            } else {
                Text(stringResource(R.string.action_continue))
            }
        }
    }
}

@Composable
private fun DoneStep(onOpenBatterySettings: () -> Unit, onFinish: () -> Unit) {
    Spacer(Modifier.height(48.dp))
    Text(
        text = stringResource(R.string.setup_done_title),
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center
    )
    Text(
        text = stringResource(R.string.setup_done_body),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center
    )
    OutlinedButton(onClick = onOpenBatterySettings, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.setup_done_open_settings))
    }
    Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.setup_done_finish))
    }
}
