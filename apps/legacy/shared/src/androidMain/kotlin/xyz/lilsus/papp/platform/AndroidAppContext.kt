package xyz.lilsus.papp.platform

import android.app.Application
import androidx.appcompat.app.AppCompatActivity
import java.lang.ref.WeakReference

object AndroidAppContext {
    private lateinit var currentApplication: Application
    private var currentActivity: WeakReference<AppCompatActivity>? = null

    val application: Application
        get() {
            check(::currentApplication.isInitialized) {
                "AndroidAppContext must be initialized from Application.onCreate()."
            }
            return currentApplication
        }

    val applicationOrNull: Application?
        get() = if (::currentApplication.isInitialized) currentApplication else null

    fun initialize(application: Application) {
        currentApplication = application
    }

    fun registerActivity(activity: AppCompatActivity) {
        currentActivity = WeakReference(activity)
    }

    fun unregisterActivity(activity: AppCompatActivity) {
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
}
