plugins {
    id("java")
    id("dev.architectury.loom") version("1.7-SNAPSHOT")
    id("architectury-plugin") version("3.4-SNAPSHOT")
    kotlin("jvm") version ("1.9.23")
}

group = property("maven_group")!!
version = property("mod_version")!!

architectury {
    platformSetupLoomIde()
    fabric()
}

loom {
    silentMojangMappingsLicense()
    mixin { defaultRefmapName.set("mixins.${project.name}.refmap.json") }
    splitEnvironmentSourceSets()
    runs {
        // Standard-Client in eigenem Verzeichnis
        named("client") {
            runDir("run-client")
            vmArgs("-Dmixin.debug=true", "-Dmixin.dumpTargetOnFailure=true")
        }
        // Standard-Server in eigenem Verzeichnis
        named("server") {
            runDir("run-server")
            vmArgs("-Dmixin.debug=true", "-Dmixin.dumpTargetOnFailure=true")
        }
        // Zweiter Client zum Parallel-Testen
        create("client2") {
            client()
            runDir("run-client2")
            vmArgs("-Dmixin.debug=true", "-Dmixin.dumpTargetOnFailure=true")
        }
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    withSourcesJar()
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
    maven("https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/")
    maven("https://maven.impactdev.net/repository/development/")
    maven("https://maven.impactdev.net/repository/maven-releases/")
    maven("https://api.modrinth.com/maven")
}

dependencies {
    minecraft("net.minecraft:minecraft:1.21.1")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:0.16.5")

    modRuntimeOnly("net.fabricmc.fabric-api:fabric-api:0.104.0+1.21.1")
    modImplementation(fabricApi.module("fabric-command-api-v2", "0.104.0+1.21.1"))

    modImplementation("net.fabricmc:fabric-language-kotlin:1.12.3+kotlin.2.0.21")
    modImplementation("com.cobblemon:fabric:1.6.0+1.21.1-SNAPSHOT")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") { expand(project.properties) }
}

tasks.jar { from("LICENSE") }