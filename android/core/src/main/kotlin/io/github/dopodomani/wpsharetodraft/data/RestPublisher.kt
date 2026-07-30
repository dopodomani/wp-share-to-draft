package io.github.dopodomani.wpsharetodraft.data

import io.github.dopodomani.wpsharetodraft.domain.AppSettings
import io.github.dopodomani.wpsharetodraft.domain.CaptureError
import io.github.dopodomani.wpsharetodraft.domain.CaptureItem
import io.github.dopodomani.wpsharetodraft.domain.DraftResult
import io.github.dopodomani.wpsharetodraft.domain.asThrowable
import java.io.IOException
import javax.inject.Inject

/**
 * The REST transport -- this is today's original `WordPressDestination` logic, unchanged
 * behaviorally, moved behind [WordPressPublisher] so [WordPressDestination] can pick between
 * this and [XmlRpcPublisher]. `AuthInterceptor` (unchanged) still injects the Basic Auth header
 * per request via the shared `OkHttpClient`. See docs/phase3c-android-xmlrpc-design.md.
 */
class RestPublisher
    @Inject
    constructor(
        private val api: MaterialCaptureApi,
        private val errorMapper: MaterialCaptureErrorMapper,
    ) : WordPressPublisher {
        override suspend fun publish(
            item: CaptureItem,
            settings: AppSettings,
        ): Result<DraftResult> {
            return try {
                val url = "${settings.siteUrl}/wp-json/material-capture/v1/draft"
                val response = api.createDraft(url, DraftRequestDto.from(item))
                if (response.isSuccessful) {
                    val body = response.body() ?: return Result.failure(CaptureError.Unknown("Empty response body").asThrowable())
                    Result.success(body.toDomain())
                } else {
                    Result.failure(errorMapper.fromHttpError(response).asThrowable())
                }
            } catch (e: IOException) {
                Result.failure(errorMapper.fromException(e).asThrowable())
            }
        }
    }
