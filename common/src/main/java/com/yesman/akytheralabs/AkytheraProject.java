package com.yesman.akytheralabs;

import com.google.gson.*;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.asset.animation.AnimationManager;
import com.yesman.akythera.api.asset.armature.ArmatureManager;
import com.yesman.akythera.api.asset.physics.ArmaturePhysicsManager;
import com.yesman.akythera.api.event.BuiltInEventHooks;
import com.yesman.akythera.api.event.registry.RegistryEntryLookupEvent;
import com.yesman.akythera.api.input.InputAction;
import com.yesman.akythera.api.lifecycle.ComponentsFactory;
import com.yesman.akythera.api.world.ability.AbilityEffect;
import com.yesman.akythera.client.api.gui.viewer.EntityModelComponent;
import com.yesman.akythera.client.api.mesh.MeshManager;
import com.yesman.akythera.client.core.asset.loader.MeshLoader;
import com.yesman.akythera.client.core.asset.loader.binary.ClothPhysicsLoader;
import com.yesman.akythera.client.core.asset.mesh.Mesh;
import com.yesman.akythera.client.core.asset.mesh.SkeletalMesh;
import com.yesman.akythera.client.core.asset.mesh.StaticMesh;
import com.yesman.akythera.client.core.asset.physics.ClothPhysicsData;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.animation.keyframe.AnimationClip;
import com.yesman.akythera.core.animation.playable.AnimationMontage;
import com.yesman.akythera.core.animation.playable.AnimationSequence;
import com.yesman.akythera.core.animation.playable.BlendSpace2D;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.loader.AnimationLoader;
import com.yesman.akythera.core.asset.loader.ArmatureLoader;
import com.yesman.akythera.core.asset.loader.AssetLoader;
import com.yesman.akythera.core.asset.loader.ResourceLoadingFailureException;
import com.yesman.akythera.core.asset.loader.json.JsonAssetLoader;
import com.yesman.akythera.core.asset.physics.ArmaturePhysicsData;
import com.yesman.akythera.core.blueprint.AbilityBlueprint;
import com.yesman.akythera.core.blueprint.AbilityCueBlueprint;
import com.yesman.akythera.core.blueprint.EntityPatchBlueprint;
import com.yesman.akythera.core.blueprint.ItemPatchBlueprint;
import com.yesman.akythera.core.blueprint.particle.ParticleBlueprint;
import com.yesman.akythera.core.blueprint.particle.ParticleInitializerScriptBlueprint;
import com.yesman.akythera.core.blueprint.particle.ParticleSystemBlueprint;
import com.yesman.akythera.core.blueprint.particle.ParticleUpdaterScriptBlueprint;
import com.yesman.akythera.core.blueprint.script.DataDrivenAnimatorScript;
import com.yesman.akythera.core.blueprint.widget.WidgetBlueprint;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.EnumerableDataBlock;
import com.yesman.akythera.core.util.EnumerableDataBlockManager;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.akythera.ability.AbilityBlueprintHolder;
import com.yesman.akytheralabs.akythera.ability.AbilityCueBlueprintHolder;
import com.yesman.akytheralabs.akythera.ability.AbilityEffectHolder;
import com.yesman.akytheralabs.akythera.ability.InputActionHolder;
import com.yesman.akytheralabs.akythera.animation.AnimationMontageHolder;
import com.yesman.akytheralabs.akythera.animation.AnimationSequenceHolder;
import com.yesman.akytheralabs.akythera.animation.AnimatorScriptHolder;
import com.yesman.akytheralabs.akythera.animation.BlendSpace2DHolder;
import com.yesman.akytheralabs.akythera.asset.AnimationMetadataHolder;
import com.yesman.akytheralabs.akythera.asset.ArmatureMetadataHolder;
import com.yesman.akytheralabs.akythera.asset.EditorAssetAccessors;
import com.yesman.akytheralabs.akythera.asset.MeshMetadataHolder;
import com.yesman.akytheralabs.akythera.entitypatch.EntityPatchBlueprintHolder;
import com.yesman.akytheralabs.akythera.widget.WidgetBlueprintHolder;
import com.yesman.akytheralabs.akythera.itempatch.ItemPatchBlueprintHolder;
import com.yesman.akytheralabs.akythera.particle.ParticleBlueprintHolder;
import com.yesman.akytheralabs.akythera.particle.ParticleInitializerScriptHolder;
import com.yesman.akytheralabs.akythera.particle.ParticleSystemHolder;
import com.yesman.akytheralabs.akythera.particle.ParticleUpdaterScriptHolder;
import com.yesman.akytheralabs.akythera.physics.ArmaturePhysicsDataHolder;
import com.yesman.akytheralabs.akythera.physics.ClothPhysicsDataHolder;
import com.yesman.akytheralabs.dataholder.*;
import com.yesman.akytheralabs.dataholder.EntityPreviewModel;
import com.yesman.akytheralabs.dataholder.EntityPreviewModelMapping;
import com.yesman.akytheralabs.dataholder.sound.SoundEventHolder;
import com.yesman.akytheralabs.editor.ContentClipboard;
import com.yesman.akytheralabs.editor.historytrack.FileCreation;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.gui.panel.AdjustablePanelManager;
import com.yesman.akytheralabs.gui.screen.MainEditorScreen;
import com.yesman.akytheralabs.gui.widget.AwaitIconModal;
import com.yesman.akytheralabs.gui.widget.main.ProjectSetupModalGenerator;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.core.ClientAsset;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/// A project instance of Akythera data pack & resource pack
@NullMarked
public final class AkytheraProject {
    /// The directory name under the game directory where all projects are stored
    public static final String PROJECTS_DIR_NAME = "akythera_projects";

    /// The file name that marks a directory as a valid Akythera project
    public static final String PROJECT_SETTINGS_FILE = "project_settings.akproject";

    /// The file name that represents a project icon
    public static final String PROJECT_ICON_FILE = "icon.png";

    /// resource manager that reads assets from project directory
    private static final ResourceManager PROJECT_RESOURCE_MANAGER = new ProjectResourceManager();

    /// Reads files out of the active project's `assets` / `data` directories, for the editor code
    /// that has to go through an engine loader.
    public static ResourceManager getProjectResourceManager() {
        return PROJECT_RESOURCE_MANAGER;
    }

    /// Where `assetId`'s sidecar belongs on disk: `assets/<path>.akmeta` under the project its
    /// namespace names — the write-side mirror of the id → file mapping [ProjectResourceManager]
    /// reads through.
    ///
    /// No extension to reverse: an accessor identifier is already trimmed of one, and the sidecar
    /// takes its name from that same trimmed stem.
    public static Path getMetadataFileOf(Identifier assetId) {
        return getProjectsRootDirectory()
            .resolve(assetId.getNamespace())
            .resolve("assets")
            .resolve(assetId.getPath() + AssetLoader.METADATA_EXTENSION);
    }

    /// Where the `.json` asset `assetId` lives on disk: `assets/<path>.json` under the project its
    /// namespace names.
    public static Path getJsonAssetFileOf(Identifier assetId) {
        return getProjectsRootDirectory()
            .resolve(assetId.getNamespace())
            .resolve("assets")
            .resolve(assetId.getPath() + ".json");
    }

    /// Where the binary asset `assetId` lives on disk: `assets/<path>.akasset` under the project its
    /// namespace names.
    public static Path getBinaryAssetFileOf(Identifier assetId) {
        return getProjectsRootDirectory()
            .resolve(assetId.getNamespace())
            .resolve("assets")
            .resolve(assetId.getPath() + "." + AssetLoader.BINARY_EXTENSION);
    }

    /// The armature physics asset belonging to the armature `armatureId`: its own path under
    /// [ArmaturePhysicsManager#DIRECTORY]. Null for an id that is not an armature's.
    public static @Nullable Identifier armaturePhysicsIdOf(Identifier armatureId) {
        if (!armatureId.getPath().startsWith(ArmatureManager.DIRECTORY)) return null;

        return Identifier.fromNamespaceAndPath(
            armatureId.getNamespace(),
            ArmaturePhysicsManager.DIRECTORY + armatureId.getPath().substring(ArmatureManager.DIRECTORY.length())
        );
    }

    /// The armature an armature physics asset belongs to — the inverse of [#armaturePhysicsIdOf].
    /// Null for an id that is not an armature physics asset's.
    public static @Nullable Identifier armatureIdOfPhysics(Identifier physicsId) {
        if (!physicsId.getPath().startsWith(ArmaturePhysicsManager.DIRECTORY)) return null;

        return Identifier.fromNamespaceAndPath(
            physicsId.getNamespace(),
            ArmatureManager.DIRECTORY + physicsId.getPath().substring(ArmaturePhysicsManager.DIRECTORY.length())
        );
    }

    /// `.../walk.akasset` → `.../walk.akmeta`. The path-side twin of [#getMetadataFileOf], for
    /// the file operations that hold a path rather than an identifier.
    public static Path getMetadataFileBeside(Path assetFile) {
        return assetFile.resolveSibling(
            FileSystemHelper.stem(assetFile.getFileName().toString()) + AssetLoader.METADATA_EXTENSION
        );
    }

    /// `.../stone.png` → `.../stone.png.mcmeta`, the sidecar a resource pack reads a texture's
    /// metadata sections from.
    public static Path getMcmetaFileBeside(Path textureFile) {
        return textureFile.resolveSibling(textureFile.getFileName() + PackResources.METADATA_EXTENSION);
    }

    @Nullable
    private static AkytheraProject currentProject = null;

    /// Returns the root directory that contains all Akythera projects.
    public static Path getProjectsRootDirectory() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(PROJECTS_DIR_NAME);
    }

    /// Scans the projects root directory and returns paths of all valid projects
    /// (directories that contain [#PROJECT_SETTINGS_FILE]).
    public static List<Path> discoverProjects() {
        Path root = getProjectsRootDirectory();

        if (!Files.isDirectory(root)) {
            return List.of();
        }

        try (Stream<Path> dirs = Files.list(root)) {
            return dirs
                .filter(Files::isDirectory)
                .filter(dir -> Files.exists(dir.resolve(PROJECT_SETTINGS_FILE)))
                .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /// Starts a new project by creating the directory structure and settings file.
    public static void startNewProject(final String projectName) {
        Path projectRoot = getProjectsRootDirectory().resolve(projectName);
        String initVersion = SemanticVersioning.INITIAL_VERSION;

        try {
            // Root directory
            Files.createDirectories(projectRoot);

            // Construct assets directory
            for (String directory : ASSETS_FOLDERS) {
                Files.createDirectories(projectRoot.resolve("assets/" + directory));
            }

            for (String directory : RESERVED_DIRECTORIES) {
                Files.createDirectories(projectRoot.resolve("assets/" + directory));
            }

            for (ResourceKey<?> datapackKey : DATAPACK_REGISTRIES) {
                Files.createDirectories(projectRoot.resolve("data/" + datapackKey.identifier().getPath()));
            }

            for (DataBlockRegistries.Entry<?, ?> entry : DataBlockRegistries.all()) {
                Files.createDirectories(projectRoot.resolve("data/" + entry.registryKey().identifier().getPath()));
            }

            // Create project_settings.akproject
            String settings = String.format("""
                {
                    "name": "%s",
                    "version": "%s",
                    "focused": "",
                    "folder_states": {},
                    "entity_preview_models": {},
                    "layouts": {}
                }
            """, projectName, initVersion);
            Files.writeString(projectRoot.resolve(PROJECT_SETTINGS_FILE), settings);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create project directory structure for: " + projectName, e);
        }

        currentProject = new AkytheraProject(projectName, initVersion, projectRoot);
        EntityPreviewModelMapping.fillVanillaBindings();
    }

    /// Loads an existing project from the given root path.
    public static AkytheraProject loadProject(final Path projectRoot) {
        Path settingsFile = projectRoot.resolve(PROJECT_SETTINGS_FILE);
        String name = projectRoot.getFileName().toString();
        String version = SemanticVersioning.INITIAL_VERSION;
        String focusedNode = "\\";
        String exportDataPath = "";
        String exportResourcePath = "";
        String sourceDataPath = "";
        String sourceResourcePath = "";
        Map<Class<? extends Screen>, List<Integer>> layouts = new HashMap<>();
        Map<String, Boolean> folderStates = new LinkedHashMap<>();
        Map<String, JsonObject> previewBindings = new LinkedHashMap<>();

        try {
            JsonElement root = JsonParser.parseReader(Files.newBufferedReader(settingsFile));
            JsonObject obj = root.getAsJsonObject();

            if (obj.has("name")) name = obj.get("name").getAsString();
            else if (obj.has("namespace")) name = obj.get("namespace").getAsString();
            if (obj.has("version")) version = obj.get("version").getAsString();
            if (obj.has("focused")) focusedNode = FileSystemHelper.toOsString(obj.get("focused").getAsString());
            if (obj.has("export_data_path")) exportDataPath = obj.get("export_data_path").getAsString();
            if (obj.has("export_resource_path")) exportResourcePath = obj.get("export_resource_path").getAsString();
            if (obj.has("source_data_path")) sourceDataPath = obj.get("source_data_path").getAsString();
            if (obj.has("source_resource_path")) sourceResourcePath = obj.get("source_resource_path").getAsString();

            if (obj.has("layouts")) {
                for (Map.Entry<String, JsonElement> e : obj.getAsJsonObject("layouts").entrySet()) {
                    try {
                        @SuppressWarnings("unchecked")
                        Class<? extends Screen> cls = (Class<? extends Screen>) Class.forName(e.getKey());
                        List<Integer> ratios = new ArrayList<>();
                        e.getValue().getAsJsonArray().forEach(el -> ratios.add(el.getAsInt()));
                        layouts.put(cls, ratios);
                    } catch (ClassNotFoundException ignored) {}
                }
            }

            if (obj.has("folder_states")) {
                for (Map.Entry<String, JsonElement> e : obj.getAsJsonObject("folder_states").entrySet()) {
                    folderStates.put(e.getKey(), e.getValue().getAsBoolean());
                }
            }

            if (obj.has("entity_preview_models")) {
                for (Map.Entry<String, JsonElement> e : obj.getAsJsonObject("entity_preview_models").entrySet()) {
                    previewBindings.put(e.getKey(), e.getValue().getAsJsonObject());
                }
            }
        } catch (IOException ignored) {}

        for (String directory : ASSETS_FOLDERS) {
            try {
                Files.createDirectories(projectRoot.resolve("assets/" + directory));
            } catch (IOException e) {
                AkytheraLabs.LOGGER.error("Failed to create asset directory {}", directory, e);
            }
        }

        for (String directory : RESERVED_DIRECTORIES) {
            try {
                Files.createDirectories(projectRoot.resolve("assets/" + directory));
            } catch (IOException e) {
                AkytheraLabs.LOGGER.error("Failed to create reserved directory {}", directory, e);
            }
        }

        currentProject = new AkytheraProject(name, version, projectRoot, layouts, folderStates, focusedNode);
        currentProject.lastExportDataPath = exportDataPath;
        currentProject.lastExportResourcePath = exportResourcePath;
        currentProject.lastSourceDataPath = sourceDataPath;
        currentProject.lastSourceResourcePath = sourceResourcePath;
        currentProject.loadRegistries();
        currentProject.discoverEditorAssets();
        currentProject.applyPreviewBindings(previewBindings);

        return currentProject;
    }

    void loadRegistries() {
        loadDatapackRegistry(AkytheraRegistries.DataPack.ANIM_MONTAGE_KEY);
        loadDatapackRegistry(AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY);
        loadDatapackRegistry(AkytheraRegistries.DataPack.BLEND_SPACE_2D_KEY);
        // Data blocks must load BEFORE the animator script registry: a script's StateMachineData
        // codec resolves data block ids to project Holders at decode time (see DataBlockHolder#holderCodec).
        loadDataBlocks();
        loadDatapackRegistry(AkytheraRegistries.DataPack.ANIMATOR_SCRIPT_KEY);
        loadDatapackRegistry(AkytheraRegistries.DataPack.ENTITY_PATCH_KEY);
        loadDatapackRegistry(AkytheraRegistries.DataPack.ITEM_PATCH_KEY);
        loadDatapackRegistry(AkytheraRegistries.DataPack.ABILITY_EFFECT_KEY);
        loadDatapackRegistry(AkytheraRegistries.DataPack.INPUT_ACTION_KEY);
        loadDatapackRegistry(AkytheraRegistries.DataPack.ABILITY_CUE_KEY);
        loadDatapackRegistry(AkytheraRegistries.DataPack.ABILITY_KEY);
        loadDatapackRegistry(AkytheraRegistries.DataPack.WIDGET_KEY);
        loadDatapackRegistry(AkytheraRegistries.DataPack.PARTICLE_PIECE_KEY);
        loadDatapackRegistry(AkytheraRegistries.DataPack.PARTICLE_SYSTEM_KEY);
        loadDatapackRegistry(AkytheraRegistries.DataPack.PARTICLE_INITIALIZER_KEY);
        loadDatapackRegistry(AkytheraRegistries.DataPack.PARTICLE_UPDATER_KEY);
    }

    /// Loads every editor-editable [EnumerableDataBlock] type's entries from `data/<registryPath>/*.json`.
    /// Designed after [#loadDatapackRegistry] — data block files are property-less `{}` JSON, so the same
    /// generic loader applies. Each type's registry was created in the constructor.
    void loadDataBlocks() {
        for (DataBlockRegistries.Entry<?, ?> entry : DataBlockRegistries.all()) {
            loadDatapackRegistry(entry.registryKey());
        }
    }

    /// Walks every asset file under `{projectRoot}/assets/<directory>` and
    /// registers an [AssetAccessor] in the corresponding `issuedEditor*Accessors`
    /// map so freshly-loaded projects expose their imported assets to selection
    /// modals without waiting for the content browser to visit each folder.
    ///
    /// Accessors are lazy references; this method does not actually load asset
    /// bytes. Per-file failures (unreadable / malformed paths) are logged and
    /// skipped without blocking project load.
    void discoverEditorAssets() {
        walkAssetFiles(MeshManager.DIRECTORY, file -> {
            try {
                getEditorMeshAccessorByPath(file);
            } catch (Exception e) {
                AkytheraLabs.LOGGER.warn("Failed to register mesh accessor for {}", file, e);
            }
        });

        walkAssetFiles(ArmatureManager.DIRECTORY, file -> {
            try {
                getEditorArmatureAccessorByPath(file);
            } catch (Exception e) {
                AkytheraLabs.LOGGER.warn("Failed to register armature accessor for {}", file, e);
            }
        });

        walkAssetFiles(AnimationManager.DIRECTORY, file -> {
            try {
                getEditorAnimationAccessorByPath(file);
            } catch (Exception e) {
                AkytheraLabs.LOGGER.warn("Failed to register animation accessor for {}", file, e);
            }
        });

        walkAssetFiles(ArmaturePhysicsManager.DIRECTORY, file -> {
            try {
                getEditorArmaturePhysicsAccessorByPath(file);
            } catch (Exception e) {
                AkytheraLabs.LOGGER.warn("Failed to register armature physics accessor for {}", file, e);
            }
        });
    }

    private void walkAssetFiles(String directory, Consumer<Path> handler) {
        Path dir = projectRoot.resolve("assets").resolve(directory);
        FileSystemHelper.forEachFile(dir, p -> {
            String s = p.toString();
            return s.endsWith("." + AssetLoader.BINARY_EXTENSION) || s.endsWith(".json");
        }, handler);
    }

    @SuppressWarnings("unchecked")
    private <T, H extends DataHolder<T>> void loadDatapackRegistry(ResourceKey<? extends Registry<T>> registryKey) {
        ProjectObjectRegistry<T, H> registry = (ProjectObjectRegistry<T, H>) modifiableRegistries.get(registryKey);
        Path dir = projectRoot.resolve("data").resolve(registryKey.identifier().getPath());

        FileSystemHelper.forEachFile(dir, p -> p.toString().endsWith(".json"), file -> {
            String rel = FileSystemHelper.relativeIdPath(dir, file);
            String idPath = rel.substring(0, rel.length() - 5);
            Identifier id = Identifier.fromNamespaceAndPath(projectName, idPath);

            readEntryFile(registry, ResourceKey.create(registryKey, id), file);
        });
    }

    /// Decodes `file` and registers the holder under `key`. A file that can't be read at all still
    /// registers — a default holder marked failed, so the entry shows in the browser with its error.
    private <T, H extends DataHolder<T>> void readEntryFile(
        ProjectObjectRegistry<T, H> registry, ResourceKey<T> key, Path file
    ) {
        try {
            JsonElement json = JsonParser.parseReader(Files.newBufferedReader(file));
            // The registry codec is guarded (see ProjectObjectRegistry): a decode failure
            // yields a default holder marked failed + carrying the raw JSON, so getOrThrow
            // only throws for unreadable / syntactically-broken files.
            H holder = registry.getCodec().parse(JsonOps.INSTANCE, json).getOrThrow();
            registry.register(key, holder);
        } catch (Exception e) {
            AkytheraLabs.LOGGER.error("Failed at loading {}", key, e);
            H holder = registry.provideDefault();
            holder.markFailed(e);
            registry.register(key, holder);
        }
    }

    /// Finish the project without saving, and close editor layout
    public static void endProject() {
        AkytheraEditorLayoutManager.getInstance().closeEditorLayout();
        // Entity preview bindings are project state living in a runtime map — drop them so they
        // don't leak into the next project.
        EntityPreviewModelMapping.clear();
        // The clipboard holds absolute paths of this project's files.
        ContentClipboard.getInstance().clear();
        currentProject = null;
    }

    /// Re-registers [EntityPreviewModelMapping]'s bindings from the saved settings. Clears the
    /// runtime maps first, so reloading the project reverts unsaved mapping edits. Entries whose
    /// entity type, mesh or armature no longer resolves are skipped with a warning.
    private void applyPreviewBindings(Map<String, JsonObject> saved) {
        EntityPreviewModelMapping.clear();

        for (Map.Entry<String, JsonObject> e : saved.entrySet()) {
            Identifier entityId = Identifier.tryParse(e.getKey());
            EntityType<?> entityType = EntityPreviewModelMapping.typeOf(entityId);

            if (entityType == null) {
                AkytheraLabs.LOGGER.warn("Skipping preview binding for {}: unknown entity type", e.getKey());
                continue;
            }

            JsonObject binding = e.getValue();

            if (binding.has("mesh_id") && binding.has("mesh_type")) {
                Identifier meshId = Identifier.tryParse(binding.get("mesh_id").getAsString());
                EntityModelComponent.ModelType meshType = EntityModelComponent.ModelType.CODEC
                    .parse(JsonOps.INSTANCE, binding.get("mesh_type"))
                    .result()
                    .orElse(null);

                if (meshId == null || meshType == null) {
                    AkytheraLabs.LOGGER.warn("Skipping preview mesh for {}: unreadable mesh binding", e.getKey());
                } else {
                    EntityPreviewModelMapping.setMesh(entityType, new EntityPreviewModel(meshId, meshType));
                }
            }

            if (!binding.has("armature")) continue;

            Identifier armatureId = Identifier.tryParse(binding.get("armature").getAsString());

            AssetAccessor<? extends Armature> armature = armatureId == null ? null : listAllArmatureAccessors()
                .filter(a -> a.identifier().equals(armatureId))
                .findFirst()
                .orElse(null);

            if (armature == null) {
                AkytheraLabs.LOGGER.warn("Skipping preview armature for {}: armature not found", e.getKey());
                continue;
            }

            EntityPreviewModelMapping.setArmature(entityType, armature);
        }
    }

    public static AkytheraProject getCurrentProject() {
        if (currentProject == null) {
            throw new IllegalStateException("No Akythera project has loaded.");
        }

        return currentProject;
    }

    public static @Nullable AkytheraProject getCurrentProjectOrNull() {
        return currentProject;
    }

    /// Whether the engine's registry lookups on this thread see the open project's entries.
    private static final ThreadLocal<Boolean> PROJECT_LOOKUPS = ThreadLocal.withInitial(() -> false);

    /// Runs `task` with the engine's registry lookups on this thread seeing the open project's entries.
    public static <T> T withProjectLookups(final Supplier<T> task) {
        boolean previous = PROJECT_LOOKUPS.get();
        PROJECT_LOOKUPS.set(true);

        try {
            return task.get();
        } finally {
            PROJECT_LOOKUPS.set(previous);
        }
    }

    /// Answers the engine's registry entry lookups with the open project's entries, inside [#withProjectLookups].
    public static void registerEvents() {
        BuiltInEventHooks.Registry.LOOKUP_ENTRY_BY_KEY.registerEvent(AkytheraProject::onLookupEntryByKey);
        BuiltInEventHooks.Registry.LOOKUP_ENTRY_BY_VALUE.registerEvent(AkytheraProject::onLookupEntryByValue);
    }

    private static <T> void onLookupEntryByKey(final RegistryEntryLookupEvent.ByKey<T> event) {
        if (currentProject == null || !PROJECT_LOOKUPS.get()) return;

        ProjectObjectRegistry<T, ?> registry = currentProject.getModifiableRegistry(event.getKey().registryKey());

        if (registry != null && registry.containsKey(event.getKey())) {
            event.setResult(registry.projectHolder(event.getKey()));
        }
    }

    private static <T> void onLookupEntryByValue(final RegistryEntryLookupEvent.ByValue<T> event) {
        if (currentProject == null || !PROJECT_LOOKUPS.get()) return;

        ProjectObjectRegistry<T, ?> registry = currentProject.getModifiableRegistry(event.getRegistry());

        if (registry != null) {
            registry.getResourceKey(event.getValue()).ifPresent(event::setResult);
        }
    }

    // ******************* Registry Management *******************

    /// Texture files, laid out the way a resource pack lays out `assets/<namespace>/textures`.
    public static final String TEXTURES_DIRECTORY = "textures/";

    /// GUI sprites, which the game stitches into its GUI atlas instead of loading them as plain
    /// textures. One of [#RESERVED_DIRECTORIES].
    public static final String GUI_SPRITES_DIRECTORY = "textures/gui/sprites/";

    /// Particle sprites, which the game stitches into its particle atlas. One of [#RESERVED_DIRECTORIES].
    public static final String PARTICLE_SPRITES_DIRECTORY = "textures/particle/";

    /// Ogg Vorbis sound files, laid out the way a resource pack lays out `assets/<namespace>/sounds`.
    public static final String SOUNDS_DIRECTORY = "sounds/";

    /// The extension of a sound file under [#SOUNDS_DIRECTORY].
    public static final String SOUND_EXTENSION = ".ogg";

    /// The extension of a sound event under [#SOUNDS_DIRECTORY], one `sounds.json` entry per file.
    public static final String SOUND_EVENT_EXTENSION = AssetLoader.METADATA_EXTENSION;

    /// The file a resource pack lists its sound events in, at `assets/<namespace>/sounds.json`.
    public static final String SOUNDS_JSON = "sounds.json";

    /// Where a resource pack keeps its lang files, under `assets/<namespace>`.
    public static final String LANG_DIRECTORY = "lang/";

    public static final Set<String> ASSETS_FOLDERS = Set.of(
        AnimationManager.DIRECTORY,
        ArmatureManager.DIRECTORY,
        MeshManager.DIRECTORY,
        ArmaturePhysicsManager.DIRECTORY,
        ClothPhysicsLoader.DIRECTORY,
        TEXTURES_DIRECTORY,
        SOUNDS_DIRECTORY
    );

    /// Directories under `assets` every project keeps. Created on project load, and locked against
    /// rename and removal along with every directory on the way to them.
    public static final Set<String> RESERVED_DIRECTORIES = Set.of(
        GUI_SPRITES_DIRECTORY,
        PARTICLE_SPRITES_DIRECTORY
    );

    public static final Set<ResourceKey<? extends Registry<?>>> DATAPACK_REGISTRIES = Set.of(
        AkytheraRegistries.DataPack.ANIM_MONTAGE_KEY,
        AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY,
        AkytheraRegistries.DataPack.BLEND_SPACE_2D_KEY,
        AkytheraRegistries.DataPack.ANIMATOR_SCRIPT_KEY,
        AkytheraRegistries.DataPack.ITEM_PATCH_KEY,
        AkytheraRegistries.DataPack.ENTITY_PATCH_KEY,
        AkytheraRegistries.DataPack.ABILITY_KEY,
        AkytheraRegistries.DataPack.ABILITY_EFFECT_KEY,
        AkytheraRegistries.DataPack.ABILITY_CUE_KEY,
        AkytheraRegistries.DataPack.INPUT_ACTION_KEY,
        AkytheraRegistries.DataPack.WIDGET_KEY,
        AkytheraRegistries.DataPack.PARTICLE_PIECE_KEY,
        AkytheraRegistries.DataPack.PARTICLE_SYSTEM_KEY,
        AkytheraRegistries.DataPack.PARTICLE_INITIALIZER_KEY,
        AkytheraRegistries.DataPack.PARTICLE_UPDATER_KEY
    );

    /// The [#DATAPACK_REGISTRIES] the explorer does not list. Their entries are reached from the editors that use them.
    public static final Set<ResourceKey<? extends Registry<?>>> EXPLORER_HIDDEN_REGISTRIES = Set.of(
        AkytheraRegistries.DataPack.PARTICLE_INITIALIZER_KEY,
        AkytheraRegistries.DataPack.PARTICLE_UPDATER_KEY
    );

    private final Map<ResourceKey<? extends Registry<?>>, ProjectObjectRegistry<?, ?>> modifiableRegistries = new HashMap<>();

    private <T, H extends DataHolder<T>> void createProjectRegistry(
        ResourceKey<? extends Registry<T>> registryKey,
        Supplier<H> defaultDataHolderProvider,
        Codec<H> codec
    ) {
        modifiableRegistries.computeIfAbsent(
            registryKey,
            _ -> new ProjectObjectRegistry<>(registryKey, defaultDataHolderProvider, codec)
        );
    }

    /// Captures one [DataBlockRegistries.Entry]'s `<T, H>` so the registry's value type is the
    /// engine enum type (not a bare `Identifier`). Called once per editable data block type.
    private <T extends EnumerableDataBlock, H extends DataBlockHolder<T>> void createDataBlockRegistry(
        DataBlockRegistries.Entry<T, H> entry
    ) {
        createProjectRegistry(entry.registryKey(), entry.holderFactory(), entry.codec());
    }

    @SuppressWarnings("unchecked")
    public <T, H extends DataHolder<T>> Optional<H> getDataHolder(final ResourceKey<T> resourceKey) {
        ProjectObjectRegistry<T, H> registry = (ProjectObjectRegistry<T, H>) modifiableRegistries.get(resourceKey.registryKey());

        if (registry == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(registry.getDataHolder(resourceKey));
    }

    @SuppressWarnings("unchecked")
    public <T, H extends DataHolder<T>> H getDataHolderOrThrow(final ResourceKey<T> resourceKey) throws NoSuchElementException {
        ProjectObjectRegistry<T, H> registry = (ProjectObjectRegistry<T, H>) modifiableRegistries.get(resourceKey.registryKey());

        if (registry == null) {
            throw new NoSuchElementException("No registry found " + resourceKey.registry() + " inside project " + projectName);
        }

        H dataHolder = registry.getDataHolder(resourceKey);

        if (dataHolder == null) {
            throw new NoSuchElementException("Can't find object from resource key " + resourceKey + ", inside project " + projectName);
        }

        return dataHolder;
    }

    /// Raw [ProjectObjectRegistry] access keyed by registry key.
    @SuppressWarnings("unchecked")
    public <T, H extends DataHolder<T>> @Nullable ProjectObjectRegistry<T, H> getModifiableRegistry(
        ResourceKey<? extends Registry<T>> registryKey
    ) {
        return (ProjectObjectRegistry<T, H>) modifiableRegistries.get(registryKey);
    }

    /// Resolves a registry entry to a live [Holder] across both the current project's registry, as a
    /// [ProjectObjectHolder], and the level's [RegistryAccess], as a built-in or datapack holder.
    /// Null when the id is in neither. `value()` reads the entry's last-saved object.
    public <T> @Nullable Holder<T> getEntryFromProjectAndRegistry(final ResourceKey<T> resourceKey) {
        return getEntryFromProjectAndRegistry(resourceKey.registryKey(), resourceKey.identifier());
    }

    /// See [#getEntryFromProjectAndRegistry(ResourceKey)].
    @SuppressWarnings("unchecked")
    public <T> @Nullable Holder<T> getEntryFromProjectAndRegistry(final ResourceKey<? extends Registry<T>> registryKey, final Identifier identifier) {
        ProjectObjectRegistry<T, ?> registry = (ProjectObjectRegistry<T, ?>) modifiableRegistries.get(registryKey);

        if (registry != null && registry.containsKey(identifier)) {
            return registry.projectHolder(ResourceKey.create(registryKey, identifier));
        }

        return StaticRegistryAccess.getRegistryAccess().get(ResourceKey.create(registryKey, identifier)).orElse(null);
    }

    /// Whether `id` resolves to a live entry of `registryKey`
    public <T> boolean hasEntry(final ResourceKey<? extends Registry<T>> registryKey, final Identifier id) {
        Holder<T> holder = getEntryFromProjectAndRegistry(registryKey, id);
        return holder != null && holder.isBound();
    }

    /// Non-null variant of [#getEntryFromProjectAndRegistry(ResourceKey)]: an id in neither the
    /// project nor the level's `RegistryAccess` resolves to the project registry's dangling shared
    /// [ProjectObjectHolder] placeholder — so reference fields stay non-null (displayed as
    /// `<none>` / dangling) and start tracking the entry if it is created or renamed later.
    public <T> Holder<T> getEntryHolderOrDangling(ResourceKey<T> resourceKey) {
        Holder<T> holder = getEntryFromProjectAndRegistry(resourceKey);
        return holder != null
            ? holder
            : getModifiableRegistry(resourceKey.registryKey()).projectHolder(resourceKey);
    }

    /// Returns all accessible entries including both the current project's entries and the current level's registry access
    ///
    /// A registry the project manages no entries of — a vanilla one a holder references, the
    /// attribute registry say — contributes the level's entries alone.
    @SuppressWarnings("unchecked")
    public <T> Set<Map.Entry<ResourceKey<T>, T>> getMergedEntries(final ResourceKey<? extends Registry<T>> registryKey) {
        ProjectObjectRegistry<T, ?> registry = (ProjectObjectRegistry<T, ?>) modifiableRegistries.get(registryKey);
        Map<ResourceKey<T>, T> entries = new HashMap<>();

        StaticRegistryAccess.getRegistryAccess().lookupOrThrow(registryKey).entrySet().forEach(entry ->
            entries.put(entry.getKey(), entry.getValue())
        );

        if (registry != null) {
            // Overwrite exported project pack
            registry.entrySet().forEach(entry -> entries.put(entry.getKey(), entry.getValue()));
        }

        return new HashSet<>(entries.entrySet());
    }

    /// All [Holder]s for `registryKey` — built-in ([RegistryAccess]) plus editor (the project
    /// registry, as [ProjectObjectHolder]s). Used by pickers that offer both kinds of entry.
    @SuppressWarnings("unchecked")
    public <T> List<Holder<T>> getMergedHolders(final ResourceKey<? extends Registry<T>> registryKey) {
        ProjectObjectRegistry<T, ?> registry = (ProjectObjectRegistry<T, ?>) modifiableRegistries.get(registryKey);
        Map<String, Holder<T>> entries = new HashMap<>();

        StaticRegistryAccess.getRegistryAccess()
            .lookupOrThrow(registryKey).listElements().map(h -> (Holder<T>) h)
            .forEach(holder -> entries.put(holder.getRegisteredName(), holder));

        if (registry != null) {
            // Overwrite exported project pack
            registry.typedRegistryKeySet().stream().map(registry::projectHolder)
                .forEach(holder -> entries.put(holder.getRegisteredName(), holder));
        }

        return entries.values().stream().toList();
    }

    /// Returns only the entries that are created by this project. Empty for a registry the project
    /// manages no entries of.
    @SuppressWarnings("unchecked")
    public <T, H extends DataHolder<T>> Set<Map.Entry<ResourceKey<T>, H>> getModifiableEntries(final ResourceKey<? extends Registry<T>> registryKey) {
        ProjectObjectRegistry<T, H> registry = (ProjectObjectRegistry<T, H>) modifiableRegistries.get(registryKey);
        return registry == null ? Set.of() : registry.dataHolderEntrySet();
    }

    /// Merged identifier listing for one editor-editable data block type: the engine
    /// manager's hardcoded/datapack values **plus** this project's editor-created entries,
    /// de-duplicated and identifier-sorted. This is the "split registry" merge — the
    /// animator script editor offers these for new states / layer nodes and validates
    /// references against this set at compile time (editor-created blocks can be removed
    /// dynamically, so a node may reference one that no longer exists).
    ///
    /// A path-identified block the project declares is listed once, as the project's entry.
    public <T extends EnumerableDataBlock> SortedSet<Identifier> getMergedDataBlockIdentifiers(
        EnumerableDataBlockManager<T> manager
    ) {
        SortedSet<Identifier> ids = new TreeSet<>(Comparator.comparing(Identifier::toString));
        ProjectObjectRegistry<T, ?> registry = getModifiableRegistry(manager.directoryKey());
        if (registry != null) ids.addAll(registry.keySet());

        for (T value : manager.universalValues()) {
            Identifier id = value.identifier();
            if (registry == null || !registry.containsKey(projectDataBlockId(manager, id))) ids.add(id);
        }

        if (registry != null && manager.isPathIdentified()) {
            Set<String> registeredPaths = new HashSet<>();
            ids.forEach(id -> registeredPaths.add(id.getPath()));

            for (Identifier key : registry.keySet()) {
                String path = key.getPath();

                for (int cut = path.lastIndexOf('/'); cut > 0; cut = path.lastIndexOf('/', cut - 1)) {
                    String parent = path.substring(0, cut);
                    if (registeredPaths.add(parent)) ids.add(Identifier.fromNamespaceAndPath(projectName, parent));
                }
            }
        }

        return ids;
    }

    /// Blocks standing for the parent paths of this project's path-identified entries, by id, see
    /// [#getDataBlockHolder]. One per id, so every reference to a parent holds the same object.
    private final Map<Identifier, EnumerableDataBlock> impliedDataBlocks = new HashMap<>();

    /// Resolve one editable data block to a live [Holder], **project &cup; manager** (NOT
    /// `StaticRegistryAccess` — data blocks live in the [EnumerableDataBlockManager], not in a
    /// `RegistryAccess` registry). Editor-created (labs) entries return a [ProjectObjectHolder]
    /// (its `value()` reads the project registry's object and follows renames); engine/hardcoded
    /// values return a direct holder (immutable). `null` if the id resolves in neither.
    public <T extends EnumerableDataBlock> @Nullable Holder<T> getDataBlockHolder(
        EnumerableDataBlockManager<T> manager, Identifier id
    ) {
        ProjectObjectRegistry<T, ?> registry = getModifiableRegistry(manager.directoryKey());
        Identifier projectId = projectDataBlockId(manager, id);

        if (registry != null && registry.containsKey(projectId)) {
            return registry.projectHolder(ResourceKey.create(manager.directoryKey(), projectId));
        }

        Optional<T> registered = manager.get(id);
        if (registered.isPresent()) return Holder.direct(registered.get());

        // A path-identified entry registers its parent paths too, which the engine does only once the
        // exported pack loads.
        DataBlockRegistries.Entry<?, ?> entry = DataBlockRegistries.entryOf(manager.directoryKey());

        if (entry == null || registry == null || !manager.isPathIdentified()
            || registry.keySet().stream().noneMatch(key -> key.getPath().startsWith(id.getPath() + "/"))) {
            return null;
        }

        @SuppressWarnings("unchecked")
        T implied = (T) impliedDataBlocks.computeIfAbsent(projectId, _ -> {
            DataBlockHolder<T> holder = (DataBlockHolder<T>) entry.holderFactory().get();
            holder.setOwnResourceKey(ResourceKey.create(manager.directoryKey(), projectId));

            return holder.makeObject();
        });

        return Holder.direct(implied);
    }

    /// The id this project keys `id` by: moved into the project namespace for a path-identified
    /// block, unchanged otherwise.
    private Identifier projectDataBlockId(EnumerableDataBlockManager<?> manager, Identifier id) {
        return manager.isPathIdentified() ? Identifier.fromNamespaceAndPath(projectName, id.getPath()) : id;
    }

    /// The first tag path that moving `from` onto `to` would break the path-identified data block rule
    /// with, or null when it breaks none. Each is a file or a directory; `keepsSource` is set for a
    /// copy, whose source files stay where they are.
    ///
    /// See [EnumerableDataBlockManager#isPathIdentified] for the rule, which [#isTagPathRefused] tests.
    public @Nullable String refusedTagPath(Path from, Path to, boolean keepsSource) {
        for (DataBlockRegistries.Entry<?, ?> entry : DataBlockRegistries.all()) {
            Path registryDir = projectRoot.resolve("data").resolve(entry.registryKey().identifier().getPath());
            if (!entry.manager().isPathIdentified() || !to.startsWith(registryDir)) continue;

            List<Path> files = new ArrayList<>();

            if (Files.isDirectory(from)) {
                FileSystemHelper.forEachFile(from, file -> file.toString().endsWith(".json"), files::add);
            } else {
                files.add(from);
            }

            Set<String> leaving = new HashSet<>();

            if (!keepsSource && from.startsWith(registryDir)) {
                for (Path file : files) {
                    String rel = FileSystemHelper.relativeIdPath(registryDir, file);
                    leaving.add(rel.substring(0, rel.length() - ".json".length()));
                }
            }

            for (Path file : files) {
                String rel = FileSystemHelper.relativeIdPath(registryDir, to.resolve(from.relativize(file)));
                String idPath = rel.substring(0, rel.length() - ".json".length());

                if (isTagPathRefused(entry.registryKey(), idPath, leaving)) return idPath;
            }

            return null;
        }

        return null;
    }

    /// Whether `idPath` may not become an entry of the path-identified data block `registryKey`: a mod
    /// or a loaded data pack registers it already, it is the parent of a project entry, or it sits
    /// under a terminal. Project entries in `leaving` are ignored. False for any other registry.
    public boolean isTagPathRefused(ResourceKey<? extends Registry<?>> registryKey, String idPath, Set<String> leaving) {
        DataBlockRegistries.Entry<?, ?> entry = DataBlockRegistries.entryOf(registryKey);
        if (entry == null || !entry.manager().isPathIdentified()) return false;

        int slash = idPath.lastIndexOf('/');

        return entry.manager().get(Identifier.fromNamespaceAndPath(projectName, idPath)).isPresent()
            || projectEntryPaths(registryKey, leaving).stream().anyMatch(path -> path.startsWith(idPath + "/"))
            || isTagParentRefused(registryKey, slash < 0 ? "" : idPath.substring(0, slash), leaving);
    }

    /// Whether no entry of the path-identified data block `registryKey` may go under `parentPath`,
    /// since it or one of its ancestors is a terminal: a project entry, or a registered entry with
    /// nothing registered beneath it. Project entries in `leaving` are ignored. False for any other
    /// registry.
    public boolean isTagParentRefused(ResourceKey<? extends Registry<?>> registryKey, String parentPath, Set<String> leaving) {
        DataBlockRegistries.Entry<?, ?> entry = DataBlockRegistries.entryOf(registryKey);
        if (entry == null || !entry.manager().isPathIdentified()) return false;

        Set<String> projectPaths = projectEntryPaths(registryKey, leaving);
        Set<String> registeredPaths = new HashSet<>();
        entry.manager().universalValues().forEach(value -> registeredPaths.add(value.identifier().getPath()));

        for (String path = parentPath; !path.isEmpty(); path = path.substring(0, Math.max(path.lastIndexOf('/'), 0))) {
            String ancestor = path;

            if (projectPaths.contains(ancestor)) return true;

            if (registeredPaths.contains(ancestor)
                && registeredPaths.stream().noneMatch(registered -> registered.startsWith(ancestor + "/"))) {
                return true;
            }
        }

        return false;
    }

    /// The id paths of this project's entries in `registryKey`, except those in `leaving`.
    private Set<String> projectEntryPaths(ResourceKey<? extends Registry<?>> registryKey, Set<String> leaving) {
        Set<String> paths = new HashSet<>();
        ProjectObjectRegistry<?, ?> registry = modifiableRegistries.get(registryKey);

        if (registry != null) {
            for (Identifier id : registry.keySet()) {
                if (!leaving.contains(id.getPath())) paths.add(id.getPath());
            }
        }

        return paths;
    }

    /// Non-null variant of [#getDataBlockHolder]: an id in neither the project nor the manager
    /// resolves to the labs registry's dangling shared [ProjectObjectHolder] (so it starts
    /// tracking the block if it is created/renamed later), or a [DanglingHolder] when the manager
    /// has no labs registry. Either way the id round-trips on save and referrers can mark
    /// `!isBound()` red instead of failing to decode.
    public <T extends EnumerableDataBlock> Holder<T> getDataBlockHolderOrDangling(
        EnumerableDataBlockManager<T> manager, Identifier id
    ) {
        Holder<T> holder = getDataBlockHolder(manager, id);
        if (holder != null) return holder;

        ResourceKey<T> key = ResourceKey.create(manager.directoryKey(), id);
        ProjectObjectRegistry<T, ?> registry = getModifiableRegistry(manager.directoryKey());
        return registry != null ? registry.projectHolder(key) : new DanglingHolder<>(key);
    }

    /// Re-save every entry whose in-memory model still references the just-renamed entry, so disk
    /// matches memory (the rekeyed [ProjectObjectHolder] is already correct in memory; only the
    /// referrers' on-disk JSON lags).
    private <T, H extends DataHolder<T>> void resaveReferrersOf(
        ProjectObjectRegistry<T, H> registry, ResourceKey<T> renamedKey
    ) {
        resaveReferrers(registry.projectHolder(renamedKey));
    }

    /// Re-saves every entry that references `renamed` — a [ProjectObjectHolder] for a renamed data
    /// entry, or an editor `AssetAccessor` for a renamed asset. Every registry is scanned, matching
    /// by identity against each holder's [DataHolder#referencedReferences].
    ///
    /// The rename is already correct in memory, the shared instance having been mutated; this is
    /// what makes it correct on disk. The scan runs on the caller's thread and the writes behind an
    /// await modal — a rename can touch many files.
    private void resaveReferrers(EditorReference renamed) {
        List<ResourceKey<?>> referrers = new ArrayList<>();

        for (ProjectObjectRegistry<?, ?> reg : modifiableRegistries.values()) {
            collectReferrersIn(reg, renamed, referrers);
        }

        if (referrers.isEmpty()) return;

        // Same split as saveEntryAsync: re-compile each referrer's cached object on the calling
        // thread (that is what reads the registries), then write the files off it.
        referrers.forEach(this::refreshEntry);
        AwaitIconModal.runSaving(() -> referrers.forEach(this::writeEntry));
    }

    private <T, H extends DataHolder<T>> void collectReferrersIn(
        ProjectObjectRegistry<T, H> registry, EditorReference renamed, List<ResourceKey<?>> out
    ) {
        for (Map.Entry<ResourceKey<T>, H> e : registry.dataHolderEntrySet()) {
            if (e.getValue().referencedReferences().anyMatch(r -> r == renamed)) out.add(e.getKey());
        }
    }

    /// Re-runs an entry's `makeObject()` and swaps the fresh object into its registry, so every
    /// [ProjectObjectHolder] for that key returns the updated object. Call after a property edit of
    /// a holder whose referrers hold it by [ProjectObjectHolder].
    public <T> void refreshEntry(ResourceKey<T> key) {
        ProjectObjectRegistry<T, ?> registry = getModifiableRegistry(key.registryKey());
        if (registry != null) registry.refreshObject(key);
    }

    /// Finds an unused entry name in the given registry by appending `_N` to
    /// `baseName` until no existing entry matches.
    ///
    /// The returned value is just the leaf name (without `subPath`); callers
    /// prepend `subPath` themselves when building the full id path.
    public <T> String findNextFreeName(
        ResourceKey<? extends Registry<T>> key,
        String baseName,
        String subPath
    ) {
        Set<String> existingPaths = getModifiableEntries(key).stream()
            .map(e -> e.getKey().identifier().getPath())
            .collect(Collectors.toSet());

        Predicate<String> taken = path -> existingPaths.contains(path) || isTagPathRefused(key, path, Set.of());
        String candidate = subPath.isEmpty() ? baseName : subPath + "/" + baseName;
        if (!taken.test(candidate)) return baseName;

        int i = 1;
        while (taken.test(candidate + "_" + i)) i++;

        return baseName + "_" + i;
    }

    /// Allocates a new registry entry under `idPath`. Under `shouldSave` it also writes the default
    /// holder to disk and pushes a [FileCreation] onto the history stack.
    ///
    /// That `FileCreation` captures the freshly-registered holder instance, so a redo re-registers
    /// the same in-memory holder rather than minting a fresh default through `provideDefault`. Edits
    /// the caller makes to the holder afterwards therefore survive undo / redo cycles.
    @SuppressWarnings("unchecked")
    public <T, H extends DataHolder<T>> ResourceKey<T> createEntry(
        ResourceKey<? extends Registry<T>> registryKey,
        String idPath,
        boolean shouldSave
    ) {
        Identifier id = Identifier.fromNamespaceAndPath(projectName, idPath);
        ResourceKey<T> key = ResourceKey.create(registryKey, id);
        ProjectObjectRegistry<T, H> registry = (ProjectObjectRegistry<T, H>) modifiableRegistries.get(registryKey);
        final H holder = registry.provideDefault();
        registry.register(key, holder);

        if (shouldSave) {
            saveEntry(key);

            Path filePath = getEntryFilePath(key);

            HistoryManager.getInstance().pushHistory(new FileCreation(
                () -> {
                    reinsertEntry(key, holder);
                    saveEntry(key);

                    AkytheraEditorLayoutManager.getInstance().<MainEditorScreen>ifLastScreen(s -> {
                        Path lastPath = s.explorerPanel.getSelected() == null ? null : s.explorerPanel.getSelected().directoryPath;
                        s.explorerPanel.rebuildTree();
                        s.explorerPanel.focusNodeByPath(lastPath);
                        s.contentBrowserPanel.pickSelections(true, filePath);
                    });
                },
                () -> {
                    deletePathRecursive(filePath);

                    AkytheraEditorLayoutManager.getInstance().<MainEditorScreen>ifLastScreen(s -> {
                        Path lastPath = s.explorerPanel.getSelected() == null ? null : s.explorerPanel.getSelected().directoryPath;
                        s.explorerPanel.rebuildTree();
                        s.explorerPanel.focusNodeByPath(lastPath);
                    });
                }
            ));
        }

        return key;
    }

    /// Re-register `holder` under `key` without creating a fresh default
    /// instance. Used by undo/redo of file creation, where the same
    /// in-memory holder (with its inbound `references` list intact) needs
    /// to come back into the registry after [#deletePathRecursive] removed
    /// it.
    ///
    /// Safe to call when the holder is already registered (the by-key / by-id maps just get rewritten with
    /// the same value). [AbstractDataHolder#setOwnResourceKey] only fires `onOwnResourceKeyAssigned` on first
    /// assignment, so re-insert doesn't double-bind outbound references.
    @SuppressWarnings("unchecked")
    public <T, H extends DataHolder<T>> void reinsertEntry(ResourceKey<T> key, H holder) {
        ProjectObjectRegistry<T, H> registry = (ProjectObjectRegistry<T, H>) modifiableRegistries.get(key.registryKey());
        if (registry == null) return;
        registry.register(key, holder);
    }

    /// On-disk file path for a data-entry [ResourceKey].
    public <T> Path getEntryFilePath(ResourceKey<T> key) {
        String idPath = key.identifier().getPath();

        return projectRoot
            .resolve("data")
            .resolve(key.registryKey().identifier().getPath())
            .resolve(FileSystemHelper.toOsString(idPath) + ".json");
    }

    public <T> void saveEntry(ResourceKey<T> key) {
        writeEntry(key);

        // Sync the registry's cached object with the just-saved holder, so every ProjectObjectHolder
        // referencing this entry returns its current (last-saved) object — the save-time refresh that
        // lets references be plain cached ProjectObjectHolders instead of per-call rebuilds.
        refreshEntry(key);
    }

    /// Every entry this project holds that is broken by [DataHolder#getFailureException]: it failed
    /// to load, it carries salvaged nested content — an unresolved graph node, a dangling asset id in
    /// an Anim Graph, a broken notify row — or its runtime object cannot be built.
    ///
    /// [#collectDataPackEntries] skips these entries: re-encoding one would emit defaults for content
    /// the editor could not read, or throw mid-export inside `makeObject` / `getDTO`. The user
    /// confirms them before any export or world reload.
    public List<ResourceKey<?>> findBrokenEntries() {
        return findBrokenEntries(_ -> true);
    }

    /// The broken entries whose key passes `filter` — the gate a per-category or per-selection
    /// compile runs, so one category's broken file can't block another's.
    public List<ResourceKey<?>> findBrokenEntries(Predicate<ResourceKey<?>> filter) {
        List<ResourceKey<?>> broken = new ArrayList<>();

        for (ProjectObjectRegistry<?, ?> registry : modifiableRegistries.values()) {
            for (Map.Entry<? extends ResourceKey<?>, ? extends DataHolder<?>> e : registry.dataHolderEntrySet()) {
                if (e.getValue().getFailureException() != null && filter.test(e.getKey())) broken.add(e.getKey());
            }
        }

        return broken;
    }

    /// A data entry's file path relative to the project root, slash-separated — e.g.
    /// `data/script/animator/player.json`. The form entries are named to the user in the
    /// broken-entries warning; mirrors [#getEntryFilePath], which owns the on-disk layout.
    public String getRelativeEntryPath(ResourceKey<?> key) {
        return projectRoot.relativize(getEntryFilePath(key)).toString().replace('\\', '/');
    }

    /// Minimum on-screen time of the save await modal, in milliseconds. See [#saveEntryAsync].
    private static final long MIN_AWAIT_MILLIS = 100;

    /// Asynchronous counterpart to [#saveEntry]. Resolves the holder, codec and path on the calling (render)
    /// thread, then encodes and writes the JSON on a background thread so a slow disk write doesn't stall the
    /// editor's render loop. Callers pair this with an await modal (see [AwaitIconModal]) and close it on
    /// completion.
    ///
    /// Safe to encode off-thread because the await modal blocks input — the holder can't be mutated
    /// while the write is in flight. Returns a future that completes once the file is written, or an
    /// already-completed one when the entry can't be resolved.
    @SuppressWarnings("unchecked")
    public <T> CompletableFuture<Void> saveEntryAsync(ResourceKey<T> key) {
        ResourceKey<? extends Registry<T>> regKey = key.registryKey();
        ProjectObjectRegistry<T, DataHolder<T>> registry = (ProjectObjectRegistry<T, @NonNull DataHolder<T>>) modifiableRegistries.get(regKey);
        if (registry == null) return CompletableFuture.completedFuture(null);

        Codec<DataHolder<T>> codec = registry.getCodec();
        DataHolder<T> holder = registry.getDataHolder(key);
        if (holder == null) return CompletableFuture.completedFuture(null);

        // Same refusal as saveEntry: no retained original to re-emit, so writing would wipe the file.
        if (holder.getDeserializationFailureException() != null && holder.retainedRaw() == null) {
            AkytheraLabs.LOGGER.error("Refusing to save {} — it failed to load and no original content was retained", key.identifier());
            return CompletableFuture.completedFuture(null);
        }

        // Sync the cached object on the calling thread (the await modal blocks edits during the write).
        registry.refreshObject(key);

        Path path = getEntryFilePath(key);
        CompletableFuture<Void> write = CompletableFuture.runAsync(() -> FileSystemHelper.writeJson(path, codec, holder));

        // Floor the perceived duration so the await modal doesn't just flash: these files write in
        // sub-millisecond time, leaving the spinner on screen for a single frame. Gate completion on
        // both the write AND a minimum delay so the modal stays up for at least [#MIN_AWAIT_MILLIS].
        CompletableFuture<Void> minDelay = CompletableFuture.runAsync(
            () -> {}, CompletableFuture.delayedExecutor(MIN_AWAIT_MILLIS, TimeUnit.MILLISECONDS));

        return CompletableFuture.allOf(write, minDelay);
    }

    /// Discard a data entry's in-memory edits by re-reading its on-disk JSON: decode a fresh holder
    /// via the registry codec and swap it in (keeping the shared [ProjectObjectHolder], so referrers
    /// stay wired). Used by the editor "Quit" path so abandoning a screen rolls the holder back to its
    /// last-saved state — the in-memory equivalent of reopening the project. No-op if the file is gone.
    @SuppressWarnings("unchecked")
    public <T> void reloadEntryFromDisk(ResourceKey<T> key) {
        ResourceKey<? extends Registry<T>> regKey = key.registryKey();
        ProjectObjectRegistry<T, DataHolder<T>> registry =
            (ProjectObjectRegistry<T, @NonNull DataHolder<T>>) modifiableRegistries.get(regKey);
        if (registry == null) return;

        Path file = getEntryFilePath(key);
        if (!Files.exists(file)) return;

        try {
            JsonElement json = JsonParser.parseReader(Files.newBufferedReader(file));
            DataHolder<T> fresh = registry.getCodec().parse(JsonOps.INSTANCE, json).getOrThrow();
            registry.replaceDataHolder(key, fresh);
        } catch (Exception e) {
            AkytheraLabs.LOGGER.error("Failed to reload {} from disk", key, e);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> @Nullable ResourceKey<T> renameEntry(ResourceKey<T> oldKey, String newName) {
        ResourceKey<? extends Registry<T>> regKey = oldKey.registryKey();
        ProjectObjectRegistry<T, DataHolder<T>> registry = (ProjectObjectRegistry<T, @NonNull DataHolder<T>>) modifiableRegistries.get(regKey);
        if (registry == null) return null;

        DataHolder<T> holder = registry.getDataHolder(oldKey);
        if (holder == null) return null;

        String oldIdPath = oldKey.identifier().getPath();
        int lastSlash = oldIdPath.lastIndexOf('/');
        String newIdPath = lastSlash >= 0
            ? oldIdPath.substring(0, lastSlash + 1) + newName
            : newName;

        Identifier newId = Identifier.fromNamespaceAndPath(oldKey.identifier().getNamespace(), newIdPath);
        ResourceKey<T> newKey = ResourceKey.create(regKey, newId);

        // Rename the holder in place: preserves the object + the shared ProjectObjectHolder
        // instance, so referrers stay correct in memory (see ProjectObjectRegistry#rekey).
        registry.rekey(oldKey, newKey);

        Path registryDir = projectRoot.resolve("data").resolve(regKey.identifier().getPath());
        Path oldFile = registryDir.resolve(FileSystemHelper.toOsString(oldIdPath) + ".json");
        Path newFile = registryDir.resolve(FileSystemHelper.toOsString(newIdPath) + ".json");

        if (!FileSystemHelper.moveReplace(oldFile, newFile)) {
            registry.rekey(newKey, oldKey);
            return null;
        }

        // Re-save every entry that references this one so disk matches the in-memory rekey.
        resaveReferrersOf(registry, newKey);

        return newKey;
    }

    /// Move a data entry to a new on-disk location (folder drag-drop in
    /// the content browser). Same shape as [#renameEntry] but driven
    /// by absolute paths instead of a new leaf name.
    @SuppressWarnings("unchecked")
    public <T> @Nullable ResourceKey<T> moveEntry(Path oldPath, Path newPath) {
        Path dataRoot = projectRoot.resolve("data");
        if (!oldPath.startsWith(dataRoot) || !newPath.startsWith(dataRoot)) return null;

        // Identify which registry owns the file by matching its
        // sub-directory under `data/`.
        ProjectObjectRegistry<T, DataHolder<T>> registry = null;
        ResourceKey<? extends Registry<T>> regKey = null;

        for (var entry : modifiableRegistries.entrySet()) {
            Path regDir = dataRoot.resolve(entry.getKey().identifier().getPath());

            if (oldPath.startsWith(regDir)) {
                regKey = (ResourceKey<? extends Registry<T>>) entry.getKey();
                registry = (ProjectObjectRegistry<T, DataHolder<T>>) entry.getValue();
                break;
            }
        }

        if (registry == null) return null;

        Path regDir = dataRoot.resolve(regKey.identifier().getPath());
        String oldRel = FileSystemHelper.relativeIdPath(regDir, oldPath);
        String newRel = FileSystemHelper.relativeIdPath(regDir, newPath);
        if (!oldRel.endsWith(".json") || !newRel.endsWith(".json")) return null;

        String oldIdPath = oldRel.substring(0, oldRel.length() - ".json".length());
        String newIdPath = newRel.substring(0, newRel.length() - ".json".length());

        Identifier oldId = Identifier.fromNamespaceAndPath(projectName, oldIdPath);
        Identifier newId = Identifier.fromNamespaceAndPath(projectName, newIdPath);
        if (oldId.equals(newId)) return ResourceKey.create(regKey, oldId);

        ResourceKey<T> oldKey = ResourceKey.create(regKey, oldId);
        ResourceKey<T> newKey = ResourceKey.create(regKey, newId);

        DataHolder<T> holder = registry.getDataHolder(oldKey);
        if (holder == null) return null;

        registry.rekey(oldKey, newKey);

        // On-disk file is already moved by the caller (matches the
        // asset path which does the same — `Files.move` first, then
        // `renameAsset`).
        resaveReferrersOf(registry, newKey);

        return newKey;
    }

    /// Renames a project subdirectory: moves the directory on disk, then walks the moved tree and
    /// dispatches each regular file to its in-memory re-key — [#moveEntry] for a data entry,
    /// [#renameAsset] for an asset file. Returns `false` when the on-disk move failed.
    public boolean renameDirectory(Path oldDir, Path newDir) {
        if (!FileSystemHelper.move(oldDir, newDir)) return false;

        Path dataRoot = projectRoot.resolve("data");
        Path assetsRoot = projectRoot.resolve("assets");

        FileSystemHelper.forEachFile(newDir, newFile -> {
            Path rel = newDir.relativize(newFile);
            Path oldFile = oldDir.resolve(rel);

            if (newFile.startsWith(dataRoot)) {
                moveEntry(oldFile, newFile);
            } else if (newFile.startsWith(assetsRoot)) {
                renameAsset(oldFile, newFile);
            }
        });

        return true;
    }

    /// Whether `directory` is one of [#RESERVED_DIRECTORIES], or a directory on the way to one.
    public boolean isReservedDirectory(Path directory) {
        Path assetsRoot = projectRoot.resolve("assets");

        if (!directory.startsWith(assetsRoot)) return false;

        String relativePath = FileSystemHelper.relativeIdPath(assetsRoot, directory) + "/";

        return RESERVED_DIRECTORIES.stream().anyMatch(reserved -> reserved.startsWith(relativePath));
    }

    /// Whether `textureFile` lies under [#GUI_SPRITES_DIRECTORY].
    public boolean isGuiSprite(Path textureFile) {
        return textureFile.startsWith(projectRoot.resolve("assets").resolve(GUI_SPRITES_DIRECTORY));
    }

    /// The id `textureFile` is exported under, `<projectName>:textures/….png`, or null when its path is
    /// no valid id path.
    public @Nullable Identifier getTextureIdentifier(Path textureFile) {
        return Identifier.tryBuild(projectName, FileSystemHelper.relativeIdPath(projectRoot.resolve("assets"), textureFile));
    }

    /// The id of the sound file or sound event `file` under [#SOUNDS_DIRECTORY]:
    /// `<projectName>:<path under sounds/>` with the extension trimmed, or null when its path is no valid
    /// id path. A sound file's id is how a sound entry names it, see [Sound#getLocation].
    public @Nullable Identifier getSoundId(Path file) {
        String relativePath = FileSystemHelper.relativeIdPath(projectRoot.resolve("assets").resolve(SOUNDS_DIRECTORY), file);

        return Identifier.tryBuild(projectName, FileSystemHelper.stem(relativePath));
    }

    /// The file defining the sound event `eventId`, the inverse of [#getSoundId]. Null for an id of
    /// another namespace. The file need not exist.
    public @Nullable Path getSoundEventFile(Identifier eventId) {
        if (!eventId.getNamespace().equals(projectName)) return null;

        return projectRoot.resolve("assets")
            .resolve(SOUNDS_DIRECTORY)
            .resolve(FileSystemHelper.toOsString(eventId.getPath() + SOUND_EVENT_EXTENSION));
    }

    /// Whether `file` lies under [#SOUNDS_DIRECTORY], where an `.akmeta` is a sound event of its own
    /// rather than another file's sidecar.
    public boolean isSoundAsset(Path file) {
        return file.startsWith(projectRoot.resolve("assets").resolve(SOUNDS_DIRECTORY));
    }

    /// Whether `file` is a sound event, see [SoundEventHolder].
    public boolean isSoundEvent(Path file) {
        return isSoundAsset(file) && file.toString().endsWith(SOUND_EVENT_EXTENSION);
    }

    /// The project sound files the `file` entries of the sound event `eventFile` name. A named file need
    /// not exist.
    public List<Path> soundFilesOf(Path eventFile) {
        Path soundsRoot = projectRoot.resolve("assets").resolve(SOUNDS_DIRECTORY);
        List<Path> soundFiles = new ArrayList<>();

        for (SoundEventHolder.SoundHolder sound : SoundEventHolder.readFrom(eventFile).getSounds()) {
            Identifier location = Identifier.tryParse(sound.getName());

            if (sound.getType() != Sound.Type.FILE || location == null || !location.getNamespace().equals(projectName)) continue;

            soundFiles.add(soundsRoot.resolve(FileSystemHelper.toOsString(location.getPath() + SOUND_EXTENSION)));
        }

        return soundFiles;
    }

    /// Ids of the project sound events naming the sound file `soundFile`.
    public List<Identifier> soundEventsUsing(Path soundFile) {
        List<Identifier> eventIds = new ArrayList<>();

        FileSystemHelper.forEachFile(
            projectRoot.resolve("assets").resolve(SOUNDS_DIRECTORY),
            this::isSoundEvent,
            eventFile -> {
                Identifier eventId = getSoundId(eventFile);
                if (eventId != null && soundFilesOf(eventFile).contains(soundFile)) eventIds.add(eventId);
            }
        );

        return eventIds;
    }

    /// Every project sound file some sound event names.
    public Set<Path> usedSoundFiles() {
        Set<Path> used = new HashSet<>();

        FileSystemHelper.forEachFile(
            projectRoot.resolve("assets").resolve(SOUNDS_DIRECTORY),
            this::isSoundEvent,
            eventFile -> used.addAll(soundFilesOf(eventFile))
        );

        return used;
    }

    /// Deletes each of `soundFiles` that no sound event names any more.
    public void deleteUnusedSoundFiles(Collection<Path> soundFiles) {
        Set<Path> used = usedSoundFiles();

        for (Path soundFile : soundFiles) {
            if (!used.contains(soundFile)) FileSystemHelper.deleteRecursive(soundFile);
        }
    }

    // ******************* Project Metadata *******************

    /// root directory of the project. Mutable because [#renameProject] moves
    /// the directory on disk and re-assigns this field afterwards.
    private Path projectRoot;

    /// name of the project
    private String projectName;

    /// Follows Semantic Versioning 2.0.0
    private SemanticVersioning version;

    /// ratio of [AdjustablePanelManager]'s panel divider of each screen
    private final Map<Class<? extends Screen>, List<Integer>> layoutDividerSetup;

    /// folder fold/unfold states stored in project_settings.akproject
    private final Map<String, Boolean> savedFolderStates;

    /// Path of the focused tree node stored in project_settings.akproject, relative to
    /// [#projectRoot] and joined with the OS separator. Empty when no node is focused.
    private String savedFocusedNode;

    /// Last folders picked in the `Export as Pack` modal (Data Path / Resource Path), remembered in
    /// project_settings.akproject so the modal reopens on the previous destinations. Forward-slash
    /// normalized (see [#saveExportPaths]); empty until the user exports at least once.
    private String lastExportDataPath = "";
    private String lastExportResourcePath = "";

    /// Last folders picked in the `Export as Source Code` modal (Data Path = generated `.java`
    /// output / Resource Path = loose assets), stored independently of the pack paths above.
    /// Forward-slash normalized (see [#saveSourceExportPaths]); empty until first source export.
    private String lastSourceDataPath = "";
    private String lastSourceResourcePath = "";

    private final ProjectLocalization localization;

    public AkytheraProject(String projectName, String versionString, Path projectRoot) {
        this(projectName, versionString, projectRoot, new HashMap<>(), new LinkedHashMap<>(), "none");
    }

    public AkytheraProject(
        String projectName,
        String versionString,
        Path projectRoot,
        Map<Class<? extends Screen>, List<Integer>> savedLayouts,
        Map<String, Boolean> savedFolderStates,
        String savedFocusedNode
    ) {
        this.projectName = projectName;
        this.projectRoot = projectRoot;
        this.version = SemanticVersioning.parse(versionString);
        this.layoutDividerSetup = new HashMap<>(savedLayouts);
        this.savedFolderStates = new LinkedHashMap<>(savedFolderStates);
        this.savedFocusedNode = savedFocusedNode;
        this.localization = ProjectLocalization.load(projectRoot);

        // Must match with 'DATAPACK_REGISTRIES'
        // Manually gives registry keys to generated typed registry
        createProjectRegistry(AkytheraRegistries.DataPack.ANIM_MONTAGE_KEY, AnimationMontageHolder::new, AnimationMontageHolder.EDITOR_CODEC);
        createProjectRegistry(AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY, AnimationSequenceHolder::new, AnimationSequenceHolder.EDITOR_CODEC);
        createProjectRegistry(AkytheraRegistries.DataPack.BLEND_SPACE_2D_KEY, BlendSpace2DHolder::new, BlendSpace2DHolder.EDITOR_CODEC);
        createProjectRegistry(AkytheraRegistries.DataPack.ANIMATOR_SCRIPT_KEY, AnimatorScriptHolder::new, AnimatorScriptHolder.EDITOR_CODEC);
        createProjectRegistry(AkytheraRegistries.DataPack.ENTITY_PATCH_KEY, EntityPatchBlueprintHolder::new, EntityPatchBlueprintHolder.EDITOR_CODEC);

        createProjectRegistry(AkytheraRegistries.DataPack.ITEM_PATCH_KEY, ItemPatchBlueprintHolder::new, ItemPatchBlueprintHolder.EDITOR_CODEC);

        createProjectRegistry(AkytheraRegistries.DataPack.ABILITY_KEY, AbilityBlueprintHolder::new, AbilityBlueprintHolder.EDITOR_CODEC);
        createProjectRegistry(AkytheraRegistries.DataPack.ABILITY_EFFECT_KEY, AbilityEffectHolder::new, AbilityEffectHolder.EDITOR_CODEC);
        createProjectRegistry(AkytheraRegistries.DataPack.ABILITY_CUE_KEY, AbilityCueBlueprintHolder::new, AbilityCueBlueprintHolder.EDITOR_CODEC);
        createProjectRegistry(AkytheraRegistries.DataPack.INPUT_ACTION_KEY, InputActionHolder::new, InputActionHolder.EDITOR_CODEC);
        createProjectRegistry(AkytheraRegistries.DataPack.WIDGET_KEY, WidgetBlueprintHolder::new, WidgetBlueprintHolder.EDITOR_CODEC);
        createProjectRegistry(AkytheraRegistries.DataPack.PARTICLE_PIECE_KEY, ParticleBlueprintHolder::new, ParticleBlueprintHolder.EDITOR_CODEC);
        createProjectRegistry(AkytheraRegistries.DataPack.PARTICLE_SYSTEM_KEY, ParticleSystemHolder::new, ParticleSystemHolder.EDITOR_CODEC);
        createProjectRegistry(AkytheraRegistries.DataPack.PARTICLE_INITIALIZER_KEY, ParticleInitializerScriptHolder::new, ParticleInitializerScriptHolder.EDITOR_CODEC);
        createProjectRegistry(AkytheraRegistries.DataPack.PARTICLE_UPDATER_KEY, ParticleUpdaterScriptHolder::new, ParticleUpdaterScriptHolder.EDITOR_CODEC);

        // Editor-editable EnumerableDataBlock types (FSMAnimationState, AnimationLayerSignature, …):
        // one registry each, value type = the engine enum type. These never enter the engine
        // manager — see DataBlockHolder.
        for (DataBlockRegistries.Entry<?, ?> entry : DataBlockRegistries.all()) {
            createDataBlockRegistry(entry);
        }
    }

    public Path getProjectRoot() {
        return projectRoot;
    }

    public String getProjectName() {
        return projectName;
    }

    public ProjectLocalization getLocalization() {
        return localization;
    }

    public void saveLocalization() {
        localization.save(projectRoot);
    }

    /// Rename the project on disk and rebuild every namespace-bearing piece
    /// of in-memory state. Returns `true` on success; on failure the project
    /// is left in its original state.
    ///
    /// NOTE: Any [AssetAccessor] captured *before* this call (e.g. records
    /// held by panels) keep their old namespace and become stale. Panels
    /// must re-fetch through [#getCurrentProject()] after a rename.
    public boolean renameProject(String newName) {
        if (newName.equals(projectName)) return true;

        Path oldRoot = projectRoot;
        Path newRoot = getProjectsRootDirectory().resolve(newName);
        String oldName = projectName;

        if (Files.exists(newRoot)) {
            Akythera.LOGGER.error("Cannot rename project: target '{}' already exists", newName);
            return false;
        }

        try {
            Files.move(oldRoot, newRoot, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            try {
                Files.move(oldRoot, newRoot);
            } catch (IOException ex) {
                Akythera.LOGGER.error("Failed to rename project directory {} -> {}", oldRoot, newRoot, ex);
                return false;
            }
        } catch (IOException e) {
            Akythera.LOGGER.error("Failed to rename project directory {} -> {}", oldRoot, newRoot, e);
            return false;
        }

        this.projectRoot = newRoot;
        this.projectName = newName;

        editorMeshes.clear();
        editorArmatures.clear();
        editorAnimations.clear();

        // Asset accessors: mutate id in place (referrers share the instance, so they follow).
        rekeyAssetAccessors(issuedEditorMeshAccessors, oldName, newName);
        rekeyAssetAccessors(issuedEditorArmatureAccessors, oldName, newName);
        rekeyAssetAccessors(issuedEditorAnimationAccessors, oldName, newName);

        // Data registries: re-key around stable holder instances (the shared ProjectObjectHolders
        // follow too). The trailing saveAllEntries() flushes every referrer's JSON.
        for (ProjectObjectRegistry<?, ?> registry : modifiableRegistries.values()) {
            registry.remapNamespace(newName);
        }

        // Load-bearing: holders whose key just changed namespace need their own JSON re-written
        // (their *own* file is now at a new path under the renamed project dir), and every referrer
        // needs its ids re-emitted — the shared holders / accessors above already report the new
        // namespace, so this is the write that makes the rename survive a reload.
        //
        // The blocking form: this runs inside the project-settings modal's apply, which closes its
        // own modal afterwards — an await modal stacked on top would be the one that got closed.
        writeAllEntries();
        persistMetadata();

        AkytheraEditorLayoutManager.getInstance().<MainEditorScreen>ifLastScreen(screen -> {
            Path oldFocusedPath = screen.explorerPanel.getFocusedPath();
            screen.explorerPanel.rebuildTree();

            if (oldFocusedPath != null) {
                screen.explorerPanel.focusNodeByPath(remapProjectPath(oldFocusedPath, oldName, newName));
            }
        });

        return true;
    }

    /// Rebuild every asset-accessor map under `newNs`. Each accessor's
    /// id mutates in place and its inbound references fire.
    private static <A extends AssetAccessor<?>> void rekeyAssetAccessors(
        Map<Identifier, A> issued, String oldNs, String newNs
    ) {
        if (issued.isEmpty()) return;

        List<Map.Entry<Identifier, A>> snap = new ArrayList<>(issued.entrySet());
        issued.clear();

        for (var e : snap) {
            Identifier oldId = e.getKey();
            Identifier newId = oldId.getNamespace().equals(oldNs)
                ? Identifier.fromNamespaceAndPath(newNs, oldId.getPath())
                : oldId;

            ((EditorReferenceable<?>) e.getValue()).rename(newId);
            issued.put(newId, e.getValue());
        }
    }

    /// Path under `akythera_projects/<oldNs>/…` → same path under
    /// `akythera_projects/<newNs>/…`. Used to translate the explorer's
    /// focused node after a rename. No-op for paths outside the old
    /// project root.
    private static Path remapProjectPath(Path path, String oldNs, String newNs) {
        Path projectsRoot = getProjectsRootDirectory();
        Path oldRoot = projectsRoot.resolve(oldNs);
        if (!path.startsWith(oldRoot)) return path;
        return projectsRoot.resolve(newNs).resolve(oldRoot.relativize(path));
    }

    /// Writes the `name` and `version` fields into the project settings file
    /// without touching layout / folder state. Used after a rename so a
    /// subsequent cold load reads the correct name without depending on a
    /// full [#saveProjectMetadata].
    private void persistMetadata() {
        Path settingsFile = projectRoot.resolve(PROJECT_SETTINGS_FILE);
        try {
            JsonObject obj;

            if (Files.exists(settingsFile)) {
                obj = JsonParser.parseReader(Files.newBufferedReader(settingsFile)).getAsJsonObject();
            } else {
                obj = new JsonObject();
            }

            obj.addProperty("name", projectName);
            FileSystemHelper.writeString(settingsFile, new GsonBuilder().setPrettyPrinting().create().toJson(obj));
            obj.addProperty("version", version.toString());
        } catch (IOException e) {
            Akythera.LOGGER.error("Failed to persist renamed project metadata", e);
        }
    }

    public SemanticVersioning getVersion() {
        return version;
    }

    public void setVersion(SemanticVersioning v) {
        this.version = v;
    }

    public Map<String, Boolean> getSavedFolderStates() {
        return Collections.unmodifiableMap(savedFolderStates);
    }

    public String getSavedFocusedNode() {
        return savedFocusedNode;
    }

    /// Last `Export as Pack` Data Path, or `""` if never exported. Forward-slash normalized —
    /// suitable to feed straight into `Path.of` (accepts `/` on Windows) and to display.
    public String getLastExportDataPath() {
        return lastExportDataPath;
    }

    /// Last `Export as Pack` Resource Path, or `""` if never exported. See [#getLastExportDataPath].
    public String getLastExportResourcePath() {
        return lastExportResourcePath;
    }

    /// Last `Export as Source Code` Data Path (generated `.java` output), or `""`. See
    /// [#getLastExportDataPath].
    public String getLastSourceDataPath() {
        return lastSourceDataPath;
    }

    /// Last `Export as Source Code` Resource Path (loose assets), or `""`. See [#getLastExportDataPath].
    public String getLastSourceResourcePath() {
        return lastSourceResourcePath;
    }

    /// Remember the `Export as Pack` destinations and persist them into project_settings.akproject
    /// so the modal reopens on the same folders. See [#persistSettingsPair] for the merge semantics.
    public void saveExportPaths(String dataPath, String resourcePath) {
        this.lastExportDataPath = FileSystemHelper.toIdString(dataPath);
        this.lastExportResourcePath = FileSystemHelper.toIdString(resourcePath);
        persistSettingsPair("export_data_path", lastExportDataPath, "export_resource_path", lastExportResourcePath);
    }

    /// Remember the `Export as Source Code` destinations. Stored independently of the pack paths
    /// (distinct `source_*` keys), so both modals keep their own last-used folders.
    public void saveSourceExportPaths(String dataPath, String resourcePath) {
        this.lastSourceDataPath = FileSystemHelper.toIdString(dataPath);
        this.lastSourceResourcePath = FileSystemHelper.toIdString(resourcePath);
        persistSettingsPair("source_data_path", lastSourceDataPath, "source_resource_path", lastSourceResourcePath);
    }

    /// Merge two string settings into project_settings.akproject (read → set the two keys → write)
    /// so folder states / layouts are left untouched; a subsequent full [#saveProjectMetadata]
    /// rewrite re-emits the same fields. Values are stored forward-slash normalized by callers so no
    /// JSON backslash-escaping is needed by the [#saveProjectMetadata] writer.
    private void persistSettingsPair(String key1, String value1, String key2, String value2) {
        Path settingsFile = projectRoot.resolve(PROJECT_SETTINGS_FILE);
        try {
            JsonObject obj = Files.exists(settingsFile)
                ? JsonParser.parseReader(Files.newBufferedReader(settingsFile)).getAsJsonObject()
                : new JsonObject();

            obj.addProperty(key1, value1);
            obj.addProperty(key2, value2);
            FileSystemHelper.writeString(settingsFile, GSON_PRETTY.toJson(obj));
        } catch (IOException e) {
            Akythera.LOGGER.error("Failed to persist settings for {}", projectName, e);
        }
    }

    /// Flushes every registered data holder to its on-disk JSON, off the render thread behind an
    /// await modal — a whole-project write is far too slow to run inline on the render loop.
    public void saveAllEntries() {
        AwaitIconModal.runSaving(this::writeAllEntries);
    }

    @SuppressWarnings("unchecked")
    private void writeAllEntries() {
        for (ProjectObjectRegistry<?, ?> raw : modifiableRegistries.values()) {
            ProjectObjectRegistry<Object, DataHolder<Object>> registry = (ProjectObjectRegistry<@NonNull Object, @NonNull DataHolder<Object>>) raw;
            Codec<DataHolder<Object>> codec = registry.getCodec();
            Path registryDir = projectRoot.resolve("data").resolve(registry.key().identifier().getPath());

            for (Map.Entry<ResourceKey<Object>, DataHolder<Object>> entry : registry.dataHolderEntrySet()) {
                String idPath = entry.getKey().identifier().getPath();
                Path filePath = registryDir.resolve(FileSystemHelper.toOsString(idPath) + ".json");

                FileSystemHelper.writeJson(filePath, codec, entry.getValue());
            }
        }
    }

    /// [#saveEntry] without the render-thread `refreshObject` — the bulk paths run off-thread behind
    /// an await modal, which blocks the edits that would make the cached object stale.
    @SuppressWarnings("unchecked")
    private <T> void writeEntry(ResourceKey<T> key) {
        ProjectObjectRegistry<T, DataHolder<T>> registry =
            (ProjectObjectRegistry<T, @NonNull DataHolder<T>>) modifiableRegistries.get(key.registryKey());
        if (registry == null) return;

        DataHolder<T> holder = registry.getDataHolder(key);
        if (holder == null) return;

        if (holder.getDeserializationFailureException() != null && holder.retainedRaw() == null) {
            AkytheraLabs.LOGGER.error(
                "Refusing to save {} — it failed to load and no original content was retained", key.identifier());
            return;
        }

        FileSystemHelper.writeJson(getEntryFilePath(key), registry.getCodec(), holder);
    }

    /// Delete a file or directory under the project root, and unregister
    /// every datapack entry / editor accessor that the deleted files
    /// covered. Use this instead of raw `Files.delete` from any UI deletion
    /// path — otherwise removed items survive in [#modifiableRegistries] /
    /// `issuedEditor*Accessors` and reappear on the next [#saveAllEntries]
    /// (datapack) or keep haunting selection modals (assets) until the
    /// project is reloaded.
    public void deletePathRecursive(Path path) {
        if (!Files.exists(path)) return;

        Path dataRoot = projectRoot.resolve("data");
        Path assetsRoot = projectRoot.resolve("assets");

        // Sound files the deleted events played, deleted with them unless another event still names one
        List<Path> releasedSoundFiles = new ArrayList<>();

        if (Files.isRegularFile(path)) {
            unregisterDeletedFile(dataRoot, assetsRoot, path);

            if (isSoundEvent(path)) releasedSoundFiles.addAll(soundFilesOf(path));

            // A directory delete already takes the sidecars with the rest of the tree. A single-file
            // delete has to take them explicitly — and here rather than inside unregisterAssetFile,
            // which the directory branch below calls while it is still walking that directory.
            Path metadataFile = getMetadataFileBeside(path);
            Path mcmetaFile = getMcmetaFileBeside(path);

            if (path.startsWith(assetsRoot) && !isSoundAsset(path) && Files.isRegularFile(metadataFile)) {
                FileSystemHelper.deleteRecursive(metadataFile);
            }

            if (path.startsWith(assetsRoot) && Files.isRegularFile(mcmetaFile)) {
                FileSystemHelper.deleteRecursive(mcmetaFile);
            }

            deleteCompanionOf(path);
        } else {
            FileSystemHelper.forEachFile(path, file -> {
                unregisterDeletedFile(dataRoot, assetsRoot, file);
                deleteCompanionOf(file);

                if (isSoundEvent(file)) releasedSoundFiles.addAll(soundFilesOf(file));
            });
        }

        FileSystemHelper.deleteRecursive(path);

        if (!releasedSoundFiles.isEmpty()) deleteUnusedSoundFiles(releasedSoundFiles);
    }

    /// Deletes the companion of a deleted asset, sidecar and accessor bookkeeping included. The
    /// companion lives under a different asset root, so this is safe to call while walking a deleted
    /// directory.
    private void deleteCompanionOf(Path assetFile) {
        Path companion = companionOf(assetFile);

        if (companion != null && Files.isRegularFile(companion)) {
            deletePathRecursive(companion);
        }
    }

    private void unregisterDeletedFile(Path dataRoot, Path assetsRoot, Path file) {
        if (file.startsWith(dataRoot)) {
            unregisterDatapackFile(dataRoot, file);
        } else if (file.startsWith(assetsRoot)) {
            unregisterAssetFile(assetsRoot, file);
        }
    }

    private void unregisterDatapackFile(Path dataRoot, Path file) {
        if (!file.toString().endsWith(".json")) return;

        for (ProjectObjectRegistry<?, ?> registry : modifiableRegistries.values()) {
            Path regDir = dataRoot.resolve(registry.key().identifier().getPath());

            if (!file.startsWith(regDir)) continue;

            String rel = FileSystemHelper.relativeIdPath(regDir, file);
            String idPath = rel.substring(0, rel.length() - ".json".length());
            registry.remove(Identifier.fromNamespaceAndPath(projectName, idPath));

            return;
        }
    }

    private void unregisterAssetFile(Path assetsRoot, Path file) {
        String rel = FileSystemHelper.relativeIdPath(assetsRoot, file);
        String pathNoExt = ParseUtils.trimExtensions(rel, AssetLoader.BINARY_EXTENSION, "json");
        Identifier id = Identifier.fromNamespaceAndPath(projectName, pathNoExt);

        if (rel.startsWith(MeshManager.DIRECTORY)) {
            editorMeshes.remove(id);
            issuedEditorMeshAccessors.remove(id);
        } else if (rel.startsWith(ArmatureManager.DIRECTORY)) {
            editorArmatures.remove(id);
            issuedEditorArmatureAccessors.remove(id);
        } else if (rel.startsWith(AnimationManager.DIRECTORY)) {
            editorAnimations.remove(id);
            issuedEditorAnimationAccessors.remove(id);
        } else if (rel.startsWith(ArmaturePhysicsManager.DIRECTORY)) {
            issuedEditorArmaturePhysicsAccessors.remove(id);
        } else if (rel.startsWith(ClothPhysicsLoader.DIRECTORY)) {
            Identifier meshId = Identifier.fromNamespaceAndPath(
                id.getNamespace(),
                MeshManager.DIRECTORY + id.getPath().substring(ClothPhysicsLoader.DIRECTORY.length())
            );

            clothPhysicsHolders.remove(meshId);
        }
    }

    /// Copies `source` into `targetDir` under a free name and registers whatever landed. An asset
    /// takes its `.akmeta` or `.mcmeta` sidecar along; a directory is copied whole. Returns the new
    /// path, or null when nothing was copied.
    public @Nullable Path copyInto(Path source, Path targetDir) {
        if (!Files.exists(source)) return null;

        boolean directory = Files.isDirectory(source);
        Path target = copyTarget(source, targetDir);

        if (!FileSystemHelper.copyRecursive(source, target)) return null;

        Path sidecar = getMetadataFileBeside(source);

        if (!directory && !isSoundAsset(source) && Files.isRegularFile(sidecar)) {
            FileSystemHelper.copyReplace(sidecar, getMetadataFileBeside(target));
        }

        Path mcmetaFile = getMcmetaFileBeside(source);

        if (!directory && Files.isRegularFile(mcmetaFile)) {
            FileSystemHelper.copyReplace(mcmetaFile, getMcmetaFileBeside(target));
        }

        registerPathRecursive(target);

        return target;
    }

    /// Where [#copyInto] puts a copy of `source` in `targetDir`: its own name, or the next free one.
    public Path copyTarget(Path source, Path targetDir) {
        String fileName = source.getFileName().toString();
        boolean directory = Files.isDirectory(source);
        String extension = directory ? "" : FileSystemHelper.extension(fileName);
        String stem = directory ? fileName : FileSystemHelper.stem(fileName);

        return targetDir.resolve(FileSystemHelper.findNextAvailableName(targetDir, stem, extension) + extension);
    }

    /// Registers every datapack entry / editor accessor the files under `path` stand for — the
    /// mirror of the unregister walk in [#deletePathRecursive], for files that arrived on disk after
    /// project load.
    public void registerPathRecursive(Path path) {
        if (!Files.exists(path)) return;

        Path dataRoot = projectRoot.resolve("data");
        Path assetsRoot = projectRoot.resolve("assets");

        if (Files.isRegularFile(path)) {
            registerCreatedFile(dataRoot, assetsRoot, path);
        } else {
            FileSystemHelper.forEachFile(path, file -> registerCreatedFile(dataRoot, assetsRoot, file));
        }
    }

    private void registerCreatedFile(Path dataRoot, Path assetsRoot, Path file) {
        if (file.startsWith(dataRoot)) {
            registerDatapackFile(dataRoot, file);
        } else if (file.startsWith(assetsRoot)) {
            registerAssetFile(assetsRoot, file);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void registerDatapackFile(Path dataRoot, Path file) {
        if (!file.toString().endsWith(".json")) return;

        for (ProjectObjectRegistry<?, ?> registry : modifiableRegistries.values()) {
            Path regDir = dataRoot.resolve(registry.key().identifier().getPath());

            if (!file.startsWith(regDir)) continue;

            String rel = FileSystemHelper.relativeIdPath(regDir, file);
            String idPath = rel.substring(0, rel.length() - ".json".length());
            Identifier id = Identifier.fromNamespaceAndPath(projectName, idPath);
            ResourceKey key = ResourceKey.create((ResourceKey) registry.key(), id);

            readEntryFile((ProjectObjectRegistry) registry, key, file);

            return;
        }
    }

    /// Issues the accessor for one asset file. A sidecar is no asset of its own — it is read through
    /// the accessor of the file it sits beside.
    private void registerAssetFile(Path assetsRoot, Path file) {
        String fileName = file.getFileName().toString();

        if (!fileName.endsWith("." + AssetLoader.BINARY_EXTENSION) && !fileName.endsWith(".json")) return;

        String rel = FileSystemHelper.relativeIdPath(assetsRoot, file);

        try {
            if (rel.startsWith(MeshManager.DIRECTORY)) {
                getEditorMeshAccessorByPath(file);
            } else if (rel.startsWith(ArmatureManager.DIRECTORY)) {
                getEditorArmatureAccessorByPath(file);
            } else if (rel.startsWith(AnimationManager.DIRECTORY)) {
                getEditorAnimationAccessorByPath(file);
            } else if (rel.startsWith(ArmaturePhysicsManager.DIRECTORY)) {
                getEditorArmaturePhysicsAccessorByPath(file);
            }
        } catch (Exception e) {
            AkytheraLabs.LOGGER.warn("Failed to register asset accessor for {}", file, e);
        }
    }

    /// Walk `path` BEFORE deletion and return a `Runnable` whose `.run()`
    /// rebuilds the exact subtree — bytes restored, registries / accessors
    /// re-registered with the SAME in-memory instances they had before.
    ///
    /// Identity-preservation rule, as in [#reinsertEntry]: the captured holder, accessor and
    /// loaded reference are the registry's previous instances, so every referrer stays wired across
    /// the delete-undo cycle.
    ///
    /// MUST be called BEFORE [#deletePathRecursive] — once the disk is
    /// gone, the byte-read step has nothing to capture.
    public Runnable buildRestoreFor(Path path) {
        List<Runnable> steps = new ArrayList<>();
        if (!Files.exists(path)) return () -> {};

        Path dataRoot = projectRoot.resolve("data");
        Path assetsRoot = projectRoot.resolve("assets");

        FileSystemHelper.forEachEntryPreOrder(path, p -> {
            if (Files.isDirectory(p)) {
                steps.add(() -> {
                    try { Files.createDirectories(p); }
                    catch (IOException e) { Akythera.LOGGER.error("Failed to recreate {}", p, e); }
                });
                return;
            }
            if (!Files.isRegularFile(p)) return;

            byte[] bytes = FileSystemHelper.readBytes(p);
            if (bytes == null) return;

            // A sound event's delete may take the sound files it plays, so the restore brings back those
            // still missing.
            if (isSoundEvent(p)) {
                Map<Path, byte[]> soundFileBytes = new HashMap<>();

                for (Path soundFile : soundFilesOf(p)) {
                    byte[] soundBytes = Files.isRegularFile(soundFile) ? FileSystemHelper.readBytes(soundFile) : null;
                    if (soundBytes != null) soundFileBytes.put(soundFile, soundBytes);
                }

                steps.add(() -> {
                    FileSystemHelper.writeBytes(p, bytes);

                    soundFileBytes.forEach((soundFile, soundBytes) -> {
                        if (!Files.exists(soundFile)) FileSystemHelper.writeBytes(soundFile, soundBytes);
                    });
                });
                return;
            }

            if (p.startsWith(dataRoot) && p.toString().endsWith(".json")) {
                DataCapture cap = captureDataEntry(dataRoot, p);
                if (cap != null) {
                    steps.add(() -> {
                        FileSystemHelper.writeBytes(p, bytes);
                        cap.reinsert().run();
                    });
                    return;
                }
            } else if (p.startsWith(assetsRoot)) {
                AssetCapture cap = captureAsset(assetsRoot, p);
                if (cap != null) {
                    Path metadataFile = getMetadataFileBeside(p);
                    byte[] metadataBytes = Files.isRegularFile(metadataFile)
                        ? FileSystemHelper.readBytes(metadataFile)
                        : null;

                    Path companion = companionOf(p);
                    boolean companionPresent = companion != null && Files.isRegularFile(companion);
                    byte[] companionBytes = companionPresent ? FileSystemHelper.readBytes(companion) : null;
                    Path companionMetadataFile = companionPresent ? getMetadataFileBeside(companion) : null;
                    byte[] companionMetadataBytes = companionPresent && Files.isRegularFile(companionMetadataFile)
                        ? FileSystemHelper.readBytes(companionMetadataFile)
                        : null;

                    steps.add(() -> {
                        FileSystemHelper.writeBytes(p, bytes);

                        if (metadataBytes != null) FileSystemHelper.writeBytes(metadataFile, metadataBytes);

                        if (companionBytes != null) {
                            FileSystemHelper.writeBytes(companion, companionBytes);

                            if (companionMetadataBytes != null) {
                                FileSystemHelper.writeBytes(companionMetadataFile, companionMetadataBytes);
                            }

                            registerPathRecursive(companion);
                        }

                        cap.reinsert().run();
                    });
                    return;
                }
            }

            // A single-file delete takes the texture's sidecar with it, so the restore brings it back too.
            Path mcmetaFile = getMcmetaFileBeside(p);
            byte[] mcmetaBytes = Files.isRegularFile(mcmetaFile) ? FileSystemHelper.readBytes(mcmetaFile) : null;

            steps.add(() -> {
                FileSystemHelper.writeBytes(p, bytes);

                if (mcmetaBytes != null) FileSystemHelper.writeBytes(mcmetaFile, mcmetaBytes);
            });
        });

        return () -> steps.forEach(Runnable::run);
    }

    /// Lightweight inline carriers — never escape the file. They bundle the
    /// closure-captured registry / accessor reinsert into one `Runnable`
    /// per file, so [#buildRestoreFor] only sees a uniform per-entry
    /// reinsert step.
    private record DataCapture(Runnable reinsert) {}
    private record AssetCapture(Runnable reinsert) {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    private @Nullable DataCapture captureDataEntry(Path dataRoot, Path file) {
        for (ProjectObjectRegistry<?, ?> registry : modifiableRegistries.values()) {
            Path regDir = dataRoot.resolve(registry.key().identifier().getPath());
            if (!file.startsWith(regDir)) continue;

            String rel = FileSystemHelper.relativeIdPath(regDir, file);
            String idPath = rel.substring(0, rel.length() - ".json".length());
            Identifier id = Identifier.fromNamespaceAndPath(projectName, idPath);
            ResourceKey key = ResourceKey.create((ResourceKey)registry.key(), id);
            DataHolder holder = registry.getDataHolder(key);
            if (holder == null) return null;

            return new DataCapture(() -> reinsertEntry(key, holder));
        }
        return null;
    }

    private @Nullable AssetCapture captureAsset(Path assetsRoot, Path file) {
        String rel = FileSystemHelper.relativeIdPath(assetsRoot, file);
        String pathNoExt = ParseUtils.trimExtensions(rel, AssetLoader.BINARY_EXTENSION, "json");
        Identifier id = Identifier.fromNamespaceAndPath(projectName, pathNoExt);

        if (rel.startsWith(MeshManager.DIRECTORY)) {
            AssetAccessor<? extends Mesh<?>> accessor = issuedEditorMeshAccessors.get(id);
            Mesh<?> loaded = editorMeshes.get(id);

            return new AssetCapture(() -> {
                if (accessor != null) issuedEditorMeshAccessors.put(id, accessor);
                if (loaded != null) editorMeshes.put(id, loaded);
            });
        } else if (rel.startsWith(ArmatureManager.DIRECTORY)) {
            AssetAccessor<? extends Armature> accessor = issuedEditorArmatureAccessors.get(id);
            Armature loaded = editorArmatures.get(id);

            return new AssetCapture(() -> {
                if (accessor != null) issuedEditorArmatureAccessors.put(id, accessor);
                if (loaded != null) editorArmatures.put(id, loaded);
            });
        } else if (rel.startsWith(AnimationManager.DIRECTORY)) {
            AssetAccessor<AnimationClip> accessor = issuedEditorAnimationAccessors.get(id);
            AnimationClip loaded = editorAnimations.get(id);

            return new AssetCapture(() -> {
                if (accessor != null) issuedEditorAnimationAccessors.put(id, accessor);
                if (loaded != null) editorAnimations.put(id, loaded);
            });
        }

        return null;
    }

    /// The file belonging to the asset at `assetFile` that lives under another asset root: a skeletal
    /// mesh's cloth, or an armature's physics. Null for a kind that has no companion — the file itself
    /// need not exist.
    private @Nullable Path companionOf(Path assetFile) {
        Path assetsRoot = projectRoot.resolve("assets");

        if (!assetFile.startsWith(assetsRoot)) return null;

        String rel = FileSystemHelper.relativeIdPath(assetsRoot, assetFile);
        String stem = ParseUtils.trimExtensions(rel, AssetLoader.BINARY_EXTENSION, "json");

        if (rel.startsWith(MeshManager.DIRECTORY)) {
            return assetsRoot.resolve(FileSystemHelper.toOsString(
                ClothPhysicsLoader.DIRECTORY + stem.substring(MeshManager.DIRECTORY.length()) + "." + AssetLoader.BINARY_EXTENSION
            ));
        }

        if (rel.startsWith(ArmatureManager.DIRECTORY)) {
            return assetsRoot.resolve(FileSystemHelper.toOsString(
                ArmaturePhysicsManager.DIRECTORY + stem.substring(ArmatureManager.DIRECTORY.length()) + ".json"
            ));
        }

        return null;
    }


    /// Re-points the editor accessor for an asset file from `oldPath` to `newPath`, after the file
    /// has been moved on disk. The accessor's id mutates in place, so every referrer holding the
    /// shared instance reports the new id — see [#renameAssetIn].
    ///
    /// `oldPath` and `newPath` are absolute paths under `assets/<directory>/`. A file outside the
    /// three asset directories — project_settings.akproject, icon.png and the like — is a no-op.
    public void renameAsset(Path oldPath, Path newPath) {
        Path assetsRoot = projectRoot.resolve("assets");
        if (!oldPath.startsWith(assetsRoot) || !newPath.startsWith(assetsRoot)) return;

        // The sidecar is a second file sharing the asset's stem, and the move that got us here only
        // moved the asset. Left behind it would both lose the metadata and shadow whatever file
        // claims the old name next.
        Path oldMetadataFile = getMetadataFileBeside(oldPath);

        if (!isSoundAsset(oldPath) && Files.isRegularFile(oldMetadataFile)) {
            FileSystemHelper.move(oldMetadataFile, getMetadataFileBeside(newPath));
        }

        Path oldMcmetaFile = getMcmetaFileBeside(oldPath);

        if (Files.isRegularFile(oldMcmetaFile)) {
            FileSystemHelper.move(oldMcmetaFile, getMcmetaFileBeside(newPath));
        }

        moveCompanion(oldPath, newPath);

        String oldRel = FileSystemHelper.relativeIdPath(assetsRoot, oldPath);
        String newRel = FileSystemHelper.relativeIdPath(assetsRoot, newPath);

        if (oldRel.startsWith(MeshManager.DIRECTORY)) {
            renameAssetIn(editorMeshes, issuedEditorMeshAccessors, oldRel, newRel);
        } else if (oldRel.startsWith(ArmatureManager.DIRECTORY)) {
            renameAssetIn(editorArmatures, issuedEditorArmatureAccessors, oldRel, newRel);
        } else if (oldRel.startsWith(AnimationManager.DIRECTORY)) {
            renameAssetIn(editorAnimations, issuedEditorAnimationAccessors, oldRel, newRel);
        } else if (isSoundAsset(oldPath)) {
            Identifier oldId = getSoundId(oldPath);
            Identifier newId = getSoundId(newPath);
            Sound.Type type = oldRel.endsWith(SOUND_EXTENSION) ? Sound.Type.FILE : Sound.Type.SOUND_EVENT;

            if (oldId != null && newId != null && !oldId.equals(newId)) rewriteSoundReferences(type, oldId, newId);
        }
    }

    /// Re-points every project sound entry of `type` naming `oldId` at `newId`. A sound event is an
    /// asset rather than a registry entry, so it carries the ids as text and has to be rewritten.
    private void rewriteSoundReferences(Sound.Type type, Identifier oldId, Identifier newId) {
        FileSystemHelper.forEachFile(
            projectRoot.resolve("assets").resolve(SOUNDS_DIRECTORY),
            this::isSoundEvent,
            eventFile -> {
                SoundEventHolder event = SoundEventHolder.readFrom(eventFile);
                boolean changed = false;

                for (SoundEventHolder.SoundHolder sound : event.getSounds()) {
                    if (sound.getType() == type && oldId.equals(Identifier.tryParse(sound.getName()))) {
                        sound.setName(newId.toString());
                        changed = true;
                    }
                }

                if (changed) FileSystemHelper.writeJson(eventFile, SoundEventHolder.CODEC, event);
            }
        );
    }

    /// Re-key both the loaded-object cache and the issued-accessor map, then `rename` the accessor
    /// — that mutates the id in place, so every referrer holding this shared accessor instance now
    /// reports the new id. A re-save scan then flushes those referrers' JSON to match.
    private <V, A extends AssetAccessor<?>> void renameAssetIn(
        Map<Identifier, V> cache,
        Map<Identifier, A> issued,
        String oldRel, String newRel
    ) {
        renameIssuedAccessor(issued, oldRel, newRel);

        V loaded = cache.remove(idOfRelativePath(oldRel));
        if (loaded != null) cache.put(idOfRelativePath(newRel), loaded);
    }

    /// Re-keys the issued accessor, then `rename`s it — that mutates the id in place, so every referrer
    /// holding this shared accessor instance now reports the new id. A re-save scan then flushes those
    /// referrers' JSON to match.
    private <A extends AssetAccessor<?>> void renameIssuedAccessor(Map<Identifier, A> issued, String oldRel, String newRel) {
        Identifier oldId = idOfRelativePath(oldRel);
        Identifier newId = idOfRelativePath(newRel);

        if (oldId.equals(newId)) return;

        A accessor = issued.remove(oldId);

        if (accessor == null) return;

        EditorReferenceable<?> renamed = (EditorReferenceable<?>) accessor;
        renamed.rename(newId);
        issued.put(newId, accessor);
        resaveReferrers(renamed);
    }

    /// The asset id of a path relative to `assets/`, extension trimmed.
    private Identifier idOfRelativePath(String rel) {
        return Identifier.fromNamespaceAndPath(projectName, ParseUtils.trimExtensions(rel, AssetLoader.BINARY_EXTENSION, "json"));
    }

    /// Carries the companion of a renamed asset — and the companion's own sidecar — to the asset's new
    /// path, then re-keys whatever the editor issued for it.
    private void moveCompanion(Path oldPath, Path newPath) {
        Path oldCompanion = companionOf(oldPath);
        Path newCompanion = companionOf(newPath);

        if (oldCompanion == null || newCompanion == null || !Files.isRegularFile(oldCompanion)) return;

        try {
            Files.createDirectories(newCompanion.getParent());
        } catch (IOException e) {
            AkytheraLabs.LOGGER.error("Failed to create {}", newCompanion.getParent(), e);
            return;
        }

        if (!FileSystemHelper.move(oldCompanion, newCompanion)) return;

        Path oldCompanionMetadata = getMetadataFileBeside(oldCompanion);

        if (Files.isRegularFile(oldCompanionMetadata)) {
            FileSystemHelper.move(oldCompanionMetadata, getMetadataFileBeside(newCompanion));
        }

        Path assetsRoot = projectRoot.resolve("assets");
        String oldRel = FileSystemHelper.relativeIdPath(assetsRoot, oldCompanion);
        String newRel = FileSystemHelper.relativeIdPath(assetsRoot, newCompanion);

        if (oldRel.startsWith(ArmaturePhysicsManager.DIRECTORY)) {
            renameIssuedAccessor(issuedEditorArmaturePhysicsAccessors, oldRel, newRel);
            rewriteClothPhysicsReferences(idOfRelativePath(oldRel), idOfRelativePath(newRel));
        } else {
            // A cloth holder is keyed by its mesh, which is what just moved
            ClothPhysicsDataHolder holder = clothPhysicsHolders.remove(idOfRelativePath(FileSystemHelper.relativeIdPath(assetsRoot, oldPath)));

            if (holder != null) {
                clothPhysicsHolders.put(idOfRelativePath(FileSystemHelper.relativeIdPath(assetsRoot, newPath)), holder);
            }
        }
    }

    /// Re-points every cloth naming `oldPhysicsId` at `newPhysicsId`. A cloth is an asset rather than a
    /// registry entry, so the referrer re-save a rename normally drives does not reach it — its
    /// `.akmeta` carries the id as text and has to be rewritten (Answer 4).
    ///
    /// A cloth already open follows on its own: it holds the shared accessor whose id moved in place.
    private void rewriteClothPhysicsReferences(Identifier oldPhysicsId, Identifier newPhysicsId) {
        Path clothRoot = projectRoot.resolve("assets").resolve(FileSystemHelper.toOsString(ClothPhysicsLoader.DIRECTORY));
        AssetAccessor<? extends ArmaturePhysicsData> moved = getOrIssueEditorArmaturePhysicsAccessor(newPhysicsId);

        FileSystemHelper.forEachFile(
            clothRoot,
            file -> file.toString().endsWith(AssetLoader.METADATA_EXTENSION),
            file -> {
                ClothPhysicsData.Metadata metadata = FileSystemHelper.readJson(file, ClothPhysicsData.Metadata.CODEC, null);

                if (metadata == null || metadata.armaturePhysics() == null) return;
                if (!metadata.armaturePhysics().identifier().equals(oldPhysicsId)) return;

                FileSystemHelper.writeJson(
                    file, ClothPhysicsData.Metadata.CODEC, new ClothPhysicsData.Metadata(moved, metadata.parameters())
                );
            }
        );
    }

    /// Writes the explorer's fold states and focused directory unless both match the stored ones.
    public void saveExplorerState(Map<String, Boolean> folderStates, @Nullable Path focusedNodePath) {
        String focusedNode = focusedNodePath == null ? "" : projectRoot.relativize(focusedNodePath).toString();

        if (savedFolderStates.equals(folderStates) && savedFocusedNode.equals(focusedNode)) return;

        saveProjectMetadata(folderStates, focusedNodePath);
    }

    public void saveProjectMetadata(Map<String, Boolean> folderStates, @Nullable Path focusedNodePath) {
        String focusedNode = focusedNodePath == null ? "" : projectRoot.relativize(focusedNodePath).toString();
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("    \"name\": \"").append(projectName).append("\",\n");
        sb.append("    \"version\": \"").append(version).append("\",\n");
        // Forward-slash normalized when saved, so safe to embed raw (no backslash escaping).
        sb.append("    \"export_data_path\": \"").append(lastExportDataPath).append("\",\n");
        sb.append("    \"export_resource_path\": \"").append(lastExportResourcePath).append("\",\n");
        sb.append("    \"source_data_path\": \"").append(lastSourceDataPath).append("\",\n");
        sb.append("    \"source_resource_path\": \"").append(lastSourceResourcePath).append("\",\n");
        sb.append("    \"focused\": \"").append(FileSystemHelper.toIdString(focusedNode)).append("\",\n");

        sb.append("    \"folder_states\": {");
        boolean firstFolder = true;

        for (Map.Entry<String, Boolean> entry : folderStates.entrySet()) {
            if (!firstFolder) sb.append(",");
            sb.append("\n        \"").append(entry.getKey()).append("\": ").append(entry.getValue());
            firstFolder = false;
        }

        sb.append("\n    },\n");

        // Entity preview bindings, read live from EntityPreviewModelMapping (the mapping modal
        // writes there directly). Identifiers are JSON-safe raw.
        sb.append("    \"entity_preview_models\": {");

        boolean firstMapping = true;

        for (EntityType<?> type : EntityPreviewModelMapping.boundTypes()) {
            EntityPreviewModel mesh = EntityPreviewModelMapping.getMesh(type);
            AssetAccessor<? extends Armature> armature = EntityPreviewModelMapping.getArmature(type);

            if (!firstMapping) sb.append(",");
            sb.append("\n        \"").append(BuiltInRegistries.ENTITY_TYPE.getKey(type)).append("\": {");

            List<String> members = new ArrayList<>();

            if (mesh != null) {
                members.add("\"mesh_id\": \"" + mesh.modelId() + "\"");
                members.add("\"mesh_type\": \"" + mesh.modelType().getSerializedName() + "\"");
            }

            if (armature != null) members.add("\"armature\": \"" + armature.identifier() + "\"");

            sb.append(String.join(", ", members)).append("}");
            firstMapping = false;
        }

        sb.append("\n    },\n");
        sb.append("    \"layouts\": {");

        boolean firstLayout = true;

        for (Map.Entry<Class<? extends Screen>, List<Integer>> entry : layoutDividerSetup.entrySet()) {
            if (!firstLayout) sb.append(",");
            sb.append("\n        \"").append(entry.getKey().getName()).append("\": ").append(entry.getValue());
            firstLayout = false;
        }

        sb.append("\n    }\n}\n");

        if (!FileSystemHelper.writeString(projectRoot.resolve(PROJECT_SETTINGS_FILE), sb.toString())) {
            throw new RuntimeException("Failed to save project settings: " + projectName);
        }

        savedFolderStates.clear();
        savedFolderStates.putAll(folderStates);
        savedFocusedNode = focusedNode;
    }

    private static final Gson GSON_PRETTY = new GsonBuilder().setPrettyPrinting().create();

    /// Export this project as a Minecraft resource pack and data pack so the
    /// running game can load it.
    ///
    /// The two trees are produced differently:
    ///
    /// - **Assets** are engine-native files on disk under `assets/<directory>/…`
    ///   (the project name IS the namespace), so they are copied verbatim into
    ///   `assets/<projectName>/`.
    /// - **Data** entries are NOT copied from disk. The on-disk JSON is written
    ///   by each holder's `EDITOR_CODEC` — a different schema (flattened
    ///   sections, editor-only `preview_*` / `notify_tracks` fields) the engine
    ///   runtime codec rejects. Instead each entry is re-encoded from
    ///   [DataHolder#makeObject] through the engine [Codec] (see
    ///   [#collectDataPackEntries]) and placed at
    ///   `data/<entryNs>/<registryNs>/<registryPath>/…`. The registry-namespace
    ///   folder is skipped only for `minecraft`; Akythera registries live under
    ///   `akythera`, so their entries land under an `akythera/` folder.
    ///
    /// Writes `<projectName>_resources.zip` into `resourcePackDir` and, when
    /// `dataPackDir` is non-null (i.e. a world is open), `<projectName>_data.zip`
    /// into `dataPackDir`. Existing archives of the same name are overwritten,
    /// so a subsequent pack reload picks up the fresh export. Both archives
    /// carry a generated `pack.mcmeta` stamped with the running game's pack
    /// format.
    ///
    /// Returns the resource pack's file name so the caller can select it in the
    /// pack repository (its repository id is `file/<returned name>`).
    public String exportAsPacks(Path resourcePackDir, @Nullable Path dataPackDir) {
        String resourcePackName = exportResourcePack(resourcePackDir);
        if (dataPackDir != null) exportDataPack(dataPackDir, false);

        return resourcePackName;
    }

    /// Write `<projectName>_resources.zip` into `resourcePackDir` (assets tree + `pack.mcmeta` +
    /// optional `pack.png`), overwriting any existing archive. Returns the archive's file name so
    /// the caller can select it in the pack repository (repository id `file/<returned name>`).
    public String exportResourcePack(Path resourcePackDir) {
        String resourcePackName = projectName + "_resources.zip";

        Map<String, byte[]> resourceEntries = new HashMap<>();
        resourceEntries.put("pack.mcmeta", packMeta(PackType.CLIENT_RESOURCES, projectName + " resources"));
        addPackIcon(resourceEntries);

        byte[] soundsJson = exportedSoundsJson();
        if (soundsJson != null) resourceEntries.put("assets/" + projectName + "/" + SOUNDS_JSON, soundsJson);

        exportedLangFiles().forEach((path, content) -> resourceEntries.put("assets/" + projectName + "/" + path, content));

        Set<Path> usedSoundFiles = usedSoundFiles();

        FileSystemHelper.zip(
            resourcePackDir.resolve(resourcePackName),
            resourceEntries,
            List.of(Pair.of("assets/" + projectName, projectRoot.resolve("assets"))),
            file -> exportedAssetContent(file, usedSoundFiles)
        );

        return resourcePackName;
    }

    /// Write the project's data pack into `dataPackDir`, overwriting any existing archive. Data
    /// entries are re-encoded from each holder's [DataHolder#makeObject] through the engine codec
    /// (see [#collectDataPackEntries]) — NOT copied from the editor-schema on-disk JSON.
    ///
    /// `includeAssets` additionally copies the project's `assets/` tree verbatim into
    /// `data/<projectName>/assets/`, where
    /// `com.yesman.akythera.core.asset.DedicatedServerAssetResourceManager` reads it, and names the
    /// archive `<projectName>_datapack_with_assets.zip` instead of `<projectName>_data.zip`.
    public void exportDataPack(Path dataPackDir, boolean includeAssets) {
        Map<String, byte[]> dataEntries = new HashMap<>();
        dataEntries.put("pack.mcmeta", packMeta(PackType.SERVER_DATA, projectName + " data"));
        addPackIcon(dataEntries);

        DynamicOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, StaticRegistryAccess.getRegistryAccess());
        collectDataPackEntries(AkytheraRegistries.DataPack.ANIM_MONTAGE_KEY, AnimationMontage.CODEC, ops, false, dataEntries);
        collectDataPackEntries(AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY, AnimationSequence.CODEC, ops, false, dataEntries);
        collectDataPackEntries(AkytheraRegistries.DataPack.BLEND_SPACE_2D_KEY, BlendSpace2D.CODEC, ops, false, dataEntries);
        collectDataPackEntries(AkytheraRegistries.DataPack.ANIMATOR_SCRIPT_KEY, DataDrivenAnimatorScript.AnimatorScriptBlueprint.CODEC, ops, false, dataEntries);
        collectDataPackEntries(AkytheraRegistries.DataPack.ENTITY_PATCH_KEY, EntityPatchBlueprint.CODEC, ops, true, dataEntries);
        collectDataPackEntries(AkytheraRegistries.DataPack.ITEM_PATCH_KEY, ItemPatchBlueprint.CODEC, ops, true, dataEntries);
        collectDataPackEntries(AkytheraRegistries.DataPack.ABILITY_EFFECT_KEY, AbilityEffect.CODEC, ops, false, dataEntries);
        collectDataPackEntries(AkytheraRegistries.DataPack.INPUT_ACTION_KEY, InputAction.CODEC, ops, false, dataEntries);
        collectDataPackEntries(AkytheraRegistries.DataPack.ABILITY_CUE_KEY, AbilityCueBlueprint.CODEC, ops, false, dataEntries);
        collectDataPackEntries(AkytheraRegistries.DataPack.ABILITY_KEY, AbilityBlueprint.CODEC, ops, false, dataEntries);
        collectDataPackEntries(AkytheraRegistries.DataPack.WIDGET_KEY, WidgetBlueprint.CODEC, ops, false, dataEntries);
        collectDataPackEntries(AkytheraRegistries.DataPack.PARTICLE_PIECE_KEY, ParticleBlueprint.CODEC, ops, false, dataEntries);
        collectDataPackEntries(AkytheraRegistries.DataPack.PARTICLE_SYSTEM_KEY, ParticleSystemBlueprint.CODEC, ops, false, dataEntries);
        collectDataPackEntries(AkytheraRegistries.DataPack.PARTICLE_INITIALIZER_KEY, ParticleInitializerScriptBlueprint.CODEC, ops, false, dataEntries);
        collectDataPackEntries(AkytheraRegistries.DataPack.PARTICLE_UPDATER_KEY, ParticleUpdaterScriptBlueprint.CODEC, ops, false, dataEntries);

        // Editor-editable EnumerableDataBlocks (datablock/<enumName>): property-less `{}` entries
        // encoded via the engine manager's unit map codec, so they reload under <projectName>:<entry>.
        for (DataBlockRegistries.Entry<?, ?> entry : DataBlockRegistries.all()) {
            collectDataBlockEntries(entry, ops, dataEntries);
        }

        String dataPackName = includeAssets
            ? projectName + "_datapack_with_assets.zip"
            : projectName + "_data.zip";

        Set<Path> usedSoundFiles = usedSoundFiles();

        FileSystemHelper.zip(
            dataPackDir.resolve(dataPackName),
            dataEntries,
            includeAssets
                ? List.of(Pair.of("data/" + projectName + "/assets", projectRoot.resolve("assets")))
                : List.of(),
            file -> exportedAssetContent(file, usedSoundFiles)
        );
    }

    /// The editor-only key every `.akmeta` sidecar may carry, stripped on export.
    private static final String PREVIEW_ENTITY_TYPE_KEY = "preview_entity_type";

    /// What `file`, somewhere under the project's `assets` tree, contributes to an export: its own
    /// bytes, or null to leave it out.
    ///
    /// A sidecar is stripped of [#PREVIEW_ENTITY_TYPE_KEY] — the editor's preview binding, which no
    /// pack reads — and left out entirely when nothing else remains in it. A sound event and a
    /// `sounds.json` placed in the project are left out: [#exportedSoundsJson] stands for both. So is a
    /// sound file outside `usedSoundFiles`, which no sound event plays.
    private byte @Nullable [] exportedAssetContent(Path file, Set<Path> usedSoundFiles) {
        Path assetsRoot = projectRoot.resolve("assets");

        if (file.equals(assetsRoot.resolve(SOUNDS_JSON))) return null;
        if (isSoundAsset(file) && file.toString().endsWith(SOUND_EXTENSION) && !usedSoundFiles.contains(file)) return null;

        if (!file.toString().endsWith(AssetLoader.METADATA_EXTENSION)) {
            return FileSystemHelper.readBytes(file);
        }

        if (file.startsWith(assetsRoot.resolve(SOUNDS_DIRECTORY))) return null;

        JsonObject sidecar = FileSystemHelper.readJsonObject(file);

        if (sidecar == null) return null;

        sidecar.remove(PREVIEW_ENTITY_TYPE_KEY);

        if (sidecar.isEmpty()) return null;

        return GSON_PRETTY.toJson(sidecar).getBytes(StandardCharsets.UTF_8);
    }

    /// Export the project's assets as loose files (NOT a zip resource pack) under
    /// `<targetRoot>/assets/<projectName>/…`, mirroring the resource-pack layout so the tree is
    /// drop-in loadable while staying editable on disk. Existing files are overwritten; the source
    /// `assets/` tree is copied verbatim (engine-native files, project name IS the namespace).
    /// No-op when the project has no `assets/` directory.
    public void exportAssetsToDirectory(Path targetRoot) {
        Path assetsSrc = projectRoot.resolve("assets");
        if (!Files.isDirectory(assetsSrc)) return;

        Path assetsDst = targetRoot.resolve("assets").resolve(projectName);
        Set<Path> usedSoundFiles = usedSoundFiles();

        FileSystemHelper.forEachFile(assetsSrc, file -> {
            byte[] content = exportedAssetContent(file, usedSoundFiles);
            if (content != null) FileSystemHelper.writeBytes(assetsDst.resolve(assetsSrc.relativize(file)), content);
        });

        byte[] soundsJson = exportedSoundsJson();
        if (soundsJson != null) FileSystemHelper.writeBytes(assetsDst.resolve(SOUNDS_JSON), soundsJson);

        exportLangFiles(targetRoot);
    }

    /// Writes the project's lang files under `<targetRoot>/assets/<projectName>/lang/`, see
    /// [ProjectLocalization#exportedLangFiles].
    public void exportLangFiles(Path targetRoot) {
        Path assetsDst = targetRoot.resolve("assets").resolve(projectName);

        exportedLangFiles().forEach((path, content) -> FileSystemHelper.writeBytes(assetsDst.resolve(path), content));
    }

    /// The project's lang files by their path under `assets/<projectName>/`.
    private Map<String, byte[]> exportedLangFiles() {
        Map<String, byte[]> files = new HashMap<>();
        Set<String> languages = Minecraft.getInstance().getLanguageManager().getLanguages().keySet();

        localization.exportedLangFiles(languages).forEach((code, langFile) ->
            files.put(LANG_DIRECTORY + code + ".json", GSON_PRETTY.toJson(langFile).getBytes(StandardCharsets.UTF_8)));

        return files;
    }

    /// The `sounds.json` the project's sound events make, one entry per [SoundEventHolder] file. Null
    /// when the project has no sound event.
    private byte @Nullable [] exportedSoundsJson() {
        JsonObject soundsJson = new JsonObject();

        FileSystemHelper.forEachFile(
            projectRoot.resolve("assets").resolve(SOUNDS_DIRECTORY),
            this::isSoundEvent,
            file -> {
                Identifier eventId = getSoundId(file);
                if (eventId == null) return;

                SoundEventHolder.CODEC.encodeStart(JsonOps.INSTANCE, SoundEventHolder.readFrom(file))
                    .resultOrPartial(error -> AkytheraLabs.LOGGER.error("Failed to export sound event {}: {}", eventId, error))
                    .ifPresent(event -> soundsJson.add(eventId.getPath(), event));
            }
        );

        if (soundsJson.isEmpty()) return null;

        return GSON_PRETTY.toJson(soundsJson).getBytes(StandardCharsets.UTF_8);
    }

    /// Adds the project icon — `<projectRoot>/icon.png`, set via the Project Settings modal
    /// ([ProjectSetupModalGenerator]) — to `entries` as the pack's root `pack.png`, so the exported pack
    /// shows it in the pack-selection list. No-op when the project has no icon file; a read failure is logged
    /// and skipped so it can't abort the export.
    private void addPackIcon(Map<String, byte[]> entries) {
        Path icon = projectRoot.resolve(PROJECT_ICON_FILE);
        if (!Files.isRegularFile(icon)) return;

        try {
            entries.put("pack.png", Files.readAllBytes(icon));
        } catch (IOException e) {
            AkytheraLabs.LOGGER.error("Failed to read project icon {} for pack export", icon, e);
        }
    }

    /// Re-encode every entry of one datapack registry into `out`, keyed by its
    /// in-archive path. Each entry is produced from the holder's
    /// [DataHolder#makeObject] via the **engine** runtime `engineCodec`, NOT the
    /// on-disk editor JSON — the editor schema (flattened sections, editor-only
    /// preview fields) is not parseable by the engine. A per-entry encode
    /// failure is logged and skipped so one bad entry can't abort the export.
    ///
    /// A broken entry ([DataHolder#getFailureException]) is skipped before it is even encoded: its
    /// object would be defaults or half-salvaged content.
    ///
    /// When `pathEncodesNamespace` is `true`, the entry's id path is treated as
    /// `<namespace>/<rest>`: the first segment becomes the data-pack namespace
    /// and the remainder the path.
    private <T> void collectDataPackEntries(
        ResourceKey<? extends Registry<T>> registryKey,
        Codec<T> engineCodec,
        DynamicOps<JsonElement> ops,
        boolean pathEncodesNamespace,
        Map<String, byte[]> out
    ) {
        ProjectObjectRegistry<T, DataHolder<T>> registry = getModifiableRegistry(registryKey);
        if (registry == null) return;

        String registryDir = dataPackRegistryDir(registryKey);

        for (Map.Entry<ResourceKey<T>, DataHolder<T>> entry : registry.dataHolderEntrySet()) {
            Identifier id = entry.getKey().identifier();

            Exception failure = entry.getValue().getFailureException();

            if (failure != null) {
                AkytheraLabs.LOGGER.warn("Skipping broken {} for data pack export: {}", id, failure.getMessage());
                continue;
            }

            String namespace = id.getNamespace();
            String entryPath = id.getPath();

            if (pathEncodesNamespace) {
                int slash = entryPath.indexOf('/');

                if (slash < 0) {
                    AkytheraLabs.LOGGER.warn("Skipping {} for data pack export: id path has no namespace segment", id);
                    continue;
                }

                namespace = entryPath.substring(0, slash);
                entryPath = entryPath.substring(slash + 1);
            }

            try {
                JsonElement json = withProjectLookups(() -> engineCodec.encodeStart(ops, entry.getValue().makeObject()).getOrThrow());
                String archivePath = "data/" + namespace + "/" + registryDir + "/" + entryPath + ".json";
                out.put(archivePath, GSON_PRETTY.toJson(json).getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                AkytheraLabs.LOGGER.error("Failed to encode {} for data pack export", id, e);
            }
        }
    }

    /// Captures one data block type's `<T>` so the export uses its engine manager's element codec
    /// (a `MapCodec.unit` — the property-less `{}` form the engine's `EnumerableDataBlockReloadListener`
    /// reads back from `data/<ns>/akythera/datablock/<enumName>/`).
    private <T extends EnumerableDataBlock> void collectDataBlockEntries(
        DataBlockRegistries.Entry<T, ?> entry, DynamicOps<JsonElement> ops, Map<String, byte[]> out
    ) {
        collectDataPackEntries(entry.registryKey(), entry.manager().getMapCodec().codec(), ops, false, out);
    }

    /// Data-pack directory (slash-joined) for a registry: `<registryPath>` when
    /// the registry namespace is the default `minecraft`, otherwise
    /// `<registryNamespace>/<registryPath>`. Akythera's registries live under
    /// the `akythera` namespace, so their entries must be exported beneath an
    /// `akythera/` folder for the data-pack loader to find them.
    private static String dataPackRegistryDir(ResourceKey<? extends Registry<?>> registryKey) {
        Identifier id = registryKey.identifier();
        return id.getNamespace().equals(Identifier.DEFAULT_NAMESPACE)
            ? id.getPath()
            : id.getNamespace() + "/" + id.getPath();
    }

    /// Build the bytes of a `pack.mcmeta` whose supported format range is
    /// pinned to the running game's pack version for `type`. Using
    /// `min_format`/`max_format` (rather than the legacy `pack_format`) matches
    /// the post-1.21.x scheme where the major version exceeds the last
    /// pre-minor version and the legacy field is rejected.
    private static byte[] packMeta(PackType type, String description) {
        PackFormat format = SharedConstants.getCurrentVersion().packVersion(type);
        String mcmeta = String.format("""
            {
              "pack": {
                "description": "%s",
                "min_format": [%d, %d],
                "max_format": [%d, %d]
              }
            }
            """, description, format.major(), format.minor(), format.major(), format.minor());

        return mcmeta.getBytes(StandardCharsets.UTF_8);
    }

    public void clear() {
        // Clear objects registered by the current project
        modifiableRegistries.values().forEach(ProjectObjectRegistry::clearEntry);

        editorMeshes.clear();
        editorArmatures.clear();
        editorAnimations.clear();

        issuedEditorMeshAccessors.clear();
        issuedEditorArmatureAccessors.clear();
        issuedEditorAnimationAccessors.clear();
    }

    public void saveLayoutOfScreen(Screen screen, List<Integer> serializedPanelDividerPositions) {
        layoutDividerSetup.put(screen.getClass(), serializedPanelDividerPositions);
    }

    /// The divider positions saved for `screenClass`, or `defaultRatios` when none are saved or the saved
    /// ones are for a layout with a different number of dividers.
    public List<Integer> getLayoutRatioOrDefault(Class<? extends Screen> screenClass, Integer... defaultRatios) {
        List<Integer> saved = layoutDividerSetup.get(screenClass);

        return saved != null && saved.size() == defaultRatios.length ? saved : List.of(defaultRatios);
    }

    public record SemanticVersioning(int major, int minor, int patch, String preRelease) {
        private static final String INITIAL_VERSION = "0.1.0+alpha.1";

        private static final Pattern SEMVER_PATTERN = Pattern.compile(
            "^(?<major>\\d+)\\.(?<minor>\\d+)\\.(?<patch>\\d+)(?:[+-](?<pre>[a-zA-Z0-9.]+))?$"
        );

        public static SemanticVersioning parse(String versionString) {
            Matcher matcher = SEMVER_PATTERN.matcher(versionString.trim());

            if (!matcher.matches()) {
                throw new IllegalArgumentException("Invalid Semantic Versioning format: " + versionString);
            }

            return new SemanticVersioning(
                Integer.parseInt(matcher.group("major")),
                Integer.parseInt(matcher.group("minor")),
                Integer.parseInt(matcher.group("patch")),
                ParseUtils.nvl(matcher.group("pre"), "")
            );
        }

        @Override
        public String toString() {
            if (preRelease.isEmpty()) {
                return String.format("%d.%d.%d", major, minor, patch);
            }
            return String.format("%d.%d.%d+%s", major, minor, patch, preRelease);
        }
    }

    // Below are the implementations of dynamic assets that data loading is conducted on
    // its first reference. Have each custom `AssetAccessor` implementation and read assets
    // from the project's root.

    /// Transform file path to accessor id
    private Identifier getAccessorIdentifierFromPath(String directory, Path path) {
        String fileSystemDirectory = directory.replaceAll("/", "\\\\");

        if (!path.toString().contains(fileSystemDirectory)) {
            throw new IllegalArgumentException(
                "Discovered asset path " + path +
                " doesn't have directory " + fileSystemDirectory +
                " on its path"
            );
        }

        return Identifier.fromNamespaceAndPath(
            projectName,
            ParseUtils.trimExtensions(
                path.toString().substring(
                    path.toString().indexOf(fileSystemDirectory)
                ).replaceAll("\\\\", "/"),
                AssetLoader.BINARY_EXTENSION, "json"
            )
        );
    }

    /// Outcome of a `load*ToCurrentProject` call: exactly one of [#accessor] on success or [#error],
    /// a human-readable failure message, is non-null.
    public record AssetLoadResult<A>(@Nullable A accessor, @Nullable String error) {
        public static <A> AssetLoadResult<A> success(A accessor) {
            return new AssetLoadResult<>(accessor, null);
        }

        public static <A> AssetLoadResult<A> failure(String error) {
            return new AssetLoadResult<>(null, error);
        }

        public boolean failed() {
            return accessor == null;
        }
    }

    /// Never-null message for an exception that may carry a null `getMessage()`.
    private static String errorMessageOf(Exception e) {
        String message = e.getMessage();
        return message != null ? message : e.getClass().getSimpleName();
    }

    private static String copyFailureMessage(Path path) {
        return "Could not copy \"" + path.getFileName() + "\" into the project.";
    }

    /// Copies `from` over `to`, then runs `parse` against the copied file. On any failure the
    /// destination is restored to exactly what it was before the call: the previous file is
    /// moved back when `to` already existed (a failed overwrite keeps the original intact), or
    /// the freshly written file is removed when this was a new import. `label` only names the
    /// asset type for the log line.
    private <A> AssetLoadResult<A> importAssetFile(Path from, Path to, String label, Supplier<AssetLoadResult<A>> parse) {
        Path backup = FileSystemHelper.moveAside(to);

        if (!FileSystemHelper.copyReplace(from, to)) {
            FileSystemHelper.rollback(backup, to);
            return AssetLoadResult.failure(copyFailureMessage(from));
        }

        try {
            AssetLoadResult<A> result = parse.get();

            if (result.failed()) {
                FileSystemHelper.rollback(backup, to);
            } else {
                FileSystemHelper.commit(backup);
            }

            return result;
        } catch (Exception e) {
            AkytheraLabs.LOGGER.error("Failed to load {} {}", label, from, e);
            FileSystemHelper.rollback(backup, to);
            return AssetLoadResult.failure(errorMessageOf(e));
        }
    }

    // ******************************** Editor textures ********************************

    /// Copies the texture file `path` into the current project at `toCopy`, then checks the copy reads
    /// as a PNG image. Answers the copied file. On failure the destination is restored to its prior
    /// state — see [#importAssetFile].
    public AssetLoadResult<Path> loadTextureToCurrentProject(Path path, Path toCopy) {
        return importAssetFile(path, toCopy, "texture", () -> {
            try (InputStream in = Files.newInputStream(toCopy)) {
                NativeImage.read(in).close();
            } catch (IOException e) {
                return AssetLoadResult.failure("Could not read \"" + path.getFileName() + "\" as a PNG image: " + errorMessageOf(e));
            }

            return AssetLoadResult.success(toCopy);
        });
    }

    /// Copies the texture sidecar `path` into the current project at `toCopy`, then checks the copy
    /// reads as a json object. Answers the copied file. On failure the destination is restored to its
    /// prior state — see [#importAssetFile].
    public AssetLoadResult<Path> loadTextureMetadataToCurrentProject(Path path, Path toCopy) {
        return importAssetFile(path, toCopy, "texture metadata", () ->
            FileSystemHelper.readJsonObject(toCopy) == null
                ? AssetLoadResult.failure("Could not read \"" + path.getFileName() + "\" as a json object.")
                : AssetLoadResult.success(toCopy)
        );
    }

    /// Copies the sound file `path` into the current project at `toCopy`, then checks the copy reads as
    /// Ogg Vorbis. Answers the copied file. On failure the destination is restored to its prior state —
    /// see [#importAssetFile].
    public AssetLoadResult<Path> loadSoundToCurrentProject(Path path, Path toCopy) {
        return importAssetFile(path, toCopy, "sound", () -> {
            byte[] bytes = FileSystemHelper.readBytes(toCopy);

            if (bytes == null || ProjectSounds.oggDuration(bytes) <= 0.0F) {
                return AssetLoadResult.failure("Could not read \"" + path.getFileName() + "\" as an Ogg Vorbis sound.");
            }

            return AssetLoadResult.success(toCopy);
        });
    }

    /// `ns:textures/block/stone.png` as the texture asset `ns:block/stone`. A path off that layout keeps
    /// itself as the asset id.
    public static ClientAsset.ResourceTexture resourceTextureOf(Identifier texturePath) {
        String path = texturePath.getPath();
        String prefix = TEXTURES_DIRECTORY;
        String suffix = ".png";

        if (!path.startsWith(prefix) || !path.endsWith(suffix)) {
            return new ClientAsset.ResourceTexture(texturePath, texturePath);
        }

        return new ClientAsset.ResourceTexture(
            texturePath.withPath(path.substring(prefix.length(), path.length() - suffix.length())),
            texturePath
        );
    }

    // ******************************** Editor mesh manager ********************************

    private final Map<Identifier, Mesh<?>> editorMeshes = new HashMap<>();
    private final Map<Identifier, AssetAccessor<? extends Mesh<?>>> issuedEditorMeshAccessors = new HashMap<>();

    /// Copies the dropped file into the current project, then parses the copy into a [Mesh].
    /// The accessor identifier is derived from the destination path (the source may live
    /// anywhere on disk), so the copy must happen first. On failure the destination is
    /// restored to its prior state — see [#importAssetFile].
    public AssetLoadResult<AssetAccessor<? extends Mesh<?>>> loadMeshToCurrentProject(Path path, Path toCopy) {
        return importAssetFile(path, toCopy, "mesh", () -> {
            AkytheraLabs.LOGGER.info("Importing {}.", path.getFileName());
            Identifier id = getAccessorIdentifierFromPath(MeshManager.DIRECTORY, toCopy);
            SingleFileResourceManager rm = new SingleFileResourceManager(toCopy);

            Mesh<?> mesh;

            try {
                // Tries to load skeletal mesh first
                mesh = MeshLoader.getLoader(id, rm, AssetLoader::readMetadata).loadSkeletalMesh(SkeletalMesh::new);
                AkytheraLabs.LOGGER.info("{} is skeletal mesh.", path.getFileName());
            } catch (ResourceLoadingFailureException e) {
                AkytheraLabs.LOGGER.info("{} is not a skeletal mesh type. Now try load as static mesh.", path.getFileName());
                mesh = MeshLoader.getLoader(id, rm, AssetLoader::readMetadata).loadStaticMesh(StaticMesh::new);
            }

            if (mesh == null) {
                return AssetLoadResult.failure("Could not read \"" + path.getFileName() + "\" as a mesh.");
            }

            editorMeshes.put(id, mesh);

            return AssetLoadResult.success(mesh instanceof SkeletalMesh
                ? issuedEditorMeshAccessors.computeIfAbsent(id, k -> new EditorSkeletalMeshAccessor<>(k, SkeletalMesh::new))
                : issuedEditorMeshAccessors.computeIfAbsent(id, k -> new EditorStaticMeshAccessor<>(k, StaticMesh::new)));
        });
    }

    @SuppressWarnings("unchecked")
    public AssetAccessor<? extends StaticMesh> getEditorStaticMeshAccessorByPath(Path path) {
        Identifier id = getAccessorIdentifierFromPath(MeshManager.DIRECTORY, path);
        return (AssetAccessor<? extends StaticMesh>)
            issuedEditorMeshAccessors.computeIfAbsent(id, k -> new EditorStaticMeshAccessor<>(k, StaticMesh::new));
    }

    @SuppressWarnings("unchecked")
    public AssetAccessor<? extends SkeletalMesh> getEditorSkeletalMeshAccessorByPath(Path path) {
        Identifier id = getAccessorIdentifierFromPath(MeshManager.DIRECTORY, path);
        return (AssetAccessor<? extends SkeletalMesh>)
            issuedEditorMeshAccessors.computeIfAbsent(id, k -> new EditorSkeletalMeshAccessor<>(k, SkeletalMesh::new));
    }

    /// Issues the accessor matching what the file at `path` actually holds, detected by
    /// [#isEditorMeshSkeletal]: [EditorSkeletalMeshAccessor] for a skeletal mesh,
    /// [EditorStaticMeshAccessor] otherwise.
    public AssetAccessor<? extends Mesh<?>> getEditorMeshAccessorByPath(Path path) {
        Identifier id = getAccessorIdentifierFromPath(MeshManager.DIRECTORY, path);
        AssetAccessor<? extends Mesh<?>> issued = issuedEditorMeshAccessors.get(id);

        if (issued != null) {
            return issued;
        }

        AssetAccessor<? extends Mesh<?>> accessor = isEditorMeshSkeletal(path)
            ? new EditorSkeletalMeshAccessor<>(id, SkeletalMesh::new)
            : new EditorStaticMeshAccessor<>(id, StaticMesh::new);
        issuedEditorMeshAccessors.put(id, accessor);

        return accessor;
    }

    /// Detect whether the on-disk mesh at `path` is skeletal (vs static), loading and caching it into
    /// [#editorMeshes] on first call. Detection matches [#loadMeshToCurrentProject]'s: a file parses
    /// as skeletal when it carries armature data, otherwise it is static. Returns false when the mesh
    /// is static or cannot be read.
    public boolean isEditorMeshSkeletal(Path path) {
        Identifier id = getAccessorIdentifierFromPath(MeshManager.DIRECTORY, path);
        Mesh<?> mesh = editorMeshes.computeIfAbsent(id, this::loadEditorMeshDetectingType);
        return mesh instanceof SkeletalMesh;
    }

    /// Parses a project mesh, trying skeletal first and then static. Returns null when neither
    /// succeeds.
    private @Nullable Mesh<?> loadEditorMeshDetectingType(Identifier id) {
        try {
            return MeshLoader.getLoader(id, PROJECT_RESOURCE_MANAGER, AssetLoader::readMetadata)
                .loadSkeletalMesh(SkeletalMesh::new);
        } catch (ResourceLoadingFailureException notSkeletal) {
            try {
                return MeshLoader.getLoader(id, PROJECT_RESOURCE_MANAGER, AssetLoader::readMetadata)
                    .loadStaticMesh(StaticMesh::new);
            } catch (ResourceLoadingFailureException notStatic) {
                AkytheraLabs.LOGGER.error("Failed to read mesh {} as skeletal or static", id, notStatic);
                return null;
            }
        }
    }

    public Stream<AssetAccessor<? extends Mesh<?>>> listIssuedEditorMeshAccessors() {
        return issuedEditorMeshAccessors.values().stream();
    }

    /// Drops the parsed mesh so the next reference re-reads the file — the sidecar metadata is
    /// folded in at parse time.
    public void invalidateEditorMesh(Identifier id) {
        Mesh<?> mesh = editorMeshes.remove(id);

        // Explicit call to release buffers
        if (mesh != null) mesh.close();
    }

    @SuppressWarnings("unchecked")
    public <T extends SkeletalMesh> AssetAccessor<T> getOrIssueEditorSkeletalMeshAccessor(
        Identifier id,
        MeshLoader.MeshConstructor<T, SkeletalMesh.SkeletalMeshBuilder> meshConstructor
    ) {
        return (AssetAccessor<T>) issuedEditorMeshAccessors.computeIfAbsent(
            id, k -> new EditorSkeletalMeshAccessor<>(k, meshConstructor)
        );
    }

    /// What the two mesh accessors share: the identifier a rename writes through in place, and the
    /// sidecar view. [AkytheraProject] issues one accessor per identifier, so there is one
    /// [MeshMetadataHolder] per identifier — every screen editing this mesh edits the same values.
    public abstract static class EditorMeshAccessor<T extends Mesh<?>> extends EditorReferenceable<MeshMetadataHolder> implements AssetAccessor<T> {
        private Identifier identifier;

        private EditorMeshAccessor(Identifier identifier) {
            this.identifier = identifier;
        }

        @Override
        public Identifier identifier() {
            return identifier;
        }

        @Override
        void setIdentifier(Identifier identifier) {
            this.identifier = identifier;
        }

        @Override
        MeshMetadataHolder readMetadata() {
            return MeshMetadataHolder.readFrom(this);
        }

        @Override
        public boolean isResourcePresent() {
            return PROJECT_RESOURCE_MANAGER.getResource(identifier()).isPresent();
        }

        @Override
        public String toString() {
            return identifier.toString();
        }

        @Override
        public int hashCode() {
            return identifier.hashCode();
        }

        @SuppressWarnings("rawtypes")
        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            } else if (obj instanceof AssetAccessor other) {
                return identifier.equals(other.identifier());
            } else if (obj instanceof Identifier rl) {
                return identifier.equals(rl);
            } else if (obj instanceof String name) {
                return identifier.toString().equals(name);
            } else {
                return false;
            }
        }
    }

    private static final class EditorStaticMeshAccessor<T extends StaticMesh> extends EditorMeshAccessor<T> {
        private final MeshLoader.MeshConstructor<T, Mesh.CommonMeshBuilder<StaticMesh.StaticMeshPart>> meshConstructor;

        private EditorStaticMeshAccessor(
            Identifier identifier,
            MeshLoader.MeshConstructor<T, Mesh.CommonMeshBuilder<StaticMesh.StaticMeshPart>> meshConstructor
        ) {
            super(identifier);
            this.meshConstructor = meshConstructor;
        }

        @Override @SuppressWarnings("unchecked")
        public T get() {
            return (T) getCurrentProject().editorMeshes.computeIfAbsent(
                identifier(),
                id -> MeshLoader.getLoader(id, PROJECT_RESOURCE_MANAGER, AssetLoader::readMetadata)
                    .loadStaticMesh(meshConstructor)
            );
        }

        @Override
        public Type type() {
            return Type.STATIC_MESH;
        }
    }

    private static final class EditorSkeletalMeshAccessor<T extends SkeletalMesh> extends EditorMeshAccessor<T> {
        private final MeshLoader.MeshConstructor<T, SkeletalMesh.SkeletalMeshBuilder> meshConstructor;

        private EditorSkeletalMeshAccessor(
            Identifier identifier,
            MeshLoader.MeshConstructor<T, SkeletalMesh.SkeletalMeshBuilder> meshConstructor
        ) {
            super(identifier);
            this.meshConstructor = meshConstructor;
        }

        @Override @SuppressWarnings("unchecked")
        public T get() {
            return (T) getCurrentProject().editorMeshes.computeIfAbsent(
                identifier(),
                id -> MeshLoader.getLoader(id, PROJECT_RESOURCE_MANAGER, AssetLoader::readMetadata)
                    .loadSkeletalMesh(meshConstructor)
            );
        }

        @Override
        public Type type() {
            return Type.SKELETAL_MESH;
        }
    }

    // ************************** Editor armature manager **************************

    private final Map<Identifier, Armature> editorArmatures = new HashMap<>();
    private final Map<Identifier, AssetAccessor<? extends Armature>> issuedEditorArmatureAccessors = new HashMap<>();

    /// Copies the dropped file into the current project, then parses the copy into an
    /// [Armature]. The accessor identifier is derived from the destination path (the source
    /// may live anywhere on disk), so the copy must happen first. On failure the destination
    /// is restored to its prior state — see [#importAssetFile].
    public AssetLoadResult<AssetAccessor<? extends Armature>> loadArmatureToCurrentProject(Path path, Path toCopy) {
        return importAssetFile(path, toCopy, "armature", () -> {
            Identifier id = getAccessorIdentifierFromPath(ArmatureManager.DIRECTORY, toCopy);
            SingleFileResourceManager rm = new SingleFileResourceManager(toCopy);
            Armature armature = ArmatureLoader.getLoader(id, rm).loadArmature(Armature::new);

            editorArmatures.put(id, armature);

            return AssetLoadResult.success(issuedEditorArmatureAccessors.computeIfAbsent(
                id, k -> new EditorArmatureAccessor<>(k, Armature::new)
            ));
        });
    }

    public AssetAccessor<? extends Armature> getEditorArmatureAccessorByPath(Path path) {
        Identifier id = getAccessorIdentifierFromPath(ArmatureManager.DIRECTORY, path);
        return issuedEditorArmatureAccessors.computeIfAbsent(
            id, k -> new EditorArmatureAccessor<>(k, Armature::new)
        );
    }

    public Stream<AssetAccessor<? extends Armature>> listIssuedEditorArmatureAccessors() {
        return issuedEditorArmatureAccessors.values().stream();
    }

    /// Drops the parsed armature so the next reference re-reads the file.
    public void invalidateEditorArmature(Identifier id) {
        editorArmatures.remove(id);
    }

    @SuppressWarnings("unchecked")
    public <T extends Armature> AssetAccessor<T> getOrIssueEditorArmatureAccessor(
        Identifier id,
        ArmatureLoader.ArmatureConstructor<T> armatureConstructor
    ) {
        return (AssetAccessor<T>) issuedEditorArmatureAccessors.computeIfAbsent(
            id, k -> new EditorArmatureAccessor<>(k, armatureConstructor)
        );
    }

    public static final class EditorArmatureAccessor<T extends Armature> extends EditorReferenceable<ArmatureMetadataHolder> implements AssetAccessor<T> {
        private Identifier identifier;
        private final ArmatureLoader.ArmatureConstructor<T> armatureConstructor;

        EditorArmatureAccessor(Identifier identifier, ArmatureLoader.ArmatureConstructor<T> armatureConstructor) {
            this.identifier = identifier;
            this.armatureConstructor = armatureConstructor;
        }

        @Override
        ArmatureMetadataHolder readMetadata() {
            return ArmatureMetadataHolder.readFrom(this);
        }

        @Override public Identifier identifier() { return identifier; }
        @Override void setIdentifier(Identifier identifier) { this.identifier = identifier; }

        @Override @SuppressWarnings("unchecked")
        public T get() {
            return (T) getCurrentProject().editorArmatures.computeIfAbsent(identifier, id ->
                ArmatureLoader.getLoader(id, PROJECT_RESOURCE_MANAGER).loadArmature(armatureConstructor)
            );
        }

        @Override
        public Type type() {
            return Type.ARMATURE;
        }

        @Override
        public boolean isResourcePresent() {
            return PROJECT_RESOURCE_MANAGER.getResource(identifier()).isPresent();
        }

        @Override
        public String toString() {
            return identifier.toString();
        }

        @Override
        public int hashCode() {
            return identifier.hashCode();
        }

        @SuppressWarnings("rawtypes")
        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            } else if (obj instanceof AssetAccessor other) {
                return identifier.equals(other.identifier());
            } else if (obj instanceof Identifier rl) {
                return identifier.equals(rl);
            } else if (obj instanceof String name) {
                return identifier.toString().equals(name);
            } else {
                return false;
            }
        }
    }

    // ************************** Editor animation manager **************************

    private final Map<Identifier, AnimationClip> editorAnimations = new HashMap<>();
    private final Map<Identifier, AssetAccessor<AnimationClip>> issuedEditorAnimationAccessors = new HashMap<>();

    /// Identifiers whose clip is being read right now. An additive clip reads its reference pose
    /// mid-load, so [#editorAnimations] is re-entered while one entry is still being produced.
    private final Set<Identifier> loadingEditorAnimations = new HashSet<>();

    /// The [AssetLoader.MetadataLoader] an animation read out of this project is loaded with. The
    /// engine's `Metadata.CODEC` arrives in `engineCodec` and is dropped for
    /// [EditorAssetAccessors#ANIMATION_METADATA_CODEC], so a `ref_pose_provider` naming a project
    /// animation resolves to that project's accessor rather than to one reading the game's packs.
    private static AnimationLoader.Metadata readProjectAnimationMetadata(
        final Identifier assetPath,
        final ResourceManager resourceManager,
        final Codec<AnimationLoader.Metadata> engineCodec,
        final AnimationLoader.Metadata orElse
    ) {
        return AssetLoader.readMetadata(
            assetPath, resourceManager, EditorAssetAccessors.ANIMATION_METADATA_CODEC, orElse);
    }

    /// Copies the dropped file into the current project, then parses the copy into an
    /// [AnimationClip]. The accessor identifier is derived from the destination path (the
    /// source may live anywhere on disk, e.g. the user's desktop), so the copy must happen
    /// first. On failure the destination is restored to its prior state — see [#importAssetFile].
    public AssetLoadResult<AssetAccessor<AnimationClip>> loadAnimationToCurrentProject(Path path, Path toCopy) {
        return importAssetFile(path, toCopy, "animation", () -> {
            Identifier id = getAccessorIdentifierFromPath(AnimationManager.DIRECTORY, toCopy);
            SingleFileResourceManager rm = new SingleFileResourceManager(toCopy);
            AnimationLoader loader = AnimationLoader.getLoader(id, rm, AssetLoader::readMetadata);

            if (loader instanceof JsonAssetLoader) {
                throw new RuntimeException("Akythera Labs doesn't support json animationsToReplace. Please use animation retargeter to export your animationsToReplace as binary assets");
            }

            AnimationClip animClip = loader.loadAnimationClip(null);
            editorAnimations.put(id, animClip);
            return AssetLoadResult.success(issuedEditorAnimationAccessors.computeIfAbsent(id, EditorAnimationAccessor::new));
        });
    }

    public AssetAccessor<AnimationClip> getEditorAnimationAccessorByPath(Path path) {
        Identifier id = getAccessorIdentifierFromPath(AnimationManager.DIRECTORY, path);
        return issuedEditorAnimationAccessors.computeIfAbsent(id, EditorAnimationAccessor::new);
    }

    public Stream<AssetAccessor<AnimationClip>> listIssuedEditorAnimationAccessors() {
        return issuedEditorAnimationAccessors.values().stream();
    }

    /// Drops the parsed clip so the next reference re-reads the file — the sidecar metadata decides
    /// whether it is read raw or as an additive. The [EditorAnimationAccessor#metadata] holder is
    /// left alone: it is the in-memory edit state the file was just written from.
    public void invalidateEditorAnimation(Identifier id) {
        editorAnimations.remove(id);
    }

    // *********************** Editor physics assets ***********************

    private final Map<Identifier, EditorArmaturePhysicsAccessor> issuedEditorArmaturePhysicsAccessors = new HashMap<>();
    private final Map<Identifier, ClothPhysicsDataHolder> clothPhysicsHolders = new HashMap<>();

    public EditorArmaturePhysicsAccessor getEditorArmaturePhysicsAccessorByPath(Path path) {
        return getOrIssueEditorArmaturePhysicsAccessor(getAccessorIdentifierFromPath(ArmaturePhysicsManager.DIRECTORY, path));
    }

    public EditorArmaturePhysicsAccessor getOrIssueEditorArmaturePhysicsAccessor(Identifier id) {
        return issuedEditorArmaturePhysicsAccessors.computeIfAbsent(id, EditorArmaturePhysicsAccessor::new);
    }

    public Stream<AssetAccessor<? extends ArmaturePhysicsData>> listIssuedEditorArmaturePhysicsAccessors() {
        return issuedEditorArmaturePhysicsAccessors.values().stream()
            .map(accessor -> (AssetAccessor<? extends ArmaturePhysicsData>) accessor);
    }

    public Stream<AssetAccessor<? extends ArmaturePhysicsData>> listAllArmaturePhysicsAccessors() {
        return mergeAccessors(
            ArmaturePhysicsManager.getInstance().listRegisteredAccessors(),
            listIssuedEditorArmaturePhysicsAccessors()
        );
    }

    /// The armature an armature physics asset is keyed by, as the editor holds it.
    public AssetAccessor<? extends Armature> getArmatureOfPhysics(AssetAccessor<? extends ArmaturePhysicsData> physics) {
        Identifier armatureId = armatureIdOfPhysics(physics.identifier());

        return armatureId == null
            ? ArmatureManager.EMPTY_ARMATURE_ACCESSOR
            : getOrIssueEditorArmatureAccessor(armatureId, Armature::new);
    }

    /// The skeletal mesh a cloth file under [ClothPhysicsLoader#DIRECTORY] belongs to: the mesh at the
    /// same path under [MeshManager#DIRECTORY].
    public AssetAccessor<? extends SkeletalMesh> getClothMeshAccessorByPath(Path path) {
        Identifier clothId = getAccessorIdentifierFromPath(ClothPhysicsLoader.DIRECTORY, path);
        Identifier meshId = Identifier.fromNamespaceAndPath(
            clothId.getNamespace(),
            MeshManager.DIRECTORY + clothId.getPath().substring(ClothPhysicsLoader.DIRECTORY.length())
        );

        return getOrIssueEditorSkeletalMeshAccessor(meshId, SkeletalMesh::new);
    }

    /// The cloth of `mesh` as the editor holds it, read off disk on first access — an empty cloth when
    /// the mesh has none yet.
    public ClothPhysicsDataHolder getClothPhysicsHolder(AssetAccessor<? extends SkeletalMesh> mesh) {
        return clothPhysicsHolders.computeIfAbsent(mesh.identifier(), _ -> ClothPhysicsDataHolder.readFrom(mesh));
    }

    /// Whether `mesh` has a cloth file on disk. A preview asks before building a simulation for it.
    public boolean hasClothFile(AssetAccessor<? extends SkeletalMesh> mesh) {
        Identifier clothId = ClothPhysicsLoader.clothPathOf(mesh.identifier());

        return clothId != null && Files.isRegularFile(getBinaryAssetFileOf(clothId));
    }

    /// An armature physics asset of this project. [#get] answers the holder's current, unsaved state.
    public static final class EditorArmaturePhysicsAccessor extends EditorReferenceable<ArmaturePhysicsDataHolder> implements AssetAccessor<ArmaturePhysicsData> {
        private Identifier identifier;

        private EditorArmaturePhysicsAccessor(Identifier identifier) {
            this.identifier = identifier;
        }

        @Override
        ArmaturePhysicsDataHolder readMetadata() {
            return ArmaturePhysicsDataHolder.readFrom(this);
        }

        @Override public Identifier identifier() { return identifier; }
        @Override void setIdentifier(Identifier identifier) { this.identifier = identifier; }

        @Override
        public ArmaturePhysicsData get() {
            return metadata().makeObject();
        }

        @Override
        public Type type() {
            return Type.ARMATURE_PHYSICS;
        }

        @Override
        public boolean isResourcePresent() {
            return Files.isRegularFile(getJsonAssetFileOf(identifier));
        }

        @Override
        public String toString() {
            return identifier.toString();
        }

        @Override
        public int hashCode() {
            return identifier.hashCode();
        }

        @SuppressWarnings("rawtypes")
        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            } else if (obj instanceof AssetAccessor other) {
                return identifier.equals(other.identifier());
            } else if (obj instanceof Identifier rl) {
                return identifier.equals(rl);
            } else if (obj instanceof String name) {
                return identifier.toString().equals(name);
            } else {
                return false;
            }
        }
    }


    // ************************** Merged accessor listings ***************************
    // Mod-registered + project-issued, deduped by identifier (project wins on collision).

    public Stream<AssetAccessor<? extends Mesh<?>>> listAllMeshAccessors() {
        return mergeAccessors(
            MeshManager.getInstance().listRegisteredAccessors(),
            listIssuedEditorMeshAccessors()
        );
    }

    public Stream<AssetAccessor<? extends Armature>> listAllArmatureAccessors() {
        return mergeAccessors(
            ArmatureManager.getInstance().listRegisteredAccessors(),
            listIssuedEditorArmatureAccessors()
        );
    }

    @SuppressWarnings("unchecked")
    public Stream<AssetAccessor<AnimationClip>> listAllAnimationAccessors() {
        return mergeAccessors(
            AnimationManager.getInstance().listRegisteredAccessors()
                .map(a -> (AssetAccessor<AnimationClip>) a), // type cast to be always `AnimationClip`
            listIssuedEditorAnimationAccessors()
        );
    }

    private static <T extends AssetAccessor<?>> Stream<T> mergeAccessors(Stream<T> registered, Stream<T> issued) {
        return Stream.concat(registered, issued)
            .collect(Collectors.toMap(
                AssetAccessor::identifier, a -> a, (_, issuedOne) -> issuedOne, LinkedHashMap::new))
            .values().stream();
    }

    public AssetAccessor<AnimationClip> getOrIssueEditorAnimationAccessor(Identifier id) {
        return issuedEditorAnimationAccessors.computeIfAbsent(id, EditorAnimationAccessor::new);
    }

    /// Accessor for animation data stored in [AnimationClip]
    public static final class EditorAnimationAccessor extends EditorReferenceable<AnimationMetadataHolder> implements AssetAccessor<AnimationClip> {
        private Identifier identifier;

        private EditorAnimationAccessor(Identifier identifier) {
            this.identifier = identifier;
        }

        @Override
        AnimationMetadataHolder readMetadata() {
            return AnimationMetadataHolder.readFrom(this);
        }

        @Override
        public Identifier identifier() {
            return identifier;
        }

        @Override
        void setIdentifier(Identifier identifier) {
            this.identifier = identifier;
        }

        @Override
        public AnimationClip get() {
            AkytheraProject project = getCurrentProject();
            AnimationClip cached = project.editorAnimations.get(identifier);

            if (cached != null) {
                return cached;
            }

            if (!project.loadingEditorAnimations.add(identifier)) {
                throw new ResourceLoadingFailureException(
                    identifier, "Reference pose cycle: this animation reaches itself through 'ref_pose_provider'.");
            }

            try {
                AnimationClip clip = AnimationLoader
                    .getLoader(identifier, PROJECT_RESOURCE_MANAGER, AkytheraProject::readProjectAnimationMetadata)
                    .loadAnimationClip(null);

                project.editorAnimations.put(identifier, clip);

                return clip;
            } finally {
                project.loadingEditorAnimations.remove(identifier);
            }
        }

        @Override
        public Type type() {
            return Type.ANIMATION;
        }

        @Override
        public boolean isResourcePresent() {
            return PROJECT_RESOURCE_MANAGER.getResource(identifier()).isPresent();
        }

        @Override
        public String toString() {
            return identifier.toString();
        }

        @Override
        public int hashCode() {
            return identifier.hashCode();
        }

        @SuppressWarnings("rawtypes")
        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            } else if (obj instanceof AssetAccessor other) {
                return identifier.equals(other.identifier());
            } else if (obj instanceof Identifier rl) {
                return identifier.equals(rl);
            } else if (obj instanceof String name) {
                return identifier.toString().equals(name);
            } else {
                return false;
            }
        }
    }

    /// Shared base for the four editor asset accessors. Subclasses own the actual `identifier`
    /// field (each has its own constructor shape). A [#rename] mutates that id in place; because
    /// each accessor is issued one-per-id and shared by every referrer, the rename propagates to
    /// referrers for free (disk is re-saved by the asset rename scan).
    private static abstract class EditorReferenceable<H> implements EditorReference {
        /// The sidecar view, read on first call and kept from then on. One accessor is issued per
        /// identifier, so there is one holder per identifier.
        private @Nullable H metadata;

        /// Subclasses mutate their own identifier field.
        abstract void setIdentifier(Identifier id);

        /// Reads this accessor's sidecar off disk.
        abstract H readMetadata();

        public H metadata() {
            if (metadata == null) {
                metadata = readMetadata();
            }

            return metadata;
        }

        void rename(Identifier newId) {
            setIdentifier(newId);
        }
    }
}
