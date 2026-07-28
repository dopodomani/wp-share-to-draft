package io.github.dopodomani.wpsharetodraft.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * Uses `@Url` rather than a fixed `@POST("wp-json/material-capture/v1/draft")` on a fixed
 * `baseUrl`, since the site URL is only known at runtime (entered in Settings). See
 * docs/phase3-android-app-design.md#5-retrofit-api.
 */
interface MaterialCaptureApi {
    @POST
    suspend fun createDraft(
        @Url url: String,
        @Body request: DraftRequestDto,
    ): Response<DraftResponseDto>
}
