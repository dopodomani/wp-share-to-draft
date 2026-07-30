# Phase 3c Design — Android XML-RPC Fallback

**Status: awaiting review.** This document is the reviewable design artifact for Phase 3c. No code exists yet — per [ROADMAP.md](../ROADMAP.md#process), implementation (Phase 3d) starts only after this doc is explicitly approved. It refines [docs/tech-decisions.md #11](tech-decisions.md#11-xml-rpc-as-an-opt-in-fallback-transport) and [docs/phase2c-xmlrpc-design.md](phase2c-xmlrpc-design.md) into concrete Android classes, extending [docs/phase3-android-app-design.md](phase3-android-app-design.md) rather than replacing any of it.

## What changes vs. the approved Phase 3 design

Nothing above the `data` layer. `Destination`, `SubmitCaptureUseCase`, `ConfirmDraftViewModel`, and `ConfirmDraftScreen` are all **unchanged** — this is deliberately structured so the already-approved, already-implemented layers don't need to move. Only:

1. `AppSettings` gains one new field (`connectionMethod`).
2. `SettingsScreen`/`SettingsViewModel` gain a picker for it.
3. `:core/data` gains a second `Destination` implementation and a small selector.

## `ConnectionMethod` and `AppSettings`

```kotlin
// domain — no Android/networking imports, same as the rest of AppSettings
enum class ConnectionMethod { REST, XML_RPC }

data class AppSettings(
    val siteUrl: String,
    val username: String,
    val applicationPassword: String,
    val connectionMethod: ConnectionMethod = ConnectionMethod.REST,
)
```

Defaulting to `REST` means every existing test/usage that constructs `AppSettings` without the new parameter keeps compiling and keeps today's behavior — no ripple changes required elsewhere just to add the field.

## Settings screen changes

```kotlin
data class Editing(
    val siteUrl: String = "",
    val username: String = "",
    val applicationPassword: String = "",
    val connectionMethod: ConnectionMethod = ConnectionMethod.REST,
    val validationError: String? = null,
) : SettingsUiState
```

UI: a simple two-option choice (radio buttons or a segmented control) labeled e.g. "接続方式" with "REST（推奨）" / "XML-RPC（RESTが使えない場合）", plus a short explanatory line ("多くのホスティングではRESTで問題ありません。RESTが401エラーになる一部の共有ホスティングではXML-RPCをお試しください。"). Defaults to REST for a new install; changing it doesn't require re-entering the Application Password, since XML-RPC uses the exact same credential (see [docs/phase2c-xmlrpc-design.md](phase2c-xmlrpc-design.md)).

## `:core/data` changes

### Rename for clarity, then add the XML-RPC sibling

`WordPressDestination` → **`WordPressRestDestination`** (pure rename, same implementation, same tests — just a name that now distinguishes it from its new sibling). A new **`WordPressXmlRpcDestination`** implements `Destination` the same way.

```kotlin
class WordPressXmlRpcDestination
    @Inject
    constructor(
        private val xmlRpcApi: MaterialCaptureXmlRpcApi,
        private val settingsRepository: SettingsRepository,
        private val errorMapper: MaterialCaptureErrorMapper,
    ) : Destination {
        override suspend fun send(item: CaptureItem): Result<DraftResult> {
            val settings = settingsRepository.get() ?: return Result.failure(CaptureError.SettingsNotConfigured.asThrowable())
            return try {
                val url = "${settings.siteUrl}/xmlrpc.php"
                val response = xmlRpcApi.createDraft(url, settings.username, settings.applicationPassword, item)
                when (response) {
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

**Confirmed design choice:** XML-RPC has no first-class Retrofit support and this project needs exactly one method call, not a general-purpose XML-RPC client — so this is a small hand-rolled request/response codec using `javax.xml.parsers`/`org.w3c.dom` (part of the JDK/Android standard library, zero new dependency), plus the existing `OkHttpClient` (already a `:core` dependency) for the actual HTTP POST. Pulling in a full third-party XML-RPC library for one method would be exactly the kind of premature dependency this project's YAGNI stance argues against.

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

This runs on OkHttp's own dispatcher (blocking `execute()` call inside a `suspend fun` is safe here the same way Retrofit's own suspend adapter works, as long as it's invoked from a coroutine already on an IO-appropriate dispatcher — exact dispatcher wiring is an implementation detail for Phase 3d, not a design concern).

### `MaterialCaptureErrorMapper.fromXmlRpcFault(...)`

New method alongside the existing `fromHttpError`/`fromException`, mapping `faultCode` → `CaptureError` using the exact table in [docs/api-spec.md's XML-RPC section](api-spec.md#xml-rpc-fallback-material_capturecreatedraft) (400/403/409/500 map the same as the REST status-code table; a fault that isn't one of WordPress core's own login-failure fault and doesn't match any of the plugin's own codes maps to `CaptureError.Unknown`, same fallback philosophy as REST).

### `CompositeWordPressDestination` — the only thing `Destination`'s Hilt binding points to

```kotlin
class CompositeWordPressDestination
    @Inject
    constructor(
        private val restDestination: WordPressRestDestination,
        private val xmlRpcDestination: WordPressXmlRpcDestination,
        private val settingsRepository: SettingsRepository,
    ) : Destination {
        override suspend fun send(item: CaptureItem): Result<DraftResult> {
            val settings = settingsRepository.get() ?: return Result.failure(CaptureError.SettingsNotConfigured.asThrowable())
            return when (settings.connectionMethod) {
                ConnectionMethod.REST -> restDestination.send(item)
                ConnectionMethod.XML_RPC -> xmlRpcDestination.send(item)
            }
        }
    }
```

`RepositoryModule`'s `@Binds fun bindDestination(...)` target changes from `WordPressRestDestination` to `CompositeWordPressDestination` — a one-line change, and `WordPressRestDestination`/`WordPressXmlRpcDestination` become plain injectable classes (still `@Inject constructor`, just not bound to the `Destination` interface themselves). `SubmitCaptureUseCase` and everything above it never sees this branching.

## Test plan

Mirrors [docs/phase3-android-app-design.md §9](phase3-android-app-design.md#9-android-test-strategy)'s existing split — all of this is `:core` (plain JVM, no Android SDK needed):

- `WordPressXmlRpcDestinationTest` (MockWebServer, same pattern as the existing `WordPressDestinationTest`): sends the correct XML-RPC request body for a given `CaptureItem`, parses a success response into the right `DraftResult`, parses fault responses into the right `CaptureError` via the new mapper method, a malformed/non-XML response maps to `CaptureError.Unknown` rather than crashing.
- `MaterialCaptureXmlRpcApiTest`: round-trips a hand-built XML-RPC methodResponse string through `parseMethodResponse` for both the success-struct and fault shapes, and confirms `buildMethodCallXml` produces well-formed XML that a plain `DocumentBuilder` can parse back (a cheap self-consistency check, not a full XML-RPC spec conformance suite — this project only ever talks to WordPress's own XML-RPC server, not arbitrary XML-RPC clients).
- `CompositeWordPressDestinationTest`: routes to the REST fake when `connectionMethod == REST`, the XML-RPC fake when `XML_RPC`, using two hand-written fakes (consistent with this project's existing "fakes over mocking DSL for small interfaces" preference).
- `MaterialCaptureErrorMapperTest` gains cases for `fromXmlRpcFault` mirroring the existing `fromHttpError` cases.

Deferred to Phase 4/manual production verification (same as the WordPress side): confirming a real Application Password authenticates successfully via `wp_xmlrpc_server::login()` against the actual production host that motivated this feature.

## Non-goals for Phase 3d

- No UI difference in `ConfirmDraftScreen`/error presentation beyond what `CaptureError` already drives — XML-RPC faults get mapped to the same `CaptureError` taxonomy and the same message/action table in [docs/phase3-android-app-design.md §7](phase3-android-app-design.md#7-error-handling), not a parallel one.
- No per-request transport override — `connectionMethod` is a per-site Settings choice, not something chosen per share.
- No automatic REST→XML-RPC fallback-on-failure (confirmed with the user: explicit Settings choice only, not automatic, so a REST failure can't silently become an XML-RPC attempt without the user's awareness).
