import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    kotlin("native.cocoapods")
    id("org.jetbrains.dokka") version "2.0.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.6"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.1"
    id("maven-publish")
    id("signing")
}

group = "in.androidplay"
version = "0.1.0"
description = "PollingEngine KMP library providing robust polling with backoff and jitter"

kotlin {
    explicitApi()

    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
        publishLibraryVariants("release")
    }

    // CocoaPods configuration for iOS consumption
    cocoapods {
        summary = "Robust polling engine with configurable backoff and jitter"
        homepage = "https://github.com/androidplay/PollingEngine"
        ios.deploymentTarget = "14.0"
        // Add extra spec attributes for license and authors
        extraSpecAttributes["license"] = "{ :type => 'Apache-2.0', :file => 'LICENSE' }"
        extraSpecAttributes["authors"] = "{ 'AndroidPlay' => 'opensource@androidplay.in' }"

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
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
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
            isMinifyEnabled = false
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

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("PollingEngine")
            description.set(project.description)
            url.set("https://github.com/androidplay/PollingEngine")
            licenses {
                license {
                    name.set("Apache-2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                }
            }
            scm {
                url.set("https://github.com/androidplay/PollingEngine")
            }
            developers {
                developer {
                    id.set("androidplay")
                    name.set("AndroidPlay")
                }
            }
        }
    }
}

signing {
    // No-op locally; configured via env/gradle.properties in CI
    isRequired = false
}
