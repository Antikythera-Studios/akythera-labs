// Configures shared Gradle setup between mod platform Gradle projects (e.g., neoforge, fabric),
// which enables "multiloader-base" and mod publish plugins.
//
// Adds a dependency on ":common", and bundles all classes and
// processed resources to the consumer Gradle project, so end-users won't need additional JAR files.
//
// Consumers of this Gradle plugin are expected to manually call the following methods:
//
// * configureModPublish: with the mod loader and output JAR file, since it's different based on the mod toolchain.
//    This will set up the mod publishing to CurseForge and Modrinth.

plugins {
    id("multiloader-base")
    id("me.modmuss50.mod-publish-plugin")
}

// FreeMarker (bundled via Jar-in-Jar) ships shell and script-interpreter gadgets that the CurseForge and Modrinth
// malware scanners reject on sight, so they are deleted from the nested JAR. Labs can never reach them: templates
// come only from the classpath, and SourceTemplateEngine forbids the "?new" built-in — the sole way to construct
// one — outright. JythonRuntime is doubly dead, as its org.python supertype is not shipped either.
stripFromNestedJars(
    "freemarker/template/utility/Execute.class",
    "freemarker/template/utility/JythonRuntime.class",
    "freemarker/template/utility/JythonRuntime\$1.class",
)

val commonProject: Project = project(":common")

dependencies {
    compileOnly(commonProject)
}

// Includes the "common" project's sources and processed resources in this platform project

tasks.compileJava { source(commonProject.sourceSets.main.get().allSource) }

val commonProcessResources = commonProject.tasks.processResources

tasks.processResources {
    dependsOn(commonProcessResources)
    from(commonProcessResources.map { it.outputs.files })
}
