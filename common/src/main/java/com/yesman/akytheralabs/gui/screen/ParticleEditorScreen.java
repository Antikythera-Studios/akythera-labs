package com.yesman.akytheralabs.gui.screen;

import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalAccessibleScreen;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.client.api.gui.viewer.BackgroundComponent;
import com.yesman.akythera.client.api.gui.viewer.CameraComponent;
import com.yesman.akythera.client.api.gui.viewer.GridFloorComponent;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.core.blueprint.particle.ParticleBlueprint;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.akythera.particle.ParticleBlueprintHolder;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.WorkHistory;
import com.yesman.akytheralabs.editor.keybind.EditorKeyMappings;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.AdjustablePanelManager;
import com.yesman.akytheralabs.gui.panel.components.ComponentDetailsPanel;
import com.yesman.akytheralabs.gui.widget.AwaitIconModal;
import com.yesman.akytheralabs.gui.widget.WidgetContainerModal;
import com.yesman.akytheralabs.gui.widget.main.ConsoleModal;
import com.yesman.akytheralabs.gui.widget.main.EditorPreferencesModalGenerator;
import com.yesman.akytheralabs.gui.widget.main.LanguageSelectionModal;
import com.yesman.akytheralabs.gui.widget.main.LocalizationManagerModal;
import com.yesman.akytheralabs.gui.widget.main.TopMenuBar;
import com.yesman.akytheralabs.gui.widget.main.TopMenuOption;
import com.yesman.akytheralabs.gui.widget.viewer.ParticlePreviewComponent;
import com.yesman.akytheralabs.gui.widget.viewer.ParticleTransformComponent;
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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/// Edits one particle piece: a preview playing it on the left, and the holder's Details on the right.
public final class ParticleEditorScreen extends Screen implements ModalAccessibleScreen, SafeCloseScreen {
    private static final int TOP_BAR_H = 22;

    /// The volume the camera frames, in blocks: the spawn point at one block up sits in its middle.
    private static final float FRAMED_WIDTH = 1.0F;
    private static final float FRAMED_HEIGHT = 2.0F;

    private final AdjustablePanelManager panelManager;
    private final TopMenuBar topMenuBar;
    private final Model3DViewer model3DViewer;
    private final ComponentDetailsPanel detailsPanel;

    private final ResourceKey<ParticleBlueprint> resourceKey;

    /// History stack top at the last save (or screen open); the title shows a `*` while the live top differs from it.
    private @Nullable WorkHistory savePoint;

    public ParticleEditorScreen(final ResourceKey<ParticleBlueprint> resourceKey, final ParticleBlueprintHolder holder) {
        super(Component.translatable(LangKeys.GUI_PARTICLE_EDITOR_SCREEN_TITLE));

        this.resourceKey = resourceKey;

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
                .on(TopMenuOption.SAVE, this::save)
                .on(TopMenuOption.LANGUAGE, () -> LanguageSelectionModal.open(minecraft.font))
                .on(TopMenuOption.EDITOR_PREFERENCES, () -> EditorPreferencesModalGenerator.open(minecraft.font))
                .on(TopMenuOption.FRAME_CAMERA, this::frameCamera)
                .on(TopMenuOption.CONSOLE, () -> ConsoleModal.open(minecraft.font))
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

        model3DViewer.addComponent(new CameraComponent(true));
        model3DViewer.addComponent(new BackgroundComponent(16, 16, 16, 255));
        model3DViewer.addComponent(new GridFloorComponent(1.0F, 16, 68, 68, 68, 255));

        detailsPanel = new ComponentDetailsPanel(minecraft.font, this).viewer(model3DViewer);
        detailsPanel.showComponent(holder);

        model3DViewer.addComponent(new ParticlePreviewComponent(holder, () -> detailsPanel.showComponent(holder)));
        model3DViewer.addComponent(new ParticleTransformComponent(holder, () -> detailsPanel.showComponent(holder)));

        panelManager = new AdjustablePanelManager(
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(TOP_BAR_H), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM
        );

        List<Integer> layoutRatios = AkytheraProject.getCurrentProject().getLayoutRatioOrDefault(getClass(), 70);

        var leftAndRight = panelManager.getRoot()
            .subdivide(1, AdjustablePanelManager.PanelDivider.Orientation.VERTICAL, layoutRatios.getFirst());

        leftAndRight.get(0).resizeCallback(model3DViewer::relocate);
        leftAndRight.get(0).resizeFinishCallback(_ -> model3DViewer.onResizeCompleted());
        leftAndRight.get(1).area(detailsPanel);

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

        frameCamera();
    }

    private void frameCamera() {
        model3DViewer.frameCamera(FRAMED_WIDTH, FRAMED_HEIGHT);
    }

    // ***************************************************************
    // Save
    // ***************************************************************

    /// Rows write straight to the holder, so there is nothing to flush before the disk write.
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
