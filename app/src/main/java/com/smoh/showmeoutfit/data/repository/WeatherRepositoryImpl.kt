package com.smoh.showmeoutfit.data.repository

import com.smoh.showmeoutfit.data.local.datasource.LocalWeatherDataSource
import com.smoh.showmeoutfit.data.remote.datasource.RemoteWeatherDataSource
import com.smoh.showmeoutfit.domain.model.DomainError
import com.smoh.showmeoutfit.domain.model.LocationInfo
import com.smoh.showmeoutfit.domain.model.WeatherData
import com.smoh.showmeoutfit.domain.model.WeatherOrigin
import com.smoh.showmeoutfit.domain.model.WeatherSnapshot
import com.smoh.showmeoutfit.domain.repository.WeatherRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs

/**
 * Offline-first weather repository.
 *
 * Cache validity requires the location, the forecast day in the location's timezone, and the
 * elapsed time since the fetch to all line up. A snapshot from another city or another day is
 * never substituted for today's forecast, even when it is the most recent row in the database.
 */
class WeatherRepositoryImpl(
    private val remoteDataSource: RemoteWeatherDataSource,
    private val localDataSource: LocalWeatherDataSource,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val retention: Duration = CACHE_RETENTION_DURATION
) : WeatherRepository {

    companion object {
        /** Data is considered fresh for 3 hours. */
        val CACHE_FRESHNESS_DURATION: Duration = Duration.ofHours(3)

        /** Data up to 12 hours old can be served as a fallback on network failure. */
        val CACHE_MAX_FALLBACK_DURATION: Duration = Duration.ofHours(12)

        /** Cached rows older than this are pruned after every successful fetch. */
        val CACHE_RETENTION_DURATION: Duration = Duration.ofDays(7)

        /** ~5 km tolerance; a GPS fix jitters between reads for the same place. */
        private const val LOCATION_TOLERANCE_DEGREES = 0.05
    }

    override suspend fun getTodayWeather(
        location: LocationInfo,
        forceRefresh: Boolean
    ): Result<WeatherData> {
        val now = Instant.now(clock)
        val today = todayIn(location, now)

        if (!forceRefresh) {
            val cached = readCached()
            if (cached != null) {
                val age = Duration.between(cached.fetchedAt, now)
                if (cached.isUsableFor(location, today, age, CACHE_FRESHNESS_DURATION)) {
                    return Result.success(
                        WeatherData(
                            snapshot = cached,
                            origin = WeatherOrigin.CACHE_FRESH,
                            cacheAge = age
                        )
                    )
                }
            }
        }

        return remoteDataSource.fetchForecast(location).fold(
            onSuccess = { snapshot ->
                persist(snapshot)
                pruneExpiredRows(now)
                Result.success(WeatherData(snapshot = snapshot, origin = WeatherOrigin.NETWORK))
            },
            onFailure = { failure ->
                if (failure is CancellationException) throw failure

                val cached = readCached()
                if (cached != null) {
                    val age = Duration.between(cached.fetchedAt, now)
                    if (cached.isUsableFor(location, today, age, CACHE_MAX_FALLBACK_DURATION)) {
                        return Result.success(
                            WeatherData(
                                snapshot = cached,
                                origin = WeatherOrigin.CACHE_STALE,
                                cacheAge = age
                            )
                        )
                    }
                    // A cache exists but belongs to another city, another day, or is too old.
                    return Result.failure(DomainError.CacheExpired(failure))
                }
                Result.failure(failure.toDomainError())
            }
        )
    }

    override suspend fun getCachedWeather(): WeatherData? {
        val cached = readCached() ?: return null
        val age = Duration.between(cached.fetchedAt, Instant.now(clock))
        val safeAge = if (age.isNegative) Duration.ZERO else age
        val origin = if (safeAge < CACHE_FRESHNESS_DURATION) {
            WeatherOrigin.CACHE_FRESH
        } else {
            WeatherOrigin.CACHE_STALE
        }
        return WeatherData(snapshot = cached, origin = origin, cacheAge = safeAge)
    }

    /**
     * Reads the cache, degrading to "no cache" when the database itself fails.
     *
     * A corrupt or unreadable local database must not surface as an unmapped exception: the
     * worst case is a cache miss, which still leaves the network path available.
     */
    private suspend fun readCached(): WeatherSnapshot? = try {
        localDataSource.getLatest()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        null
    }

    /** Best-effort write; a failed cache write must not fail a successful network fetch. */
    private suspend fun persist(snapshot: WeatherSnapshot) {
        try {
            localDataSource.save(snapshot)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            // Fall through with the freshly fetched snapshot in hand.
        }
    }

    private fun WeatherSnapshot.isUsableFor(
        location: LocationInfo,
        today: LocalDate,
        age: Duration,
        maxAge: Duration
    ): Boolean {
        if (age.isNegative || age >= maxAge) return false
        if (!isSameLocation(this.location, location)) return false
        return dailyForecast.date == today
    }

    private suspend fun pruneExpiredRows(now: Instant) {
        try {
            localDataSource.pruneOlderThan(now.minus(retention).toEpochMilli())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            // Retention is best-effort; a failed prune must not fail an otherwise good fetch.
        }
    }

    private fun todayIn(location: LocationInfo, now: Instant): LocalDate {
        val zone = location.timezone
            ?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            ?: clock.zone
        return now.atZone(zone).toLocalDate()
    }

    private fun isSameLocation(first: LocationInfo, second: LocationInfo): Boolean {
        val latitudeDelta = abs(first.latitude - second.latitude)
        val longitudeDelta = abs(first.longitude - second.longitude)
        return latitudeDelta < LOCATION_TOLERANCE_DEGREES &&
            longitudeDelta < LOCATION_TOLERANCE_DEGREES
    }
}
