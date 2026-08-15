package com.yesman.akytheralabs.gui.panel.main.processors;

import com.yesman.akythera.core.asset.loader.AssetLoader;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.main.ContentBrowserPanel;
import com.yesman.akytheralabs.gui.panel.main.ExplorerPanel;
import com.yesman.akytheralabs.gui.widget.ContextMenuContainer;
import com.yesman.akytheralabs.gui.widget.main.ContentWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class MeshResourceProcessor implements ResourceProcessor<Identifier> {
    public static final MeshResourceProcessor INSTANCE = new MeshResourceProcessor();

    private MeshResourceProcessor() {}

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
            .supplyAsync(() ->
                TinyFileDialogs.tinyfd_openFileDialog(
                    "Select Mesh File", "", null, "Mesh Files (*." + AssetLoader.BINARY_EXTENSION + " *.json)", false
                )
            )
            .thenAcceptAsync(result -> {
                if (result == null) return;
                browser.importExternalAssets(List.of(Path.of(result)), targetDir, INSTANCE);
            }, Minecraft.getInstance());
    }

    @Override
    public @NonNull ContentWidget<?> createContentWidget(Font font, Path path) {
        String name = ResourceProcessor.fileNameNoExt(path);
        return AkytheraProject.getCurrentProject().isEditorMeshSkeletal(path)
            ? new ContentWidget.SkeletalMeshContent(font, path, name)
            : new ContentWidget.StaticMeshContent(font, path, name);
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