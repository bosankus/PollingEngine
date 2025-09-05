plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    id("io.github.gradle-nexus.publish-plugin") version "1.3.0"
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.14.0"
}

nexusPublishing {
    repositories {
        sonatype {
            // Using the modern s01 host (most new OSSRH accounts)
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
            username.set((findProperty("ossrhUsername") as String?))
            password.set((findProperty("ossrhPassword") as String?))
        }
    }
}