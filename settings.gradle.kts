rootProject.name = "launchdarkly-java-demo-app"

// By default the logger is resolved from mavenLocal, which exercises the same
// published artifact and POM a real consumer would get.
//
// Pass -PuseLocalLogger to build it from source in the sibling checkout instead,
// so edits to the library show up here without republishing.
if (providers.gradleProperty("useLocalLogger").isPresent) {
    includeBuild("../launchdarkly-java-logger")
}
