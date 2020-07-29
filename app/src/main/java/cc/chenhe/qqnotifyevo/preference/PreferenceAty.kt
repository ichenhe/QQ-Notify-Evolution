package cc.chenhe.qqnotifyevo.preference

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import cc.chenhe.qqnotifyevo.R
import cc.chenhe.qqnotifyevo.StaticReceiver
import cc.chenhe.qqnotifyevo.utils.ACTION_MULTI_MSG_DONT_SHOW
import cc.chenhe.qqnotifyevo.utils.MODE_LEGACY
import cc.chenhe.qqnotifyevo.utils.NOTIFY_ID_MULTI_MSG
import cc.chenhe.qqnotifyevo.utils.getShowInRecent

class PreferenceAty : AppCompatActivity() {

    companion object {
        /**
         * 由 Nevo 模式下检测到合并消息所发出使用提示的通知跳转过来。
         *
         * 值为 [Boolean] 类型 = true.
         */
        const val EXTRA_NEVO_MULTI_MSG = "nevo_multi_msg"
    }

    private lateinit var mainPreferenceFr: MainPreferenceFr

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.preference_layout)
        mainPreferenceFr = MainPreferenceFr()
        supportFragmentManager.beginTransaction()
                .replace(R.id.frameLayout, mainPreferenceFr)
                .commit()

        showNevoMultiMsgDialogIfNeeded()
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
                        mainPreferenceFr.setMode(MODE_LEGACY)
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
