plugins {
    `java-library`
}

group = "com.lifeline"
version = "0.0.1"
description = "2-Player Co-op Plugin for Paper: Shared Waypoints, Shared Vault, and Downed/Revive System"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://oss.sonatype.org/content/groups/public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    processResources {
        val props = mapOf("version" to version)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("paper-plugin.yml") {
            expand(props)
        }
    }

    jar {
        archiveBaseName.set("Lifeline")
        archiveClassifier.set("")
        archiveVersion.set(project.version.toString())
    }
}
