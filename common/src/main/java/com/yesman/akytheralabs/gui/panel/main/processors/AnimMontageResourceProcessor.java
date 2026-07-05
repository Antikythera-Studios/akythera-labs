package com.yesman.akytheralabs.gui.panel.main.processors;

import com.yesman.akythera.core.animation.playable.AnimationMontage;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.dataholder.animation.AnimationMontageHolder;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.main.ContentBrowserPanel;
import com.yesman.akytheralabs.gui.panel.main.ExplorerPanel;
import com.yesman.akytheralabs.gui.screen.MainEditorScreen;
import com.yesman.akytheralabs.gui.widget.ContextMenuContainer;
import com.yesman.akytheralabs.gui.widget.main.ContentWidget;
import com.yesman.akytheralabs.gui.widget.main.ModelSelectModal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

public final class AnimMontageResourceProcessor implements ResourceProcessor<AnimationMontage> {
    public static final AnimMontageResourceProcessor INSTANCE = new AnimMontageResourceProcessor();

    private AnimMontageResourceProcessor() {}

    @Override
    public boolean canCreateSubDirectories() {
        return true;
    }

    @Override
    public void onPressAddButton(ContentBrowserPanel browser, ExplorerPanel.TreeNode selected) {
        ExplorerPanel.TreeNode regRoot = ResourceProcessor.regRootOf(selected);
        if (regRoot == null) return;

        // Pick a preview model
        ModelSelectModal modal = new ModelSelectModal(browser.getFont(), null, previewModel -> {
            String subPath = ResourceProcessor.buildSubPath(regRoot, selected);
            AkytheraProject project = AkytheraProject.getCurrentProject();

            String entryName = project.findNextFreeName(AkytheraRegistries.DataPack.ANIM_MONTAGE_KEY, "new_entry", subPath);
            String idPath = subPath.isEmpty() ? entryName : subPath + "/" + entryName;
            ResourceKey<@NonNull AnimationMontage> key = project.createEntry(AkytheraRegistries.DataPack.ANIM_MONTAGE_KEY, idPath, true);

            AnimationMontageHolder holder = project.getDataHolderOrThrow(key);
            holder.setPreviewModel(previewModel);
            project.saveEntry(key);

            int lastSlash = idPath.lastIndexOf('/');
            String parentSubdir = lastSlash >= 0 ? idPath.substring(0, lastSlash) : "";
            Path focusDir = project.getProjectRoot()
                .resolve("data")
                .resolve(AkytheraRegistries.DataPack.ANIM_MONTAGE_KEY.identifier().getPath())
                .resolve(parentSubdir);

            AkytheraEditorLayoutManager.getInstance().<MainEditorScreen>ifLastScreen(mainScreen -> {
                mainScreen.explorerPanel.rebuildTree();
                mainScreen.explorerPanel.focusNodeByPath(focusDir);
            });

            AkytheraEditorLayoutManager.getInstance().closeModal();
        });

        AkytheraEditorLayoutManager.getInstance().openModalWidgetAtCenter(modal);
    }

    @Override
    public @Nullable ContentWidget<?> createContentWidget(Font font, Path path) {
        return ResourceProcessor.resolveDatapackWidget(
            font, path,
            AkytheraRegistries.DataPack.ANIM_MONTAGE_KEY,
            ContentWidget.AnimMontageContent::new
        );
    }

    @Override
    public ContextMenuContainer createContextMenuOnWidget(ContentBrowserPanel browser, ContentWidget<?> rightPressed) {
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
}