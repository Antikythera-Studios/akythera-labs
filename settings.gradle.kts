pluginManagement {
    repositories {
        fun strictMaven(name: String, url: String, filter: InclusiveRepositoryContentDescriptor.() -> Unit) {
            exclusiveContent {
                forRepository { maven { this.name = name; this.url = uri(url) } }
                filter { filter() }
            }
        }

        fun strictMaven(name: String, url: String, vararg includeGroups: String) {
            strictMaven(name, url) {
                includeGroups.forEach { includeGroup(it) }
            }
        }

        gradlePluginPortal()

        strictMaven(
            "NeoForged",
            "https://maven.neoforged.net/releases",
            "net.neoforged"
        )
        strictMaven(
            "Fabric",
            "https://maven.fabricmc.net/",
        ) {
            @Suppress("UnstableApiUsage")
            includeGroupAndSubgroups("net.fabricmc")
            includeGroup("fabric-loom")
        }
    }
    includeBuild("gradle/build-logic")
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention").version("1.0.0")
}

rootProject.name = "Akythera Labs"

// Depend on the akythera engine
includeBuild("../akythera_26_1_multiloader")

include(
    ":common",
    ":neoforge",
    ":fabric",
)
