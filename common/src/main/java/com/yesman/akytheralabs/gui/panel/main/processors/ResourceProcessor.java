package com.yesman.akytheralabs.gui.panel.main.processors;

import com.yesman.akythera.api.asset.animation.AnimationManager;
import com.yesman.akythera.api.asset.armature.ArmatureManager;
import com.yesman.akythera.api.asset.physics.ArmaturePhysicsManager;
import com.yesman.akythera.client.api.mesh.MeshManager;
import com.yesman.akythera.client.core.asset.loader.binary.ClothPhysicsLoader;
import com.yesman.akythera.core.asset.loader.AssetLoader;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.FileSystemHelper;
import com.yesman.akytheralabs.dataholder.DataBlockRegistries;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.main.ContentBrowserPanel;
import com.yesman.akytheralabs.gui.panel.main.ContentContextOption;
import com.yesman.akytheralabs.gui.panel.main.ExplorerPanel;
import com.yesman.akytheralabs.gui.widget.UniversalMenu;
import com.yesman.akytheralabs.gui.widget.main.ContentWidget;
import com.yesman.akytheralabs.gui.widget.main.ExportModalGenerator;
import net.minecraft.client.gui.Font;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/// A set of widget control & callbacks for a specific resource type.
///
/// Each implementation is a singleton (one per asset type). Tree nodes hold a
/// reference to the processor of the registry root they belong to; reference
/// equality on the `processor` field therefore tests "same asset type."
///
/// @param <T> Registry key type for a resource. Commonly either a [ResourceKey] or [Identifier].
public interface ResourceProcessor<T> {
    /// Tag → processor singleton dispatch. The tag is whatever the project
    /// enumerates per registry root: [String] for asset folders
    /// (e.g. [AnimationManager#DIRECTORY]), or [ResourceKey] for datapack registries.
    Map<Object, ResourceProcessor<?>> BY_TAG = Map.ofEntries(
        Map.entry(ArmatureManager.DIRECTORY,                       ArmatureResourceProcessor.INSTANCE),
        Map.entry(MeshManager.DIRECTORY,                           MeshResourceProcessor.INSTANCE),
        Map.entry(AnimationManager.DIRECTORY,                      AnimationAssetResourceProcessor.INSTANCE),
        Map.entry(ArmaturePhysicsManager.DIRECTORY,                ArmaturePhysicsResourceProcessor.INSTANCE),
        Map.entry(ClothPhysicsLoader.DIRECTORY,                    ClothPhysicsResourceProcessor.INSTANCE),
        Map.entry(AkytheraProject.TEXTURES_DIRECTORY,              TextureResourceProcessor.INSTANCE),
        Map.entry(AkytheraProject.SOUNDS_DIRECTORY,                SoundResourceProcessor.INSTANCE),
        Map.entry(AkytheraRegistries.DataPack.ANIM_MONTAGE_KEY,    AnimMontageResourceProcessor.INSTANCE),
        Map.entry(AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY,   AnimSequenceResourceProcessor.INSTANCE),
        Map.entry(AkytheraRegistries.DataPack.BLEND_SPACE_2D_KEY,  BlendSpace2DResourceProcessor.INSTANCE),
        Map.entry(AkytheraRegistries.DataPack.ANIMATOR_SCRIPT_KEY, AnimatorScriptResourceProcessor.INSTANCE),
        Map.entry(AkytheraRegistries.DataPack.ITEM_PATCH_KEY,      ItemPatchResourceProcessor.INSTANCE),
        Map.entry(AkytheraRegistries.DataPack.ENTITY_PATCH_KEY,    EntityPatchResourceProcessor.INSTANCE),
        Map.entry(AkytheraRegistries.DataPack.ABILITY_KEY,         AbilityResourceProcessor.INSTANCE),
        Map.entry(AkytheraRegistries.DataPack.ABILITY_CUE_KEY,     AbilityCueResourceProcessor.INSTANCE),
        Map.entry(AkytheraRegistries.DataPack.ABILITY_EFFECT_KEY,  AbilityEffectResourceProcessor.INSTANCE),
        Map.entry(AkytheraRegistries.DataPack.INPUT_ACTION_KEY,    InputActionResourceProcessor.INSTANCE),
        Map.entry(AkytheraRegistries.DataPack.WIDGET_KEY,          WidgetBlueprintResourceProcessor.INSTANCE),
        Map.entry(AkytheraRegistries.DataPack.PARTICLE_PIECE_KEY,  ParticlePieceResourceProcessor.INSTANCE),
        Map.entry(AkytheraRegistries.DataPack.PARTICLE_SYSTEM_KEY, ParticleSystemResourceProcessor.INSTANCE)
    );

    static @Nullable ResourceProcessor<?> forTag(@Nullable Object tag) {
        if (tag == null) return null;
        ResourceProcessor<?> processor = BY_TAG.get(tag);
        // Data block registry keys are derived dynamically from the engine's editable-type
        // managers, so they can't live in the static BY_TAG map — fall back to them.
        return processor != null ? processor : DataBlockRegistries.forKey(tag);
    }

    /// Whether this asset type allows creating subdirectories under its registry root.
    boolean canCreateSubDirectories();

    /// Whether this asset type takes a copy of one of its entries. False where an entry's id is the
    /// only binding to what it patches and the type has no unbound form.
    default boolean canDuplicate() {
        return true;
    }

    /// Whether the first segment of an entry's id path is its namespace, split back out at datapack
    /// export ([AkytheraProject#exportAsPacks]). Such a type takes no entry directly in its registry
    /// root — there is no segment there to read as the namespace.
    default boolean pathEncodesNamespace() {
        return false;
    }

    /// The id `key` takes once exported, so a project entry reads and resolves the same as the loaded
    /// world's copy of it — `epicfight:minecraft/sword` becomes `minecraft:sword`. Splits at the first
    /// slash, the way [AkytheraProject#exportAsPacks] does, and only for a [#pathEncodesNamespace]
    /// type. Anything else, and an entry with no such segment, is its own id.
    ///
    /// This is the form a reference to the entry is **stored** in, the engine knowing nothing of the
    /// project the entry was authored in.
    static Identifier exportedId(ResourceKey<?> key) {
        ResourceProcessor<?> processor = forTag(key.registryKey());

        if (processor == null || !processor.pathEncodesNamespace()) return key.identifier();

        String path = key.identifier().getPath();
        int slash = path.indexOf('/');

        if (slash < 0) return key.identifier();

        return Identifier.fromNamespaceAndPath(path.substring(0, slash), path.substring(slash + 1));
    }

    /// The inverse of [#exportedId]: `minecraft:sword` becomes `<projectName>:minecraft/sword`, the id
    /// this project keys that entry by. What a stored reference has to be read back as before the
    /// project can resolve it.
    static Identifier projectId(ResourceKey<? extends Registry<?>> registryKey, Identifier exported) {
        ResourceProcessor<?> processor = forTag(registryKey);

        if (processor == null || !processor.pathEncodesNamespace()) return exported;

        return Identifier.fromNamespaceAndPath(
            AkytheraProject.getCurrentProject().getProjectName(),
            exported.getNamespace() + "/" + exported.getPath());
    }

    /// The id `key` is shown to the user by — [#exportedId], so a project entry reads the same as the
    /// loaded world's copy of it.
    static String displayId(ResourceKey<?> key) {
        return exportedId(key).toString();
    }

    /// Right-click on a [ContentWidget] in [ContentBrowserPanel]: which [ContentContextOption]s this
    /// asset type answers for `rightPressed`. The panel lays every option out itself, so an option
    /// left out of the returned [UniversalMenu.Actions] keeps its place, greyed out.
    ///
    /// The default answers the shared set — see [#entryActions]. Override only to add options of this
    /// type's own.
    ///
    /// Call [ContentBrowserPanel#getSelectedWidgets()] to get all selected items ordered by the location
    /// of a widget in the grid
    default UniversalMenu.Actions<ContentContextOption> contextActions(ContentBrowserPanel browser, ContentWidget<?> rightPressed) {
        return entryActions(this, browser, rightPressed);
    }

    /// This category's whole-category source compile — every entry of it folded into the one generated
    /// `.java` it owns, run by [ExportModalGenerator#openCompile]. Null when the category has no
    /// generated source of its own, which greys the explorer's `Compile Objects` row.
    default ExportModalGenerator.@Nullable Compile categoryCompile() {
        return null;
    }

    /// The source compile of `entryKeys` — entries of this category, each becoming its own generated
    /// `.java`. Null when this category's entries have no generated source of their own, which greys
    /// the content browser's `Compile Selected` row.
    default ExportModalGenerator.@Nullable Compile entryCompile(List<ResourceKey<?>> entryKeys) {
        return null;
    }

    /// Whether `file`, from outside the project, is one this category imports through the `+` button's
    /// file dialog or a drop onto the editor. `batch` is every file picked or dropped with it, `file`
    /// included.
    default boolean canImport(Path file, List<Path> batch) {
        return false;
    }

    /// Copies the external `source` onto `destination` and loads it into the project. Only called for a
    /// file [#canImport] accepts.
    default AkytheraProject.AssetLoadResult<?> importFile(Path source, Path destination) {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " imports no files");
    }

    /// Whether `file` is an engine asset file: binary, or json.
    static boolean isAssetFile(Path file) {
        String fileName = file.getFileName().toString();
        return fileName.endsWith("." + AssetLoader.BINARY_EXTENSION) || fileName.endsWith(".json");
    }

    /// Pressing the green `+` button in [ContentBrowserPanel].
    /// `selected` is the user-focused tree node (may equal the registry root or
    /// a descendant). The registry root is reachable via [#regRootOf].
    void onPressAddButton(ContentBrowserPanel browser, ExplorerPanel.TreeNode selected);

    /// Convert a single regular-file path inside this asset type's directory
    /// into a [ContentWidget]. Returns null if the file is not recognized.
    @Nullable ContentWidget<?> createContentWidget(Font font, Path path);

    /// Right-click on empty space: only the `Add New` group — `New Item` re-fires the green-cross
    /// button on the browser's currently selected node, `New Folder` creates a subdirectory under the
    /// browsed directory while this asset type allows subdirectories.
    static UniversalMenu.Actions<ContentContextOption> backgroundActions(ContentBrowserPanel browser) {
        Path browsedDir = browser.getBrowsedDirectory();

        return UniversalMenu.<ContentContextOption>actions()
            .on(ContentContextOption.NEW_FOLDER, browser::canCreateFolder, browser::triggerCreateFolder)
            .on(ContentContextOption.NEW_ITEM, browser::triggerAddPressed)
            .on(ContentContextOption.PASTE, () -> browser.canPasteInto(browsedDir), () -> browser.pasteInto(browsedDir));
    }

    /// Right-click on a folder widget, shared by every processor. `Paste` targets the pressed folder
    /// rather than the browsed one.
    static UniversalMenu.Actions<ContentContextOption> folderActions(ContentBrowserPanel browser, ContentWidget<?> rightPressed) {
        boolean onDisk = Files.exists(rightPressed.getPath());
        Path folder = rightPressed.getPath();

        return UniversalMenu.<ContentContextOption>actions()
            .on(ContentContextOption.OPEN, rightPressed::onDoubleClicked)
            .on(ContentContextOption.OPEN_IN_FILE_EXPLORER, () -> onDisk, () -> FileSystemHelper.openInFileExplorer(folder))
            .on(ContentContextOption.CUT, browser::canCutSelection, () -> browser.putSelectionOnClipboard(true))
            .on(ContentContextOption.COPY, browser::canCopySelection, () -> browser.putSelectionOnClipboard(false))
            .on(ContentContextOption.PASTE, () -> browser.canPasteInto(folder), () -> browser.pasteInto(folder))
            .on(ContentContextOption.RENAME, rightPressed::isEditable, rightPressed::startEditing)
            .on(ContentContextOption.DELETE_SELECTED, browser::canDeleteSelection, browser::triggerDeleteSelected);
    }

    /// The options every content widget answers, each resolved off what the widget already carries:
    /// `Open` while it has an editor screen, `Open in File Explorer` while its file is still on disk
    /// (tested once as the menu is built), `Compile Selected` while the selection has generated source
    /// of its own, `Rename` while its name is editable, `Copy` while the category takes duplicates,
    /// `Paste` while the clipboard fits the browsed directory, plus `Cut` and `Delete Selected`.
    static UniversalMenu.Actions<ContentContextOption> entryActions(
        ResourceProcessor<?> processor, ContentBrowserPanel browser, ContentWidget<?> rightPressed
    ) {
        boolean onDisk = Files.exists(rightPressed.getPath());
        Path browsedDir = browser.getBrowsedDirectory();
        List<ResourceKey<?>> selectedKeys = selectedEntryKeys(browser);
        ExportModalGenerator.@Nullable Compile compile = selectedKeys.isEmpty() ? null : processor.entryCompile(selectedKeys);

        UniversalMenu.Actions<ContentContextOption> actions = UniversalMenu.<ContentContextOption>actions()
            .on(ContentContextOption.OPEN_IN_FILE_EXPLORER, () -> onDisk, () -> FileSystemHelper.openInFileExplorer(rightPressed.getPath()))
            .on(ContentContextOption.CUT, browser::canCutSelection, () -> browser.putSelectionOnClipboard(true))
            .on(ContentContextOption.COPY, browser::canCopySelection, () -> browser.putSelectionOnClipboard(false))
            .on(ContentContextOption.PASTE, () -> browser.canPasteInto(browsedDir), () -> browser.pasteInto(browsedDir))
            .on(ContentContextOption.DELETE_SELECTED, browser::canDeleteSelection, browser::triggerDeleteSelected);

        if (rightPressed.isOpenable()) {
            actions.on(ContentContextOption.OPEN, rightPressed::onDoubleClicked);
        }

        if (rightPressed.isEditable()) {
            actions.on(ContentContextOption.RENAME, rightPressed::startEditing);
        }

        if (compile != null) {
            actions.on(ContentContextOption.COMPILE, () -> ExportModalGenerator.openCompile(
                browser.getFont(),
                Component.translatable(LangKeys.GUI_EXPORT_MODAL_COMPILE_SELECTED_TITLE),
                compile
            ));
        }

        return actions;
    }

    /// The registry keys of the selected content entries. Folders and asset files carry no project
    /// registry entry and drop out.
    private static List<ResourceKey<?>> selectedEntryKeys(ContentBrowserPanel browser) {
        return browser.getSelectedWidgets().stream()
            .filter(ContentWidget.DataDrivenObjectContent.class::isInstance)
            .<ResourceKey<?>>map(widget -> ((ContentWidget.DataDrivenObjectContent<?, ?>) widget).getResourceKey())
            .toList();
    }

    /// Walks parents to find the registry-root node — the topmost ancestor
    /// whose parent does not own a processor. Returns null if `node` is itself
    /// a top-level root (no processor).
    static ExplorerPanel.@Nullable TreeNode regRootOf(ExplorerPanel.TreeNode node) {
        ExplorerPanel.TreeNode cur = node;

        while (cur.parentNode != null && cur.parentNode.processor != null) {
            cur = cur.parentNode;
        }

        return cur.processor != null ? cur : null;
    }

    /// Sub-path under `regRoot`, joined with `/`. Empty string when `target == regRoot`.
    static String buildSubPath(ExplorerPanel.TreeNode regRoot, ExplorerPanel.TreeNode target) {
        if (target == regRoot) return "";

        List<String> parts = new ArrayList<>();
        ExplorerPanel.TreeNode cur = target;

        while (cur != null && cur != regRoot) {
            parts.addFirst(cur.name);
            cur = cur.parentNode;
        }

        return String.join("/", parts);
    }

    /// Resolves the directory the user is currently writing into. Falls back
    /// to the registry root when `selected` is null or outside the subtree.
    static @Nullable Path resolveTargetDir(
        ExplorerPanel.@Nullable TreeNode regRoot,
        ExplorerPanel.@Nullable TreeNode selected
    ) {
        if (regRoot == null) return null;

        if (selected != null && selected.directoryPath != null && isDescendantOrSelf(regRoot, selected)) {
            return selected.directoryPath;
        }

        return regRoot.directoryPath;
    }

    static boolean isDescendantOrSelf(ExplorerPanel.TreeNode ancestor, ExplorerPanel.TreeNode node) {
        ExplorerPanel.TreeNode cur = node;

        while (cur != null) {
            if (cur == ancestor) return true;
            cur = cur.parentNode;
        }

        return false;
    }

    static String fileNameNoExt(Path path) {
        String fileName = path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');

        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    /// Reverses the project-relative file path into a [ResourceKey], then
    /// builds a datapack content widget via `ctor`.
    static <V> @Nullable ContentWidget<?> resolveDatapackWidget(
        Font font,
        Path path,
        ResourceKey<? extends @NonNull Registry<@NonNull V>> registryKey,
        DatapackContentConstructor<V> constructor
    ) {
        Path projectsRoot = AkytheraProject.getProjectsRootDirectory().toAbsolutePath().normalize();
        Path absolutePath = path.toAbsolutePath().normalize();

        if (!absolutePath.startsWith(projectsRoot)) {
            AkytheraLabs.LOGGER.error("Failed at parsing {}. Path isn't under {}", registryKey, projectsRoot);
            return null;
        }

        String relativeLocation = FileSystemHelper.relativeIdPath(projectsRoot, absolutePath);
        int beforeModId = relativeLocation.indexOf('/');
        String namespace = relativeLocation.substring(0, beforeModId);
        String relativePackLocation = relativeLocation.substring(beforeModId + 1);
        String registryPathPrefix = registryKey.identifier().getPath() + "/";
        int beforeRegistryPath = relativePackLocation.indexOf(registryPathPrefix);
        String registryLocation = relativePackLocation.substring(beforeRegistryPath + registryPathPrefix.length());
        int dot = registryLocation.lastIndexOf('.');
        String registryLocationNoExt = dot > 0 ? registryLocation.substring(0, dot) : registryLocation;

        return constructor.construct(
            font,
            ResourceKey.create(registryKey, Identifier.fromNamespaceAndPath(namespace, registryLocationNoExt)),
            path,
            fileNameNoExt(path)
        );
    }

    @FunctionalInterface
    interface DatapackContentConstructor<V> {
        ContentWidget<?> construct(Font font, ResourceKey<@NonNull V> resourceKey, Path path, String name);
    }
}
