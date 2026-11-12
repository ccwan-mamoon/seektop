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
        packageVersion = "0.3.0"
        includeAllModules = true
        appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))
        licenseFile.set(rootProject.file("LICENSE"))
    }
}
