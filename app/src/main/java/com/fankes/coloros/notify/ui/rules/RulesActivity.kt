package com.fankes.coloros.notify.ui.rules

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fankes.coloros.notify.R
import com.fankes.coloros.notify.core.SystemPackages
import com.fankes.coloros.notify.framework.LsposedServiceBridge
import com.fankes.coloros.notify.framework.RemoteRuleMirror
import com.fankes.coloros.notify.framework.SystemUiRefreshSignal
import com.fankes.coloros.notify.rules.IconRule
import com.fankes.coloros.notify.rules.RuleStore
import com.fankes.coloros.notify.ui.theme.ColorOSNotifyIconTheme
import io.github.libxposed.service.XposedService

class RulesActivity : ComponentActivity() {

    private var uiState by mutableStateOf(RuleListState())
    private var currentService: XposedService? = null

    private val frameworkListener = object : LsposedServiceBridge.Listener {
        override fun onServiceChanged(service: XposedService?) {
            runOnUiThread {
                refreshState(currentService = service)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshState(currentService = LsposedServiceBridge.getCurrentService())

        enableEdgeToEdge()
        setContent {
            ColorOSNotifyIconTheme {
                RuleListScreen(
                    state = uiState,
                    onBack = ::finish,
                    onQueryChange = ::updateQuery,
                    onRuleEnabledChange = ::setRuleEnabled,
                    onRuleEnabledAllChange = ::setRuleEnabledAll,
                )
            }
        }
    }

    private fun refreshState(currentService: XposedService? = this.currentService) {
        this.currentService = currentService
        uiState = uiState.copy(
            rules = RuleStore.rules,
            config = RuleStore.moduleConfig,
            canEditConfig = currentService?.scope?.containsAll(REQUIRED_SCOPES) == true,
        )
    }

    private fun updateQuery(query: String) {
        uiState = uiState.copy(query = query)
    }

    private fun setRuleEnabled(rule: IconRule, enabled: Boolean, onShowMessage: (String) -> Unit) {
        val service = requireFrameworkService(onShowMessage) ?: return
        val previous = rule.isEnabled
        RuleStore.setRuleEnabled(rule.packageName, enabled)
        uiState = uiState.copy(
            rules = uiState.rules.mapRule(rule.packageName) { it.copy(isEnabled = enabled) },
            config = RuleStore.moduleConfig,
        )
        mirrorConfigChanged(service, onShowMessage) {
            RuleStore.setRuleEnabled(rule.packageName, previous)
            uiState = uiState.copy(
                rules = uiState.rules.mapRule(rule.packageName) { it.copy(isEnabled = previous) },
                config = RuleStore.moduleConfig,
            )
        }
    }

    private fun setRuleEnabledAll(rule: IconRule, enabledAll: Boolean, onShowMessage: (String) -> Unit) {
        val service = requireFrameworkService(onShowMessage) ?: return
        val previous = rule.isEnabledAll
        RuleStore.setRuleEnabledAll(rule.packageName, enabledAll)
        uiState = uiState.copy(
            rules = uiState.rules.mapRule(rule.packageName) { it.copy(isEnabledAll = enabledAll) },
            config = RuleStore.moduleConfig,
        )
        mirrorConfigChanged(service, onShowMessage) {
            RuleStore.setRuleEnabledAll(rule.packageName, previous)
            uiState = uiState.copy(
                rules = uiState.rules.mapRule(rule.packageName) { it.copy(isEnabledAll = previous) },
                config = RuleStore.moduleConfig,
            )
        }
    }

    private fun mirrorConfigChanged(
        service: XposedService,
        onShowMessage: (String) -> Unit,
        rollback: () -> Unit,
    ) {
        RemoteRuleMirror.syncAsync(service) { result ->
            result.onSuccess {
                SystemUiRefreshSignal.request(this)
            }
            result.onFailure {
                rollback()
                onShowMessage(
                    getString(
                        R.string.message_config_mirror_failed,
                        it.message ?: it.javaClass.simpleName,
                    )
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        LsposedServiceBridge.addListener(frameworkListener)
    }

    override fun onStop() {
        LsposedServiceBridge.removeListener(frameworkListener)
        super.onStop()
    }

    private fun requireFrameworkService(onShowMessage: (String) -> Unit): XposedService? {
        val service = currentService
        if (service == null) {
            onShowMessage(getString(R.string.message_framework_unavailable))
        }
        return service
    }

    private fun List<IconRule>.mapRule(
        packageName: String,
        transform: (IconRule) -> IconRule,
    ) = map { rule ->
        if (rule.packageName == packageName) transform(rule) else rule
    }

    companion object {
        private val REQUIRED_SCOPES = setOf(SystemPackages.SYSTEM_SCOPE, SystemPackages.SYSTEM_UI)
    }
}
