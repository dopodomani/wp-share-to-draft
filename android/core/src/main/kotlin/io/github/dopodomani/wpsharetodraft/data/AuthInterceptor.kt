package io.github.dopodomani.wpsharetodraft.data

import io.github.dopodomani.wpsharetodraft.domain.SettingsRepository
import kotlinx.coroutines.runBlocking
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Injects the `Authorization: Basic ...` header from [SettingsRepository]. Kept as its own
 * interceptor rather than something each [Destination] implementation builds itself, so
 * auth header construction lives in exactly one place.
 */
class AuthInterceptor
    @Inject
    constructor(private val settingsRepository: SettingsRepository) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val settings = runBlocking { settingsRepository.get() }
            val request =
                if (settings == null) {
                    chain.request()
                } else {
                    val basic = Credentials.basic(settings.username, settings.applicationPassword)
                    chain.request().newBuilder().addHeader("Authorization", basic).build()
                }
            return chain.proceed(request)
        }
    }
