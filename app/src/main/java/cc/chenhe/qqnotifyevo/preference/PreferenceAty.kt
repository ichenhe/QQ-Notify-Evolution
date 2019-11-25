package cc.chenhe.qqnotifyevo.preference

import android.os.Bundle

import androidx.appcompat.app.AppCompatActivity
import cc.chenhe.qqnotifyevo.R

class PreferenceAty : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.preference_layout)
        supportFragmentManager.beginTransaction()
                .replace(R.id.frameLayout, MainPrefFr())
                .commit()
    }

}
