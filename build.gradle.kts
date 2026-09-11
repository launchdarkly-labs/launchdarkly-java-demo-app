plugins {
    application
}

description = "Sample app exercising launchdarkly-java-logger against a live LaunchDarkly environment"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    // The logger is installed here by `./gradlew publishToMavenLocal` in the
    // launchdarkly-java-logger checkout.
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("io.github.bradbunce:launchdarkly-java-logger:1.0.0")

    // The logger declares these compileOnly on purpose, so a consumer supplies
    // them. Getting this wrong is the most likely first-use mistake, which makes
    // it worth showing plainly here.
    implementation("com.launchdarkly:launchdarkly-java-server-sdk:7.16.0")
    runtimeOnly("ch.qos.logback:logback-classic:1.6.3")
}

application {
    mainClass = "io.github.bradbunce.ldloggerdemo.DemoApp"
}

/**
 * Reads a `.env` file into a map, so `./gradlew run` needs no shell preamble.
 * Blank lines and `#` comments are skipped; surrounding quotes are stripped.
 */
fun dotEnv(): Map<String, String> {
    val envFile = file(".env")
    if (!envFile.exists()) {
        return emptyMap()
    }
    return envFile.readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
        .associate { line ->
            val name = line.substringBefore("=").trim()
            val value = line.substringAfter("=").trim().removeSurrounding("\"").removeSurrounding("'")
            name to value
        }
}

tasks.named<JavaExec>("run") {
    // Without this the demo's interactive commands cannot be typed under
    // `gradlew run`, since Gradle gives the process an empty stdin.
    standardInput = System.`in`
    environment(dotEnv())
}

tasks.register<JavaExec>("doctor") {
    group = "application"
    description = "Prints diagnostics for the configured flags and backend, then exits."
    mainClass = application.mainClass
    classpath = sourceSets["main"].runtimeClasspath
    environment(dotEnv())
    args("doctor")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}
