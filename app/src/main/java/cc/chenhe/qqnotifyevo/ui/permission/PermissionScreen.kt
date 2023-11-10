package cc.chenhe.qqnotifyevo.ui.permission

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.launch
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.CircleNotifications
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MotionPhotosPaused
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.chenhe.qqnotifyevo.R
import cc.chenhe.qqnotifyevo.ui.common.PreferenceDivider
import cc.chenhe.qqnotifyevo.ui.common.PreferenceGroup
import cc.chenhe.qqnotifyevo.ui.common.PreferenceGroupInterval
import cc.chenhe.qqnotifyevo.ui.common.PreferenceItem
import cc.chenhe.qqnotifyevo.ui.theme.AppTheme
import cc.chenhe.qqnotifyevo.utils.Mode

@Composable
fun PermissionScreen(navigateUp: () -> Unit, model: PermissionViewModel = viewModel()) {
    val uiState by model.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(key1 = lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            model.refreshPermissionState()
        }
    }
    Permission(uiState = uiState, navigateUp = navigateUp)
}

@Composable
@Preview
private fun PermissionPreview() {
    AppTheme {
        Permission(PermissionUiState(unusedAppRestrictionsEnabled = true))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Permission(uiState: PermissionUiState, navigateUp: () -> Unit = {}) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(text = stringResource(id = R.string.pref_cate_permit)) },
            navigationIcon = {
                IconButton(onClick = navigateUp) {
                    Icon(Icons.Default.NavigateBefore, contentDescription = null)
                }
            })
    }) { padding ->
        val scrollState = rememberScrollState()
        Column(
            Modifier
                .padding(padding)
                .fillMaxWidth()
                .clip(CardDefaults.shape)
                .verticalScroll(scrollState)
        ) {
            if (uiState.mode == Mode.Legacy) {
                LegacyModeGroup(uiState.notificationAccess, uiState.accessibility)
                PreferenceGroupInterval()
            }
            BatteryGroup(uiState.ignoreBatteryOptimize, uiState.unusedAppRestrictionsEnabled)
        }
    }
}

@Composable
private fun LegacyModeGroup(notificationAccess: Boolean?, accessibility: Boolean?) {
    val ctx = LocalContext.current
    PreferenceGroup(groupTitle = stringResource(id = R.string.pref_cate_legacy_permission)) {
        PreferenceItem(
            title = stringResource(id = R.string.pref_notf_permit),
            icon = Icons.Rounded.CircleNotifications,
            description = when (notificationAccess) {
                true -> stringResource(id = R.string.pref_notf_permit_allow)
                false -> stringResource(id = R.string.pref_notf_permit_deny)
                null -> ""
            },
            onClick = { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
            button = {
                if (notificationAccess == false) {
                    Icon(
                        Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            },
        )
        PreferenceDivider()
        PreferenceItem(
            title = stringResource(id = R.string.pref_aces_permit),
            icon = Icons.Rounded.AccessibilityNew,
            description = when (accessibility) {
                true -> stringResource(id = R.string.pref_aces_permit_allow)
                false -> stringResource(id = R.string.pref_aces_permit_deny)
                null -> ""
            },
            onClick = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            button = {
                if (accessibility == false) {
                    Icon(
                        Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        )
    }
}

@Composable
private fun BatteryGroup(ignoreBatteryOptimize: Boolean?, appRestrictionsEnabled: Boolean?) {
    val ctx = LocalContext.current
    PreferenceGroup(groupTitle = stringResource(id = R.string.pref_cate_background_running)) {
        val ignoreBatteryOptimezeLauncher = rememberLauncherForActivityResult(contract = object :
            ActivityResultContract<Unit, Unit>() {
            @SuppressLint("BatteryLife")
            override fun createIntent(context: Context, input: Unit): Intent {
                return Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + ctx.packageName)
                )
            }

            override fun parseResult(resultCode: Int, intent: Intent?) {}
        }, onResult = { })
        PreferenceItem(title = stringResource(id = R.string.pref_cancel_battery_optimize),
            icon = Icons.Rounded.BatterySaver,
            description = when (ignoreBatteryOptimize) {
                true -> stringResource(id = R.string.pref_battery_optimize_disable)
                false -> stringResource(id = R.string.pref_battery_optimize_enable)
                null -> ""
            },
            enabled = ignoreBatteryOptimize == false,
            onClick = { ignoreBatteryOptimezeLauncher.launch() },
            button = {
                if (ignoreBatteryOptimize == false) {
                    Icon(
                        Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        )
        PreferenceDivider()

        if (appRestrictionsEnabled != null) {
            // 应用休眠，自动移除权限
            val disableAppHibernationLauncher = rememberLauncherForActivityResult(
                contract = object : ActivityResultContract<Unit, Unit>() {
                    override fun createIntent(context: Context, input: Unit): Intent =
                        IntentCompat.createManageUnusedAppRestrictionsIntent(ctx, ctx.packageName)

                    override fun parseResult(resultCode: Int, intent: Intent?) {}
                }, onResult = {})
            var showDisableAppHibernationDialog by remember { mutableStateOf(false) }
            if (showDisableAppHibernationDialog) {
                AlertDialog(
                    icon = { Icon(Icons.Rounded.MotionPhotosPaused, contentDescription = null) },
                    title = { Text(stringResource(id = R.string.pref_disable_app_hibernation_dialog_title)) },
                    text = { Text(stringResource(id = R.string.pref_disable_app_hibernation_dialog_text)) },
                    onDismissRequest = { showDisableAppHibernationDialog = false },
                    confirmButton = {
                        TextButton(onClick = {
                            disableAppHibernationLauncher.launch()
                            showDisableAppHibernationDialog = false
                        }) {
                            Text(stringResource(id = R.string.pref_disable_app_hibernation_dialog_confirm))
                        }
                    }
                )
            }
            PreferenceItem(title = stringResource(id = R.string.pref_disable_app_hibernation),
                icon = Icons.Rounded.MotionPhotosPaused,
                description = when (appRestrictionsEnabled) {
                    true -> stringResource(id = R.string.pref_app_hibernation_enabled)
                    false -> stringResource(id = R.string.pref_app_hibernation_disabled)
                },
                enabled = appRestrictionsEnabled == true,
                onClick = { showDisableAppHibernationDialog = true },
                button = {
                    if (appRestrictionsEnabled == true) {
                        Icon(
                            Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            )
            PreferenceDivider()
        }


        var showAutoRunCheckDialog by remember { mutableStateOf(false) }
        if (showAutoRunCheckDialog) {
            AlertDialog(title = { Text(text = stringResource(id = R.string.pref_auto_start)) },
                icon = { Icon(Icons.Rounded.RestartAlt, contentDescription = null) },
                text = { Text(text = stringResource(id = R.string.pref_auto_start_message)) },
                onDismissRequest = { showAutoRunCheckDialog = false },
                confirmButton = {
                    TextButton(onClick = { showAutoRunCheckDialog = false }) {
                        Text(text = stringResource(id = R.string.confirm))
                    }
                })
        }
        PreferenceItem(title = stringResource(id = R.string.pref_auto_start),
            icon = Icons.Rounded.RestartAlt,
            description = stringResource(id = R.string.pref_auto_start_summary),
            onClick = { showAutoRunCheckDialog = true })
    }
}