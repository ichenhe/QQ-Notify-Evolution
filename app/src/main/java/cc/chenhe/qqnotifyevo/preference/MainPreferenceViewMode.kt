package cc.chenhe.qqnotifyevo.preference

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import cc.chenhe.qqnotifyevo.utils.fetchMode

class MainPreferenceViewMode(application: Application) : AndroidViewModel(application) {

    val mode = fetchMode(application)
}