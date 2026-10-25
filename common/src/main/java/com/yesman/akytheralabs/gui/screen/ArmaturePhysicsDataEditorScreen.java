package com.yesman.akytheralabs.gui.screen;

import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.client.api.gui.AkytheraButton;
import com.yesman.akythera.client.api.gui.CheckBox;
import com.yesman.akythera.client.api.gui.WidgetTable;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalAccessibleScreen;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.client.api.gui.viewer.BackgroundComponent;
import com.yesman.akythera.client.api.gui.viewer.CameraComponent;
import com.yesman.akythera.client.api.gui.viewer.ColliderViewerComponent;
import com.yesman.akythera.client.api.gui.viewer.EntityModelComponent;
import com.yesman.akythera.client.api.gui.viewer.GridFloorComponent;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewerComponent;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.armature.Bone;
import com.yesman.akythera.core.asset.physics.ArmaturePhysicsData;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.math.physics.ragdoll.RagdollSimulation;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.akythera.collider.ColliderComponentHolder;
import com.yesman.akytheralabs.akythera.physics.ArmaturePhysicsDataHolder;
import com.yesman.akytheralabs.akythera.physics.BoneBodyHolder;
import com.yesman.akytheralabs.dataholder.EntityPreviewModelMapping;
import com.yesman.akytheralabs.editor.EditorWidgetFactory;
import com.yesman.akytheralabs.editor.historytrack.DataChange;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.WorkHistory;
import com.yesman.akytheralabs.editor.keybind.EditorKeyMappings;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.AdjustablePanelManager;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsButtonTheme;
import com.yesman.akytheralabs.gui.widget.EditorComboBox;
import com.yesman.akytheralabs.gui.widget.PanelHeader;
import com.yesman.akytheralabs.gui.widget.main.ConsoleModal;
import com.yesman.akytheralabs.gui.widget.main.EditorPreferencesModalGenerator;
import com.yesman.akytheralabs.gui.widget.main.EntityTypeSelectModal;
import com.yesman.akytheralabs.gui.widget.main.LocalizationManagerModal;
import com.yesman.akytheralabs.gui.widget.main.TopMenuBar;
import com.yesman.akytheralabs.gui.widget.main.TopMenuOption;
import com.yesman.akytheralabs.gui.widget.viewer.ArmatureViewComponent;
import com.yesman.akytheralabs.gui.widget.viewer.BoneConstraintLimitComponent;
import com.yesman.akytheralabs.gui.widget.viewer.ColliderPreviewComponent;
import com.yesman.akytheralabs.gui.widget.viewer.RagdollDragComponent;
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
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/// Edits the rag doll of one armature: the body and the constraint of each bone, picked by bone in the
/// Collider Properties panel or by clicking the armature in the viewer, and the simulation settings.
///
/// The armature is the asset's own path (invariant 7), so this screen never picks one — it opens on
/// whatever the file belongs to.
public final class ArmaturePhysicsDataEditorScreen extends Screen implements ModalAccessibleScreen, SafeCloseScreen {
    private static final int TOP_BAR_H = 22;
    private static final int ROW_H = 20;
    private static final int COMBO_H = 14;
    private static final int ROW_PAD = 6;
    private static final int OVERLAY_PAD = 4; // gap between an overlay widget and the viewer's edge
    private static final int SIMULATE_W = 100;
    private static final int VIEW_BUTTON = 20; // side of the view-mode button
    private static final int VIEW_BUTTON_X = 58; // right of the drag gizmo's Move button and orientation toggle

    /// The two faces of the view-mode button, held so the split lines are cached between frames.
    private static final Tooltip WIREFRAME_TOOLTIP = Tooltip.create(Component.translatable(LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_VIEW_WIREFRAME));
    private static final Tooltip TEXTURED_TOOLTIP = Tooltip.create(Component.translatable(LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_VIEW_TEXTURED));
    private static final int SIMULATE_H = 20;
    private static final int BUOYANCY_CHECKBOX_W = 130;
    private static final int BUOYANCY_CHECKBOX_H = 12;

    /// How deep the water of a buoyancy simulation is, in blocks, over its floor.
    private static final double BUOYANCY_POOL_DEPTH = 8.0D;

    /// The push [#toggleSimulation] gives the topmost bone, in mass times blocks per second.
    private static final double SIMULATION_PUSH = 1.0D;

    /// One game tick, in nanoseconds.
    private static final long TICK_NANOS = 50_000_000L;

    /// Most ticks one frame catches up on; a longer stall drops the time it missed.
    private static final int MAX_TICKS_PER_FRAME = 5;

    private static final int SIMULATED_COLLIDER_COLOR = 0xFF33FF66;

    private final AdjustablePanelManager panelManager;
    private final TopMenuBar topMenuBar;
    private final Model3DViewer model3DViewer;
    private final PanelHeader header;

    /// The selected bone's body.
    private final WidgetTable bodyTable;

    private final PanelHeader constraintHeader;

    /// The selected bone's constraint.
    private final WidgetTable constraintTable;

    private final PanelHeader simulationHeader;
    private final WidgetTable simulationTable;

    /// The asset view, taken off the accessor so a reopened screen continues the same edit.
    private final ArmaturePhysicsDataHolder holder;

    private @Nullable EditorComboBox<String> boneCombo;
    private @Nullable String selectedBone;
    private @Nullable ScreenRectangle panelRect;
    private @Nullable ScreenRectangle constraintRect;
    private @Nullable ScreenRectangle simulationRect;

    private @Nullable ArmatureViewComponent armatureView;

    /// The collider wireframes: [ColliderPreviewComponent]s while editing, plain [ColliderViewerComponent]s while a
    /// simulation runs, so a click reaches [#ragdollDrag].
    private final List<Model3DViewerComponent> colliderPreviews = new ArrayList<>();

    /// The selected bone's rotation limits, null while no bone is selected or a simulation runs.
    private @Nullable BoneConstraintLimitComponent constraintLimit;

    private final AkytheraButton simulateButton;

    /// Whether [#simulateButton] drops the rag doll into water whose surface is at y = 0, rather than onto a floor there.
    private boolean buoyancySimulation;

    /// Switches a running simulation between the textured view and the wireframe view. Shown while one runs.
    private final AkytheraButton viewModeButton;

    /// Whether a running simulation is drawn as collider and bone wireframes rather than the textured mesh.
    private boolean simulationWireframe;

    /// The rag doll [#simulateButton] runs over a floor at the viewer's origin, null while none runs.
    private @Nullable RagdollSimulation simulation;

    /// The physics [#simulation] was built from, whose bone constraints the drawn pose takes.
    private @Nullable ArmaturePhysicsData simulatedPhysics;

    /// Holds the picked bone at the gizmo while [#simulation] runs; installed in place of [#armatureView].
    private @Nullable RagdollDragComponent ragdollDrag;

    /// When [#simulation] last ticked, in [System#nanoTime] nanoseconds.
    private long simulationTickNanos;

    /// The pose [#simulation] draws this frame, null while none runs.
    private @Nullable Pose simulatedPose;

    /// Each bone's transform in armature space for the collider previews: the bind pose, or [#simulatedPose] while a
    /// simulation runs.
    private final Map<String, Transform> scenePlacements = new HashMap<>();

    private @Nullable WorkHistory savePoint;

    public ArmaturePhysicsDataEditorScreen(AkytheraProject.EditorArmaturePhysicsAccessor accessor) {
        super(Component.translatable(LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_TITLE));

        this.holder = accessor.metadata();

        topMenuBar = new TopMenuBar(
            minecraft.font,
            Component.literal(accessor.identifier().toString()),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(TOP_BAR_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            () -> handleQuitRequest(),
            TopMenuBar.actions()
                .on(TopMenuOption.LOCALIZATION, () -> LocalizationManagerModal.open(minecraft.font))
                .on(TopMenuOption.SAVE, this::save)
                .on(TopMenuOption.EDITOR_PREFERENCES, () -> EditorPreferencesModalGenerator.open(font))
                .on(TopMenuOption.SELECT_PREVIEW_MODEL, this::openPreviewModelSelect)
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
        applyPreviewEntityType(holder.getPreviewEntityType());

        header = new PanelHeader(minecraft.font, Component.translatable(LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_SECTION_COLLIDER_PROPERTIES));

        bodyTable = new WidgetTable(
            this,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(PanelHeader.DEFAULT_HEADER_HEIGHT + COMBO_H + ROW_PAD * 2), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM,
            ROW_H
        );

        constraintHeader = new PanelHeader(minecraft.font, Component.translatable(LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_SECTION_BONE_CONSTRAINT));
        constraintTable = new WidgetTable(
            this,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(PanelHeader.DEFAULT_HEADER_HEIGHT + 2), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM,
            ROW_H
        );

        simulationHeader = new PanelHeader(minecraft.font, Component.translatable(LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_SECTION_SIMULATION));
        simulateButton = AkytheraButton
            .buttonBuilder(minecraft.font, LocalizedMessage.translatable(LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_BUTTON_SIMULATE), this::toggleSimulation)
            .xUnits(AnchoredWidget.pixel(OVERLAY_PAD), AnchoredWidget.pixel(SIMULATE_W))
            .yUnits(AnchoredWidget.pixel(OVERLAY_PAD), AnchoredWidget.pixel(SIMULATE_H))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.BOTTOM_HEIGHT)
            .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
            .build();
        model3DViewer.addOverlayWidget(simulateButton);

        CheckBox buoyancyCheckBox = new CheckBox(
            minecraft.font,
            AnchoredWidget.pixel(OVERLAY_PAD * 2 + SIMULATE_W), AnchoredWidget.pixel(BUOYANCY_CHECKBOX_W),
            AnchoredWidget.pixel(OVERLAY_PAD + (SIMULATE_H - BUOYANCY_CHECKBOX_H) / 2), AnchoredWidget.pixel(BUOYANCY_CHECKBOX_H),
            AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH,
            AnchoredWidget.VerticalAnchorType.BOTTOM_HEIGHT,
            () -> buoyancySimulation,
            enabled -> buoyancySimulation = enabled,
            Component.translatable(LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_BUOYANCY_SIMULATION),
            true
        );
        buoyancyCheckBox.valueSetter().accept(buoyancySimulation);
        model3DViewer.addOverlayWidget(buoyancyCheckBox);

        viewModeButton = AkytheraButton
            .buttonBuilder(minecraft.font, LocalizedMessage.EMPTY, () -> applySimulationView(!simulationWireframe))
            .xUnits(AnchoredWidget.pixel(VIEW_BUTTON_X), AnchoredWidget.pixel(VIEW_BUTTON))
            .yUnits(AnchoredWidget.pixel(OVERLAY_PAD), AnchoredWidget.pixel(VIEW_BUTTON))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.TOP_HEIGHT)
            .theme(AkytheraLabsButtonTheme.CLOTH_TEXTURED_BUTTON)
            .tooltip(_ -> simulationWireframe ? WIREFRAME_TOOLTIP : TEXTURED_TOOLTIP)
            .build();
        viewModeButton.visible = false;
        model3DViewer.addOverlayWidget(viewModeButton);

        simulationTable = new WidgetTable(
            this,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(PanelHeader.DEFAULT_HEADER_HEIGHT + 2), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM,
            ROW_H
        );

        panelManager = new AdjustablePanelManager(
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(TOP_BAR_H), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM
        );

        // Divider order (== serialize order): columns(0), left column body | constraint(1), constraint | simulation(2)
        List<Integer> layoutRatios = AkytheraProject.getCurrentProject().getLayoutRatioOrDefault(getClass(), 28, 45, 75);
        var leftAndRight = panelManager.getRoot()
            .subdivide(1, AdjustablePanelManager.PanelDivider.Orientation.VERTICAL, layoutRatios.get(0));
        var leftSections = leftAndRight.get(0)
            .subdivide(2, AdjustablePanelManager.PanelDivider.Orientation.HORIZONTAL, layoutRatios.get(1), layoutRatios.get(2));

        leftSections.get(0).resizeCallback(rect -> {
            panelRect = rect;
            header.relocate(rect);
            bodyTable.relocate(rect);

            if (boneCombo != null) boneCombo.relocate(rect);
        });
        leftSections.get(1).resizeCallback(rect -> {
            constraintRect = rect;
            constraintHeader.relocate(rect);
            constraintTable.relocate(rect);
        });
        leftSections.get(2).resizeCallback(rect -> {
            simulationRect = rect;
            simulationHeader.relocate(rect);
            simulationTable.relocate(rect);
        });
        leftAndRight.get(1).resizeCallback(model3DViewer::relocate);
        leftAndRight.get(1).resizeFinishCallback(_ -> model3DViewer.onResizeCompleted());

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
        addRenderableWidget(header);
        addRenderableWidget(bodyTable);
        addRenderableWidget(constraintHeader);
        addRenderableWidget(constraintTable);
        addRenderableWidget(simulationHeader);
        addRenderableWidget(simulationTable);

        rebuildAll();
        fillSimulationTable();
        frameCamera();
    }

    private void frameCamera() {
        model3DViewer.frameCamera(Model3DViewer.DEFAULT_SKELETAL_DIMENSIONS.width(), Model3DViewer.DEFAULT_SKELETAL_DIMENSIONS.height());
    }

    // ***************************************************************
    // Panels and scene
    // ***************************************************************

    /// Everything the armature decides: the bone combo, the body table, the armature view and the
    /// collider previews.
    private void rebuildAll() {
        Armature armature = holder.getArmature().getOrHandleException(AkytheraLabs.LOGGER::error);
        List<String> boneNames = new ArrayList<>();

        if (armature != null) {
            armature.visitBones(bone -> boneNames.add(bone.getName()));
            boneNames.sort(Comparator.naturalOrder());
        }

        if (selectedBone != null && !boneNames.contains(selectedBone)) {
            selectedBone = null;
        }

        if (boneCombo != null) removeWidget(boneCombo);

        boneCombo = new EditorComboBox<>(
            font,
            AnchoredWidget.pixel(ROW_PAD), AnchoredWidget.pixel(ROW_PAD),
            AnchoredWidget.pixel(PanelHeader.DEFAULT_HEADER_HEIGHT + ROW_PAD), AnchoredWidget.pixel(COMBO_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT, AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            () -> selectedBone,
            this::selectBone,
            Math.clamp(boneNames.size(), 8, 16),
            Component.translatable(LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_BONE),
            List.copyOf(boneNames),
            Function.identity()
        );

        boneCombo.setClearable(false);

        if (selectedBone != null) boneCombo.setValueQuiet(selectedBone);
        if (panelRect != null) boneCombo.relocate(panelRect);

        addRenderableWidget(boneCombo);
        fillBodyTable();
        fillConstraintTable();

        if (armatureView == null) {
            ArmatureViewComponent view = new ArmatureViewComponent(holder.getArmature());
            view.setOnSelectionChanged(() -> {
                Bone picked = view.getSelectedBone();
                if (picked != null) selectBone(picked.getName());
            });

            armatureView = view;
            model3DViewer.addComponent(view);
        }

        rebuildScene();
    }

    /// The selected bone's body rows. A refill adds fresh widgets, so the table is relocated to place them.
    private void fillBodyTable() {
        if (selectedBone == null) {
            bodyTable.clearWidgets();
        } else {
            new EditorWidgetFactory(font).fillWidgetTable(bodyTable, holder.bodyOf(selectedBone), () -> {
                fillBodyTable();
                rebuildScene();
            });
        }

        if (panelRect != null) bodyTable.relocate(panelRect);
    }

    /// The selected bone's constraint rows.
    private void fillConstraintTable() {
        if (selectedBone == null) {
            constraintTable.clearWidgets();
        } else {
            new EditorWidgetFactory(font).fillWidgetTable(constraintTable, holder.constraintsOf(selectedBone), this::fillConstraintTable, holder);
        }

        if (constraintRect != null) constraintTable.relocate(constraintRect);
    }

    private void fillSimulationTable() {
        new EditorWidgetFactory(font).fillWidgetTable(simulationTable, holder.getSimulation(), this::fillSimulationTable);

        if (simulationRect != null) simulationTable.relocate(simulationRect);
    }

    /// One collider preview per collider, placed by its bone's [#scenePlacements] transform, and the selected bone's
    /// rotation limits while no simulation runs.
    private void rebuildScene() {
        colliderPreviews.forEach(model3DViewer::removeComponent);
        colliderPreviews.clear();

        if (constraintLimit != null) {
            model3DViewer.removeComponent(constraintLimit);
            constraintLimit = null;
        }

        Armature armature = holder.getArmature().getOrHandleException(AkytheraLabs.LOGGER::error);

        if (armature == null) return;

        if (simulation == null) {
            scenePlacements.clear();
            collectModelTransforms(armature.getRootBone(), new Matrix4f(), Pose.EMPTY_POSE, scenePlacements);
        }

        Bone selected = selectedBone == null ? null : armature.getBoneByName(selectedBone);

        if (selected != null && simulation == null) {
            constraintLimit = new BoneConstraintLimitComponent(
                scenePlacements.get(selected.getName()), selected.getToTail().length(), holder.constraintsOf(selected.getName())
            );
            model3DViewer.addComponent(constraintLimit);
        }

        if (simulation != null && !simulationWireframe) return;

        for (Map.Entry<String, BoneBodyHolder> body : holder.getBodies().entrySet()) {
            String bone = body.getKey();

            if (!scenePlacements.containsKey(bone)) continue;

            Supplier<Transform> placement = () -> scenePlacements.getOrDefault(bone, Transform.IDENTITY);

            for (ColliderComponentHolder collider : body.getValue().getColliders()) {
                Model3DViewerComponent colliderPreview;

                if (simulation != null) {
                    ColliderViewerComponent wireframe = new ColliderViewerComponent(collider::makeObject, placement);
                    wireframe.setColor(SIMULATED_COLLIDER_COLOR);
                    colliderPreview = wireframe;
                } else if (bone.equals(selectedBone)) {
                    colliderPreview = ColliderPreviewComponent.editable(collider, placement, this::fillBodyTable, _ -> {});
                } else {
                    colliderPreview = ColliderPreviewComponent.inherited(collider, placement, _ -> selectBone(bone));
                }

                colliderPreviews.add(colliderPreview);
                model3DViewer.addComponent(colliderPreview);
            }
        }
    }

    /// Walks `pose` the way [Pose#boundTransformRecursively] does, recording each bone's model transform.
    private static void collectModelTransforms(final Bone bone, final Matrix4f parentTransform, final Pose pose, final Map<String, Transform> out) {
        Matrix4f modelTransform = parentTransform.mul(bone.getLocalTransform(), new Matrix4f()).mul(pose.orElseIdentity(bone.getName()).toMatrix());
        out.put(bone.getName(), Transform.fromMatrix(modelTransform));

        for (Bone child : bone.getChildBones()) {
            collectModelTransforms(child, modelTransform, pose, out);
        }
    }

    // ***************************************************************
    // Simulation preview
    // ***************************************************************

    /// Drops the rag doll of the asset as it is edited now onto a floor at the viewer's origin, or into water whose
    /// surface is there while [#buoyancySimulation] is on, pushed at its topmost bone; or stops the one running. While
    /// it runs, the preview mesh follows it and the picked bone can be dragged by the gizmo. It starts in the textured
    /// view.
    private void toggleSimulation() {
        if (simulation != null) {
            stopSimulation();
            return;
        }

        Armature armature = holder.getArmature().getOrHandleException(AkytheraLabs.LOGGER::error);

        if (armature == null) return;

        simulatedPhysics = holder.makeObject();
        simulation = RagdollSimulation.start(
            armature, simulatedPhysics,
            buoyancySimulation ? RagdollSimulation.Terrain.pool(0.0D, BUOYANCY_POOL_DEPTH) : RagdollSimulation.Terrain.floor(0.0D), null,
            new RagdollSimulation.Snapshot(Pose.EMPTY_POSE, Transform.IDENTITY, 0.0D), 1.0F
        );

        if (simulation == null) return;

        scenePlacements.entrySet().stream()
            .max(Comparator.comparingDouble(entry -> entry.getValue().loc().y))
            .ifPresent(top -> simulation.applyImpulse(top.getKey(), new Vec3(0.0D, 0.0D, SIMULATION_PUSH), null));

        simulationTickNanos = System.nanoTime();
        simulatedPose = armature.applyBoneConstraints(simulation.makePose(Transform.IDENTITY, 0.0F), simulatedPhysics, true);
        simulateButton.setMessage(LocalizedMessage.translatable(LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_BUTTON_STOP_SIMULATION));

        EntityModelComponent shown = model3DViewer.getComponent(EntityModelComponent.class);

        if (shown != null) shown.setPoseSource(() -> simulatedPose);

        if (armatureView != null) {
            armatureView.setPoseSource(() -> simulatedPose);
            model3DViewer.removeComponent(armatureView);

            ragdollDrag = new RagdollDragComponent(armatureView, () -> simulation, bone -> scenePlacements.getOrDefault(bone, Transform.IDENTITY));
            model3DViewer.addComponent(ragdollDrag);
        }

        viewModeButton.visible = true;
        applySimulationView(false);
    }

    /// Draws the running simulation as the textured mesh with the picked bone alone, or as collider and bone
    /// wireframes. Bones are picked in either.
    private void applySimulationView(final boolean wireframe) {
        simulationWireframe = wireframe;
        viewModeButton.setTheme(wireframe ? AkytheraLabsButtonTheme.CLOTH_WIREFRAME_BUTTON : AkytheraLabsButtonTheme.CLOTH_TEXTURED_BUTTON);

        EntityModelComponent shown = model3DViewer.getComponent(EntityModelComponent.class);

        if (shown != null) shown.setModelVisible(!wireframe);
        if (armatureView != null) armatureView.setDrawSelectedOnly(!wireframe);

        rebuildScene();
    }

    private void stopSimulation() {
        simulation = null;
        simulatedPhysics = null;
        simulatedPose = null;
        simulateButton.setMessage(LocalizedMessage.translatable(LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_BUTTON_SIMULATE));

        viewModeButton.visible = false;

        EntityModelComponent shown = model3DViewer.getComponent(EntityModelComponent.class);

        if (shown != null) {
            shown.setPoseSource(null);
            shown.setModelVisible(true);
        }

        if (ragdollDrag != null) {
            model3DViewer.removeComponent(ragdollDrag);
            ragdollDrag = null;
        }

        if (armatureView != null) {
            armatureView.setPoseSource(null);
            armatureView.setDrawSelectedOnly(false);
            model3DViewer.addComponent(armatureView);
        }

        rebuildScene();
    }

    /// Steps [#simulation] by the real time since its last tick, one game tick at a time, and poses the scene between
    /// the last two ticks. An editor screen is drawn but never ticked by the game, so the frame drives it.
    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        Armature armature = simulation == null ? null : holder.getArmature().getOrHandleException(AkytheraLabs.LOGGER::error);

        if (simulation != null && simulatedPhysics != null && armature != null) {
            long now = System.nanoTime();

            for (int steps = 0; now - simulationTickNanos >= TICK_NANOS && steps < MAX_TICKS_PER_FRAME; steps++) {
                simulation.tick();
                simulationTickNanos += TICK_NANOS;
            }

            if (now - simulationTickNanos >= TICK_NANOS) simulationTickNanos = now;

            float partial = Math.clamp((now - simulationTickNanos) / (float) TICK_NANOS, 0.0F, 1.0F);

            simulatedPose = armature.applyBoneConstraints(simulation.makePose(Transform.IDENTITY, partial), simulatedPhysics, true);
            collectModelTransforms(armature.getRootBone(), new Matrix4f(), simulatedPose, scenePlacements);
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    /// Settles the pick, wherever it came from: the combo, a click in the viewer, or a click on a dim
    /// collider. The viewer highlights it either way (Answer 8).
    private void selectBone(final @Nullable String bone) {
        if (bone == null || bone.equals(selectedBone)) return;

        selectedBone = bone;

        if (boneCombo != null) boneCombo.setValueQuiet(bone);

        if (armatureView != null) {
            Armature armature = holder.getArmature().getOrHandleException(AkytheraLabs.LOGGER::error);

            if (armature != null) armatureView.setSelectedBone(armature.getBoneByName(bone));
        }

        fillBodyTable();
        fillConstraintTable();
        rebuildScene();
    }

    private void openPreviewModelSelect() {
        ModalManager.getInstance().openModalWidgetAtCenter(new EntityTypeSelectModal(font, holder.getPreviewEntityType(), entityType -> {
            Identifier before = holder.getPreviewEntityType();

            if (Objects.equals(before, entityType)) return;

            applyPreviewEntityType(entityType);
            HistoryManager.getInstance().pushHistory(new DataChange<>(before, entityType, this::applyPreviewEntityType));
        }));
    }

    /// Installs `model` as the preview: the holder field, and the viewer's model posed by this asset's
    /// armature.
    private void applyPreviewEntityType(final @Nullable Identifier entityType) {
        holder.setPreviewEntityType(entityType);
        EntityPreviewModelMapping.displayOrWarn(model3DViewer, font, entityType, true, false);

        EntityModelComponent shown = model3DViewer.getComponent(EntityModelComponent.class);

        if (shown != null) shown.setArmature(holder.getArmature());
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
