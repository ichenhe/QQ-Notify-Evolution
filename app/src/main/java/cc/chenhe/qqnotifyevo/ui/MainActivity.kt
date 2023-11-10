package cc.chenhe.qqnotifyevo.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import cc.chenhe.qqnotifyevo.R
import cc.chenhe.qqnotifyevo.StaticReceiver
import cc.chenhe.qqnotifyevo.service.UpgradeService
import cc.chenhe.qqnotifyevo.ui.advanced.AdvancedOptionsScreen
import cc.chenhe.qqnotifyevo.ui.main.MainPreferenceScreen
import cc.chenhe.qqnotifyevo.ui.permission.PermissionScreen
import cc.chenhe.qqnotifyevo.ui.theme.AppTheme
import cc.chenhe.qqnotifyevo.utils.ACTION_APPLICATION_UPGRADE_COMPLETE
import cc.chenhe.qqnotifyevo.utils.ACTION_MULTI_MSG_DONT_SHOW

class MainActivity : ComponentActivity() {
    companion object {
        /**
         * 由 Nevo 模式下检测到合并消息所发出使用提示的通知跳转过来。
         *
         * 值为 [Boolean] 类型 = true.
         */
        const val EXTRA_NEVO_MULTI_MSG = "nevo_multi_msg"

        private const val ENTER_TRANSACTION = 200
        private const val EXIT_TRANSACTION = 200
    }

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val ctx = LocalContext.current
            var upgrading by remember { mutableStateOf(UpgradeService.isRunningOrPrepared()) }
            DisposableEffect(key1 = Unit) {
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context, i: Intent?) {
                        if (i?.action == ACTION_APPLICATION_UPGRADE_COMPLETE) {
                            upgrading = false
                        }
                    }
                }
                LocalBroadcastManager.getInstance(ctx)
                    .registerReceiver(receiver, IntentFilter(ACTION_APPLICATION_UPGRADE_COMPLETE))
                // 避免极端情况下在注册监听器之前更新完成
                upgrading = UpgradeService.isRunningOrPrepared()
                onDispose {
                    LocalBroadcastManager.getInstance(ctx).unregisterReceiver(receiver)
                }
            }

            AppTheme {
                if (upgrading) {
                    MigratingData()
                } else {
                    Frame(viewModel)
                }
            }
        }

        showNevoMultiMsgDialogIfNeeded(intent)
    }

    override fun onNewIntent(newIntent: Intent?) {
        super.onNewIntent(newIntent)
        showNevoMultiMsgDialogIfNeeded(newIntent)
    }

    private fun showNevoMultiMsgDialogIfNeeded(intent: Intent?) {
        if (intent?.extras?.getBoolean(EXTRA_NEVO_MULTI_MSG, false) == true) {
            viewModel.sendIntent(MainViewIntent.ShowMultiMessageWarning(true))
        }
    }

    @Composable
    private fun Frame(modelView: MainViewModel) {
        val navController = rememberNavController()
        val uiState by modelView.uiState.collectAsStateWithLifecycle()
        NavHost(
            navController = navController, startDestination = "main",
            enterTransition = {
                slideInHorizontally(tween(ENTER_TRANSACTION), initialOffsetX = { it }) +
                        fadeIn(animationSpec = tween(ENTER_TRANSACTION))
            },
            exitTransition = {
                fadeOut(tween(ENTER_TRANSACTION), targetAlpha = 0.5f)
            },
            popEnterTransition = {
                fadeIn(tween(EXIT_TRANSACTION))
            },
            popExitTransition = {
                slideOutHorizontally(tween(EXIT_TRANSACTION), targetOffsetX = { it }) +
                        fadeOut(animationSpec = tween(EXIT_TRANSACTION))
            },
        ) {
            composable("main") {
                MainPreferenceScreen(
                    navigateToPermissionScreen = { navController.navigate("permission") },
                    navigateToAdvancedOptionsScreen = { navController.navigate("advancedOptions") },
                )
            }
            composable("permission") {
                PermissionScreen(navigateUp = { navController.navigateUp() })
            }
            composable("advancedOptions") {
                AdvancedOptionsScreen(navigateUp = { navController.navigateUp() })
            }
        }

        if (uiState.showMultiMessageWarning) {
            val ctx = LocalContext.current
            AlertDialog(
                title = { Text(text = stringResource(id = R.string.tip)) },
                icon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                text = { Text(text = stringResource(id = R.string.multi_msg_dialog)) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.sendIntent(MainViewIntent.ChangeToLegacyMode)
                        dismissMultiMessageDialog()
                    }) {
                        Text(text = stringResource(id = R.string.multi_msg_dialog_positive))
                    }
                },
                dismissButton = {
                    Row {
                        // 下次再说
                        TextButton(onClick = { dismissMultiMessageDialog() }) {
                            Text(text = stringResource(id = R.string.multi_msg_dialog_neutral))
                        }
                        // 不再提示
                        TextButton(onClick = {
                            Intent(this@MainActivity, StaticReceiver::class.java).also {
                                it.action = ACTION_MULTI_MSG_DONT_SHOW
                            }.also { intent -> ctx.sendBroadcast(intent) }
                            dismissMultiMessageDialog()
                        }) {
                            Text(text = stringResource(id = R.string.dont_show))
                        }
                    }
                },
                onDismissRequest = { dismissMultiMessageDialog() },
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    @Preview
    private fun MigratingData() {
        Scaffold(topBar = {
            TopAppBar(title = { Text(text = stringResource(id = R.string.activity_splash)) })
        }) { padding ->
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 64.dp)
                    )
                    Text(
                        text = stringResource(id = R.string.upgrade_message),
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
        }
    }

    private fun dismissMultiMessageDialog() {
        viewModel.sendIntent(MainViewIntent.ShowMultiMessageWarning(false))
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (viewModel.showInRecent) {
            @Suppress("DEPRECATION") // should call super
            super.onBackPressed()
        } else {
            finishAndRemoveTask()
        }
    }

}