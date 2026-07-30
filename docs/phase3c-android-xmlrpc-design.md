# Phase 3c Design — Android XML-RPC Fallback

**Status: approved with amendments (revision 2), proceeding to Phase 3d.** This document is the reviewable design artifact for Phase 3c/3d. It refines [docs/tech-decisions.md #11](tech-decisions.md#11-xml-rpc-as-an-opt-in-fallback-transport) and [docs/phase2c-xmlrpc-design.md](phase2c-xmlrpc-design.md) into concrete Android classes, extending [docs/phase3-android-app-design.md](phase3-android-app-design.md) rather than replacing any of it.

**Revision 2 changes (2026-07-30 review):** renamed the strategy layer to `WordPressPublisher`/`XmlRpcPublisher`/`RestPublisher`/`WordPressPublisherFactory` (was `WordPressXmlRpcDestination`/`WordPressRestDestination`/`CompositeWordPressDestination`); **default connection method changed to `XML_RPC`** (was `REST`) — reflecting that REST-over-Basic-Auth is the one that doesn't work on this project's own production host, so a fresh install shouldn't default to the transport known to fail there; added a `Logger` port so the required "Publishing via X" log line doesn't pull `android.util.Log` into the SDK-independent `:core` module; clarified the "no new secrets" instruction's translation into this app (see [Credentials](#credentials-no-new-fields) below).

## What changes vs. the approved Phase 3 design

Nothing above the `data` layer. `Destination`, `SubmitCaptureUseCase`, `ConfirmDraftViewModel`, and `ConfirmDraftScreen` are all **unchanged**. Only:

1. `AppSettings` gains one new field (`connectionMethod`), default `XML_RPC`.
2. `SettingsScreen`/`SettingsViewModel` gain a picker for it.
3. `:core/data` gains a `WordPressPublisher` abstraction (two implementations, chosen by a factory) behind the existing single `WordPressDestination`.
4. `:core` gains a small `Logger` port, implemented on the Android side.

## `ConnectionMethod` and `AppSettings`

```kotlin
// domain — no Android/networking imports, same as the rest of AppSettings
enum class ConnectionMethod { XML_RPC, REST }

data class AppSettings(
    val siteUrl: String,
    val username: String,
    val applicationPassword: String,
    val connectionMethod: ConnectionMethod = ConnectionMethod.XML_RPC,
)
```

`XML_RPC` as the default (not `REST`) is the one deliberate deviation from this project's general "REST is preferred" framing in [ADR #11](tech-decisions.md#11-xml-rpc-as-an-opt-in-fallback-transport) — a fresh install defaults to the transport that's actually been confirmed to work end-to-end, not the one confirmed broken on this project's own reference deployment. Users on hosts where REST works are free to switch explicitly; nothing about REST support is removed or degraded.

## Credentials: no new fields

The three values already collected by Settings (`siteUrl`, `username`, `applicationPassword`) are reused unchanged for both transports — WordPress's Application Password feature is documented to work for XML-RPC exactly as it does for REST (see [docs/phase2c-xmlrpc-design.md](phase2c-xmlrpc-design.md)). No new input field, no separate XML-RPC URL (derived as `"${siteUrl}/xmlrpc.php"`, same pattern as REST's `"${siteUrl}/wp-json/material-capture/v1/draft"`).

This Android app has no GitHub Actions Secrets/Environment Variables of its own to begin with (those names — `WP_XMLRPC_URL`/`WORDPRESS_USERNAME`/`WORDPRESS_APPLICATION_PASSWORD` — belong to the separate, out-of-scope downstream pipeline mentioned in [README.md](../README.md#the-problem), not this repository). Translated into this app's own terms, "reuse existing, add nothing new" means exactly the "no new Settings field" rule above.

## Settings screen changes

```kotlin
data class Editing(
    val siteUrl: String = "",
    val username: String = "",
    val applicationPassword: String = "",
    val connectionMethod: ConnectionMethod = ConnectionMethod.XML_RPC,
    val validationError: String? = null,
) : SettingsUiState
```

UI: "接続方式" with two radio options, **XML-RPC listed first (default)**, then REST API:

```
接続方式
◉ XML-RPC
○ REST API

REST APIが利用できないWordPress環境ではXML-RPCを使用します。
```

## `Logger` port (new, small)

```kotlin
// :core/domain — plain interface, no Android import, so :core stays SDK-independent
interface Logger {
    fun d(tag: String, message: String)
}
```

`:app` provides the real implementation via Hilt:

```kotlin
class AndroidLogger @Inject constructor() : Logger {
    override fun d(tag: String, message: String) {
        android.util.Log.d(tag, message)
    }
}
```

Used only for the one required line: `logger.d("WordPressDestination", "Publishing via ${settings.connectionMethod}")`, producing exactly "Publishing via XML_RPC" / "Publishing via REST" (enum `.toString()` — `XML_RPC`/`REST`, matching the enum constant names; a display-string mapping to "XML-RPC"/"REST API" is a `presentation`-layer concern for the Settings UI labels, not this log line). Tests use a trivial fake `Logger` (records calls, no I/O), consistent with this project's "fakes over mocking DSL for small interfaces" preference.

## `:core/data` changes

### `WordPressPublisher` — the strategy interface

```kotlin
// :core/data — one level below Destination; Destination is the app-wide "where does this
// go" port (WordPress today, GitHub/Notion/Slack future); WordPressPublisher is specifically
// "how do I talk to WordPress," an implementation detail of the one WordPress Destination.
interface WordPressPublisher {
    suspend fun publish(item: CaptureItem, settings: AppSettings): Result<DraftResult>
}
```

### `RestPublisher` / `XmlRpcPublisher`

`RestPublisher` is today's existing REST logic (the class that was named `WordPressDestination` before this change), moved behind the new interface with no behavioral change — same `MaterialCaptureApi`, same `MaterialCaptureErrorMapper.fromHttpError`/`fromException`.

```kotlin
class RestPublisher
    @Inject
    constructor(
        private val api: MaterialCaptureApi,
        private val errorMapper: MaterialCaptureErrorMapper,
    ) : WordPressPublisher {
        override suspend fun publish(item: CaptureItem, settings: AppSettings): Result<DraftResult> {
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
```

(`AuthInterceptor` — unchanged — still injects the REST Basic Auth header per request via the shared `OkHttpClient`; `RestPublisher` itself doesn't touch auth headers directly, same division of responsibility as before.)

```kotlin
class XmlRpcPublisher
    @Inject
    constructor(
        private val xmlRpcApi: MaterialCaptureXmlRpcApi,
        private val errorMapper: MaterialCaptureErrorMapper,
    ) : WordPressPublisher {
        override suspend fun publish(item: CaptureItem, settings: AppSettings): Result<DraftResult> {
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
```

### `MaterialCaptureXmlRpcApi` — hand-rolled, no new library dependency

**Confirmed design choice, unchanged from revision 1:** XML-RPC has no first-class Retrofit support and this project needs exactly one method call, not a general-purpose XML-RPC client — so this is a small hand-rolled request/response codec using `javax.xml.parsers`/`org.w3c.dom` (part of the JDK/Android standard library, zero new dependency), plus the existing `OkHttpClient`.

```kotlin
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
            val request = Request.Builder().url(url)
                .post(requestXml.toRequestBody("text/xml".toMediaType()))
                .build()
            httpClient.newCall(request).execute().use { response ->
                val responseXml = response.body?.string().orEmpty()
                return parseMethodResponse(responseXml)
            }
        }

        private fun buildMethodCallXml(username: String, applicationPassword: String, item: CaptureItem): String { /* ... */ }
        private fun parseMethodResponse(xml: String): XmlRpcResult { /* ... */ }
    }

sealed interface XmlRpcResult {
    data class Success(val result: DraftResult) : XmlRpcResult
    data class Fault(val faultCode: Int, val faultString: String) : XmlRpcResult
}
```

### `MaterialCaptureErrorMapper.fromXmlRpcFault(...)`

New method alongside the existing `fromHttpError`/`fromException`, mapping `faultCode` → `CaptureError` using the exact table in [docs/api-spec.md's XML-RPC section](api-spec.md#xml-rpc-fallback-material_capturecreatedraft).

### `WordPressPublisherFactory` and `WordPressDestination`

```kotlin
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

class WordPressDestination
    @Inject
    constructor(
        private val publisherFactory: WordPressPublisherFactory,
        private val settingsRepository: SettingsRepository,
        private val logger: Logger,
    ) : Destination {
        override suspend fun send(item: CaptureItem): Result<DraftResult> {
            val settings = settingsRepository.get()
                ?: return Result.failure(CaptureError.SettingsNotConfigured.asThrowable())

            logger.d(TAG, "Publishing via ${settings.connectionMethod}")

            return publisherFactory.create(settings.connectionMethod).publish(item, settings)
        }

        private companion object {
            const val TAG = "WordPressDestination"
        }
    }
```

This is the **only** class `Destination`'s Hilt binding points to (`RepositoryModule`'s `@Binds fun bindDestination(impl: WordPressDestination): Destination` — unchanged signature from before this feature, since the class name `WordPressDestination` doesn't change, only its internals do). `RestPublisher`/`XmlRpcPublisher` are plain injectable classes, never bound to `Destination` or reachable from `presentation` directly — satisfying "UI never calls the REST/XML-RPC implementation classes directly."

## Connection test (not currently a feature)

This app does not currently have a "test connection" button or feature — Settings only has Save. If one is added in a future phase, per this round's instruction it must test **only the currently-selected `connectionMethod`**, never the other one, and never fall back automatically. Recorded here as a constraint for that future feature, not something built now.

## Test plan

All of this is `:core` (plain JVM, no Android SDK needed) except `Logger`'s real Android implementation:

- `RestPublisherTest` (MockWebServer): renamed/adapted from the existing `WordPressDestinationTest` — same coverage (auth header, 201/400/401/409/500 mapping, dropped connection).
- `XmlRpcPublisherTest` (MockWebServer): sends the correct XML-RPC request body for a given `CaptureItem`, parses success/fault responses, malformed/non-XML response maps to `CaptureError.Unknown` rather than crashing.
- `MaterialCaptureXmlRpcApiTest`: round-trips a hand-built XML-RPC methodResponse string through `parseMethodResponse` for both shapes; confirms `buildMethodCallXml` produces well-formed XML a plain `DocumentBuilder` can parse back.
- `WordPressPublisherFactoryTest`: `create(XML_RPC)` returns the injected `XmlRpcPublisher` instance, `create(REST)` returns the injected `RestPublisher` instance (reference equality against the fakes passed to the constructor — no need to exercise `publish()` here, that's each publisher's own test's job).
- `WordPressDestinationTest` (renamed/refactored from today's version): delegates to whichever `WordPressPublisher` the factory returns based on current settings, using a fake `Logger` to also assert the "Publishing via ..." line fires with the right value, without asserting anything about how that line is rendered on a real device.
- `MaterialCaptureErrorMapperTest` gains cases for `fromXmlRpcFault` mirroring the existing `fromHttpError` cases.

Deferred to manual verification (as before): confirming a real Application Password authenticates successfully via `wp_xmlrpc_server::login()` against the actual production host — this is exactly what motivated the feature, so it's the one thing to re-check by hand once implemented, not something a MockWebServer test can substitute for.

## Non-goals for Phase 3d

- No UI difference in `ConfirmDraftScreen`/error presentation beyond what `CaptureError` already drives.
- No per-request transport override — `connectionMethod` is a per-site Settings choice.
- **No automatic REST→XML-RPC fallback-on-failure** (explicit, repeated instruction) — a REST failure never silently becomes an XML-RPC attempt, and vice versa.
- No automatic connection-method detection.
- No investigation into *why* REST's 401 happens on the reference host (already diagnosed and documented in [docs/tech-decisions.md #11](tech-decisions.md#11-xml-rpc-as-an-opt-in-fallback-transport) and [docs/phase3-android-smoke-test-results.md](phase3-android-smoke-test-results.md)) — this phase's job is the fallback, not further root-causing.
- No new authentication method beyond Application Password (used identically for both transports).
