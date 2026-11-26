package hirz.codx.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
/* lifecycle locking handled in Activity */
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import hirz.codx.app.crypto.HirzVault
import hirz.codx.app.ui.lock.LockScreen
import hirz.codx.app.ui.setup.SetupFlow
import hirz.codx.app.ui.theme.HirzTheme

class MainActivity : FragmentActivity() {

    private val vault by lazy { HirzVault.get(applicationContext) }
    private val screenOffReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action == android.content.Intent.ACTION_SCREEN_OFF) {
                vault.lock()
            }
        }
    }
    private var receiverRegistered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BuildConfig.DEBUG) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }
        enableEdgeToEdge()
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                if (vault.state.value.configured) vault.lock()
            }
        })
        setContent {
            HirzTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    HirzApp(vault = vault, activity = this)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (!receiverRegistered) {
            registerReceiver(
                screenOffReceiver,
                android.content.IntentFilter(android.content.Intent.ACTION_SCREEN_OFF)
            )
            receiverRegistered = true
        }
    }

    override fun onDestroy() {
        if (receiverRegistered) {
            unregisterReceiver(screenOffReceiver)
            receiverRegistered = false
        }
        super.onDestroy()
    }
}

@Composable
private fun HirzApp(vault: HirzVault, activity: FragmentActivity) {
    val state by vault.state.collectAsState()
    var setupComplete by remember(state.configured) { mutableStateOf(state.configured) }
    // Locking is handled by ProcessLifecycleOwner in MainActivity to cover both app background
    // and process-level transitions. No idle timer by design.

    when {
        !setupComplete -> SetupFlow(
            vault = vault,
            activity = activity,
            onFinished = {
                setupComplete = true
                vault.refresh()
            }
        )

        !state.unlocked -> LockScreen(
            vault = vault,
            activity = activity,
            onUnlocked = { vault.refresh() }
        )

        else -> UnlockedPlaceholder(onLock = { vault.lock() })
    }
}

@Composable
private fun UnlockedPlaceholder(onLock: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.vault_unlocked_placeholder),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp)
        )
        androidx.compose.material3.TextButton(onClick = onLock, modifier = Modifier.padding(top = 24.dp)) {
            Text(stringResource(R.string.action_lock))
        }
    }
}
