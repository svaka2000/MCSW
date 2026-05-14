plugins {
    java
    id("io.papermc.paperweight.userdev") version "1.7.7"
}

description = "PvPTL Duels — 1v1 duels with queues, parties, custom kits, and parallel arenas"

base {
    archivesName.set("Duels")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

dependencies {
    paperweight.paperDevBundle("1.21.1-R0.1-SNAPSHOT")
    // Soft-dependencies: compile against the APIs, runtime classes come from
    // the PvPTLStats / PvPTLKits plugins loaded at server startup.
    compileOnly(project(":stats"))
    compileOnly(project(":kits"))
    compileOnly(project(":parties"))
    compileOnly(project(":tourney"))
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
