package com.yesman.akytheralabs.gui.panel.main.processors;

import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.core.blueprint.ItemPatchBlueprint;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.FreemarkerExporter;
import com.yesman.akytheralabs.akythera.itempatch.ItemPatchBlueprintHolder;
import com.yesman.akytheralabs.gui.panel.main.ContentBrowserPanel;
import com.yesman.akytheralabs.gui.panel.main.ExplorerPanel;
import com.yesman.akytheralabs.gui.screen.ItemPatchEditorScreen;
import com.yesman.akytheralabs.gui.screen.MainEditorScreen;
import com.yesman.akytheralabs.gui.widget.main.ContentWidget;
import com.yesman.akytheralabs.gui.widget.main.ExportModalGenerator;
import com.yesman.akytheralabs.gui.widget.main.ItemPatchGenerateModal;
import net.minecraft.client.gui.Font;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

/// [ResourceProcessor] for the item-patch datapack registry
/// ([AkytheraRegistries.DataPack#ITEM_PATCH_KEY]). An item patch is keyed by the item it targets, so
/// the green `+` first asks for the item, then creates the blueprint entry at
/// `item_patch/<itemNs>/<itemPath>` and opens the [ItemPatchEditorScreen]. One patch per item.
///
/// The same modal creates a **meta** patch instead: one bound to no item, only ever reached through
/// a child patch's parent reference. It lands under the browsed folder, and at
/// `<projectName>/<metaName>` when that is the registry root — the leading segment is not
/// decoration, [AkytheraProject#exportAsPacks] reads an item patch's id path as `<namespace>/<rest>`
/// and drops an entry that has no such segment.
public final class ItemPatchResourceProcessor implements ResourceProcessor<ItemPatchBlueprint> {
    public static final ItemPatchResourceProcessor INSTANCE = new ItemPatchResourceProcessor();

    private ItemPatchResourceProcessor() {}

    /// The first folder level is the entry's namespace, the rest its path. An item-bound patch still
    /// has to sit at `<itemNs>/<itemPath>` for `AkytheraPatches#getItemPatch` to find it, so only a
    /// meta patch uses the deeper levels.
    @Override
    public boolean canCreateSubDirectories() {
        return true;
    }

    @Override
    public boolean pathEncodesNamespace() {
        return true;
    }

    @Override
    public void onPressAddButton(ContentBrowserPanel browser, ExplorerPanel.TreeNode selected) {
        AkytheraProject project = AkytheraProject.getCurrentProject();

        ExplorerPanel.TreeNode regRoot = ResourceProcessor.regRootOf(selected);
        if (regRoot == null) return;

        String subPath = ResourceProcessor.buildSubPath(regRoot, selected);

        ItemPatchGenerateModal modal = new ItemPatchGenerateModal(
            browser.getFont(),
            ItemPatchResourceProcessor::hasNoPatch,
            name -> isPathFree(metaIdPath(project, subPath, name)),
            item -> {
                Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
                // <itemNs>/<itemPath> — pathEncodesNamespace form, split back at datapack export.
                createAndOpen(project, itemId.getNamespace() + "/" + itemId.getPath(), Items.AIR);
            },
            (metaName, previewItem) ->
                createAndOpen(project, metaIdPath(project, subPath, metaName), previewItem)
        );

        ModalManager.getInstance().openModalWidgetAtCenter(modal);
    }

    /// The id path a meta patch named `metaName` takes under the browsed `subPath`. At the registry
    /// root the project's own name stands in as the namespace segment the export requires.
    private static String metaIdPath(AkytheraProject project, String subPath, String metaName) {
        return subPath.isEmpty()
            ? project.getProjectName() + "/" + metaName
            : subPath + "/" + metaName;
    }

    /// Creates the blueprint entry at `idPath`, focuses the folder it landed in and opens the entry's
    /// editor. `previewItem` is what a meta patch draws itself with; [Items#AIR] leaves the patch
    /// reading its item off the entry id.
    private static void createAndOpen(AkytheraProject project, String idPath, Item previewItem) {
        ModalManager.getInstance().closeModal();

        ResourceKey<ItemPatchBlueprint> key = project.createEntry(
            AkytheraRegistries.DataPack.ITEM_PATCH_KEY, idPath, true);

        ItemPatchBlueprintHolder holder = project.getDataHolderOrThrow(key);

        if (previewItem != Items.AIR) {
            holder.setPreviewItem(previewItem);
        }

        project.saveEntry(key);

        Path focusDir = project.getEntryFilePath(key).getParent();

        AkytheraEditorLayoutManager.getInstance().<MainEditorScreen>ifLastScreen(mainScreen -> {
            mainScreen.explorerPanel.rebuildTree();
            mainScreen.explorerPanel.focusNodeByPath(focusDir);
        });

        AkytheraEditorLayoutManager.getInstance().openNewScreen(new ItemPatchEditorScreen(key, holder));
    }

    /// Whether the project has no item patch for `item` yet. Entry ids are
    /// `<project>:<itemNs>/<itemPath>`, so the comparison happens in that entry-path form.
    private static boolean hasNoPatch(Item item) {
        Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
        String idPath = itemId.getNamespace() + "/" + itemId.getPath();

        return isPathFree(idPath);
    }

    /// Whether no item patch entry of this project sits at `idPath`.
    private static boolean isPathFree(String idPath) {
        return AkytheraProject.getCurrentProject()
            .getModifiableEntries(AkytheraRegistries.DataPack.ITEM_PATCH_KEY)
            .stream()
            .noneMatch(entry -> entry.getKey().identifier().getPath().equals(idPath));
    }

    @Override
    public @Nullable ContentWidget<?> createContentWidget(Font font, Path path) {
        return ResourceProcessor.resolveDatapackWidget(
            font, path, AkytheraRegistries.DataPack.ITEM_PATCH_KEY, ContentWidget.ItemPatchContent::new
        );
    }

    @Override
    public ExportModalGenerator.Compile categoryCompile() {
        return new ExportModalGenerator.Compile(
            key -> key.registryKey().equals(AkytheraRegistries.DataPack.ITEM_PATCH_KEY),
            (project, sourceRoot, packageName) -> FreemarkerExporter.exportRegistryProvider(
                project, AkytheraRegistries.DataPack.ITEM_PATCH_KEY, sourceRoot, packageName
            )
        );
    }
}
