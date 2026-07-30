package io.github.dopodomani.wpsharetodraft.data

import io.github.dopodomani.wpsharetodraft.domain.ConnectionMethod
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import retrofit2.Retrofit

class WordPressPublisherFactoryTest {
    private val errorMapper = MaterialCaptureErrorMapper()
    private val api = Retrofit.Builder().baseUrl("https://example.com/").build().create(MaterialCaptureApi::class.java)
    private val xmlRpcPublisher = XmlRpcPublisher(MaterialCaptureXmlRpcApi(OkHttpClient()), errorMapper)
    private val restPublisher = RestPublisher(api, errorMapper)
    private val factory = WordPressPublisherFactory(xmlRpcPublisher, restPublisher)

    @Test
    fun `create XML_RPC returns the injected XmlRpcPublisher instance`() {
        assertSame(xmlRpcPublisher, factory.create(ConnectionMethod.XML_RPC))
    }

    @Test
    fun `create REST returns the injected RestPublisher instance`() {
        assertSame(restPublisher, factory.create(ConnectionMethod.REST))
    }
}
