package com.yesman.akytheralabs;

import com.google.gson.*;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.yesman.akythera.core.asset.loader.binary.BinaryAssetLoader;
import com.yesman.akythera.core.blueprint.EntityPatchBlueprint;
import com.yesman.akythera.core.blueprint.ItemPatchBlueprint;
import com.yesman.akythera.core.blueprint.script.DataDrivenAnimatorScript;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.asset.animation.AnimationManager;
import com.yesman.akythera.api.asset.armature.ArmatureManager;
import com.yesman.akythera.api.lifecycle.ComponentsFactory;
import com.yesman.akythera.client.api.mesh.MeshManager;
import com.yesman.akythera.client.core.asset.loader.MeshLoader;
import com.yesman.akythera.client.core.asset.mesh.Mesh;
import com.yesman.akythera.client.core.asset.mesh.SkeletalMesh;
import com.yesman.akythera.client.core.asset.mesh.StaticMesh;
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
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.EnumerableDataBlock;
import com.yesman.akythera.core.util.EnumerableDataBlockManager;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.dataholder.DanglingHolder;
import com.yesman.akytheralabs.dataholder.DataHolder;
import com.yesman.akytheralabs.dataholder.EditorReference;
import com.yesman.akytheralabs.dataholder.ProjectObjectRegistry;
import com.yesman.akytheralabs.akythera.asset.AnimationMetadataHolder;
import com.yesman.akytheralabs.akythera.asset.MeshMetadataHolder;
import com.yesman.akytheralabs.akythera.animation.AnimationMontageHolder;
import com.yesman.akytheralabs.akythera.animation.AnimationSequenceHolder;
import com.yesman.akytheralabs.akythera.animation.AnimatorScriptHolder;
import com.yesman.akytheralabs.akythera.entitypatch.EntityPatchBlueprintHolder;
import com.yesman.akytheralabs.akythera.itempatch.ItemPatchBlueprintHolder;
import com.yesman.akytheralabs.akythera.animation.BlendSpace2DHolder;
import com.yesman.akytheralabs.dataholder.DataBlockHolder;
import com.yesman.akytheralabs.dataholder.DataBlockRegistries;
import com.yesman.akytheralabs.editor.historytrack.FileCreation;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.gui.panel.AdjustablePanelManager;
import com.yesman.akytheralabs.gui.screen.MainEditorScreen;
import com.yesman.akytheralabs.gui.widget.AwaitIconModal;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
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

    /// `.../walk.efasset` → `.../walk.akmeta`. The path-side twin of [#getMetadataFileOf], for
    /// the file operations that hold a path rather than an identifier.
    private static Path getMetadataFileBeside(Path assetFile) {
        return assetFile.resolveSibling(
            FileSystemHelper.stem(assetFile.getFileName().toString()) + AssetLoader.METADATA_EXTENSION
        );
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
                    "layouts": {}
                }
            """, projectName, initVersion);
            Files.writeString(projectRoot.resolve(PROJECT_SETTINGS_FILE), settings);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create project directory structure for: " + projectName, e);
        }

        currentProject = new AkytheraProject(projectName, initVersion, projectRoot);
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
        Map<String, String> armatureMappings = new LinkedHashMap<>();

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

            if (obj.has("armature_mappings")) {
                for (Map.Entry<String, JsonElement> e : obj.getAsJsonObject("armature_mappings").entrySet()) {
                    armatureMappings.put(e.getKey(), e.getValue().getAsString());
                }
            }
        } catch (IOException ignored) {}

        currentProject = new AkytheraProject(name, version, projectRoot, layouts, folderStates, focusedNode);
        currentProject.lastExportDataPath = exportDataPath;
        currentProject.lastExportResourcePath = exportResourcePath;
        currentProject.lastSourceDataPath = sourceDataPath;
        currentProject.lastSourceResourcePath = sourceResourcePath;
        currentProject.loadRegistries();
        currentProject.discoverEditorAssets();
        currentProject.applyArmatureMappings(armatureMappings);

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
    }

    /// Loads every editor-editable [com.yesman.akythera.core.util.EnumerableDataBlock]
    /// type's entries from `data/<registryPath>/*.json`. Designed after
    /// [#loadDatapackRegistry] — data block files are property-less `{}` JSON, so the
    /// same generic loader applies. Each type's registry was created in the constructor.
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
                getEditorSkeletalMeshAccessorByPath(file);
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
    }

    private void walkAssetFiles(String directory, Consumer<Path> handler) {
        Path dir = projectRoot.resolve("assets").resolve(directory);
        FileSystemHelper.forEachFile(dir, p -> {
            String s = p.toString();
            return s.endsWith(".efasset") || s.endsWith(".json");
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
            ResourceKey<T> key = ResourceKey.create(registryKey, id);

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
        });
    }

    /// Finish the project without saving, and close editor layout
    public static void endProject() {
        AkytheraEditorLayoutManager.getInstance().closeEditorLayout();
        // Entity→armature preview mappings are project state living in the engine's
        // runtime map — drop them so they don't leak into the next project.
        ComponentsFactory.getImpl().clearArmatureMappings();
        currentProject = null;
    }

    /// Re-registers [ComponentsFactory]'s entity→armature preview mappings from the saved
    /// settings map (entity type id → armature accessor id). Clears the runtime map first,
    /// so reloading the project reverts unsaved mapping edits. Entries whose entity type or
    /// armature no longer resolves (removed mod, deleted asset) are skipped with a warning.
    private void applyArmatureMappings(Map<String, String> saved) {
        ComponentsFactory factory = ComponentsFactory.getImpl();
        factory.clearArmatureMappings();

        for (Map.Entry<String, String> e : saved.entrySet()) {
            Identifier entityId = Identifier.tryParse(e.getKey());
            Identifier armatureId = Identifier.tryParse(e.getValue());

            if (entityId == null || armatureId == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(entityId)) {
                AkytheraLabs.LOGGER.warn("Skipping armature mapping {} -> {}: unknown entity type", e.getKey(), e.getValue());
                continue;
            }

            AssetAccessor<? extends Armature> armature = listAllArmatureAccessors()
                .filter(a -> a.identifier().equals(armatureId))
                .findFirst()
                .orElse(null);

            if (armature == null) {
                AkytheraLabs.LOGGER.warn("Skipping armature mapping {} -> {}: armature not found", e.getKey(), e.getValue());
                continue;
            }

            factory.registerDefaultArmature(BuiltInRegistries.ENTITY_TYPE.getValue(entityId), armature);
        }
    }

    public static AkytheraProject getCurrentProject() {
        if (currentProject == null) {
            throw new IllegalStateException("No Akythera project has loaded yet");
        }

        return currentProject;
    }

    public static @Nullable AkytheraProject getCurrentProjectOrNull() {
        return currentProject;
    }

    // ******************* Registry Management *******************

    public static final Set<String> ASSETS_FOLDERS = Set.of(
        AnimationManager.DIRECTORY,
        ArmatureManager.DIRECTORY,
        MeshManager.DIRECTORY
    );

    public static final Set<ResourceKey<? extends Registry<?>>> DATAPACK_REGISTRIES = Set.of(
        AkytheraRegistries.DataPack.ANIM_MONTAGE_KEY,
        AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY,
        AkytheraRegistries.DataPack.BLEND_SPACE_2D_KEY,
        AkytheraRegistries.DataPack.ANIMATOR_SCRIPT_KEY,
        AkytheraRegistries.DataPack.ITEM_PATCH_KEY,
        AkytheraRegistries.DataPack.ENTITY_PATCH_KEY
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
    @SuppressWarnings("unchecked")
    public <T> Set<Map.Entry<ResourceKey<T>, T>> getMergedEntries(final ResourceKey<? extends Registry<T>> registryKey) {
        Map<ResourceKey<T>, T> entries = new HashMap<>();

        StaticRegistryAccess.getRegistryAccess().lookupOrThrow(registryKey).entrySet().forEach(entry ->
            entries.put(entry.getKey(), entry.getValue())
        );

        // Overwrite exported project pack
        ((ProjectObjectRegistry<T, ?>) modifiableRegistries.get(registryKey)).entrySet().forEach(entry ->
            entries.put(entry.getKey(), entry.getValue())
        );

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

    /// Returns only the entries that are created by this project
    @SuppressWarnings("unchecked")
    public <T, H extends DataHolder<T>> Set<Map.Entry<ResourceKey<T>, H>> getModifiableEntries(final ResourceKey<? extends Registry<T>> registryKey) {
        ProjectObjectRegistry<T, H> registry = (ProjectObjectRegistry<T, H>) modifiableRegistries.get(registryKey);
        return registry.dataHolderEntrySet();
    }

    /// Merged identifier listing for one editor-editable data block type: the engine
    /// manager's hardcoded/datapack values **plus** this project's editor-created entries,
    /// de-duplicated and identifier-sorted. This is the "split registry" merge — the
    /// animator script editor offers these for new states / layer nodes and validates
    /// references against this set at compile time (editor-created blocks can be removed
    /// dynamically, so a node may reference one that no longer exists).
    public <T extends EnumerableDataBlock> SortedSet<Identifier> getMergedDataBlockIdentifiers(
        EnumerableDataBlockManager<T> manager
    ) {
        SortedSet<Identifier> ids = new TreeSet<>(Comparator.comparing(Identifier::toString));
        for (T value : manager.universalValues()) ids.add(value.identifier());

        ProjectObjectRegistry<T, ?> registry = getModifiableRegistry(manager.directoryKey());
        if (registry != null) ids.addAll(registry.keySet());

        return ids;
    }

    /// Resolve one editable data block to a live [Holder], **project &cup; manager** (NOT
    /// `StaticRegistryAccess` — data blocks live in the [EnumerableDataBlockManager], not in a
    /// `RegistryAccess` registry). Editor-created (labs) entries return a [ProjectObjectHolder]
    /// (its `value()` reads the project registry's object and follows renames); engine/hardcoded
    /// values return a direct holder (immutable). `null` if the id resolves in neither.
    public <T extends EnumerableDataBlock> @Nullable Holder<T> getDataBlockHolder(
        EnumerableDataBlockManager<T> manager, Identifier id
    ) {
        ProjectObjectRegistry<T, ?> registry = getModifiableRegistry(manager.directoryKey());

        if (registry != null && registry.containsKey(id)) {
            return registry.projectHolder(ResourceKey.create(manager.directoryKey(), id));
        }

        return manager.get(id).map(Holder::direct).orElse(null);
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

        String candidate = subPath.isEmpty() ? baseName : subPath + "/" + baseName;
        if (!existingPaths.contains(candidate)) return baseName;

        int i = 1;
        while (existingPaths.contains(candidate + "_" + i)) i++;

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
    /// Safe to call when the holder is already registered (the by-key /
    /// by-id maps just get rewritten with the same value).
    /// [com.yesman.akytheralabs.dataholder.AbstractDataHolder#setOwnResourceKey]
    /// only fires `onOwnResourceKeyAssigned` on first assignment, so
    /// re-insert doesn't double-bind outbound references.
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
        List<ResourceKey<?>> broken = new ArrayList<>();

        for (ProjectObjectRegistry<?, ?> registry : modifiableRegistries.values()) {
            for (Map.Entry<? extends ResourceKey<?>, ? extends DataHolder<?>> e : registry.dataHolderEntrySet()) {
                if (e.getValue().getFailureException() != null) broken.add(e.getKey());
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

    /// Asynchronous counterpart to [#saveEntry]. Resolves the holder, codec and path on the calling
    /// (render) thread, then encodes and writes the JSON on a background thread so a slow disk write
    /// doesn't stall the editor's render loop. Callers pair this with an await modal (see
    /// [com.yesman.akytheralabs.gui.widget.AwaitIconModal]) and close it on completion.
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

    /// folder fold/unfold states loaded from project_settings.akproject
    private final Map<String, Boolean> savedFolderStates;

    /// slash-joined node path of the last focused tree node, or "none"
    private final String savedFocusedNode;

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

        // Must match with 'DATAPACK_REGISTRIES'
        // Manually gives registry keys to generated typed registry
        createProjectRegistry(AkytheraRegistries.DataPack.ANIM_MONTAGE_KEY, AnimationMontageHolder::new, AnimationMontageHolder.EDITOR_CODEC);
        createProjectRegistry(AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY, AnimationSequenceHolder::new, AnimationSequenceHolder.EDITOR_CODEC);
        createProjectRegistry(AkytheraRegistries.DataPack.BLEND_SPACE_2D_KEY, BlendSpace2DHolder::new, BlendSpace2DHolder.EDITOR_CODEC);
        createProjectRegistry(AkytheraRegistries.DataPack.ANIMATOR_SCRIPT_KEY, AnimatorScriptHolder::new, AnimatorScriptHolder.EDITOR_CODEC);
        createProjectRegistry(AkytheraRegistries.DataPack.ENTITY_PATCH_KEY, EntityPatchBlueprintHolder::new, EntityPatchBlueprintHolder.EDITOR_CODEC);

        createProjectRegistry(AkytheraRegistries.DataPack.ITEM_PATCH_KEY, ItemPatchBlueprintHolder::new, ItemPatchBlueprintHolder.EDITOR_CODEC);

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

            ((EditorReferenceable) e.getValue()).rename(newId);
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

        if (Files.isRegularFile(path)) {
            unregisterDeletedFile(dataRoot, assetsRoot, path);

            // A directory delete already takes the sidecar with the rest of the tree. A single-file
            // delete has to take it explicitly — and here rather than inside unregisterAssetFile,
            // which the directory branch below calls while it is still walking that directory.
            Path metadataFile = getMetadataFileBeside(path);

            if (path.startsWith(assetsRoot) && Files.isRegularFile(metadataFile)) {
                FileSystemHelper.deleteRecursive(metadataFile);
            }
        } else {
            FileSystemHelper.forEachFile(path, file -> unregisterDeletedFile(dataRoot, assetsRoot, file));
        }

        FileSystemHelper.deleteRecursive(path);
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
        String pathNoExt = ParseUtils.trimExtensions(rel, BinaryAssetLoader.EXTENSION, "json");
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

                    steps.add(() -> {
                        FileSystemHelper.writeBytes(p, bytes);

                        if (metadataBytes != null) FileSystemHelper.writeBytes(metadataFile, metadataBytes);

                        cap.reinsert().run();
                    });
                    return;
                }
            }

            steps.add(() -> FileSystemHelper.writeBytes(p, bytes));
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
        String pathNoExt = ParseUtils.trimExtensions(rel, BinaryAssetLoader.EXTENSION, "json");
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

        if (Files.isRegularFile(oldMetadataFile)) {
            FileSystemHelper.move(oldMetadataFile, getMetadataFileBeside(newPath));
        }

        String oldRel = FileSystemHelper.relativeIdPath(assetsRoot, oldPath);
        String newRel = FileSystemHelper.relativeIdPath(assetsRoot, newPath);

        if (oldRel.startsWith(MeshManager.DIRECTORY)) {
            renameAssetIn(editorMeshes, issuedEditorMeshAccessors, oldRel, newRel);
        } else if (oldRel.startsWith(ArmatureManager.DIRECTORY)) {
            renameAssetIn(editorArmatures, issuedEditorArmatureAccessors, oldRel, newRel);
        } else if (oldRel.startsWith(AnimationManager.DIRECTORY)) {
            renameAssetIn(editorAnimations, issuedEditorAnimationAccessors, oldRel, newRel);
        }
    }

    /// Re-key both the loaded-object cache and the issued-accessor map, then `rename` the accessor
    /// — that mutates the id in place, so every referrer holding this shared accessor instance now
    /// reports the new id. A re-save scan then flushes those referrers' JSON to match.
    private <V, A extends AssetAccessor<?>> void renameAssetIn(
        Map<Identifier, V> cache,
        Map<Identifier, A> issued,
        String oldRel, String newRel
    ) {
        Identifier oldId = Identifier.fromNamespaceAndPath(projectName, ParseUtils.trimExtensions(oldRel, BinaryAssetLoader.EXTENSION, "json"));
        Identifier newId = Identifier.fromNamespaceAndPath(projectName, ParseUtils.trimExtensions(newRel, BinaryAssetLoader.EXTENSION, "json"));

        if (oldId.equals(newId)) return;

        A accessor = issued.remove(oldId);

        if (accessor != null) {
            EditorReferenceable renamed = (EditorReferenceable) accessor;
            renamed.rename(newId);
            issued.put(newId, accessor);
            resaveReferrers(renamed);
        }

        V loaded = cache.remove(oldId);
        if (loaded != null) cache.put(newId, loaded);
    }

    public void saveProjectMetadata(Map<String, Boolean> folderStates, @Nullable Path focusedNodePath) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("    \"name\": \"").append(projectName).append("\",\n");
        sb.append("    \"version\": \"").append(version).append("\",\n");
        // Forward-slash normalized when saved, so safe to embed raw (no backslash escaping).
        sb.append("    \"export_data_path\": \"").append(lastExportDataPath).append("\",\n");
        sb.append("    \"export_resource_path\": \"").append(lastExportResourcePath).append("\",\n");
        sb.append("    \"source_data_path\": \"").append(lastSourceDataPath).append("\",\n");
        sb.append("    \"source_resource_path\": \"").append(lastSourceResourcePath).append("\",\n");
        sb.append("    \"focused\": \"").append(
            focusedNodePath == null
                ? ""
                : FileSystemHelper.toIdString(
                    focusedNodePath.toString().replace(getProjectRoot().toString() + File.separator, "")
                  )
        ).append("\",\n");

        sb.append("    \"folder_states\": {");
        boolean firstFolder = true;

        for (Map.Entry<String, Boolean> entry : folderStates.entrySet()) {
            if (!firstFolder) sb.append(",");
            sb.append("\n        \"").append(entry.getKey()).append("\": ").append(entry.getValue());
            firstFolder = false;
        }

        sb.append("\n    },\n");

        // Entity→armature preview mappings, read live from the engine's runtime map (the
        // Model Armature Mapping modal writes there directly). Identifiers are JSON-safe raw.
        sb.append("    \"armature_mappings\": {");

        boolean firstMapping = true;

        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            AssetAccessor<? extends Armature> armature = ComponentsFactory.getImpl().getExternalArmatureMappingFor(type);
            if (armature == null) continue;

            if (!firstMapping) sb.append(",");
            sb.append("\n        \"").append(BuiltInRegistries.ENTITY_TYPE.getKey(type))
                .append("\": \"").append(armature.identifier()).append("\"");
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

        FileSystemHelper.zip(
            resourcePackDir.resolve(resourcePackName),
            resourceEntries,
            List.of(Pair.of("assets/" + projectName, projectRoot.resolve("assets")))
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

        // Editor-editable EnumerableDataBlocks (datablock/<enumName>): property-less `{}` entries
        // encoded via the engine manager's unit map codec, so they reload under <projectName>:<entry>.
        for (DataBlockRegistries.Entry<?, ?> entry : DataBlockRegistries.all()) {
            collectDataBlockEntries(entry, ops, dataEntries);
        }

        String dataPackName = includeAssets
            ? projectName + "_datapack_with_assets.zip"
            : projectName + "_data.zip";

        FileSystemHelper.zip(
            dataPackDir.resolve(dataPackName),
            dataEntries,
            includeAssets
                ? List.of(Pair.of("data/" + projectName + "/assets", projectRoot.resolve("assets")))
                : List.of()
        );
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
        FileSystemHelper.forEachFile(assetsSrc, file ->
            FileSystemHelper.copyReplace(file, assetsDst.resolve(assetsSrc.relativize(file)))
        );
    }

    /// Adds the project icon — `<projectRoot>/icon.png`, set via the Project Settings modal
    /// ([com.yesman.akytheralabs.gui.widget.main.ProjectSetupModalGenerator]) — to `entries`
    /// as the pack's root `pack.png`, so the exported pack shows it in the pack-selection list.
    /// No-op when the project has no icon file; a read failure is logged and skipped so it can't
    /// abort the export.
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
                T object = entry.getValue().makeObject();
                JsonElement json = engineCodec.encodeStart(ops, object).getOrThrow();
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

    public List<Integer> getLayoutRatioOrDefault(Class<? extends Screen> screenClass, Integer... defaultRatios) {
        return layoutDividerSetup.getOrDefault(screenClass, List.of(defaultRatios));
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
                BinaryAssetLoader.EXTENSION, "json"
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
                mesh = MeshLoader.getLoader(id, rm).loadSkeletalMesh(SkeletalMesh::new);
                AkytheraLabs.LOGGER.info("{} is skeletal mesh.", path.getFileName());
            } catch (ResourceLoadingFailureException e) {
                AkytheraLabs.LOGGER.info("{} is not a skeletal mesh type. Now try load as static mesh.", path.getFileName());
                mesh = MeshLoader.getLoader(id, rm).loadStaticMesh(StaticMesh::new);
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
            return MeshLoader.getLoader(id, PROJECT_RESOURCE_MANAGER).loadSkeletalMesh(SkeletalMesh::new);
        } catch (ResourceLoadingFailureException notSkeletal) {
            try {
                return MeshLoader.getLoader(id, PROJECT_RESOURCE_MANAGER).loadStaticMesh(StaticMesh::new);
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
    public <T extends StaticMesh> AssetAccessor<T> getOrIssueEditorStaticMeshAccessor(
        Identifier id,
        MeshLoader.MeshConstructor<T, Mesh.CommonMeshBuilder<StaticMesh.StaticMeshPart>> meshConstructor
    ) {
        return (AssetAccessor<T>) issuedEditorMeshAccessors.computeIfAbsent(
            id, k -> new EditorStaticMeshAccessor<>(k, meshConstructor)
        );
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
    public abstract static class EditorMeshAccessor<T extends Mesh<?>> extends EditorReferenceable implements AssetAccessor<T> {
        private Identifier identifier;

        private @Nullable MeshMetadataHolder metadata;

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

        /// The sidecar view, read off disk on first call and kept from then on.
        public MeshMetadataHolder metadata() {
            if (metadata == null) {
                metadata = MeshMetadataHolder.readFrom(this);
            }

            return metadata;
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
                id -> MeshLoader.getLoader(id, PROJECT_RESOURCE_MANAGER)
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
                id -> MeshLoader.getLoader(id, PROJECT_RESOURCE_MANAGER)
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

    private static final class EditorArmatureAccessor<T extends Armature> extends EditorReferenceable implements AssetAccessor<T> {
        private Identifier identifier;
        private final ArmatureLoader.ArmatureConstructor<T> armatureConstructor;

        EditorArmatureAccessor(Identifier identifier, ArmatureLoader.ArmatureConstructor<T> armatureConstructor) {
            this.identifier = identifier;
            this.armatureConstructor = armatureConstructor;
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

    /// Copies the dropped file into the current project, then parses the copy into an
    /// [AnimationClip]. The accessor identifier is derived from the destination path (the
    /// source may live anywhere on disk, e.g. the user's desktop), so the copy must happen
    /// first. On failure the destination is restored to its prior state — see [#importAssetFile].
    public AssetLoadResult<AssetAccessor<AnimationClip>> loadAnimationToCurrentProject(Path path, Path toCopy) {
        return importAssetFile(path, toCopy, "animation", () -> {
            Identifier id = getAccessorIdentifierFromPath(AnimationManager.DIRECTORY, toCopy);
            SingleFileResourceManager rm = new SingleFileResourceManager(toCopy);
            AnimationLoader loader = AnimationLoader.getLoader(id, rm);

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
    public static final class EditorAnimationAccessor extends EditorReferenceable implements AssetAccessor<AnimationClip> {
        private Identifier identifier;

        private @Nullable AnimationMetadataHolder metadata;

        private EditorAnimationAccessor(Identifier identifier) {
            this.identifier = identifier;
        }

        /// The sidecar view, read off disk on first call and kept from then on. [AkytheraProject]
        /// issues one accessor per identifier, so there is one holder per identifier.
        public AnimationMetadataHolder metadata() {
            if (metadata == null) {
                metadata = AnimationMetadataHolder.readFrom(this);
            }

            return metadata;
        }

        @Override public Identifier identifier() { return identifier; }
        @Override void setIdentifier(Identifier identifier) { this.identifier = identifier; }

        @Override
        public AnimationClip get() {
            return getCurrentProject().editorAnimations.computeIfAbsent(identifier, id ->
                AnimationLoader
                    .getLoader(id, PROJECT_RESOURCE_MANAGER)
                    .loadAnimationClip(null)
            );
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
    private static abstract class EditorReferenceable implements EditorReference {
        /// Subclasses mutate their own identifier field.
        abstract void setIdentifier(Identifier id);

        void rename(Identifier newId) {
            setIdentifier(newId);
        }
    }
}
