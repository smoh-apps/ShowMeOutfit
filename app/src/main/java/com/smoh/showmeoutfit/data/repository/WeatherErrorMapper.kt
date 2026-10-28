package com.smoh.showmeoutfit.data.repository

import com.smoh.showmeoutfit.domain.model.DomainError
import io.ktor.client.plugins.ResponseException
import io.ktor.serialization.ContentConvertException
import java.io.IOException
import kotlinx.serialization.SerializationException

/**
 * Translates data-layer failures into [DomainError]s.
 *
 * `CancellationException` is intentionally not handled here: callers rethrow it before this
 * mapper is reached, so a cancelled coroutine is never reported as a weather failure.
 */
internal fun Throwable.toDomainError(): DomainError = when (this) {
    is DomainError -> this
    is ResponseException -> DomainError.ApiFailure(this)
    is ContentConvertException -> DomainError.MalformedData(this)
    is SerializationException -> DomainError.MalformedData(this)
    is IOException -> DomainError.Offline(this)
    else -> DomainError.Unknown(this)
}
