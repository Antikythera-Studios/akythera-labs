// Shared utilities between all Gradle projects or plugins.

import me.modmuss50.mpp.ModPublishExtension
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.file.RegularFile
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.bundling.Jar
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

// Note: Prefer "strictMaven()" over "maven { ... }" to reduce unnecessary repository lookups (i.e., reduce network calls)
fun RepositoryHandler.strictMaven(name: String, url: String, vararg includeGroups: String) {
    exclusiveContent {
        forRepository { maven { this.name = name; this.url = URI(url) } }
        filter { includeGroups.forEach { includeGroup(it) } }
    }
}

fun RepositoryHandler.parchmentMcRepository() {
    strictMaven(
        "ParchmentMC",
        "https://maven.parchmentmc.org",
        "org.parchmentmc", "org.parchmentmc.data",
    )
}

fun RepositoryHandler.terraformersRepository() {
    strictMaven(
        "Terraformers (Mod Menu)",
        "https://maven.terraformersmc.com",
        "com.terraformersmc",
    )
}

const val generationTaskGroup = "generation"

private val Project.versionCatalog: VersionCatalog
    get() = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

private fun Project.catalogVersion(name: String): String = versionCatalog.findVersion(name).get().toString()

private fun Project.gradleProperty(name: String): String {
    return this.providers.gradleProperty(name).get()
}

val Project.modVersion: String
    get() = catalogVersion("mod")

private val Project.modLicense: String
    get() = gradleProperty("license")

val Project.modId: String
    get() = gradleProperty("mod_id")

val Project.mcVersion: String
    get() = catalogVersion("minecraft")

val Project.parchmentMc: String
    get() = catalogVersion("parchmentMappings")

val Project.javaVersion: Int
    get() = catalogVersion("java").toInt()

private val Project.modIssueTracker: String
    get() = gradleProperty("issue_tracker")

private val Project.modDisplayName: String
    get() = gradleProperty("display_name")

private val Project.modDisplayUrl: String
    get() = gradleProperty("display_url")

private val Project.modSourceCode: String
    get() = gradleProperty("source_code")

private val Project.modDescription: String
    get() = gradleProperty("description")

private val Project.modCredits: String
    get() = gradleProperty("credits")

private val Project.modAuthors: String
    get() = gradleProperty("authors")

val Project.groupId: String
    get() = gradleProperty("group_id")

private val Project.fabricLoaderVersion: String
    get() = catalogVersion("fabric-loader")

private val Project.fabricApiVersion: String
    get() = catalogVersion("fabric-api")

private val Project.neoForgeVersion: String
    get() = catalogVersion("neoforge")

/**
 * Configures the JAR file names to be unique, clear, and consistent.
 */
fun Project.configureBaseArchive(variant: String) {
    extensions.getByType(BasePluginExtension::class.java).apply {
        archivesName.set(modDisplayName)
        version = getFullModVersion(variant)
    }
}

private fun Project.getFullModVersion(variant: String): String = "$variant-mc${mcVersion}-${modVersion}"

private fun Project.getCompactModVersion(variant: String): String = "$variant-${modVersion}"

enum class ModLoader(val conventionalName: String) {
    NeoForge("NeoForge"),
    Fabric("Fabric"),
    ;

    val isForgeLike: Boolean
        get() = this == NeoForge

    val isFabricLike: Boolean
        get() = this == Fabric
}

private fun Project.extractCurrentVersionChangelog(): String? {
    val changelogFile = rootProject.file("CHANGELOG.md")
    if (!changelogFile.exists()) return null
    val fullChangelogText = changelogFile.readText()

    val versionSectionRegex =
        "(?s)## \\[$modVersion\\] - \\d{4}-\\d{2}-\\d{2}\\R(.*?)(?=\\R### For Devs|\\R## \\[.*?\\] |\\Z)"
    val matcher = Regex(versionSectionRegex).find(fullChangelogText) ?: return null

    return matcher.groupValues[1]
}

private fun Project.buildReleaseChangelog(
    currentVersionChangelog: String,
    modLoader: ModLoader,
): String {
    return buildString {
        append(currentVersionChangelog)
        appendLine(); appendLine()

        val modLoaderName = modLoader.name
        val modLoaderVersion: String = when (modLoader) {
            ModLoader.NeoForge -> neoForgeVersion
            ModLoader.Fabric -> fabricLoaderVersion
        }

        append(
            """
    ### **Tested against:**
    - **Minecraft:** $mcVersion
    - **$modLoaderName:** $modLoaderVersion
    """.trimIndent()
        )

        if (modLoader.isFabricLike) {
            appendLine("- **Fabric API:** $fabricApiVersion")
        }
    }
}

/**
 * Configures the mod publishing to mod sites (e.g., Modrinth, CurseForge).
 */
fun Project.configureModPublish(
    modLoader: ModLoader,
    jarFile: () -> Provider<RegularFile>,
) {
    val project = this

    // The JAR is scrubbed of malware-scanner-flagged classes by a finalizer of the JAR tasks (see
    // stripFromNestedJars). Every upload task depends only on `jar`, which would leave the scrub free to run
    // *after* the artifact has already been uploaded — so make all of them wait for it. This covers the real
    // upload tasks ("publishCurseforge", "publishModrinth"), not just the "publishMods" aggregate.
    val stripNestedJars = project.tasks.matching { it.name == stripNestedJarsTaskName }
    project.tasks.matching { it.name.startsWith("publish") }.configureEach { dependsOn(stripNestedJars) }

    project.tasks.named("publishMods") {
        doLast {
            if (extractCurrentVersionChangelog() == null) {
                throw GradleException("Cannot run publishMods task: No changelog found for version $modVersion in CHANGELOG.md file")
            }
        }
    }
    extensions.getByType(ModPublishExtension::class.java).apply {
        val sourcesJar = project.tasks.named("sourcesJar")

        dryRun.set(false)

        val latestChangelog = extractCurrentVersionChangelog() ?: run {
            logger.warn("Skipping the publishMods configuration: No changelog found for version $modVersion in CHANGELOG.md file")
            return@apply
        }
        val releaseChangelog = project.buildReleaseChangelog(latestChangelog, modLoader)
        changelog.set(releaseChangelog)

        modLoaders.add(modLoader.conventionalName)
        val fullVersion = project.getFullModVersion(modLoader.conventionalName);
        displayName.set("$modDisplayName+$fullVersion")
        version.set(project.getCompactModVersion(modLoader.conventionalName))
        type.set(ALPHA) // TODO: change publish type: ALPHA, BETA, STABLE
        file.set(jarFile())
        additionalFiles.from(sourcesJar)

        val requiredDependencies = buildList {
            if (modLoader.isFabricLike) {
                add("fabric-api")
                add("forge-config-api-port")
                //add("akythera")
            }
        }

        val optionalDependencies =
            buildList {
                if (modLoader.isFabricLike) {
                    add("modmenu")
                }
            }

        curseforge {
            accessToken.set(providers.environmentVariable("CURSEFORGE_TOKEN"))
            projectId.set("1594147")
            minecraftVersions.add(mcVersion)
            projectSlug.set("akytheralabs")

            requiredDependencies.forEach { requires(it) }
            optionalDependencies.forEach { optional(it) }
        }

        modrinth {
            accessToken.set(providers.environmentVariable("MODRINTH_TOKEN"))
            projectId.set("AkTWTnIS")
            minecraftVersions.add(mcVersion)

            requiredDependencies.forEach { requires(it) }
            optionalDependencies.forEach { optional(it) }
        }
    }
}

/// Where each mod loader nests its Jar-in-Jar libraries inside the produced mod JAR.
private val nestedJarDirectories = listOf("META-INF/jars/", "META-INF/jarjar/")

const val stripNestedJarsTaskName = "stripNestedJars"

/**
 * Deletes [classEntries] (slash-separated paths, e.g. `freemarker/template/utility/Execute.class`) from every
 * Jar-in-Jar library nested inside the JARs this project produces.
 *
 * Mod distribution sites (CurseForge, Modrinth) run malware scanners over nested JARs and reject uploads that
 * carry known remote-code-execution gadgets, even when the shipping mod never reaches them. Neither Loom's
 * `include` nor ModDevGradle's `jarJar` can filter the *contents* of a nested JAR — they embed the resolved
 * artifact verbatim — so the offending entries are removed from the finished archive instead.
 *
 * Applies to all [Jar] tasks (Loom's `remapJar` and ModDevGradle's `jar` both extend it), and no-ops on the
 * JARs that nest nothing, such as `sourcesJar`.
 */
fun Project.stripFromNestedJars(vararg classEntries: String) {
    val entriesToStrip = classEntries.toSortedSet()
    val archives = objects.listProperty(RegularFile::class.java)

    // A finalizer rather than a "doLast" on the JAR tasks themselves: Loom nests its "include" libraries in an
    // action appended to `jar` after this plugin is applied, so a `doLast` here would run before they even exist.
    // Finalizers are ordering-independent — they run once every finalized task has fully completed.
    val stripTask = tasks.register(stripNestedJarsTaskName) {
        description = "Removes known malware-scanner-flagged classes from Jar-in-Jar libraries."
        dependsOn(tasks.withType(Jar::class.java))

        // Scanning is cheap and stripping is idempotent, so always run: an UP-TO-DATE `jar` must not skip this.
        outputs.upToDateWhen { false }

        // Stripping only matters for artifacts that get distributed.
        onlyIf { !isRunInvocation() }

        doLast {
            archives.get().map { it.asFile }.filter { it.isFile }.forEach { jarFile ->
                stripFromNestedJars(jarFile, entriesToStrip).forEach {
                    logger.lifecycle("Stripped $it from ${jarFile.name}")
                }
            }
        }
    }

    tasks.withType(Jar::class.java).configureEach {
        archives.add(archiveFile)
        finalizedBy(stripTask)
    }
}

/// Matches the run tasks generated by both mod toolchains (Loom's `runClient`/`runServer`, ModDevGradle's
/// `runClient`/`runData`/…): the literal `run` optionally followed by an upper-case-led suffix. Excludes
/// unrelated names such as `runtime…`.
private val runTaskNamePattern = Regex("^run([A-Z].*)?$")

/// True when the current invocation was launched to run the game. Resolves the *root* build (walking past any
/// composite/included-build nesting) and inspects the tasks it was asked to run, so this holds even when the
/// owning project is only an included build whose JAR a run task in the root build consumes and locks.
private fun Task.isRunInvocation(): Boolean {
    val rootGradle = generateSequence(project.gradle) { it.parent }.last()
    return rootGradle.startParameter.taskNames.any { runTaskNamePattern.matches(it.substringAfterLast(":")) }
}

/// Rewrites [jarFile] in place, removing [entriesToStrip] from each nested JAR. Returns a `nested.jar!entry`
/// description of everything removed, empty when nothing matched.
private fun stripFromNestedJars(jarFile: File, entriesToStrip: Set<String>): List<String> {
    if (entriesToStrip.isEmpty() || !jarFile.hasNestedJarEntries(entriesToStrip)) return emptyList()

    val stripped = mutableListOf<String>()
    val rewritten = File(jarFile.parentFile, "${jarFile.name}.strip-tmp")

    ZipOutputStream(rewritten.outputStream().buffered()).use { output ->
        ZipInputStream(jarFile.inputStream().buffered()).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                var content = input.readBytes()

                if (entry.isNestedJar()) {
                    content.withoutEntries(entriesToStrip)?.let { (filtered, removed) ->
                        content = filtered
                        removed.forEach { stripped += "${entry.name}!$it" }
                    }
                }

                output.putNextEntry(entry.copyFor(content))
                output.write(content)
                output.closeEntry()
            }
        }
    }

    if (stripped.isEmpty()) {
        rewritten.delete()
    } else {
        check(jarFile.delete() && rewritten.renameTo(jarFile)) { "Failed to replace $jarFile with its stripped copy" }
    }

    return stripped
}

/// Read-only check for whether any nested JAR still carries one of [entriesToStrip], so the common case — an
/// already-clean archive — costs a scan rather than a full rewrite.
private fun File.hasNestedJarEntries(entriesToStrip: Set<String>): Boolean {
    ZipInputStream(inputStream().buffered()).use { input ->
        while (true) {
            val entry = input.nextEntry ?: break
            if (!entry.isNestedJar()) continue

            ZipInputStream(input.readBytes().inputStream()).use { nested ->
                while (true) {
                    val nestedEntry = nested.nextEntry ?: break
                    if (nestedEntry.name in entriesToStrip) return true
                }
            }
        }
    }

    return false
}

private fun ZipEntry.isNestedJar(): Boolean =
    !isDirectory && name.endsWith(".jar") && nestedJarDirectories.any { name.startsWith(it) }

/// Returns this ZIP's bytes without [entriesToStrip], paired with the names actually removed — or `null` when
/// the archive contained none of them and therefore needs no rewrite.
private fun ByteArray.withoutEntries(entriesToStrip: Set<String>): Pair<ByteArray, List<String>>? {
    val removed = mutableListOf<String>()
    val rewritten = ByteArrayOutputStream(size)

    ZipOutputStream(rewritten).use { output ->
        ZipInputStream(inputStream()).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                val content = input.readBytes()

                if (entry.name in entriesToStrip) {
                    removed += entry.name
                    continue
                }

                output.putNextEntry(entry.copyFor(content))
                output.write(content)
                output.closeEntry()
            }
        }
    }

    return if (removed.isEmpty()) null else rewritten.toByteArray() to removed
}

/// A writable copy of this entry carrying [content]. Preserves the compression method and timestamp so the
/// rewrite stays as close to the original archive as possible; STORED entries need their size and CRC restated
/// because stripping a nested JAR changes both.
private fun ZipEntry.copyFor(content: ByteArray): ZipEntry = ZipEntry(name).also { copy ->
    copy.method = if (method == -1) ZipEntry.DEFLATED else method
    copy.time = time

    if (copy.method == ZipEntry.STORED) {
        copy.size = content.size.toLong()
        copy.compressedSize = content.size.toLong()
        copy.crc = CRC32().apply { update(content) }.value
    }
}

/**
 * The placeholders to be replaced with in mod loader metadata (fabric.mod.json, neoforge.mods.toml).
 */
val Project.modPlatformMetadataReplaceProperties: Map<String, Any>
    get() = mapOf(
        "modId" to modId,
        "modVersion" to modVersion,
        "license" to modLicense,
        "issueTracker" to modIssueTracker,
        "displayName" to modDisplayName,
        "displayUrl" to modDisplayUrl,
        "description" to modDescription,
        "authors" to modAuthors,
        "authorEntries" to modAuthors
            .split(",")
            .joinToString(
                separator = "\", \"",
                prefix = "\"",
                postfix = "\""
            ) { it.trim() },
        "credits" to modCredits,
        "sourceCode" to modSourceCode,
        "minecraft" to mcVersion,
        "neoforgeVersion" to neoForgeVersion,
        "javaVersion" to javaVersion,
        "fabricLoader" to fabricLoaderVersion,
    )
