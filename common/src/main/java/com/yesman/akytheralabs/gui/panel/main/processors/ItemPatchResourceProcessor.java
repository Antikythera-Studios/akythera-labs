package com.yesman.akytheralabs.gui.panel.main.processors;

import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.api.world.patch.item.ItemPatch;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.main.ContentBrowserPanel;
import com.yesman.akytheralabs.gui.panel.main.ExplorerPanel;
import com.yesman.akytheralabs.gui.widget.ContextMenuContainer;
import com.yesman.akytheralabs.gui.widget.main.ContentWidget;
import com.yesman.akytheralabs.gui.widget.main.ItemSelectModal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

public final class ItemPatchResourceProcessor implements ResourceProcessor<ItemPatch> {
    public static final ItemPatchResourceProcessor INSTANCE = new ItemPatchResourceProcessor();

    private ItemPatchResourceProcessor() {}

    @Override
    public boolean canCreateSubDirectories() {
        return false;
    }

    @Override
    public void onPressAddButton(ContentBrowserPanel browser, ExplorerPanel.TreeNode selected) {
        ItemSelectModal modal = new ItemSelectModal(browser.getFont(), item -> {
            String idPath = BuiltInRegistries.ITEM.getKey(item).getPath();
            AkytheraProject.getCurrentProject().createEntry(AkytheraRegistries.DataPack.ITEM_PATCH_KEY, idPath, true);
            AkytheraEditorLayoutManager.getInstance().closeModal();
        });

        AkytheraEditorLayoutManager.getInstance().openModalWidgetAtCenter(modal);
    }

    @Override
    public @Nullable ContentWidget<?> createContentWidget(Font font, Path path) {
        return new ContentWidget.ItemPatchContent(font, Items.PAPER, path);
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