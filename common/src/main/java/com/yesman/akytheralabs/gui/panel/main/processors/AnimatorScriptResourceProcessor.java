package com.yesman.akytheralabs.gui.panel.main.processors;

import com.yesman.akythera.core.blueprint.script.DataDrivenAnimatorScript;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.dataholder.animation.AnimatorScriptHolder;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.main.ContentBrowserPanel;
import com.yesman.akytheralabs.gui.panel.main.ExplorerPanel;
import com.yesman.akytheralabs.gui.screen.AnimatorScriptEditorScreen;
import com.yesman.akytheralabs.gui.screen.MainEditorScreen;
import com.yesman.akytheralabs.gui.widget.ContextMenuContainer;
import com.yesman.akytheralabs.gui.widget.main.ContentWidget;
import com.yesman.akytheralabs.gui.widget.main.ModelSelectModal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

public final class AnimatorScriptResourceProcessor implements ResourceProcessor<DataDrivenAnimatorScript.AnimatorScriptBlueprint> {
    public static final AnimatorScriptResourceProcessor INSTANCE = new AnimatorScriptResourceProcessor();

    private AnimatorScriptResourceProcessor() {}

    @Override
    public boolean canCreateSubDirectories() {
        return true;
    }

    /// Green `+`: pick the preview model first, then create. The script's id path is
    /// free-form — the entity patch selects which script an entity uses, so the data
    /// pack path is no longer keyed by entity type. The entry is created in the
    /// directory the user has focused, named after the chosen model's leaf name.
    @Override
    public void onPressAddButton(ContentBrowserPanel browser, ExplorerPanel.TreeNode selected) {
        ExplorerPanel.TreeNode regRoot = ResourceProcessor.regRootOf(selected);
        if (regRoot == null) return;

        ModelSelectModal modal = new ModelSelectModal(browser.getFont(), null, previewModel -> {
            String subPath = ResourceProcessor.buildSubPath(regRoot, selected);
            AkytheraProject project = AkytheraProject.getCurrentProject();

            // Only the leaf — user already chose the target dir via focus.
            String modelPath = previewModel.modelId().getPath();
            int modelLastSlash = modelPath.lastIndexOf('/');
            String baseName = modelLastSlash >= 0 ? modelPath.substring(modelLastSlash + 1) : modelPath;

            String entryName = project.findNextFreeName(AkytheraRegistries.DataPack.ANIMATOR_SCRIPT_KEY, baseName, subPath);
            String idPath = subPath.isEmpty() ? entryName : subPath + "/" + entryName;
            ResourceKey<DataDrivenAnimatorScript.AnimatorScriptBlueprint> key = project.createEntry(
                AkytheraRegistries.DataPack.ANIMATOR_SCRIPT_KEY, idPath, true
            );

            AnimatorScriptHolder holder = project.getDataHolderOrThrow(key);
            holder.setPreviewModel(previewModel);
            project.saveEntry(key);

            Path focusDir = project.getProjectRoot()
                .resolve("data")
                .resolve(AkytheraRegistries.DataPack.ANIMATOR_SCRIPT_KEY.identifier().getPath())
                .resolve(subPath);

            AkytheraEditorLayoutManager.getInstance().<MainEditorScreen>ifLastScreen(mainScreen -> {
                mainScreen.explorerPanel.rebuildTree();
                mainScreen.explorerPanel.focusNodeByPath(focusDir);
            });

            AkytheraEditorLayoutManager.getInstance().openNewScreen(
                new AnimatorScriptEditorScreen(key, holder));
        });

        AkytheraEditorLayoutManager.getInstance().openModalWidgetAtCenter(modal);
    }

    @Override
    public @Nullable ContentWidget<?> createContentWidget(Font font, Path path) {
        return ResourceProcessor.resolveDatapackWidget(
            font, path, AkytheraRegistries.DataPack.ANIMATOR_SCRIPT_KEY,
            ContentWidget.AnimatorScriptContent::new);
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