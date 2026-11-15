package io.github.seektop.desktop

import io.github.seektop.ai.AiExchangeRate
import io.github.seektop.ai.AiExchangeRates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.math.BigDecimal
import java.math.MathContext
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal val exchangeCurrencies = listOf("USD", "AUD", "CAD", "CHF", "CNY", "EUR", "GBP", "HKD", "INR", "JPY", "KRW", "NZD", "RUB", "SGD", "TWD")
private const val DAY_MS = 86400000L
private const val RETRY_MS = 900000L
private const val FX_SOURCE = "https://api.frankfurter.dev/v2/rates"

@Serializable
internal data class ReferenceRate(val toCny: String, val date: String)

@Serializable
internal data class ExchangeRateCache(val checkedAt: Long, val rates: Map<String, ReferenceRate>, val overrides: Map<String, ReferenceRate> = emptyMap())

/** API quotes are CNY -> foreign; normalize once so every stored quote is 1 foreign -> CNY. */
internal fun parseExchangeRates(body: String, now: Long): ExchangeRateCache {
    require(body.length <= 65536)
    val today = Instant.ofEpochMilli(now).atZone(ZoneId.of("Asia/Shanghai")).toLocalDate()
    val rates = linkedMapOf<String, ReferenceRate>()
    Json.parseToJsonElement(body).jsonArray.forEach { element ->
        val row = element.jsonObject
        require(row.getValue("base").jsonPrimitive.content == "CNY")
        val code = row.getValue("quote").jsonPrimitive.content
        require(code in exchangeCurrencies && code != "CNY" && code !in rates)
        val date = LocalDate.parse(row.getValue("date").jsonPrimitive.content)
        require(!date.isAfter(today))
        val rate = row.getValue("rate").jsonPrimitive.content.toBigDecimal()
        require(rate >= BigDecimal("0.000000001") && rate <= BigDecimal("1000000000"))
        rates[code] = ReferenceRate(BigDecimal.ONE.divide(rate, MathContext.DECIMAL128).stripTrailingZeros().toPlainString(), date.toString())
    }
    require(rates.keys == exchangeCurrencies.filterNot { it == "CNY" }.toSet())
    rates["CNY"] = ReferenceRate("1", today.toString())
    return ExchangeRateCache(now, rates)
}

/** A bad refresh never replaces the last complete, validated snapshot. */
internal class DesktopExchangeRates(private val file: File, private val fetch: suspend () -> String, private val now: () -> Long = System::currentTimeMillis) {
    private val mutex = Mutex()

    @Volatile private var current: ExchangeRateCache? = null
    private var loaded = false
    private var attemptedAt: Long? = null

    @Volatile private var refreshFailed = false

    private fun loadSaved() {
        if (loaded) return
        loaded = true
        try {
            if (file.length() in 1..65536) {
                val cached = Json.decodeFromString<ExchangeRateCache>(file.readText())
                val today = Instant.ofEpochMilli(now()).atZone(ZoneId.of("Asia/Shanghai")).toLocalDate()
                require(cached.checkedAt in 0..now())
                require(if (cached.checkedAt == 0L) cached.rates.isEmpty() else cached.rates.keys == exchangeCurrencies.toSet())
                (cached.rates.entries + cached.overrides.entries).forEach { (code, rate) ->
                    require(code in exchangeCurrencies && rate.toCny.length <= 64)
                    require(rate.toCny.toBigDecimal() in BigDecimal("0.000000001")..BigDecimal("1000000000") && !LocalDate.parse(rate.date).isAfter(today))
                    require(code != "CNY" || rate.toCny.toBigDecimal().compareTo(BigDecimal.ONE) == 0)
                }
                require("CNY" !in cached.overrides)
                current = cached
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) { /* Corrupt cache is discarded; fetch public data instead. */ }
    }

    private fun persist(snapshot: ExchangeRateCache) {
        val temporary = File(file.parentFile, file.name + ".tmp")
        file.parentFile.mkdirs()
        temporary.writeText(Json.encodeToString(snapshot))
        try {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        current = snapshot
    }

    suspend fun refresh(force: Boolean = false) = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadSaved()
            val instant = now()
            if (!force && current?.let { it.checkedAt > 0 && instant - it.checkedAt in 0 until DAY_MS } == true) return@withLock
            if (!force && attemptedAt?.let { instant - it in 0 until RETRY_MS } == true) return@withLock
            attemptedAt = instant
            try {
                persist(parseExchangeRates(fetch(), now()).copy(overrides = current?.overrides.orEmpty()))
                refreshFailed = false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                refreshFailed = true
            }
        }
    }

    suspend fun configuration(): AiExchangeRates = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadSaved()
            val snapshot = current
            AiExchangeRates(
                exchangeCurrencies.map { code ->
                    val manual = snapshot?.overrides?.get(code)
                    val automatic = snapshot?.rates?.get(code)
                    val effective = manual ?: automatic
                    AiExchangeRate(code, if (code == "CNY") "1" else effective?.toCny.orEmpty(), effective?.date.orEmpty(), manual != null, automatic?.toCny.orEmpty())
                },
                snapshot?.checkedAt?.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).toString() }.orEmpty(),
                refreshFailed,
            )
        }
    }

    suspend fun saveOverrides(values: Map<String, String>): AiExchangeRates {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                loadSaved()
                require(values.keys.all { it in exchangeCurrencies && it != "CNY" }) { "FX_INVALID_RATE" }
                val today = Instant.ofEpochMilli(now()).atZone(ZoneId.of("Asia/Shanghai")).toLocalDate().toString()
                val overrides = values.mapValues { (code, value) ->
                    val clean = value.trim()
                    require(clean.length in 1..40 && Regex("(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)").matches(clean)) { "FX_INVALID_RATE" }
                    val number = clean.toBigDecimal()
                    require(number in BigDecimal("0.000000001")..BigDecimal("1000000000")) { "FX_INVALID_RATE" }
                    val normalized = number.stripTrailingZeros().toPlainString()
                    val old = current?.overrides?.get(code)
                    if (old?.toCny == normalized) old else ReferenceRate(normalized, today)
                }
                persist((current ?: ExchangeRateCache(0, emptyMap())).copy(overrides = overrides))
            }
        }
        return configuration()
    }

    suspend fun analysisContext(): String {
        // Startup refresh is asynchronous. On a first-ever analysis, wait briefly for its snapshot.
        if (current == null) withTimeoutOrNull(5000) { refresh() }
        val snapshot = current
        return buildString {
            append("\nClient reference FX cache. Source: Frankfurter ($FX_SOURCE), blended reference rates, not live payment quotes. ")
            if (snapshot == null) {
                append("No validated FX data available. Never invent exchange rates; still calculate remaining value in its original currency when possible. Only cross-currency fields may be unavailable.\n")
                return@buildString
            }
            if (snapshot.checkedAt > 0) append("Retrieved: ").append(Instant.ofEpochMilli(snapshot.checkedAt)).append(". ")
            if (snapshot.checkedAt > 0 && now() - snapshot.checkedAt >= DAY_MS) append("Cached data older than 24h; clearly label its date, do not claim it is today's rate. ")
            append("Each entry means 1 unit of the named currency equals toCny CNY; rateDate is its actual quote date:\n")
            exchangeCurrencies.forEach { code ->
                val manual = snapshot.overrides[code]
                val rate = manual ?: snapshot.rates[code]
                if (code == "CNY") {
                    append("CNY: toCny=1 (identity)\n")
                } else if (rate == null) {
                    append(code).append(": unavailable\n")
                } else {
                    append(code).append(": toCny=").append(rate.toCny).append(", rateDate=").append(rate.date).append(if (manual != null) ", source=manually saved in client settings" else ", source=Frankfurter").append('\n')
                }
            }
            append("Use these supplied rates for conversions: CNY amount = foreign amount * toCny; A to B = amount * A.toCny / B.toCny. An explicit user-supplied rate overrides the cache; label it as user supplied. Convert to a common currency before computing premium or premium percentage. Calculate remaining value in original currency first even when an FX pair is unavailable. Identify ambiguous currency symbols instead of guessing their currency. Briefly annotate only the rate(s) actually used and their quote date inline in the result; do not dump the full rate list or add a long explanation. Fees are separate unless explicitly included.\n")
        }
    }
}
