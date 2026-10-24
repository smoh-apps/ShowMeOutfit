package com.smoh.showmeoutfit.domain.model

data class LocationInfo(
    val latitude: Double,
    val longitude: Double,
    val cityName: String,
    val country: String? = null,
    val adminRegion: String? = null,
    val timezone: String? = null
)
