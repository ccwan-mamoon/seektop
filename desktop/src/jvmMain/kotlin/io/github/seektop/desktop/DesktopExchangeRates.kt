package io.github.seektop.desktop

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
internal data class ExchangeRateCache(val checkedAt: Long, val rates: Map<String, ReferenceRate>)

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

    suspend fun refresh() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!loaded) {
                loaded = true
                try {
                    if (file.length() in 1..65536) {
                        val cached = Json.decodeFromString<ExchangeRateCache>(file.readText())
                        val today = Instant.ofEpochMilli(now()).atZone(ZoneId.of("Asia/Shanghai")).toLocalDate()
                        require(cached.checkedAt in 1..now() && cached.rates.keys == exchangeCurrencies.toSet())
                        cached.rates.forEach { (code, rate) ->
                            require(rate.toCny.toBigDecimal() in BigDecimal("0.000000001")..BigDecimal("1000000000") && !LocalDate.parse(rate.date).isAfter(today))
                            require(code != "CNY" || rate.toCny.toBigDecimal().compareTo(BigDecimal.ONE) == 0)
                        }
                        current = cached
                    }
                } catch (_: Exception) { /* Corrupt cache is discarded; fetch public data instead. */ }
            }
            val instant = now()
            if (current?.let { instant - it.checkedAt in 0 until DAY_MS } == true) return@withLock
            if (attemptedAt?.let { instant - it in 0 until RETRY_MS } == true) return@withLock
            attemptedAt = instant
            try {
                val fresh = parseExchangeRates(fetch(), now())
                val temporary = File(file.parentFile, file.name + ".tmp")
                file.parentFile.mkdirs()
                temporary.writeText(Json.encodeToString(fresh))
                try {
                    Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                current = fresh
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) { /* Preserve cached rates and retry later; analysis remains available. */ }
        }
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
            append("Retrieved: ").append(Instant.ofEpochMilli(snapshot.checkedAt)).append(". ")
            if (now() - snapshot.checkedAt >= DAY_MS) append("Cached data older than 24h; clearly label its date, do not claim it is today's rate. ")
            append("Each entry means 1 unit of the named currency equals toCny CNY; rateDate is its actual quote date:\n")
            exchangeCurrencies.forEach { code ->
                val rate = snapshot.rates.getValue(code)
                append(code).append(": toCny=").append(rate.toCny).append(", rateDate=").append(rate.date).append('\n')
            }
            append("Use these supplied rates for conversions: CNY amount = foreign amount * toCny; A to B = amount * A.toCny / B.toCny. An explicit user-supplied rate overrides the cache; label it as user supplied. Convert to a common currency before computing premium or premium percentage. Calculate remaining value in original currency first even when an FX pair is unavailable. Identify ambiguous currency symbols instead of guessing their currency. Briefly annotate only the rate(s) actually used and their quote date inline in the result; do not dump the full rate list or add a long explanation. Fees are separate unless explicitly included.\n")
        }
    }
}
