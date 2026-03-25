package com.fankes.coloros.notify.ui.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fankes.coloros.notify.R
import com.fankes.coloros.notify.data.ConfigData
import com.fankes.coloros.notify.ui.screen.MainScreen
import com.fankes.coloros.notify.ui.screen.MainScreenState
import com.fankes.coloros.notify.ui.screen.SyncStage
import com.fankes.coloros.notify.ui.theme.OStatusTheme
import com.fankes.coloros.notify.utils.tool.FrameworkServiceBridge
import com.fankes.coloros.notify.utils.tool.IconRuleManagerTool
import com.fankes.coloros.notify.utils.tool.RemoteConfigSyncTool
import com.fankes.coloros.notify.utils.tool.SystemUiControl
import io.github.libxposed.service.XposedService

class MainActivity : ComponentActivity() {

    private var uiState by mutableStateOf(MainScreenState())

    private val frameworkListener = object : FrameworkServiceBridge.Listener {
        override fun onServiceChanged(service: XposedService?) {
            runOnUiThread {
                refreshLocalState(currentService = service)
                mirrorPendingRemoteStoreIfNeeded()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ConfigData.initialize(applicationContext)
        refreshLocalState(currentService = FrameworkServiceBridge.getCurrentService())

        enableEdgeToEdge()
        setContent {
            OStatusTheme {
                MainScreen(
                    state = uiState,
                    onToggleModule = ::toggleModuleEnabled,
                    onSyncRules = ::syncRules,
                    onRestartSystemUi = ::performRestartSystemUi,
                )
            }
        }
    }

    private fun refreshLocalState(currentService: XposedService? = uiState.currentService) {
        uiState = uiState.copy(
            currentService = currentService,
            frameworkSnapshot = ConfigData.readFrameworkSnapshot(),
            moduleEnabled = ConfigData.isModuleEnabled && ConfigData.isIconEnhancementEnabled,
            rulesCount = ConfigData.rulesCount,
            rulesUpdatedAt = ConfigData.rulesUpdatedAt,
        )
    }

    private fun toggleModuleEnabled(checked: Boolean, onShowMessage: (String) -> Unit) {
        ConfigData.isModuleEnabled = checked
        ConfigData.isIconEnhancementEnabled = checked
        refreshLocalState()

        val currentService = uiState.currentService
        if (currentService == null) {
            onShowMessage(getString(R.string.message_settings_saved_local_pending_sync))
            return
        }
        mirrorToRemoteStore(currentService) { result ->
            val message = result.fold(
                onSuccess = { getString(R.string.message_settings_saved_synced_restart) },
                onFailure = {
                    getString(
                        R.string.message_settings_saved_mirror_failed,
                        it.message ?: it.javaClass.simpleName
                    )
                },
            )
            onShowMessage(message)
        }
    }

    private fun syncRules(onShowMessage: (String) -> Unit) {
        uiState = uiState.copy(syncStage = SyncStage.SyncingRules)
        IconRuleManagerTool.syncRules { result ->
            result.onSuccess { syncResult ->
                refreshLocalState()
                val currentService = uiState.currentService
                if (currentService == null) {
                    uiState = uiState.copy(syncStage = SyncStage.Idle)
                    onShowMessage(
                        getString(
                            R.string.message_rules_sync_success_local_pending,
                            syncResult.count
                        )
                    )
                    return@onSuccess
                }

                uiState = uiState.copy(syncStage = SyncStage.MirroringRemote)
                mirrorToRemoteStore(currentService) { mirrorResult ->
                    uiState = uiState.copy(syncStage = SyncStage.Idle)
                    refreshLocalState()
                    val message = mirrorResult.fold(
                        onSuccess = {
                            getString(
                                R.string.message_rules_sync_success_remote,
                                syncResult.count
                            )
                        },
                        onFailure = {
                            getString(
                                R.string.message_rules_sync_mirror_failed,
                                syncResult.count,
                                it.message ?: it.javaClass.simpleName
                            )
                        },
                    )
                    onShowMessage(message)
                }
            }.onFailure {
                uiState = uiState.copy(syncStage = SyncStage.Idle)
                onShowMessage(
                    getString(
                        R.string.message_rules_sync_failed,
                        it.message ?: it.javaClass.simpleName
                    )
                )
            }
        }
    }

    private fun mirrorPendingRemoteStoreIfNeeded() {
        val currentService = uiState.currentService ?: return
        if (!ConfigData.hasPendingRemoteSync) return
        mirrorToRemoteStore(currentService)
    }

    private fun mirrorToRemoteStore(
        service: XposedService,
        onResult: ((Result<RemoteConfigSyncTool.SyncResult>) -> Unit)? = null,
    ) {
        RemoteConfigSyncTool.syncAsync(service) { result ->
            refreshLocalState()
            onResult?.invoke(result)
        }
    }

    private fun performRestartSystemUi(onShowMessage: (String) -> Unit) {
        val currentService = uiState.currentService
        if (currentService == null && ConfigData.hasPendingRemoteSync) {
            onShowMessage(getString(R.string.message_pending_sync_then_restart))
            return
        }
        if (currentService == null) {
            restartSystemUiDirectly(onShowMessage)
            return
        }
        mirrorToRemoteStore(currentService) { mirrorResult ->
            mirrorResult.onSuccess {
                restartSystemUiDirectly(onShowMessage)
            }.onFailure {
                onShowMessage(
                    getString(
                        R.string.message_config_not_synced,
                        it.message ?: it.javaClass.simpleName
                    )
                )
            }
        }
    }

    private fun restartSystemUiDirectly(onShowMessage: (String) -> Unit) {
        SystemUiControl.restartSystemUi { result ->
            result.onSuccess {
                onShowMessage(getString(R.string.message_restart_requested))
            }.onFailure {
                onShowMessage(
                    getString(
                        R.string.message_restart_failed,
                        it.message ?: it.javaClass.simpleName
                    )
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        FrameworkServiceBridge.addListener(frameworkListener)
    }

    override fun onStop() {
        FrameworkServiceBridge.removeListener(frameworkListener)
        super.onStop()
    }
}
