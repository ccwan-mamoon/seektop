plugins {
    id("org.jetbrains.kotlin.multiplatform")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    id("seektop.dependency-locking")
}
kotlin {
    jvmToolchain(21)
    jvm()
    sourceSets.jvmMain.dependencies {
        implementation(project(":ui"))
        implementation(libs.jna)
        implementation(libs.androidx.sqlite.bundled)
        implementation(libs.coil.core)
        implementation(libs.coil.compose)
        implementation(libs.coil.network.core)
        implementation(libs.coil.svg)
        implementation(libs.coil.network.cache.control)
        implementation(libs.kotlinx.coroutines.swing)
    }
    sourceSets.jvmTest.dependencies { implementation(kotlin("test")) }
}
compose.desktop.application {
    mainClass = "io.github.seektop.desktop.MainKt"
    nativeDistributions {
        packageName = "SeekTop"
        packageVersion = libs.versions.seektop.get()
        windows { iconFile.set(project.file("icons/seektop.ico")) }
        // Keep the UI runtime, networking, native access, charsets and locale providers.
        // Compiler, debugger and other development modules are not needed by the client.
        includeAllModules = providers.gradleProperty("fullDesktopRuntime").map(String::toBoolean).getOrElse(false)
        modules(
            "java.desktop",
            "java.logging",
            "java.management",
            "java.naming",
            "java.net.http",
            "java.sql",
            "java.xml",
            "jdk.accessibility",
            "jdk.charsets",
            "jdk.crypto.ec",
            "jdk.localedata",
            "jdk.management",
            "jdk.unsupported",
        )
        if (System.getProperty("os.name").startsWith("Windows")) {
            modules("jdk.crypto.mscapi")
        }
        appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))
        licenseFile.set(rootProject.file("LICENSE"))
    }
}

val compileWindowsUpdater by tasks.registering(Exec::class) {
    val source = layout.projectDirectory.file("updater/Program.cs")
    val output = layout.projectDirectory.file("resources/windows/SeekTopUpdater.exe")
    inputs.file(source)
    outputs.file(output)
    onlyIf { System.getProperty("os.name").startsWith("Windows") }
    doFirst { output.asFile.parentFile.mkdirs() }
    executable = File(System.getenv("WINDIR") ?: "C:/Windows", "Microsoft.NET/Framework64/v4.0.30319/csc.exe").path
    args("/nologo", "/target:winexe", "/codepage:65001", "/reference:System.Web.Extensions.dll", "/reference:System.Windows.Forms.dll", "/out:${output.asFile.absolutePath}", source.asFile.absolutePath)
}
tasks.matching { it.name == "prepareAppResources" || it.name == "jvmTest" }.configureEach { dependsOn(compileWindowsUpdater) }

val compileWindowsLinkRouter by tasks.registering(Exec::class) {
    val source = layout.projectDirectory.file("link-router/Program.cs")
    val output = layout.projectDirectory.file("resources/windows/SeekTopLinkRouter.exe")
    inputs.file(source)
    outputs.file(output)
    onlyIf { System.getProperty("os.name").startsWith("Windows") }
    doFirst { output.asFile.parentFile.mkdirs() }
    executable = File(System.getenv("WINDIR") ?: "C:/Windows", "Microsoft.NET/Framework64/v4.0.30319/csc.exe").path
    args("/nologo", "/target:winexe", "/codepage:65001", "/reference:System.Web.Extensions.dll", "/reference:System.Windows.Forms.dll", "/out:${output.asFile.absolutePath}", source.asFile.absolutePath)
}
tasks.matching { it.name == "prepareAppResources" || it.name == "jvmTest" }.configureEach { dependsOn(compileWindowsLinkRouter) }

// Compose's app-image implementation can remain up-to-date after prepareAppResources changes.
// Track provisioned files explicitly until that task includes appResourcesRootDir in its own inputs.
tasks.matching { it.name == "createDistributableImpl" }.configureEach {
    inputs.dir(layout.projectDirectory.dir("resources"))
}
