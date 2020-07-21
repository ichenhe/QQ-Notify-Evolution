package cc.chenhe.qqnotifyevo.preference

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import cc.chenhe.qqnotifyevo.utils.MODE_LEGACY
import cc.chenhe.qqnotifyevo.utils.MODE_NEVO
import cc.chenhe.qqnotifyevo.utils.fetchMode

class MainPreferenceViewMode(application: Application) : AndroidViewModel(application) {

    val mode: LiveData<Int> = fetchMode(application)

    private val nevoServiceRunning = MutableLiveData<Boolean?>(null)
    private val notificationMonitorServiceRunning = MutableLiveData<Boolean?>(null)

    private val _serviceRunning = MediatorLiveData<Boolean?>()
    val serviceRunning: LiveData<Boolean?> = _serviceRunning

    init {
        _serviceRunning.addSource(mode) { workMode ->
            if (workMode == MODE_NEVO) {
                _serviceRunning.removeSource(notificationMonitorServiceRunning)
                _serviceRunning.addSource(nevoServiceRunning) { nevoRunning ->
                    if (mode.value == MODE_NEVO && _serviceRunning.value != nevoRunning) {
                        _serviceRunning.value = nevoRunning
                    }
                }
            } else if (workMode == MODE_LEGACY) {
                _serviceRunning.removeSource(nevoServiceRunning)
                _serviceRunning.addSource(notificationMonitorServiceRunning) { monitorRunning ->
                    if (mode.value == MODE_LEGACY && _serviceRunning.value != monitorRunning) {
                        _serviceRunning.value = monitorRunning
                    }
                }
            }
        }
    }

    fun setNevoServiceRunning(running: Boolean) {
        nevoServiceRunning.postValue(running)
    }

    fun setNotificationMonitorServiceRunning(running: Boolean) {
        notificationMonitorServiceRunning.postValue(running)
    }
}