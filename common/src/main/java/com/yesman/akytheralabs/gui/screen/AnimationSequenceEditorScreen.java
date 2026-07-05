package com.yesman.akytheralabs.gui.screen;

import com.yesman.akythera.client.gui.widget.ModelPreviewer;
import com.yesman.akythera.client.gui.widget.WidgetTable;
import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akythera.core.animation.keyframe.AnimationClip;
import com.yesman.akythera.core.animation.playable.AnimationSequence;
import com.yesman.akythera.core.asset.loader.ArmatureLoader;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.dataholder.EntityPreviewModel;
import com.yesman.akytheralabs.dataholder.animation.AnimationSequenceHolder;
import com.yesman.akytheralabs.dataholder.animation.NotifyTrackHolder;
import com.yesman.akytheralabs.editor.EditorWidgetFactory;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.WorkHistory;
import com.yesman.akytheralabs.editor.keybind.EditorKeyMappings;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.AdjustablePanelManager;
import com.yesman.akytheralabs.gui.panel.animation.NotifyDetailsPanel;
import com.yesman.akytheralabs.gui.widget.AwaitIconModal;
import com.yesman.akytheralabs.gui.widget.PanelHeader;
import com.yesman.akytheralabs.gui.widget.WidgetContainerModal;
import com.yesman.akytheralabs.gui.widget.animation.AnimTrackEntry;
import com.yesman.akytheralabs.gui.widget.animation.AnimationTrackEditorPanel;
import com.yesman.akytheralabs.gui.widget.animation.NotifyRow;
import com.yesman.akytheralabs.gui.widget.main.EditorPreferencesModalGenerator;
import com.yesman.akytheralabs.gui.widget.main.ModelSelectModal;
import com.yesman.akytheralabs.gui.widget.main.TopMenuBar;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class AnimationSequenceEditorScreen extends Screen implements ModalAccessibleScreen, SafeCloseScreen {
    private static final int TOP_BAR_H = 22;
    private static final int ROW_H = 20;

    /// Trajectory sampling interval (sec) — mirrors the root-motion fixed tick (0.05).
    private static final float TRAJECTORY_SAMPLE_STEP = 0.05F;

    private final AdjustablePanelManager panelManager;
    private final TopMenuBar topMenuBar;
    private final ModelPreviewer modelPreviewer;
    private final WidgetTable propertiesTable;
    private final PanelHeader propertiesHeader;
    private final AnimationTrackEditorPanel notifyTrackEditorPanel;
    private final NotifyDetailsPanel notifyDetailsPanel;
    private final AnimationSequenceHolder holder;
    private final ResourceKey<@NonNull AnimationSequence> resourceKey;

    /// History stack top at the last save (or screen open). The title shows a `*` while the live top
    /// differs from this; a completed save re-snapshots it. See [HistoryManager#currentSavePoint].
    private @Nullable WorkHistory savePoint;

    public AnimationSequenceEditorScreen(ResourceKey<@NonNull AnimationSequence> resourceKey, AnimationSequenceHolder holder) {
        super(Component.translatable(LangKeys.GUI_ANIM_SEQUENCE_SCREEN_TITLE));

        this.resourceKey = resourceKey;
        this.holder = holder;

        topMenuBar = new TopMenuBar(
            minecraft.font,
            Component.literal(resourceKey.identifier().toString()),
            AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(TOP_BAR_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            _ -> handleQuitRequest(),
            TopMenuBar.menuBuilder()
                .newList(Component.translatable(LangKeys.GUI_MENU_BAR_FILE))
                    .newOption(Component.translatable(LangKeys.GUI_MENU_BAR_FILE_SAVE), this::save)
                .done()
                .newList(Component.translatable(LangKeys.GUI_MENU_BAR_EDIT))
                    .newOption(Component.translatable(LangKeys.GUI_MAIN_SCREEN_MENU_BAR_EDIT_EDITOR_PREFERENCES), () -> EditorPreferencesModalGenerator.open(font))
                .done()
                .newList(Component.translatable(LangKeys.GUI_MENU_BAR_VIEW))
                    .newOption(Component.translatable(LangKeys.GUI_MENU_BAR_VIEW_SELECT_PREVIEW_MODEL), this::openPreviewModelSelectModal)
                .done()
                .newList(Component.translatable(LangKeys.GUI_MAIN_SCREEN_MENU_BAR_HELP))
                    .newOption(Component.translatable(LangKeys.GUI_MAIN_SCREEN_MENU_BAR_HELP_DOCS), () -> {})
                .done()
        );

        modelPreviewer = new ModelPreviewer(
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM,
            AkytheraEditorLayoutManager.getInstance()::getLayoutGuiScale
        );

        modelPreviewer.addComponent(new ModelPreviewer.CameraComponent(true));
        modelPreviewer.addComponent(new ModelPreviewer.BackgroundComponent(16, 16, 16, 255));
        modelPreviewer.addComponent(new ModelPreviewer.GridFloorComponent(2.0F, 50, 68, 68, 68, 255));
        EntityPreviewModel.display(modelPreviewer, holder.getPreviewModel(), true, true);

        propertiesHeader = new PanelHeader(
            minecraft.font,
            Component.translatable(LangKeys.GUI_WIDGET_DETAILS)
        );

        propertiesTable = new WidgetTable(
            this,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(PanelHeader.DEFAULT_HEADER_HEIGHT + 2), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM,
            ROW_H
        );

        new EditorWidgetFactory(minecraft.font).fillWidgetTable(propertiesTable, holder);

        notifyDetailsPanel = new NotifyDetailsPanel(this);
        notifyTrackEditorPanel = new AnimationTrackEditorPanel(
            holder.getNotifyTrackHolder(),
            modelPreviewer,
            holder,
            notifyDetailsPanel::onSelectionChanged,
            panel -> {
                List<AnimTrackEntry> rows = new ArrayList<>();
                for (NotifyTrackHolder.Track t : holder.getNotifyTrackHolder().getTracks()) {
                    rows.add(new NotifyRow(panel, t));
                }
                return rows;
            }
        );
        notifyTrackEditorPanel.ensureAtLeastOneTrack();

        refreshPreviewAnimation();

        // Show the root-motion trajectory while the Root Motion flag is on; re-evaluated on flip
        // (incl. undo/redo, which replays the setter).
        holder.setRootMotionChangeListener(_ -> refreshRootTrajectory());
        refreshRootTrajectory();

        // Re-display when the Animation PopupBox (or an undo/redo) swaps the clip: the pick modal
        // may also have changed the preview model, so the entity component is rebuilt from the
        // holder before the new clip is replayed. The notify track then re-fits its frame window
        // and playhead to the new clip's length.
        holder.setAnimClipChangeListener(() -> {
            EntityPreviewModel.display(modelPreviewer, holder.getPreviewModel(), true, true);
            refreshPreviewAnimation();
            refreshRootTrajectory();
            notifyTrackEditorPanel.onClipLengthChanged();
        });

        panelManager = new AdjustablePanelManager(
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(TOP_BAR_H), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM
        );

        List<Integer> layoutRatios = AkytheraProject.getCurrentProject().getLayoutRatioOrDefault(getClass(), 60, 75, 70);

        var topAndBottom = panelManager.getRoot()
            .subdivide(1, AdjustablePanelManager.PanelDivider.Orientation.HORIZONTAL, layoutRatios.get(0));
        var leftAndRight = topAndBottom.get(0)
            .subdivide(1, AdjustablePanelManager.PanelDivider.Orientation.VERTICAL, layoutRatios.get(1));
        var notifyAndDetails = topAndBottom.get(1)
            .subdivide(1, AdjustablePanelManager.PanelDivider.Orientation.VERTICAL, layoutRatios.get(2));

        leftAndRight.get(0).resizeCallback(modelPreviewer::relocate);
        leftAndRight.get(0).resizeFinishCallback(_ -> modelPreviewer.onResizeCompleted());
        leftAndRight.get(1).resizeCallback(rect -> {
            propertiesHeader.relocate(rect);
            propertiesTable.relocate(rect);
        });
        notifyAndDetails.get(0).area(notifyTrackEditorPanel);
        notifyAndDetails.get(1).area(notifyDetailsPanel);

        // Unsaved-changes marker: the title shows a `*` whenever the live history top diverges from the
        // last save point. Snapshot the point after all seeding above so a freshly opened editor is clean.
        topMenuBar.setDirtyChecker(this::hasUnsavedChanges);
        savePoint = HistoryManager.getInstance().currentSavePoint();
    }

    @Override
    protected void init() {
        super.init();

        layoutWidgets();

        addRenderableWidget(topMenuBar);
        addRenderableWidget(panelManager);
        addRenderableWidget(modelPreviewer);
        addRenderableWidget(propertiesHeader);
        addRenderableWidget(propertiesTable);
    }

    private void openPreviewModelSelectModal() {
        ModelSelectModal modal = new ModelSelectModal(
            font,
            holder.getPreviewModel(),
            newModel -> {
                holder.setPreviewModel(newModel);
                EntityPreviewModel.display(modelPreviewer, newModel, true, true);
                refreshPreviewAnimation();
            }
        );

        AkytheraEditorLayoutManager.getInstance().openModalWidgetAtCenter(modal);
    }

    /// (Re)plays the holder's clip on the previewer's current entity component and rewires the
    /// playback-end stop for non-looping sequences. Safe to call after any `displayEntity` — the
    /// freshly created component is fetched from the previewer.
    private void refreshPreviewAnimation() {
        ModelPreviewer.EntityModelComponent entityModelComponent = modelPreviewer.getComponent(ModelPreviewer.EntityModelComponent.class);
        if (entityModelComponent == null) return;

        entityModelComponent.setSimplePlayingAnimation(holder.getAnimClip(), false);

        if (entityModelComponent.getAnimator() instanceof ModelPreviewer.EntityModelComponent.SimpleHeadlessAnimator simpleHeadlessAnimator) {
            simpleHeadlessAnimator.setOnPlaybackEnds(() -> {
                if (!holder.loops()) {
                    notifyTrackEditorPanel.setPlaying(false);
                    notifyTrackEditorPanel.setCurrentFrame(holder.getTotalFrameTime() * 60.0F);
                }
            });
        }
    }

    private void layoutWidgets() {
        var rect = getRectangle();
        topMenuBar.relocate(rect);
        panelManager.relocate(rect);
    }

    /// Adds or removes the root-motion trajectory preview based on the holder's current flag.
    /// Called on construction and whenever the Root Motion checkbox flips.
    private void refreshRootTrajectory() {
        modelPreviewer.removeAllComponents(ModelPreviewer.RootMotionTrajectoryComponent.class);

        if (!holder.isRootMotion()) return;

        modelPreviewer.addComponent(new ModelPreviewer.RootMotionTrajectoryComponent(
            () -> samplePath(holder.getAnimClip().get()),
            () -> {
                AnimationClip clip = holder.getAnimClip().get();
                if (clip == null) return Vec3.ZERO;

                ModelPreviewer.EntityModelComponent c = modelPreviewer.getComponent(ModelPreviewer.EntityModelComponent.class);
                float elapsed = (c != null && c.getAnimator() instanceof ModelPreviewer.EntityModelComponent.EditorScrubAnimator s)
                    ? s.getElapsedTime() : 0.0F;
                return rootLoc(clip, elapsed);
            }
        ));
    }

    /// Samples the clip's root path at a fixed interval for the trajectory polyline (a single clip
    /// has no sections, so the path is just the raw root over time).
    private static List<Vec3> samplePath(@Nullable AnimationClip clip) {
        if (clip == null) return List.of();

        float total = clip.getTotalTime();
        List<Vec3> pts = new ArrayList<>();
        for (float t = 0.0F; t < total; t += TRAJECTORY_SAMPLE_STEP) pts.add(rootLoc(clip, t));
        pts.add(rootLoc(clip, total)); // exact end point

        return pts;
    }

    /// Raw root-bone location at `t` (clip pose keeps the root, unlike `AnimationSequence.getPose`).
    private static Vec3 rootLoc(AnimationClip clip, float t) {
        return clip.getPose(Math.clamp(t, 0.0F, clip.getTotalTime()))
            .orElseZero(ArmatureLoader.ROOT_BONE).loc();
    }

    /// Writes the entry asynchronously behind an await modal. The returned future completes on the
    /// render thread once the file is written, the modal is closed, and the save point is re-snapshotted
    /// (clearing the title's `*`).
    private CompletableFuture<Void> save() {
        WidgetContainerModal awaitModal = AwaitIconModal.openSaving(font);
        return AkytheraProject.getCurrentProject().saveEntryAsync(resourceKey)
            .whenCompleteAsync((result, error) -> onSaveComplete(awaitModal, error), minecraft);
    }

    /// Render-thread completion for [#save]: closes the await modal (only if it is still the top one)
    /// and, on success, re-snapshots the save point so the title's `*` clears.
    private void onSaveComplete(WidgetContainerModal awaitModal, @Nullable Throwable error) {
        if (AkytheraEditorLayoutManager.getInstance().getModal() == awaitModal) {
            AkytheraEditorLayoutManager.getInstance().closeModal();
        }

        if (error != null) {
            AkytheraLabs.LOGGER.error("Failed to save animation sequence '{}'", resourceKey.identifier(), error);
            return;
        }

        savePoint = HistoryManager.getInstance().currentSavePoint();
    }

    /// True when the live history top has diverged from the last save point — i.e. there are edits
    /// to persist. Drives both the title's `*` marker and the quit-prompt skip.
    private boolean hasUnsavedChanges() {
        return HistoryManager.getInstance().currentSavePoint() != savePoint;
    }

    @Override
    public void handleQuitRequest() {
        SafeCloseScreen.openQuiteScreen(
            font,
            Component.translatable(LangKeys.GUI_MESSAGE_ASK_TO_SAVE, topMenuBar.getMessage()),
            hasUnsavedChanges(),
            () -> {
                // Dismiss the prompt, then save asynchronously and close only once the write finishes.
                AkytheraEditorLayoutManager.getInstance().closeModal();
                save().thenRun(this::onClose);
            },
            () -> {
                // Quit without saving: roll the holder back to its on-disk state so abandoned edits
                // don't survive in memory into the next open.
                if (hasUnsavedChanges()) AkytheraProject.getCurrentProject().reloadEntryFromDisk(resourceKey);
                onClose();
            }
        );
    }

    @Override
    public void onClose() {
        modelPreviewer.destroy();
        AkytheraProject.getCurrentProject().saveLayoutOfScreen(this, panelManager.serializePanelDividerPositions());
        AkytheraEditorLayoutManager.getInstance().removeLastScreen();
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        // Draw nothing
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        AkytheraEditorLayoutManager.blurFocusChainLeaf(this);
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape() && shouldCloseOnEsc()) {
            handleQuitRequest();
            return true;
        }

        if (EditorKeyMappings.SAVE.matches(event)) {
            save();
            return true;
        }

        if (EditorKeyMappings.REDO.matches(event)) {
            HistoryManager.getInstance().redo();
            return true;
        }

        if (EditorKeyMappings.UNDO.matches(event)) {
            HistoryManager.getInstance().undo();
            return true;
        }

        return super.keyPressed(event);
    }

    @Override
    public @NonNull Optional<GuiEventListener> getChildAt(double x, double y) {
        AbstractWidget modal = getHoveringModal(x, y);
        if (modal != null) return Optional.of(modal);

        return super.getChildAt(x, y);
    }

    @Override
    public void setFocused(@Nullable GuiEventListener g) {
        if (isModal(g)) {
            if (g != null) g.setFocused(true);
        } else {
            super.setFocused(g);
        }
    }

    /// Instead of assigning `modal` directly into [#getFocused()], check and return opened modal via
    /// [AkytheraEditorLayoutManager#getModal()] to release focus automatically when closed.
    @Nullable
    public GuiEventListener getFocused() {
        AbstractWidget modal = AkytheraEditorLayoutManager.getInstance().getModal();
        if (modal != null) return modal;

        return super.getFocused();
    }
}