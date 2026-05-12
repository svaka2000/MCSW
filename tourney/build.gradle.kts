plugins {
    java
    id("io.papermc.paperweight.userdev") version "1.7.7"
}

description = "Auto tournament plugin: 1v1 first-to-5-kills bracket with parallel arenas"

base {
    archivesName.set("Tourney")
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
