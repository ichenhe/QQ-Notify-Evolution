package cc.chenhe.qqnotifyevo.ui.main

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EditNotifications
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.chenhe.qqnotifyevo.R
import cc.chenhe.qqnotifyevo.ui.common.ErrorCard
import cc.chenhe.qqnotifyevo.ui.common.PreferenceDivider
import cc.chenhe.qqnotifyevo.ui.common.PreferenceGroup
import cc.chenhe.qqnotifyevo.ui.common.PreferenceGroupInterval
import cc.chenhe.qqnotifyevo.ui.common.PreferenceItem
import cc.chenhe.qqnotifyevo.ui.common.SingleSelectionDialog
import cc.chenhe.qqnotifyevo.ui.common.permission.rememberNotificationPermissionState
import cc.chenhe.qqnotifyevo.ui.theme.AppTheme
import cc.chenhe.qqnotifyevo.utils.GITHUB_URL
import cc.chenhe.qqnotifyevo.utils.IconStyle
import cc.chenhe.qqnotifyevo.utils.MANUAL_URL
import cc.chenhe.qqnotifyevo.utils.Mode
import cc.chenhe.qqnotifyevo.utils.Mode.*
import cc.chenhe.qqnotifyevo.utils.getVersion
import timber.log.Timber

private const val TAG = "MainPreferenceScreen"

@Composable
fun MainPreferenceScreen(
    navigateToPermissionScreen: () -> Unit,
    navigateToAdvancedOptionsScreen: () -> Unit,
    model: MainPreferenceViewModel = viewModel(),
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
) {
    val uiState by model.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(key1 = lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            model.checkUnsupportedApp()
        }
    }
    MainPreference(
        uiState = uiState,
        onIntent = { model.sendIntent(it) },
        navigateToPermissionScreen = navigateToPermissionScreen,
        navigateToAdvancedOptionsScreen = navigateToAdvancedOptionsScreen,
    )
}

@Composable
@Preview
fun MainPreferencePreview() {
    AppTheme {
        MainPreference(MainPreferenceUiState(mode = Legacy))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainPreference(
    uiState: MainPreferenceUiState,
    onIntent: (MainPreferenceIntent) -> Unit = {},
    navigateToPermissionScreen: () -> Unit = {},
    navigateToAdvancedOptionsScreen: () -> Unit = {},
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(text = stringResource(id = R.string.nevo_name)) }) },
    ) { padding ->
        val scrollState = rememberScrollState()
        Column(
            Modifier
                .padding(padding)
                .fillMaxWidth()
                .clip(CardDefaults.shape)
                .verticalScroll(scrollState)
        ) {
            if (uiState.showNevoNotInstalledDialog) {
                AlertDialog(
                    title = { Text(text = stringResource(id = R.string.tip)) },
                    text = { Text(text = stringResource(id = R.string.main_nevo_not_install)) },
                    onDismissRequest = {
                        onIntent(MainPreferenceIntent.ShowNevoNotInstalledDialog(false))
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            onIntent(MainPreferenceIntent.ShowNevoNotInstalledDialog(false))
                        }) {
                            Text(text = stringResource(id = R.string.confirm))
                        }
                    }
                )
            }

            WarningCards(
                uiState.showUnsupportedAppWarning,
                isServiceRunning = uiState.isServiceRunning,
                mode = uiState.mode,
                onIntent = onIntent,
                navigateToPermissionScreen = navigateToPermissionScreen,
            )

            BasePreferenceGroup(uiState.mode, onIntent, navigateToPermissionScreen)
            PreferenceGroupInterval()
            NotificationPreferenceGroup(uiState.mode, uiState.iconStyle, onIntent = onIntent)
            PreferenceGroupInterval()
            InfoPreferenceGroup(navigateToAdvancedOptionsScreen)
        }
    }
}

@Composable
private fun WarningCards(
    unsupportedAppDetected: Boolean,
    isServiceRunning: Boolean,
    mode: Mode,
    onIntent: (MainPreferenceIntent) -> Unit,
    navigateToPermissionScreen: () -> Unit,
) {
    val ctx = LocalContext.current
    val space = PaddingValues(bottom = 12.dp)

    // 不支持的 QQ 版本
    AnimatedVisibility(visible = unsupportedAppDetected) {
        UnsupportedAppWarningCard(modifier = Modifier.padding(space), onIntent = onIntent)
    }

    // 通知权限
    val notificationPermission = rememberNotificationPermissionState(onAlwaysDenied = {
        openNotificationSettings(ctx)
    })
    AnimatedVisibility(visible = !notificationPermission.isGranted) {
        NotificationPermissionWarningCard(
            modifier = Modifier.padding(space),
            requestPermission = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermission.launchPermissionRequest()
                } else {
                    openNotificationSettings(ctx)
                }
            }
        )
    }

    // 服务未运行
    AnimatedVisibility(visible = !isServiceRunning) {
        when (mode) {
            Nevo -> NevoServiceWarningCard(Modifier.padding(space), onIntent)
            Legacy -> LegacyNotificationMonitorServiceWarningCard(
                Modifier.padding(space),
                navigateToPermissionScreen,
            )
        }
    }
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "failed to request notification permission")
    }
}

@Composable
@Preview
private fun UnsupportedAppWarningCard(
    modifier: Modifier = Modifier,
    onIntent: (MainPreferenceIntent) -> Unit = {},
) {
    ErrorCard(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        title = stringResource(id = R.string.unsupported_app_card_title),
        description = stringResource(id = R.string.unsupported_app_card_text),
        button = {
            TextButton(onClick = { onIntent(MainPreferenceIntent.DismissUnsupportedAppWarning) }) {
                Text(text = stringResource(id = R.string.unsupported_app_card_button))
            }
        },
        modifier = modifier,
    )
}

@Composable
@Preview
private fun NotificationPermissionWarningCard(
    modifier: Modifier = Modifier,
    requestPermission: () -> Unit = {},
) {
    ErrorCard(
        modifier = modifier,
        title = stringResource(id = R.string.permission_notification_card_title),
        description = stringResource(id = R.string.permission_notification_card_text),
        button = {
            TextButton(
                onClick = requestPermission,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer)
            ) {
                Text(text = stringResource(id = R.string.permission_notification_card_allow))
            }
        },
    )
}

@Composable
@Preview
private fun NevoServiceWarningCard(
    modifier: Modifier = Modifier,
    onIntent: (MainPreferenceIntent) -> Unit = {},
) {
    val ctx = LocalContext.current
    ErrorCard(
        title = stringResource(id = R.string.nevo_service_card_title),
        description = stringResource(id = R.string.nevo_service_card_text),
        button = {
            TextButton(
                onClick = {
                    if (!startNevoApp(ctx)) {
                        onIntent(MainPreferenceIntent.ShowNevoNotInstalledDialog(true))
                    }
                },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer)
            ) {
                Text(text = stringResource(id = R.string.nevo_service_card_button))
            }
        },
        modifier = modifier,
    )
}

@Composable
@Preview
private fun LegacyNotificationMonitorServiceWarningCard(
    modifier: Modifier = Modifier,
    navigateToPermissionScreen: () -> Unit = {},
) {
    ErrorCard(
        title = stringResource(id = R.string.monitor_service_card_title),
        description = stringResource(id = R.string.monitor_service_card_text),
        button = {
            TextButton(
                onClick = navigateToPermissionScreen,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer)
            ) {
                Text(text = stringResource(id = R.string.monitor_service_card_button))
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun ModeSelectionDialog(
    currentMode: Mode,
    onDismiss: () -> Unit,
    onConfirm: (Mode) -> Unit
) {
    val modes = values().toList()
    val titles = modes.map { stringResource(id = it.strId) }
    var selectedIndex by remember { mutableIntStateOf(modes.indexOf(currentMode)) }

    SingleSelectionDialog(
        title = stringResource(id = R.string.pref_mode),
        icon = { Icon(Icons.Rounded.Extension, contentDescription = null) },
        data = titles,
        currentSelectedIndex = selectedIndex,
        onSelected = { selectedIndex = it },
        onDismiss = onDismiss,
        onConfirm = { onConfirm(modes[selectedIndex]) },
    )
}

@Composable
private fun IconStyleSelectionDialog(
    currentStyle: IconStyle,
    onDismiss: () -> Unit,
    onConfirm: (IconStyle) -> Unit
) {
    val styles = IconStyle.values().toList()
    val titles = styles.map { stringResource(id = it.strId) }
    var selectedIndex by remember { mutableIntStateOf(styles.indexOf(currentStyle)) }

    SingleSelectionDialog(
        title = stringResource(id = R.string.pref_icon_mode),
        icon = { Icon(Icons.Rounded.Style, contentDescription = null) },
        data = titles,
        currentSelectedIndex = selectedIndex,
        onSelected = { selectedIndex = it },
        onDismiss = onDismiss,
        onConfirm = { onConfirm(styles[selectedIndex]) },
    )
}

@Composable
private fun BasePreferenceGroup(
    mode: Mode,
    onIntent: (MainPreferenceIntent) -> Unit,
    navigateToPermissionScreen: () -> Unit,
) {
    var showModeSelectionDialog by remember { mutableStateOf(false) }
    if (showModeSelectionDialog) {
        ModeSelectionDialog(
            currentMode = mode,
            onDismiss = { showModeSelectionDialog = false },
            onConfirm = { newMode ->
                onIntent(MainPreferenceIntent.SetMode(newMode))
                showModeSelectionDialog = false
            },
        )
    }
    PreferenceGroup(groupTitle = stringResource(id = R.string.pref_cate_base)) {
        PreferenceItem(
            title = stringResource(id = R.string.pref_mode),
            icon = Icons.Rounded.Extension,
            description = stringResource(id = mode.strId),
            onClick = { showModeSelectionDialog = true }
        )

        PreferenceDivider()
        PreferenceItem(
            title = stringResource(id = R.string.pref_cate_permit),
            icon = Icons.Rounded.Shield,
            onClick = navigateToPermissionScreen,
        )
    }
}

@Composable
private fun NotificationPreferenceGroup(
    mode: Mode,
    iconStyle: IconStyle,
    onIntent: (MainPreferenceIntent) -> Unit,
) {
    var showIconSelectionDialog by remember { mutableStateOf(false) }
    if (showIconSelectionDialog) {
        IconStyleSelectionDialog(
            currentStyle = iconStyle,
            onDismiss = { showIconSelectionDialog = false },
            onConfirm = {
                onIntent(MainPreferenceIntent.SetIconStyle(it))
                showIconSelectionDialog = false
            },
        )
    }
    PreferenceGroup(groupTitle = stringResource(id = R.string.pref_cate_notify)) {
        val notificationSettingDescribe = when (mode) {
            Nevo -> stringResource(id = R.string.pref_notify_nevo_summary)
            Legacy -> stringResource(id = R.string.pref_notify_system_summary)
        }
        val ctx = LocalContext.current
        PreferenceItem(
            title = stringResource(id = R.string.pref_notify),
            description = notificationSettingDescribe,
            icon = Icons.Rounded.EditNotifications,
            onClick = {
                val pkgName = when (mode) {
                    Nevo -> "com.oasisfeng.nevo"
                    Legacy -> ctx.packageName
                }
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, pkgName)
                }
                try {
                    ctx.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    if (mode == Nevo) {
                        onIntent(MainPreferenceIntent.ShowNevoNotInstalledDialog(true))
                    }
                }
            },
        )

        PreferenceDivider()
        PreferenceItem(
            title = stringResource(id = R.string.pref_icon_mode),
            icon = Icons.Rounded.Style,
            description = stringResource(id = iconStyle.strId),
            onClick = { showIconSelectionDialog = true },
        )
    }
}

@Composable
private fun InfoDialog(onDismiss: () -> Unit, openGithub: () -> Unit) {
    AlertDialog(
        icon = { Icon(Icons.Rounded.Info, contentDescription = null) },
        title = { Text(text = stringResource(id = R.string.pref_cate_about)) },
        text = { Text(text = stringResource(id = R.string.about_dialog_message)) },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = openGithub) {
                Text(text = stringResource(id = R.string.about_dialog_github))
            }
        },
    )
}

@Composable
private fun InfoPreferenceGroup(navigateToAdvancedOptionsScreen: () -> Unit) {
    PreferenceGroup(groupTitle = stringResource(id = R.string.pref_cate_about)) {
        val ctx = LocalContext.current
        PreferenceItem(
            title = stringResource(id = R.string.pref_cate_advanced),
            icon = Icons.Rounded.Tune,
            onClick = navigateToAdvancedOptionsScreen,
        )
        PreferenceDivider()
        PreferenceItem(
            title = stringResource(id = R.string.about_manual),
            icon = Icons.Rounded.MenuBook,
            description = stringResource(id = R.string.about_manual_summary),
            onClick = { openUrl(ctx, MANUAL_URL) }
        )
        PreferenceDivider()
        var showInfoDialog by remember { mutableStateOf(false) }
        if (showInfoDialog) {
            val context = LocalContext.current
            InfoDialog(
                onDismiss = { showInfoDialog = false },
                openGithub = {
                    openUrl(context, GITHUB_URL)
                    showInfoDialog = false
                })
        }
        val version = remember { getVersion(ctx) }
        PreferenceItem(
            title = stringResource(id = R.string.pref_osc),
            icon = Icons.Rounded.Info,
            description = stringResource(id = R.string.pref_version_code, version),
            onClick = { showInfoDialog = true },
        )
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        Intent().let {
            it.action = Intent.ACTION_VIEW
            it.data = Uri.parse(url)
            context.startActivity(Intent.createChooser(it, null))
        }
    } catch (e: Exception) {
        Toast.makeText(context, "找不到浏览器", Toast.LENGTH_SHORT).show()
    }
}

private fun startNevoApp(context: Context): Boolean {
    return try {
        Intent().let {
            it.action = Intent.ACTION_MAIN
            it.addCategory(Intent.CATEGORY_LAUNCHER)
            it.setPackage("com.oasisfeng.nevo")
            context.startActivity(it)
        }
        true
    } catch (e: Exception) {
        false
    }
}