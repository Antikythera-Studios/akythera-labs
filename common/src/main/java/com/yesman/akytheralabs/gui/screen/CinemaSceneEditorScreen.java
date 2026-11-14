package com.yesman.akytheralabs.gui.screen;

import com.mojang.blaze3d.platform.Window;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalAccessibleScreen;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.client.api.gui.viewer.BackgroundComponent;
import com.yesman.akythera.client.api.gui.viewer.CameraComponent;
import com.yesman.akythera.client.api.gui.viewer.EntityModelComponent;
import com.yesman.akythera.client.api.gui.viewer.GridFloorComponent;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.core.cinema.CinemaScene;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.akythera.cinema.CinemaActorHolder;
import com.yesman.akytheralabs.akythera.cinema.CinemaSceneHolder;
import com.yesman.akytheralabs.config.AkytheraLabsConfig;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.WorkHistory;
import com.yesman.akytheralabs.editor.keybind.EditorKeyMappings;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.AdjustablePanelManager;
import com.yesman.akytheralabs.gui.panel.components.ComponentDetailsPanel;
import com.yesman.akytheralabs.gui.widget.AwaitIconModal;
import com.yesman.akytheralabs.gui.widget.WidgetContainerModal;
import com.yesman.akytheralabs.gui.widget.cinema.CinemaTrackPanel;
import com.yesman.akytheralabs.gui.widget.main.ConsoleModal;
import com.yesman.akytheralabs.gui.widget.main.EditorPreferencesModalGenerator;
import com.yesman.akytheralabs.gui.widget.main.LanguageSelectionModal;
import com.yesman.akytheralabs.gui.widget.main.LocalizationManagerModal;
import com.yesman.akytheralabs.gui.widget.main.McpServerModalGenerator;
import com.yesman.akytheralabs.gui.widget.main.ProjectTagsModal;
import com.yesman.akytheralabs.gui.widget.main.TopMenuBar;
import com.yesman.akytheralabs.gui.widget.main.TopMenuOption;
import com.yesman.akytheralabs.gui.widget.viewer.CinemaActorComponent;
import com.yesman.akytheralabs.gui.widget.viewer.CinemaCameraComponent;
import com.yesman.akytheralabs.gui.widget.viewer.CinemaScenePreviewComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/// Edits one cinema scene: the preview over the cinema track on the left, and the Details of the selected actor, or
/// of the scene with none selected, on the right.
///
/// The preview draws each actor's model where the scene places it at the playhead, with its montage's root motion
/// path. The selected actor's camera shows as a pyramid, and its view in an inset.
public final class CinemaSceneEditorScreen extends Screen implements ModalAccessibleScreen, SafeCloseScreen {
    private static final int TOP_BAR_H = 22;

    /// The volume the camera frames, in blocks.
    private static final float FRAMED_WIDTH = 4.0F;
    private static final float FRAMED_HEIGHT = 3.0F;

    private final AdjustablePanelManager panelManager;
    private final TopMenuBar topMenuBar;
    private final Model3DViewer model3DViewer;
    private final ComponentDetailsPanel detailsPanel;
    private final CinemaTrackPanel trackPanel;
    private final CinemaSceneHolder holder;

    private final ResourceKey<CinemaScene> resourceKey;

    /// History stack top at the last save (or screen open); the title shows a `*` while the live top differs from it.
    private @Nullable WorkHistory savePoint;

    public CinemaSceneEditorScreen(final ResourceKey<CinemaScene> resourceKey, final CinemaSceneHolder holder) {
        super(Component.translatable(LangKeys.GUI_CINEMA_SCENE_SCREEN_TITLE));

        this.resourceKey = resourceKey;
        this.holder = holder;

        topMenuBar = new TopMenuBar(
            minecraft.font,
            Component.literal(resourceKey.identifier().toString()),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(TOP_BAR_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            () -> handleQuitRequest(),
            TopMenuBar.actions()
                .on(TopMenuOption.LOCALIZATION, () -> LocalizationManagerModal.open(minecraft.font))
                .on(TopMenuOption.PROJECT_TAGS, () -> ProjectTagsModal.open(minecraft.font))
                .on(TopMenuOption.SAVE, this::save)
                .on(TopMenuOption.LANGUAGE, () -> LanguageSelectionModal.open(minecraft.font))
                .on(TopMenuOption.EDITOR_PREFERENCES, () -> EditorPreferencesModalGenerator.open(minecraft.font))
                .on(TopMenuOption.FRAME_CAMERA, this::frameCamera)
                .on(TopMenuOption.CONSOLE, () -> ConsoleModal.open(minecraft.font))
                .on(TopMenuOption.OPEN_MCP, () -> McpServerModalGenerator.open(minecraft.font))
                .on(TopMenuOption.DOCS, () -> {})
                .on(TopMenuOption.ISSUE_REPORT, () -> {})
        );

        model3DViewer = new Model3DViewer(
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM,
            AkytheraEditorLayoutManager.getInstance()::getLayoutGuiScale
        );

        CinemaScenePreviewComponent preview = new CinemaScenePreviewComponent(holder);

        model3DViewer.addComponent(new CameraComponent(true));
        model3DViewer.addComponent(new BackgroundComponent(16, 16, 16, 255));
        model3DViewer.addComponent(new GridFloorComponent(1.0F, 16, 68, 68, 68, 255));
        model3DViewer.addComponent(preview);

        detailsPanel = new ComponentDetailsPanel(minecraft.font, this).viewer(model3DViewer);

        trackPanel = new CinemaTrackPanel(holder, preview, actor -> {
            CinemaCameraComponent camera = null;

            for (int i = 0; model3DViewer.getComponent(CinemaCameraComponent.class, i) instanceof CinemaCameraComponent shown; i++) {
                if (shown.getActor() == actor) camera = shown;
            }

            // A picked actor focuses its arrow, unless its arrow or its camera holds the focus
            if (actor == null) {
                if (model3DViewer.getFocused() instanceof CinemaActorComponent || model3DViewer.getFocused() instanceof CinemaCameraComponent) {
                    model3DViewer.focus(null);
                }
            } else if (model3DViewer.getFocused() != camera) {
                for (int i = 0; model3DViewer.getComponent(CinemaActorComponent.class, i) instanceof CinemaActorComponent shown; i++) {
                    if (shown.getActor() == actor && model3DViewer.getFocused() != shown) model3DViewer.focus(shown);
                }
            }

            if (camera == null) {
                model3DViewer.setInsetView(null);
            } else {
                Window window = Minecraft.getInstance().getWindow();

                model3DViewer.setInsetView(new Model3DViewer.InsetView(
                    camera::worldTransform,
                    Minecraft.getInstance().options.fov().get(),
                    (float)window.getWidth() / Math.max(1, window.getHeight()),
                    shown -> shown instanceof EntityModelComponent || shown instanceof GridFloorComponent,
                    Component.translatable(LangKeys.GUI_CINEMA_SCENE_SCREEN_CAMERA_PREVIEW)
                ));
            }

            showDetails(actor);
        }, this::showDetails, panel -> {
            List<CinemaActorComponent> actors = new ArrayList<>();

            for (int i = 0; model3DViewer.getComponent(CinemaActorComponent.class, i) instanceof CinemaActorComponent shown; i++) {
                actors.add(shown);
            }

            // The arrows and the cameras follow the actors
            for (CinemaActorComponent shown : actors) {
                if (holder.getActors().contains(shown.getActor())) continue;

                for (int i = 0; model3DViewer.getComponent(CinemaCameraComponent.class, i) instanceof CinemaCameraComponent camera; i++) {
                    if (camera.getActor() == shown.getActor()) model3DViewer.removeComponent(camera);
                }

                shown.removeFrom(model3DViewer);
                model3DViewer.removeComponent(shown);
            }

            for (CinemaActorHolder actor : holder.getActors()) {
                if (actors.stream().anyMatch(shown -> shown.getActor() == actor)) continue;

                CinemaActorComponent added = new CinemaActorComponent(
                    actor,
                    preview,
                    picked -> {
                        if (panel.getSelected() != picked) panel.setSelected(picked);
                    },
                    panel::refreshSelectionView,
                    () -> panel.getSelected() == actor
                );

                model3DViewer.addComponent(added);
                model3DViewer.addComponent(new CinemaCameraComponent(added, panel::refreshSelectionView, () -> panel.getSelected() == actor));
            }
        });

        detailsPanel.showComponent(holder);

        panelManager = new AdjustablePanelManager(
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(TOP_BAR_H), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM
        );

        // Divider order: columns (0), left split (1)
        List<Integer> ratios = AkytheraProject.getCurrentProject().getLayoutRatioOrDefault(getClass(), 72, 60);
        List<AdjustablePanelManager.PanelSection.SetupProxy> columns = panelManager.getRoot()
            .subdivide(1, AdjustablePanelManager.PanelDivider.Orientation.VERTICAL, ratios.get(0));
        List<AdjustablePanelManager.PanelSection.SetupProxy> leftSplit = columns.get(0)
            .subdivide(1, AdjustablePanelManager.PanelDivider.Orientation.HORIZONTAL, ratios.get(1));

        leftSplit.get(0).resizeCallback(model3DViewer::relocate);
        leftSplit.get(0).resizeFinishCallback(_ -> model3DViewer.onResizeCompleted());
        leftSplit.get(1).area(trackPanel);
        columns.get(1).area(detailsPanel);

        topMenuBar.setDirtyChecker(this::hasUnsavedChanges);
        savePoint = HistoryManager.getInstance().currentSavePoint();
    }

    /// Shows `actor`'s rows in Details, or the scene's rows for null.
    private void showDetails(final @Nullable CinemaActorHolder actor) {
        detailsPanel.showComponent(actor == null ? holder : actor);
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

        frameCamera();
    }

    private void frameCamera() {
        model3DViewer.frameCamera(FRAMED_WIDTH, FRAMED_HEIGHT);
    }

    // ***************************************************************
    // Save
    // ***************************************************************

    private CompletableFuture<Void> save() {
        WidgetContainerModal awaitModal = AwaitIconModal.openSaving(minecraft.font);

        return AkytheraProject.getCurrentProject().saveEntryAsync(resourceKey)
            .whenCompleteAsync((_, error) -> {
                if (ModalManager.getInstance().getModal() == awaitModal) {
                    ModalManager.getInstance().closeModal();
                }

                if (error != null) {
                    AkytheraLabs.LOGGER.error("Failed to save entry '{}'", resourceKey.identifier(), error);
                    return;
                }

                savePoint = HistoryManager.getInstance().currentSavePoint();
            }, minecraft);
    }

    private boolean hasUnsavedChanges() {
        return HistoryManager.getInstance().currentSavePoint() != savePoint;
    }

    // ***************************************************************
    // Screen lifecycle / input
    // ***************************************************************

    @Override
    public void handleQuitRequest() {
        SafeCloseScreen.openQuiteScreen(
            minecraft.font,
            Component.translatable(LangKeys.GUI_MESSAGE_ASK_TO_SAVE, topMenuBar.getMessage()),
            hasUnsavedChanges(),
            () -> {
                ModalManager.getInstance().closeModal();
                save().thenRun(this::onClose);
            },
            () -> {
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

        if (EditorKeyMappings.INSERT_KEYFRAME.matches(event)) {
            int scale = AkytheraLabsConfig.getEditorGuiScale();
            int mouseX = (int)Math.round(minecraft.mouseHandler.xpos() / scale);
            int mouseY = (int)Math.round(minecraft.mouseHandler.ypos() / scale);

            if ((trackPanel.isMouseOver(mouseX, mouseY) || model3DViewer.isMouseOver(mouseX, mouseY)) && trackPanel.insertCameraKey()) return true;
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

    /// The modal owning focus, through [ModalAccessibleScreen#getFocusOwningModal], so focus is released when it
    /// closes.
    @Override
    public @Nullable GuiEventListener getFocused() {
        AbstractWidget modal = getFocusOwningModal();
        if (modal != null) return modal;

        return super.getFocused();
    }
}
