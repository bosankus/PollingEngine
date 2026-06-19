import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.dokka)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.vanniktechMavenPublish)
    kotlin("native.cocoapods")
    id("signing")
}

group = "in.androidplay"
// The released version is tag-driven in CI: release.yml derives it from the latest `v*` git tag and
// passes it as `-PreleaseVersion=<x.y.z>`. The libs.versions.toml value is only the local/dev
// fallback when no override is supplied.
version = (project.findProperty("releaseVersion") as String?)?.takeIf { it.isNotBlank() }
    ?: libs.versions.pollingengine.get()
description = "PollingEngine KMP library providing robust polling with backoff and jitter"

kotlin {
    explicitApi()

    android {
        namespace = "in.androidplay.pollingengine"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()

        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }

        optimization {
            consumerKeepRules.apply {
                publish = true
                file("consumer-rules.pro")
            }
        }
    }

    // CocoaPods configuration for iOS consumption
    cocoapods {
        ios.deploymentTarget = "14.0"
        summary = (project.findProperty("pollingengine.summary") as String?) ?: description.orEmpty()
        homepage = (project.findProperty("pollingengine.homepage") as String?)
            ?: "https://github.com/bosankus/PollingEngine"
        extraSpecAttributes["license"] = (project.findProperty("pollingengine.license") as String?)
            ?: "Apache-2.0"
        extraSpecAttributes["authors"] = (project.findProperty("pollingengine.authors") as String?)
            ?: "Ankush Bose"

        framework {
            baseName = "PollingEngine"
            isStatic = true
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "PollingEngine"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// Dokka minimal task alias (Dokka 2.x: the V1 `dokkaHtml` task is disabled)
tasks.register("docs") { dependsOn(tasks.named("dokkaGenerate")) }

// Detekt minimal config
detekt {
    buildUponDefaultConfig = true
    autoCorrect = true
}

ktlint {
    android.set(true)
}

apply(from = "publishing.gradle.kts")
