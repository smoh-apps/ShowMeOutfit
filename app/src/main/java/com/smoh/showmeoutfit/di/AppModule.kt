package com.smoh.showmeoutfit.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.smoh.showmeoutfit.core.database.WeatherDatabase
import com.smoh.showmeoutfit.core.database.dao.WeatherSnapshotDao
import com.smoh.showmeoutfit.core.datastore.UserPreferencesStore
import com.smoh.showmeoutfit.core.network.HttpClientFactory
import com.smoh.showmeoutfit.data.local.datasource.LocalWeatherDataSource
import com.smoh.showmeoutfit.data.local.datasource.RoomLocalWeatherDataSource
import com.smoh.showmeoutfit.data.location.DeviceLocationSource
import com.smoh.showmeoutfit.data.location.FusedDeviceLocationSource
import com.smoh.showmeoutfit.data.remote.datasource.OpenMeteoDataSource
import com.smoh.showmeoutfit.data.remote.datasource.RemoteWeatherDataSource
import com.smoh.showmeoutfit.data.scheduler.AlarmScheduler
import com.smoh.showmeoutfit.data.scheduler.AndroidAlarmScheduler
import com.smoh.showmeoutfit.data.scheduler.MorningOutfitRoutine
import com.smoh.showmeoutfit.data.scheduler.MorningScheduleCoordinator
import com.smoh.showmeoutfit.data.repository.LocationRepositoryImpl
import com.smoh.showmeoutfit.data.repository.UserRepositoryImpl
import com.smoh.showmeoutfit.data.repository.WeatherRepositoryImpl
import com.smoh.showmeoutfit.domain.engine.IOutfitRecommendationEngine
import com.smoh.showmeoutfit.domain.engine.RuleBasedPresetEngine
import com.smoh.showmeoutfit.domain.notification.OutfitNotifier
import com.smoh.showmeoutfit.domain.repository.LocationRepository
import com.smoh.showmeoutfit.domain.repository.UserRepository
import com.smoh.showmeoutfit.domain.repository.WeatherRepository
import com.smoh.showmeoutfit.domain.usecase.GetTodayOutfitRecommendationUseCase
import com.smoh.showmeoutfit.domain.usecase.ManageUserProfileUseCase
import com.smoh.showmeoutfit.domain.usecase.SearchLocationUseCase
import com.smoh.showmeoutfit.ui.home.HomeViewModel
import com.smoh.showmeoutfit.ui.location.LocationSearchViewModel
import com.smoh.showmeoutfit.ui.notifications.AndroidOutfitNotifier
import com.smoh.showmeoutfit.ui.onboarding.OnboardingViewModel
import com.smoh.showmeoutfit.ui.permissions.AlertsPermissionReader
import com.smoh.showmeoutfit.ui.permissions.AndroidAlertsPermissionReader
import com.smoh.showmeoutfit.ui.settings.SettingsViewModel
import com.smoh.showmeoutfit.ui.app.AppViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Dispatchers are dependencies, not globals: tests substitute a test dispatcher and the
 * production graph resolves this binding, so ViewModels keep their default parameter for direct
 * construction in unit tests.
 */
val dispatcherModule = module {
    single<CoroutineDispatcher> { Dispatchers.Default }
}

val networkModule = module {
    single<HttpClient> {
        HttpClientFactory.create(OkHttp.create())
    }
    single<RemoteWeatherDataSource> {
        OpenMeteoDataSource(httpClient = get())
    }
}

val databaseModule = module {
    single<WeatherDatabase> {
        Room.databaseBuilder(
            androidContext(),
            WeatherDatabase::class.java,
            WeatherDatabase.DATABASE_NAME
        ).fallbackToDestructiveMigration(dropAllTables = false).build()
    }
    single<WeatherSnapshotDao> {
        get<WeatherDatabase>().weatherSnapshotDao()
    }
    single<DataStore<Preferences>> {
        PreferenceDataStoreFactory.create(
            produceFile = { androidContext().preferencesDataStoreFile("user_preferences") }
        )
    }
}

val storageModule = module {
    single<LocalWeatherDataSource> {
        RoomLocalWeatherDataSource(dao = get())
    }
    single {
        UserPreferencesStore(dataStore = get())
    }
}

val repositoryModule = module {
    single<WeatherRepository> {
        WeatherRepositoryImpl(
            remoteDataSource = get(),
            localDataSource = get()
        )
    }
    single<UserRepository> {
        UserRepositoryImpl(preferencesStore = get())
    }
    single<LocationRepository> {
        LocationRepositoryImpl(
            deviceLocationSource = get(),
            remoteDataSource = get()
        )
    }
}

val locationModule = module {
    single<DeviceLocationSource> {
        FusedDeviceLocationSource(context = androidContext())
    }
}

val engineModule = module {
    single<IOutfitRecommendationEngine> {
        RuleBasedPresetEngine()
    }
}

val useCaseModule = module {
    factory {
        GetTodayOutfitRecommendationUseCase(
            weatherRepository = get(),
            userRepository = get(),
            locationRepository = get(),
            engine = get()
        )
    }
    factory {
        ManageUserProfileUseCase(userRepository = get())
    }
    factory {
        SearchLocationUseCase(locationRepository = get())
    }
}

val presentationModule = module {
    viewModelOf(::AppViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::LocationSearchViewModel)
    viewModelOf(::OnboardingViewModel)
    viewModelOf(::SettingsViewModel)
}

/**
 * Morning scheduler graph.
 *
 * `AndroidAlarmScheduler` degrades on its own when exact-alarm access is missing, and
 * `AndroidOutfitNotifier` reports refusal instead of throwing, so neither the worker nor the boot
 * receiver needs a platform branch.
 */
val schedulerModule = module {
    single<AlarmScheduler> { AndroidAlarmScheduler(context = androidContext()) }
    single<OutfitNotifier> { AndroidOutfitNotifier(context = androidContext()) }
    single<AlertsPermissionReader> { AndroidAlertsPermissionReader(context = androidContext()) }
    single { MorningScheduleCoordinator(userRepository = get(), alarmScheduler = get()) }
    single {
        MorningOutfitRoutine(
            getTodayOutfit = get(),
            userRepository = get(),
            notifier = get()
        )
    }
}

val appModules = listOf(
    dispatcherModule,
    networkModule,
    databaseModule,
    storageModule,
    locationModule,
    repositoryModule,
    engineModule,
    useCaseModule,
    schedulerModule,
    presentationModule
)
