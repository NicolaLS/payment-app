package xyz.lilsus.papp

import android.app.Application
import org.koin.core.context.startKoin
import xyz.lilsus.papp.di.nwcModule

class PappApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            modules(nwcModule)
        }
    }
}
