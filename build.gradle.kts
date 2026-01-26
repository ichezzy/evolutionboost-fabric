plugins {
    id("java")
    id("dev.architectury.loom") version "1.7-SNAPSHOT"  // Bleibt bei 1.7 für Gradle 8.8
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
        // Client 1 - OHNE Mixin debug (verursacht Cobblemon 1.6.1 Crash)
        named("client") {
            client()
            name("Client 1")
            runDir("run/client1")
            // vmArgs ENTFERNT - Mixin debug macht Cobblemon 1.6.1 kaputt
            programArgs("--username", "TestOne", "--uuid", "00000000-0000-0000-0000-000000000001")
        }
        // Client 2
        create("client2") {
            client()
            name("Client 2")
            runDir("run/client2")
            programArgs("--username", "TestTwo", "--uuid", "00000000-0000-0000-0000-000000000002")
        }
        // Dev-Server
        named("server") {
            server()
            name("Dev Server")
            runDir("run/server")
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
    maven("https://api.modrinth.com/maven")
    maven("https://cursemaven.com")
    maven("https://impactdevelopment.github.io/maven/")
    maven("https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/")
    maven("https://maven.impactdev.net/repository/development/")
    maven("https://maven.impactdev.net/repository/maven-releases/")
    maven("https://maven.terraformersmc.com/")
    maven("https://maven.ladysnake.org/releases")
    mavenLocal()
}

dependencies {
    val fabricVersion = "0.104.0+1.21.1"

    // --- Minecraft + Mappings ---
    minecraft("net.minecraft:minecraft:1.21.1")
    mappings(loom.officialMojangMappings())

    // --- Fabric Loader & API ---
    modImplementation("net.fabricmc:fabric-loader:0.16.5")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricVersion")

    // --- Kotlin-Runtime ---
    modImplementation("net.fabricmc:fabric-language-kotlin:1.12.3+kotlin.2.0.21")

    // --- Cobblemon 1.6.1 ---
    modImplementation("maven.modrinth:cobblemon:1.6.1")

    // --- Tests ---
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")

    // --- Trinkets ---
    modCompileOnly("dev.emi:trinkets:3.10.0")
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand(project.properties)
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    from("LICENSE")
}
