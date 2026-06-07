package com.fankes.coloros.notify.ui.home

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
import com.fankes.coloros.notify.ui.theme.OStatusTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    state: HomeScreenState,
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
private fun StatusCard(state: HomeScreenState) {
    val context = LocalContext.current
    val indicatorColor = when {
        !state.isModuleActive -> MaterialTheme.colorScheme.error
        state.missingScopes.isNotEmpty() -> MaterialTheme.colorScheme.tertiary
        else -> Color(0xFF10B981)
    }
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(indicatorColor),
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
        StatusRowText(stringResource(R.string.label_manager_control_hint))
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
private fun RulesCard(
    state: HomeScreenState,
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
    state: HomeScreenState,
): String {
    val missingScopes = state.missingScopes.joinToString()
    return when {
        !state.isModuleActive -> context.getString(R.string.status_module_not_connected)
        state.missingScopes.isNotEmpty() ->
            context.getString(R.string.status_module_connected_missing_scopes, missingScopes)
        else -> context.getString(R.string.status_module_connected_ready)
    }
}

private fun frameworkStatusText(
    context: android.content.Context,
    state: HomeScreenState,
): String {
    val frameworkConnection = state.frameworkConnection
    return if (frameworkConnection == null) {
        context.getString(R.string.status_framework_not_connected)
    } else {
        context.getString(
            R.string.status_framework_connected,
            frameworkConnection.name,
            frameworkConnection.version,
            frameworkConnection.apiVersion,
        )
    }
}

private fun scopeStatusText(
    context: android.content.Context,
    state: HomeScreenState,
): String {
    return if (state.frameworkConnection == null) {
        context.getString(R.string.status_scope_unavailable)
    } else {
        val scopeLabel = if (state.grantedScopes.isEmpty()) {
            context.getString(R.string.label_none)
        } else {
            state.grantedScopes.joinToString()
        }
        context.getString(R.string.status_scope_granted, scopeLabel)
    }
}

private fun syncButtonTextRes(syncStage: RuleSyncStage): Int = when (syncStage) {
    RuleSyncStage.Idle -> R.string.button_sync_rules
    RuleSyncStage.SyncingRules -> R.string.button_syncing_rules
    RuleSyncStage.MirroringRemote -> R.string.button_mirroring_rules
}

@Preview(
    name = "Home Screen Light",
    showBackground = true,
    widthDp = 393,
    heightDp = 852,
)
@Composable
private fun HomeScreenLightPreview() {
    OStatusTheme(darkTheme = false) {
        HomeScreen(
            state = HomeScreenState(
                rulesCount = 128,
                rulesUpdatedAt = 1742861100000L,
                syncStage = RuleSyncStage.Idle,
            ),
            onSyncRules = {},
            onRestartSystemUi = {},
        )
    }
}

@Preview(
    name = "Home Screen Dark",
    showBackground = true,
    widthDp = 393,
    heightDp = 852,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun HomeScreenDarkPreview() {
    OStatusTheme(darkTheme = true) {
        HomeScreen(
            state = HomeScreenState(
                rulesCount = 256,
                rulesUpdatedAt = 1742861400000L,
                syncStage = RuleSyncStage.MirroringRemote,
            ),
            onSyncRules = {},
            onRestartSystemUi = {},
        )
    }
}

@Preview(
    name = "Home Screen Empty",
    showBackground = true,
    widthDp = 393,
    heightDp = 852,
)
@Composable
private fun HomeScreenEmptyPreview() {
    OStatusTheme(darkTheme = false) {
        HomeScreen(
            state = HomeScreenState(
                rulesCount = 0,
                rulesUpdatedAt = 0L,
                syncStage = RuleSyncStage.Idle,
            ),
            onSyncRules = {},
            onRestartSystemUi = {},
        )
    }
}
