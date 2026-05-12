plugins {
    java
    id("io.papermc.paperweight.userdev") version "1.7.7"
}

description = "PvPTL AI Help — Groq-powered NPC that answers player questions in-character"

base {
    archivesName.set("AIHelp")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

dependencies {
    paperweight.paperDevBundle("1.21.1-R0.1-SNAPSHOT")
    // Gson is on Paper's classpath already; no extra dep needed.
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
