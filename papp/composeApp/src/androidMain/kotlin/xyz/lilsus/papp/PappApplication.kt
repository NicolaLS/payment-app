package xyz.lilsus.papp

import android.app.Application
import java.lang.ref.WeakReference
import org.koin.core.context.startKoin
import xyz.lilsus.papp.di.nwcModule

class PappApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        startKoin {
            modules(nwcModule)
        }
    }

    fun registerActivity(activity: MainActivity) {
        currentActivity = WeakReference(activity)
    }

    fun unregisterActivity(activity: MainActivity) {
        currentActivity?.get()?.let {
            if (it === activity) {
                currentActivity = null
            }
        }
    }

    fun recreateTopActivity() {
        currentActivity?.get()?.let { activity ->
            activity.runOnUiThread { activity.recreate() }
        }
    }

    companion object {
        lateinit var instance: PappApplication
            private set

        private var currentActivity: WeakReference<MainActivity>? = null
    }
}
