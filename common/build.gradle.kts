plugins {
    id("multiloader-base")
    alias(libs.plugins.moddevgradle)
    alias(libs.plugins.mcSafeResources)
}

configureBaseArchive("Common")

neoForge {
    neoFormVersion = libs.versions.neoform.get()
}

dependencies {
    compileOnly(libs.mixin)
    compileOnly(libs.asm.tree)
    compileOnly(libs.forgeconfigapiport.common) // Required in non-Forge like platforms
    compileOnly(libs.freemarker)

    compileOnly("io.github.yesssssman.akythera:common")
}

mcSafeResources {
    namespace.set(modId)
    outputPackage.set("com.yesman.${modId}.generated")
}

java.sourceSets.main.get().java.srcDirs(
    tasks.generateLangKeys.map { it.outputs.files.singleFile },
    tasks.generateSoundKeys.map { it.outputs.files.singleFile }
)

tasks.compileJava { dependsOn(tasks.generateLangKeys, tasks.generateSoundKeys) }
