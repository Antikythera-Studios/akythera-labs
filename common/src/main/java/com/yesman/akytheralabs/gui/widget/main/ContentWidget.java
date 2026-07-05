package com.yesman.akytheralabs.gui.widget.main;

import com.yesman.akythera.core.blueprint.script.DataDrivenAnimatorScript;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.client.core.asset.mesh.SkeletalMesh;
import com.yesman.akythera.client.core.asset.mesh.StaticMesh;
import com.yesman.akythera.client.gui.widget.AnchoredButton;
import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.animation.keyframe.AnimationClip;
import com.yesman.akythera.core.animation.playable.AnimationMontage;
import com.yesman.akythera.core.animation.playable.AnimationSequence;
import com.yesman.akythera.core.animation.playable.BlendSpace2D;
import com.yesman.akythera.api.world.patch.item.ItemPatch;
import com.yesman.akythera.core.blueprint.EntityPatchBlueprint;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.FileSystemHelper;
import com.yesman.akytheralabs.dataholder.DataHolder;
import com.yesman.akythera.core.util.EnumerableDataBlock;
import com.yesman.akytheralabs.dataholder.datablock.DataBlockHolder;
import com.yesman.akytheralabs.dataholder.animation.AnimationMontageHolder;
import com.yesman.akytheralabs.dataholder.animation.AnimationSequenceHolder;
import com.yesman.akytheralabs.dataholder.animation.AnimatorScriptHolder;
import com.yesman.akytheralabs.dataholder.animation.BlendSpace2DHolder;
import com.yesman.akytheralabs.dataholder.entitypatch.EntityPatchBlueprintHolder;
import com.yesman.akytheralabs.editor.historytrack.FileMove;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.main.ContentBrowserPanel;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsButtonTheme;
import com.yesman.akytheralabs.gui.widget.WidgetContainerModal;
import com.yesman.akytheralabs.gui.screen.AnimationMontageEditorScreen;
import com.yesman.akytheralabs.gui.screen.AnimationSequenceEditorScreen;
import com.yesman.akytheralabs.gui.screen.AnimatorScriptEditorScreen;
import com.yesman.akytheralabs.gui.screen.EntityPatchEditorScreen;
import com.yesman.akytheralabs.gui.screen.BlendSpace2DEditorScreen;
import com.yesman.akytheralabs.gui.screen.MainEditorScreen;
import com.yesman.akytheralabs.gui.widget.AdvancedEditBox;
import com.yesman.akytheralabs.gui.widget.Grid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;

/// A single [ContentBrowserPanel] object button
///
/// Required to implement [AnchoredWidget] to be used in [Grid]
public abstract class ContentWidget<T> extends AbstractWidget {
    /// Minimum widget height at which the bottom label is rendered
    private static final int LABEL_VISIBLE_THRESHOLD = 28;
    private static final int LABEL_HEIGHT = 12;
    private static final int ICON_SIZE = 16;

    private final Font font;
    private String contentName;

    private boolean selected = false;
    private boolean dragHighlighted = false;

    /// Null for directory nodes
    @Nullable
    private final T referencingObject;

    @Nullable
    protected Runnable sortCallback;

    protected Path path;

    /// Null when editable == false
    @Nullable
    protected final AdvancedEditBox<String> editBox;
    protected final boolean editable;

    public ContentWidget(final Font font, final String contentName, final boolean editable, final Path path, final @Nullable T referencingObject) {
        super(0, 0, 0, 0, Component.literal("content_widget"));

        this.font = font;
        this.editable = editable;
        this.contentName = contentName;
        this.editBox = editable
            ? AdvancedEditBox.stringBox(
                font,
                AnchoredWidget.pixel(2),
                AnchoredWidget.pixel(2),
                AnchoredWidget.pixel(4),
                AnchoredWidget.pixel(LABEL_HEIGHT),
                AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
                AnchoredWidget.VerticalAnchorType.BOTTOM_HEIGHT,
                this::getContentName,
                this::setContentName,
                Component.literal("")
            )
            : null;

        this.path = path;

        if (editable) {
            editBox.visible = false;

            // editBox visible == the widget focused
            editBox.setFocused(true);
        }

        this.referencingObject = referencingObject;
    }

    public @Nullable T getReferencingObject() {
        return referencingObject;
    }

    @Override
    public void setX(final int x) {
        super.setX(x);
        if (editBox != null) editBox.relocate(new ScreenRectangle(getX(), getY(), getWidth(), getHeight()));
    }

    @Override
    public void setY(final int y) {
        super.setY(y);
        if (editBox != null) editBox.relocate(new ScreenRectangle(getX(), getY(), getWidth(), getHeight()));
    }

    @Override
    public void setSize(final int width, final int height) {
        super.setSize(width, height);
        if (editBox != null) editBox.relocate(new ScreenRectangle(getX(), getY(), getWidth(), getHeight()));
    }

    public boolean isSelected() {
        return selected;
    }

    public boolean isEditable() {
        return editable;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public void setDragHighlighted(boolean highlighted) {
        dragHighlighted = highlighted;
    }

    public abstract void onDoubleClicked();

    public Path getPath() {
        return path;
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float a) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight() - 1;
        boolean hovered = isHovered();
        boolean showLabel = h >= LABEL_VISIBLE_THRESHOLD;
        boolean highlighted = isFocused() || selected;

        // Background
        guiGraphics.fill(x, y, x + w, y + h, highlighted ? 0xFF1E4D72 : (hovered ? 0xFF2A2A2A : 0xFF1E1E1E));

        // 1px border on each edge
        int borderColor = dragHighlighted || highlighted ? 0xFF5A8AAA : 0xFF3A3A3A;
        guiGraphics.outline(x, y, w, h, borderColor);

        renderIcon(guiGraphics, showLabel);

        // editable == editBox != null
        if (editable && editBox.visible) {
            editBox.extractRenderState(guiGraphics, mouseX, mouseY, a);
        } else if (showLabel) {
            String label = font.plainSubstrByWidth(getContentName(), w - 2);
            int labelX = x + (w - font.width(label)) / 2;
            guiGraphics.text(font, label, labelX, y + h - LABEL_HEIGHT, 0xFF888888, false);
        }
    }

    /// Renders the content-specific icon within the widget bounds.
    ///
    /// @param showLabel true when the widget is tall enough to display a bottom label,
    ///                  implementations should shift the icon upward to make room
    protected abstract void renderIcon(@NonNull GuiGraphicsExtractor guiGraphics, boolean showLabel);

    /// Returns the display name for the bottom label
    public String getContentName() {
        return contentName;
    }

    public void setContentName(String newName) {
        contentName = newName;
    }

    public void setSortCallback(Runnable sortCallback) {
        this.sortCallback = sortCallback;

        if (editBox != null) {
            editBox.setOnCommitted((_, _) -> {
                onCommitName(editBox.getText());
                editBox.visible = false;     // close on Enter — see Issue B in plan
            });
        }
    }

    /// Template: validate the new name, ask the subclass to rename on
    /// disk via [#renameOnDisk], update local state, push a [FileMove]
    /// so Ctrl+Z reverses the rename, then rebuild the explorer / browser
    /// so the change is visible without a project reload. Subclasses that
    /// can rename only need to override [#renameOnDisk] (and optionally
    /// [#computeNewPath] if the leaf shape is unusual).
    protected final void onCommitName(String newText) {
        if (newText.isBlank() || !Identifier.isValidPath(newText)) return;

        String fileName = path.getFileName().toString();
        String currentStem = FileSystemHelper.stem(fileName);

        if (newText.equals(currentStem)) return;

        AkytheraProject project = AkytheraProject.getCurrentProject();
        Path oldPath = path;
        Path newPath = computeNewPath(newText);

        // A name already taken in the same directory is confirmed before it
        // clobbers the existing file — mirrors the import duplicate prompt.
        if (Files.exists(newPath)) {
            showRenameConflictPopup(() -> applyRename(project, oldPath, newPath, newText, true));
            return;
        }

        applyRename(project, oldPath, newPath, newText, false);
    }

    /// Performs the on-disk rename `oldPath` → `newPath` plus history bookkeeping.
    /// When `overwrite` is set the existing target is captured (so undo can
    /// restore it) and removed first, since [#renameOnDisk] can't move onto an
    /// existing file.
    private void applyRename(AkytheraProject project, Path oldPath, Path newPath, String newStem, boolean overwrite) {
        Runnable restoreOverwritten = overwrite ? project.buildRestoreFor(newPath) : () -> {};
        if (overwrite) project.deletePathRecursive(newPath);

        if (!renameOnDisk(project, oldPath, newPath)) return;

        path = newPath;
        setContentName(newStem);

        HistoryManager.getInstance().pushHistory(new FileMove(
            () -> {
                if (overwrite) project.deletePathRecursive(newPath);
                if (renameOnDisk(project, oldPath, newPath)) refreshAndSelectAfterRename(newPath);
            },
            () -> {
                if (renameOnDisk(project, newPath, oldPath)) {
                    restoreOverwritten.run();
                    refreshAndSelectAfterRename(oldPath);
                }
            }
        ));

        refreshAndSelectAfterRename(newPath);
    }

    /// "The name already exists" overwrite confirmation. Same
    /// [WidgetContainerModal] shape as the content browser's other prompts;
    /// `onOverwrite` runs only when the user accepts.
    private void showRenameConflictPopup(Runnable onOverwrite) {
        int popupW = 300;
        int btnW = WidgetContainerModal.evenButtonWidth(popupW, 2);

        WidgetContainerModal popup = WidgetContainerModal.builder(
                font, Component.translatable(LangKeys.GUI_MAIN_SCREEN_CONTENT_BROWSER_POPUP_RENAME_CONFLICT_TITLE), popupW)
            .textLines(Component.translatable(LangKeys.GUI_MAIN_SCREEN_CONTENT_BROWSER_POPUP_RENAME_CONFLICT_BODY))
            // Footer buttons render right-to-left in insertion order, so add
            // Cancel first (rightmost) and OK last (leftmost) to read "OK ... Cancel".
            .footerButton(
                AnchoredButton.buttonBuilder(font, Component.translatable(LangKeys.GUI_CANCEL),
                        _ -> AkytheraEditorLayoutManager.getInstance().closeModal())
                    .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
                    .build(),
                btnW
            )
            .footerButton(
                AnchoredButton.buttonBuilder(font, Component.translatable(LangKeys.GUI_OK), _ -> {
                        AkytheraEditorLayoutManager.getInstance().closeModal();
                        onOverwrite.run();
                    })
                    .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
                    .build(),
                btnW
            )
            .build();

        AkytheraEditorLayoutManager.getInstance().openModalWidgetAtCenter(popup);
    }

    /// Compute the new on-disk path for the given user-supplied stem.
    /// Default preserves the current file extension (or absence thereof —
    /// covers folders with no extension, `.json` for datapack entries,
    /// `.efasset` for assets).
    protected Path computeNewPath(String newStem) {
        return FileSystemHelper.withNewStem(path, newStem);
    }

    /// Perform the on-disk rename `from` → `to` plus any registry /
    /// accessor re-keying. Returns `false` on failure so callers can
    /// abort the history-push / refresh.
    ///
    /// Default: no-op return `false` — non-renamable widgets
    /// (e.g. [ItemPatchContent]) inherit this and the editbox commit
    /// becomes a no-op as before.
    protected boolean renameOnDisk(AkytheraProject project, Path from, Path to) {
        return false;
    }

    /// Shared rename helper for asset widgets (mesh / armature / animation).
    /// File move + `project.renameAsset` — the latter re-keys the accessor
    /// and fires inbound `ReferenceInfo` redirects.
    protected static boolean assetRenameOnDisk(AkytheraProject project, Path from, Path to) {
        if (!FileSystemHelper.move(from, to)) return false;
        project.renameAsset(from, to);
        return true;
    }

    private static void refreshAndSelectAfterRename(Path file) {
        AkytheraEditorLayoutManager.getInstance().<MainEditorScreen>ifLastScreen(mainEditorScreen -> {
            mainEditorScreen.explorerPanel.rebuildTree();
            mainEditorScreen.explorerPanel.focusNodeByPath(file.getParent());
            mainEditorScreen.contentBrowserPanel.pickSelections(true, file);
        });
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput narrationElementOutput) {
    }

    @Override
    public void playDownSound(@NonNull SoundManager soundManager) {
    }

    @Override
    public void onClick(@NonNull MouseButtonEvent event, boolean doubleClick) {
        double x = event.x();
        double y = event.y();

        if (
            editable && // editable == editBox != null
            x >= (double)editBox.getX() &&
            y >= (double)editBox.getY() &&
            x < (double)editBox.getRight() &&
            y < (double)editBox.getBottom()
        ) {
            if (doubleClick) {
                startEditing();
            }
            if (editBox.visible) editBox.mouseClicked(event, doubleClick);
        } else {
            if (doubleClick) onDoubleClicked();
            stopEditing();
        }
    }

    public void startEditing() {
        if (!editable) return;

        editBox.visible = true;
        editBox.setValue(getContentName());
    }

    public void stopEditing() {
        if (!editable) return;

        editBox.visible = false;
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);

        if (!focused && editBox != null) editBox.visible = false;
    }

    @Override
    public boolean charTyped(@NonNull CharacterEvent event) {
        if (editable && editBox.visible && editBox.charTyped(event)) {
            return true;
        }

        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (editable && editBox.visible) {
            // Commit name change
            if (event.isConfirmation()) {
                onCommitName(editBox.getText());
                editBox.visible = false;
            }

            return editBox.keyPressed(event);
        }

        return super.keyPressed(event);
    }

    private static void blitIconAtCenter(GuiGraphicsExtractor graphics, Identifier icon, boolean showLabel, int x, int y, int w, int h) {
        int iconSize = Math.min(w, showLabel ? h - LABEL_HEIGHT - 4 : h) - 4;
        iconSize = Math.max(iconSize, 1);
        int iconX = x + (w - iconSize) / 2;
        int iconY = y + (showLabel ? (h - LABEL_HEIGHT - iconSize) / 2 : (h - iconSize) / 2);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, icon, iconX, iconY, iconSize, iconSize);
    }

    public static class ArmatureAssetContent extends ContentWidget<AssetAccessor<? extends Armature>> {
        private static final Identifier ICON = AkytheraLabs.identifier("widget/armature");

        public ArmatureAssetContent(Font font, Path path, String name) {
            super(
                font, name, true, path,
                AkytheraProject.getCurrentProject().getEditorArmatureAccessorByPath(path)
            );
        }

        @Override
        public void onDoubleClicked() {

        }

        @Override
        protected boolean renameOnDisk(AkytheraProject project, Path from, Path to) {
            return assetRenameOnDisk(project, from, to);
        }

        @Override
        protected void renderIcon(@NonNull GuiGraphicsExtractor graphics, boolean showLabel) {
            blitIconAtCenter(graphics, ICON, showLabel, getX(), getY(), getWidth(), getHeight());
        }
    }

    public static class StaticMeshContent extends ContentWidget<AssetAccessor<? extends StaticMesh>> {
        private static final Identifier ICON = AkytheraLabs.identifier("widget/static_mesh");

        public StaticMeshContent(Font font, Path path, String name) {
            super(
                font, name, true, path,
                AkytheraProject.getCurrentProject()
                    .getEditorStaticMeshAccessorByPath(path)
            );
        }

        @Override
        public void onDoubleClicked() {

        }

        @Override
        protected boolean renameOnDisk(AkytheraProject project, Path from, Path to) {
            return assetRenameOnDisk(project, from, to);
        }

        @Override
        protected void renderIcon(@NonNull GuiGraphicsExtractor graphics, boolean showLabel) {
            blitIconAtCenter(graphics, ICON, showLabel, getX(), getY(), getWidth(), getHeight());
        }
    }

    public static class SkeletalMeshContent extends ContentWidget<AssetAccessor<? extends SkeletalMesh>> {
        private static final Identifier ICON = AkytheraLabs.identifier("widget/skeletal_mesh");

        public SkeletalMeshContent(Font font, Path path, String name) {
            super(
                font, name, true, path,
                AkytheraProject.getCurrentProject()
                    .getEditorSkeletalMeshAccessorByPath(path)
            );
        }

        @Override
        public void onDoubleClicked() {

        }

        @Override
        protected boolean renameOnDisk(AkytheraProject project, Path from, Path to) {
            return assetRenameOnDisk(project, from, to);
        }

        @Override
        protected void renderIcon(@NonNull GuiGraphicsExtractor graphics, boolean showLabel) {
            blitIconAtCenter(graphics, ICON, showLabel, getX(), getY(), getWidth(), getHeight());
        }
    }

    public static class AnimationAssetContent extends ContentWidget<AssetAccessor<AnimationClip>> {
        private static final Identifier ICON = AkytheraLabs.identifier("widget/anim_sequence");

        public AnimationAssetContent(Font font, Path path, String name) {
            super(
                font, name, true, path,
                AkytheraProject.getCurrentProject()
                    .getEditorAnimationAccessorByPath(path)
            );
        }

        @Override
        public void onDoubleClicked() {
            AssetAccessor<AnimationClip> clip = getReferencingObject();
            if (clip == null) return;

            AkytheraEditorLayoutManager.getInstance().openModalWidgetAtCenter(
                new AnimationPreviewerModal(Minecraft.getInstance().font, clip)
            );
        }

        @Override
        protected boolean renameOnDisk(AkytheraProject project, Path from, Path to) {
            return assetRenameOnDisk(project, from, to);
        }

        @Override
        protected void renderIcon(@NonNull GuiGraphicsExtractor graphics, boolean showLabel) {
            blitIconAtCenter(graphics, ICON, showLabel, getX(), getY(), getWidth(), getHeight());
        }
    }

    public static abstract class DataDrivenObjectContent<T, D extends DataHolder<T>> extends ContentWidget<D> {
        protected ResourceKey<@NonNull T> resourceKey;

        public DataDrivenObjectContent(Font font, String contentName, Path path, ResourceKey<@NonNull T> resourceKey, @NonNull D referencingObject) {
            this(font, contentName, true, path, resourceKey, referencingObject);
        }

        public DataDrivenObjectContent(Font font, String contentName, boolean editable, Path path, ResourceKey<@NonNull T> resourceKey, @NonNull D referencingObject) {
            super(font, contentName, editable, path, referencingObject);
            this.resourceKey = resourceKey;
        }

        @Override
        protected boolean renameOnDisk(AkytheraProject project, Path from, Path to) {
            if (!FileSystemHelper.move(from, to)) return false;

            ResourceKey<T> newKey = project.<T>moveEntry(from, to);
            if (newKey != null) resourceKey = newKey;

            return true;
        }
    }

    public static class AnimMontageContent extends DataDrivenObjectContent<AnimationMontage, AnimationMontageHolder> {
        private static final Identifier ICON = AkytheraLabs.identifier("widget/anim_montage");

        public AnimMontageContent(Font font, ResourceKey<@NonNull AnimationMontage> resourceKey, Path path, String name) {
            super(font, name, path, resourceKey, AkytheraProject.getCurrentProject().getDataHolderOrThrow(resourceKey));
        }

        @Override
        public void onDoubleClicked() {
            // Open the live registry holder, not the cached one — a prior "Quit" may have swapped in a
            // disk-reloaded instance (see AkytheraProject#reloadEntryFromDisk).
            AkytheraEditorLayoutManager.getInstance().openNewScreen(
                new AnimationMontageEditorScreen(resourceKey, AkytheraProject.getCurrentProject().getDataHolderOrThrow(resourceKey)));
        }

        @Override
        protected void renderIcon(@NonNull GuiGraphicsExtractor graphics, boolean showLabel) {
            blitIconAtCenter(graphics, ICON, showLabel, getX(), getY(), getWidth(), getHeight());
        }
    }

    public static class AnimSequenceContent extends DataDrivenObjectContent<AnimationSequence, AnimationSequenceHolder> {
        private static final Identifier ICON = AkytheraLabs.identifier("widget/anim_sequence");

        public AnimSequenceContent(Font font, ResourceKey<@NonNull AnimationSequence> resourceKey, Path path, String name) {
            super(font, name, path, resourceKey, AkytheraProject.getCurrentProject().getDataHolderOrThrow(resourceKey));
        }

        @Override
        public void onDoubleClicked() {
            // Open the live registry holder, not the cached one (a prior "Quit" may have disk-reloaded it).
            AkytheraEditorLayoutManager.getInstance().openNewScreen(
                new AnimationSequenceEditorScreen(resourceKey, AkytheraProject.getCurrentProject().getDataHolderOrThrow(resourceKey)));
        }

        @Override
        protected void renderIcon(@NonNull GuiGraphicsExtractor graphics, boolean showLabel) {
            blitIconAtCenter(graphics, ICON, showLabel, getX(), getY(), getWidth(), getHeight());
        }
    }

    public static class BlendSpaceContent extends DataDrivenObjectContent<BlendSpace2D, BlendSpace2DHolder> {
        private static final Identifier ICON = AkytheraLabs.identifier("widget/blend_space");

        public BlendSpaceContent(Font font, ResourceKey<@NonNull BlendSpace2D> resourceKey, Path path, String name) {
            super(font, name, path, resourceKey, AkytheraProject.getCurrentProject().getDataHolderOrThrow(resourceKey));
        }

        @Override
        public void onDoubleClicked() {
            // Open the live registry holder, not the cached one (a prior "Quit" may have disk-reloaded it).
            AkytheraEditorLayoutManager.getInstance().openNewScreen(
                new BlendSpace2DEditorScreen(resourceKey, AkytheraProject.getCurrentProject().getDataHolderOrThrow(resourceKey)));
        }

        @Override
        protected void renderIcon(@NonNull GuiGraphicsExtractor graphics, boolean showLabel) {
            blitIconAtCenter(graphics, ICON, showLabel, getX(), getY(), getWidth(), getHeight());
        }
    }

    public static class AnimatorScriptContent extends DataDrivenObjectContent<DataDrivenAnimatorScript.AnimatorScriptBlueprint, AnimatorScriptHolder> {
        private static final Identifier ICON = AkytheraLabs.identifier("widget/script");

        public AnimatorScriptContent(Font font, ResourceKey<DataDrivenAnimatorScript.@NonNull AnimatorScriptBlueprint> resourceKey, Path path, String name) {
            super(font, name, path, resourceKey, AkytheraProject.getCurrentProject().getDataHolderOrThrow(resourceKey));
        }

        /// The preview model is bound when the script is created (see
        /// [com.yesman.akytheralabs.gui.panel.main.processors.AnimatorScriptResourceProcessor#onPressAddButton]),
        /// so opening just hands the holder to the editor.
        @Override
        public void onDoubleClicked() {
            // Open the live registry holder, not the cached one (a prior "Quit" may have disk-reloaded it).
            AkytheraEditorLayoutManager.getInstance().openNewScreen(
                new AnimatorScriptEditorScreen(resourceKey, AkytheraProject.getCurrentProject().getDataHolderOrThrow(resourceKey)));
        }

        @Override
        protected void renderIcon(@NonNull GuiGraphicsExtractor graphics, boolean showLabel) {
            blitIconAtCenter(graphics, ICON, showLabel, getX(), getY(), getWidth(), getHeight());
        }
    }

    public static class EntityPatchContent extends DataDrivenObjectContent<EntityPatchBlueprint, EntityPatchBlueprintHolder> {
        private static final Identifier ICON = AkytheraLabs.identifier("widget/script");

        public EntityPatchContent(Font font, ResourceKey<@NonNull EntityPatchBlueprint> resourceKey, Path path, String name) {
            super(font, name, false, path, resourceKey, AkytheraProject.getCurrentProject().getDataHolderOrThrow(resourceKey));
        }

        /// The target entity is bound when the patch is created (see
        /// [com.yesman.akytheralabs.gui.panel.main.processors.EntityPatchResourceProcessor#onPressAddButton]),
        /// so opening just hands the holder to the editor.
        @Override
        public void onDoubleClicked() {
            // Open the live registry holder, not the cached one (a prior "Quit" may have disk-reloaded it).
            AkytheraEditorLayoutManager.getInstance().openNewScreen(
                new EntityPatchEditorScreen(resourceKey, AkytheraProject.getCurrentProject().getDataHolderOrThrow(resourceKey)));
        }

        @Override
        protected void renderIcon(@NonNull GuiGraphicsExtractor graphics, boolean showLabel) {
            blitIconAtCenter(graphics, ICON, showLabel, getX(), getY(), getWidth(), getHeight());
        }
    }

    /// A single editable [com.yesman.akythera.core.util.EnumerableDataBlock] entry. Reuses
    /// the data-driven rename machinery ([DataDrivenObjectContent#renameOnDisk] → `moveEntry`,
    /// which re-keys the project's data block registry), but double-click opens nothing — data
    /// blocks are property-less. The "object" is the real engine enum value, wrapped by a
    /// per-type [DataBlockHolder].
    public static class DataBlockContent<T extends EnumerableDataBlock> extends DataDrivenObjectContent<T, DataBlockHolder<T>> {
        private static final Identifier ICON = AkytheraLabs.identifier("widget/data_block");

        public DataBlockContent(Font font, ResourceKey<@NonNull T> resourceKey, Path path, String name) {
            super(font, name, true, path, resourceKey, AkytheraProject.getCurrentProject().getDataHolderOrThrow(resourceKey));
        }

        @Override
        public void onDoubleClicked() {
            // Property-less — no editor screen.
        }

        @Override
        protected void renderIcon(@NonNull GuiGraphicsExtractor graphics, boolean showLabel) {
            blitIconAtCenter(graphics, ICON, showLabel, getX(), getY(), getWidth(), getHeight());
        }
    }

    public static class FolderContent extends ContentWidget<Void> {
        private static final Identifier ICON = AkytheraLabs.identifier("widget/folder");

        public FolderContent(Font font, String name, Path path) {
            super(
                font,
                name,
                true,
                path,
                null
            );
        }

        @Override
        protected boolean renameOnDisk(AkytheraProject project, Path from, Path to) {
            return project.renameDirectory(from, to);
        }

        @Override
        public void onDoubleClicked() {
            AkytheraEditorLayoutManager.getInstance().<MainEditorScreen>ifLastScreen(mainEditorScreen ->
                mainEditorScreen.explorerPanel.focusNodeByPath(path)
            );
        }

        @Override
        protected void renderIcon(@NonNull GuiGraphicsExtractor graphics, boolean showLabel) {
            blitIconAtCenter(graphics, ICON, showLabel, getX(), getY(), getWidth(), getHeight());
        }
    }

    public static class ItemPatchContent extends ContentWidget<ItemPatch> {
        private final ItemStack stack;

        public ItemPatchContent(final Font font, final Item item, Path path) {
            super(font, item.getDefaultInstance().getHoverName().getString(), false, path, null);
            stack = item.getDefaultInstance();
        }

        @Override
        public void onDoubleClicked() {

        }

        @Override
        protected void renderIcon(@NonNull GuiGraphicsExtractor guiGraphics, boolean showLabel) {
            int iconX = getX() + (getWidth() - ICON_SIZE) / 2;
            int iconY = showLabel
                ? getY() + (getHeight() - ICON_SIZE) / 2 - 4
                : getY() + (getHeight() - ICON_SIZE) / 2;

            guiGraphics.item(stack, iconX, iconY);
        }

        @Override
        public String getContentName() {
            return stack.getHoverName().getString();
        }
    }
}
