package cc.chenhe.qqnotifyevo.preference

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import cc.chenhe.qqnotifyevo.R
import cc.chenhe.qqnotifyevo.utils.NotifyChannel
import cc.chenhe.qqnotifyevo.utils.getRingtone

class NotifyInAppSettingsFr : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {
    companion object {
        private const val REQUEST_RINGTONE_FRIEND = 1
        private const val REQUEST_RINGTONE_GROUP = 2
        private const val REQUEST_RINGTONE_QZONE = 3
    }

    private lateinit var ctx: Context

    override fun onAttach(context: Context) {
        super.onAttach(context)
        ctx = context
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_notify_in_app_settings, rootKey)
        refreshSummary()
    }

    override fun onResume() {
        super.onResume()
        refreshSummary()
        preferenceManager.sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        preferenceManager.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        super.onPause()
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        when (preference.key) {
            "friend_ringtone",
            "group_ringtone",
            "qzone_ringtone" -> {
                val code = when (preference.key) {
                    "friend_ringtone" -> REQUEST_RINGTONE_FRIEND
                    "group_ringtone" -> REQUEST_RINGTONE_GROUP
                    "qzone_ringtone" -> REQUEST_RINGTONE_QZONE
                    else -> -1
                }
                Intent(RingtoneManager.ACTION_RINGTONE_PICKER).let {
                    it.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                    it.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                            getRingtone(ctx, NotifyChannel.FRIEND))
                    startActivityForResult(it, code)
                }
                return true
            }
        }
        return super.onPreferenceTreeClick(preference)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, s: String) {
        refreshSummary()
    }

    private fun refreshSummary() {
        var uri = getRingtone(ctx, NotifyChannel.FRIEND)
        var summary = if (uri == null)
            getString(R.string.pref_ringtone_none)
        else
            RingtoneManager.getRingtone(ctx, uri).getTitle(ctx)
        findPreference<Preference>("friend_ringtone")?.summary = getString(R.string.pref_ringtone_summary, summary)

        uri = getRingtone(ctx, NotifyChannel.GROUP)
        summary = if (uri == null)
            getString(R.string.pref_ringtone_none)
        else
            RingtoneManager.getRingtone(ctx, uri).getTitle(ctx)
        findPreference<Preference>("group_ringtone")?.summary = summary

        uri = getRingtone(ctx, NotifyChannel.QZONE)
        summary = if (uri == null)
            getString(R.string.pref_ringtone_none)
        else
            RingtoneManager.getRingtone(ctx, uri).getTitle(ctx)
        findPreference<Preference>("qzone_ringtone")?.summary = summary
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && (requestCode == REQUEST_RINGTONE_FRIEND ||
                        requestCode == REQUEST_RINGTONE_GROUP)) {
            data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)?.let { pickedUri ->
                val key = when (requestCode) {
                    REQUEST_RINGTONE_FRIEND -> "friend_ringtone"
                    REQUEST_RINGTONE_GROUP -> "group_ringtone"
                    REQUEST_RINGTONE_QZONE -> "qzone_ringtone"
                    else -> ""
                }
                PreferenceManager.getDefaultSharedPreferences(ctx).edit()
                        .putString(key, pickedUri.toString())
                        .apply()
            }
        }
    }

}
