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

// Writes the compile spec AkytheraLabs's in-game hot reload (Tools > Compile Source Code) compiles this platform's
// classes with: the source folders and their patterns, the compile classpath and options, the class and resource
// output folders. Written to build/akytheralabs/hotreload_spec.json after every compileJava.
val writeHotReloadSpec = tasks.register("writeHotReloadSpec") {
    val specFile = layout.buildDirectory.file("akytheralabs/hotreload_spec.json")

    outputs.file(specFile)
    outputs.upToDateWhen { false }

    doLast {
        val compileJava = tasks.compileJava.get()
        val sources = listOf(sourceSets.main.get().java, commonProject.sourceSets.main.get().java).flatMap { set ->
            set.srcDirs.map { dir -> mapOf("dir" to dir.absolutePath, "includes" to set.includes.toList(), "excludes" to set.excludes.toList()) }
        }
        val options = compileJava.options.allCompilerArgs.toMutableList()

        compileJava.options.release.orNull?.let { options += listOf("--release", it.toString()) }
        compileJava.options.encoding?.let { options += listOf("-encoding", it) }
        if (compileJava.options.isDebug) options += compileJava.options.debugOptions.debugLevel?.let { "-g:$it" } ?: "-g"

        val spec = mapOf(
            "namespaces" to listOf(modId),
            "sources" to sources,
            "classpath" to compileJava.classpath.files.map { it.absolutePath },
            "options" to options,
            "output" to compileJava.destinationDirectory.get().asFile.absolutePath,
            "resources" to tasks.processResources.get().destinationDir.absolutePath,
        )
        val file = specFile.get().asFile

        file.parentFile.mkdirs()
        file.writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(spec)))
    }
}

tasks.compileJava { finalizedBy(writeHotReloadSpec) }

// The jar is also AkytheraLabs's Java agent (-javaagent:<this jar>), which hands the in-game hot reload its
// Instrumentation
tasks.jar {
    manifest {
        attributes(
            "Premain-Class" to "com.yesman.akytheralabs.dev.hotreload.HotReloadAgent",
            "Can-Redefine-Classes" to "true",
        )
    }
}
