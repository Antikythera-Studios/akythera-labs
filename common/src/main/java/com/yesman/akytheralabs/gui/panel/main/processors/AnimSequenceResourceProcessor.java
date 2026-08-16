package com.yesman.akytheralabs.gui.panel.main.processors;

import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.asset.animation.AnimationManager;
import com.yesman.akythera.core.animation.keyframe.AnimationClip;
import com.yesman.akythera.core.animation.playable.AnimationSequence;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.akythera.animation.AnimationSequenceHolder;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.main.ContentBrowserPanel;
import com.yesman.akytheralabs.gui.panel.main.ExplorerPanel;
import com.yesman.akytheralabs.gui.screen.MainEditorScreen;
import com.yesman.akytheralabs.gui.widget.ContextMenuContainer;
import com.yesman.akytheralabs.gui.widget.main.AnimationSelectModal;
import com.yesman.akytheralabs.gui.widget.main.ContentWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

public final class AnimSequenceResourceProcessor implements ResourceProcessor<AnimationSequence> {
    public static final AnimSequenceResourceProcessor INSTANCE = new AnimSequenceResourceProcessor();

    private AnimSequenceResourceProcessor() {}

    @Override
    public boolean canCreateSubDirectories() {
        return true;
    }

    @Override
    public void onPressAddButton(ContentBrowserPanel browser, ExplorerPanel.TreeNode selected) {
        ExplorerPanel.TreeNode regRoot = ResourceProcessor.regRootOf(selected);
        if (regRoot == null) return;

        AnimationSelectModal modal = new AnimationSelectModal(browser.getFont(), null, true, (animClips, previewModel) -> {
            String subPath = ResourceProcessor.buildSubPath(regRoot, selected);
            AkytheraProject project = AkytheraProject.getCurrentProject();
            String lastIdPath = "";

            for (AssetAccessor<AnimationClip> animClip : animClips) {
                // Only the leaf — user already chose the target dir via focus.
                String animPath = animClip.identifier().getPath().replace(AnimationManager.DIRECTORY, "");
                int animLastSlash = animPath.lastIndexOf('/');
                String baseName = animLastSlash >= 0 ? animPath.substring(animLastSlash + 1) : animPath;

                String entryName = project.findNextFreeName(AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY, baseName, subPath);
                String idPath = subPath.isEmpty() ? entryName : subPath + "/" + entryName;
                ResourceKey<@NonNull AnimationSequence> key = project.createEntry(AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY, idPath, true);
                AnimationSequenceHolder holder = project.getDataHolderOrThrow(key);
                holder.setAnimClip(animClip);
                holder.setPreviewModel(previewModel);
                project.saveEntry(key);

                lastIdPath = idPath;
            }

            int lastSlash = lastIdPath.lastIndexOf('/');
            String parentSubdir = lastSlash >= 0 ? lastIdPath.substring(0, lastSlash) : "";
            Path focusDir = project.getProjectRoot()
                .resolve("data")
                .resolve(AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY.identifier().getPath())
                .resolve(parentSubdir);

            AkytheraEditorLayoutManager.getInstance().<MainEditorScreen>ifLastScreen(mainScreen -> {
                mainScreen.explorerPanel.rebuildTree();
                mainScreen.explorerPanel.focusNodeByPath(focusDir);
            });
        });

        AkytheraEditorLayoutManager.getInstance().openModalWidgetAtCenter(modal);
    }

    @Override
    public @Nullable ContentWidget<?> createContentWidget(Font font, Path path) {
        return ResourceProcessor.resolveDatapackWidget(
            font, path, AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY,
            ContentWidget.AnimSequenceContent::new);
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