package com.yesman.akytheralabs.gui.screen;

import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.asset.SingletonAccessor;
import com.yesman.akythera.api.asset.armature.ArmatureManager;
import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.client.api.gui.AkytheraButton;
import com.yesman.akythera.client.api.gui.WidgetTable;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalAccessibleScreen;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.client.api.gui.viewer.BackgroundComponent;
import com.yesman.akythera.client.api.gui.viewer.CameraComponent;
import com.yesman.akythera.client.api.gui.viewer.EntityModelComponent;
import com.yesman.akythera.client.api.gui.viewer.GridFloorComponent;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.core.animation.ik.IKCurves;
import com.yesman.akythera.core.animation.keyframe.AnimationClip;
import com.yesman.akythera.core.animation.keyframe.AnimationMirror;
import com.yesman.akythera.core.animation.keyframe.FloatCurve;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.loader.AnimationLoader;
import com.yesman.akythera.core.asset.loader.ResourceLoadingFailureException;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.akythera.asset.AnimationMetadataHolder;
import com.yesman.akytheralabs.akythera.physics.ArmaturePhysicsDataHolder;
import com.yesman.akytheralabs.akythera.physics.ik.IKChainHolder;
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
import com.yesman.akytheralabs.gui.widget.curve.FloatCurveEditorWidget;
import com.yesman.akytheralabs.gui.widget.main.ConsoleModal;
import com.yesman.akytheralabs.gui.widget.main.EditorPreferencesModalGenerator;
import com.yesman.akytheralabs.gui.widget.main.EntityTypeSelectModal;
import com.yesman.akytheralabs.gui.widget.main.LocalizationManagerModal;
import com.yesman.akytheralabs.gui.widget.main.TopMenuBar;
import com.yesman.akytheralabs.gui.widget.main.TopMenuOption;
import com.yesman.akytheralabs.gui.widget.viewer.ArmatureViewComponent;
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
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/// Edits an animation asset's sidecar metadata: its [AnimationLoader.ImportType], for an additive import the reference
/// pose the keyframes are read relative to, and the [IKCurves] of the vendor armature's IK chains. The IK Curves panel
/// lists one row per chain and curve; the picked one is edited in the Curve Editor under the preview.
public final class AnimationAssetEditorScreen extends Screen implements ModalAccessibleScreen, SafeCloseScreen {
    private static final int TOP_BAR_H = 22;
    private static final int ROW_H = 20;

    private final AdjustablePanelManager panelManager;
    private final TopMenuBar topMenuBar;
    private final Model3DViewer model3DViewer;
    private final WidgetTable propertiesTable;
    private final PanelHeader propertiesHeader;

    /// Switches the previewer between the clip on disk and its reflection.
    private final AkytheraButton mirrorPreviewButton;

    /// The sidecar view, taken off the accessor so both screens on the same animation share one —
    /// and so the preview model chosen here survives a close and reopen.
    private final AnimationMetadataHolder holder;

    private @Nullable WorkHistory savePoint;

    /// Whether the previewer is playing the mirrored bake rather than the source clip.
    private boolean previewingMirror;

    /// Set when the View menu picks a preview model. That write lands on the holder rather than on
    /// [HistoryManager], so it carries its own unsaved-changes bit.
    private boolean previewModelDirty;

    private final PanelHeader curveListHeader;

    /// One row per curve of each IK chain of the vendor armature, and of each chain the sidecar holds curves for.
    private final WidgetTable curveListTable;

    private final PanelHeader curveEditorHeader;
    private final FloatCurveEditorWidget curveEditor;
    private @Nullable ScreenRectangle curveListRect;

    /// The curve [#curveEditor] edits, null while none is picked.
    private @Nullable CurvePick pickedCurve;

    /// The vendor armature [#curveListTable] was filled for.
    private @Nullable AssetAccessor<? extends Armature> listedVendor;

    /// The previewed model's bones, shown while a curve is picked. Null while none is.
    private @Nullable ArmatureViewComponent armatureView;

    public AnimationAssetEditorScreen(AkytheraProject.EditorAnimationAccessor accessor) {
        super(Component.translatable(LangKeys.GUI_ANIMATION_ASSET_SCREEN_TITLE));

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
                .on(TopMenuOption.SELECT_PREVIEW_MODEL, this::openPreviewModelSelectModal)
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

        mirrorPreviewButton = AkytheraButton
            .buttonBuilder(minecraft.font, LocalizedMessage.EMPTY, () -> toggleMirrorPreview())
            .xUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(0))
            .yUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.TOP_HEIGHT)
            .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
            .build();

        mirrorPreviewButton.setTooltip(
            Tooltip.create(Component.translatable(LangKeys.GUI_MESSAGE_MIRROR_PREVIEW_NEEDS_VENDOR)));

        EntityPreviewModelMapping.displayOrWarn(model3DViewer, font, holder.getPreviewEntityType(), true, true);
        refreshPreviewAnimation();

        propertiesHeader = new PanelHeader(minecraft.font, Component.translatable(LangKeys.GUI_WIDGET_DETAILS));

        propertiesTable = new WidgetTable(
            this,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(PanelHeader.DEFAULT_HEADER_HEIGHT + 2), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM,
            ROW_H
        );

        new EditorWidgetFactory(minecraft.font).fillWidgetTable(propertiesTable, holder);

        propertiesTable.newRow().addWidget(mirrorPreviewButton);

        curveListHeader = new PanelHeader(minecraft.font, Component.translatable(LangKeys.GUI_ANIMATION_ASSET_SCREEN_SECTION_IK_CURVES));
        curveListTable = new WidgetTable(
            this,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(PanelHeader.DEFAULT_HEADER_HEIGHT + 2), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM,
            ROW_H
        );

        curveEditorHeader = new PanelHeader(minecraft.font, Component.translatable(LangKeys.GUI_ANIMATION_ASSET_SCREEN_SECTION_CURVE_EDITOR));
        curveEditor = new FloatCurveEditorWidget(
            minecraft,
            () -> {
                IKCurves curves = pickedCurve == null ? null : holder.getIKCurves(pickedCurve.tipBone());

                return curves == null ? null : pickedCurve.plant() ? curves.plant() : curves.alpha();
            },
            curve -> {
                if (pickedCurve == null) return;

                IKCurves before = holder.getIKCurves(pickedCurve.tipBone());
                FloatCurve alpha = pickedCurve.plant() ? (before == null ? null : before.alpha()) : curve;
                FloatCurve plant = pickedCurve.plant() ? curve : (before == null ? null : before.plant());
                boolean hadCurve = (pickedCurve.plant() ? (before == null ? null : before.plant()) : (before == null ? null : before.alpha())) != null;
                holder.setIKCurves(pickedCurve.tipBone(), new IKCurves(alpha, plant));

                // A row marks whether its curve exists, so the list follows when one appears or goes
                if (hadCurve != (curve != null)) fillCurveList();
            },
            () -> pickedCurve != null,
            () -> {
                AnimationClip clip = holder.getAccessor().getOrHandleException(_ -> {});

                return clip == null ? 0.0F : clip.getTotalTime();
            },
            this::previewAnimator,
            Component.translatable(LangKeys.GUI_ANIMATION_ASSET_SCREEN_MESSAGE_PICK_CURVE),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(PanelHeader.DEFAULT_HEADER_HEIGHT), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM
        );

        panelManager = new AdjustablePanelManager(
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(TOP_BAR_H), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM
        );

        // Divider order (== serialize order): columns(0), preview | curve editor(1), details | IK curves(2)
        List<Integer> layoutRatios = AkytheraProject.getCurrentProject().getLayoutRatioOrDefault(getClass(), 70, 65, 55);

        var leftAndRight = panelManager.getRoot()
            .subdivide(1, AdjustablePanelManager.PanelDivider.Orientation.VERTICAL, layoutRatios.get(0));
        var previewAndCurve = leftAndRight.get(0)
            .subdivide(1, AdjustablePanelManager.PanelDivider.Orientation.HORIZONTAL, layoutRatios.get(1));
        var detailsAndCurves = leftAndRight.get(1)
            .subdivide(1, AdjustablePanelManager.PanelDivider.Orientation.HORIZONTAL, layoutRatios.get(2));

        previewAndCurve.get(0).resizeCallback(model3DViewer::relocate);
        previewAndCurve.get(0).resizeFinishCallback(_ -> model3DViewer.onResizeCompleted());
        previewAndCurve.get(1).resizeCallback(rect -> {
            curveEditorHeader.relocate(rect);
            curveEditor.relocate(rect);
        });
        detailsAndCurves.get(0).resizeCallback(rect -> {
            propertiesHeader.relocate(rect);
            propertiesTable.relocate(rect);
        });
        detailsAndCurves.get(1).resizeCallback(rect -> {
            curveListRect = rect;
            curveListHeader.relocate(rect);
            curveListTable.relocate(rect);
        });

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
        addRenderableWidget(propertiesHeader);
        addRenderableWidget(propertiesTable);
        addRenderableWidget(curveListHeader);
        addRenderableWidget(curveListTable);
        addRenderableWidget(curveEditorHeader);
        addRenderableWidget(curveEditor);

        fillCurveList();
    }

    /// The physics asset of the vendor armature, null while no vendor is picked.
    private @Nullable ArmaturePhysicsDataHolder vendorPhysics() {
        AssetAccessor<? extends Armature> vendor = holder.getVendor();
        Identifier physicsId = vendor == ArmatureManager.EMPTY_ARMATURE_ACCESSOR ? null : AkytheraProject.armaturePhysicsIdOf(vendor.identifier());

        return physicsId == null ? null : AkytheraProject.getCurrentProject().getOrIssueEditorArmaturePhysicsAccessor(physicsId).metadata();
    }

    /// The previewer's animator, null while the previewer shows no model that plays the clip.
    private EntityModelComponent.@Nullable EditorScrubAnimator previewAnimator() {
        EntityModelComponent component = model3DViewer.getComponent(EntityModelComponent.class);

        return component != null && component.getAnimator() instanceof EntityModelComponent.EditorScrubAnimator animator ? animator : null;
    }

    /// Two rows per IK chain of the vendor armature, and per chain the sidecar holds curves for: its IK Alpha and its
    /// Plant curve. A row picks its curve for the Curve Editor, marked with ● while the curve exists, and its ✕
    /// removes the curve. A Plant row's ⇓ fills the curve with [IKCurves#detectPlant].
    private void fillCurveList() {
        listedVendor = holder.getVendor();
        curveListTable.clearWidgets();

        Set<String> tipBones = new LinkedHashSet<>();
        ArmaturePhysicsDataHolder physics = vendorPhysics();

        if (physics != null) {
            physics.getIKChains().stream().map(IKChainHolder::getTipBone).filter(Objects::nonNull).forEach(tipBones::add);
        }

        tipBones.addAll(holder.getIKCurves().keySet());

        for (String tipBone : tipBones) {
            for (boolean plant : new boolean[] { false, true }) {
                CurvePick pick = new CurvePick(tipBone, plant);
                IKCurves curves = holder.getIKCurves(tipBone);
                boolean exists = curves != null && (plant ? curves.plant() : curves.alpha()) != null;
                Component label = Component.literal((exists ? "● " : "") + tipBone + " · ")
                    .append(Component.translatable(plant ? LangKeys.GUI_ANIMATION_ASSET_SCREEN_WIDGET_PLANT : LangKeys.GUI_ANIMATION_ASSET_SCREEN_WIDGET_IK_ALPHA));

                AkytheraButton pickButton = AkytheraButton
                    .buttonBuilder(font, LocalizedMessage.handwritten(label.getString()), () -> {
                        pickedCurve = pick;
                        refreshArmatureView();
                    })
                    .xUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(plant ? 2 * (ROW_H + 2) : ROW_H + 2))
                    .yUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H))
                    .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT)
                    .verticalAnchorType(AnchoredWidget.VerticalAnchorType.TOP_HEIGHT)
                    .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
                    .isHighlighted(_ -> pick.equals(pickedCurve))
                    .build();

                AkytheraButton removeButton = AkytheraButton
                    .buttonBuilder(font, LocalizedMessage.handwritten("✕"), () -> {
                        IKCurves before = holder.getIKCurves(tipBone);

                        if (before == null) return;

                        IKCurves after = plant ? new IKCurves(before.alpha(), null) : new IKCurves(null, before.plant());
                        Consumer<IKCurves> setter = written -> {
                            holder.setIKCurves(tipBone, written);
                            fillCurveList();
                        };

                        setter.accept(after);
                        HistoryManager.getInstance().pushHistory(new DataChange<>(before, after, setter));
                    })
                    .xUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H))
                    .yUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H))
                    .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH)
                    .verticalAnchorType(AnchoredWidget.VerticalAnchorType.TOP_HEIGHT)
                    .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
                    .tooltip(_ -> Tooltip.create(Component.translatable(LangKeys.GUI_ANIMATION_ASSET_SCREEN_MESSAGE_REMOVE_CURVE)))
                    .build();
                removeButton.active = exists;

                curveListTable.newRow().addWidget(pickButton).addWidget(removeButton);

                if (!plant) continue;

                AkytheraButton detectButton = AkytheraButton
                    .buttonBuilder(font, LocalizedMessage.handwritten("⇓"), () -> {
                        AnimationClip clip = holder.getAccessor().getOrHandleException(AkytheraLabs.LOGGER::error);
                        FloatCurve detected = clip == null ? null : IKCurves.detectPlant(clip, holder.getVendor(), tipBone);

                        if (detected == null) return;

                        IKCurves before = holder.getIKCurves(tipBone);
                        IKCurves after = new IKCurves(before == null ? null : before.alpha(), detected);
                        Consumer<IKCurves> setter = written -> {
                            holder.setIKCurves(tipBone, written);
                            fillCurveList();
                        };

                        setter.accept(after);
                        HistoryManager.getInstance().pushHistory(new DataChange<>(before, after, setter));
                    })
                    .xUnits(AnchoredWidget.pixel(ROW_H + 2), AnchoredWidget.pixel(ROW_H))
                    .yUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H))
                    .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH)
                    .verticalAnchorType(AnchoredWidget.VerticalAnchorType.TOP_HEIGHT)
                    .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
                    .tooltip(_ -> Tooltip.create(Component.translatable(LangKeys.GUI_ANIMATION_ASSET_SCREEN_MESSAGE_DETECT_PLANT)))
                    .build();

                curveListTable.addWidget(detectButton);
            }
        }

        if (curveListRect != null) curveListTable.relocate(curveListRect);
    }

    /// Draws the previewed model's bones while a curve is picked, filling those of the picked curve's chain. A chain
    /// the vendor's physics asset does not hold fills its tip bone alone.
    private void refreshArmatureView() {
        if (armatureView != null) model3DViewer.removeComponent(armatureView);

        armatureView = null;

        EntityModelComponent component = model3DViewer.getComponent(EntityModelComponent.class);
        AssetAccessor<? extends Armature> armature = component == null ? null : component.getArmature();

        if (pickedCurve == null || armature == null) return;

        ArmatureViewComponent view = new ArmatureViewComponent(armature);
        view.setTransformSource(() -> component.getArmatureComponent().modelTransform());
        view.setPoseSource(() -> component.getAnimator() == null ? null : component.getAnimator().getPose());
        view.setFilledBones(() -> {
            Armature drawn = armature.getOrHandleException(_ -> {}); // the view reports the failure
            ArmaturePhysicsDataHolder physics = vendorPhysics();
            CurvePick pick = pickedCurve;

            if (drawn == null || pick == null) return Set.of();

            IKChainHolder chain = physics == null ? null
                : physics.getIKChains().stream().filter(c -> pick.tipBone().equals(c.getTipBone())).findFirst().orElse(null);

            return chain == null ? Set.of(pick.tipBone()) : chain.boneNames(drawn);
        });

        armatureView = view;
        model3DViewer.addComponent(view);
    }

    /// Refills the IK Curves panel when the vendor changes.
    @Override
    public void extractRenderState(final @NonNull GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float partialTick) {
        if (listedVendor != holder.getVendor()) fillCurveList();

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void openPreviewModelSelectModal() {
        EntityTypeSelectModal modal = new EntityTypeSelectModal(font, holder.getPreviewEntityType(), newEntityType -> {
            Identifier oldEntityType = holder.getPreviewEntityType();
            if (Objects.equals(oldEntityType, newEntityType)) return;

            applyPreviewEntityType(newEntityType);
            HistoryManager.getInstance().pushHistory(
                new DataChange<>(oldEntityType, newEntityType, this::applyPreviewEntityType));
        });

        ModalManager.getInstance().openModalWidgetAtCenter(modal);
    }

    /// Installs `model` as the previewed entity: the holder field, the viewer, and the clip replayed
    /// on the entity the viewer built. Undo and redo replay it.
    private void applyPreviewEntityType(@Nullable Identifier entityType) {
        holder.setPreviewEntityType(entityType);
        previewModelDirty = true;
        EntityPreviewModelMapping.displayOrWarn(model3DViewer, font, entityType, true, true);
        refreshPreviewAnimation();
        refreshArmatureView();
    }

    /// Plays the reflection in place of the source clip, or the source back. A vendor is what the
    /// bake moves tracks along, so with the slot empty there is nothing to switch to.
    private void toggleMirrorPreview() {
        if (!previewingMirror && holder.getVendor() == ArmatureManager.EMPTY_ARMATURE_ACCESSOR) {
            return;
        }

        previewingMirror = !previewingMirror;
        refreshPreviewAnimation();
    }

    /// The mirrored clip as something the previewer can play, baked on the spot. Null when the
    /// armature or the clip cannot be read, which leaves the source playing.
    private @Nullable AssetAccessor<AnimationClip> bakeMirror() {
        Identifier sourceId = holder.getAccessor().identifier();

        try {
            return new SingletonAccessor<>(
                Identifier.fromNamespaceAndPath(
                    sourceId.getNamespace(), sourceId.getPath() + AnimationMirror.IDENTIFIER_SUFFIX),
                AnimationMirror.mirror(holder.getAccessor().get(), holder.getVendor().get()),
                AssetAccessor.Type.ANIMATION
            );
        } catch (ResourceLoadingFailureException e) {
            AkytheraLabs.LOGGER.error("Failed at baking the mirror of '{}'", sourceId, e);

            return null;
        }
    }

    /// (Re)plays the edited clip on the previewer's current entity component. Safe to call after any
    /// `displayEntity` — the freshly created component is fetched from the previewer.
    private void refreshPreviewAnimation() {
        EntityModelComponent component = model3DViewer.getComponent(EntityModelComponent.class);
        AssetAccessor<AnimationClip> mirror = previewingMirror ? bakeMirror() : null;
        previewingMirror = mirror != null;

        if (component != null) {
            component.setSimplePlayingAnimation(previewingMirror ? mirror : holder.getAccessor());
        }

        mirrorPreviewButton.setMessage(Component.translatable(previewingMirror
            ? LangKeys.GUI_ANIMATION_ASSET_SCREEN_BUTTON_PREVIEW_SOURCE
            : LangKeys.GUI_ANIMATION_ASSET_SCREEN_BUTTON_PREVIEW_MIRROR));
    }

    private void save() {
        if (!holder.save()) return;

        previewModelDirty = false;
        refreshPreviewAnimation();
        savePoint = HistoryManager.getInstance().currentSavePoint();
    }

    private boolean hasUnsavedChanges() {
        return previewModelDirty || HistoryManager.getInstance().currentSavePoint() != savePoint;
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
        // No background of its own; the per-frame hook the vendor slot is polled on
        mirrorPreviewButton.active = previewingMirror || holder.getVendor() != ArmatureManager.EMPTY_ARMATURE_ACCESSOR;
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
    /// One curve of one IK chain: the chain's tip bone, and whether the curve is its Plant curve rather than its IK
    /// Alpha.
    private record CurvePick(String tipBone, boolean plant) {}
}
