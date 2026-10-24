package com.smoh.showmeoutfit.data.repository

import com.smoh.showmeoutfit.core.datastore.UserPreferencesStore
import com.smoh.showmeoutfit.domain.model.UserProfile
import com.smoh.showmeoutfit.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow

class UserRepositoryImpl(
    private val preferencesStore: UserPreferencesStore
) : UserRepository {

    override fun getUserProfile(): Flow<UserProfile> {
        return preferencesStore.getUserProfile()
    }

    override suspend fun updateUserProfile(profile: UserProfile): Result<Unit> {
        return runCatching {
            preferencesStore.saveUserProfile(profile)
        }
    }
}
