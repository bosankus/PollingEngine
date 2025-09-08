import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.withGroovyBuilder
import org.gradle.plugins.signing.Sign

// Publishing and signing configuration extracted from build.gradle.kts to declutter the module script.
fun prop(name: String): String? =
    (project.findProperty(name) as String?)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

val signingEnabledGate: Boolean = run {
    val fromProp = prop("signing.enabled")
        ?.toBooleanStrictOrNull()
    val fromEnv = System.getenv("SIGNING_ENABLED")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.toBooleanStrictOrNull()
    (fromProp ?: fromEnv) == true
}

val hasInMemorySigning: Boolean = listOf("signing.key", "signing.password")
    .all { prop(it)?.isNotBlank() == true }

val hasSecretKeyRingSigning: Boolean = run {
    val keyRing = prop("signing.secretKeyRingFile")
    val passwordOk = prop("signing.password")
        ?.isNotBlank() == true
    keyRing?.let { file(it).exists() } == true && passwordOk
}

val shouldSignPublications: Boolean =
    signingEnabledGate && (hasInMemorySigning || hasSecretKeyRingSigning)

println("[mavenPublishing] Signing detection -> gate=$signingEnabledGate, inMemory=$hasInMemorySigning, secretKeyRing=$hasSecretKeyRingSigning, shouldSign=$shouldSignPublications")

// Ensure any auto-wired signing tasks are skipped when no credentials are present
tasks.withType(Sign::class.java).configureEach {
    enabled = shouldSignPublications
    onlyIf { shouldSignPublications }
}

// Configure Vanniktech Maven Publish extension dynamically to avoid typed references
extensions.getByName("mavenPublishing").withGroovyBuilder {
    // Publish to Maven Central (S01 host is picked up from properties by the plugin if set)
    "publishToMavenCentral"()

    if (shouldSignPublications) {
        "signAllPublications"()
    } else {
        println(
            "[mavenPublishing] No signing config detected. Skipping signing of publications."
        )
    }

    // Define POM metadata required by Maven Central. Values are read from Gradle properties.
    "pom" {
        fun p(key: String) = providers.gradleProperty(key)
        // Top-level POM fields
        (getProperty("name") as Property<String>).set(
            p("pom.name")
        )
        (getProperty("description") as Property<String>).set(
            p("pom.description")
        )
        (getProperty("url") as Property<String>).set(
            p("pom.url")
        )
        // Licenses
        "licenses" {
            "license" {
                (getProperty("name") as Property<String>).set(
                    p("pom.license.name")
                )
                (getProperty("url") as Property<String>).set(
                    p("pom.license.url")
                )
            }
        }
        // Developers
        "developers" {
            "developer" {
                (getProperty("id") as Property<String>).set(
                    p("pom.developer.id")
                )
                (getProperty("name") as Property<String>).set(
                    p("pom.developer.name")
                )
                (getProperty("url") as Property<String>).set(
                    p("pom.developer.url")
                )
            }
        }
        // SCM
        "scm" {
            (getProperty("url") as Property<String>).set(
                p("pom.scm.url")
            )
            (getProperty("connection") as Property<String>).set(
                p("pom.scm.connection")
            )
            (getProperty("developerConnection") as Property<String>).set(
                p("pom.scm.developerConnection")
            )
        }
    }
}
