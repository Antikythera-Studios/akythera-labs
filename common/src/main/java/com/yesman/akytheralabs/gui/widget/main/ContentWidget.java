package com.yesman.akytheralabs.gui.widget.main;

import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.api.input.InputAction;
import com.yesman.akythera.api.world.ability.AbilityEffect;
import com.yesman.akythera.client.api.gui.AkytheraButton;
import com.yesman.akythera.client.api.gui.Grid;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.client.core.asset.mesh.SkeletalMesh;
import com.yesman.akythera.client.core.asset.mesh.StaticMesh;
import com.yesman.akythera.core.animation.keyframe.AnimationClip;
import com.yesman.akythera.core.animation.playable.AnimationMontage;
import com.yesman.akythera.core.animation.playable.AnimationSequence;
import com.yesman.akythera.core.animation.playable.BlendSpace2D;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.physics.ArmaturePhysicsData;
import com.yesman.akythera.core.blueprint.AbilityBlueprint;
import com.yesman.akythera.core.blueprint.AbilityCueBlueprint;
import com.yesman.akythera.core.blueprint.EntityPatchBlueprint;
import com.yesman.akythera.core.blueprint.ItemPatchBlueprint;
import com.yesman.akythera.core.blueprint.particle.ParticleBlueprint;
import com.yesman.akythera.core.blueprint.particle.ParticleSystemBlueprint;
import com.yesman.akythera.core.blueprint.script.DataDrivenAnimatorScript;
import com.yesman.akythera.core.blueprint.widget.WidgetBlueprint;
import com.yesman.akythera.core.util.EnumerableDataBlock;
import com.yesman.akythera.mixinaccessor.client.GuiGraphicsAccessor;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.FileSystemHelper;
import com.yesman.akytheralabs.akythera.ability.AbilityBlueprintHolder;
import com.yesman.akytheralabs.akythera.ability.AbilityCueBlueprintHolder;
import com.yesman.akytheralabs.akythera.ability.AbilityEffectHolder;
import com.yesman.akytheralabs.akythera.ability.InputActionHolder;
import com.yesman.akytheralabs.akythera.particle.ParticleBlueprintHolder;
import com.yesman.akytheralabs.akythera.particle.ParticleSystemHolder;
import com.yesman.akytheralabs.akythera.animation.AnimationMontageHolder;
import com.yesman.akytheralabs.akythera.animation.AnimationSequenceHolder;
import com.yesman.akytheralabs.akythera.animation.AnimatorScriptHolder;
import com.yesman.akytheralabs.akythera.animation.BlendSpace2DHolder;
import com.yesman.akytheralabs.akythera.entitypatch.EntityPatchBlueprintHolder;
import com.yesman.akytheralabs.akythera.itempatch.ItemPatchBlueprintHolder;
import com.yesman.akytheralabs.akythera.widget.WidgetBlueprintHolder;
import com.yesman.akytheralabs.dataholder.AbstractDataHolder;
import com.yesman.akytheralabs.dataholder.DataBlockHolder;
import com.yesman.akytheralabs.dataholder.DataHolder;
import com.yesman.akytheralabs.dataholder.sound.SoundEventHolder;
import com.yesman.akytheralabs.editor.ContentClipboard;
import com.yesman.akytheralabs.editor.EditorFieldScanner;
import com.yesman.akytheralabs.editor.historytrack.FileMove;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.main.ContentBrowserPanel;
import com.yesman.akytheralabs.gui.panel.main.processors.AnimatorScriptResourceProcessor;
import com.yesman.akytheralabs.gui.panel.main.processors.EntityPatchResourceProcessor;
import com.yesman.akytheralabs.gui.panel.main.processors.ItemPatchResourceProcessor;
import com.yesman.akytheralabs.gui.screen.AbilityCueEditorScreen;
import com.yesman.akytheralabs.gui.screen.ParticleEditorScreen;
import com.yesman.akytheralabs.gui.screen.ParticleSystemScreen;
import com.yesman.akytheralabs.gui.screen.AbilityEditorScreen;
import com.yesman.akytheralabs.gui.screen.AbilityPropertiesEditorScreen;
import com.yesman.akytheralabs.gui.screen.AnimationAssetEditorScreen;
import com.yesman.akytheralabs.gui.screen.AnimationMontageEditorScreen;
import com.yesman.akytheralabs.gui.screen.AnimationSequenceEditorScreen;
import com.yesman.akytheralabs.gui.screen.AnimatorScriptEditorScreen;
import com.yesman.akytheralabs.gui.screen.ArmatureAssetEditorScreen;
import com.yesman.akytheralabs.gui.screen.ArmaturePhysicsDataEditorScreen;
import com.yesman.akytheralabs.gui.screen.BlendSpace2DEditorScreen;
import com.yesman.akytheralabs.gui.screen.ClothPhysicsDataEditorScreen;
import com.yesman.akytheralabs.gui.screen.DataBlockEditorScreen;
import com.yesman.akytheralabs.gui.screen.EntityPatchEditorScreen;
import com.yesman.akytheralabs.gui.screen.ItemPatchEditorScreen;
import com.yesman.akytheralabs.gui.screen.MainEditorScreen;
import com.yesman.akytheralabs.gui.screen.MeshAssetEditorScreen;
import com.yesman.akytheralabs.gui.screen.SoundAssetEditorScreen;
import com.yesman.akytheralabs.gui.screen.TexturePreviewScreen;
import com.yesman.akytheralabs.gui.screen.WidgetEditorScreen;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsButtonTheme;
import com.yesman.akytheralabs.gui.widget.EditorEditBox;
import com.yesman.akytheralabs.gui.widget.RefusalPopup;
import com.yesman.akytheralabs.gui.widget.WidgetContainerModal;
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
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/// A single [ContentBrowserPanel] object button
///
/// Required to implement [AnchoredWidget] to be used in [Grid]
public abstract class ContentWidget<T> extends AbstractWidget implements PatchComponent {
    /// Minimum widget height at which the bottom label is rendered
    private static final int LABEL_VISIBLE_THRESHOLD = 28;
    private static final int LABEL_HEIGHT = 12;
    private static final int ICON_SIZE = 16;

    /// Height of the red strip drawn along the widget's bottom edge when the
    /// referencing [DataHolder] failed to load.
    private static final int ERROR_BAR_HEIGHT = 3;
    private static final int COLOR_ERROR = 0xFFD03A3A;
    private static final int COLOR_ERROR_BORDER = 0xFF7A1F1F;
    private static final int COLOR_ERROR_TEXT = 0xFFFF8A8A;

    /// Translucent black over a tile sitting on the clipboard as a cut.
    private static final int COLOR_CUT_VEIL = 0x80101010;

    /// Wrap width of the failure tooltip. The message is never truncated — it only wraps.
    private static final int ERROR_TOOLTIP_WRAP_WIDTH = 260;

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
    protected final EditorEditBox<String> editBox;
    protected final boolean editable;

    public ContentWidget(final Font font, final String contentName, final boolean editable, final Path path, final @Nullable T referencingObject) {
        super(0, 0, 0, 0, Component.literal("content_widget"));

        this.font = font;
        this.editable = editable;
        this.contentName = contentName;
        this.editBox = editable
            ? EditorEditBox.stringBox(
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

    /// Whether double-click opens something for this content. False for the kinds whose
    /// [#onDoubleClicked] is empty, so the menus offering `Open` grey the row instead of carrying a
    /// row that does nothing.
    public boolean isOpenable() {
        return true;
    }

    /// True while the rename box is open over this widget's label.
    public boolean isEditing() {
        return editable && editBox.visible;
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
        Exception loadingFailure = getLoadingFailureException();

        // Background
        guiGraphics.fill(x, y, x + w, y + h, highlighted ? 0xFF1E4D72 : (hovered ? 0xFF2A2A2A : 0xFF1E1E1E));

        // 1px border on each edge — turns red when the holder failed to load
        int borderColor = loadingFailure != null
            ? COLOR_ERROR_BORDER
            : (dragHighlighted || highlighted ? 0xFF5A8AAA : 0xFF3A3A3A);
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

        // Dimmed while it waits for its cut's paste.
        if (ContentClipboard.getInstance().isCut(path)) {
            guiGraphics.fill(x, y, x + w, y + h, COLOR_CUT_VEIL);
        }

        if (loadingFailure != null) {
            // Red strip on the bottom edge, inside the border and below the label baseline.
            guiGraphics.fill(x + 1, y + h - ERROR_BAR_HEIGHT - 1, x + w - 1, y + h - 1, COLOR_ERROR);

            // The content browser scissors its grid, so the tooltip is deferred to the frame's
            // tail (drawn outside the clip, above every other widget).
            if (hovered) {
                ((GuiGraphicsAccessor) guiGraphics).akythera$setDeferredWidgetRenderer(
                    () -> drawFailureTooltip(guiGraphics, loadingFailure, mouseX, mouseY)
                );
            }
        }
    }

    /// The load failure of the referencing object ([DataHolder#getFailureException] — a root decode
    /// failure, a nested-content failure, or an unbuildable runtime object), or null when it loaded
    /// fine / isn't a [DataHolder] at all (directories and raw asset accessors never carry one). Any
    /// of the three red-marks the tile; only a root decode failure gates opening behind the replace
    /// popup. The nested channel is a live query, so it clears the moment a clean save / compile
    /// heals and flushes the content.
    private @Nullable Exception getLoadingFailureException() {
        if (!(referencingObject instanceof DataHolder<?> dataHolder)) return null;

        return dataHolder.getFailureException();
    }

    /// Draws the whole exception message next to the cursor — wrapped, never truncated. The box
    /// is clamped into the scaled window; when the message is taller than the screen it starts at
    /// the top edge and simply runs long, since the message must stay fully readable.
    private void drawFailureTooltip(GuiGraphicsExtractor guiGraphics, Exception exception, int mouseX, int mouseY) {
        List<FormattedCharSequence> lines = new ArrayList<>(
            font.split(Component.literal(exception.getClass().getSimpleName()), ERROR_TOOLTIP_WRAP_WIDTH));
        int headerLines = lines.size();

        String message = exception.getMessage();
        if (message != null && !message.isBlank()) {
            lines.addAll(font.split(Component.literal(message), ERROR_TOOLTIP_WRAP_WIDTH));
        }

        int pad = 4;
        int lineH = font.lineHeight + 1;
        int textW = lines.stream().mapToInt(font::width).max().orElse(0);
        int boxW = textW + pad * 2;
        int boxH = lines.size() * lineH - 1 + pad * 2;

        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();

        int boxX = Math.max(2, Math.min(mouseX + 12, screenW - boxW - 2));
        int boxY = Math.max(2, Math.min(mouseY + 12, screenH - boxH - 2));

        guiGraphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xF0200C0C);
        guiGraphics.outline(boxX, boxY, boxW, boxH, COLOR_ERROR_BORDER);

        for (int i = 0; i < lines.size(); i++) {
            guiGraphics.text(font, lines.get(i), boxX + pad, boxY + pad + i * lineH,
                i < headerLines ? COLOR_ERROR_TEXT : 0xFFE0E0E0);
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

        String refusedTagPath = project.refusedTagPath(oldPath, newPath, false);

        if (refusedTagPath != null) {
            RefusalPopup.open(
                Component.translatable(LangKeys.GUI_MAIN_SCREEN_CONTENT_BROWSER_POPUP_DATA_BLOCK_TAKEN_TITLE),
                Component.translatable(LangKeys.GUI_MAIN_SCREEN_CONTENT_BROWSER_POPUP_DATA_BLOCK_TAKEN_BODY, refusedTagPath)
            );

            return;
        }

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
                AkytheraButton.buttonBuilder(font, LocalizedMessage.translatable(LangKeys.GUI_CANCEL),
                        () -> ModalManager.getInstance().closeModal())
                    .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
                    .build(),
                btnW
            )
            .footerButton(
                AkytheraButton.buttonBuilder(font, LocalizedMessage.translatable(LangKeys.GUI_OK), () -> {
                        ModalManager.getInstance().closeModal();
                        onOverwrite.run();
                    })
                    .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
                    .build(),
                btnW
            )
            .build();

        ModalManager.getInstance().openModalWidgetAtCenter(popup);
    }

    /// Compute the new on-disk path for the given user-supplied stem.
    /// Default preserves the current file extension (or absence thereof —
    /// covers folders with no extension, `.json` for datapack entries,
    /// `.akasset` for assets).
    protected Path computeNewPath(String newStem) {
        return FileSystemHelper.withNewStem(path, newStem);
    }

    /// Perform the on-disk rename `from` → `to` plus any registry /
    /// accessor re-keying. Returns `false` on failure so callers can
    /// abort the history-push / refresh.
    ///
    /// Default: returns `false`, which leaves a non-renamable widget's editbox commit a no-op.
    protected boolean renameOnDisk(AkytheraProject project, Path from, Path to) {
        return false;
    }

    /// Shared rename helper for the asset widgets — mesh, armature, animation and texture. Moves the
    /// file, then `project.renameAsset` carries its sidecars and re-keys the shared accessor so every
    /// referrer follows.
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
            if (doubleClick) {
                // A decode-failed entry never opens its editor directly — the only way forward is
                // the gated replace-with-default popup (its data is defaults + retained raw; an
                // object-creation failure alone opens normally, the data is intact and editable).
                if (referencingObject instanceof AbstractDataHolder<?> failed
                    && failed.getDeserializationFailureException() != null
                    && failed.getOwnResourceKey() != null) {
                    showReplaceFailedEntryPopup(failed);
                } else {
                    onDoubleClicked();
                }
            }
            stopEditing();
        }
    }

    /// The gated recovery affordance for an entry whose file failed to deserialize: replace the
    /// object with a default one (writing it to disk), or leave the file byte-identical. Same
    /// [WidgetContainerModal] shape as the content browser's other prompts.
    private void showReplaceFailedEntryPopup(AbstractDataHolder<?> failedHolder) {
        ResourceKey<?> key = failedHolder.getOwnResourceKey();
        if (key == null) return;

        int popupW = 320;
        int btnW = WidgetContainerModal.evenButtonWidth(popupW, 2);

        Exception failure = failedHolder.getDeserializationFailureException();
        String failureMessage = failure == null ? "" : String.valueOf(failure.getMessage());

        WidgetContainerModal popup = WidgetContainerModal.builder(
                font, Component.translatable(LangKeys.GUI_MAIN_SCREEN_CONTENT_BROWSER_POPUP_REPLACE_FAILED_TITLE), popupW)
            .textLines(Component.literal(failureMessage))
            .textLines(Component.translatable(LangKeys.GUI_MAIN_SCREEN_CONTENT_BROWSER_POPUP_REPLACE_FAILED_BODY, key.identifier().toString()))
            // Footer buttons render right-to-left in insertion order, so add
            // Cancel first (rightmost) and Replace last (leftmost).
            .footerButton(
                AkytheraButton.buttonBuilder(font, LocalizedMessage.translatable(LangKeys.GUI_CANCEL),
                        () -> ModalManager.getInstance().closeModal())
                    .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
                    .build(),
                btnW
            )
            .footerButton(
                AkytheraButton.buttonBuilder(font, LocalizedMessage.translatable(LangKeys.GUI_MESSAGE_BUTTON_REPLACE), () -> {
                        ModalManager.getInstance().closeModal();
                        // Clear failure + retained raw: the default holder state becomes
                        // authoritative, the save writes it, and the tile un-reddens.
                        failedHolder.markResolved();
                        AkytheraProject.getCurrentProject().saveEntry(key);
                        onDoubleClicked();
                    })
                    .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
                    .build(),
                btnW
            )
            .build();

        ModalManager.getInstance().openModalWidgetAtCenter(popup);
    }

    /// Opens the rename box over the label with the whole name selected, so the first typed
    /// character replaces it.
    public void startEditing() {
        if (!editable) return;

        editBox.visible = true;
        editBox.setValue(getContentName());

        // Re-focus: a previous rename committed with Enter left the box unfocused, and an unfocused
        // box drops every key and character it is handed.
        editBox.setFocused(true);
        editBox.selectAll();
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
            if (!(getReferencingObject() instanceof AkytheraProject.EditorArmatureAccessor<?> armature)) return;

            AkytheraEditorLayoutManager.getInstance().openNewScreen(new ArmatureAssetEditorScreen(armature));
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

    public static class ArmaturePhysicsContent extends ContentWidget<AssetAccessor<? extends ArmaturePhysicsData>> {
        private static final Identifier ICON = AkytheraLabs.identifier("widget/armature_physics_data");

        public ArmaturePhysicsContent(Font font, Path path, String name) {
            super(
                font, name, false, path,
                AkytheraProject.getCurrentProject().getEditorArmaturePhysicsAccessorByPath(path)
            );
        }

        @Override
        public void onDoubleClicked() {
            if (!(getReferencingObject() instanceof AkytheraProject.EditorArmaturePhysicsAccessor accessor)) return;

            AkytheraEditorLayoutManager.getInstance().openNewScreen(new ArmaturePhysicsDataEditorScreen(accessor));
        }

        @Override
        protected void renderIcon(@NonNull GuiGraphicsExtractor graphics, boolean showLabel) {
            blitIconAtCenter(graphics, ICON, showLabel, getX(), getY(), getWidth(), getHeight());
        }
    }

    public static class ClothPhysicsContent extends ContentWidget<AssetAccessor<? extends SkeletalMesh>> {
        private static final Identifier ICON = AkytheraLabs.identifier("widget/cloth_data");

        public ClothPhysicsContent(Font font, Path path, String name) {
            super(
                font, name, false, path,
                AkytheraProject.getCurrentProject().getClothMeshAccessorByPath(path)
            );
        }

        @Override
        public void onDoubleClicked() {
            AkytheraEditorLayoutManager.getInstance().openNewScreen(new ClothPhysicsDataEditorScreen(getReferencingObject()));
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
            if (!(getReferencingObject() instanceof AkytheraProject.EditorMeshAccessor<?> mesh)) return;

            AkytheraEditorLayoutManager.getInstance().openNewScreen(new MeshAssetEditorScreen(mesh));
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
            if (!(getReferencingObject() instanceof AkytheraProject.EditorMeshAccessor<?> mesh)) return;

            AkytheraEditorLayoutManager.getInstance().openNewScreen(new MeshAssetEditorScreen(mesh));
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
            if (!(getReferencingObject() instanceof AkytheraProject.EditorAnimationAccessor clip)) return;

            AkytheraEditorLayoutManager.getInstance().openNewScreen(new AnimationAssetEditorScreen(clip));
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

    /// A PNG under [AkytheraProject#TEXTURES_DIRECTORY]. No accessor stands for it: the file is the
    /// texture, read by whatever previews it.
    public static class TextureAssetContent extends ContentWidget<Void> {
        private static final Identifier ICON = AkytheraLabs.identifier("widget/texture_asset");

        public TextureAssetContent(Font font, Path path, String name) {
            super(font, name, true, path, null);
        }

        @Override
        public void onDoubleClicked() {
            AkytheraEditorLayoutManager.getInstance().openNewScreen(new TexturePreviewScreen(path));
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

    /// A sound event under [AkytheraProject#SOUNDS_DIRECTORY], see [SoundEventHolder]. No accessor
    /// stands for it: the file is the event, read by whatever plays it.
    public static class SoundEventContent extends ContentWidget<Void> {
        private static final Identifier ICON = AkytheraLabs.identifier("widget/sound_file");

        public SoundEventContent(Font font, Path path, String name) {
            super(font, name, true, path, null);
        }

        @Override
        public void onDoubleClicked() {
            AkytheraEditorLayoutManager.getInstance().openNewScreen(new SoundAssetEditorScreen(path));
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

        /// The project registry entry this widget stands for. Re-read after a rename, which re-keys it.
        public ResourceKey<@NonNull T> getResourceKey() {
            return resourceKey;
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
        /// [AnimatorScriptResourceProcessor#onPressAddButton]), so opening just hands the holder to the
        /// editor.
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
        /// [EntityPatchResourceProcessor#onPressAddButton]), so opening just hands the holder to the editor.
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

    /// A single editable [EnumerableDataBlock] entry. Reuses the data-driven rename machinery
    /// ([DataDrivenObjectContent#renameOnDisk] → `moveEntry`, which re-keys the project's data block
    /// registry). The "object" is the real engine enum value, wrapped by a per-type [DataBlockHolder].
    /// A holder declaring no editor field — the property-less types, whose identity *is* the identifier
    /// — has nothing to open; one that does opens a [DataBlockEditorScreen].
    public static class DataBlockContent<T extends EnumerableDataBlock> extends DataDrivenObjectContent<T, DataBlockHolder<T>> {
        private static final Identifier ICON = AkytheraLabs.identifier("widget/data_block");

        public DataBlockContent(Font font, ResourceKey<@NonNull T> resourceKey, Path path, String name) {
            super(font, name, true, path, resourceKey, AkytheraProject.getCurrentProject().getDataHolderOrThrow(resourceKey));
        }

        @Override
        public void onDoubleClicked() {
            if (!isOpenable()) return;

            // Open the live registry holder, not the cached one (a prior "Quit" may have disk-reloaded it).
            DataBlockHolder<T> holder = AkytheraProject.getCurrentProject().getDataHolderOrThrow(resourceKey);
            AkytheraEditorLayoutManager.getInstance().openNewScreen(new DataBlockEditorScreen(resourceKey, holder));
        }

        @Override
        public boolean isOpenable() {
            DataBlockHolder<T> holder = getReferencingObject();
            return holder != null && !EditorFieldScanner.fieldsOf(holder.getClass()).isEmpty();
        }

        @Override
        protected void renderIcon(@NonNull GuiGraphicsExtractor graphics, boolean showLabel) {
            blitIconAtCenter(graphics, ICON, showLabel, getX(), getY(), getWidth(), getHeight());
        }
    }

    public static class FolderContent extends ContentWidget<Void> {
        private static final Identifier ICON = AkytheraLabs.identifier("widget/folder");

        public FolderContent(Font font, String name, Path path, boolean editable) {
            super(
                font,
                name,
                editable,
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

    public static class AbilityContent extends DataDrivenObjectContent<AbilityBlueprint, AbilityBlueprintHolder> {
        private static final Identifier ICON = AkytheraLabs.identifier("widget/script");

        public AbilityContent(Font font, ResourceKey<@NonNull AbilityBlueprint> resourceKey, Path path, String name) {
            super(font, name, path, resourceKey, AkytheraProject.getCurrentProject().getDataHolderOrThrow(resourceKey));
        }

        @Override
        public void onDoubleClicked() {
            AkytheraEditorLayoutManager.getInstance().openNewScreen(new AbilityEditorScreen(
                resourceKey, AkytheraProject.getCurrentProject().getDataHolderOrThrow(resourceKey)));
        }

        @Override
        protected void renderIcon(@NonNull GuiGraphicsExtractor graphics, boolean showLabel) {
            blitIconAtCenter(graphics, ICON, showLabel, getX(), getY(), getWidth(), getHeight());
        }
    }

    public static class AbilityCueContent extends DataDrivenObjectContent<AbilityCueBlueprint, AbilityCueBlueprintHolder> {
        private static final Identifier ICON = AkytheraLabs.identifier("widget/script");

        public AbilityCueContent(Font font, ResourceKey<@NonNull AbilityCueBlueprint> resourceKey, Path path, String name) {
            super(font, name, path, resourceKey, AkytheraProject.getCurrentProject().getDataHolderOrThrow(resourceKey));
        }

        @Override
        public void onDoubleClicked() {
            AkytheraEditorLayoutManager.getInstance().openNewScreen(new AbilityCueEditorScreen(
                resourceKey, AkytheraProject.getCurrentProject().getDataHolderOrThrow(resourceKey)));
        }

        @Override
        protected void renderIcon(@NonNull GuiGraphicsExtractor graphics, boolean showLabel) {
            blitIconAtCenter(graphics, ICON, showLabel, getX(), getY(), getWidth(), getHeight());
        }
    }

    public static class WidgetBlueprintContent extends DataDrivenObjectContent<WidgetBlueprint, WidgetBlueprintHolder> {
        private static final Identifier ICON = AkytheraLabs.identifier("widget/widget_blueprint");

        public WidgetBlueprintContent(Font font, ResourceKey<@NonNull WidgetBlueprint> resourceKey, Path path, String name) {
            super(font, name, path, resourceKey, AkytheraProject.getCurrentProject().getDataHolderOrThrow(resourceKey));
        }

        @Override
        public void onDoubleClicked() {
            AkytheraEditorLayoutManager.getInstance().openNewScreen(new WidgetEditorScreen(
                resourceKey, AkytheraProject.getCurrentProject().getDataHolderOrThrow(resourceKey)));
        }

        @Override
        protected void renderIcon(@NonNull GuiGraphicsExtractor graphics, boolean showLabel) {
            blitIconAtCenter(graphics, ICON, showLabel, getX(), getY(), getWidth(), getHeight());
        }
    }

    public static class AbilityEffectContent extends DataDrivenObjectContent<AbilityEffect, AbilityEffectHolder> {
        private static final Identifier ICON = AkytheraLabs.identifier("widget/data_block");

        public AbilityEffectContent(Font font, ResourceKey<@NonNull AbilityEffect> resourceKey, Path path, String name) {
            super(font, name, path, resourceKey, AkytheraProject.getCurrentProject().getDataHolderOrThrow(resourceKey));
        }

        @Override
        public void onDoubleClicked() {
            AkytheraEditorLayoutManager.getInstance().openNewScreen(new AbilityPropertiesEditorScreen(
                resourceKey, AkytheraProject.getCurrentProject().getDataHolderOrThrow(resourceKey)));
        }

        @Override
        protected void renderIcon(@NonNull GuiGraphicsExtractor graphics, boolean showLabel) {
            blitIconAtCenter(graphics, ICON, showLabel, getX(), getY(), getWidth(), getHeight());
        }
    }

    public static class InputActionContent extends DataDrivenObjectContent<InputAction, InputActionHolder> {
        private static final Identifier ICON = AkytheraLabs.identifier("widget/data_block");

        public InputActionContent(Font font, ResourceKey<@NonNull InputAction> resourceKey, Path path, String name) {
            super(font, name, path, resourceKey, AkytheraProject.getCurrentProject().getDataHolderOrThrow(resourceKey));
        }

        @Override
        public void onDoubleClicked() {
            AkytheraEditorLayoutManager.getInstance().openNewScreen(new AbilityPropertiesEditorScreen(
                resourceKey, AkytheraProject.getCurrentProject().getDataHolderOrThrow(resourceKey)));
        }

        @Override
        protected void renderIcon(@NonNull GuiGraphicsExtractor graphics, boolean showLabel) {
            blitIconAtCenter(graphics, ICON, showLabel, getX(), getY(), getWidth(), getHeight());
        }
    }

    public static class ParticlePieceContent extends DataDrivenObjectContent<ParticleBlueprint, ParticleBlueprintHolder> {
        private static final Identifier ICON = AkytheraLabs.identifier("widget/particle_piece");

        public ParticlePieceContent(Font font, ResourceKey<@NonNull ParticleBlueprint> resourceKey, Path path, String name) {
            super(font, name, path, resourceKey, AkytheraProject.getCurrentProject().getDataHolderOrThrow(resourceKey));
        }

        @Override
        public void onDoubleClicked() {
            AkytheraEditorLayoutManager.getInstance().openNewScreen(new ParticleEditorScreen(
                resourceKey, AkytheraProject.getCurrentProject().getDataHolderOrThrow(resourceKey)));
        }

        @Override
        protected void renderIcon(@NonNull GuiGraphicsExtractor graphics, boolean showLabel) {
            blitIconAtCenter(graphics, ICON, showLabel, getX(), getY(), getWidth(), getHeight());
        }
    }

    public static class ParticleSystemContent extends DataDrivenObjectContent<ParticleSystemBlueprint, ParticleSystemHolder> {
        private static final Identifier ICON = AkytheraLabs.identifier("widget/particle_system");

        public ParticleSystemContent(Font font, ResourceKey<@NonNull ParticleSystemBlueprint> resourceKey, Path path, String name) {
            super(font, name, path, resourceKey, AkytheraProject.getCurrentProject().getDataHolderOrThrow(resourceKey));
        }

        @Override
        public void onDoubleClicked() {
            AkytheraEditorLayoutManager.getInstance().openNewScreen(new ParticleSystemScreen(
                resourceKey, AkytheraProject.getCurrentProject().getDataHolderOrThrow(resourceKey)));
        }

        @Override
        protected void renderIcon(@NonNull GuiGraphicsExtractor graphics, boolean showLabel) {
            blitIconAtCenter(graphics, ICON, showLabel, getX(), getY(), getWidth(), getHeight());
        }
    }

    public static class ItemPatchContent extends DataDrivenObjectContent<ItemPatchBlueprint, ItemPatchBlueprintHolder> {
        private static final Identifier ICON = AkytheraLabs.identifier("widget/script");

        /// The entry id names the item this patches, so a rename is what turns an item-bound patch
        /// into a meta one and back.
        public ItemPatchContent(Font font, ResourceKey<@NonNull ItemPatchBlueprint> resourceKey, Path path, String name) {
            super(font, name, path, resourceKey, AkytheraProject.getCurrentProject().getDataHolderOrThrow(resourceKey));
        }

        /// The target item is bound at creation (see [ItemPatchResourceProcessor#onPressAddButton]), so
        /// opening just hands the holder to the editor.
        @Override
        public void onDoubleClicked() {
            // Open the live registry holder, not the cached one (a prior "Quit" may have disk-reloaded it).
            AkytheraEditorLayoutManager.getInstance().openNewScreen(
                new ItemPatchEditorScreen(resourceKey, AkytheraProject.getCurrentProject().getDataHolderOrThrow(resourceKey)));
        }

        @Override
        protected void renderIcon(@NonNull GuiGraphicsExtractor graphics, boolean showLabel) {
            blitIconAtCenter(graphics, ICON, showLabel, getX(), getY(), getWidth(), getHeight());
        }
    }
}
