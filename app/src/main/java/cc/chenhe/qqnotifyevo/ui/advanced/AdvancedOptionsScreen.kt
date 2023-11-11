package cc.chenhe.qqnotifyevo.ui.advanced

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Adb
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkRemove
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FolderDelete
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NoAccounts
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.TableRows
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.chenhe.qqnotifyevo.R
import cc.chenhe.qqnotifyevo.ui.common.PreferenceDivider
import cc.chenhe.qqnotifyevo.ui.common.PreferenceGroup
import cc.chenhe.qqnotifyevo.ui.common.PreferenceGroupInterval
import cc.chenhe.qqnotifyevo.ui.common.PreferenceItem
import cc.chenhe.qqnotifyevo.ui.common.SingleSelectionDialog
import cc.chenhe.qqnotifyevo.ui.theme.AppTheme
import cc.chenhe.qqnotifyevo.utils.AvatarCacheAge
import cc.chenhe.qqnotifyevo.utils.NOTIFY_SELF_TIPS_CHANNEL_ID
import cc.chenhe.qqnotifyevo.utils.SpecialGroupChannel
import cc.chenhe.qqnotifyevo.utils.SpecialGroupChannel.*

@Composable
fun AdvancedOptionsScreen(navigateUp: () -> Unit, model: AdvancedOptionsViewModel = viewModel()) {
    val uiState by model.uiState.collectAsStateWithLifecycle()
    AdvancedOptions(uiState = uiState, onIntent = { model.sendIntent(it) }, navigateUp = navigateUp)
}

@Composable
@Preview
private fun AdvancedOptionsPreview() {
    AppTheme {
        AdvancedOptions(AdvancedOptionsUiState(formatNickname = true, nicknameFormat = "[\$n]"))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdvancedOptions(
    uiState: AdvancedOptionsUiState,
    onIntent: (AdvancedOptionsIntent) -> Unit = {},
    navigateUp: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(text = stringResource(id = R.string.pref_cate_advanced)) },
                navigationIcon = {
                    IconButton(onClick = navigateUp) {
                        Icon(Icons.Default.NavigateBefore, contentDescription = null)
                    }
                })
        },
    ) { padding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxWidth()
                .clip(CardDefaults.shape)
                .verticalScroll(scrollState)
        ) {
            NotificationGroup(
                specialPrefix = uiState.specialPrefix,
                uiState.specialInGroupChannel,
                uiState.formatNickname,
                uiState.nicknameFormat,
                onIntent
            )
            PreferenceGroupInterval()
            OtherGroup(
                uiState.avatarCacheAge,
                uiState.deleteAvatarCacheDone,
                uiState.deleteNevoChannelDone,
                uiState.resetUsageTipDone,
                uiState.showInRecentApps,
                onIntent
            )
            PreferenceGroupInterval()
            DebugGroup(uiState.enableLog, uiState.logSize, onIntent)
        }
    }
}

@Composable
private fun NotificationGroup(
    specialPrefix: Boolean,
    specialInGroupChannel: SpecialGroupChannel,
    formatNickname: Boolean,
    nicknameFormat: String,
    onIntent: (AdvancedOptionsIntent) -> Unit,
) {
    PreferenceGroup(groupTitle = stringResource(id = R.string.pref_cate_advanced_notify)) {
        // 显示特别关心前缀
        PreferenceItem(title = stringResource(id = R.string.pref_advanced_show_special_prefix),
            icon = Icons.Rounded.Favorite,
            description = stringResource(id = R.string.pref_advanced_show_special_prefix_summary),
            button = { Switch(checked = specialPrefix, onCheckedChange = null) },
            onClick = { onIntent(AdvancedOptionsIntent.SetSpecialPrefix(!specialPrefix)) }
        )
        PreferenceDivider()

        // 特别关心群消息通知渠道
        var showSpecialGroupChannelDialog by remember { mutableStateOf(false) }
        if (showSpecialGroupChannelDialog) {
            SpecialInGroupChannelSelectionDialog(
                current = specialInGroupChannel,
                onDismiss = { showSpecialGroupChannelDialog = false },
                onConfirm = {
                    onIntent(AdvancedOptionsIntent.SetSpecialGroupChannel(it))
                    showSpecialGroupChannelDialog = false
                }
            )
        }
        PreferenceItem(
            title = stringResource(id = R.string.pref_advanced_special_group_channel),
            icon = Icons.Rounded.Bookmark,
            description = stringResource(id = specialInGroupChannel.strId),
            onClick = { showSpecialGroupChannelDialog = true }
        )
        PreferenceDivider()
        // 格式化昵称
        PreferenceItem(
            title = stringResource(id = R.string.pref_advanced_wrap_nickname),
            icon = Icons.Rounded.FormatQuote,
            button = { Switch(checked = formatNickname, onCheckedChange = null) },
            onClick = { onIntent(AdvancedOptionsIntent.SetFormatNickname(!formatNickname)) }
        )
        // 昵称格式
        var showNicknameFormatEditDialog by remember { mutableStateOf(false) }
        if (showNicknameFormatEditDialog) {
            NicknameFormatDialog(
                nicknameFormat = nicknameFormat,
                onConfirm = {
                    onIntent(AdvancedOptionsIntent.SetNicknameFormat(it))
                    showNicknameFormatEditDialog = false
                },
                onDismiss = { showNicknameFormatEditDialog = false }
            )
        }
        AnimatedVisibility(visible = formatNickname) {
            PreferenceItem(
                title = stringResource(id = R.string.pref_advanced_nickname_wrapper),
                description = nicknameFormat,
                enabled = formatNickname,
                onClick = { showNicknameFormatEditDialog = true }
            )
        }
    }
}

@Composable
private fun OtherGroup(
    avatarCacheAge: AvatarCacheAge,
    deleteAvatarCacheDone: Boolean,
    deleteNevoChannelDone: Boolean,
    resetUsageTipDone: Boolean,
    showInRecentApps: Boolean,
    onIntent: (AdvancedOptionsIntent) -> Unit
) {
    val ctx = LocalContext.current
    PreferenceGroup(groupTitle = stringResource(id = R.string.pref_cate_advanced_other)) {
        // 头像缓存有效期
        var showAvatarCacheAgeDialog by remember { mutableStateOf(false) }
        if (showAvatarCacheAgeDialog) {
            AvatarCacheAgeSelectionDialog(
                current = avatarCacheAge,
                onDismiss = { showAvatarCacheAgeDialog = false },
                onConfirm = {
                    onIntent(AdvancedOptionsIntent.SetAvatarCacheAge(it))
                    showAvatarCacheAgeDialog = false
                }
            )
        }
        PreferenceItem(
            title = stringResource(id = R.string.pref_avatar_cache_period),
            icon = Icons.Rounded.AccountCircle,
            description = stringResource(id = avatarCacheAge.strId),
            onClick = { showAvatarCacheAgeDialog = true },
        )
        PreferenceDivider()
        // 删除头像缓存
        PreferenceItem(
            title = stringResource(id = R.string.pref_delete_avatar_cache),
            icon = Icons.Rounded.NoAccounts,
            enabled = !deleteAvatarCacheDone,
            onClick = { onIntent(AdvancedOptionsIntent.DeleteAvatarCache) },
            button = {
                AnimatedVisibility(visible = deleteAvatarCacheDone) {
                    OperationDoneTip(text = stringResource(id = R.string.pref_delete_avatar_cache_done))
                }
            }
        )
        PreferenceDivider()
        // 删除 Nevo 通知渠道
        PreferenceItem(
            title = stringResource(id = R.string.pref_delete_nevo_channel),
            icon = Icons.Rounded.BookmarkRemove,
            enabled = !deleteNevoChannelDone,
            onClick = { onIntent(AdvancedOptionsIntent.DeleteNevoChannel) },
            button = {
                AnimatedVisibility(visible = deleteNevoChannelDone) {
                    OperationDoneTip(text = stringResource(id = R.string.pref_delete_nevo_channel_done))
                }
            }
        )
        PreferenceDivider()
        // 重置使用提示
        var showNotificationDisabledDialog by remember { mutableStateOf(false) }
        if (showNotificationDisabledDialog) {
            val dismiss = { showNotificationDisabledDialog = false }
            AlertDialog(
                title = { Text(text = stringResource(id = R.string.pref_reset_tips_notify_dialog_title)) },
                icon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                text = { Text(text = stringResource(id = R.string.pref_reset_tips_notify_dialog_text)) },
                onDismissRequest = dismiss,
                confirmButton = {
                    TextButton(onClick = {
                        openTipsNotificationSetting(ctx)
                        dismiss()
                    }) {
                        Text(text = stringResource(id = R.string.pref_reset_tips_notify_dialog_allow))
                    }
                },
                dismissButton = {
                    TextButton(onClick = dismiss) {
                        Text(text = stringResource(id = R.string.pref_reset_tips_notify_dialog_deny))
                    }
                },
                properties = DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false
                )
            )
        }
        PreferenceItem(
            title = stringResource(id = R.string.pref_reset_tips),
            icon = Icons.Rounded.Refresh,
            enabled = !resetUsageTipDone,
            onClick = {
                onIntent(AdvancedOptionsIntent.ResetUsageTips)
                if (NotificationManagerCompat.from(ctx)
                        .getNotificationChannel(NOTIFY_SELF_TIPS_CHANNEL_ID)?.importance ==
                    NotificationManagerCompat.IMPORTANCE_NONE
                ) {
                    showNotificationDisabledDialog = true
                }
            },
            button = {
                AnimatedVisibility(visible = resetUsageTipDone) {
                    OperationDoneTip(text = stringResource(id = R.string.pref_reset_tips_done))
                }
            }
        )
        PreferenceDivider()
        // 在最近应用列表显示
        PreferenceItem(
            title = stringResource(id = R.string.pref_show_in_recent),
            icon = Icons.Rounded.TableRows,
            description = if (showInRecentApps) {
                stringResource(id = R.string.pref_show_in_recent_on)
            } else {
                stringResource(id = R.string.pref_show_in_recent_off)
            },
            onClick = {
                onIntent(AdvancedOptionsIntent.SetShowInRecentApps(!showInRecentApps))
            },
            button = { Switch(checked = showInRecentApps, onCheckedChange = null) },
        )
    }
}

@Composable
private fun DebugGroup(
    enableLog: Boolean,
    logSize: String,
    onIntent: (AdvancedOptionsIntent) -> Unit,
) {
    PreferenceGroup(groupTitle = stringResource(id = R.string.pref_cat_debug)) {
        // 开启日志
        var showLogWarningDialog by remember { mutableStateOf(false) }
        if (showLogWarningDialog) {
            AlertDialog(
                title = { Text(text = stringResource(id = R.string.tip)) },
                icon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                text = { Text(text = stringResource(id = R.string.pref_log_dialog_message)) },
                onDismissRequest = { showLogWarningDialog = false },
                confirmButton = {
                    TextButton(onClick = {
                        onIntent(AdvancedOptionsIntent.SetEnableLog(true))
                        showLogWarningDialog = false
                    }) {
                        Text(text = stringResource(id = R.string.pref_log_dialog_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLogWarningDialog = false }) {
                        Text(text = stringResource(id = R.string.pref_log_dialog_cancel))
                    }
                },
            )
        }
        PreferenceItem(
            title = stringResource(id = R.string.pref_log),
            icon = Icons.Rounded.Adb,
            onClick = {
                if (enableLog) {
                    onIntent(AdvancedOptionsIntent.SetEnableLog(false))
                } else {
                    showLogWarningDialog = true
                }
            },
            button = { Switch(checked = enableLog, onCheckedChange = null) },
        )
        PreferenceDivider()
        // 删除日志
        var showDeleteLogConfirm by remember { mutableStateOf(false) }
        if (showDeleteLogConfirm) {
            AlertDialog(
                title = { Text(text = stringResource(id = R.string.pref_delete_log_dialog_message)) },
                icon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                onDismissRequest = { showDeleteLogConfirm = false },
                confirmButton = {
                    TextButton(onClick = {
                        onIntent(AdvancedOptionsIntent.DeleteLog)
                        showDeleteLogConfirm = false
                    }) {
                        Text(text = stringResource(id = R.string.pref_delete_log_dialog_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteLogConfirm = false }) {
                        Text(text = stringResource(id = R.string.cancel))
                    }
                },
            )
        }
        PreferenceItem(
            title = stringResource(id = R.string.pref_delete_log),
            icon = Icons.Rounded.FolderDelete,
            onClick = { showDeleteLogConfirm = true },
            description = logSize,
        )
    }
}

@Composable
private fun OperationDoneTip(text: String) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
        Row {
            Text(text = text)
            Icon(
                Icons.Rounded.CheckCircleOutline,
                contentDescription = null,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
private fun SpecialInGroupChannelSelectionDialog(
    current: SpecialGroupChannel,
    onDismiss: () -> Unit,
    onConfirm: (SpecialGroupChannel) -> Unit,
) {
    val options = SpecialGroupChannel.values()
    val titles = options.map { stringResource(id = it.strId) }
    var currentIndex by remember { mutableIntStateOf(options.indexOf(current)) }
    SingleSelectionDialog(
        title = stringResource(id = R.string.pref_advanced_special_group_channel),
        data = titles,
        currentSelectedIndex = currentIndex,
        onSelected = { currentIndex = it },
        onConfirm = { onConfirm(options[currentIndex]) },
        onDismiss = onDismiss
    )
}

@Composable
@Preview
private fun NicknameFormatDialogPreview() {
    NicknameFormatDialog(nicknameFormat = "[\$n]", onConfirm = {}, onDismiss = {})
}

@Composable
private fun NicknameFormatDialog(
    nicknameFormat: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var current by remember { mutableStateOf(nicknameFormat) }
    val isError: Boolean = remember(current) { !current.contains("\$n") }
    val supportingText: (@Composable () -> Unit)? = remember(isError) {
        if (isError) {
            @Composable {
                Text(text = stringResource(id = R.string.pref_advanced_nickname_wrapper_invalid_message))
            }
        } else {
            null
        }
    }
    AlertDialog(
        title = { Text(text = stringResource(id = R.string.pref_advanced_nickname_wrapper)) },
        onDismissRequest = onDismiss,
        text = {
            Column {
                Text(text = stringResource(id = R.string.pref_advanced_nickname_wrapper_message))
                TextField(
                    value = current,
                    onValueChange = { current = it },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Rounded.FormatQuote, contentDescription = null)
                    },
                    isError = isError,
                    supportingText = supportingText,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(current) }, enabled = !isError) {
                Text(text = stringResource(id = R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.cancel))
            }
        }
    )
}

@Composable
private fun AvatarCacheAgeSelectionDialog(
    current: AvatarCacheAge,
    onDismiss: () -> Unit,
    onConfirm: (AvatarCacheAge) -> Unit,
) {
    val options = AvatarCacheAge.values()
    val titles = options.map { stringResource(id = it.strId) }
    var currentIndex by remember { mutableIntStateOf(options.indexOf(current)) }

    SingleSelectionDialog(
        title = stringResource(id = R.string.pref_avatar_cache_period),
        data = titles,
        currentSelectedIndex = currentIndex,
        onSelected = { currentIndex = it },
        onDismiss = onDismiss,
        onConfirm = { onConfirm(options[currentIndex]) })
}

private fun openTipsNotificationSetting(ctx: Context) {
    val intent = Intent().apply {
        action = Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS
        putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        putExtra(Settings.EXTRA_CHANNEL_ID, NOTIFY_SELF_TIPS_CHANNEL_ID)
    }
    try {
        ctx.startActivity(intent)
    } catch (e: Exception) {
        val b = Intent().apply {
            action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
            putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        }
        ctx.startActivity(b)
    }
}