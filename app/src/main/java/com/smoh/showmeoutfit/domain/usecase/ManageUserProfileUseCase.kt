package com.smoh.showmeoutfit.domain.usecase

import com.smoh.showmeoutfit.domain.model.UserProfile
import com.smoh.showmeoutfit.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow

class ManageUserProfileUseCase(
    private val userRepository: UserRepository
) {
    fun getUserProfile(): Flow<UserProfile> {
        return userRepository.getUserProfile()
    }

    suspend fun saveUserProfile(profile: UserProfile): Result<Unit> {
        return userRepository.updateUserProfile(profile)
    }
}
