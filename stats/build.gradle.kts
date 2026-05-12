plugins {
    java
    id("io.papermc.paperweight.userdev") version "1.7.7"
}

description = "PvPTL Stats — shared SQLite-backed player stats backend for Tourney + Duels"

base {
    archivesName.set("PvPTLStats")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    withSourcesJar()
}

dependencies {
    paperweight.paperDevBundle("1.21.1-R0.1-SNAPSHOT")
    // sqlite-jdbc is loaded at runtime via plugin.yml's `libraries:` block.
    compileOnly("org.xerial:sqlite-jdbc:3.46.1.3")
}

tasks {
    compileJava {
        options.encoding = Charsets.UTF_8.name()
        options.release.set(21)
    }
    processResources {
        filteringCharset = Charsets.UTF_8.name()
        val props = mapOf("version" to project.version)
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
