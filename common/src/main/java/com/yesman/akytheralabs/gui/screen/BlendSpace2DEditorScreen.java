package com.yesman.akytheralabs.gui.screen;

import com.mojang.datafixers.util.Pair;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.client.gui.widget.ClampedNumberBox;
import com.yesman.akythera.client.gui.widget.ModelPreviewer;
import com.yesman.akythera.client.gui.widget.Static;
import com.yesman.akythera.client.gui.widget.WidgetTable;
import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akythera.core.animation.driver.AnimationMontagePlayer;
import com.yesman.akythera.core.animation.keyframe.AnimationClip;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.animation.playable.AnimationMontage;
import com.yesman.akythera.core.animation.playable.AnimationSequence;
import com.yesman.akythera.core.animation.playable.BlendSpace2D;
import com.yesman.akythera.core.math.algorithm.DelaunayTriangulation;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.dataholder.EntityPreviewModel;
import com.yesman.akytheralabs.dataholder.animation.AnimationRef;
import com.yesman.akytheralabs.dataholder.animation.BlendSpace2DHolder;
import com.yesman.akytheralabs.editor.EditorWidgetFactory;
import com.yesman.akytheralabs.editor.historytrack.DataChange;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.PreviewModelChange;
import com.yesman.akytheralabs.editor.historytrack.WorkHistory;
import com.yesman.akytheralabs.editor.keybind.EditorKeyMappings;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.AdjustablePanelManager;
import com.yesman.akytheralabs.gui.panel.AssetBrowserPanel;
import com.yesman.akytheralabs.gui.panel.blendspace2d.BlendSpaceDetailsPanel;
import com.yesman.akytheralabs.gui.widget.AdvancedEditBox;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsEditBoxTheme;
import com.yesman.akytheralabs.gui.widget.AwaitIconModal;
import com.yesman.akytheralabs.gui.widget.PanelHeader;
import com.yesman.akytheralabs.gui.widget.TabPanel;
import com.yesman.akytheralabs.gui.widget.WidgetContainerModal;
import com.yesman.akytheralabs.gui.widget.blendspace2d.BlendSpaceNodeGraphWidget;
import com.yesman.akytheralabs.gui.widget.main.EditorPreferencesModalGenerator;
import com.yesman.akytheralabs.gui.widget.main.ModelSelectModal;
import com.yesman.akytheralabs.gui.widget.main.TopMenuBar;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/// Editor screen for the BlendSpace2D asset.
public final class BlendSpace2DEditorScreen extends Screen implements ModalAccessibleScreen, SafeCloseScreen {
    // Widget layout parameters
    private static final int TOP_BAR_H = 22;

    // Details table layout
    private static final int LABEL_W = 80;
    private static final int INDENT = 8;
    private static final int ROW_H = 20;

    /// Trajectory sampling interval (sec) — mirrors the root-motion fixed tick (0.05).
    private static final float TRAJECTORY_SAMPLE_STEP = 0.05F;

    private final AdjustablePanelManager panelManager;

    private final TopMenuBar topMenuBar;
    private final BlendSpaceNodeGraphWidget graphWidget;
    private final ModelPreviewer modelPreviewer;
    private final WidgetTable detailsTable;
    private final WidgetTable nodeEditorTable;
    private final TabPanel tabPanel;
    private final AssetBrowserPanel<Holder<AnimationSequence>> assetBrowserPage;
    private final BlendSpace2DHolder blendSpaceHolder;
    private final ResourceKey<@NonNull BlendSpace2D> resourceKey;

    /// History stack top at the last save (or screen open). The title shows a `*` while the live top
    /// differs from this; a completed save re-snapshots it. See [HistoryManager#currentSavePoint].
    private @Nullable WorkHistory savePoint;

    private BlendSpace2DHolder.@Nullable MutableAnimNode selectedNode;

    /// Asset Browser → graph drag payload; null when not dragging a sequence onto a node.
    @Nullable private Holder<AnimationSequence> draggingSequence;

    /// Live control point in blend-space units, driven by the graph's preview cursor.
    private float previewX;
    private float previewY;

    /// Cached engine BlendSpace2D + the holder version it was built at. Rebuilt lazily when stale.
    @Nullable private BlendSpace2D previewBlendSpace;
    private long previewBlendSpaceVersion = -1;

    public BlendSpace2DEditorScreen(ResourceKey<@NonNull BlendSpace2D> resourceKey, BlendSpace2DHolder blendSpaceHolder) {
        super(Component.translatable(LangKeys.GUI_BLEND_SPACE_2D_SCREEN_TITLE));

        this.resourceKey = resourceKey;
        this.blendSpaceHolder = blendSpaceHolder;

        // Start the control point at the blend-space center, matching where the graph drops the cursor.
        previewX = (blendSpaceHolder.getMinX() + blendSpaceHolder.getMaxX()) * 0.5F;
        previewY = (blendSpaceHolder.getMinY() + blendSpaceHolder.getMaxY()) * 0.5F;

        // Top widgets
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

        graphWidget = new BlendSpaceNodeGraphWidget(
            minecraft,
            blendSpaceHolder,
            this::onNodeSelected,
            (blendX, blendY) -> {
                previewX = blendX;
                previewY = blendY;
            },
            // Ghost-node drop preview is shown whenever a sequence is being dragged from the Asset Browser.
            () -> draggingSequence != null,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM
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
        EntityPreviewModel.display(modelPreviewer, blendSpaceHolder.getPreviewModel(), true, true);

        installBlendAnimator();

        // Show the root-motion trajectory
        blendSpaceHolder.setOnNodeGraphChanged(this::refreshRootTrajectory);

        detailsTable = new WidgetTable(
            this,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(PanelHeader.DEFAULT_HEADER_HEIGHT + 2), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM,
            ROW_H
        );

        new EditorWidgetFactory(minecraft.font).fillWidgetTable(detailsTable, blendSpaceHolder);

        // Tab content (the tab strip is its header now), so fill from near the top.
        nodeEditorTable = new WidgetTable(
            this,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(2), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM,
            ROW_H
        );

        ClampedNumberBox<Float> xBox = makeFloatBox(
            () -> selectedNode != null ? selectedNode.x() : 0f,
            v -> { if (selectedNode != null) selectedNode.setX(v); },
            -Float.MAX_VALUE, Float.MAX_VALUE
        );

        xBox.setOnCommitted((oldVal, newVal) -> pushNodeCoordCommit(
            xBox, selectedNode, oldVal, newVal, BlendSpace2DHolder.MutableAnimNode::setX
        ));

        ClampedNumberBox<Float> yBox = makeFloatBox(
            () -> selectedNode != null ? selectedNode.y() : 0f,
            v -> { if (selectedNode != null) selectedNode.setY(v); },
            -Float.MAX_VALUE, Float.MAX_VALUE
        );

        yBox.setOnCommitted((oldVal, newVal) -> pushNodeCoordCommit(
            yBox, selectedNode, oldVal, newVal, BlendSpace2DHolder.MutableAnimNode::setY
        ));

        // Animation row: read-only — assignment happens by dropping a sequence from the Asset Browser.
        AdvancedEditBox animationBox = new AdvancedEditBox<>(
            font,
            AnchoredWidget.pixel(LABEL_W + 2), AnchoredWidget.pixel(INDENT),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT, AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            () -> selectedNode != null ? selectedNode.animation().displayName() : "",
            _ -> {},
            _ -> true,
            s -> s,
            s -> s,
            Component.empty()
        );
        animationBox.setTheme(AkytheraLabsEditBoxTheme.DEFAULT_EDIT_BOX);
        animationBox.setEditable(false);

        nodeEditorTable
            .newRow()
            .addWidget(makeLabel(Component.literal("X")))
            .addWidget(xBox)
            .newRow()
            .addWidget(makeLabel(Component.literal("Y")))
            .addWidget(yBox)
            .newRow()
            .addWidget(makeLabel(Component.translatable(LangKeys.GUI_BLEND_SPACE_2D_SCREEN_WIDGET_ANIMATION)))
            .addWidget(animationBox);

        nodeEditorTable.initialize(false);

        // Bottom-right tab strip: "Node Details" (the editor table) + "Asset Browser" (draggable sequences).
        assetBrowserPage = new AssetBrowserPanel<>(
            font, BlendSpace2DEditorScreen::buildSequenceAssetItems, this::beginSequenceDrag,
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
            .addTab(Component.translatable(LangKeys.GUI_BLEND_SPACE_2D_SCREEN_WIDGET_NODE_DETAILS), nodeEditorTable)
            .addTab(Component.translatable(LangKeys.GUI_ANIM_MONTAGE_SCREEN_TAB_ASSET_BROWSER), assetBrowserPage);

        AdjustablePanelManager.PanelArea tabHostArea = new AdjustablePanelManager.PanelArea() {
            {
                addWidget(tabPanel);
            }

            @Override
            protected void onPanelResized(ScreenRectangle screenRectangle) {
                tabPanel.relocate(screenRectangle);
            }
        };

        panelManager = new AdjustablePanelManager(
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(TOP_BAR_H), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM
        );

        // Layout: top = [ModelPreviewer | Details]; bottom = [Graph | Node-detail tabs].
        List<Integer> layoutRatios = AkytheraProject.getCurrentProject().getLayoutRatioOrDefault(getClass(), 62, 70, 55);

        var topAndBottom = panelManager.getRoot().subdivide(1, AdjustablePanelManager.PanelDivider.Orientation.HORIZONTAL, layoutRatios.get(0));
        var topSplit = topAndBottom.getFirst().subdivide(1, AdjustablePanelManager.PanelDivider.Orientation.VERTICAL, layoutRatios.get(1));
        var bottomSplit = topAndBottom.get(1).subdivide(1, AdjustablePanelManager.PanelDivider.Orientation.VERTICAL, layoutRatios.get(2));

        topSplit.get(0).resizeCallback(modelPreviewer::relocate);
        topSplit.get(0).resizeFinishCallback(_ -> modelPreviewer.onResizeCompleted());
        topSplit.get(1).area(new BlendSpaceDetailsPanel(minecraft.font, detailsTable));

        bottomSplit.get(0).resizeCallback(graphWidget::relocate);
        bottomSplit.get(1).area(tabHostArea);

        // Unsaved-changes marker: the title shows a `*` whenever the live history top diverges from the
        // last save point. Snapshot the point after all seeding above so a freshly opened editor is clean.
        topMenuBar.setDirtyChecker(this::hasUnsavedChanges);
        savePoint = HistoryManager.getInstance().currentSavePoint();
    }

    /// Asset Browser starts a drag of a sequence; tracked until release (see [#mouseReleased]).
    private void beginSequenceDrag(Holder<AnimationSequence> sequence) {
        draggingSequence = sequence;
    }

    /// Every available animation sequence, sorted by identifier — **both** editor-created (project
    /// `ProjectObjectHolder`s) and built-in (engine/datapack holders) — so a blendspace node can
    /// reference either. The node holds a [Holder] (via [AnimationRef]) so editor renames ripple
    /// and built-ins resolve at runtime.
    private static List<AssetBrowserPanel.Item<Holder<AnimationSequence>>> buildSequenceAssetItems() {
        return AkytheraProject.getCurrentProject()
            .getMergedHolders(AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY).stream()
            .sorted(Comparator.comparing(h -> h.unwrapKey().map(k -> k.identifier().toString()).orElse("")))
            .map(h -> new AssetBrowserPanel.Item<>(h.unwrapKey().map(k -> k.identifier().toString()).orElse("?"), h))
            .toList();
    }

    /// Drop the dragged sequence at the cursor: add a new graph node there carrying that sequence as its
    /// animation (snapped to the grid when snapping is on). No-op if dropped outside the graph area.
    private void dropSequenceAsNode(double x, double y) {
        Holder<AnimationSequence> sequence = draggingSequence;
        if (sequence == null) return;

        graphWidget.dropNodeAt(x, y, new AnimationRef.Sequence(sequence));
    }

    @Override
    protected void init() {
        super.init();

        layoutWidgets();
        addRenderableWidget(topMenuBar);
        addRenderableWidget(panelManager);
        addRenderableWidget(graphWidget);
        addRenderableWidget(modelPreviewer);
    }

    private void openPreviewModelSelectModal() {
        ModelSelectModal modal = new ModelSelectModal(
            font,
            blendSpaceHolder.getPreviewModel(),
            newModel -> {
                EntityPreviewModel oldModel = blendSpaceHolder.getPreviewModel();
                if (Objects.equals(oldModel, newModel)) return;

                blendSpaceHolder.setPreviewModel(newModel);
                EntityPreviewModel.display(modelPreviewer, newModel, true, true);
                installBlendAnimator();

                HistoryManager.getInstance().pushHistory(new PreviewModelChange(
                    blendSpaceHolder, modelPreviewer, oldModel, newModel
                ));
            }
        );

        AkytheraEditorLayoutManager.getInstance().openModalWidgetAtCenter(modal);
    }

    private Static makeLabel(Component text) {
        return new Static(
            font,
            AnchoredWidget.pixel(INDENT), AnchoredWidget.pixel(LABEL_W - INDENT),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH, AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            text
        );
    }

    private ClampedNumberBox<Float> makeFloatBox(
        Callable<Float> getter,
        Consumer<Float> setter,
        float min,
        float max
    ) {
        return new ClampedNumberBox<>(
            font,
            AnchoredWidget.pixel(LABEL_W + 2), AnchoredWidget.pixel(INDENT),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT, AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            getter,
            setter,
            Component.empty(),
            false,
            min, max, 0.1f, -0.1f,
            Float::parseFloat,
            ParseUtils::floatingPointToString,
            Float::compareTo,
            Float::sum
        );
    }

    private void pushNodeCoordCommit(
        ClampedNumberBox<Float> box,
        BlendSpace2DHolder.@Nullable MutableAnimNode capturedNode,
        Float oldVal,
        Float newVal,
        BiConsumer<BlendSpace2DHolder.MutableAnimNode, Float> apply
    ) {
        if (capturedNode == null || Objects.equals(oldVal, newVal)) return;

        HistoryManager.getInstance().pushHistory(new DataChange<>(
            oldVal, newVal,
            v -> {
                apply.accept(capturedNode, v);
                if (selectedNode == capturedNode) {
                    box.setValue(v);
                    setFocused(box);
                }
            }
        ));
    }

    private void onNodeSelected(BlendSpace2DHolder.@Nullable MutableAnimNode node) {
        selectedNode = node;
        graphWidget.selectNode(node);
        nodeEditorTable.initialize(false);
    }

    private void layoutWidgets() {
        var rect = getRectangle();

        topMenuBar.relocate(rect);
        panelManager.relocate(rect);
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
            AkytheraLabs.LOGGER.error("Failed to save blend space '{}'", resourceKey.identifier(), error);
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

        // Prevents infinite loop
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
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dragX, double dragY) {
        // While dragging a sequence out of the Asset Browser, swallow drags so lists don't scroll.
        if (draggingSequence != null) return true;
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        if (draggingSequence != null) {
            dropSequenceAsNode(event.x(), event.y());
            draggingSequence = null;
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

        return super.keyPressed(event);
    }

    @Override
    public @NonNull Optional<GuiEventListener> getChildAt(final double x, final double y) {
        AbstractWidget optModal = getHoveringModal(x, y);

        if (optModal != null) {
            return Optional.of(optModal);
        }

        return super.getChildAt(x, y);
    }

    @Override
    public void setFocused(@Nullable GuiEventListener g) {
        if (isModal(g)) {
            // Apply only the focused effect
            if (g != null) g.setFocused(true);
        } else {
            super.setFocused(g);
        }
    }

    /// Instead of assigning `modal` directly into [Screen#getFocused], check and return opened modal via
    /// [AkytheraEditorLayoutManager#getModal()] to release focus automatically when closed.
    @Nullable
    public GuiEventListener getFocused() {
        AbstractWidget modal = AkytheraEditorLayoutManager.getInstance().getModal();
        if (modal != null) return modal;

        return super.getFocused();
    }

    /// Installs (or re-installs after a model swap) the blendspace animator. Suppliers read live, so
    /// node + cursor edits need no re-install. `updatePose = true`: the animator runs its own clock and
    /// the control point (cursor) chooses the blend each frame.
    private void installBlendAnimator() {
        ModelPreviewer.EntityModelComponent c = modelPreviewer.getComponent(ModelPreviewer.EntityModelComponent.class);

        if (c != null) {
            BlendSpaceAnimator animator = new BlendSpaceAnimator(this::previewBlendSpace, () -> previewX, () -> previewY);
            animator.setUpdatePose(true);
            c.setAnimator(animator);
        }

        // displayEntity() (model swap) clears all components, so re-add the trajectory here too.
        refreshRootTrajectory();
    }

    /// Adds or removes the root-motion trajectory preview based on whether any node animation has root
    /// motion on. Called on construction, model swap, and the holder's node-graph change listener.
    private void refreshRootTrajectory() {
        modelPreviewer.removeAllComponents(ModelPreviewer.RootMotionTrajectoryComponent.class);

        if (!anyNodeHasRootMotion()) return;

        // Cache the sampled path within this component's lifetime, keyed on the cursor — the node graph
        // can't change without the listener rebuilding the component, so only cursor moves invalidate it.
        @SuppressWarnings("unchecked")
        List<Vec3>[] cache = new List[]{null};
        float[] cacheKey = {Float.NaN, Float.NaN};

        modelPreviewer.addComponent(new ModelPreviewer.RootMotionTrajectoryComponent(
            () -> {
                if (cache[0] == null || previewX != cacheKey[0] || previewY != cacheKey[1]) {
                    cache[0] = sampleBlendPath();
                    cacheKey[0] = previewX;
                    cacheKey[1] = previewY;
                }
                return cache[0];
            },
            () -> {
                BlendSpace2D bs = previewBlendSpace();
                if (bs == null) return Vec3.ZERO;

                ModelPreviewer.EntityModelComponent c = modelPreviewer.getComponent(ModelPreviewer.EntityModelComponent.class);
                float elapsed = (c != null && c.getAnimator() instanceof ModelPreviewer.EntityModelComponent.EditorScrubAnimator s)
                    ? s.getElapsedTime() : 0.0F;
                return blendedRoot(bs, previewX, previewY, elapsed);
            }
        ));
    }

    /// Trajectory polyline = the blended root over time at the **current** preview cursor. Reshapes as
    /// the cursor moves (each corner contributes its own root motion, weighted barycentrically).
    private List<Vec3> sampleBlendPath() {
        BlendSpace2D bs = previewBlendSpace();
        if (bs == null) return List.of();

        float total = blendedTotalTime(bs, previewX, previewY);
        if (total <= 0.0F) return List.of();

        int steps = Math.min(4000, Math.max(1, (int) (total / TRAJECTORY_SAMPLE_STEP)));
        List<Vec3> pts = new ArrayList<>(steps + 1);
        for (int i = 0; i <= steps; i++) {
            float t = Math.min(i * TRAJECTORY_SAMPLE_STEP, total);
            pts.add(blendedRoot(bs, previewX, previewY, t));
        }

        return pts;
    }

    /// Barycentric blend of the enclosing triangle corners' raw root locations at `t`. Mirrors
    /// `BlendSpaceAnimator.getPose` / `BlendSpace2DDriver.getBlendedRootMotion`, but on root loc.
    private static Vec3 blendedRoot(BlendSpace2D bs, float cx, float cy, float t) {
        DelaunayTriangulation<BlendSpace2D.AnimationNode> space = bs.getNodeSpace();
        float x = Math.clamp(cx, space.minX(), space.maxX());
        float y = Math.clamp(cy, space.minY(), space.maxY());

        int tri = space.findContainingTriangle(x, y);
        if (tri < 0 || tri >= space.getTriangles().size()) return Vec3.ZERO;

        var t3 = space.getTriangles().get(tri);
        var p1 = space.getPoint(t3.a());
        var p2 = space.getPoint(t3.b());
        var p3 = space.getPoint(t3.c());
        float[] w = space.barycentricWeightsOf(x, y, p1, p2, p3);

        var a1 = p1.animation().value();
        var a2 = p2.animation().value();
        var a3 = p3.animation().value();

        // Same phase-sync as BlendSpaceAnimator#getPose: `t` runs on the blended clock, each
        // corner is sampled at its own length scaled by the shared phase, so the trajectory
        // marker stays under the animated feet.
        float total = a1.getTotalFrameTime() * w[0] + a2.getTotalFrameTime() * w[1] + a3.getTotalFrameTime() * w[2];
        float s1, s2, s3;
        if (total > 0.0F && total != Float.MAX_VALUE) {
            float phase = t / total;
            s1 = phase * a1.getTotalFrameTime();
            s2 = phase * a2.getTotalFrameTime();
            s3 = phase * a3.getTotalFrameTime();
        } else {
            s1 = s2 = s3 = t;
        }

        return a1.getRootMotionTransform(s1).loc().scale(w[0])
            .add(a2.getRootMotionTransform(s2).loc().scale(w[1]))
            .add(a3.getRootMotionTransform(s3).loc().scale(w[2]));
    }

    /// Weighted clip length of the triangle the cursor sits in (matches `BlendSpaceAnimator`).
    private static float blendedTotalTime(BlendSpace2D bs, float cx, float cy) {
        DelaunayTriangulation<BlendSpace2D.AnimationNode> space = bs.getNodeSpace();
        float x = Math.clamp(cx, space.minX(), space.maxX());
        float y = Math.clamp(cy, space.minY(), space.maxY());

        int tri = space.findContainingTriangle(x, y);
        if (tri < 0 || tri >= space.getTriangles().size()) return 0.0F;

        var t3 = space.getTriangles().get(tri);
        var p1 = space.getPoint(t3.a());
        var p2 = space.getPoint(t3.b());
        var p3 = space.getPoint(t3.c());
        float[] w = space.barycentricWeightsOf(x, y, p1, p2, p3);

        float total = p1.animation().value().getTotalFrameTime() * w[0]
            + p2.animation().value().getTotalFrameTime() * w[1]
            + p3.animation().value().getTotalFrameTime() * w[2];

        return Float.isFinite(total) ? total : 0.0F;
    }

    /// True if any node's referenced animation has root motion on. A blend space can't itself trigger
    /// root motion, so a nested blend node contributes nothing here.
    private boolean anyNodeHasRootMotion() {
        for (BlendSpace2DHolder.MutableAnimNode n : blendSpaceHolder.getNodes()) {
            if (hasRootMotion(n.animation())) return true;
        }

        return false;
    }

    private static boolean hasRootMotion(AnimationRef ref) {
        return switch (ref) {
            case AnimationRef.Sequence s -> s.holder().isBound() && s.holder().value().rootMotion();
            case AnimationRef.Montage m -> m.holder().isBound() && m.holder().value().rootMotion();
            case AnimationRef.Blend ignored -> false;
            case AnimationRef.Empty ignored -> false;
        };
    }

    /// The built engine BlendSpace2D, rebuilt only when the holder's [BlendSpace2DHolder#getStructureVersion]
    /// moved (node add/remove/position/animation). Avoids re-resolving every node clip per frame.
    private @Nullable BlendSpace2D previewBlendSpace() {
        long v = blendSpaceHolder.getStructureVersion();

        if (previewBlendSpace == null || v != previewBlendSpaceVersion) {
            previewBlendSpace = blendSpaceHolder.makeObject();
            previewBlendSpaceVersion = v;
        }

        return previewBlendSpace;
    }

    /// Headless animator that previews a [BlendSpace2D] for the editor. Reads its blendspace and control
    /// point (x, y) live from suppliers, so node/position/animation edits and cursor moves show up without
    /// re-install. Pose = barycentric blend of the enclosing triangle's 3 corner animations, mirroring
    /// `BlendSpace2DDriver#getPose`. [#getOwner] / [#getMontagePlayer] unsupported.
    private static final class BlendSpaceAnimator implements ModelPreviewer.EntityModelComponent.EditorScrubAnimator {
        private final Supplier<@Nullable BlendSpace2D> blendSupplier;
        private final Supplier<Float> xSupplier;
        private final Supplier<Float> ySupplier;
        private float elapsedTime = 0.0F;
        private boolean updatePose = true;
        @Nullable private Runnable onPlaybackEnds;

        BlendSpaceAnimator(
            final Supplier<@Nullable BlendSpace2D> blendSupplier,
            final Supplier<Float> xSupplier,
            final Supplier<Float> ySupplier
        ) {
            this.blendSupplier = blendSupplier;
            this.xSupplier = xSupplier;
            this.ySupplier = ySupplier;
        }

        @Override public @Nullable AssetAccessor<AnimationClip> getPreviewAnimation() { return null; }
        @Override public float getElapsedTime() { return elapsedTime; }
        @Override public void setUpdatePose(final boolean updatePose) { this.updatePose = updatePose; }
        @Override public void setOnPlaybackEnds(final Runnable onPlaybackEnds) { this.onPlaybackEnds = onPlaybackEnds; }
        @Override public void setElapsedTime(final float seconds) { elapsedTime = Math.max(0.0F, seconds); }

        @Override
        public Pose getPose() {
            BlendSpace2D bs = blendSupplier.get();
            if (bs == null) return Pose.EMPTY_POSE;

            DelaunayTriangulation<BlendSpace2D.AnimationNode> space = bs.getNodeSpace();
            float x = Math.clamp(xSupplier.get(), space.minX(), space.maxX());
            float y = Math.clamp(ySupplier.get(), space.minY(), space.maxY());

            int tri = space.findContainingTriangle(x, y);
            if (tri < 0 || tri >= space.getTriangles().size()) return Pose.EMPTY_POSE;

            var t  = space.getTriangles().get(tri);
            var p1 = space.getPoint(t.a());
            var p2 = space.getPoint(t.b());
            var p3 = space.getPoint(t.c());
            float[] w = space.barycentricWeightsOf(x, y, p1, p2, p3);

            var a1 = p1.animation().value();
            var a2 = p2.animation().value();
            var a3 = p3.animation().value();

            // Phase-sync: the playback clock wraps at the *weighted* total (see [#update]), so
            // sampling corners at raw elapsedTime freezes any shorter clip on its last keyframe
            // until the wrap — a visible pause between loops. Sample each corner at the shared
            // normalized phase scaled to its own length instead.
            float total = a1.getTotalFrameTime() * w[0] + a2.getTotalFrameTime() * w[1] + a3.getTotalFrameTime() * w[2];
            float t1, t2, t3;
            if (total > 0.0F && total != Float.MAX_VALUE) {
                float phase = elapsedTime / total;
                t1 = phase * a1.getTotalFrameTime();
                t2 = phase * a2.getTotalFrameTime();
                t3 = phase * a3.getTotalFrameTime();
            } else {
                t1 = t2 = t3 = elapsedTime;
            }

            return Pose.blendPoses(
                Pair.of(a1.getPose(t1), w[0]),
                Pair.of(a2.getPose(t2), w[1]),
                Pair.of(a3.getPose(t3), w[2])
            );
        }

        @Override
        public void update(final float timeFrame, final float partialTick) {
            if (!updatePose) return;
            BlendSpace2D bs = blendSupplier.get();
            if (bs == null) return;

            float total = relativeFinalTime(bs);
            if (total <= 0.0F || total == Float.MAX_VALUE) return;

            elapsedTime += timeFrame;
            if (elapsedTime > total) {
                if (onPlaybackEnds != null) onPlaybackEnds.run();
                elapsedTime = bs.loops() ? elapsedTime % total : total;
            }
        }

        /// Weighted clip length of the triangle the control point sits in. Mirrors
        /// BlendSpace2DDriver.update's `relativeFinalTime`.
        private float relativeFinalTime(BlendSpace2D bs) {
            DelaunayTriangulation<BlendSpace2D.AnimationNode> space = bs.getNodeSpace();
            float x = Math.clamp(xSupplier.get(), space.minX(), space.maxX());
            float y = Math.clamp(ySupplier.get(), space.minY(), space.maxY());
            int tri = space.findContainingTriangle(x, y);
            if (tri < 0 || tri >= space.getTriangles().size()) return Float.MAX_VALUE;

            var t  = space.getTriangles().get(tri);
            var p1 = space.getPoint(t.a());
            var p2 = space.getPoint(t.b());
            var p3 = space.getPoint(t.c());
            float[] w = space.barycentricWeightsOf(x, y, p1, p2, p3);
            return p1.animation().value().getTotalFrameTime() * w[0]
                 + p2.animation().value().getTotalFrameTime() * w[1]
                 + p3.animation().value().getTotalFrameTime() * w[2];
        }

        @Override public void tick() {}
        @Override @Deprecated public void setSoftPause(final boolean paused) {}
        @Override @Deprecated public void setHardPause(final boolean paused) {}
        @Override @Deprecated public boolean isSoftPaused() { return false; }
        @Override @Deprecated public boolean isHardPaused() { return false; }

        @Override
        public EntityPatch<Entity> getOwner() {
            throw new UnsupportedOperationException("BlendSpaceAnimator has no owner entity");
        }

        @Override
        public AnimationMontagePlayer getMontagePlayer(final ResourceKey<@NonNull AnimationMontage> animMontageKey) {
            throw new UnsupportedOperationException("BlendSpaceAnimator has no montage player");
        }
    }
}