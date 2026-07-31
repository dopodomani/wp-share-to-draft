package io.github.dopodomani.wpsharetodraft.data

import io.github.dopodomani.wpsharetodraft.domain.AppSettings
import io.github.dopodomani.wpsharetodraft.domain.CaptureItem
import io.github.dopodomani.wpsharetodraft.domain.DraftResult
import io.github.dopodomani.wpsharetodraft.domain.asThrowable
import java.io.IOException
import javax.inject.Inject

/**
 * The XML-RPC transport, for hosting environments that don't forward the `Authorization`
 * header to PHP (see docs/tech-decisions.md#11-xml-rpc-as-an-opt-in-fallback-transport). Uses
 * the same Application Password credential as [RestPublisher] -- WordPress core supports it for
 * XML-RPC natively, so no new credential type is needed.
 */
class XmlRpcPublisher
    @Inject
    constructor(
        private val xmlRpcApi: MaterialCaptureXmlRpcApi,
        private val errorMapper: MaterialCaptureErrorMapper,
    ) : WordPressPublisher {
        override suspend fun publish(
            item: CaptureItem,
            settings: AppSettings,
        ): Result<DraftResult> {
            return try {
                val url = "${settings.siteUrl}/xmlrpc.php"
                when (val response = xmlRpcApi.createDraft(url, settings.username, settings.applicationPassword, item)) {
                    is XmlRpcResult.Success -> Result.success(response.result)
                    is XmlRpcResult.Fault -> Result.failure(errorMapper.fromXmlRpcFault(response).asThrowable())
                }
            } catch (e: IOException) {
                Result.failure(errorMapper.fromException(e).asThrowable())
            }
        }
    }
