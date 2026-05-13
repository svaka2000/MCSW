plugins {
    java
    id("io.papermc.paperweight.userdev") version "1.7.7"
}

description = "PvPTL Parties — group system used by Duels for team fights"

base {
    archivesName.set("PvPTLParties")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    withSourcesJar()
}

dependencies {
    paperweight.paperDevBundle("1.21.1-R0.1-SNAPSHOT")
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
