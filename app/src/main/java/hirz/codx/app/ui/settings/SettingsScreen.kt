package hirz.codx.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hirz.codx.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onLock: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action_settings)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) } }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            ListItem(headlineContent = { Text(stringResource(R.string.action_lock)) }, supportingContent = { Text(stringResource(R.string.settings_lock_now)) }, modifier = Modifier.padding(vertical = 4.dp))
            HorizontalDivider()
            ListItem(headlineContent = { Text(stringResource(R.string.settings_recents_title)) }, supportingContent = { Text(stringResource(R.string.settings_recents_blank)) })
            HorizontalDivider()
            ListItem(headlineContent = { Text(stringResource(R.string.settings_language_title)) }, supportingContent = { Text(stringResource(R.string.settings_language_system)) })
            HorizontalDivider()
            ListItem(headlineContent = { Text(stringResource(R.string.settings_icon_title)) }, supportingContent = { Text(stringResource(R.string.settings_icon_default)) })
            HorizontalDivider()
            ListItem(headlineContent = { Text(stringResource(R.string.settings_backup_title)) }, supportingContent = { Text(stringResource(R.string.settings_backup_not_configured)) })
            HorizontalDivider()
            TextButton(onClick = onLock) { Text(stringResource(R.string.action_lock)) }
        }
    }
}
