package com.smoh.showmeoutfit.data.repository

import com.smoh.showmeoutfit.core.datastore.UserPreferencesStore
import com.smoh.showmeoutfit.domain.model.DomainError
import com.smoh.showmeoutfit.domain.model.UserProfile
import com.smoh.showmeoutfit.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlin.coroutines.cancellation.CancellationException

class UserRepositoryImpl(
    private val preferencesStore: UserPreferencesStore
) : UserRepository {

    override fun getUserProfile(): Flow<UserProfile> {
        return preferencesStore.getUserProfile()
    }

    override suspend fun updateUserProfile(profile: UserProfile): Result<Unit> {
        return try {
            preferencesStore.saveUserProfile(profile)
            Result.success(Unit)
        } catch (cancellation: CancellationException) {
            // A cancelled write is not a persistence failure; the caller's scope is going away.
            throw cancellation
        } catch (error: Throwable) {
            Result.failure(DomainError.Unknown(error))
        }
    }
}
