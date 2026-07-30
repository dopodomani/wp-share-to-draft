package io.github.dopodomani.wpsharetodraft.data

import io.github.dopodomani.wpsharetodraft.domain.CaptureItem
import io.github.dopodomani.wpsharetodraft.domain.DraftResult
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.time.Instant
import javax.inject.Inject
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Hand-rolled `material_capture.createDraft` XML-RPC call. XML-RPC has no first-class Retrofit
 * support and this project needs exactly one method call, not a general-purpose XML-RPC client
 * -- so this is a small request/response codec using `javax.xml.parsers`/`org.w3c.dom` (part of
 * the JDK/Android standard library, zero new dependency) plus the existing shared
 * [OkHttpClient]. See docs/phase3c-android-xmlrpc-design.md and docs/api-spec.md's XML-RPC
 * section for the param order and fault code table this implements.
 */
class MaterialCaptureXmlRpcApi
    @Inject
    constructor(private val httpClient: OkHttpClient) {
        suspend fun createDraft(
            url: String,
            username: String,
            applicationPassword: String,
            item: CaptureItem,
        ): XmlRpcResult {
            val requestXml = buildMethodCallXml(username, applicationPassword, item)
            val request =
                Request.Builder()
                    .url(url)
                    .post(requestXml.toRequestBody(XML_MEDIA_TYPE))
                    .build()
            httpClient.newCall(request).execute().use { response ->
                val responseXml = response.body?.string().orEmpty()
                return parseMethodResponse(responseXml)
            }
        }

        private fun buildMethodCallXml(
            username: String,
            applicationPassword: String,
            item: CaptureItem,
        ): String {
            val params =
                listOf(
                    username,
                    applicationPassword,
                    item.title,
                    item.url,
                    item.sharedText,
                    item.memo,
                    item.source,
                    item.sharedAt.toString(),
                )
            return buildString {
                append("<?xml version=\"1.0\"?><methodCall>")
                append("<methodName>$METHOD_NAME</methodName>")
                append("<params>")
                params.forEach { param -> append("<param><value>${valueXml(param)}</value></param>") }
                append("</params>")
                append("</methodCall>")
            }
        }

        private fun valueXml(value: String?): String = if (value == null) "<nil/>" else "<string>${escapeXml(value)}</string>"

        private fun escapeXml(text: String): String =
            text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")

        private fun parseMethodResponse(xml: String): XmlRpcResult {
            val document =
                runCatching {
                    val factory = DocumentBuilderFactory.newInstance()
                    factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
                }.getOrNull()
                    ?: return XmlRpcResult.Fault(0, "Malformed XML-RPC response")

            val root = document.documentElement
            val faultStruct = root.firstElementChildNamed("fault")?.firstElementChildNamed("value")?.firstElementChildNamed("struct")
            if (faultStruct != null) {
                val members = structMembers(faultStruct)
                val faultCode = members["faultCode"]?.toIntOrNull() ?: 0
                val faultString = members["faultString"] ?: "Unknown XML-RPC fault"
                return XmlRpcResult.Fault(faultCode, faultString)
            }

            val resultStruct =
                root
                    .firstElementChildNamed("params")
                    ?.firstElementChildNamed("param")
                    ?.firstElementChildNamed("value")
                    ?.firstElementChildNamed("struct")
                    ?: return XmlRpcResult.Fault(0, "Malformed XML-RPC response")

            val members = structMembers(resultStruct)
            return try {
                XmlRpcResult.Success(
                    DraftResult(
                        postId = members["post_id"]?.toLong() ?: return XmlRpcResult.Fault(0, "Missing post_id"),
                        status = members["status"].orEmpty(),
                        title = members["title"].orEmpty(),
                        editUrl = members["edit_url"],
                        previewUrl = members["preview_url"],
                        category = members["category"].orEmpty(),
                        createdAt = Instant.parse(members["created_at"]),
                    ),
                )
            } catch (e: Exception) {
                XmlRpcResult.Fault(0, "Malformed XML-RPC response: ${e.message}")
            }
        }

        private fun structMembers(struct: Element): Map<String, String?> {
            val members = mutableMapOf<String, String?>()
            struct.childElements("member").forEach { member ->
                val name = member.firstElementChildNamed("name")?.textContent ?: return@forEach
                val value = member.firstElementChildNamed("value")
                members[name] = value?.let(::valueText)
            }
            return members
        }

        /** Reads a `<value>` node's content, honoring the `<nil/>` extension and typed tags. */
        private fun valueText(value: Element): String? {
            val typed = value.childElements().firstOrNull()
            return when {
                typed == null -> value.textContent
                typed.tagName == "nil" -> null
                else -> typed.textContent
            }
        }

        private fun Element.firstElementChildNamed(name: String): Element? = childElements().firstOrNull { it.tagName == name }

        private fun Element.childElements(name: String): List<Element> = childElements().filter { it.tagName == name }

        /** Direct child elements only -- XML-RPC structs/members/values are not self-nesting here. */
        private fun Element.childElements(): List<Element> {
            val nodes = childNodes
            return (0 until nodes.length)
                .map { nodes.item(it) }
                .filterIsInstance<Element>()
        }

        private companion object {
            const val METHOD_NAME = "material_capture.createDraft"
            val XML_MEDIA_TYPE = "text/xml".toMediaType()
        }
    }

sealed interface XmlRpcResult {
    data class Success(val result: DraftResult) : XmlRpcResult

    data class Fault(val faultCode: Int, val faultString: String) : XmlRpcResult
}
