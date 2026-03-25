package com.fankes.coloros.notify.ui.screen

import android.content.res.Configuration
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fankes.coloros.notify.BuildConfig
import com.fankes.coloros.notify.R
import com.fankes.coloros.notify.const.PackageName
import com.fankes.coloros.notify.data.ConfigData
import com.fankes.coloros.notify.ui.theme.OStatusTheme
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SyncStage {
    Idle,
    SyncingRules,
    MirroringRemote,
}

data class MainScreenState(
    val currentService: XposedService? = null,
    val frameworkSnapshot: ConfigData.FrameworkSnapshot = ConfigData.FrameworkSnapshot(),
    val moduleEnabled: Boolean = false,
    val rulesCount: Int = 0,
    val rulesUpdatedAt: Long = 0L,
    val syncStage: SyncStage = SyncStage.Idle,
) {
    val grantedScopes: Set<String>
        get() = (currentService?.scope ?: frameworkSnapshot.scopes).toSet()

    val missingScopes: Set<String>
        get() = REQUIRED_SCOPES - grantedScopes

    val isReady: Boolean
        get() = moduleEnabled && missingScopes.isEmpty() &&
            (currentService != null || frameworkSnapshot.hasConnectionRecord)

    val isSyncing: Boolean
        get() = syncStage != SyncStage.Idle

    companion object {
        private val REQUIRED_SCOPES = setOf(PackageName.SYSTEM_SCOPE, PackageName.SYSTEM_UI)
    }
}

@Composable
fun MainScreen(
    state: MainScreenState,
    onToggleModule: (Boolean, (String) -> Unit) -> Unit,
    onSyncRules: ((String) -> Unit) -> Unit,
    onRestartSystemUi: ((String) -> Unit) -> Unit,
) {
    var showRestartDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun showSnackbar(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header(onRestartClick = { showRestartDialog = true })
            Subtitle()
            Spacer(modifier = Modifier.height(8.dp))

            StatusCard(state = state)
            SettingsCard(
                moduleEnabled = state.moduleEnabled,
                onToggleModule = { checked -> onToggleModule(checked, ::showSnackbar) },
            )
            RulesCard(
                state = state,
                onSyncRules = { onSyncRules(::showSnackbar) },
            )
        }
    }

    if (showRestartDialog) {
        RestartDialog(
            onDismiss = { showRestartDialog = false },
            onConfirm = {
                showRestartDialog = false
                onRestartSystemUi(::showSnackbar)
            },
        )
    }
}

@Composable
private fun Header(onRestartClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            ),
        )
        IconButton(
            onClick = onRestartClick,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = stringResource(R.string.action_restart_systemui),
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun Subtitle() {
    Text(
        text = stringResource(R.string.home_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = 22.sp,
    )
}

@Composable
private fun StatusCard(state: MainScreenState) {
    val context = LocalContext.current
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (state.isReady) Color(0xFF10B981) else Color(0xFFEF4444)),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.section_module_status),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = moduleStatusText(context, state),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(12.dp))
        StatusRowText(frameworkStatusText(context, state))
        StatusRowText(scopeStatusText(context, state))
        StatusRowText(
            stringResource(
                R.string.label_module_version,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE,
            )
        )
        StatusRowText(
            stringResource(
                R.string.label_target_environment,
                Build.VERSION.RELEASE,
                Build.VERSION.SDK_INT,
            )
        )
    }
}

@Composable
private fun SettingsCard(
    moduleEnabled: Boolean,
    onToggleModule: (Boolean) -> Unit,
) {
    InfoCard {
        Text(
            text = stringResource(R.string.section_core_settings),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.label_enable_icon_enhancement),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Switch(
                checked = moduleEnabled,
                onCheckedChange = onToggleModule,
            )
        }
        Text(
            text = stringResource(R.string.hint_settings_sync),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun RulesCard(
    state: MainScreenState,
    onSyncRules: () -> Unit,
) {
    val lastSyncText = remember(state.rulesUpdatedAt) {
        if (state.rulesUpdatedAt > 0L) {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(Date(state.rulesUpdatedAt))
        } else {
            null
        }
    }

    InfoCard {
        Text(
            text = stringResource(R.string.section_rules_data),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(12.dp))
        StatusRowText(stringResource(R.string.label_local_rules, state.rulesCount))
        StatusRowText(
            stringResource(
                R.string.label_last_sync,
                lastSyncText ?: stringResource(R.string.label_never_synced),
            )
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onSyncRules,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            enabled = !state.isSyncing,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        ) {
            Text(
                text = stringResource(syncButtonTextRes(state.syncStage)),
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            )
        }
    }
}

@Composable
private fun RestartDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.dialog_restart_title),
                fontWeight = FontWeight.Bold,
            )
        },
        text = { Text(text = stringResource(R.string.dialog_restart_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.dialog_confirm_restart),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.dialog_cancel),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
private fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content,
        )
    }
}

@Composable
private fun StatusRowText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

private fun moduleStatusText(
    context: android.content.Context,
    state: MainScreenState,
): String {
    val missingScopes = state.missingScopes.joinToString()
    return when {
        !state.moduleEnabled -> context.getString(R.string.status_module_disabled)
        state.currentService == null && state.frameworkSnapshot.hasConnectionRecord &&
            state.missingScopes.isEmpty() -> context.getString(R.string.status_module_waiting_service)
        state.currentService == null && state.frameworkSnapshot.hasConnectionRecord ->
            context.getString(R.string.status_module_last_connected_missing_scopes, missingScopes)
        state.currentService == null -> context.getString(R.string.status_module_not_connected)
        state.missingScopes.isNotEmpty() ->
            context.getString(R.string.status_module_connected_missing_scopes, missingScopes)
        else -> context.getString(R.string.status_module_connected_ready)
    }
}

private fun frameworkStatusText(
    context: android.content.Context,
    state: MainScreenState,
): String {
    val currentService = state.currentService
    return if (currentService == null) {
        if (state.frameworkSnapshot.hasConnectionRecord) {
            context.getString(
                R.string.status_framework_last_connected,
                state.frameworkSnapshot.frameworkName,
                state.frameworkSnapshot.frameworkVersion,
                state.frameworkSnapshot.apiVersion,
            )
        } else {
            context.getString(R.string.status_framework_not_connected)
        }
    } else {
        context.getString(
            R.string.status_framework_connected,
            currentService.frameworkName,
            currentService.frameworkVersion,
            currentService.apiVersion,
        )
    }
}

private fun scopeStatusText(
    context: android.content.Context,
    state: MainScreenState,
): String {
    val scopeLabel = if (state.grantedScopes.isEmpty()) {
        context.getString(R.string.label_none)
    } else {
        state.grantedScopes.joinToString()
    }
    return if (state.currentService == null) {
        if (state.frameworkSnapshot.hasConnectionRecord) {
            val cachedLabel = if (state.frameworkSnapshot.scopes.isEmpty()) {
                context.getString(R.string.label_none)
            } else {
                state.frameworkSnapshot.scopes.joinToString()
            }
            context.getString(R.string.status_scope_last_detected, cachedLabel)
        } else {
            context.getString(R.string.status_scope_unavailable)
        }
    } else {
        context.getString(R.string.status_scope_granted, scopeLabel)
    }
}

private fun syncButtonTextRes(syncStage: SyncStage): Int = when (syncStage) {
    SyncStage.Idle -> R.string.button_sync_rules
    SyncStage.SyncingRules -> R.string.button_syncing_rules
    SyncStage.MirroringRemote -> R.string.button_mirroring_rules
}

@Preview(
    name = "Main Screen Light",
    showBackground = true,
    widthDp = 393,
    heightDp = 852,
)
@Composable
private fun MainScreenLightPreview() {
    OStatusTheme(darkTheme = false) {
        MainScreen(
            state = MainScreenState(
                frameworkSnapshot = ConfigData.FrameworkSnapshot(
                    frameworkName = "LSPosed",
                    frameworkVersion = "1.9.3",
                    apiVersion = 100,
                    scopes = listOf(PackageName.SYSTEM_SCOPE),
                    lastConnectedAt = 1742860800000L,
                ),
                moduleEnabled = true,
                rulesCount = 128,
                rulesUpdatedAt = 1742861100000L,
                syncStage = SyncStage.Idle,
            ),
            onToggleModule = { _, _ -> },
            onSyncRules = {},
            onRestartSystemUi = {},
        )
    }
}

@Preview(
    name = "Main Screen Dark",
    showBackground = true,
    widthDp = 393,
    heightDp = 852,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun MainScreenDarkPreview() {
    OStatusTheme(darkTheme = true) {
        MainScreen(
            state = MainScreenState(
                frameworkSnapshot = ConfigData.FrameworkSnapshot(
                    frameworkName = "LSPosed",
                    frameworkVersion = "1.9.3",
                    apiVersion = 100,
                    scopes = listOf(PackageName.SYSTEM_SCOPE, PackageName.SYSTEM_UI),
                    lastConnectedAt = 1742860800000L,
                ),
                moduleEnabled = true,
                rulesCount = 256,
                rulesUpdatedAt = 1742861400000L,
                syncStage = SyncStage.MirroringRemote,
            ),
            onToggleModule = { _, _ -> },
            onSyncRules = {},
            onRestartSystemUi = {},
        )
    }
}

@Preview(
    name = "Main Screen Empty",
    showBackground = true,
    widthDp = 393,
    heightDp = 852,
)
@Composable
private fun MainScreenEmptyPreview() {
    OStatusTheme(darkTheme = false) {
        MainScreen(
            state = MainScreenState(
                frameworkSnapshot = ConfigData.FrameworkSnapshot(),
                moduleEnabled = false,
                rulesCount = 0,
                rulesUpdatedAt = 0L,
                syncStage = SyncStage.Idle,
            ),
            onToggleModule = { _, _ -> },
            onSyncRules = {},
            onRestartSystemUi = {},
        )
    }
}
