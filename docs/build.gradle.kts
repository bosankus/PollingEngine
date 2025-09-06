// Minimal Gradle module to allow running Gradle from the `docs/` directory without errors.
// This project does not participate in the build; it just makes Gradle aware that `docs/` is a subproject.

// No plugins are applied here intentionally.

// Provide a friendly helper task (optional)
tasks.register("docsInfo") {
    group = "documentation"
    description =
        "Info: The docs directory is not a buildable module; use :pollingengine:dokkaHtml to generate API docs."
    doLast {
        println("Run ./gradlew :pollingengine:dokkaHtml to generate Dokka HTML docs.")
    }
}
