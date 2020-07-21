package cc.chenhe.qqnotifyevo.preference

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import cc.chenhe.qqnotifyevo.R
import cc.chenhe.qqnotifyevo.utils.ACTION_DELETE_NEVO_CHANNEL

class AdvancedFr : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_advanced, rootKey)
    }

    override fun onPreferenceTreeClick(preference: Preference?): Boolean {
        when (preference?.key) {
            "delete_nevo_channel" -> {
                requireContext().sendBroadcast(Intent(ACTION_DELETE_NEVO_CHANNEL))
                Toast.makeText(requireContext(), R.string.requested, Toast.LENGTH_SHORT).show()
                return true
            }
        }
        return super.onPreferenceTreeClick(preference)
    }
}