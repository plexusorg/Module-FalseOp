plugins {
    java
    `maven-publish`
}

repositories {
    maven {
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }

    maven {
        url = uri("https://nexus.telesphoreo.me/repository/plex/")
    }

    maven {
        url = uri("https://repo.dmulloy2.net/repository/public/")
    }

    mavenCentral()
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    compileOnly("dev.plex:server:1.5-SNAPSHOT")
    implementation("com.comphenix.protocol:ProtocolLib:5.3.0-SNAPSHOT")
}

group = "dev.plex"
version = "1.5-SNAPSHOT"
description = "Module-FalseOp"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

tasks.getByName < Jar > ("jar") {
    archiveBaseName.set("Module-FalseOp")
    archiveVersion.set("")
}

tasks {
    compileJava {
        options.encoding = Charsets.UTF_8.name()
    }
    javadoc {
        options.encoding = Charsets.UTF_8.name()
    }
    processResources {
        filteringCharset = Charsets.UTF_8.name()
    }
}