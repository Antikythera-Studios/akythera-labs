package com.yesman.akytheralabs.gui.screen;

import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.keybind.EditorKeyMappings;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.AdjustablePanelManager;
import com.yesman.akytheralabs.gui.panel.main.ContentBrowserPanel;
import com.yesman.akytheralabs.gui.panel.main.ExplorerPanel;
import com.yesman.akytheralabs.gui.panel.main.WorldviewPanel;
import com.yesman.akytheralabs.gui.widget.WorldViewportHost;
import com.yesman.akytheralabs.gui.widget.main.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/// A main editor screen layout
///
/// This screen doesn't register at [Minecraft#screen] to allow running
/// games with the editor screen opened and don't be overridden by
/// in-game screens like inventory or chest container.
///
/// [Minecraft#level] always not null when the screen is opened
public final class MainEditorScreen extends Screen implements ModalAccessibleScreen, SafeCloseScreen, WorldViewportHost {
    private static final int RIGHT_TOOLBAR_WIDTH = 40;
    private static final int TOP_BAR_HEIGHT = 22;

    private final AdjustablePanelManager panelManager;
    private final WorldViewport worldViewport;

    public final TopMenuBar topMenuBar;
    public final ExplorerPanel explorerPanel;
    public final ContentBrowserPanel contentBrowserPanel;

    public MainEditorScreen() {
        super(Component.translatable(LangKeys.GUI_MAIN_SCREEN_TITLE));

        Minecraft mc = Minecraft.getInstance();

        // Top widgets
        topMenuBar = new TopMenuBar(
            mc.font,
            Component.literal(AkytheraProject.getCurrentProject().getProjectName()),
            AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(TOP_BAR_HEIGHT),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            _ -> handleQuitRequest(),
            TopMenuBar.menuBuilder()
                .newList(Component.translatable(LangKeys.GUI_MENU_BAR_FILE))
                    .newOption(Component.translatable(LangKeys.GUI_MENU_BAR_FILE_SAVE), this::saveProject)
                    .newSubList(Component.translatable(LangKeys.GUI_MAIN_SCREEN_MENU_BAR_FILE_EXPORT))
                        .newOption(Component.translatable(LangKeys.GUI_MAIN_SCREEN_MENU_BAR_FILE_EXPORT_AS_PACK), () -> ExportModalGenerator.openAsPack(font))
                        .newOption(Component.translatable(LangKeys.GUI_MAIN_SCREEN_MENU_BAR_FILE_EXPORT_AS_SOURCE_CODE), () -> ExportModalGenerator.openAsSourceCode(font))
                    .done()
                    .newOption(Component.translatable(LangKeys.GUI_MAIN_SCREEN_MENU_BAR_FILE_DELETE), () -> DeleteProjectModalGenerator.open(font))
                .done()
                .newList(Component.translatable(LangKeys.GUI_MENU_BAR_EDIT))
                    .newOption(Component.translatable(LangKeys.GUI_MAIN_SCREEN_MENU_BAR_EDIT_LANGUAGE), () -> LanguageSelectionModal.open(font))
                    .newOption(Component.translatable(LangKeys.GUI_MAIN_SCREEN_MENU_BAR_EDIT_PROJECT_SETTINGS), () -> ProjectSetupModalGenerator.open(font))
                    .newOption(Component.translatable(LangKeys.GUI_MAIN_SCREEN_MENU_BAR_EDIT_EDITOR_PREFERENCES), () -> EditorPreferencesModalGenerator.open(font))
                    .newOption(Component.translatable(LangKeys.GUI_MAIN_SCREEN_MENU_BAR_EDIT_MODEL_ARMATURE_MAPPING), () -> ModelArmatureMappingModal.open(font))
                .done()
                //.newList(Component.translatable(LangKeys.GUI_MENU_BAR_VIEW))
                //.done()
                .newList(Component.translatable(LangKeys.GUI_MAIN_SCREEN_MENU_BAR_TOOLS))
                    .newOption(Component.translatable(LangKeys.GUI_MAIN_SCREEN_MENU_BAR_TOOLS_ANIMATION_RETARGET), () -> AnimationRetargetModal.open(font))
                    .newOption(Component.translatable(LangKeys.GUI_MAIN_SCREEN_MENU_BAR_TOOLS_DEBUG), () -> DebuggableScreen.openDebugSelector(font))
                .done()
                .newList(Component.translatable(LangKeys.GUI_MAIN_SCREEN_MENU_BAR_HELP))
                    .newOption(Component.translatable(LangKeys.GUI_MAIN_SCREEN_MENU_BAR_HELP_DOCS), () -> {})
                    .newOption(Component.translatable(LangKeys.GUI_MAIN_SCREEN_MENU_BAR_HELP_ISSUE_REPORT), () -> {})
                .done()
        );

        // Top right panel widgets
        worldViewport = new WorldViewport(
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(RIGHT_TOOLBAR_WIDTH),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM,
            Component.translatable(LangKeys.GUI_MAIN_SCREEN_WIDGET_GAMEPLAY_VIEWPORT)
        );

        panelManager = new AdjustablePanelManager(
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(TOP_BAR_HEIGHT), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM
        );

        List<Integer> layoutRatios = AkytheraProject.getCurrentProject().getLayoutRatioOrDefault(getClass(), 60, 25);

        var subSection1 = panelManager.getRoot().subdivide(1, AdjustablePanelManager.PanelDivider.Orientation.HORIZONTAL, layoutRatios.get(0));
        var subSection2 = subSection1.getFirst().subdivide(1, AdjustablePanelManager.PanelDivider.Orientation.VERTICAL, layoutRatios.get(1));

        contentBrowserPanel = new ContentBrowserPanel(getFont());
        explorerPanel = new ExplorerPanel(getFont());

        subSection2.get(0)
            .area(explorerPanel)
            .backgroundColor(0xFF1A1A1A);

        subSection2.get(1)
            .area(new WorldviewPanel(getFont(), worldViewport))
            .backgroundColor(0xFF1E1E1E);

        subSection2.get(1)
            .resizeFinishCallback(_ -> onViewportResized());

        subSection1.get(0)
            .resizeFinishCallback(_ -> onViewportResized());

        subSection1.get(1)
            .area(contentBrowserPanel);
    }

    @Override
    public void onFilesDrop(@NonNull List<Path> paths) {
        if (AnimationRetargetModal.onFilesDrop(paths)) return;
        if (ProjectSetupModalGenerator.onFilesDrop(paths)) return;

        ExplorerPanel.TreeNode selected = explorerPanel.getSelected();

        if (selected == null || selected.directoryPath == null || selected.processor == null) return;

        contentBrowserPanel.importExternalAssets(paths, selected.directoryPath, selected.processor);
    }

    private void onViewportResized() {
        // Min pixel guard: 10px
        AkytheraEditorLayoutManager.getInstance().resizeWorldViewport(Math.max(worldViewport.getWidth(), 10), Math.max(worldViewport.getHeight(), 10));
    }

    @Override
    protected void init() {
        super.init();

        layoutWidgets();
        onViewportResized();
        addRenderableWidget(panelManager);
    }

    /// Relocate and scale widgets by screen rectangle
    private void layoutWidgets() {
        var rect = getRectangle();

        topMenuBar.relocate(rect);
        panelManager.relocate(rect);
    }

    @Override
    public void onClose() {
        AkytheraProject.getCurrentProject().saveLayoutOfScreen(this, panelManager.serializePanelDividerPositions());

        // Prevents infinite loop
        AkytheraEditorLayoutManager.getInstance().removeLastScreen();
        AkytheraEditorLayoutManager.getInstance().closeEditorLayout();
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (!AkytheraEditorLayoutManager.getInstance().snatchInputs()) {
            // If mouse pointer is in world viewport, modify the parameters of current mouse
            // pointers to avoid highlighting widgets
            mouseX = Integer.MAX_VALUE;
            mouseY = Integer.MAX_VALUE;
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        // TopMenuBar renders last so dropdown draws over all panels
        topMenuBar.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void handleQuitRequest() {
        SafeCloseScreen.openQuiteScreen(
            font,
            Component.translatable(LangKeys.GUI_MAIN_SCREEN_MESSAGE_QUIT_WITHOUT_SAVE),
            // Project-level quit has no per-item save point, so always prompt.
            true,
            () -> {
                saveProject();
                AkytheraProject.endProject();
            },
            AkytheraProject::endProject
        );
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

    /// Instead of assigning `modal` directly into [#focused], check and return opened modal via
    /// [ModalAccessibleScreen#getFocusOwningModal] to release focus automatically when closed.
    @Nullable
    public GuiEventListener getFocused() {
        AbstractWidget modal = getFocusOwningModal();
        if (modal != null) return modal;

        return super.getFocused();
    }

    @Override
    public void repositionElements() {
        super.repositionElements();

        onViewportResized();
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        AkytheraEditorLayoutManager.blurFocusChainLeaf(this, event.x(), event.y());

        // TopMenuBar gets first click priority (handles open dropdown or closes it)
        if (topMenuBar.mouseClicked(event, doubleClick)) return true;

        return super.mouseClicked(event, doubleClick);
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
    public @NonNull Optional<GuiEventListener> getChildAt(final double x, final double y) {
        AbstractWidget optModal = getHoveringModal(x, y);

        if (optModal != null) {
            return Optional.of(optModal);
        }

        return super.getChildAt(x, y);
    }

    private void saveProject() {
        AkytheraProject project = AkytheraProject.getCurrentProject();
        project.saveLayoutOfScreen(this, panelManager.serializePanelDividerPositions());
        project.saveAllEntries();
        project.saveProjectMetadata(explorerPanel.collectFolderStates(), explorerPanel.getFocusedPath());
    }

    public boolean isEditorDragging() {
        return panelManager.isDragging();
    }

    @Override
    public ScreenRectangle getWorldViewportRectangle() {
        return worldViewport.getRectangle();
    }
}
