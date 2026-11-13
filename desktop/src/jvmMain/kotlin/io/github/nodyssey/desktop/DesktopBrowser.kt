package io.github.nodyssey.desktop

import io.github.nodyssey.data.proxy.ProxyConfig
import io.github.plaza.core.net.HttpBody
import io.github.plaza.core.net.HttpRequest
import io.github.plaza.core.net.HttpResponse
import io.github.plaza.core.net.HttpTransport
import io.github.plaza.core.net.SessionCookieStore
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import io.github.plaza.core.net.UploadProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.time.Duration
import java.util.Base64
import java.util.UUID
import java.net.http.HttpRequest as JdkRequest
import java.net.http.HttpResponse as JdkResponse

/** The browser and native requests share Chromium's persistent session; cookies are never exported to disk by the JVM. */
class DesktopBrowser(executable: File, helper: File, directory: File) :
    SessionCookieStore,
    AutoCloseable {
    private val token = UUID.randomUUID().toString()
    private val process = ProcessBuilder(executable.absolutePath, helper.absolutePath).apply {
        environment()["NODYSSEY_BRIDGE_TOKEN"] = token
        environment()["NODYSSEY_BROWSER_DATA"] = directory.absolutePath
        environment()["NODYSSEY_PARENT_PID"] = ProcessHandle.current().pid().toString()
        System.getProperty("compose.application.resources.dir")?.let {
            environment()["SEEKTOP_DESKTOP_EXE"] = File(it, "../../SeekTop.exe").canonicalPath
        }
        redirectError(File(directory.parentFile, "browser-error.log"))
    }.start()
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).proxy(object : java.net.ProxySelector() {
        override fun select(uri: URI): List<java.net.Proxy> = listOf(java.net.Proxy.NO_PROXY)
        override fun connectFailed(uri: URI, address: java.net.SocketAddress, error: java.io.IOException) = Unit
    }).build()
    private val port: Int = java.util.concurrent.CompletableFuture.supplyAsync {
        process.inputStream.bufferedReader().lineSequence().first { it.trim().startsWith("{\"port\":") }
            .let { Json.parseToJsonElement(it).jsonObject.getValue("port").jsonPrimitive.int }
    }.get(30, java.util.concurrent.TimeUnit.SECONDS)
    val userAgent = call("info").getValue("userAgent").jsonPrimitive.content

    suspend fun configureNotifications(): JsonObject = withContext(Dispatchers.IO) { call("notification-configure") }

    suspend fun notificationStatus(): JsonObject = withContext(Dispatchers.IO) { call("notification-status") }

    suspend fun toast(title: String, body: String): Unit = withContext(Dispatchers.IO) {
        call(
            "notification-show",
            buildJsonObject {
                put("title", title)
                put("body", body)
            },
        )
        Unit
    }

    internal fun call(operation: String, body: JsonObject = buildJsonObject {}): JsonObject {
        val builder = JdkRequest.newBuilder(URI("http://127.0.0.1:$port/$operation"))
            .header("Authorization", "Bearer $token").header("Content-Type", "application/json")
            .POST(JdkRequest.BodyPublishers.ofString(body.toString()))
        if (operation != "login") builder.timeout(Duration.ofSeconds(if (operation == "verify") 240 else 90))
        val request = builder.build()
        val response = client.send(request, JdkResponse.BodyHandlers.ofString())
        val result = Json.parseToJsonElement(response.body()).jsonObject
        check(response.statusCode() == 200) { result["error"]?.jsonPrimitive?.content ?: "Browser bridge failed" }
        return result
    }

    suspend fun login(url: String) = withContext(Dispatchers.IO) {
        call("login", buildJsonObject { put("url", url) })
        Unit
    }

    /** A scoped, cancellable verification request; no credentials are given to the widget window. */
    suspend fun verify(dark: Boolean, host: java.awt.Component): String? {
        val id = UUID.randomUUID().toString()
        try {
            while (!host.isShowing || host.width <= 0 || host.height <= 0) kotlinx.coroutines.delay(16)
            val result = kotlinx.coroutines.coroutineScope {
                val request = async(Dispatchers.IO) {
                    kotlinx.coroutines.runInterruptible {
                        call(
                            "verify",
                            buildJsonObject {
                                put("id", id)
                                put("origin", io.github.nodyssey.core.NodeSeekSite.BASE_URL)
                                put("dark", dark)
                                put("embedded", true)
                            },
                        )
                    }
                }
                var attached = false
                while (!request.isCompleted) {
                    val handle = withContext(Dispatchers.IO) { call("verification-window", buildJsonObject { put("id", id) })["handle"]?.jsonPrimitive?.content.orEmpty() }
                    if (handle.isNotBlank()) {
                        withContext(Dispatchers.Main) { EmbeddedVerification.resize(handle, host, !attached) }
                        if (!attached) {
                            withContext(Dispatchers.IO) { call("verification-show", buildJsonObject { put("id", id) }) }
                            attached = true
                        }
                    }
                    kotlinx.coroutines.delay(150)
                }
                request.await()
            }
            if (result["kind"]?.jsonPrimitive?.content == "error") throw IllegalStateException(result["value"]?.jsonPrimitive?.content?.take(160)?.ifBlank { "verification-error" } ?: "verification-error")
            return result["value"]?.jsonPrimitive?.content?.takeIf { result["kind"]?.jsonPrimitive?.content == "token" && it.isNotBlank() }
        } finally {
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { runCatching { call("verification-close", buildJsonObject { put("id", id) }) } }
        }
    }
    override fun cookieHeader(url: String): String? = call("cookies", buildJsonObject { put("url", url) })["header"]?.jsonPrimitive?.content?.ifBlank { null }
    override fun setCookie(url: String, cookie: String) {
        call(
            "set-cookie",
            buildJsonObject {
                put("url", url)
                put("cookie", cookie)
            },
        )
    }
    override suspend fun removeAll() {
        withContext(Dispatchers.IO) { call("clear-cookies") }
    }
    override fun flush() {
        call("flush")
    }
    suspend fun cacheSize(): Long = withContext(Dispatchers.IO) { call("cache-size").getValue("size").jsonPrimitive.long }
    suspend fun clearCache() {
        withContext(Dispatchers.IO) { call("clear-cache") }
    }
    private val proxyMutex = Mutex()
    private var appliedProxy: ProxyConfig? = null

    suspend fun applyProxy(config: ProxyConfig) = withContext(Dispatchers.IO) {
        proxyMutex.withLock {
            if (appliedProxy == config) return@withLock
            call(
                "proxy",
                buildJsonObject {
                    put("enabled", config.enabled && config.isUsable)
                    put("type", config.type.name)
                    put("host", config.host)
                    put("port", config.port)
                    put("scope", config.scope.name)
                    put("username", config.username)
                    put("password", config.password)
                },
            )
            appliedProxy = config
            Unit
        }
    }

    fun transport(forum: Boolean): HttpTransport = object : HttpTransport {
        override suspend fun execute(request: HttpRequest, onUploadProgress: UploadProgress?): HttpResponse = withContext(Dispatchers.IO) {
            val q = buildJsonObject {
                put("url", request.url)
                put("method", request.method)
                put("forum", forum)
                put("headers", buildJsonObject { request.headers.forEach { (k, v) -> put(k, v) } })
                request.body?.let { body ->
                    put(
                        "body",
                        buildJsonObject {
                            when (body) {
                                HttpBody.Empty -> put("kind", "empty")

                                is HttpBody.Text -> {
                                    put("kind", "text")
                                    put("content", body.content)
                                    put("contentType", body.contentType)
                                }

                                is HttpBody.Multipart -> {
                                    put("kind", "multipart")
                                    put("fields", buildJsonObject { body.fields.forEach { (k, v) -> put(k, v) } })
                                    put("fileField", body.fileField)
                                    put("name", body.fileName)
                                    put("mime", body.fileMimeType)
                                    put("bytes", Base64.getEncoder().encodeToString(body.fileBytes))
                                }
                            }
                        },
                    )
                }
            }
            val r = try {
                call("fetch", q)
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                throw SiteException(SiteError.Network, failure)
            }
            onUploadProgress?.invoke(1f)
            HttpResponse(
                r.getValue("code").jsonPrimitive.int,
                r.getValue("url").jsonPrimitive.content,
                r.getValue("headers").jsonObject.mapValues { it.value.jsonPrimitive.content },
                r.getValue("body").jsonPrimitive.content,
            )
        }
    }

    suspend fun image(url: String, maxBytes: Long): Pair<HttpResponse, ByteArray> = withContext(Dispatchers.IO) {
        val host = URI(url).host
        val r = call(
            "fetch",
            buildJsonObject {
                put("url", url)
                put("method", "GET")
                put("forum", host == "www.nodeseek.com" || host == "nodeseek.com")
                put("binary", true)
                put("maxBytes", maxBytes)
                put("headers", buildJsonObject { put("Referer", "https://www.nodeseek.com/") })
            },
        )
        HttpResponse(r.getValue("code").jsonPrimitive.int, url, r.getValue("headers").jsonObject.mapValues { it.value.jsonPrimitive.content }, "") to Base64.getDecoder().decode(r.getValue("body").jsonPrimitive.content)
    }
    suspend fun bytes(url: String, maxBytes: Long): ByteArray? = image(url, maxBytes).let { (response, bytes) -> if (response.isSuccessful) bytes else null }
    override fun close() {
        runCatching { call("shutdown") }
        if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) process.destroy()
    }
}
