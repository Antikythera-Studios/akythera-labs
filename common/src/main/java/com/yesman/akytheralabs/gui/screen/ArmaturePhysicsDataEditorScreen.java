package com.yesman.akytheralabs.gui.screen;

import com.yesman.akythera.client.api.gui.WidgetTable;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalAccessibleScreen;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.client.api.gui.viewer.BackgroundComponent;
import com.yesman.akythera.client.api.gui.viewer.CameraComponent;
import com.yesman.akythera.client.api.gui.viewer.EntityModelComponent;
import com.yesman.akythera.client.api.gui.viewer.GridFloorComponent;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.armature.Bone;
import com.yesman.akythera.core.math.Transform;
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
import com.yesman.akytheralabs.gui.widget.EditorComboBox;
import com.yesman.akytheralabs.gui.widget.PanelHeader;
import com.yesman.akytheralabs.gui.widget.main.ConsoleModal;
import com.yesman.akytheralabs.gui.widget.main.EditorPreferencesModalGenerator;
import com.yesman.akytheralabs.gui.widget.main.EntityTypeSelectModal;
import com.yesman.akytheralabs.gui.widget.main.LocalizationManagerModal;
import com.yesman.akytheralabs.gui.widget.main.TopMenuBar;
import com.yesman.akytheralabs.gui.widget.main.TopMenuOption;
import com.yesman.akytheralabs.gui.widget.viewer.ArmatureViewComponent;
import com.yesman.akytheralabs.gui.widget.viewer.ColliderPreviewComponent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/// Edits the bone bodies of one armature: the colliders each body carries, picked by bone in the
/// Collider Properties panel or by clicking the armature in the viewer.
///
/// The armature is the asset's own path (invariant 7), so this screen never picks one — it opens on
/// whatever the file belongs to.
public final class ArmaturePhysicsDataEditorScreen extends Screen implements ModalAccessibleScreen, SafeCloseScreen {
    private static final int TOP_BAR_H = 22;
    private static final int ROW_H = 20;
    private static final int COMBO_H = 14;
    private static final int ROW_PAD = 6;

    private final AdjustablePanelManager panelManager;
    private final TopMenuBar topMenuBar;
    private final Model3DViewer model3DViewer;
    private final PanelHeader header;

    /// The selected bone's body.
    private final WidgetTable bodyTable;

    /// The asset view, taken off the accessor so a reopened screen continues the same edit.
    private final ArmaturePhysicsDataHolder holder;

    private @Nullable EditorComboBox<String> boneCombo;
    private @Nullable String selectedBone;
    private @Nullable ScreenRectangle panelRect;

    private @Nullable ArmatureViewComponent armatureView;

    private final List<ColliderPreviewComponent> colliderPreviews = new ArrayList<>();

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

        panelManager = new AdjustablePanelManager(
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(TOP_BAR_H), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM
        );

        List<Integer> layoutRatios = AkytheraProject.getCurrentProject().getLayoutRatioOrDefault(getClass(), 28);
        var leftAndRight = panelManager.getRoot()
            .subdivide(1, AdjustablePanelManager.PanelDivider.Orientation.VERTICAL, layoutRatios.getFirst());

        leftAndRight.get(0).resizeCallback(rect -> {
            panelRect = rect;
            header.relocate(rect);
            bodyTable.relocate(rect);

            if (boneCombo != null) boneCombo.relocate(rect);
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

        rebuildAll();
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

    /// One collider preview per collider, placed by its bone's bind transform.
    private void rebuildScene() {
        colliderPreviews.forEach(model3DViewer::removeComponent);
        colliderPreviews.clear();

        Armature armature = holder.getArmature().getOrHandleException(AkytheraLabs.LOGGER::error);

        if (armature == null) return;

        Map<String, Transform> bindTransforms = new HashMap<>();
        collectBindTransforms(armature.getRootBone(), new Matrix4f(), bindTransforms);

        for (Map.Entry<String, BoneBodyHolder> body : holder.getBodies().entrySet()) {
            String bone = body.getKey();
            Transform placement = bindTransforms.get(bone);

            if (placement == null) continue;

            for (ColliderComponentHolder collider : body.getValue().getColliders()) {
                ColliderPreviewComponent colliderPreview = bone.equals(selectedBone)
                    ? ColliderPreviewComponent.editable(collider, () -> placement, this::fillBodyTable, _ -> {})
                    : ColliderPreviewComponent.inherited(collider, () -> placement, _ -> selectBone(bone));

                colliderPreviews.add(colliderPreview);
                model3DViewer.addComponent(colliderPreview);
            }
        }
    }

    /// Walks the bind pose the way [Bone#initOriginTransform] does, recording each bone's model
    /// transform.
    private static void collectBindTransforms(final Bone bone, final Matrix4f parentTransform, final Map<String, Transform> out) {
        Matrix4f modelTransform = parentTransform.mul(bone.getLocalTransform(), new Matrix4f());
        out.put(bone.getName(), Transform.fromMatrix(modelTransform));

        for (Bone child : bone.getChildBones()) {
            collectBindTransforms(child, modelTransform, out);
        }
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
