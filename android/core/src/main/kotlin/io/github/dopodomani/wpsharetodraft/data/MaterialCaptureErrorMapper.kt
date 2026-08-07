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

        fun fromXmlRpcHttpStatus(status: Int): CaptureError =
            when (status) {
                401 -> CaptureError.Unauthenticated
                403 -> CaptureError.InsufficientCapability
                429 -> CaptureError.RateLimited
                503 -> CaptureError.ServiceUnavailable
                in 500..599 -> CaptureError.ServerError("HTTP $status")
                else -> CaptureError.Unknown("HTTP $status")
            }

        /**
         * XML-RPC faults carry only a `faultCode`/`faultString`, no `code` string like REST's
         * error body -- see docs/api-spec.md's XML-RPC fault table. `faultCode` 403 covers both
         * this plugin's `insufficient_capability` and WordPress core's own
         * `wp_xmlrpc_server::login()` failure ("Incorrect username or password"), so those two
         * are told apart by inspecting `faultString`, mirroring REST's division of
         * responsibility between core auth (401) and this plugin's own checks (403).
         */
        fun fromXmlRpcFault(fault: XmlRpcResult.Fault): CaptureError {
            val message = fault.faultString
            return when (fault.faultCode) {
                400 ->
                    if (message.contains("https", ignoreCase = true)) {
                        CaptureError.HttpsRequired
                    } else {
                        CaptureError.Validation(field = "unknown", message = message)
                    }
                403 ->
                    if (message.contains("username", ignoreCase = true) || message.contains("password", ignoreCase = true)) {
                        CaptureError.Unauthenticated
                    } else {
                        CaptureError.InsufficientCapability
                    }
                409 -> CaptureError.CategoryUnavailable
                500 -> CaptureError.ServerError(message)
                else -> CaptureError.Unknown(message)
            }
        }
    }
