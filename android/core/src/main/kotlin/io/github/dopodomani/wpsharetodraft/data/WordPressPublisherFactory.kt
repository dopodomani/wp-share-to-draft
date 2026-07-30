package io.github.dopodomani.wpsharetodraft.data

import io.github.dopodomani.wpsharetodraft.domain.ConnectionMethod
import javax.inject.Inject

/** Selects the [WordPressPublisher] for a given [ConnectionMethod] -- never auto-switches. */
class WordPressPublisherFactory
    @Inject
    constructor(
        private val xmlRpcPublisher: XmlRpcPublisher,
        private val restPublisher: RestPublisher,
    ) {
        fun create(connectionMethod: ConnectionMethod): WordPressPublisher =
            when (connectionMethod) {
                ConnectionMethod.XML_RPC -> xmlRpcPublisher
                ConnectionMethod.REST -> restPublisher
            }
    }
