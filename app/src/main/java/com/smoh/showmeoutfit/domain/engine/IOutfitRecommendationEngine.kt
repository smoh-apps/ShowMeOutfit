package com.smoh.showmeoutfit.domain.engine

import com.smoh.showmeoutfit.domain.model.OutfitRecommendation
import com.smoh.showmeoutfit.domain.model.UserProfile
import com.smoh.showmeoutfit.domain.model.WeatherSnapshot

interface IOutfitRecommendationEngine {
    fun recommend(
        weather: WeatherSnapshot,
        profile: UserProfile = UserProfile()
    ): OutfitRecommendation
}
