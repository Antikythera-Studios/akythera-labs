package com.yesman.akytheralabs.gui.panel.main.processors;

import com.yesman.akythera.api.asset.animation.AnimationManager;
import com.yesman.akythera.core.animation.playable.AnimationSequence;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.akythera.animation.AnimationSequenceHolder;
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
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.NonNull;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class AnimationAssetResourceProcessor implements ResourceProcessor<Identifier> {
    public static final AnimationAssetResourceProcessor INSTANCE = new AnimationAssetResourceProcessor();

    private AnimationAssetResourceProcessor() {}

    @Override
    public boolean canCreateSubDirectories() {
        return true;
    }

    @Override
    public void onPressAddButton(ContentBrowserPanel browser, ExplorerPanel.TreeNode selected) {
        ExplorerPanel.TreeNode regRoot = ResourceProcessor.regRootOf(selected);
        Path targetDir = ResourceProcessor.resolveTargetDir(regRoot, selected);

        if (targetDir == null) return;

        CompletableFuture
            .supplyAsync(
                () -> TinyFileDialogs.tinyfd_openFileDialog(
                    "Select Animation File", "", null, "Animation Files (*.efasset *.json)", false
                )
            )
            .thenAcceptAsync(result -> {
                if (result == null) return;
                browser.importExternalAssets(List.of(Path.of(result)), targetDir, INSTANCE);
            }, Minecraft.getInstance());
    }

    @Override
    public ContentWidget<?> createContentWidget(Font font, Path path) {
        return new ContentWidget.AnimationAssetContent(font, path, ResourceProcessor.fileNameNoExt(path));
    }

    @Override
    public ContextMenuContainer createContextMenuOnWidget(ContentBrowserPanel browser, ContentWidget<?> rightPressed) {
        return ContextMenuContainer.builder(Minecraft.getInstance()).pageWidth(100)
            .entry()
                .message(Component.translatable(LangKeys.GUI_MAIN_SCREEN_CONTENT_BROWSER_CONTEXT_OPEN))
                .onPress(_ -> rightPressed.onDoubleClicked())
                .done()
            .entry()
                .message(Component.translatable(LangKeys.GUI_MAIN_SCREEN_CONTENT_BROWSER_CONTEXT_RENAME))
                .onPress(_ -> rightPressed.startEditing())
                .done()
            .entry()
                .message(Component.translatable(LangKeys.GUI_MAIN_SCREEN_CONTENT_BROWSER_CONTEXT_CREATE))
                .subpage()
                    .entry()
                        .message(Component.translatable(LangKeys.GUI_MAIN_SCREEN_CONTENT_BROWSER_CONTEXT_CREATE_ANIM_SEQUENCE))
                        .onPress(_ -> openPreviewModelSelectThenCreateAnimSequences(new ArrayList<>(browser.getSelectedWidgets())))
                        .done()
                    .entry()
                        .message(Component.translatable(LangKeys.GUI_MAIN_SCREEN_CONTENT_BROWSER_CONTEXT_CREATE_ANIM_MONTAGE))
                        .onPress(_ -> {
                            // TODO: anim montage flow — no AnimMontage editor screen yet
                        })
                        .done()
                    .done()
            .entry()
                .message(Component.translatable(LangKeys.GUI_MAIN_SCREEN_CONTENT_BROWSER_CONTEXT_DELETE_SELECTED))
                .onPress(_ -> browser.triggerDeleteSelected())
                .done()
            .build();
    }

    private static void openPreviewModelSelectThenCreateAnimSequences(List<ContentWidget<?>> selectedWidgets) {
        if (selectedWidgets.removeIf(w -> !(w instanceof ContentWidget.AnimationAssetContent animWidget) || animWidget.getReferencingObject() == null)) {
            AkytheraLabs.LOGGER.error("Invalid assets were selected for creating Animation Sequence");
            return;
        }

        ModelSelectModal modal = new ModelSelectModal(
            Minecraft.getInstance().font,
            null,
            previewModel -> {
                AkytheraProject project = AkytheraProject.getCurrentProject();
                String commonAncestor = null;

                for (ContentWidget<?> w : selectedWidgets) {
                    ContentWidget.AnimationAssetContent animWidget = (ContentWidget.AnimationAssetContent)w;
                    String baseName = animWidget.getReferencingObject().identifier().getPath().replace(AnimationManager.DIRECTORY, "");

                    int lastSlash = baseName.lastIndexOf('/');
                    String parentDir = lastSlash >= 0 ? baseName.substring(0, lastSlash) : "";
                    commonAncestor = commonAncestor == null ? parentDir : longestCommonDirPrefix(commonAncestor, parentDir);

                    String entryName = project.findNextFreeName(AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY, baseName, "");
                    ResourceKey<@NonNull AnimationSequence> key = project.createEntry(
                        AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY, entryName, true);
                    AnimationSequenceHolder holder = project.getDataHolderOrThrow(key);
                    holder.setAnimClip(animWidget.getReferencingObject());
                    holder.setPreviewModel(previewModel);
                    project.saveEntry(key);
                }

                String focusSuffix = commonAncestor == null ? "" : commonAncestor;

                AkytheraEditorLayoutManager.getInstance().<MainEditorScreen>ifLastScreen(mainScreen -> {
                    mainScreen.explorerPanel.rebuildTree();
                    Path animSeqDir = project.getProjectRoot()
                        .resolve("data")
                        .resolve(AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY.identifier().getPath())
                        .resolve(focusSuffix);

                    mainScreen.explorerPanel.focusNodeByPath(animSeqDir);
                });

                AkytheraEditorLayoutManager.getInstance().closeModal();
            }
        );

        AkytheraEditorLayoutManager.getInstance().openModalWidgetAtCenter(modal);
    }

    private static String longestCommonDirPrefix(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return "";

        String[] aParts = a.split("/");
        String[] bParts = b.split("/");
        int n = Math.min(aParts.length, bParts.length);
        int i = 0;

        while (i < n && aParts[i].equals(bParts[i])) i++;

        return i == 0 ? "" : String.join("/", Arrays.copyOfRange(aParts, 0, i));
    }
}