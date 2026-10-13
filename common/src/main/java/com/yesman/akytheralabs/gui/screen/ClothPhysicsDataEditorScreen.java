package com.yesman.akytheralabs.gui.screen;

import com.yesman.akythera.api.animation.animator.Animator;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.client.api.gui.AkytheraButton;
import com.yesman.akythera.client.api.gui.CheckBox;
import com.yesman.akythera.client.api.gui.WidgetTable;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalAccessibleScreen;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.client.api.gui.viewer.BackgroundComponent;
import com.yesman.akythera.client.api.gui.viewer.CameraComponent;
import com.yesman.akythera.client.api.gui.viewer.ClothModelComponent;
import com.yesman.akythera.client.api.gui.viewer.ColliderViewerComponent;
import com.yesman.akythera.client.api.gui.viewer.EntityModelComponent;
import com.yesman.akythera.client.api.gui.viewer.GridFloorComponent;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewerComponent;
import com.yesman.akythera.client.api.lifecycle.ClientComponentsFactory;
import com.yesman.akythera.client.core.asset.mesh.SkeletalMesh;
import com.yesman.akythera.core.animation.keyframe.AnimationClip;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.armature.Bone;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.util.helper.Matrix4fUtils;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.akythera.collider.ColliderComponentHolder;
import com.yesman.akytheralabs.akythera.physics.ArmaturePhysicsDataHolder;
import com.yesman.akytheralabs.akythera.physics.BoneBodyHolder;
import com.yesman.akytheralabs.akythera.physics.BrushSettings;
import com.yesman.akytheralabs.akythera.physics.ClothPhysicsDataHolder;
import com.yesman.akytheralabs.akythera.physics.ParticleMappingSettings;
import com.yesman.akytheralabs.dataholder.DataHolder;
import com.yesman.akytheralabs.dataholder.EntityPreviewModelMapping;
import com.yesman.akytheralabs.editor.EditorWidgetFactory;
import com.yesman.akytheralabs.editor.historytrack.DataChange;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.WorkHistory;
import com.yesman.akytheralabs.editor.keybind.EditorKeyMappings;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.AdjustablePanelManager;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsButtonTheme;
import com.yesman.akytheralabs.gui.widget.PanelHeader;
import com.yesman.akytheralabs.gui.widget.main.AnimationSelectModal;
import com.yesman.akytheralabs.gui.widget.main.ConsoleModal;
import com.yesman.akytheralabs.gui.widget.main.EditorPreferencesModalGenerator;
import com.yesman.akytheralabs.gui.widget.main.LocalizationManagerModal;
import com.yesman.akytheralabs.gui.widget.main.TopMenuBar;
import com.yesman.akytheralabs.gui.widget.main.TopMenuOption;
import com.yesman.akytheralabs.gui.widget.viewer.ClothParticleMappingComponent;
import com.yesman.akytheralabs.gui.widget.viewer.ClothWeightPaintComponent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/// Paints the per-vertex simulation weights of the cloth a skeletal mesh carries, pairs the vertices of
/// its two layers when it has a thickness, tunes the solver, and runs it.
///
/// With one of this project's armature physics assets picked, the viewer also shows that asset's preview
/// model and collider boxes, and Simulate runs the cloth on them. The brush and the pairing tool are off
/// while the cloth runs.
public final class ClothPhysicsDataEditorScreen extends Screen implements ModalAccessibleScreen, SafeCloseScreen {
    private static final int TOP_BAR_H = 22;
    private static final int ROW_H = 20;
    private static final int OVERLAY_PAD = 4; // gap between an overlay widget and the viewer's edge
    private static final int VIEW_BUTTON = 20; // side of a view-mode button
    private static final int VIEW_BUTTON_GAP = 2;
    private static final int SIMULATE_W = 100;
    private static final int SIMULATE_H = 20;
    private static final int CHECKBOX_W = 140;
    private static final int CHECKBOX_H = 12;

    private final AdjustablePanelManager panelManager;
    private final TopMenuBar topMenuBar;
    private final Model3DViewer model3DViewer;
    private final PanelHeader paintHeader;
    private final WidgetTable paintTable;
    private final PanelHeader parametersHeader;
    private final WidgetTable parametersTable;
    private final AkytheraButton simulateButton;

    /// Switches the paint table between weight painting and particle mapping.
    private AkytheraButton mappingButton;

    /// The mesh's cloth, taken off the project so a reopened screen continues the same edit.
    private final ClothPhysicsDataHolder holder;
    private final BrushSettings brush = new BrushSettings();
    private final ParticleMappingSettings mappingSettings = new ParticleMappingSettings();

    private @Nullable ClothWeightPaintComponent painter;

    private @Nullable ClothParticleMappingComponent mapper;

    /// Whether the viewer holds [#mapper] instead of [#painter] while the cloth is not running.
    private boolean mappingParticles;

    /// The picked armature physics asset's preview model, or null while there is none.
    private @Nullable EntityModelComponent previewModel;

    /// The running cloth, or null while painting.
    private @Nullable ClothModelComponent simulatedCloth;

    /// What the preview model plays while the cloth runs. Not saved.
    private @Nullable AssetAccessor<AnimationClip> previewAnimation;

    private boolean previewModelVisible = true;

    /// The tool column's last rectangle, which a refilled table is relocated into.
    private @Nullable ScreenRectangle paintRect;

    /// The parameter column's last rectangle, which a refilled table is relocated into.
    private @Nullable ScreenRectangle parametersRect;

    private @Nullable WorkHistory savePoint;

    public ClothPhysicsDataEditorScreen(AssetAccessor<? extends SkeletalMesh> mesh) {
        super(Component.translatable(LangKeys.GUI_CLOTH_PHYSICS_SCREEN_TITLE));

        this.holder = AkytheraProject.getCurrentProject().getClothPhysicsHolder(mesh);

        topMenuBar = new TopMenuBar(
            minecraft.font,
            Component.literal(mesh.identifier().toString()),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(TOP_BAR_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            () -> handleQuitRequest(),
            TopMenuBar.actions()
                .on(TopMenuOption.LOCALIZATION, () -> LocalizationManagerModal.open(minecraft.font))
                .on(TopMenuOption.SAVE, this::save)
                .on(TopMenuOption.EDITOR_PREFERENCES, () -> EditorPreferencesModalGenerator.open(font))
                .on(TopMenuOption.SELECT_PREVIEW_ANIMATION, this::openPreviewAnimationSelect)
                .on(TopMenuOption.FRAME_CAMERA, this::frameCamera)
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
        model3DViewer.addComponent(new GridFloorComponent(1.0F, 16, 68, 68, 68, 255));
        model3DViewer.addOverlayWidget(viewModeButton(
            ClothWeightPaintComponent.ViewMode.WIREFRAME, AkytheraLabsButtonTheme.CLOTH_WIREFRAME_BUTTON,
            LangKeys.GUI_CLOTH_PHYSICS_SCREEN_VIEW_WIREFRAME, OVERLAY_PAD
        ));
        model3DViewer.addOverlayWidget(viewModeButton(
            ClothWeightPaintComponent.ViewMode.TEXTURED, AkytheraLabsButtonTheme.CLOTH_TEXTURED_BUTTON,
            LangKeys.GUI_CLOTH_PHYSICS_SCREEN_VIEW_TEXTURED, OVERLAY_PAD + VIEW_BUTTON + VIEW_BUTTON_GAP
        ));

        CheckBox previewModelCheckBox = new CheckBox(
            minecraft.font,
            AnchoredWidget.pixel(OVERLAY_PAD), AnchoredWidget.pixel(CHECKBOX_W),
            AnchoredWidget.pixel(OVERLAY_PAD), AnchoredWidget.pixel(CHECKBOX_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH,
            AnchoredWidget.VerticalAnchorType.BOTTOM_HEIGHT,
            () -> previewModelVisible,
            visible -> {
                previewModelVisible = visible;

                if (previewModel != null) previewModel.setModelVisible(visible);
            },
            Component.translatable(LangKeys.GUI_CLOTH_PHYSICS_SCREEN_WIDGET_SHOW_PREVIEW_MODEL),
            true
        );
        previewModelCheckBox.valueSetter().accept(previewModelVisible);
        model3DViewer.addOverlayWidget(previewModelCheckBox);

        simulateButton = AkytheraButton
            .buttonBuilder(minecraft.font, LocalizedMessage.translatable(LangKeys.GUI_CLOTH_PHYSICS_SCREEN_SIMULATE), () -> setSimulating(simulatedCloth == null))
            .xUnits(AnchoredWidget.pixel(OVERLAY_PAD), AnchoredWidget.pixel(SIMULATE_W))
            .yUnits(AnchoredWidget.pixel(OVERLAY_PAD), AnchoredWidget.pixel(SIMULATE_H))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.BOTTOM_HEIGHT)
            .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
            .build();
        model3DViewer.addOverlayWidget(simulateButton);

        AkytheraButton autoPairButton = AkytheraButton
            .buttonBuilder(minecraft.font, LocalizedMessage.translatable(LangKeys.GUI_CLOTH_PHYSICS_SCREEN_AUTO_PAIR), () -> {
                if (mapper != null) mapper.autoPair(mappingSettings);
            })
            .xUnits(AnchoredWidget.pixel(OVERLAY_PAD + SIMULATE_W + VIEW_BUTTON_GAP), AnchoredWidget.pixel(SIMULATE_W))
            .yUnits(AnchoredWidget.pixel(OVERLAY_PAD + SIMULATE_H + VIEW_BUTTON_GAP), AnchoredWidget.pixel(SIMULATE_H))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.BOTTOM_HEIGHT)
            .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
            .build();
        autoPairButton.visible = false;
        model3DViewer.addOverlayWidget(autoPairButton);

        AkytheraButton clearPairingsButton = AkytheraButton
            .buttonBuilder(minecraft.font, LocalizedMessage.translatable(LangKeys.GUI_CLOTH_PHYSICS_SCREEN_CLEAR_PAIRINGS), () -> {
                if (mapper != null) mapper.clearPairs();
            })
            .xUnits(AnchoredWidget.pixel(OVERLAY_PAD), AnchoredWidget.pixel(SIMULATE_W))
            .yUnits(AnchoredWidget.pixel(OVERLAY_PAD + SIMULATE_H + VIEW_BUTTON_GAP), AnchoredWidget.pixel(SIMULATE_H))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.BOTTOM_HEIGHT)
            .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
            .build();
        clearPairingsButton.visible = false;
        model3DViewer.addOverlayWidget(clearPairingsButton);

        paintHeader = new PanelHeader(minecraft.font, Component.translatable(LangKeys.GUI_CLOTH_PHYSICS_SCREEN_SECTION_PAINT_TOOL));
        paintTable = new WidgetTable(
            this,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(PanelHeader.DEFAULT_HEADER_HEIGHT + 2), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM,
            ROW_H
        );

        parametersHeader = new PanelHeader(minecraft.font, Component.translatable(LangKeys.GUI_CLOTH_PHYSICS_SCREEN_SECTION_PARAMETERS));
        parametersTable = new WidgetTable(
            this,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(PanelHeader.DEFAULT_HEADER_HEIGHT + 2), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM,
            ROW_H
        );

        mappingButton = AkytheraButton
            .buttonBuilder(minecraft.font, LocalizedMessage.translatable(LangKeys.GUI_CLOTH_PHYSICS_SCREEN_PARTICLE_MAPPING), () -> {
                mappingParticles = !mappingParticles;
                setSimulating(false);
                autoPairButton.visible = mappingParticles;
                clearPairingsButton.visible = mappingParticles;
                mappingButton.setMessage(LocalizedMessage.translatable(
                    mappingParticles ? LangKeys.GUI_CLOTH_PHYSICS_SCREEN_WEIGHT_PAINTING : LangKeys.GUI_CLOTH_PHYSICS_SCREEN_PARTICLE_MAPPING
                ));
                paintHeader.setMessage(Component.translatable(
                    mappingParticles ? LangKeys.GUI_CLOTH_PHYSICS_SCREEN_SECTION_MAPPING_TOOL : LangKeys.GUI_CLOTH_PHYSICS_SCREEN_SECTION_PAINT_TOOL
                ));
                new EditorWidgetFactory(font).viewer(model3DViewer).fillWidgetTable(paintTable, mappingParticles ? mappingSettings : brush);

                if (paintRect != null) paintTable.relocate(paintRect);
            })
            .xUnits(AnchoredWidget.pixel(OVERLAY_PAD + SIMULATE_W + VIEW_BUTTON_GAP), AnchoredWidget.pixel(SIMULATE_W))
            .yUnits(AnchoredWidget.pixel(OVERLAY_PAD), AnchoredWidget.pixel(SIMULATE_H))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.BOTTOM_HEIGHT)
            .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
            .build();
        model3DViewer.addOverlayWidget(mappingButton);

        new EditorWidgetFactory(minecraft.font).fillWidgetTable(paintTable, brush);
        fillParameters();

        panelManager = new AdjustablePanelManager(
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(TOP_BAR_H), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM
        );

        // Divider order (== serialize order): col-left(0), col-right(1).
        List<Integer> r = AkytheraProject.getCurrentProject().getLayoutRatioOrDefault(getClass(), 20, 76);
        var columns = panelManager.getRoot().subdivide(2, AdjustablePanelManager.PanelDivider.Orientation.VERTICAL, r.get(0), r.get(1));

        columns.get(0).resizeCallback(rect -> {
            paintRect = rect;
            paintHeader.relocate(rect);
            paintTable.relocate(rect);
        });
        columns.get(1).resizeCallback(model3DViewer::relocate);
        columns.get(1).resizeFinishCallback(_ -> model3DViewer.onResizeCompleted());
        columns.get(2).resizeCallback(rect -> {
            parametersRect = rect;
            parametersHeader.relocate(rect);
            parametersTable.relocate(rect);
        });

        buildScene();
        rebuildPreview();

        topMenuBar.setDirtyChecker(this::hasUnsavedChanges);
        savePoint = HistoryManager.getInstance().currentSavePoint();
    }

    @Override
    protected void init() {
        super.init();

        var rect = getRectangle();
        topMenuBar.relocate(rect);
        panelManager.relocate(rect);

        addRenderableWidget(topMenuBar);
        addRenderableWidget(panelManager);
        addRenderableWidget(model3DViewer);
        addRenderableWidget(paintHeader);
        addRenderableWidget(paintTable);
        addRenderableWidget(parametersHeader);
        addRenderableWidget(parametersTable);

        frameCamera();
    }

    private void frameCamera() {
        SkeletalMesh mesh = holder.getMesh().getOrHandleException(AkytheraLabs.LOGGER::error);

        if (mesh == null) {
            model3DViewer.frameCamera(Model3DViewer.DEFAULT_SKELETAL_DIMENSIONS.width(), Model3DViewer.DEFAULT_SKELETAL_DIMENSIONS.height());
        } else {
            model3DViewer.frameCamera(mesh.getMetadata().width(), mesh.getMetadata().height());
        }
    }

    // ***************************************************************
    // Panels and scene
    // ***************************************************************

    /// The cloth rows. A refill adds fresh widgets, so the table is relocated to place them. Picking an
    /// armature physics asset refills it, and rebuilds the preview with it.
    private void fillParameters() {
        new EditorWidgetFactory(font).fillWidgetTable(parametersTable, holder, () -> {
            fillParameters();
            rebuildPreview();
        });

        if (parametersRect != null) parametersTable.relocate(parametersRect);
    }

    /// The painter over the bind pose, focused so it takes presses anywhere in the viewer, and the pairing
    /// tool that stands in for it. Built once — nothing in this screen replaces the mesh they edit.
    private void buildScene() {
        AssetAccessor<? extends SkeletalMesh> mesh = holder.getMesh();
        SkeletalMesh resolvedMesh = mesh.getOrHandleException(AkytheraLabs.LOGGER::error);

        if (resolvedMesh == null) return;

        holder.fitWeights(resolvedMesh.getPositionCount());

        painter = new ClothWeightPaintComponent(mesh, holder::getWeights, holder::getOffsetPairs, brush, this::commitStroke);
        mapper = new ClothParticleMappingComponent(mesh, holder::getOffsetPairs, (before, after) -> {
            holder.setOffsetPairs(after);
            HistoryManager.getInstance().pushHistory(new DataChange<>(before, after, holder::setOffsetPairs));
        });
        model3DViewer.addComponent(painter);
        model3DViewer.focus(painter);
    }

    /// The armature physics asset the cloth collides with, when it is one of this project's.
    private AkytheraProject.@Nullable EditorArmaturePhysicsAccessor selectedArmaturePhysics() {
        return holder.getArmaturePhysics() instanceof AkytheraProject.EditorArmaturePhysicsAccessor physics
            && !physics.identifier().equals(DataHolder.EMPTY_REFERENCE) ? physics : null;
    }

    /// The picked armature physics asset's preview model and one wireframe per body collider, placed on its
    /// bone. Stops a running simulation, which was built on the previous pick.
    private void rebuildPreview() {
        setSimulating(false);
        model3DViewer.removeAllComponents(ColliderViewerComponent.class);

        AkytheraProject.EditorArmaturePhysicsAccessor physics = selectedArmaturePhysics();
        ArmaturePhysicsDataHolder bodies = physics == null ? null : physics.metadata();

        EntityPreviewModelMapping.displayOrWarn(model3DViewer, font, bodies == null ? null : bodies.getPreviewEntityType(), true, false);
        previewModel = model3DViewer.getComponent(EntityModelComponent.class);
        simulateButton.active = bodies != null && ClientComponentsFactory.getImpl().supportClothSimulation();

        if (bodies == null) return;

        AssetAccessor<? extends Armature> armature = bodies.getArmature();
        Armature resolvedArmature = armature.getOrHandleException(AkytheraLabs.LOGGER::error);

        if (previewModel != null) {
            previewModel.setArmature(armature);
            previewModel.setModelVisible(previewModelVisible);
        }

        if (resolvedArmature == null) return;

        for (Map.Entry<String, BoneBodyHolder> body : bodies.getBodies().entrySet()) {
            Bone bone = resolvedArmature.getBoneByName(body.getKey());

            if (bone == null) continue;

            for (ColliderComponentHolder collider : body.getValue().getColliders()) {
                model3DViewer.addComponent(new ColliderViewerComponent(collider::makeObject, () -> {
                    Pose animated = animatedPose();
                    Pose pose = animated == null ? resolvedArmature.getTPose() : animated;
                    Matrix4f[] transforms = pose.getForwardBoundTransforms(armature, Matrix4fUtils.allocateMatrixArray(resolvedArmature.getBoneCount()));

                    return Transform.fromMatrix(transforms[bone.getId()]);
                }));
            }
        }
    }

    /// The preview model's animated pose while the cloth runs; null otherwise, which stands for the bind
    /// pose the mesh is painted in.
    private @Nullable Pose animatedPose() {
        Animator animator = simulatedCloth == null || previewModel == null ? null : previewModel.getAnimator();

        return animator == null ? null : animator.getPose();
    }

    /// Runs the cloth on the preview model, or goes back to painting or pairing. Neither tool is in the
    /// viewer while the cloth runs.
    private void setSimulating(final boolean simulate) {
        AkytheraProject.EditorArmaturePhysicsAccessor physics = selectedArmaturePhysics();

        if (simulatedCloth != null) {
            model3DViewer.removeComponent(simulatedCloth);
            simulatedCloth = null;
        }

        // Lets go of a direction arrow, which leaves the viewer with its gizmo
        model3DViewer.focus(null);

        if (painter != null) model3DViewer.removeComponent(painter);
        if (mapper != null) model3DViewer.removeComponent(mapper);

        if (simulate && physics != null) {
            ArmaturePhysicsDataHolder bodies = physics.metadata();

            simulatedCloth = new ClothModelComponent(
                holder.getMesh(), bodies.getArmature(), holder::getWeights, holder::getOffsetPairs, holder.getParameters()::makeObject
            );
            simulatedCloth.setArmaturePhysics(bodies::makeObject);
            simulatedCloth.setPoseSource(this::animatedPose);
            model3DViewer.addComponent(simulatedCloth);
        } else {
            Model3DViewerComponent tool = mappingParticles ? mapper : painter;

            if (tool != null) {
                model3DViewer.addComponent(tool);
                model3DViewer.focus(tool);
            }
        }

        if (previewModel != null) previewModel.setSimplePlayingAnimation(simulatedCloth == null ? null : previewAnimation);

        simulateButton.setMessage(Component.translatable(
            simulatedCloth == null ? LangKeys.GUI_CLOTH_PHYSICS_SCREEN_SIMULATE : LangKeys.GUI_CLOTH_PHYSICS_SCREEN_STOP_SIMULATION
        ));
    }

    /// Picks what the preview model plays while the cloth runs. Any animation plays; it is not checked
    /// against the armature.
    private void openPreviewAnimationSelect() {
        AkytheraProject.EditorArmaturePhysicsAccessor physics = selectedArmaturePhysics();
        Identifier previewEntityType = physics == null ? null : physics.metadata().getPreviewEntityType();

        ModalManager.getInstance().openModalWidgetAtCenter(new AnimationSelectModal(font, previewEntityType, (clips, _) -> {
            previewAnimation = clips.isEmpty() ? null : clips.getFirst();

            if (simulatedCloth != null && previewModel != null) previewModel.setSimplePlayingAnimation(previewAnimation);
        }));
    }

    /// One sprite button picking `mode`, highlighted while it is the painter's view.
    private AkytheraButton viewModeButton(
        final ClothWeightPaintComponent.ViewMode mode, final AkytheraLabsButtonTheme theme, final String tooltipKey, final int offsetX
    ) {
        Tooltip tooltip = Tooltip.create(Component.translatable(tooltipKey));

        return AkytheraButton
            .buttonBuilder(minecraft.font, LocalizedMessage.EMPTY, () -> {
                if (painter != null) painter.setViewMode(mode);
            })
            .xUnits(AnchoredWidget.pixel(offsetX), AnchoredWidget.pixel(VIEW_BUTTON))
            .yUnits(AnchoredWidget.pixel(OVERLAY_PAD), AnchoredWidget.pixel(VIEW_BUTTON))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.TOP_HEIGHT)
            .theme(theme)
            .tooltip(_ -> tooltip)
            .isHighlighted(_ -> painter != null && painter.getViewMode() == mode)
            .build();
    }

    /// Replaces the weights with a finished stroke's and files the swap whole on the history.
    private void commitStroke(final float[] before, final float[] after) {
        holder.setWeights(after);
        HistoryManager.getInstance().pushHistory(new DataChange<>(before, after, holder::setWeights));
    }

    // ***************************************************************
    // Screen
    // ***************************************************************

    private void save() {
        if (!holder.save()) return;

        savePoint = HistoryManager.getInstance().currentSavePoint();
    }

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
                ModalManager.getInstance().closeModal();
                save();
                onClose();
            },
            this::onClose
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
}
