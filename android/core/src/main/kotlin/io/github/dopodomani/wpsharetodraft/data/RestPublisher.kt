package io.github.dopodomani.wpsharetodraft.data

import io.github.dopodomani.wpsharetodraft.domain.AppSettings
import io.github.dopodomani.wpsharetodraft.domain.CaptureError
import io.github.dopodomani.wpsharetodraft.domain.CaptureItem
import io.github.dopodomani.wpsharetodraft.domain.DraftResult
import io.github.dopodomani.wpsharetodraft.domain.asThrowable
import kotlinx.serialization.SerializationException
import okhttp3.Credentials
import java.io.IOException
import java.time.format.DateTimeParseException
import javax.inject.Inject

/**
 * The REST transport -- this is today's original `WordPressDestination` logic, unchanged
 * behaviorally, moved behind [WordPressPublisher] so [WordPressDestination] can pick between
 * this and [XmlRpcPublisher]. The Basic Auth header is passed only to this REST API call; the
 * shared `OkHttpClient` has no credential-bearing interceptor. See
 * docs/phase3c-android-xmlrpc-design.md.
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
                val authorization = Credentials.basic(settings.username, settings.applicationPassword)
                val response = api.createDraft(url, authorization, DraftRequestDto.from(item))
                if (response.isSuccessful) {
                    val body = response.body() ?: return Result.failure(CaptureError.Unknown("Empty response body").asThrowable())
                    Result.success(body.toDomain())
                } else {
                    Result.failure(errorMapper.fromHttpError(response).asThrowable())
                }
            } catch (e: IOException) {
                Result.failure(errorMapper.fromException(e).asThrowable())
            } catch (e: SerializationException) {
                // A 2xx response whose body doesn't match DraftResponseDto's shape (missing
                // field, malformed JSON) -- distinct from the "empty body" case above, but the
                // same underlying condition: the request nominally succeeded per HTTP status,
                // but the response can't be trusted. Retrofit's kotlinx.serialization converter
                // throws this synchronously from the createDraft() call itself, not lazily from
                // response.body(), so it must be caught here rather than assumed away.
                Result.failure(CaptureError.Unknown("Malformed response: ${e.message}").asThrowable())
            } catch (e: DateTimeParseException) {
                // DraftResponseDto.toDomain() parses created_at as an Instant -- a well-formed
                // JSON body with an unparseable timestamp surfaces here, not as a
                // SerializationException.
                Result.failure(CaptureError.Unknown("Malformed response: ${e.message}").asThrowable())
            }
        }
    }
