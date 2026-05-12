// Root workspace config — runs `./gradlew build` here to build all modules at once.
// Per-module config lives in each module's build.gradle.kts.

allprojects {
    group = "com.samarth"
    version = "1.0.0"

    repositories {
        mavenCentral()
    }
}
