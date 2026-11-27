// Lmg Client for Minecraft 1.8.9 (Forge). A separate, smaller PvP edition of the 26.3 mod in the folder above.
// Build:  ./gradlew build      ->  build/libs/lmg-client-1.8.9-<version>.jar
plugins {
    java
    id("gg.essential.loom") version "0.10.0.5"
    id("dev.architectury.architectury-pack200") version "0.1.3"
}

val modVersion: String by project
version = modVersion
group = "dev.lmg"
base.archivesName.set("lmg-client-1.8.9")

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(8))
}

loom {
    forge {
        pack200Provider.set(dev.architectury.pack200.java.Pack200Adapter())
    }
    runConfigs {
        named("client") {
            isIdeConfigGenerated = false
        }
    }
}

sourceSets.main {
    output.setResourcesDir(sourceSets.main.flatMap { it.java.classesDirectory })
}

repositories {
    mavenCentral()
    maven("https://repo.spongepowered.org/maven/")
}

dependencies {
    minecraft("com.mojang:minecraft:1.8.9")
    mappings("de.oceanlabs.mcp:mcp_stable:22-1.8.9")
    forge("net.minecraftforge:forge:1.8.9-11.15.1.2318-1.8.9")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("mcmod.info") {
        expand("version" to project.version)
    }
}


// 1.8.9 only runs on Java 8, whatever Java runs Gradle itself
tasks.withType<JavaExec> {
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
}

// "runLegacy": starts 1.8.9 through LaunchWrapper + FMLTweaker (loom's own runClient picks the modern Forge
// launcher here). Uses loom's classpath and the launch.cfg it writes (run runClient once to create it).
tasks.register<JavaExec>("runLegacy") {
    group = "lmg"
    dependsOn("downloadAssets", "classes")
    classpath(sourceSets.main.get().runtimeClasspath)
    mainClass.set("net.fabricmc.devlaunchinjector.Main")
    systemProperty("fabric.dli.main", "net.minecraft.launchwrapper.Launch")
    systemProperty("fabric.dli.config", file(".gradle/loom-cache/launch.cfg").absolutePath)
    systemProperty("fabric.dli.env", "client")
    systemProperty("lmg.test", System.getenv("LMG_TEST") ?: "")
    args("--width", "1280", "--height", "720")
    workingDir = file("run")
    doFirst { workingDir.mkdirs() }
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
}
