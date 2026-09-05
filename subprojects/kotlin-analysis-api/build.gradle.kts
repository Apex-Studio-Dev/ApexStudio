import dev.apexstudio.ide.build.config.BuildConfig
import dev.apexstudio.ide.plugins.extension.AssetSource

plugins {
    id("com.android.library")
    id("kotlin-android")
    id("dev.apexstudio.ide.build.external-assets")
}

android {
    namespace = "${BuildConfig.PACKAGE_NAME}.kt.analysis"
    compileSdk = 36
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

val ktAndroidRepo = "https://github.com/Apex-Studio-Dev/kotlin-android"
val ktAndroidVersion = "2.3.255"
val ktAndroidTag = "v${ktAndroidVersion}-c07dc30"
val ktAndroidJarName = "analysis-api-standalone-embeddable-for-ide-${ktAndroidVersion}-SNAPSHOT.jar"

externalAssets {
    jarDependency("kt-android") {
        configuration = "api"
        source =
            AssetSource.External(
                url = uri("$ktAndroidRepo/releases/download/$ktAndroidTag/$ktAndroidJarName"),
                sha256Checksum = "418da363aaa6ebea3f234042cf91444de751bcf8264c4eb9cde38c763a79b3a5",
            )
    }
}