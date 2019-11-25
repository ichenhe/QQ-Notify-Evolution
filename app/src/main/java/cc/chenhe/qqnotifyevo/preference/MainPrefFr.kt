package cc.chenhe.qqnotifyevo.preference

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import cc.chenhe.qqnotifyevo.R
import cc.chenhe.qqnotifyevo.utils.GITHUB_URL
import cc.chenhe.qqnotifyevo.utils.getVersion

class MainPrefFr : PreferenceFragmentCompat() {

    lateinit var ctx: Context

    override fun onAttach(context: Context) {
        super.onAttach(context)
        ctx = context
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        findPreference<Preference>("permission")?.fragment = PermissionFr::class.java.name
        findPreference<Preference>("in_app_notify_setting")?.fragment =
                NotifyInAppSettingsFr::class.java.name

        findPreference<Preference>("system_notify_setting")?.isVisible =
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        findPreference<Preference>("in_app_notify_setting")?.isVisible =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.O

        findPreference<Preference>("version_code")?.summary = getVersion(ctx)
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        when (preference.key) {
            "system_notify_setting" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Intent().let {
                        it.action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                        it.putExtra(Settings.EXTRA_APP_PACKAGE, context!!.packageName)
                        startActivity(it)
                    }
                    return true
                }
            }
            "best_practice" -> {
                showDialog(R.string.best_practice, R.string.best_practice_content)
                return true
            }
            "version_code" -> {
                showInfo()
                return true
            }
            "QA" -> {
                showQA()
                return true
            }
        }
        return super.onPreferenceTreeClick(preference)
    }

    private fun showInfo() {
        AlertDialog.Builder(context)
                .setTitle(getString(R.string.about_dialog_title))
                .setMessage(getString(R.string.about_dialog_message))
                .setNeutralButton(R.string.about_dialog_github) { _, _ ->
                    openGitHub()
                }
                .setPositiveButton(R.string.about_dialog_button, null)
                .show()
    }

    private fun showQA() {
        AlertDialog.Builder(context)
                .setTitle(getString(R.string.about_qa_title))
                .setMessage(getString(R.string.about_qa_message))
                .setNeutralButton(R.string.about_dialog_github) { _, _ ->
                    openGitHub()
                }
                .setPositiveButton(R.string.about_dialog_button, null)
                .show()
    }

    private fun showDialog(@StringRes title: Int, @StringRes message: Int) {
        AlertDialog.Builder(context)
                .setTitle(getString(title))
                .setMessage(getString(message))
                .setPositiveButton(R.string.confirm, null)
                .show()
    }

    private fun openGitHub() {
        try {
            Intent().let {
                it.action = Intent.ACTION_VIEW
                it.data = Uri.parse(GITHUB_URL)
                startActivity(Intent.createChooser(it, null))
            }

        } catch (e: Exception) {
        }
    }

}
