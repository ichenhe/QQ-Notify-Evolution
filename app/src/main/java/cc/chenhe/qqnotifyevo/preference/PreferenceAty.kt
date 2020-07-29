package cc.chenhe.qqnotifyevo.preference

import android.os.Bundle

import androidx.appcompat.app.AppCompatActivity
import cc.chenhe.qqnotifyevo.R
import cc.chenhe.qqnotifyevo.utils.getShowInRecent

class PreferenceAty : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.preference_layout)
        supportFragmentManager.beginTransaction()
                .replace(R.id.frameLayout, MainPreferenceFr())
                .commit()
    }

    override fun onBackPressed() {
        if (getShowInRecent(this)) {
            super.onBackPressed()
        } else {
            finishAndRemoveTask()
        }
    }

}
