package cc.chenhe.qqnotifyevo.utils

import android.content.SharedPreferences
import androidx.lifecycle.LiveData

/**
 * An common wrapper that trans [SharedPreferences] to [LiveData].
 */
sealed class SpLiveData<T>(
        private val sp: SharedPreferences,
        private val key: String,
        private val default: T
) : LiveData<T>() {

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == this.key) {
            setNewValue(getValueFromSp(sp, key, default))
        }
    }

    override fun onActive() {
        super.onActive()
        setNewValue(getValueFromSp(sp, key, default))
        sp.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    protected fun setNewValue(newValue: T) {
        if (value != newValue) {
            value = newValue
        }
    }

    override fun onInactive() {
        sp.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
        super.onInactive()
    }

    abstract fun getValueFromSp(sp: SharedPreferences, key: String, default: T): T
}

class SpIntLiveData(sp: SharedPreferences, key: String, default: Int, init: Boolean = false)
    : SpLiveData<Int>(sp, key, default) {

    init {
        if (init) {
            setNewValue(getValueFromSp(sp, key, default))
        }
    }

    override fun getValueFromSp(sp: SharedPreferences, key: String, default: Int): Int {
        return sp.getInt(key, default)
    }
}

class SpFloatLiveData(sp: SharedPreferences, key: String, default: Float, init: Boolean = false)
    : SpLiveData<Float>(sp, key, default) {

    init {
        if (init) {
            setNewValue(getValueFromSp(sp, key, default))
        }
    }

    override fun getValueFromSp(sp: SharedPreferences, key: String, default: Float): Float {
        return sp.getFloat(key, default)
    }
}

class SpBooleanLiveData(sp: SharedPreferences, key: String, default: Boolean, init: Boolean = false)
    : SpLiveData<Boolean>(sp, key, default) {

    init {
        if (init) {
            setNewValue(getValueFromSp(sp, key, default))
        }
    }

    override fun getValueFromSp(sp: SharedPreferences, key: String, default: Boolean): Boolean {
        return sp.getBoolean(key, default)
    }
}

class SpStringLiveData(sp: SharedPreferences, key: String, default: String?, init: Boolean = false)
    : SpLiveData<String?>(sp, key, default) {

    init {
        if (init) {
            setNewValue(getValueFromSp(sp, key, default))
        }
    }

    override fun getValueFromSp(sp: SharedPreferences, key: String, default: String?): String? {
        return sp.getString(key, default)
    }
}