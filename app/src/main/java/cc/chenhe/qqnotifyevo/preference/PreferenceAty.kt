package cc.chenhe.qqnotifyevo.preference

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import cc.chenhe.qqnotifyevo.R
import cc.chenhe.qqnotifyevo.StaticReceiver
import cc.chenhe.qqnotifyevo.service.UpgradeService
import cc.chenhe.qqnotifyevo.utils.*

class PreferenceAty : AppCompatActivity() {

    companion object {
        /**
         * 由 Nevo 模式下检测到合并消息所发出使用提示的通知跳转过来。
         *
         * 值为 [Boolean] 类型 = true.
         */
        const val EXTRA_NEVO_MULTI_MSG = "nevo_multi_msg"
    }

    private var mainPreferenceFr: MainPreferenceFr? = null

    private var upgradeReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.aty_preference)

        if (!UpgradeService.isRunningOrPrepared()) {
            initPreferenceFragment()
        } else {
            supportFragmentManager.beginTransaction()
                .replace(R.id.frameLayout, UpgradingFr())
                .commit()
            upgradeReceiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, i: Intent?) {
                    if (i?.action == ACTION_APPLICATION_UPGRADE_COMPLETE) {
                        LocalBroadcastManager.getInstance(this@PreferenceAty)
                            .unregisterReceiver(this)
                        upgradeReceiver = null
                        initPreferenceFragment()
                    }
                }
            }
            LocalBroadcastManager.getInstance(this).registerReceiver(
                upgradeReceiver!!,
                IntentFilter(ACTION_APPLICATION_UPGRADE_COMPLETE)
            )
            if (!UpgradeService.isRunningOrPrepared()) {
                // 避免极端情况下在注册监听器之前更新完成
                initPreferenceFragment()
            }
        }

        showNevoMultiMsgDialogIfNeeded()
    }

    override fun onDestroy() {
        super.onDestroy()
        upgradeReceiver?.also { receiver ->
            LocalBroadcastManager.getInstance(this@PreferenceAty).unregisterReceiver(receiver)
        }
    }

    private fun initPreferenceFragment() {
        if (mainPreferenceFr != null) {
            return
        }
        mainPreferenceFr = MainPreferenceFr().also { fr ->
            supportFragmentManager.beginTransaction()
                .replace(R.id.frameLayout, fr)
                .commit()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        showNevoMultiMsgDialogIfNeeded()
    }

    private fun showNevoMultiMsgDialogIfNeeded() {
        if (intent?.extras?.getBoolean(EXTRA_NEVO_MULTI_MSG, false) == true) {
            NotificationManagerCompat.from(this).cancel(NOTIFY_ID_MULTI_MSG)
            AlertDialog.Builder(this)
                .setTitle(R.string.tip)
                .setMessage(R.string.multi_msg_dialog)
                .setNeutralButton(R.string.multi_msg_dialog_neutral, null)
                .setPositiveButton(R.string.multi_msg_dialog_positive) { _, _ ->
                    mainPreferenceFr?.setMode(MODE_LEGACY)
                }
                .setNegativeButton(R.string.dont_show) { _, _ ->
                    sendBroadcast(Intent(this, StaticReceiver::class.java).also {
                        it.action = ACTION_MULTI_MSG_DONT_SHOW
                    })
                }
                .show()
        }
    }

    override fun onBackPressed() {
        if (getShowInRecent(this)) {
            super.onBackPressed()
        } else {
            finishAndRemoveTask()
        }
    }

}
