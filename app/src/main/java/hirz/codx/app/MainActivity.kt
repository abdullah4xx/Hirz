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
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import hirz.codx.app.crypto.HirzVault
import hirz.codx.app.ui.lock.LockScreen
import hirz.codx.app.ui.setup.SetupFlow
import hirz.codx.app.ui.theme.HirzTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BuildConfig.DEBUG) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }
        enableEdgeToEdge()
        val vault = HirzVault.get(applicationContext)
        setContent {
            HirzTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    HirzApp(vault = vault, activity = this)
                }
            }
        }
    }
}

@Composable
private fun HirzApp(vault: HirzVault, activity: FragmentActivity) {
    val state by vault.state.collectAsState()
    var setupComplete by remember(state.configured) { mutableStateOf(state.configured) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, state.configured) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && state.configured) {
                vault.lock()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
