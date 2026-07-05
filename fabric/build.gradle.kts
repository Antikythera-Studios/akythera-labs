plugins {
    id("multiloader-platform")
    alias(libs.plugins.fabric.loom)
    id("maven-publish")
}

configureBaseArchive("Fabric")

repositories {
    parchmentMcRepository()
    terraformersRepository()
}

dependencies {
    minecraft(libs.fabric.minecraft)
    implementation(libs.fabric.loader)
    implementation(libs.fabric.api)
    implementation(libs.fabric.modmenu)

    implementation("com.yesman.akythera:fabric")

    implementation(libs.forgeconfigapiport.fabric) {
        exclude(group = libs.fabric.loader.get().group)
        exclude(group = libs.fabric.api.get().group)
    }

    implementation(libs.freemarker)
    include(libs.freemarker)
}

loom {
    accessWidenerPath = file("../common/src/main/resources/${modId}.accesswidener")

    mods {
        register(modId) {
            sourceSet(sourceSets.main.get())
        }
    }
}

tasks.processResources {
    val replaceProperties = modPlatformMetadataReplaceProperties

    inputs.properties(replaceProperties)

    filesMatching("fabric.mod.json") {
        expand(replaceProperties)
    }
}

fabricApi {
    configureDataGeneration {
        client = true
    }
}