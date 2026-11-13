plugins {
    id("org.jetbrains.kotlin.multiplatform")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    id("plaza.dependency-locking")
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
    mainClass = "io.github.nodyssey.desktop.MainKt"
    nativeDistributions {
        packageName = "SeekTop"
        packageVersion = libs.versions.seektop.get()
        windows { iconFile.set(project.file("icons/seektop.ico")) }
        includeAllModules = true
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
