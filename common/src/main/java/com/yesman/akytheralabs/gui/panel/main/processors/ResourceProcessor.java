package com.yesman.akytheralabs.gui.panel.main.processors;

import com.yesman.akythera.api.asset.animation.AnimationManager;
import com.yesman.akythera.api.asset.armature.ArmatureManager;
import com.yesman.akythera.client.api.mesh.MeshManager;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.dataholder.datablock.DataBlockRegistries;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.main.ContentBrowserPanel;
import com.yesman.akytheralabs.gui.panel.main.ExplorerPanel;
import com.yesman.akytheralabs.gui.widget.ContextMenuContainer;
import com.yesman.akytheralabs.gui.widget.main.ContentWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.File;
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
    Map<Object, ResourceProcessor<?>> BY_TAG = Map.of(
        ArmatureManager.DIRECTORY,                       ArmatureResourceProcessor.INSTANCE,
        MeshManager.DIRECTORY,                           MeshResourceProcessor.INSTANCE,
        AnimationManager.DIRECTORY,                      AnimationAssetResourceProcessor.INSTANCE,
        AkytheraRegistries.DataPack.ANIM_MONTAGE_KEY,    AnimMontageResourceProcessor.INSTANCE,
        AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY,   AnimSequenceResourceProcessor.INSTANCE,
        AkytheraRegistries.DataPack.BLEND_SPACE_2D_KEY,  BlendSpace2DResourceProcessor.INSTANCE,
        AkytheraRegistries.DataPack.ANIMATOR_SCRIPT_KEY, AnimatorScriptResourceProcessor.INSTANCE,
        AkytheraRegistries.DataPack.ITEM_PATCH_KEY,      ItemPatchResourceProcessor.INSTANCE,
        AkytheraRegistries.DataPack.ENTITY_PATCH_KEY,    EntityPatchResourceProcessor.INSTANCE
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

    /// Right-click on a [ContentWidget] in [ContentBrowserPanel]. Each processor
    /// builds its own menu, capturing `widget` in closures so the menu reacts to
    /// whichever widget the user clicked.
    ///
    /// Call [ContentBrowserPanel#getSelectedWidgets()] to get all selected items ordered by the location of a widget
    /// in the grid
    ContextMenuContainer createContextMenuOnWidget(ContentBrowserPanel browser, ContentWidget<?> rightPressed);

    /// Pressing the green `+` button in [ContentBrowserPanel].
    /// `selected` is the user-focused tree node (may equal the registry root or
    /// a descendant). The registry root is reachable via [#regRootOf].
    void onPressAddButton(ContentBrowserPanel browser, ExplorerPanel.TreeNode selected);

    /// Convert a single regular-file path inside this asset type's directory
    /// into a [ContentWidget]. Returns null if the file is not recognized.
    @Nullable ContentWidget<?> createContentWidget(Font font, Path path);

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
        DatapackContentConstructor<V> ctor
    ) {
        // Normalize to '/' up front: Identifier#getPath() always uses '/', but on Windows the
        // OS path uses '\'. Registries whose path has a nested segment (e.g. "script/animator")
        // would otherwise never match the search needle and slice the key mid-segment.
        String pathString = path.toString().replace(File.separatorChar, '/');
        String projectsPrefix = AkytheraProject.PROJECTS_DIR_NAME + "/";
        int projectsRoot = pathString.indexOf(projectsPrefix);

        if (projectsRoot == -1) {
            AkytheraLabs.LOGGER.error("Failed at parsing {}. Path doesn't indicate akythera_projects", registryKey);
            return null;
        }

        String relativeLocation = pathString.substring(projectsRoot).replace(projectsPrefix, "");
        int beforeModId = relativeLocation.indexOf('/');
        String namespace = relativeLocation.substring(0, beforeModId);
        String relativePackLocation = relativeLocation.substring(beforeModId + 1);
        String registryPathPrefix = registryKey.identifier().getPath() + "/";
        int beforeRegistryPath = relativePackLocation.indexOf(registryPathPrefix);
        String registryLocation = relativePackLocation.substring(beforeRegistryPath + registryPathPrefix.length());
        int dot = registryLocation.lastIndexOf('.');
        String registryLocationNoExt = dot > 0 ? registryLocation.substring(0, dot) : registryLocation;

        return ctor.construct(
            font,
            ResourceKey.create(registryKey, Identifier.fromNamespaceAndPath(namespace, registryLocationNoExt)),
            path,
            fileNameNoExt(path)
        );
    }

    /// Single shared right-click-on-empty-space menu for every processor.
    /// One entry, "Add", which re-fires the green-cross button on the
    /// browser's currently selected node.
    static ContextMenuContainer backgroundMenu(ContentBrowserPanel browser) {
        return ContextMenuContainer.builder(Minecraft.getInstance()).pageWidth(140)
            .entry()
                .message(Component.translatable(LangKeys.GUI_MAIN_SCREEN_CONTENT_BROWSER_CONTEXT_ADD_NEW))
                .onPress(_ -> browser.triggerAddPressed())
                .done()
            .build();
    }

    /// Single shared right-click-on-folder-widget menu for every processor.
    static ContextMenuContainer folderMenu(ContentBrowserPanel browser, ContentWidget<?> rightPressed) {
        return ContextMenuContainer.builder(Minecraft.getInstance()).pageWidth(140)
            .entry()
            .message(Component.translatable(LangKeys.GUI_MAIN_SCREEN_CONTENT_BROWSER_CONTEXT_OPEN))
            .onPress(_ -> rightPressed.onDoubleClicked())
            .done()
            .entry()
            .message(Component.translatable(LangKeys.GUI_MAIN_SCREEN_CONTENT_BROWSER_CONTEXT_RENAME))
            .onPress(_ -> rightPressed.startEditing())
            .done()
            .entry()
            .message(Component.translatable(LangKeys.GUI_MAIN_SCREEN_CONTENT_BROWSER_CONTEXT_DELETE_SELECTED))
            .onPress(_ -> browser.triggerDeleteSelected())
            .done()
            .build();
    }

    @FunctionalInterface
    interface DatapackContentConstructor<V> {
        ContentWidget<?> construct(Font font, ResourceKey<@NonNull V> resourceKey, Path path, String name);
    }
}