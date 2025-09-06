import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.dokka)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.vanniktechMavenPublish)
    kotlin("native.cocoapods")
    id("signing")
}

group = "io.github.bosankus"
version = libs.versions.pollingengine.get()
description = "PollingEngine KMP library providing robust polling with backoff and jitter"

kotlin {
    explicitApi()

    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }

    // CocoaPods configuration for iOS consumption
    cocoapods {
        ios.deploymentTarget = "14.0"
        summary = project.findProperty("pollingengine.summary") as String
        homepage = project.findProperty("pollingengine.homepage") as String
        extraSpecAttributes["license"] = project.findProperty("pollingengine.license") as String
        extraSpecAttributes["authors"] = project.findProperty("pollingengine.authors") as String

        framework {
            baseName = "PollingEngine"
            isStatic = true
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
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

android {
    namespace = "in.androidplay.pollingengine"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildTypes {
        release {
            isMinifyEnabled = true
        }
    }
}

// Dokka minimal task alias
tasks.register("docs") { dependsOn(tasks.named("dokkaHtml")) }

// Detekt minimal config
detekt {
    buildUponDefaultConfig = true
    autoCorrect = true
}

ktlint {
    android.set(true)
}

apply(from = "publishing.gradle.kts")
