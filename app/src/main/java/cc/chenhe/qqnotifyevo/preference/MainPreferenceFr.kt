package cc.chenhe.qqnotifyevo.preference

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.observe
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import cc.chenhe.qqnotifyevo.R
import cc.chenhe.qqnotifyevo.service.NevoDecorator
import cc.chenhe.qqnotifyevo.service.NotificationMonitorService
import cc.chenhe.qqnotifyevo.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainPreferenceFr : PreferenceFragmentCompat() {

    companion object {
        private const val CHECK_SERVICE_INTERVAL = 1000L
    }

    private lateinit var ctx: Context
    private lateinit var model: MainPreferenceViewMode

    private lateinit var notification: Preference
    private lateinit var serviceWarning: Preference

    override fun onAttach(context: Context) {
        super.onAttach(context)
        ctx = context
        model = ViewModelProvider(this).get(MainPreferenceViewMode::class.java)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        notification = findPreference("system_notify_setting")!!
        serviceWarning = findPreference("service_warning")!!

        findPreference<Preference>("permission")?.fragment = PermissionFr::class.java.name
        findPreference<Preference>("version_code")?.summary = getVersion(ctx)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch { checkServiceRunning() }

        model.mode.observe(viewLifecycleOwner) { mode ->
            notification.setTitle(if (mode == MODE_NEVO)
                R.string.pref_notify_nevo else R.string.pref_notify_system)
            notification.setSummary(if (mode == MODE_NEVO)
                R.string.pref_notify_nevo_summary else R.string.pref_notify_system_summary)
        }

        model.serviceRunning.observe(viewLifecycleOwner) { serviceRunning ->
            if (serviceRunning != false) {
                serviceWarning.isVisible = false
            } else {
                if (model.mode.value == MODE_NEVO) {
                    serviceWarning.setTitle(R.string.warning_nevo_service)
                    serviceWarning.setSummary(R.string.warning_nevo_service_summary)
                } else if (model.mode.value == MODE_LEGACY) {
                    serviceWarning.setTitle(R.string.warning_monitor_service)
                    serviceWarning.setSummary(R.string.warning_monitor_service_summary)
                }
                serviceWarning.isVisible = true
            }
        }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        when (preference.key) {
            "system_notify_setting" -> {
                val pkgName = if (model.mode.value == MODE_NEVO) "com.oasisfeng.nevo" else requireContext().packageName
                Intent().let {
                    it.action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                    it.putExtra(Settings.EXTRA_APP_PACKAGE, pkgName)
                    startActivity(it)
                }
                return true
            }
            "delete_nevo_channel" -> {
                requireContext().sendBroadcast(Intent(ACTION_DELETE_NEVO_CHANNEL))
                Toast.makeText(requireContext(), R.string.requested, Toast.LENGTH_SHORT).show()
                return true
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

    private suspend fun checkServiceRunning() {
        withContext(Dispatchers.Default) {
            if (model.mode.value == MODE_NEVO)
                model.setNevoServiceRunning(NevoDecorator.isRunning())
            else
                model.setNotificationMonitorServiceRunning(NotificationMonitorService.isRunning())
            delay(CHECK_SERVICE_INTERVAL)
            checkServiceRunning()
        }
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
