package cc.chenhe.qqnotifyevo.preference

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import cc.chenhe.qqnotifyevo.R
import cc.chenhe.qqnotifyevo.core.AvatarManager
import cc.chenhe.qqnotifyevo.utils.ACTION_DELETE_NEVO_CHANNEL
import cc.chenhe.qqnotifyevo.utils.getAvatarCachePeriod
import cc.chenhe.qqnotifyevo.utils.getAvatarDiskCacheDir

class AdvancedFr : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_advanced, rootKey)

        findPreference<ListPreference>("avatar_cache_period")?.setSummaryProvider { preference ->
            if (preference is ListPreference) {
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
                builder.toString()
            } else {
                "error"
            }
        }
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
        }
        return super.onPreferenceTreeClick(preference)
    }
}