package com.yesman.akytheralabs.gui.screen;

import com.yesman.akythera.client.gui.widget.WidgetTable;
import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akythera.client.gui.widget.viewer.BackgroundComponent;
import com.yesman.akythera.client.gui.widget.viewer.CameraComponent;
import com.yesman.akythera.client.gui.widget.viewer.EntityModelComponent;
import com.yesman.akythera.client.gui.widget.viewer.GridFloorComponent;
import com.yesman.akythera.client.gui.widget.viewer.Model3DViewer;
import com.yesman.akythera.core.asset.loader.AnimationLoader;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.FileSystemHelper;
import com.yesman.akytheralabs.akythera.asset.AnimationMetadataHolder;
import com.yesman.akytheralabs.dataholder.EntityPreviewModel;
import com.yesman.akytheralabs.editor.EditorWidgetFactory;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.WorkHistory;
import com.yesman.akytheralabs.editor.keybind.EditorKeyMappings;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.AdjustablePanelManager;
import com.yesman.akytheralabs.gui.widget.PanelHeader;
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
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/// Edits an animation asset's sidecar metadata: its [AnimationLoader.ImportType] and, for an
/// additive import, the reference pose the keyframes are read relative to.
public final class AnimationAssetEditorScreen extends Screen implements ModalAccessibleScreen, SafeCloseScreen {
    private static final int TOP_BAR_H = 22;
    private static final int ROW_H = 20;

    private final AdjustablePanelManager panelManager;
    private final TopMenuBar topMenuBar;
    private final Model3DViewer model3DViewer;
    private final WidgetTable propertiesTable;
    private final PanelHeader propertiesHeader;
    /// The sidecar view, taken off the accessor so both screens on the same animation share one —
    /// and so the preview model chosen here survives a close and reopen.
    private final AnimationMetadataHolder holder;

    private @Nullable WorkHistory savePoint;

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
        EntityPreviewModel.display(model3DViewer, holder.getPreviewModel(), true, true);
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
        leftAndRight.get(1).resizeCallback(rect -> {
            propertiesHeader.relocate(rect);
            propertiesTable.relocate(rect);
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
    }

    private void openPreviewModelSelectModal() {
        ModelSelectModal modal = new ModelSelectModal(font, holder.getPreviewModel(), newModel -> {
            holder.setPreviewModel(newModel);
            EntityPreviewModel.display(model3DViewer, newModel, true, true);
            refreshPreviewAnimation();
        });

        AkytheraEditorLayoutManager.getInstance().openModalWidgetAtCenter(modal);
    }

    /// (Re)plays the edited clip on the previewer's current entity component. Safe to call after any
    /// `displayEntity` — the freshly created component is fetched from the previewer.
    private void refreshPreviewAnimation() {
        EntityModelComponent component = model3DViewer.getComponent(EntityModelComponent.class);

        if (component != null) component.setSimplePlayingAnimation(holder.getAccessor());
    }

    /// `walk.akasset` → `walk.akmeta`, next to it. Resolved from the accessor's identifier on
    /// every call, so a rename while this screen is open writes next to the new name.
    private Path metadataFile() {
        return AkytheraProject.getMetadataFileOf(holder.getAccessor().identifier());
    }

    /// Writes the sidecar, then drops the parsed clip: the metadata decides whether the file reads
    /// raw or additive, so the previewer must re-read it.
    private void save() {
        if (!FileSystemHelper.writeJson(metadataFile(), AnimationLoader.Metadata.CODEC, holder.makeMetadata())) {
            AkytheraLabs.LOGGER.error("Failed to save animation metadata '{}'", metadataFile());
            return;
        }

        AkytheraProject.getCurrentProject().invalidateEditorAnimation(holder.getAccessor().identifier());
        refreshPreviewAnimation();
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
                AkytheraEditorLayoutManager.getInstance().closeModal();
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

    /// Instead of assigning `modal` directly into [#getFocused()], check and return opened modal via
    /// [ModalAccessibleScreen#getFocusOwningModal] to release focus automatically when closed.
    @Nullable
    public GuiEventListener getFocused() {
        AbstractWidget modal = getFocusOwningModal();
        if (modal != null) return modal;

        return super.getFocused();
    }
}
