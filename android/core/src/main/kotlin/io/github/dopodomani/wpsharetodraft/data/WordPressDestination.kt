package io.github.dopodomani.wpsharetodraft.data

import io.github.dopodomani.wpsharetodraft.domain.CaptureError
import io.github.dopodomani.wpsharetodraft.domain.CaptureItem
import io.github.dopodomani.wpsharetodraft.domain.Destination
import io.github.dopodomani.wpsharetodraft.domain.DraftResult
import io.github.dopodomani.wpsharetodraft.domain.Logger
import io.github.dopodomani.wpsharetodraft.domain.SettingsRepository
import io.github.dopodomani.wpsharetodraft.domain.asThrowable
import javax.inject.Inject

/**
 * The first (and, for now, only) [Destination] implementation. A thin dispatcher: it reads the
 * current [io.github.dopodomani.wpsharetodraft.domain.ConnectionMethod], logs which transport
 * is used, and delegates to whichever [WordPressPublisher] the factory returns for it -- REST
 * vs. XML-RPC logic itself lives in [RestPublisher]/[XmlRpcPublisher]. See
 * docs/phase3c-android-xmlrpc-design.md.
 */
class WordPressDestination
    @Inject
    constructor(
        private val publisherFactory: WordPressPublisherFactory,
        private val settingsRepository: SettingsRepository,
        private val logger: Logger,
    ) : Destination {
        override suspend fun send(item: CaptureItem): Result<DraftResult> {
            val settings =
                settingsRepository.get()
                    ?: return Result.failure(CaptureError.SettingsNotConfigured.asThrowable())
            val normalizedSiteUrl =
                WordPressSiteUrl.normalize(settings.siteUrl)
                    ?: return Result.failure(CaptureError.InvalidSettings.asThrowable())
            val validatedSettings = settings.copy(siteUrl = normalizedSiteUrl)

            logger.d(TAG, "Publishing via ${validatedSettings.connectionMethod}")

            return publisherFactory.create(validatedSettings.connectionMethod).publish(item, validatedSettings)
        }

        private companion object {
            const val TAG = "WordPressDestination"
        }
    }
