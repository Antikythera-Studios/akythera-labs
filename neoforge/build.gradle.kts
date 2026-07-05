plugins {
    id("multiloader-platform")
    alias(libs.plugins.moddevgradle)
}

val neoforgeVersion: String = libs.versions.neoforge.get()

configureBaseArchive(ModLoader.NeoForge.name)

configureModPublish(ModLoader.NeoForge) { tasks.jar.get().archiveFile }

neoForge {
    version = neoforgeVersion

    accessTransformers.from("../common/src/main/resources/META-INF/accesstransformer.cfg")

    runs {
        create("client") {
            client()
            systemProperty("neoforge.enabledGameTestNamespaces", modId)
        }

        create("server") {
            server()
            programArgument("--nogui")
            systemProperty("neoforge.enabledGameTestNamespaces", modId)
        }

        create("gameTestServer") {
            type = "gameTestServer"
            systemProperty("neoforge.enabledGameTestNamespaces", modId)
        }

        create("data") {
            clientData()
            programArguments.addAll(
                "--mod", modId,
                "--all",
                "--output", file("src/generated/resources/").absolutePath,
                "--existing", file("src/main/resources/").absolutePath
            )
        }

        configureEach {
            systemProperty("forge.logging.markers", "REGISTRIES")
            logLevel = org.slf4j.event.Level.DEBUG
        }
    }

    mods {
        register(modId) {
            sourceSet(sourceSets.main.get())
        }
    }
}

dependencies {
    implementation("com.yesman.akythera:neoforge")

    implementation(libs.freemarker)

    jarJar(libs.freemarker) {
        version {
            strictly("[2.3.34,3.0.0)")
            prefer("2.3.34")
        }
    }
}

tasks.processResources {
    val replaceProperties = modPlatformMetadataReplaceProperties

    inputs.properties(replaceProperties)

    filesMatching("META-INF/*.mods.toml") {
        expand(replaceProperties)
    }
}
