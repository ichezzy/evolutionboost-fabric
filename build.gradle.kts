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
        // Client 1
        named("client") {
            client()
            name("Client 1")
            runDir("run/client1")
            vmArgs("-Dmixin.debug=true", "-Dmixin.dumpTargetOnFailure=true")
            // eigener Name/UUID -> parallele Instanz möglich
            programArgs("--username", "TestOne", "--uuid", "00000000-0000-0000-0000-000000000001")
        }
        // Client 2
        create("client2") {
            client()
            name("Client 2")
            runDir("run/client2")
            vmArgs("-Dmixin.debug=true", "-Dmixin.dumpTargetOnFailure=true")
            programArgs("--username", "TestTwo", "--uuid", "00000000-0000-0000-0000-000000000002")
        }
        // Dev-Server
        named("server") {
            server()
            name("Dev Server")
            runDir("run/server")
            vmArgs("-Dmixin.debug=true", "-Dmixin.dumpTargetOnFailure=true")
            // programArgs("--nogui")
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
    maven("https://api.modrinth.com/maven")      // Cobblemon (primär)
    maven("https://cursemaven.com")               // Fallback
    maven("https://impactdevelopment.github.io/maven/")
    maven("https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/")
    maven("https://maven.impactdev.net/repository/development/")
    maven("https://maven.impactdev.net/repository/maven-releases/")
    mavenLocal()
}

dependencies {
    // --- Minecraft + Mappings (WICHTIG: Mojang, kein Yarn) ---
    minecraft("net.minecraft:minecraft:1.21.1")
    mappings(loom.officialMojangMappings())
    modImplementation(fabricApi.module("fabric-lifecycle-events-v1", "0.104.0+1.21.1"))

    // --- Fabric Loader & API ---
    modImplementation("net.fabricmc:fabric-loader:0.16.5")
    modRuntimeOnly("net.fabricmc.fabric-api:fabric-api:0.104.0+1.21.1")
    modImplementation(fabricApi.module("fabric-command-api-v2", "0.104.0+1.21.1"))
    modImplementation(fabricApi.module("fabric-networking-api-v1", "0.104.0+1.21.1"))


    // --- Kotlin-Runtime (Cobblemon benötigt sie) ---
    modImplementation("net.fabricmc:fabric-language-kotlin:1.12.3+kotlin.2.0.21")

    // --- Cobblemon 1.6.1 (Fabric 1.21.1) ---
    // Variante A: Modrinth Maven (empfohlen)
    modImplementation("maven.modrinth:cobblemon:1.6.1")

    // Variante B (Fallback): CurseMaven mit exakter File-ID (auskommentiert lassen, nur nutzen falls A bei dir nicht auflöst)
    // modImplementation("curse.maven:cobblemon-687131:6125079")

    // --- Tests (optional) ---
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") { expand(project.properties) }
}

tasks.test { useJUnitPlatform() }
tasks.jar { from("LICENSE") }
