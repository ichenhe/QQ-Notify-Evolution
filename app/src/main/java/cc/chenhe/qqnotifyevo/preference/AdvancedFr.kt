package cc.chenhe.qqnotifyevo.preference

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import cc.chenhe.qqnotifyevo.MyApplication
import cc.chenhe.qqnotifyevo.R
import cc.chenhe.qqnotifyevo.core.AvatarManager
import cc.chenhe.qqnotifyevo.utils.*

class AdvancedFr : PreferenceFragmentCompat() {

    private lateinit var deleteLog: Preference

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_advanced, rootKey)

        findPreference<ListPreference>("avatar_cache_period")!!.summaryProvider = AvatarCachePeriodSummaryProvider()
        findPreference<SwitchPreferenceCompat>("log")!!.setOnPreferenceChangeListener { pref, new ->
            if (new as Boolean) {
                AlertDialog.Builder(requireContext())
                        .setTitle(R.string.tip)
                        .setMessage(R.string.pref_log_dialog_message)
                        .setCancelable(false)
                        .setPositiveButton(R.string.confirm) { _, _ ->
                            (pref as SwitchPreferenceCompat).isChecked = true
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                false
            } else {
                true
            }
        }
        deleteLog = findPreference("delete_log")!!
        refreshLogSize()
    }

    override fun onPreferenceTreeClick(preference: Preference?): Boolean {
        when (preference?.key) {
            "delete_avatar_cache" -> {
                AvatarManager.get(getAvatarDiskCacheDir(requireContext()), getAvatarCachePeriod(requireContext()))
                        .clearCache()
                Toast.makeText(requireContext(), R.string.done, Toast.LENGTH_SHORT).show()
            }
            "delete_nevo_channel" -> {
                requireContext().sendBroadcast(Intent(ACTION_DELETE_NEVO_CHANNEL))
                Toast.makeText(requireContext(), R.string.requested, Toast.LENGTH_SHORT).show()
                return true
            }
            "delete_log" -> {
                AlertDialog.Builder(requireContext())
                        .setTitle(R.string.tip)
                        .setMessage(R.string.pref_delete_log_dialog_message)
                        .setPositiveButton(R.string.confirm) { _, _ ->
                            (requireContext().applicationContext as MyApplication).deleteLog()
                            Toast.makeText(requireContext(), R.string.done, Toast.LENGTH_SHORT).show()
                            refreshLogSize()
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
            }
        }
        return super.onPreferenceTreeClick(preference)
    }

    private fun refreshLogSize() {
        val files = getLogDir(requireContext()).listFiles { f -> f.isFile }
        val size = files?.sumByDouble { f -> f.length().toDouble() } ?: 0.0
        deleteLog.summary = getString(R.string.pref_delete_log_summary,
                files?.size ?: 0, describeFileSize(size.toLong()))
    }

    private inner class AvatarCachePeriodSummaryProvider : Preference.SummaryProvider<ListPreference> {
        override fun provideSummary(preference: ListPreference): CharSequence {
            val period: Long = preference.value.toLong() / 1000
            val day = (period / (24 * 3600)).toInt()
            val hour = ((period / 3600) % 24).toInt()
            val min = (period / 60 % 60).toInt()
            val sec = (period % 60).toInt()

            val builder = StringBuilder(30)
            if (day > 0) {
                builder.append(day)
                builder.append(getString(R.string.day))
            }
            if (hour > 0) {
                builder.append(hour)
                builder.append(getString(R.string.hour))
            }
            if (min > 0) {
                builder.append(min)
                builder.append(getString(R.string.minute))
            }
            if (sec > 0) {
                builder.append(sec)
                builder.append(getString(R.string.second))
            }
            return builder.toString()
        }
    }
}