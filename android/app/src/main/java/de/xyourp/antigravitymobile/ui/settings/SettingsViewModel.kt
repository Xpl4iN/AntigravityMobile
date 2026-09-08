package de.xyourp.antigravitymobile.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.xyourp.antigravitymobile.data.AppRepository
import de.xyourp.antigravitymobile.data.ConnectionSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class SettingsUiState(val host: String = "", val port: String = "5117", val paired: Boolean = false, val pairCode: String = "", val busy: Boolean = false, val message: String? = null)
class SettingsViewModel(private val repo: AppRepository) : ViewModel() {
    private val mutable = MutableStateFlow(SettingsUiState()); val state: StateFlow<SettingsUiState> = mutable.asStateFlow()
    init { viewModelScope.launch { val s = repo.settings.first(); mutable.value = SettingsUiState(s.host, s.restPort.toString(), s.deviceToken.isNotBlank()) } }
    fun host(value: String) { mutable.value = mutable.value.copy(host = value.trim(), message = null) }
    fun port(value: String) { mutable.value = mutable.value.copy(port = value.filter(Char::isDigit).take(5), message = null) }
    fun code(value: String) { mutable.value = mutable.value.copy(pairCode = value.filter(Char::isDigit).take(6), message = null) }
    private fun target() = ConnectionSettings(mutable.value.host, mutable.value.port.toIntOrNull() ?: 5117, mutable.value.port.toIntOrNull() ?: 5117)
    fun test() = viewModelScope.launch { mutable.value = mutable.value.copy(busy = true); runCatching { repo.api.health(target()) }.onSuccess { mutable.value = mutable.value.copy(busy = false, message = if (it.ok) "OAC bridge found, protocol ${it.protocolVersion}" else "Unexpected bridge response") }.onFailure { mutable.value = mutable.value.copy(busy = false, message = it.message) } }
    fun pair() = viewModelScope.launch {
        if (mutable.value.pairCode.length != 6) { mutable.value = mutable.value.copy(message = "Enter the six-digit code shown in OAC settings"); return@launch }
        mutable.value = mutable.value.copy(busy = true)
        val error = runCatching { repo.pair(target(), mutable.value.pairCode, android.os.Build.MODEL) }.getOrElse { it.message ?: "Pairing failed" }
        mutable.value = mutable.value.copy(busy = false, paired = error == null, message = error ?: "Paired successfully")
    }
    fun save(done: () -> Unit) = viewModelScope.launch { repo.saveConnection(target()); done() }
}
