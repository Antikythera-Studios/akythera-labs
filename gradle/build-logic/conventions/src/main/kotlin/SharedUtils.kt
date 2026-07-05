// Shared utilities between all Gradle projects or plugins.

import me.modmuss50.mpp.ModPublishExtension
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.file.RegularFile
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.provider.Provider
import java.net.URI

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
        type.set(STABLE)
        displayName.set(project.getFullModVersion(modLoader.conventionalName))
        file.set(jarFile())
        additionalFiles.from(sourcesJar)
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
