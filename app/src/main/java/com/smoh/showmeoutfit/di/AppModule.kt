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
import com.smoh.showmeoutfit.data.remote.datasource.OpenMeteoDataSource
import com.smoh.showmeoutfit.data.remote.datasource.RemoteWeatherDataSource
import com.smoh.showmeoutfit.data.repository.LocationRepositoryImpl
import com.smoh.showmeoutfit.data.repository.UserRepositoryImpl
import com.smoh.showmeoutfit.data.repository.WeatherRepositoryImpl
import com.smoh.showmeoutfit.domain.engine.IOutfitRecommendationEngine
import com.smoh.showmeoutfit.domain.engine.RuleBasedPresetEngine
import com.smoh.showmeoutfit.domain.repository.LocationRepository
import com.smoh.showmeoutfit.domain.repository.UserRepository
import com.smoh.showmeoutfit.domain.repository.WeatherRepository
import com.smoh.showmeoutfit.domain.usecase.GetTodayOutfitRecommendationUseCase
import com.smoh.showmeoutfit.domain.usecase.ManageUserProfileUseCase
import com.smoh.showmeoutfit.domain.usecase.SearchLocationUseCase
import com.smoh.showmeoutfit.ui.home.HomeViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

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
            remoteDataSource = get(),
            preferencesStore = get()
        )
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
    viewModel {
        HomeViewModel(
            getTodayOutfit = get(),
            userRepository = get()
        )
    }
}

val appModules = listOf(
    networkModule,
    databaseModule,
    storageModule,
    repositoryModule,
    engineModule,
    useCaseModule,
    presentationModule
)
