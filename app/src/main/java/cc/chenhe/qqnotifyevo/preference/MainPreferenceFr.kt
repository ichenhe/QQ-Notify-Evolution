package cc.chenhe.qqnotifyevo.preference

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import cc.chenhe.qqnotifyevo.BuildConfig
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
        preferenceManager.setStorageDeviceProtected()
        setPreferencesFromResource(R.xml.pref_main, rootKey)
        findPreference<Preference>("donate")?.isVisible = !BuildConfig.PLAY

        notification = findPreference("system_notify_setting")!!
        serviceWarning = findPreference("service_warning")!!

        findPreference<Preference>("permission")?.fragment = PermissionFr::class.java.name
        findPreference<Preference>("advanced")?.fragment = AdvancedFr::class.java.name
        findPreference<Preference>("version_code")?.summary =
            getString(R.string.pref_version_code, getVersion(ctx))
    }

    fun setMode(@Mode mode: Int) {
        findPreference<ListPreference>("mode")?.value = mode.toString()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch { checkServiceRunning() }

        model.mode.observe(viewLifecycleOwner) { mode ->
            notification.setTitle(
                if (mode == MODE_NEVO)
                    R.string.pref_notify_nevo else R.string.pref_notify_system
            )
            notification.setSummary(
                if (mode == MODE_NEVO)
                    R.string.pref_notify_nevo_summary else R.string.pref_notify_system_summary
            )
        }

        model.serviceRunning.observe(viewLifecycleOwner) { serviceRunning ->
            if (serviceRunning != false) {
                serviceWarning.isVisible = false
            } else {
                if (model.mode.value == MODE_NEVO) {
                    serviceWarning.setTitle(R.string.warning_nevo_service)
                    serviceWarning.setSummary(R.string.warning_nevo_service_summary)
                    serviceWarning.setOnPreferenceClickListener {
                        startNevoApp()
                        true
                    }
                } else if (model.mode.value == MODE_LEGACY) {
                    serviceWarning.setTitle(R.string.warning_monitor_service)
                    serviceWarning.setSummary(R.string.warning_monitor_service_summary)
                    serviceWarning.onPreferenceChangeListener = null
                }
                serviceWarning.isVisible = true
            }
        }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        when (preference.key) {
            "system_notify_setting" -> {
                val pkgName =
                    if (model.mode.value == MODE_NEVO) "com.oasisfeng.nevo" else requireContext().packageName
                Intent().let {
                    it.action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                    it.putExtra(Settings.EXTRA_APP_PACKAGE, pkgName)
                    startActivity(it)
                }
                return true
            }
            "manual" -> {
                openUrl(MANUAL_URL)
            }
            "donate" -> {
                donate()
            }
            "version_code" -> {
                showInfo()
                return true
            }
        }
        return super.onPreferenceTreeClick(preference)
    }

    private fun startNevoApp() {
        try {
            Intent().let {
                it.action = Intent.ACTION_MAIN
                it.addCategory(Intent.CATEGORY_LAUNCHER)
                it.setPackage("com.oasisfeng.nevo")
                startActivity(it)
            }
        } catch (e: Exception) {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.tip)
                .setMessage(R.string.main_nevo_not_install)
                .setPositiveButton(R.string.confirm, null)
                .show()
        }
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

    private fun donate() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.pref_donate_message)
            .setSingleChoiceItems(R.array.pref_donate_options, -1) { _, _ ->
                startAliPay()
            }
            .show()
    }

    private fun showInfo() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.about_dialog_title))
            .setMessage(getString(R.string.about_dialog_message))
            .setNeutralButton(R.string.about_dialog_github) { _, _ ->
                openUrl(GITHUB_URL)
            }
            .setPositiveButton(R.string.confirm, null)
            .show()
    }

    private fun startAliPay() {
        try {
            val uri = Uri.parse(ALIPAY)
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        } catch (e: java.lang.Exception) {
            Toast.makeText(requireContext(), R.string.pref_donate_alipay_error, Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun openUrl(url: String) {
        try {
            Intent().let {
                it.action = Intent.ACTION_VIEW
                it.data = Uri.parse(url)
                startActivity(Intent.createChooser(it, null))
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "找不到浏览器", Toast.LENGTH_SHORT).show()
        }
    }

}
