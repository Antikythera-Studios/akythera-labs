package com.yesman.akytheralabs.gui.screen;

import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.asset.SingletonAccessor;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.client.api.gui.WidgetTable;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalAccessibleScreen;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.client.api.gui.viewer.*;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.animation.driver.AnimationDriver;
import com.yesman.akythera.core.animation.driver.AnimationMontagePlayer;
import com.yesman.akythera.core.animation.keyframe.AnimationClip;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.animation.playable.AnimationMontage;
import com.yesman.akythera.core.animation.playable.SectionInfo;
import com.yesman.akythera.core.asset.loader.ArmatureLoader;
import com.yesman.akythera.core.math.TimeWindow;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.mixinaccessor.common.EntityAccessor;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.akythera.animation.AnimationMontageHolder;
import com.yesman.akytheralabs.akythera.animation.NotifyTrackHolder;
import com.yesman.akytheralabs.dataholder.EntityPreviewModelMapping;
import com.yesman.akytheralabs.editor.EditorWidgetFactory;
import com.yesman.akytheralabs.editor.debug.AnimatorProbe;
import com.yesman.akytheralabs.editor.debug.EntityDebugSession;
import com.yesman.akytheralabs.editor.historytrack.DataChange;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.WorkHistory;
import com.yesman.akytheralabs.editor.keybind.EditorKeyMappings;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.AdjustablePanelManager;
import com.yesman.akytheralabs.gui.panel.AssetBrowserPanel;
import com.yesman.akytheralabs.gui.panel.animation.MontageSectionsPage;
import com.yesman.akytheralabs.gui.panel.animation.NotifyDetailsPanel;
import com.yesman.akytheralabs.gui.widget.AwaitIconModal;
import com.yesman.akytheralabs.gui.widget.PanelHeader;
import com.yesman.akytheralabs.gui.widget.TabPanel;
import com.yesman.akytheralabs.gui.widget.WidgetContainerModal;
import com.yesman.akytheralabs.gui.widget.animation.AnimationTrackEditorPanel;
import com.yesman.akytheralabs.gui.widget.animation.NotifyRow;
import com.yesman.akytheralabs.gui.widget.animation.SectionRow;
import com.yesman.akytheralabs.gui.widget.animation.SegmentRow;
import com.yesman.akytheralabs.gui.widget.animation.TimelineTrackEntry;
import com.yesman.akytheralabs.gui.widget.main.ConsoleModal;
import com.yesman.akytheralabs.gui.widget.main.EditorPreferencesModalGenerator;
import com.yesman.akytheralabs.gui.widget.main.EntityTypeSelectModal;
import com.yesman.akytheralabs.gui.widget.main.LocalizationManagerModal;
import com.yesman.akytheralabs.gui.widget.main.TopMenuBar;
import com.yesman.akytheralabs.gui.widget.main.TopMenuOption;
import com.yesman.akytheralabs.gui.widget.viewer.RootMotionTrajectoryComponent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class AnimationMontageEditorScreen extends Screen implements ModalAccessibleScreen, SafeCloseScreen, DebuggableScreen {
    private static final int TOP_BAR_H = 22;
    private static final int ROW_H = 20;

    /// Trajectory sampling interval (sec) — mirrors the root-motion fixed tick (0.05).
    private static final float TRAJECTORY_SAMPLE_STEP = 0.05F;

    private static final int TAB_MONTAGE_SECTIONS = 0;
    private static final int TAB_ASSET_BROWSER = 1;
    private static final int TAB_NOTIFY_DETAILS = 2;

    private final AdjustablePanelManager panelManager;
    private final TopMenuBar topMenuBar;
    private final Model3DViewer model3DViewer;
    private final WidgetTable propertiesTable;
    private final PanelHeader propertiesHeader;
    private final AnimationTrackEditorPanel animationTrackEditorPanel;
    private final TabPanel tabPanel;
    private final NotifyDetailsPanel notifyDetailsPanel;
    private final MontageSectionsPage montageSectionsPage;
    private final AssetBrowserPanel<AssetAccessor<AnimationClip>> assetBrowserPage;

    /// Shared interaction state for the two segment lanes (selection + reorder drag).
    private final SegmentRow.Interaction segmentInteraction = new SegmentRow.Interaction();

    /// Asset-browser → Segments cross-panel drag payload; null when not dragging.
    @Nullable
    private AssetAccessor<AnimationClip> draggingClip;

    private final AnimationMontageHolder holder;
    private final ResourceKey<@NonNull AnimationMontage> resourceKey;

    /// History stack top at the last save (or screen open). The title shows a `*` while the live top
    /// differs from this; a completed save re-snapshots it. See [HistoryManager#currentSavePoint].
    private @Nullable WorkHistory savePoint;

    /// Cached root-motion trajectory polyline + the signature it was built for (merged-clip identity +
    /// sections + playing orders). Rebuilt lazily by the trajectory component's path supplier.
    @Nullable
    private List<Vec3> cachedTrajectoryPath;
    private int cachedTrajectorySig;

    public AnimationMontageEditorScreen(ResourceKey<@NonNull AnimationMontage> resourceKey, AnimationMontageHolder holder) {
        super(Component.translatable(LangKeys.GUI_ANIM_MONTAGE_SCREEN_TITLE));

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
            () -> handleQuitRequest(),
            TopMenuBar.actions()
                .on(TopMenuOption.LOCALIZATION, () -> LocalizationManagerModal.open(minecraft.font))
                .on(TopMenuOption.SAVE, this::save)
                .on(TopMenuOption.EDITOR_PREFERENCES, () -> EditorPreferencesModalGenerator.open(font))
                .on(TopMenuOption.SELECT_PREVIEW_MODEL, this::openPreviewModelSelectModal)
                .on(TopMenuOption.DEBUG, () -> DebuggableScreen.openDebugSelector(font))
                .on(TopMenuOption.CONSOLE, () -> ConsoleModal.open(font))
                .on(TopMenuOption.DOCS, () -> {})
        );

        model3DViewer = new Model3DViewer(
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM,
            AkytheraEditorLayoutManager.getInstance()::getLayoutGuiScale
        );

        model3DViewer.addComponent(new CameraComponent(true));
        model3DViewer.addComponent(new BackgroundComponent(16, 16, 16, 255));
        model3DViewer.addComponent(new GridFloorComponent(2.0F, 50, 68, 68, 68, 255));
        EntityPreviewModelMapping.displayOrWarn(model3DViewer, font, holder.getPreviewEntityType(), true, true);

        EntityModelComponent entityModelComponent = model3DViewer.getComponent(EntityModelComponent.class);

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

        // Right-bottom panel: a TabPanel with the three real pages.
        notifyDetailsPanel = new NotifyDetailsPanel(this);
        montageSectionsPage = new MontageSectionsPage(
            minecraft.font, holder, this::playFromSection,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM
        );

        assetBrowserPage = new AssetBrowserPanel<>(
            minecraft.font,
            () -> AkytheraProject.getCurrentProject().listAllAnimationAccessors()
                .sorted(Comparator.comparing(AssetAccessor::identifier))
                .toList(),
            accessor -> accessor.identifier().toString(),
            this::beginClipDrag,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM
        );

        tabPanel = new TabPanel(
            minecraft.font,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM
        );

        tabPanel
            .addTab(Component.translatable(LangKeys.GUI_ANIM_MONTAGE_SCREEN_TAB_MONTAGE_SECTIONS), montageSectionsPage)
            .addTab(Component.translatable(LangKeys.GUI_ANIM_MONTAGE_SCREEN_TAB_ASSET_BROWSER), assetBrowserPage)
            .addTab(Component.translatable(LangKeys.GUI_ANIM_MONTAGE_SCREEN_TAB_NOTIFY_DETAILS), notifyDetailsPanel);

        AdjustablePanelManager.PanelArea tabHostArea = new AdjustablePanelManager.PanelArea() {
            {
                addWidget(tabPanel);
            }

            @Override
            protected void onPanelResized(ScreenRectangle screenRectangle) {
                tabPanel.relocate(screenRectangle);
            }
        };

        animationTrackEditorPanel = new AnimationTrackEditorPanel(
            holder.getNotifyTrackHolder(),
            model3DViewer,
            holder,
            ref -> {
                notifyDetailsPanel.onSelectionChanged(ref);
                if (ref != null) tabPanel.setActiveIndex(TAB_NOTIFY_DETAILS);
            },
            this::buildAllRows
        );
        animationTrackEditorPanel.ensureAtLeastOneTrack();
        animationTrackEditorPanel.setDebugDrivenChecker(EntityDebugSession.getInstance()::isActive);

        if (entityModelComponent != null) installMontageAnimator(entityModelComponent);

        // Show the root-motion trajectory while the Root Motion flag is on; re-evaluated on flip
        // (incl. undo/redo, which replays the setter).
        holder.setRootMotionChangeListener(_ -> refreshRootTrajectory());
        refreshRootTrajectory();

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
        var notifyAndTabs = topAndBottom.get(1)
            .subdivide(1, AdjustablePanelManager.PanelDivider.Orientation.VERTICAL, layoutRatios.get(2));

        leftAndRight.get(0).resizeCallback(model3DViewer::relocate);
        leftAndRight.get(0).resizeFinishCallback(_ -> model3DViewer.onResizeCompleted());
        leftAndRight.get(1).resizeCallback(rect -> {
            propertiesHeader.relocate(rect);
            propertiesTable.relocate(rect);
        });
        notifyAndTabs.get(0).area(animationTrackEditorPanel);
        notifyAndTabs.get(1).area(tabHostArea);

        // Unsaved-changes marker: the title shows a `*` whenever the live history top diverges from the
        // last save point. Snapshot the point after all seeding above so a freshly opened editor is clean.
        topMenuBar.setDirtyChecker(this::hasUnsavedChanges);
        savePoint = HistoryManager.getInstance().currentSavePoint();
    }

    /// All rows for the track panel: the montage Sections indicator row, the two Segments
    /// lanes (top/bottom), then the notify rows. Clicking a section/segment/notify opens its
    /// matching tab. Rebuilt live by the panel.
    private List<TimelineTrackEntry> buildAllRows(AnimationTrackEditorPanel panel) {
        List<TimelineTrackEntry> rows = new ArrayList<>();
        rows.add(new SectionRow(panel, holder, () -> tabPanel.setActiveIndex(TAB_MONTAGE_SECTIONS), montageSectionsPage::rebuildChain));
        rows.add(new SegmentRow(panel, holder, SegmentRow.Lane.TOP, () -> tabPanel.setActiveIndex(TAB_ASSET_BROWSER), segmentInteraction));
        rows.add(new SegmentRow(panel, holder, SegmentRow.Lane.BOTTOM, () -> tabPanel.setActiveIndex(TAB_ASSET_BROWSER), segmentInteraction));

        for (NotifyTrackHolder.Track t : holder.getNotifyTrackHolder().getTracks()) {
            rows.add(new NotifyRow(panel, t));
        }

        return rows;
    }

    /// Asset Browser starts a drag; the screen tracks it until release (see [#mouseReleased]).
    private void beginClipDrag(AssetAccessor<AnimationClip> clip) {
        draggingClip = clip;
    }

    /// Drop the dragged clip into the Segments track if released over the timeline area.
    /// (Visual drag ghost is a TODO — no foreground render hook on these editor screens yet.)
    private void dropClipIntoSegments(double x, double y) {
        AssetAccessor<AnimationClip> clip = draggingClip;
        if (clip == null) return;

        boolean inTrackArea = x >= animationTrackEditorPanel.getTrackStart() && x < animationTrackEditorPanel.getRight()
            && y >= animationTrackEditorPanel.getY() + AnimationTrackEditorPanel.HEADER_H
            && y < animationTrackEditorPanel.getBottom() - AnimationTrackEditorPanel.FOOTER_H;
        if (!inTrackArea) return;

        int slot = segmentDropIndex(x);
        holder.insertSegment(slot, clip);
        animationTrackEditorPanel.rebuildEntries();

        HistoryManager.getInstance().pushHistory(new WorkHistory() {
            @Override public void undo() { holder.removeSegment(slot); animationTrackEditorPanel.rebuildEntries(); }
            @Override public void redo() { holder.insertSegment(slot, clip); animationTrackEditorPanel.rebuildEntries(); }
        });
    }

    /// Insertion slot (0..n) for a screen-space x: how many segment centers sit left of it.
    private int segmentDropIndex(double x) {
        int slot = 0;
        for (TimeWindow w : holder.getSegmentBounds()) {
            int centerX = animationTrackEditorPanel.frameToX((w.from() + w.to()) / 2.0F * 60.0F);
            if (x > centerX) slot++;
        }
        return slot;
    }

    /// Installs (or re-installs, after a preview-model swap) the section-ordered montage animator
    /// and re-attaches the playback-end hook — a re-install builds a fresh animator, so the hook
    /// must be set each time. Suppliers are read live, so segment/section edits need no re-install.
    private void installMontageAnimator(EntityModelComponent entityModelComponent) {
        MontageAnimator animator = new MontageAnimator(this::previewAccessor, this::buildSectionInfo, holder::isRootMotion);
        animator.setUpdatePose(false);
        entityModelComponent.setAnimator(animator);

        animator.setOnPlaybackEnds(animationTrackEditorPanel::onPlaybackEnds);
    }

    /// A fresh accessor wrapping the holder's *current* merged clip. Called live by the
    /// [MontageAnimator] each frame, so segment edits
    /// (which re-bake [AnimationMontageHolder#getMergedClip]) show up without re-install.
    /// Empty montage → empty clip → rest pose.
    private AssetAccessor<AnimationClip> previewAccessor() {
        return new SingletonAccessor<>(resourceKey.identifier(), holder.getMergedClip(), AssetAccessor.Type.ANIMATION);
    }

    /// Section playback order for the montage, rebuilt from the holder each call. Wrapped so a
    /// malformed order (duplicate transition / reserved name) falls back to linear play instead
    /// of throwing every frame inside the animator. See [SectionInfo].
    private @Nullable SectionInfo buildSectionInfo() {
        try {
            return new SectionInfo(holder.getSections(), holder.getPlayingOrders());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return null;
        }
    }

    /// Start preview playback at a section's start time. The montage animator follows that
    /// section's transition chain (built live from every sequence) and pauses at the chain end.
    /// No-op if the name isn't a real section (half-authored chain).
    private void playFromSection(String sectionName) {
        Float start = holder.getSections().get(sectionName);
        if (start == null) return;

        animationTrackEditorPanel.setCurrentFrame(start * 60.0F);
        animationTrackEditorPanel.setPlaying(true);
    }

    @Override
    protected void init() {
        super.init();

        layoutWidgets();

        addRenderableWidget(topMenuBar);
        addRenderableWidget(panelManager);
        addRenderableWidget(model3DViewer);
        addRenderableWidget(propertiesHeader);
        addRenderableWidget(propertiesTable);
    }

    private void openPreviewModelSelectModal() {
        EntityTypeSelectModal modal = new EntityTypeSelectModal(
            font,
            holder.getPreviewEntityType(),
            newEntityType -> {
                Identifier oldEntityType = holder.getPreviewEntityType();
                if (Objects.equals(oldEntityType, newEntityType)) return;

                applyPreviewEntityType(newEntityType);
                HistoryManager.getInstance().pushHistory(
                    new DataChange<>(oldEntityType, newEntityType, this::applyPreviewEntityType));
            }
        );

        ModalManager.getInstance().openModalWidgetAtCenter(modal);
    }

    /// Installs `model` as the previewed entity: the holder field, the viewer, and the montage
    /// animator the new entity needs rebuilding on. Undo and redo replay it.
    private void applyPreviewEntityType(@Nullable Identifier entityType) {
        holder.setPreviewEntityType(entityType);
        EntityPreviewModelMapping.displayOrWarn(model3DViewer, font, entityType, true, true);

        EntityModelComponent entityModelComponent = model3DViewer.getComponent(EntityModelComponent.class);
        if (entityModelComponent != null) installMontageAnimator(entityModelComponent);
    }

    private void layoutWidgets() {
        var rect = getRectangle();
        topMenuBar.relocate(rect);
        panelManager.relocate(rect);
    }

    private void refreshRootTrajectory() {
        model3DViewer.removeAllComponents(RootMotionTrajectoryComponent.class);

        if (!holder.isRootMotion()) return;

        model3DViewer.addComponent(new RootMotionTrajectoryComponent(
            () -> {
                AnimationClip merged = holder.getMergedClip();
                int sig = System.identityHashCode(merged);
                sig = sig * 31 + holder.getSections().hashCode();
                sig = sig * 31 + holder.getPlayingOrders().hashCode();

                if (cachedTrajectoryPath == null || sig != cachedTrajectorySig) {
                    cachedTrajectoryPath = simulateMontagePath();
                    cachedTrajectorySig = sig;
                }

                return cachedTrajectoryPath;
            },
            // Gizmo: the live simulated root position the model is being driven to.
            () -> {
                EntityModelComponent c = model3DViewer.getComponent(EntityModelComponent.class);
                return (c != null && c.getAnimator() instanceof MontageAnimator m) ? m.getSimPos() : Vec3.ZERO;
            }
        ));
    }

    /// Builds the trajectory polyline by simulating section-ordered playback in fixed steps and
    /// integrating the root delta at each step (matching how `RootMotionComponent` moves the entity
    /// at runtime). One pass; self-looping chains are bounded by a step cap.
    private List<Vec3> simulateMontagePath() {
        AnimationClip clip = holder.getMergedClip();
        float total = clip.getTotalTime();

        List<Vec3> pts = new ArrayList<>();
        if (total <= 0.0F) return pts;

        SectionInfo info = buildSectionInfo();
        float elapsed = 0.0F;
        float windowStart = sectionStart(info, 0.0F);
        Vec3 accumBase = rootLoc(clip, windowStart);
        pts.add(accumBase.add(rootLoc(clip, elapsed).subtract(rootLoc(clip, windowStart))));

        // Mirrors MontageAnimator's visited-section pause so the drawn path ends where playback holds.
        Set<String> visited = new HashSet<>();

        if (info != null) {
            String start = info.getSectionAt(0.0F);
            if (start != null) visited.add(start);
        }

        int maxSteps = (int) (total / TRAJECTORY_SAMPLE_STEP) * 4 + 100;

        for (int i = 0; i < maxSteps; i++) {
            MontageAnimator.StepResult result = MontageAnimator.step(info, elapsed, TRAJECTORY_SAMPLE_STEP, total, visited);

            // Loop back: the path ends at the loop boundary (one pass) — add the exit point and stop.
            if (result.loopedBack()) {
                pts.add(accumBase.add(rootLoc(clip, result.left().to()).subtract(rootLoc(clip, windowStart))));
                break;
            }

            if (result.transitioned()) {
                accumBase = accumBase.add(rootLoc(clip, result.left().to()).subtract(rootLoc(clip, windowStart)));
                windowStart = result.entered().from();
            }

            elapsed = result.newElapsed();
            pts.add(accumBase.add(rootLoc(clip, elapsed).subtract(rootLoc(clip, windowStart))));

            if (result.ended()) break;
        }

        return pts;
    }

    /// Start time of the section window containing `t` (0 when there are no sections).
    private static float sectionStart(@Nullable SectionInfo info, float t) {
        if (info == null) return 0.0F;
        String name = info.getSectionAt(t);
        return name == null ? 0.0F : info.getSectionTimeWindow(name).from();
    }

    /// Raw root-bone location at `t` (clip pose keeps the root, unlike `AnimationMontage.getPose`).
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
        if (ModalManager.getInstance().getModal() == awaitModal) {
            ModalManager.getInstance().closeModal();
        }

        if (error != null) {
            AkytheraLabs.LOGGER.error("Failed to save animation montage '{}'", resourceKey.identifier(), error);
            return;
        }

        savePoint = HistoryManager.getInstance().currentSavePoint();
    }

    // ***************************************************************
    // [DebuggableScreen]
    // ***************************************************************

    @Override
    public Predicate<Entity> debugTargetFilter() {
        return entity -> ((EntityAccessor)entity).akythera$getEntityPatch() != null;
    }

    /// Snaps the track playhead onto the possessed entity's playback, parking it at 0 once the entity
    /// moves on to another animation. Leaves it alone only while the edited entry cannot be resolved
    /// at all — being unable to tell is not the same as knowing the entity moved on.
    @Override
    public void onDebugTick(final EntityPatch<?> entityPatch) {
        AnimationMontage edited = AnimatorProbe.resolve(entityPatch, resourceKey);

        if (edited == null) {
            return;
        }

        AnimationDriver driver = AnimatorProbe.findDriverPlaying(entityPatch, edited);

        animationTrackEditorPanel.setDebugCurrentFrame(driver == null ? 0.0F : driver.getTime() * 60.0F);
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
                ModalManager.getInstance().closeModal();
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
        model3DViewer.destroy();
        AkytheraProject.getCurrentProject().saveLayoutOfScreen(this, panelManager.serializePanelDividerPositions());
        AkytheraEditorLayoutManager.getInstance().removeLastScreen();
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        // Draw nothing
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        AkytheraEditorLayoutManager.blurFocusChainLeaf(this, event.x(), event.y());
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dragX, double dragY) {
        // While dragging a clip out of the Asset Browser, swallow drags so lists don't scroll.
        if (draggingClip != null) return true;
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        if (draggingClip != null) {
            dropClipIntoSegments(event.x(), event.y());
            draggingClip = null;
            return true;
        }
        return super.mouseReleased(event);
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

    /// The modal owning focus, through [ModalAccessibleScreen#getFocusOwningModal], so focus is
    /// released when it closes.
    @Nullable
    public GuiEventListener getFocused() {
        AbstractWidget modal = getFocusOwningModal();
        if (modal != null) return modal;

        return super.getFocused();
    }

    /// A headless animator that plays an [AnimationMontage]'s merged clip following its **section
    /// order** instead of straight start→end. (Lives here because only this screen uses it.)
    ///
    /// The merged clip is one linear timeline; sections are named timestamps on it. So pose
    /// sampling is still `mergedClip.getPose(elapsedTime)` — only the way `elapsedTime` advances
    /// changes: it jumps between section windows per [SectionInfo], mirroring
    /// [AnimationMontagePlayer#update] minus the fade logic.
    ///
    /// Fed by suppliers so it always reads the freshest baked clip + section order (the editor
    /// re-bakes the merged clip as segments/sections change). Manual scrub via [#setElapsedTime]
    /// is linear over the merged timeline. [#getOwner] / [#getMontagePlayer] are unsupported.
    private static final class MontageAnimator implements EntityModelComponent.EditorScrubAnimator {
        private final Supplier<@Nullable AssetAccessor<AnimationClip>> clipSupplier;
        private final Supplier<@Nullable SectionInfo> sectionSupplier;
        private final BooleanSupplier rootMotionEnabled;
        private float elapsedTime = 0.0F;
        private boolean updatePose = true;
        @Nullable
        private Runnable onPlaybackEnds;

        // Montage root motion simulation based on section playback order
        private boolean simInit = false;
        private Vec3 accumBase = Vec3.ZERO;
        private float windowStartTime = 0.0F;

        /// Sections already played this pass. Once the chain re-enters one of them, the root-motion
        /// tracker freezes (one pass) while pose playback keeps looping. Reset on each (re)seat.
        private final Set<String> visitedSections = new HashSet<>();

        /// Once the section chain loops back, the tracker holds here instead of advancing forever.
        private boolean simFrozen = false;
        private Vec3 frozenSimPos = Vec3.ZERO;

        MontageAnimator(
            final Supplier<@Nullable AssetAccessor<AnimationClip>> clipSupplier,
            final Supplier<@Nullable SectionInfo> sectionSupplier,
            final BooleanSupplier rootMotionEnabled
        ) {
            this.clipSupplier = clipSupplier;
            this.sectionSupplier = sectionSupplier;
            this.rootMotionEnabled = rootMotionEnabled;
        }

        private @Nullable AnimationClip clip() {
            AssetAccessor<AnimationClip> accessor = clipSupplier.get();
            return accessor == null ? null : accessor.getOrHandleException(Akythera.LOGGER::error);
        }

        @Override
        public @Nullable AssetAccessor<AnimationClip> getPreviewAnimation() {
            return clipSupplier.get();
        }

        @Override
        public float getElapsedTime() {
            return elapsedTime;
        }

        @Override
        public void setUpdatePose(final boolean updatePose) {
            this.updatePose = updatePose;
        }

        @Override
        public void setOnPlaybackEnds(final Runnable onPlaybackEnds) {
            this.onPlaybackEnds = onPlaybackEnds;
        }

        @Override
        public void setElapsedTime(final float seconds) {
            // Manual scrub inspects the flat merged timeline, so just clamp to its length.
            AnimationClip clip = clip();
            float total = clip == null ? Float.MAX_VALUE : clip.getTotalTime();
            elapsedTime = Math.clamp(seconds, 0.0F, total);
            seatSimLinear(); // scrubbing is linear over the merged clip
        }

        @Override
        public Pose getPose() {
            AnimationClip clip = clip();
            if (clip == null) return Pose.EMPTY_POSE;

            Pose pose = clip.getPose(elapsedTime);

            // Drive the model along the simulated (section-ordered) path instead of the raw merged root
            if (rootMotionEnabled.getAsBoolean() && pose.hasTransform(ArmatureLoader.ROOT_BONE)) {
                Transform root = pose.get(ArmatureLoader.ROOT_BONE);
                pose.putBoneTransform(ArmatureLoader.ROOT_BONE, new Transform(getSimPos(), root.rot(), root.sca()));
            }

            return pose;
        }

        /// Section-ordered advance + live root-motion integration. The advance is factored into
        /// [#step] (mirrors [AnimationMontagePlayer#update] minus fades); the integration keeps `simPos`
        /// continuous across section jumps (the boundary split).
        @Override
        public void update(final float timeFrame, final float partialTick) {
            if (!updatePose) return;

            AnimationClip clip = clip();
            if (clip == null) return;

            float total = clip.getTotalTime();
            if (total <= 0.0F) return;

            if (!simInit) seatSimLinear();

            StepResult r = step(sectionSupplier.get(), elapsedTime, timeFrame, total, visitedSections);

            if (r.loopedBack()) {
                if (!simFrozen) {
                    frozenSimPos = accumBase.add(rootLoc(clip, r.left().to()).subtract(rootLoc(clip, windowStartTime)));
                    simFrozen = true;
                }
            } else if (r.transitioned()) {
                // Close out the window we're leaving: accumBase becomes simPos at the exit point.
                accumBase = accumBase.add(rootLoc(clip, r.left().to()).subtract(rootLoc(clip, windowStartTime)));
                windowStartTime = r.entered().from();
            }

            elapsedTime = r.newElapsed();

            if (r.ended()) fireEnd();
        }

        /// (Re)seat the windowed offset so `simPos == root(elapsed)` (linear). Used on first update and
        /// on manual scrub — the editor timeline is linear over the merged clip.
        private void seatSimLinear() {
            AnimationClip clip = clip();
            windowStartTime = 0.0F;
            accumBase = clip == null ? Vec3.ZERO : rootLoc(clip, 0.0F);
            simInit = true;

            // Restart the played-section set + unfreeze the tracker, seeding the section the playhead
            // currently sits in.
            simFrozen = false;
            visitedSections.clear();
            SectionInfo info = sectionSupplier.get();

            if (info != null) {
                String start = info.getSectionAt(elapsedTime);
                if (start != null) visitedSections.add(start);
            }
        }

        /// Live simulated root position the model is driven to (and what the trajectory gizmo marks).
        /// Frozen at the loop boundary once the section chain repeats, so it never runs off forever.
        Vec3 getSimPos() {
            if (simFrozen) return frozenSimPos;

            AnimationClip clip = clip();
            if (clip == null) return Vec3.ZERO;
            if (!simInit) seatSimLinear();
            return accumBase.add(rootLoc(clip, elapsedTime).subtract(rootLoc(clip, windowStartTime)));
        }

        private void fireEnd() {
            if (onPlaybackEnds != null) onPlaybackEnds.run();
        }

        /// One section-ordered advance from `elapsed` by `dt` on a clip of length `total`. Shared by the
        /// live animator and the screen's static path simulation.is what keeps a looping section playing while
        /// the trajectory stays bounded to one pass.
        static StepResult step(@Nullable SectionInfo info, float elapsed, float dt, float total, Set<String> visited) {
            if (info == null) {
                float next = elapsed + dt;
                if (next > total) return new StepResult(next % total, false, null, null, true, false);
                return new StepResult(next, false, null, null, false, false);
            }

            String currentName = info.getSectionAt(elapsed);
            TimeWindow current = currentName == null ? null : info.getSectionTimeWindow(currentName);
            float next = elapsed + dt;
            boolean leftWindow = current != null && !current.isIn(next);
            boolean pastTotal = next > total;

            if (leftWindow || pastTotal) {
                String nextName = currentName == null ? null : info.getNextSection(currentName);

                // Terminal chain → stop.
                if (nextName == null) {
                    float end = current != null ? Math.min(current.to(), total) : total;
                    return new StepResult(end, false, null, null, true, false);
                }

                // Transition (loop included). `loopedBack` when re-entering a section already played.
                boolean loopedBack = visited.contains(nextName);
                if (!loopedBack) visited.add(nextName);

                TimeWindow nextWindow = info.getSectionTimeWindow(nextName);
                float overshoot = next - current.to();

                return new StepResult(nextWindow.from() + Math.max(AnimationMontage.SEGMENT_GAP, overshoot), true, current, nextWindow, false, loopedBack);
            }

            return new StepResult(next, false, null, null, false, false);
        }

        /// Result of one [#step]: the new time, whether a section jump happened (with the windows
        /// involved), whether playback ended (terminal), and whether it looped back into a played section.
        record StepResult(float newElapsed, boolean transitioned, @Nullable TimeWindow left, @Nullable TimeWindow entered, boolean ended, boolean loopedBack) {}

        @Override
        public void tick() {
        }

        @Override @Deprecated
        public void setSoftPause(final boolean paused) {
        }

        @Override @Deprecated
        public void setHardPause(final boolean paused) {
        }

        @Override @Deprecated
        public boolean isSoftPaused() {
            return false;
        }

        @Override @Deprecated
        public boolean isHardPaused() {
            return false;
        }

        @Override
        public <O> O getOwner() {
            throw new UnsupportedOperationException("MontageAnimator has no owner");
        }

        @Override
        public AnimationMontagePlayer getMontagePlayer(final ResourceKey<@NonNull AnimationMontage> animMontageKey) {
            throw new UnsupportedOperationException("MontageAnimator has no montage player");
        }
    }
}