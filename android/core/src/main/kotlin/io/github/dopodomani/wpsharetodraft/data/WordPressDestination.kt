package io.github.dopodomani.wpsharetodraft.data

import io.github.dopodomani.wpsharetodraft.domain.CaptureError
import io.github.dopodomani.wpsharetodraft.domain.CaptureItem
import io.github.dopodomani.wpsharetodraft.domain.Destination
import io.github.dopodomani.wpsharetodraft.domain.DraftResult
import io.github.dopodomani.wpsharetodraft.domain.SettingsRepository
import io.github.dopodomani.wpsharetodraft.domain.asThrowable
import java.io.IOException
import javax.inject.Inject

/**
 * The first (and, for now, only) [Destination] implementation. Never reads
 * `Authorization`-header details itself -- that's [AuthInterceptor]'s job -- it only
 * supplies the full request URL and body. See
 * docs/phase3-android-app-design.md#4-repository-construction.
 */
class WordPressDestination
    @Inject
    constructor(
        private val api: MaterialCaptureApi,
        private val settingsRepository: SettingsRepository,
        private val errorMapper: MaterialCaptureErrorMapper,
    ) : Destination {
        override suspend fun send(item: CaptureItem): Result<DraftResult> {
            val settings =
                settingsRepository.get()
                    ?: return Result.failure(CaptureError.SettingsNotConfigured.asThrowable())

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
