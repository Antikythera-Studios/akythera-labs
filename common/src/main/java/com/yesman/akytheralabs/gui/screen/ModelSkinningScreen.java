package com.yesman.akytheralabs.gui.screen;

import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.api.animation.animator.Animator;
import com.yesman.akythera.client.api.gui.AkytheraButton;
import com.yesman.akythera.client.api.gui.CheckBox;
import com.yesman.akythera.client.api.gui.WidgetTable;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalAccessibleScreen;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.client.api.gui.viewer.BackgroundComponent;
import com.yesman.akythera.client.api.gui.viewer.CameraComponent;
import com.yesman.akythera.client.api.gui.viewer.EntityModelComponent;
import com.yesman.akythera.client.api.gui.viewer.GridFloorComponent;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.client.api.gui.viewer.Ray;
import com.yesman.akythera.client.core.asset.mesh.SkeletalMesh;
import com.yesman.akythera.core.animation.keyframe.AnimationClip;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.loader.AssetLoader;
import com.yesman.akythera.core.util.helper.Matrix4fUtils;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.FileSystemHelper;
import com.yesman.akytheralabs.akythera.asset.ModelSkinHolder;
import com.yesman.akytheralabs.akythera.asset.BendingSettings;
import com.yesman.akytheralabs.akythera.physics.BrushSettings;
import com.yesman.akytheralabs.dataholder.EntityPreviewModelMapping;
import com.yesman.akytheralabs.editor.EditorWidgetFactory;
import com.yesman.akytheralabs.editor.historytrack.DataChange;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.keybind.EditorKeyMappings;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.AdjustablePanelManager;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsButtonTheme;
import com.yesman.akytheralabs.gui.widget.PanelHeader;
import com.yesman.akytheralabs.gui.widget.WidgetContainerModal;
import com.yesman.akytheralabs.gui.widget.main.AccessorSelectionModal;
import com.yesman.akytheralabs.gui.widget.main.AnimationSelectModal;
import com.yesman.akytheralabs.gui.widget.main.AssetExportManager;
import com.yesman.akytheralabs.gui.widget.main.ConsoleModal;
import com.yesman.akytheralabs.gui.widget.main.EditorPreferencesModalGenerator;

import com.yesman.akytheralabs.gui.widget.main.LocalizationManagerModal;
import com.yesman.akytheralabs.gui.widget.main.TopMenuBar;
import com.yesman.akytheralabs.gui.widget.main.TopMenuOption;
import com.yesman.akytheralabs.gui.widget.viewer.ArmatureViewComponent;
import com.yesman.akytheralabs.gui.widget.viewer.BonePoseComponent;
import com.yesman.akytheralabs.gui.widget.viewer.MeshSliceComponent;
import com.yesman.akytheralabs.gui.widget.viewer.WeightPaintComponent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/// Skins a vanilla model onto an armature: weights it from a reference mesh the way the game bakes a worn
/// armor piece, paints the weights of one bone at a time, edits the weights of one vertex, and exports the
/// result as a skeletal mesh.
///
/// In the wireframe and textured views a left press on a bone makes it the painted bone and raises a gizmo
/// that poses it, and a left press on a vertex lists its weights in the Details panel. In the weight paint
/// view the brush paints the weights of the painted bone, and no bone is picked.
///
/// The mesh follows the pose the bones are put in, or the preview animation while one plays. The preview
/// model of the first entity type mapped to the picked armature stands under it.
///
/// Add Bending turns the next left press on the mesh into a slice of the part under it, see
/// [ModelSkinHolder#addBending]; a right press gives it up.
public final class ModelSkinningScreen extends Screen implements ModalAccessibleScreen, SafeCloseScreen {
    private static final int TOP_BAR_H = 22;
    private static final int ROW_H = 20;
    private static final int OVERLAY_PAD = 4; // gap between an overlay widget and the viewer's edge
    private static final int VIEW_BUTTON = 20; // side of the view-mode button
    private static final int BUTTON_GAP = 2;
    private static final int BUTTON_W = 100;
    private static final int BUTTON_H = 20;
    private static final int BENDING_MODAL_W = 260;
    private static final int BENDING_ROWS = 4; // rows of BendingSettings
    private static final int CHECKBOX_W = 140;
    private static final int CHECKBOX_H = 12;


    private static final int[] NO_PAIRS = new int[0];

    private final AdjustablePanelManager panelManager;
    private final TopMenuBar topMenuBar;
    private final Model3DViewer model3DViewer;
    private final PanelHeader paintHeader;
    private final WidgetTable paintTable;
    private final PanelHeader informationHeader;
    private final WidgetTable informationTable;
    private final PanelHeader detailsHeader;
    private final WidgetTable detailsTable;
    private final AkytheraButton autoWeightButton;
    private final AkytheraButton exportButton;

    /// Starts and gives up the slice pick, highlighted while it runs.
    private final AkytheraButton addBendingButton;

    /// Steps the painter through its views, drawn as the one it is in.
    private AkytheraButton viewModeButton;

    private final CheckBox previewModelCheckBox;

    private final ModelSkinHolder holder = new ModelSkinHolder();
    private final BrushSettings brush = new BrushSettings(BrushSettings.Mode.SCREEN, BrushSettings.Mode.VOLUME);
    private final WeightPaintComponent painter;

    /// In the viewer, holding its focus, while a slice is picked. Null otherwise.
    private @Nullable MeshSliceComponent slicer;

    /// The bones of [#shownArmature], drawn over the mesh by [#bonePoser]. Null before the first [#refresh].
    private @Nullable ArmatureViewComponent armatureView;

    /// Picks a bone of [#armatureView] and poses it in [#manualPose] through a gizmo. In the viewer, and
    /// holding its focus while a bone is posed.
    private @Nullable BonePoseComponent bonePoser;

    /// The pose the gizmo puts the bones in, by bone name. Starts over with every armature.
    private Pose manualPose = new Pose();

    /// The entity type whose preview model stands under the mesh: the first one mapped to [#shownArmature],
    /// null when none is.
    private @Nullable Identifier previewEntityType;

    /// The preview model of [#previewEntityType], or null while there is none.
    private @Nullable EntityModelComponent previewModel;

    /// What the preview model plays, and the mesh with it. Not saved.
    private @Nullable AssetAccessor<AnimationClip> previewAnimation;

    private boolean previewModelVisible = true;

    /// The armature [#armatureView] was built for.
    private @Nullable AssetAccessor<? extends Armature> shownArmature;

    /// The Model Information panel's last rectangle, which a refilled table is relocated into.
    private @Nullable ScreenRectangle informationRect;

    /// The Details column's last rectangle, which a refilled table is relocated into.
    private @Nullable ScreenRectangle detailsRect;

    public ModelSkinningScreen() {
        super(Component.translatable(LangKeys.GUI_MODEL_SKINNING_SCREEN_TITLE));

        topMenuBar = new TopMenuBar(
            minecraft.font,
            getTitle(),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(TOP_BAR_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            () -> handleQuitRequest(),
            TopMenuBar.actions()
                .on(TopMenuOption.LOCALIZATION, () -> LocalizationManagerModal.open(minecraft.font))
                .on(TopMenuOption.EDITOR_PREFERENCES, () -> EditorPreferencesModalGenerator.open(font))

                .on(TopMenuOption.SELECT_PREVIEW_ANIMATION, () -> ModalManager.getInstance().openModalWidgetAtCenter(
                    new AnimationSelectModal(font, previewEntityType, (clips, _) -> {
                        previewAnimation = clips.isEmpty() ? null : clips.getFirst();
                        rebuildPreviewModel();
                    })
                ))
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

        painter = new WeightPaintComponent(holder.getMesh(), holder::paintedWeights, () -> NO_PAIRS, brush, this::commitStroke);
        painter.setHeatColors(true);
        painter.setPaintable(() -> holder.getPaintedBone() != null);
        // The slice is picked on the bind pose, which is what the mesh is cut in
        painter.setPoser(bind -> {
            AssetAccessor<? extends Armature> armature = holder.getArmature();
            Armature resolved = armature.getOrHandleException(AkytheraLabs.LOGGER::error);

            if (slicer != null || resolved == null) return bind;

            Matrix4f[] boneTransforms = currentPose().getForwardBoundTransforms(
                armature, Matrix4fUtils.allocateMatrixArray(resolved.getBoneCount())
            );

            resolved.boundBackwardTransform(boneTransforms);

            return holder.pose(bind, boneTransforms);
        });
        painter.setOnVertexPicked(vertex -> {
            holder.pickVertex(vertex);
            fillDetails();
        });
        model3DViewer.addComponent(painter);
        model3DViewer.focus(painter);

        viewModeButton = AkytheraButton
            .buttonBuilder(minecraft.font, LocalizedMessage.EMPTY, () -> {
                painter.setViewMode(painter.getViewMode().next());
                viewModeButton.setTheme(painter.getViewMode().buttonTheme());

                // Takes the gizmo off a bone once the brush is back
                if (slicer == null && !painter.isPickingVertices()) model3DViewer.focus(painter);
            })
            .xUnits(AnchoredWidget.pixel(OVERLAY_PAD), AnchoredWidget.pixel(VIEW_BUTTON))
            .yUnits(AnchoredWidget.pixel(OVERLAY_PAD), AnchoredWidget.pixel(VIEW_BUTTON))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.TOP_HEIGHT)
            .theme(painter.getViewMode().buttonTheme())
            .tooltip(_ -> painter.getViewMode().tooltip())
            .build();
        model3DViewer.addOverlayWidget(viewModeButton);

        previewModelCheckBox = new CheckBox(
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

        autoWeightButton = AkytheraButton
            .buttonBuilder(minecraft.font, LocalizedMessage.translatable(LangKeys.GUI_MODEL_SKINNING_SCREEN_AUTO_WEIGHT), this::openReferenceMeshSelect)
            .xUnits(AnchoredWidget.pixel(OVERLAY_PAD), AnchoredWidget.pixel(BUTTON_W))
            .yUnits(AnchoredWidget.pixel(OVERLAY_PAD), AnchoredWidget.pixel(BUTTON_H))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.BOTTOM_HEIGHT)
            .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
            .build();
        model3DViewer.addOverlayWidget(autoWeightButton);

        exportButton = AkytheraButton
            .buttonBuilder(minecraft.font, LocalizedMessage.translatable(LangKeys.GUI_MODEL_SKINNING_SCREEN_EXPORT), this::export)
            .xUnits(AnchoredWidget.pixel(OVERLAY_PAD + BUTTON_W + BUTTON_GAP), AnchoredWidget.pixel(BUTTON_W))
            .yUnits(AnchoredWidget.pixel(OVERLAY_PAD), AnchoredWidget.pixel(BUTTON_H))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.BOTTOM_HEIGHT)
            .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
            .build();
        model3DViewer.addOverlayWidget(exportButton);

        addBendingButton = AkytheraButton
            .buttonBuilder(minecraft.font, LocalizedMessage.translatable(LangKeys.GUI_MODEL_SKINNING_SCREEN_ADD_BENDING), () -> setSlicing(slicer == null))
            .xUnits(AnchoredWidget.pixel(OVERLAY_PAD), AnchoredWidget.pixel(BUTTON_W))
            .yUnits(AnchoredWidget.pixel(OVERLAY_PAD + BUTTON_H + BUTTON_GAP), AnchoredWidget.pixel(BUTTON_H))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.BOTTOM_HEIGHT)
            .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
            .isHighlighted(_ -> slicer != null)
            .build();
        model3DViewer.addOverlayWidget(addBendingButton);

        paintHeader = new PanelHeader(minecraft.font, Component.translatable(LangKeys.GUI_CLOTH_PHYSICS_SCREEN_SECTION_PAINT_TOOL));
        paintTable = new WidgetTable(
            this,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(PanelHeader.DEFAULT_HEADER_HEIGHT + 2), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM,
            ROW_H
        );

        informationHeader = new PanelHeader(minecraft.font, Component.translatable(LangKeys.GUI_MODEL_SKINNING_SCREEN_SECTION_MODEL_INFORMATION));
        informationTable = new WidgetTable(
            this,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(PanelHeader.DEFAULT_HEADER_HEIGHT + 2), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM,
            ROW_H
        );

        detailsHeader = new PanelHeader(minecraft.font, Component.translatable(LangKeys.GUI_WIDGET_DETAILS));
        detailsTable = new WidgetTable(
            this,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(PanelHeader.DEFAULT_HEADER_HEIGHT + 2), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM,
            ROW_H
        );

        new EditorWidgetFactory(minecraft.font).fillWidgetTable(paintTable, brush);

        panelManager = new AdjustablePanelManager(
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(TOP_BAR_H), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM
        );

        // Divider order (== serialize order): col-left(0), col-right(1), row-paint(2).
        List<Integer> r = AkytheraProject.getCurrentProject().getLayoutRatioOrDefault(getClass(), 22, 78, 40);
        var columns = panelManager.getRoot().subdivide(2, AdjustablePanelManager.PanelDivider.Orientation.VERTICAL, r.get(0), r.get(1));
        var rows = columns.get(0).subdivide(1, AdjustablePanelManager.PanelDivider.Orientation.HORIZONTAL, r.get(2));

        rows.get(0).resizeCallback(rect -> {
            paintHeader.relocate(rect);
            paintTable.relocate(rect);
        });
        rows.get(1).resizeCallback(rect -> {
            informationRect = rect;
            informationHeader.relocate(rect);
            informationTable.relocate(rect);
        });
        columns.get(1).resizeCallback(model3DViewer::relocate);
        columns.get(1).resizeFinishCallback(_ -> model3DViewer.onResizeCompleted());
        columns.get(2).resizeCallback(rect -> {
            detailsRect = rect;
            detailsHeader.relocate(rect);
            detailsTable.relocate(rect);
        });

        refresh();
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
        addRenderableWidget(informationHeader);
        addRenderableWidget(informationTable);
        addRenderableWidget(detailsHeader);
        addRenderableWidget(detailsTable);

        frameCamera();
    }

    private void frameCamera() {
        SkeletalMesh mesh = holder.getMesh().get();

        if (mesh == null) {
            model3DViewer.frameCamera(Model3DViewer.DEFAULT_SKELETAL_DIMENSIONS.width(), Model3DViewer.DEFAULT_SKELETAL_DIMENSIONS.height());
        } else {
            model3DViewer.frameCamera(mesh.getMetadata().width(), mesh.getMetadata().height());
        }
    }

    // ***************************************************************
    // Panels and scene
    // ***************************************************************

    /// The weights of the picked vertex. A refill adds fresh widgets, so the table is relocated to place them.
    private void fillDetails() {
        new EditorWidgetFactory(font).fillWidgetTable(detailsTable, holder.getPickedVertex(), this::fillDetails, holder);

        if (detailsRect != null) detailsTable.relocate(detailsRect);
    }

    /// Brings the bone overlay, the buttons and both panels in step with the holder. A bone picked in the
    /// viewer becomes the painted one, and the focus goes back to the tool that held it.
    private void refresh() {
        AssetAccessor<? extends Armature> armature = holder.getArmature();

        if (armatureView == null || shownArmature != armature) {
            ArmatureViewComponent view = new ArmatureViewComponent(armature);

            manualPose = new Pose();
            view.setPoseSource(this::currentPose);
            view.setOnSelectionChanged(() -> holder.setPaintedBone(view.getSelectedBone()));

            model3DViewer.removeAllComponents(BonePoseComponent.class);
            bonePoser = new BonePoseComponent(view, armature, () -> manualPose);
            model3DViewer.addComponent(bonePoser);

            if (model3DViewer.getFocused() == null) model3DViewer.focus(slicer == null ? painter : slicer);

            armatureView = view;
            shownArmature = armature;
            previewEntityType = EntityPreviewModelMapping.boundTypes().stream()
                .filter(entityType -> {
                    AssetAccessor<? extends Armature> mapped = EntityPreviewModelMapping.getArmature(entityType);

                    return mapped != null && mapped.identifier().equals(armature.identifier());
                })
                .map(BuiltInRegistries.ENTITY_TYPE::getKey)
                .findFirst()
                .orElse(null);
            rebuildPreviewModel();
        }

        armatureView.setSelectedBone(holder.getPaintedBone());
        autoWeightButton.active = holder.hasMesh();
        exportButton.active = holder.hasMesh();
        addBendingButton.active = holder.hasMesh();

        if (!holder.hasMesh() && slicer != null) setSlicing(false);

        new EditorWidgetFactory(font).fillWidgetTable(informationTable, holder, this::refresh);

        if (informationRect != null) informationTable.relocate(informationRect);

        fillDetails();
    }

    /// The pose the mesh and the bones are drawn in: the preview animation's while one plays, else the one
    /// the gizmo made.
    private Pose currentPose() {
        Animator animator = previewAnimation == null || previewModel == null ? null : previewModel.getAnimator();
        Pose animated = animator == null ? null : animator.getPose();

        return animated == null ? manualPose : animated;
    }

    /// Stands the preview model of [#previewEntityType] under the mesh, playing [#previewAnimation] or held
    /// in the gizmo's pose.
    private void rebuildPreviewModel() {
        EntityPreviewModelMapping.displayOrWarn(model3DViewer, font, previewEntityType, true, false);
        previewModel = model3DViewer.getComponent(EntityModelComponent.class);

        if (previewModel == null) return;

        previewModel.setModelVisible(previewModelVisible);
        previewModel.setSimplePlayingAnimation(previewAnimation);
        previewModel.setPoseSource(previewAnimation == null ? () -> manualPose : null);
    }

    /// Puts the slice pick into the viewer in place of the painter, or the painter back.
    private void setSlicing(final boolean slicing) {
        if (slicer != null) model3DViewer.removeComponent(slicer);

        slicer = slicing
            ? new MeshSliceComponent(holder::sliceAt, slice -> openBendingModal(slice, new BendingSettings()), () -> setSlicing(false))
            : null;

        if (slicer != null) model3DViewer.addComponent(slicer);

        model3DViewer.focus(slicer == null ? painter : slicer);
    }

    /// Asks how the bending at `slice` is weighted, and adds it on confirm. A pick that changes which bones
    /// the other rows offer reopens the modal with fresh rows.
    private void openBendingModal(final ModelSkinHolder.Slice slice, final BendingSettings settings) {
        int buttonWidth = WidgetContainerModal.evenButtonWidth(BENDING_MODAL_W, 2);
        WidgetTable table = new WidgetTable(
            this,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM,
            ROW_H
        );

        new EditorWidgetFactory(font).fillWidgetTable(
            table,
            settings,
            () -> minecraft.execute(() -> {
                ModalManager.getInstance().closeModal();
                openBendingModal(slice, settings);
            }),
            holder
        );

        ModalManager.getInstance().openModalWidgetAtCenter(
            WidgetContainerModal
                .builder(font, Component.translatable(LangKeys.GUI_MODEL_SKINNING_SCREEN_ADD_BENDING), BENDING_MODAL_W)
                .singleWidgetRow(BENDING_ROWS * ROW_H + 24, table)
                .footerButton(
                    AkytheraButton
                        .buttonBuilder(font, LocalizedMessage.translatable(LangKeys.GUI_CANCEL), () -> ModalManager.getInstance().closeModal())
                        .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
                        .build(),
                    buttonWidth
                )
                .footerButton(
                    AkytheraButton
                        .buttonBuilder(font, LocalizedMessage.translatable(LangKeys.GUI_MODEL_SKINNING_SCREEN_ADD_BENDING), () -> {
                            ModalManager.getInstance().closeModal();
                            holder.addBending(slice, settings);
                            setSlicing(false);
                            refresh();
                        })
                        .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
                        .build(),
                    buttonWidth
                )
                .build()
        );
    }

    /// Applies a finished stroke to the painted bone and files the weights before and after it on the history.
    private void commitStroke(final float[] before, final float[] after) {
        List<List<ModelSkinHolder.Influence>> previous = holder.copyWeights();

        holder.paint(before, after);
        HistoryManager.getInstance().pushHistory(new DataChange<>(previous, holder.copyWeights(), weights -> {
            holder.restoreWeights(weights);
            fillDetails();
        }));
        fillDetails();
    }

    /// Picks the skeletal mesh the model is weighted from, and weights it on the pick.
    private void openReferenceMeshSelect() {
        ModalManager.getInstance().openModalWidgetAtCenter(new AccessorSelectionModal(font, AssetAccessor.Type.SKELETAL_MESH, accessor -> {
            if (accessor.getOrHandleException(AkytheraLabs.LOGGER::error) instanceof SkeletalMesh reference) {
                holder.rebuild(reference);
                refresh();
            }
        }));
    }

    /// Asks for a folder and writes the mesh as it is painted into it.
    private void export() {
        String projectMeshes = AkytheraProject.getCurrentProject().getProjectRoot()
            .resolve("assets").resolve("animmodels").resolve(AssetExportManager.MESH_DIRECTORY)
            .toString();

        CompletableFuture
            .supplyAsync(() -> TinyFileDialogs.tinyfd_selectFolderDialog("Choose Export Folder", projectMeshes))
            .thenAcceptAsync(folder -> {
                if (folder == null || !holder.hasMesh()) return;

                Path file = Path.of(folder).resolve(holder.exportName() + "." + AssetLoader.BINARY_EXTENSION);

                try {
                    Files.createDirectories(file.getParent());
                    Files.write(file, AssetExportManager.INSTANCE.encodeMesh(holder.toBuilder()));
                    FileSystemHelper.openInFileExplorer(file);
                } catch (IOException e) {
                    AkytheraLabs.LOGGER.error("Failed to export the skinned model to {}", file, e);
                }
            }, minecraft);
    }

    // ***************************************************************
    // Screen
    // ***************************************************************

    @Override
    public void handleQuitRequest() {
        SafeCloseScreen.openQuiteScreen(font, Component.empty(), false, () -> {}, this::onClose);
    }

    @Override
    public void onClose() {
        model3DViewer.destroy();
        holder.close();
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

        Ray ray = event.button() == 0
            && slicer == null
            && bonePoser != null
            && getHoveringModal(event.x(), event.y()) == null
            && model3DViewer.isMouseOver(event.x(), event.y())
            && Stream.of(viewModeButton, previewModelCheckBox, autoWeightButton, exportButton, addBendingButton)
                .noneMatch(widget -> widget.isMouseOver(event.x(), event.y()))
            ? model3DViewer.buildRay(event.x(), event.y())
            : null;

        if (ray == null) return super.mouseClicked(event, doubleClick);

        boolean overBone = bonePoser.rayTrace(model3DViewer, ray) >= 0.0F;
        boolean wasPosing = model3DViewer.getFocused() == bonePoser;

        if (!painter.isPickingVertices()) {
            // The viewer would focus a bone the brush lets go of
            return overBone && painter.rayTrace(model3DViewer, ray) < 0.0F || super.mouseClicked(event, doubleClick);
        }

        // The mesh hides the bones from the viewer's own pick, so a bone no vertex lies on is picked here
        if (!wasPosing && overBone && painter.vertexAt(model3DViewer, event.x(), event.y()) < 0) {
            model3DViewer.focus(bonePoser);

            return true;
        }

        boolean handled = super.mouseClicked(event, doubleClick);

        // A press that took the focus off the gizmo is not handed to the painter by the viewer
        if (wasPosing && model3DViewer.getFocused() == painter) painter.onMouseDown(model3DViewer, event);

        return handled;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape() && shouldCloseOnEsc()) {
            handleQuitRequest();
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
