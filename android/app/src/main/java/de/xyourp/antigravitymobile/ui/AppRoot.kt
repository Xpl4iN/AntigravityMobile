package de.xyourp.antigravitymobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.xyourp.antigravitymobile.data.AppRepository
import de.xyourp.antigravitymobile.ui.chat.ChatViewModel
import de.xyourp.antigravitymobile.ui.settings.SettingsScreen
import de.xyourp.antigravitymobile.ui.settings.SettingsViewModel
import de.xyourp.antigravitymobile.ui.theme.AntigravityTheme

@Composable fun AppRoot(repo: AppRepository) {
    val settings by repo.settings.collectAsStateWithLifecycle(initialValue = null)
    var showSettings by remember { mutableStateOf(false) }
    val settingsVm: SettingsViewModel = viewModel(factory = appViewModelFactory { SettingsViewModel(repo) })
    val chatVm: ChatViewModel = viewModel(factory = appViewModelFactory { ChatViewModel(repo) })
    val chat by chatVm.state.collectAsStateWithLifecycle()
    val display by repo.displayPreferences.collectAsStateWithLifecycle(initialValue = de.xyourp.antigravitymobile.data.DisplayPreferences())
    AntigravityTheme(chat.snapshot.theme.tokens) {
        if (settings == null) return@AntigravityTheme
        if (showSettings || settings?.isConfigured != true || settings?.deviceToken.isNullOrBlank()) SettingsScreen(settingsVm) { showSettings = false }
        else MainScaffold(chatVm, repo.socket.connected.collectAsStateWithLifecycle().value, display) { showSettings = true }
    }
}
