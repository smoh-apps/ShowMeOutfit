package com.smoh.showmeoutfit

import android.app.Application
import com.smoh.showmeoutfit.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class ShowMeOutfitApp : Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@ShowMeOutfitApp)
            modules(appModules)
        }
    }
}
