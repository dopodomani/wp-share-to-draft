package io.github.dopodomani.wpsharetodraft.data

import io.github.dopodomani.wpsharetodraft.domain.CaptureError
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.net.ssl.SSLException

/**
 * The only place that inspects an HTTP status/body or a caught exception and picks a
 * [CaptureError] -- mirroring the WordPress plugin's own `RestResponseFactory`/error-table
 * pattern. See docs/phase3-android-app-design.md#7-error-handling for the full mapping
 * tables this implements.
 */
class MaterialCaptureErrorMapper
    @Inject
    constructor() {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromHttpError(response: Response<*>): CaptureError {
            val status = response.code()
            val errorDto =
                runCatching {
                    response.errorBody()?.string()?.let { json.decodeFromString<DraftErrorDto>(it) }
                }.getOrNull()

            return when {
                // A 401 here is WordPress core's own authentication error shape, not this
                // plugin's -- see docs/phase2-wordpress-plugin-design.md's division of
                // responsibility. We don't inspect its `code`, just its status.
                status == 401 -> CaptureError.Unauthenticated
                status == 400 && errorDto?.code == "https_required" -> CaptureError.HttpsRequired
                status == 400 ->
                    CaptureError.Validation(
                        field = errorDto?.code ?: "unknown",
                        message = errorDto?.message ?: "Bad request",
                    )
                status == 403 -> CaptureError.InsufficientCapability
                status == 409 -> CaptureError.CategoryUnavailable
                status == 500 -> CaptureError.ServerError(errorDto?.message)
                else -> CaptureError.Unknown(errorDto?.message ?: "HTTP $status")
            }
        }

        fun fromException(e: IOException): CaptureError =
            when (e) {
                is SocketTimeoutException -> CaptureError.Network.Timeout
                is UnknownHostException -> CaptureError.Network.DnsFailure
                is SSLException -> CaptureError.Network.SslFailure
                else -> CaptureError.Network.Unreachable
            }
    }
