package io.github.dopodomani.wpsharetodraft.data

import io.github.dopodomani.wpsharetodraft.domain.CaptureError
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class MaterialCaptureErrorMapperTest {
    private val mapper = MaterialCaptureErrorMapper()

    @Test
    fun `401 maps to Unauthenticated regardless of body shape`() {
        val response = errorResponse(401, """{"code":"rest_not_logged_in","message":"..."}""")

        assertEquals(CaptureError.Unauthenticated, mapper.fromHttpError(response))
    }

    @Test
    fun `400 https_required maps to HttpsRequired`() {
        val response = errorResponse(400, """{"code":"https_required","message":"HTTPS required"}""")

        assertEquals(CaptureError.HttpsRequired, mapper.fromHttpError(response))
    }

    @Test
    fun `400 missing_required_field maps to Validation carrying the field code`() {
        val response = errorResponse(400, """{"code":"missing_required_field","message":"title is required"}""")

        val error = mapper.fromHttpError(response)

        assertEquals(CaptureError.Validation("missing_required_field", "title is required"), error)
    }

    @Test
    fun `403 maps to InsufficientCapability`() {
        val response = errorResponse(403, """{"code":"insufficient_capability","message":"nope"}""")

        assertEquals(CaptureError.InsufficientCapability, mapper.fromHttpError(response))
    }

    @Test
    fun `409 maps to CategoryUnavailable`() {
        val response = errorResponse(409, """{"code":"category_unavailable","message":"gone"}""")

        assertEquals(CaptureError.CategoryUnavailable, mapper.fromHttpError(response))
    }

    @Test
    fun `500 maps to ServerError carrying the message`() {
        val response = errorResponse(500, """{"code":"insert_failed","message":"db down"}""")

        assertEquals(CaptureError.ServerError("db down"), mapper.fromHttpError(response))
    }

    @Test
    fun `SocketTimeoutException maps to Network Timeout`() {
        assertEquals(CaptureError.Network.Timeout, mapper.fromException(SocketTimeoutException()))
    }

    @Test
    fun `UnknownHostException maps to Network DnsFailure`() {
        assertEquals(CaptureError.Network.DnsFailure, mapper.fromException(UnknownHostException()))
    }

    @Test
    fun `SSLException maps to Network SslFailure`() {
        assertEquals(CaptureError.Network.SslFailure, mapper.fromException(SSLException("bad cert")))
    }

    @Test
    fun `a generic IOException maps to Network Unreachable`() {
        assertEquals(CaptureError.Network.Unreachable, mapper.fromException(IOException("connection reset")))
    }

    private fun errorResponse(
        code: Int,
        json: String,
    ): Response<DraftResponseDto> = Response.error(code, json.toResponseBody("application/json".toMediaType()))
}
