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

    @Test
    fun `xml-rpc faultCode 400 with an https message maps to HttpsRequired`() {
        val fault = XmlRpcResult.Fault(400, "HTTPS required")

        assertEquals(CaptureError.HttpsRequired, mapper.fromXmlRpcFault(fault))
    }

    @Test
    fun `xml-rpc faultCode 400 without an https message maps to Validation`() {
        val fault = XmlRpcResult.Fault(400, "title is required")

        assertEquals(CaptureError.Validation("unknown", "title is required"), mapper.fromXmlRpcFault(fault))
    }

    @Test
    fun `xml-rpc faultCode 403 with a login-failure message maps to Unauthenticated`() {
        val fault = XmlRpcResult.Fault(403, "Incorrect username or password")

        assertEquals(CaptureError.Unauthenticated, mapper.fromXmlRpcFault(fault))
    }

    @Test
    fun `xml-rpc faultCode 403 with a plugin message maps to InsufficientCapability`() {
        val fault = XmlRpcResult.Fault(403, "insufficient_capability")

        assertEquals(CaptureError.InsufficientCapability, mapper.fromXmlRpcFault(fault))
    }

    @Test
    fun `xml-rpc faultCode 409 maps to CategoryUnavailable`() {
        val fault = XmlRpcResult.Fault(409, "category_unavailable")

        assertEquals(CaptureError.CategoryUnavailable, mapper.fromXmlRpcFault(fault))
    }

    @Test
    fun `xml-rpc faultCode 500 maps to ServerError carrying the message`() {
        val fault = XmlRpcResult.Fault(500, "db down")

        assertEquals(CaptureError.ServerError("db down"), mapper.fromXmlRpcFault(fault))
    }

    @Test
    fun `an unrecognized xml-rpc faultCode maps to Unknown`() {
        val fault = XmlRpcResult.Fault(0, "Malformed XML-RPC response")

        assertEquals(CaptureError.Unknown("Malformed XML-RPC response"), mapper.fromXmlRpcFault(fault))
    }

    private fun errorResponse(
        code: Int,
        json: String,
    ): Response<DraftResponseDto> = Response.error(code, json.toResponseBody("application/json".toMediaType()))
}
