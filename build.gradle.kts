plugins {
    id("java")
    id("dev.architectury.loom") version "1.7-SNAPSHOT"
    id("architectury-plugin") version "3.4-SNAPSHOT"
    kotlin("jvm") version "1.9.23"
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
    minecraft("net.minecraft:minecraft:${property("minecraft_version")}")
    mappings(loom.officialMojangMappings())

    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")

    // Fabric API wie im Cobblemon MDK
    modRuntimeOnly("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_runtime")}")

    // ➜ zusätzliche Module NUR für Compile/Zeit:
    modImplementation(fabricApi.module("fabric-lifecycle-events-v1", property("fabric_api_runtime") as String))
    modImplementation(fabricApi.module("fabric-item-group-api-v1",   property("fabric_api_runtime") as String))

    // Command API bleibt wie gehabt (aus dem MDK)
    modImplementation(fabricApi.module("fabric-command-api-v2", property("fabric_api_cmd") as String))

    // Kotlin-Runtime (Cobblemon braucht das)
    modImplementation("net.fabricmc:fabric-language-kotlin:${property("fabric_kotlin")}")

    // Cobblemon (ImpactDev Maven, dev build)
    modImplementation("com.cobblemon:fabric:${property("cobblemon_version")}")
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") { expand(project.properties) }
}

tasks.jar { from("LICENSE") }
