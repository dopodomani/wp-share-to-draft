package io.github.dopodomani.wpsharetodraft.domain

/**
 * Every way [Destination.send] can fail, matching docs/api-spec.md's error table plus a
 * handful of app-local conditions. See docs/phase3-android-app-design.md#7-error-handling.
 */
sealed interface CaptureError {
    /** 400 missing_required_field / invalid_url / invalid_shared_at. */
    data class Validation(val field: String, val message: String) : CaptureError

    /** 400 https_required. */
    data object HttpsRequired : CaptureError

    /** WordPress core's own 401 shape -- this app defines no `code` for it. */
    data object Unauthenticated : CaptureError

    /** 403 insufficient_capability. */
    data object InsufficientCapability : CaptureError

    /** 409 category_unavailable. */
    data object CategoryUnavailable : CaptureError

    /** 500 insert_failed. */
    data class ServerError(val detail: String?) : CaptureError

    /** HTTP 429 from WordPress, a proxy, or a WAF. Safe to retry after waiting. */
    data object RateLimited : CaptureError

    /** HTTP 503 from WordPress or its upstream. Safe to retry later. */
    data object ServiceUnavailable : CaptureError

    /**
     * Split out of a single bucket, since each has a different likely cause and a
     * different user-facing fix -- confirmed in Phase 3a review round 2.
     */
    sealed interface Network : CaptureError {
        /** java.net.SocketTimeoutException */
        data object Timeout : Network

        /** java.net.UnknownHostException */
        data object DnsFailure : Network

        /** javax.net.ssl.SSLException (including SSLHandshakeException) */
        data object SslFailure : Network

        /** Any other IOException (connection refused, reset, etc.) */
        data object Unreachable : Network
    }

    /** App-local: Settings was never completed. Not surfaced as an Error state -- see docs/phase3-android-app-design.md#1-screen-transition-diagram. */
    data object SettingsNotConfigured : CaptureError

    /** App-local: saved settings contain a site URL that is unsafe or cannot be parsed. */
    data object InvalidSettings : CaptureError

    /** Anything unrecognized. */
    data class Unknown(val detail: String?) : CaptureError
}

/** Wraps a [CaptureError] as a [Throwable] so it can be carried by [kotlin.Result.failure]. */
class CaptureException(val error: CaptureError) : Exception()

fun CaptureError.asThrowable(): Throwable = CaptureException(this)

/** Unwraps a [CaptureException] back into its [CaptureError], or null if this isn't one. */
fun Throwable.asCaptureErrorOrNull(): CaptureError? = (this as? CaptureException)?.error
