package com.yesman.akytheralabs.gui.panel.main.processors;

import com.yesman.akythera.core.blueprint.ItemPatchBlueprint;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.akythera.itempatch.ItemPatchBlueprintHolder;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.main.ContentBrowserPanel;
import com.yesman.akytheralabs.gui.panel.main.ExplorerPanel;
import com.yesman.akytheralabs.gui.screen.ItemPatchEditorScreen;
import com.yesman.akytheralabs.gui.screen.MainEditorScreen;
import com.yesman.akytheralabs.gui.widget.ContextMenuContainer;
import com.yesman.akytheralabs.gui.widget.main.ContentWidget;
import com.yesman.akytheralabs.gui.widget.main.ItemSelectModal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

/// [ResourceProcessor] for the item-patch datapack registry
/// ([AkytheraRegistries.DataPack#ITEM_PATCH_KEY]). Modeled on [EntityPatchResourceProcessor]: an
/// item patch is keyed by the item it targets, so the green `+` first asks for the item, then
/// creates the blueprint entry at `item_patch/<itemNs>/<itemPath>` and opens the
/// [ItemPatchEditorScreen]. One patch per item — the pick list is filtered by [#hasNoPatch].
public final class ItemPatchResourceProcessor implements ResourceProcessor<ItemPatchBlueprint> {
    public static final ItemPatchResourceProcessor INSTANCE = new ItemPatchResourceProcessor();

    private ItemPatchResourceProcessor() {}

    @Override
    public boolean canCreateSubDirectories() {
        return false;
    }

    @Override
    public void onPressAddButton(ContentBrowserPanel browser, ExplorerPanel.TreeNode selected) {
        AkytheraProject project = AkytheraProject.getCurrentProject();

        ItemSelectModal modal = new ItemSelectModal(browser.getFont(), ItemPatchResourceProcessor::hasNoPatch, item -> {
            Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
            // <itemNs>/<itemPath> — pathEncodesNamespace form, split back at datapack export.
            String idPath = itemId.getNamespace() + "/" + itemId.getPath();

            AkytheraEditorLayoutManager.getInstance().closeModal();

            ResourceKey<ItemPatchBlueprint> key = project.createEntry(
                AkytheraRegistries.DataPack.ITEM_PATCH_KEY, idPath, true);

            ItemPatchBlueprintHolder holder = project.getDataHolderOrThrow(key);
            project.saveEntry(key);

            Path focusDir = project.getProjectRoot()
                .resolve("data")
                .resolve(AkytheraRegistries.DataPack.ITEM_PATCH_KEY.identifier().getPath());

            AkytheraEditorLayoutManager.getInstance().<MainEditorScreen>ifLastScreen(mainScreen -> {
                mainScreen.explorerPanel.rebuildTree();
                mainScreen.explorerPanel.focusNodeByPath(focusDir);
            });

            AkytheraEditorLayoutManager.getInstance().openNewScreen(
                new ItemPatchEditorScreen(key, holder));
        });

        AkytheraEditorLayoutManager.getInstance().openModalWidgetAtCenter(modal);
    }

    /// Whether the project has no item patch for `item` yet. Entry ids are
    /// `<project>:<itemNs>/<itemPath>`, so the comparison happens in that entry-path form.
    private static boolean hasNoPatch(Item item) {
        Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
        String idPath = itemId.getNamespace() + "/" + itemId.getPath();

        return AkytheraProject.getCurrentProject()
            .getModifiableEntries(AkytheraRegistries.DataPack.ITEM_PATCH_KEY)
            .stream()
            .noneMatch(entry -> entry.getKey().identifier().getPath().equals(idPath));
    }

    @Override
    public @Nullable ContentWidget<?> createContentWidget(Font font, Path path) {
        return ResourceProcessor.resolveDatapackWidget(
            font, path, AkytheraRegistries.DataPack.ITEM_PATCH_KEY,
            ContentWidget.ItemPatchContent::new);
    }

    @Override
    public ContextMenuContainer createContextMenuOnWidget(ContentBrowserPanel browser, ContentWidget<?> rightPressed) {
        return ContextMenuContainer.builder(Minecraft.getInstance()).pageWidth(140)
            .entry()
                .message(Component.translatable(LangKeys.GUI_MAIN_SCREEN_CONTENT_BROWSER_CONTEXT_OPEN))
                .onPress(_ -> rightPressed.onDoubleClicked())
                .done()
            .entry()
                .message(Component.translatable(LangKeys.GUI_MAIN_SCREEN_CONTENT_BROWSER_CONTEXT_DELETE_SELECTED))
                .onPress(_ -> browser.triggerDeleteSelected())
                .done()
            .build();
    }
}
