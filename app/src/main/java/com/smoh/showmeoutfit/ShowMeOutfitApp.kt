package com.smoh.showmeoutfit

import android.app.Application
import com.smoh.showmeoutfit.data.scheduler.MorningScheduleCoordinator
import com.smoh.showmeoutfit.di.appModules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class ShowMeOutfitApp : Application() {

    /**
     * Lives as long as the process. The coordinator collects the profile for this whole time, so
     * a Settings edit re-arms the alarm without the UI knowing anything about `AlarmManager`.
     */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        val koinApplication = startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@ShowMeOutfitApp)
            modules(appModules)
        }

        // The alarm is armed from the persisted profile at process start, which also covers an
        // app update that killed the previous process without a reboot.
        koinApplication.koin.get<MorningScheduleCoordinator>().start(applicationScope)
    }
}
