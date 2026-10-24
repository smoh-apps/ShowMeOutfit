package com.smoh.showmeoutfit.domain.repository

import com.smoh.showmeoutfit.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    fun getUserProfile(): Flow<UserProfile>
    suspend fun updateUserProfile(profile: UserProfile): Result<Unit>
}
