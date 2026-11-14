package io.github.seektop.desktop

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.management.ManagementFactory
import java.net.http.HttpClient
import java.nio.charset.Charset
import java.security.KeyPairGenerator
import java.text.DateFormatSymbols
import java.util.Locale
import javax.imageio.ImageIO
import javax.net.ssl.SSLContext

/** Runs through the packaged launcher, so a developer's full JDK cannot hide missing modules. */
internal fun checkDesktopRuntime(result: File) {
    try {
        HttpClient.newHttpClient().use { check(it.sslContext() != null) }
        check(SSLContext.getDefault().createSSLEngine().supportedProtocols.contains("TLSv1.3"))
        check(KeyPairGenerator.getInstance("EC").generateKeyPair().public.encoded.isNotEmpty())
        for (name in listOf("GB18030", "Big5")) {
            val charset = Charset.forName(name)
            check(String("中文".toByteArray(charset), charset) == "中文")
        }
        check(DateFormatSymbols.getInstance(Locale.SIMPLIFIED_CHINESE).months[0].contains("月"))
        check(ManagementFactory.getMemoryMXBean().heapMemoryUsage.used >= 0)
        Class.forName("sun.misc.Unsafe")
        Class.forName("java.sql.DriverManager")
        Class.forName("javax.naming.InitialContext")
        val image = BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB)
        ByteArrayOutputStream().use { check(ImageIO.write(image, "png", it)) }
        BundledSQLiteDriver().open(":memory:").use { connection ->
            connection.prepare("SELECT 1").use { statement ->
                check(statement.step())
                check(statement.getLong(0) == 1L)
            }
        }
        result.writeText("PASS: packaged runtime HTTP, TLS, EC, Chinese charsets/locales, AWT, native SQLite and JVM services\n")
    } catch (failure: Throwable) {
        result.writeText("FAIL: ${failure.stackTraceToString()}")
        throw failure
    }
}
