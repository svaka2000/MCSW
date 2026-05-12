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
    // Soft-dependency: compile against the StatsService API, but the runtime
    // class comes from the PvPTLStats plugin loaded at server startup.
    compileOnly(project(":stats"))
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
