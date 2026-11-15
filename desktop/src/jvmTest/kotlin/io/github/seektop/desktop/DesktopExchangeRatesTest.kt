package io.github.seektop.desktop

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopExchangeRatesTest {
    private val initial = Instant.parse("2026-11-15T04:00:00Z").toEpochMilli()
    private fun payload(date: String = "2026-11-13", usd: String = "0.125") = exchangeCurrencies.filterNot { it == "CNY" }.joinToString(",", "[", "]") {
        """{"date":"$date","base":"CNY","quote":"$it","rate":${if (it == "USD") usd else "2"}}"""
    }

    @Test fun `manual rates survive restart and automatic updates and reset restores latest automatic rates`() = runBlocking<Unit> {
        val directory = Files.createTempDirectory("fx-manual-test").toFile()
        try {
            val file = directory.resolve("rates.json")
            var now = initial
            var body = payload()
            val rates = DesktopExchangeRates(file, { body }, { now })
            rates.refresh()
            rates.saveOverrides(mapOf("USD" to "7.25", "EUR" to "8.5"))
            assertTrue("USD: toCny=7.25" in rates.analysisContext())
            assertTrue(rates.configuration().rates.first { it.code == "USD" }.manual)
            now += 86400001
            body = payload(usd = "0.2")
            rates.refresh()
            assertEquals("5", rates.configuration().rates.first { it.code == "USD" }.automatic)
            assertTrue("USD: toCny=7.25" in rates.analysisContext())
            val restored = DesktopExchangeRates(file, { error("offline") }, { now })
            restored.refresh()
            assertTrue("USD: toCny=7.25" in restored.analysisContext())
            restored.saveOverrides(mapOf("EUR" to "8.5"))
            assertFalse(restored.configuration().rates.first { it.code == "USD" }.manual)
            assertTrue("USD: toCny=5" in restored.analysisContext())
            assertTrue("EUR: toCny=8.5" in restored.analysisContext())
            restored.saveOverrides(emptyMap())
            assertTrue(restored.configuration().rates.none { it.manual })
            val before = file.readText()
            for (invalid in listOf(mapOf("USD" to "0"), mapOf("USD" to "NaN"), mapOf("CNY" to "2"), mapOf("BAD" to "1"))) {
                assertFailsWith<IllegalArgumentException> { restored.saveOverrides(invalid) }
                assertEquals(before, file.readText())
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun `manual configuration works without any automatic rates and records its own date`() = runBlocking<Unit> {
        val directory = Files.createTempDirectory("fx-offline-manual-test").toFile()
        try {
            val file = directory.resolve("rates.json")
            val rates = DesktopExchangeRates(file, { error("offline") }, { initial })
            val config = rates.saveOverrides(mapOf("USD" to "7.100"))
            assertEquals("7.1", config.rates.first { it.code == "USD" }.toCny)
            assertEquals("2026-11-15", config.rates.first { it.code == "USD" }.date)
            val restarted = DesktopExchangeRates(file, { error("offline") }, { initial })
            restarted.refresh()
            assertTrue("USD: toCny=7.1" in restarted.analysisContext())
            assertTrue("source=manually saved in client settings" in restarted.analysisContext())
            assertTrue("EUR: unavailable" in restarted.analysisContext())
            assertEquals("1", restarted.configuration().rates.first { it.code == "CNY" }.toCny)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun `CNY base rates are inverted once and actual quote dates survive`() {
        val parsed = parseExchangeRates(payload(), initial)
        assertEquals(exchangeCurrencies.toSet(), parsed.rates.keys)
        assertEquals("8", parsed.rates.getValue("USD").toCny)
        assertEquals("0.5", parsed.rates.getValue("JPY").toCny)
        assertEquals("1", parsed.rates.getValue("CNY").toCny)
        assertEquals("2026-11-13", parsed.rates.getValue("USD").date)
        for (bad in listOf(payload(usd = "0"), payload(usd = "-1"), payload(date = "2026-11-16"), payload().replace("\"base\":\"CNY\"", "\"base\":\"USD\""), payload().replace("\"TWD\"", "\"USD\""))) {
            assertFailsWith<IllegalArgumentException> { parseExchangeRates(bad, initial) }
        }
    }

    @Test fun `concurrent refreshes share a request daily cache survives restart and failed refresh`() = runBlocking<Unit> {
        val directory = Files.createTempDirectory("fx-cache-test").toFile()
        try {
            val file = directory.resolve("ai/rates.json")
            var now = initial
            var calls = 0
            var failure = false
            val rates = DesktopExchangeRates(file, {
                calls++
                delay(10)
                if (failure) error("offline")
                payload()
            }, { now })
            List(5) { async { rates.refresh() } }.awaitAll()
            assertEquals(1, calls)
            val context = rates.analysisContext()
            assertTrue("USD: toCny=8, rateDate=2026-11-13" in context)
            assertTrue("https://api.frankfurter.dev/v2/rates" in context)
            val restored = DesktopExchangeRates(file, { error("must not fetch fresh cache") }, { now })
            restored.refresh()
            assertEquals(context, restored.analysisContext())
            val before = file.readText()
            now += 86400001
            failure = true
            rates.refresh()
            assertEquals(2, calls)
            rates.refresh()
            assertEquals(2, calls)
            assertEquals(before, file.readText())
            assertTrue("Cached data older than 24h" in rates.analysisContext())
            now += 900001
            failure = false
            rates.refresh()
            assertEquals(3, calls)
            assertFalse("Cached data older than 24h" in rates.analysisContext())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun `malformed or incomplete updates keep old rates and corrupt offline cache never invents rates`() = runBlocking<Unit> {
        val directory = Files.createTempDirectory("fx-invalid-test").toFile()
        try {
            val file = directory.resolve("rates.json")
            var now = initial
            var body = payload()
            val rates = DesktopExchangeRates(file, { body }, { now })
            rates.refresh()
            val previous = file.readText()
            now += 86400001
            body = "[]"
            rates.refresh()
            assertEquals(previous, file.readText())
            assertTrue("USD: toCny=8" in rates.analysisContext())
            file.writeText("broken json")
            val offline = DesktopExchangeRates(file, { error("offline") }, { now })
            assertTrue("No validated FX data available" in offline.analysisContext())
            assertFalse("USD: toCny=" in offline.analysisContext())
            val cancelled = DesktopExchangeRates(directory.resolve("other.json"), { throw CancellationException() }, { now })
            assertFailsWith<CancellationException> { cancelled.refresh() }
        } finally {
            directory.deleteRecursively()
        }
    }
}
