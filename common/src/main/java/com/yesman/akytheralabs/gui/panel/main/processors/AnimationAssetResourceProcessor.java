package com.yesman.akytheralabs.gui.panel.main.processors;

import com.yesman.akythera.api.asset.animation.AnimationManager;
import com.yesman.akythera.core.animation.playable.AnimationMontage;
import com.yesman.akythera.core.animation.playable.AnimationSequence;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.akythera.animation.AnimationMontageHolder;
import com.yesman.akytheralabs.akythera.animation.AnimationSequenceHolder;
import com.yesman.akytheralabs.gui.panel.main.ContentBrowserPanel;
import com.yesman.akytheralabs.gui.panel.main.ContentContextOption;
import com.yesman.akytheralabs.gui.panel.main.ExplorerPanel;
import com.yesman.akytheralabs.gui.screen.MainEditorScreen;
import com.yesman.akytheralabs.gui.widget.UniversalMenu;
import com.yesman.akytheralabs.gui.widget.main.ContentWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
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
                    "Select Animation File", "", null, "Animation Files (*.akasset *.json)", false
                )
            )
            .thenAcceptAsync(result -> {
                if (result == null) return;
                browser.importExternalAssets(List.of(Path.of(result)), targetDir, INSTANCE);
            }, Minecraft.getInstance());
    }

    @Override
    public boolean canImport(Path file, List<Path> batch) {
        return ResourceProcessor.isAssetFile(file);
    }

    @Override
    public AkytheraProject.AssetLoadResult<?> importFile(Path source, Path destination) {
        return AkytheraProject.getCurrentProject().loadAnimationToCurrentProject(source, destination);
    }

    @Override
    public ContentWidget<?> createContentWidget(Font font, Path path) {
        return new ContentWidget.AnimationAssetContent(font, path, ResourceProcessor.fileNameNoExt(path));
    }

    @Override
    public UniversalMenu.Actions<ContentContextOption> contextActions(ContentBrowserPanel browser, ContentWidget<?> rightPressed) {
        return ResourceProcessor.entryActions(this, browser, rightPressed)
            .on(ContentContextOption.CREATE_ANIM_SEQUENCE, () -> createAnimSequences(new ArrayList<>(browser.getSelectedWidgets())))
            .on(ContentContextOption.CREATE_ANIM_MONTAGE, () -> createAnimMontages(new ArrayList<>(browser.getSelectedWidgets())));
    }

    /// One [AnimationSequence] per selected animation, mirroring the animation's own path under the
    /// registry root and taking its preview model from the animation's sidecar.
    private static void createAnimSequences(List<ContentWidget<?>> selectedWidgets) {
        List<AkytheraProject.EditorAnimationAccessor> animations = animationsOf(selectedWidgets);
        if (animations.isEmpty()) return;

        AkytheraProject project = AkytheraProject.getCurrentProject();
        String commonAncestor = null;

        for (AkytheraProject.EditorAnimationAccessor animation : animations) {
            String baseName = animation.identifier().getPath().replace(AnimationManager.DIRECTORY, "");
            commonAncestor = commonAncestor == null
                ? parentDirOf(baseName)
                : longestCommonDirPrefix(commonAncestor, parentDirOf(baseName));

            String entryName = project.findNextFreeName(AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY, baseName, "");
            ResourceKey<@NonNull AnimationSequence> key = project.createEntry(
                AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY, entryName, true);
            AnimationSequenceHolder holder = project.getDataHolderOrThrow(key);
            holder.setAnimClip(animation);
            holder.setPreviewEntityType(animation.metadata().getPreviewEntityType());
            project.saveEntry(key);
        }

        focusRegistryDirectory(AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY, commonAncestor);
    }

    /// One [AnimationMontage] per selected animation, holding it as the montage's single segment.
    private static void createAnimMontages(List<ContentWidget<?>> selectedWidgets) {
        List<AkytheraProject.EditorAnimationAccessor> animations = animationsOf(selectedWidgets);
        if (animations.isEmpty()) return;

        AkytheraProject project = AkytheraProject.getCurrentProject();
        String commonAncestor = null;

        for (AkytheraProject.EditorAnimationAccessor animation : animations) {
            String baseName = animation.identifier().getPath().replace(AnimationManager.DIRECTORY, "");
            commonAncestor = commonAncestor == null
                ? parentDirOf(baseName)
                : longestCommonDirPrefix(commonAncestor, parentDirOf(baseName));

            String entryName = project.findNextFreeName(AkytheraRegistries.DataPack.ANIM_MONTAGE_KEY, baseName, "");
            ResourceKey<@NonNull AnimationMontage> key = project.createEntry(
                AkytheraRegistries.DataPack.ANIM_MONTAGE_KEY, entryName, true);
            AnimationMontageHolder holder = project.getDataHolderOrThrow(key);
            holder.addSegment(animation);
            holder.setPreviewEntityType(animation.metadata().getPreviewEntityType());
            project.saveEntry(key);
        }

        focusRegistryDirectory(AkytheraRegistries.DataPack.ANIM_MONTAGE_KEY, commonAncestor);
    }

    /// The animation accessors behind `selectedWidgets`, or an empty list when the selection holds
    /// anything else — the callers create one entry per animation and have nothing to fall back on.
    private static List<AkytheraProject.EditorAnimationAccessor> animationsOf(List<ContentWidget<?>> selectedWidgets) {
        List<AkytheraProject.EditorAnimationAccessor> animations = new ArrayList<>(selectedWidgets.size());

        for (ContentWidget<?> widget : selectedWidgets) {
            if (!(widget.getReferencingObject() instanceof AkytheraProject.EditorAnimationAccessor animation)) {
                AkytheraLabs.LOGGER.error("Invalid assets were selected for creating animation data");
                return List.of();
            }

            animations.add(animation);
        }

        return animations;
    }

    /// Everything before the last `/` of `idPath`, or `""` when it has none.
    private static String parentDirOf(String idPath) {
        int lastSlash = idPath.lastIndexOf('/');
        return lastSlash >= 0 ? idPath.substring(0, lastSlash) : "";
    }

    /// Rebuilds the explorer tree and focuses `<registry root>/<subdirectory>`, where the new
    /// entries landed.
    private static void focusRegistryDirectory(
        ResourceKey<? extends @NonNull Registry<?>> registryKey,
        @Nullable String subdirectory
    ) {
        AkytheraEditorLayoutManager.getInstance().<MainEditorScreen>ifLastScreen(mainScreen -> {
            mainScreen.explorerPanel.rebuildTree();

            Path directory = AkytheraProject.getCurrentProject().getProjectRoot()
                .resolve("data")
                .resolve(registryKey.identifier().getPath())
                .resolve(subdirectory == null ? "" : subdirectory);

            mainScreen.explorerPanel.focusNodeByPath(directory);
        });
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