package com.yesman.akytheralabs.gui.panel.main;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.mojang.datafixers.util.Pair;
import com.yesman.akythera.client.gui.widget.AnchoredButton;
import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.FileSystemHelper;
import com.yesman.akytheralabs.editor.historytrack.FileDeletion;
import com.yesman.akytheralabs.editor.historytrack.FileMove;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.keybind.EditorKeyMappings;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.AdjustablePanelManager;
import com.yesman.akytheralabs.gui.panel.main.ExplorerPanel.TreeNode;
import com.yesman.akytheralabs.gui.panel.main.processors.AnimationAssetResourceProcessor;
import com.yesman.akytheralabs.gui.panel.main.processors.ArmatureResourceProcessor;
import com.yesman.akytheralabs.gui.panel.main.processors.MeshResourceProcessor;
import com.yesman.akytheralabs.gui.panel.main.processors.ResourceProcessor;
import com.yesman.akytheralabs.gui.screen.MainEditorScreen;
import com.yesman.akytheralabs.gui.widget.*;
import com.yesman.akytheralabs.gui.widget.main.ContentWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public final class ContentBrowserPanel extends AdjustablePanelManager.PanelArea {
    private static final int HEADER_H = PanelHeader.DEFAULT_HEADER_HEIGHT;
    private static final int GRID_PADDING = 10;
    private static final int HEADER_WIDGET_H = 14;
    private static final int ADD_BUTTON_W = 30;
    private static final int PADDING = 6;

    private final Font font;
    private final PanelHeader header;
    private final AdvancedEditBox<String> searchBox;
    private final AnchoredButton addButton;
    private final Grid<ContentWidget<?>> grid;

    private ExplorerPanel.@Nullable TreeNode selectedNode;
    private final Set<ContentWidget<?>> selectedWidgets = new HashSet<>();
    @Nullable
    private ContentWidget<?> shiftSelectAnchor = null;
    @Nullable
    private ContentWidget<?> dragHighlighted = null;

    private boolean draggingContent = false;

    public ContentBrowserPanel(Font font) {
        this.font = font;
        this.header = new PanelHeader(font, Component.translatable(LangKeys.GUI_MAIN_SCREEN_WIDGET_CONTENT_BROWSER, 0));
        this.searchBox = AdvancedEditBox.stringBox(
            font,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(ADD_BUTTON_W + PADDING + 6),
            AnchoredWidget.pixel((HEADER_H - HEADER_WIDGET_H) / 2), AnchoredWidget.pixel(HEADER_WIDGET_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            () -> "",
            _ -> onSearchChanged(),
            Component.translatable(LangKeys.GUI_MESSAGE_EMPTY_SEARCHBAR)
        );

        this.addButton = AnchoredButton
            .buttonBuilder(font, Component.empty(), _ -> onAddPressed())
            .xUnits(AnchoredWidget.pixel(PADDING), AnchoredWidget.pixel(ADD_BUTTON_W))
            .yUnits(AnchoredWidget.pixel((HEADER_H - HEADER_WIDGET_H) / 2), AnchoredWidget.pixel(HEADER_WIDGET_H))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.TOP_HEIGHT)
            .theme(AkytheraLabsButtonTheme.AKYTHERA_CONTENT_ADD_BUTTON)
            .build();

        this.grid = new Grid<>(
            AnchoredWidget.pixel(GRID_PADDING),
            AnchoredWidget.pixel(GRID_PADDING),
            AnchoredWidget.pixel(GRID_PADDING + HEADER_H),
            AnchoredWidget.pixel(GRID_PADDING),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM,
            rect -> Math.clamp((rect.width() - SCROLLBAR_WIDTH) / 4 - GRID_PADDING, 40, 70),
            rect -> Math.clamp((rect.width() - SCROLLBAR_WIDTH) / 4 - GRID_PADDING, 40, 70),
            _ -> GRID_PADDING,
            Comparator.<ContentWidget<?>, Boolean>comparing(w -> !(w instanceof ContentWidget.FolderContent))
                .thenComparing(ContentWidget::getContentName, String.CASE_INSENSITIVE_ORDER)
        );

        addWidget(header);
        addWidget(searchBox);
        addWidget(addButton);
        addWidget(grid);
    }

    public Font getFont() {
        return font;
    }

    public void onNodeSelected(@Nullable TreeNode node) {
        selectedNode = node;
        addButton.active = node != null && node.processor != null;
        rebuildGrid();
    }

    private void onAddPressed() {
        if (selectedNode == null || selectedNode.processor == null) return;
        selectedNode.processor.onPressAddButton(this, selectedNode);
    }

    /// Public accessor for [ResourceProcessor#backgroundMenu] and any other
    /// path that needs to fire the green-cross "+" handler externally.
    public void triggerAddPressed() {
        onAddPressed();
    }

    /// Public accessor for processor widget menus that need to invoke the
    /// shared "Delete Selected" popup flow.
    public void triggerDeleteSelected() {
        if (selectedWidgets.isEmpty()) return;
        showDeleteSelectedPopup();
    }

    private void rebuildGrid() {
        clearContentSelection();
        grid.clearChildren();
        String query = searchBox.getText().toLowerCase();

        if (selectedNode != null && selectedNode.directoryPath != null && Files.isDirectory(selectedNode.directoryPath)) {
            List<ContentWidget<?>> folders = new ArrayList<>();
            List<ContentWidget<?>> files = new ArrayList<>();

            try (Stream<Path> entries = Files.list(selectedNode.directoryPath)) {
                entries.sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .forEach(path -> {
                        String name = path.getFileName().toString();

                        if (Files.isDirectory(path)) {
                            ContentWidget<?> w = new ContentWidget.FolderContent(font, name, path);

                            if (query.isEmpty() || name.toLowerCase().contains(query)) {
                                folders.add(w);
                            }
                        } else if (Files.isRegularFile(path)) {
                            ContentWidget<?> w = (selectedNode.processor != null)
                                ? selectedNode.processor.createContentWidget(font, path)
                                : null;

                            if (w != null) {
                                w.setSortCallback(this::sortWidgets);
                                String nameNoExt = ResourceProcessor.fileNameNoExt(path);

                                if (query.isEmpty() || nameNoExt.toLowerCase().contains(query)) {
                                    files.add(w);
                                }
                            }
                        }
                    });
            } catch (IOException ignored) {}

            folders.forEach(grid::addChild);
            files.forEach(grid::addChild);
        }

        grid.sort();
        header.setMessage(Component.translatable(LangKeys.GUI_MAIN_SCREEN_WIDGET_CONTENT_BROWSER, grid.children().size()));

        int titleWidth = font.width(header.getMessage().getString());
        searchBox.setX1(AnchoredWidget.pixel(6 + titleWidth + 10));

        ScreenRectangle rect = getRectangle();
        grid.relocate(rect);
        searchBox.relocate(rect);
        addButton.relocate(rect);
    }

    @Override
    protected void onPanelResized(ScreenRectangle screenRectangle) {
        header.relocate(screenRectangle);

        // Position the search box inline within the header, to the right of the title
        int titleWidth = font.width(header.getMessage().getString());
        searchBox.setX1(AnchoredWidget.pixel(PADDING + titleWidth + 10));
        searchBox.relocate(screenRectangle);
        addButton.relocate(screenRectangle);

        rebuildGrid();
        grid.relocate(screenRectangle);
    }

    private void onSearchChanged() {
        grid.setFocused(null);
        rebuildGrid();
        grid.setScrollAmount(0);
    }

    public void addDatapackWidget(ContentWidget<?> widget) {
        widget.setSortCallback(this::sortWidgets);
        grid.addChild(widget);
        header.setMessage(Component.translatable(LangKeys.GUI_MAIN_SCREEN_WIDGET_CONTENT_BROWSER, grid.children().size()));
        grid.relocate(getRectangle());
        grid.setFocused(widget);
        widget.startEditing();
    }

    public void sortWidgets() {
        grid.sort();
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (isMouseOver(event.x(), event.y())) {
            if (event.button() == 1) {
                if (selectedNode == null || selectedNode.processor == null) {
                    return super.mouseClicked(event, doubleClick);
                }

                ContentWidget<?> hovered = getHoveredContentWidget(event.x(), event.y());

                if (hovered != null) {
                    // If newly pressed widget is not selected, cancel all current selections
                    if (!hovered.isSelected()) {
                        // Remove existing widgets
                        selectedWidgets.forEach(w -> w.setSelected(false));
                        selectedWidgets.clear();
                        // Set selected
                        hovered.setSelected(true);
                        selectedWidgets.add(hovered);
                    }

                    // Move focus
                    setFocused(hovered);
                } else {
                    selectedWidgets.forEach(w -> w.setSelected(false));
                    selectedWidgets.clear();
                }

                ContextMenuContainer toShow = (hovered != null)
                    ? hovered instanceof ContentWidget.FolderContent
                        ? ResourceProcessor.folderMenu(this, hovered)
                        : selectedNode.processor.createContextMenuOnWidget(this, hovered)
                    : ResourceProcessor.backgroundMenu(this);

                AkytheraEditorLayoutManager.getInstance().openModalWidget(toShow, (int) event.x(), (int) event.y());

                return true;
            }

            if (event.button() == 0) {
                ContentWidget<?> hovered = getHoveredContentWidget(event.x(), event.y());

                if (hovered != null) {
                    handleContentSelection(hovered, event);
                } else {
                    clearContentSelection();
                }

                return super.mouseClicked(event, doubleClick);
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
        if (event.button() == 0 && !selectedWidgets.isEmpty()) {
            draggingContent = true;
            return true;
        }

        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        if (event.button() == 0) {
            boolean wasDragging = draggingContent;

            if (wasDragging) {
                draggingContent = false;

                if (isMouseOver(event.x(), event.y())) { // Internal dragging end
                    ContentWidget<?> hovering = getHoveredContentWidget(event.x(), event.y());

                    if (hovering instanceof ContentWidget.FolderContent folderContent && !selectedWidgets.contains(hovering)) {
                        showMoveConfirmPopup(folderContent.getPath());
                        hovering.setDragHighlighted(false);
                    }
                } else { // External dragging end
                    MutableBoolean rv = new MutableBoolean(false);

                    AkytheraEditorLayoutManager.getInstance().<MainEditorScreen>ifLastScreen(mainEditorScreen -> {
                        ExplorerPanel ep = mainEditorScreen.explorerPanel;
                        ExplorerPanel.TreeNode target = ep.getNodeAt((int) event.x(), (int) event.y());

                        if (ep.isCompatibleDrop(selectedNode != null ? selectedNode.processor : null, target)) {
                            showMoveConfirmPopup(target.directoryPath);
                            rv.setTrue();
                            return;
                        }

                        ep.setDragHighlightNode(null);
                    });

                    if (rv.booleanValue()) {
                        return true;
                    }
                }
            }

            if (!event.hasShiftDown() && !event.hasControlDown() && !wasDragging) {
                ContentWidget<?> cw = getHoveredContentWidget(event.x(), event.y());

                if (cw != null) {
                    selectedWidgets.removeIf(widget -> {
                        if (widget != cw) {
                            widget.setSelected(false);
                            return true;
                        }

                        return false;
                    });

                    return true;
                }
            }
        }

        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        // Global shortcut: select every content widget regardless of which panel is focused.
        if (EditorKeyMappings.SELECT_ALL.matches(event) && !grid.children().isEmpty()) {
            for (ContentWidget<?> w : grid.children()) {
                w.setSelected(true);
                selectedWidgets.add(w);
            }

            shiftSelectAnchor = grid.children().getFirst();

            return true;
        }

        // Rename the single selected widget (skipped for multi-select / non-editable).
        if (EditorKeyMappings.RENAME.matches(event) && selectedWidgets.size() == 1) {
            ContentWidget<?> target = selectedWidgets.iterator().next();
            if (target.isEditable()) {
                grid.setFocused(target);
                target.startEditing();

                return true;
            }
        }

        return super.keyPressed(event);
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractWidgetRenderState(graphics, mouseX, mouseY, a);

        AkytheraEditorLayoutManager.getInstance().<MainEditorScreen>ifLastScreen(mainEditorScreen -> {
            ExplorerPanel ep = mainEditorScreen.explorerPanel;

            if (draggingContent) {
                if (isMouseOver(mouseX, mouseY)) { // Internal dragging
                    ContentWidget<?> hovering = getHoveredContentWidget(mouseX, mouseY);

                    if (hovering != null) {
                        boolean canDrop = hovering instanceof ContentWidget.FolderContent && !selectedWidgets.contains(hovering);
                        graphics.requestCursor(canDrop ? CursorTypes.POINTING_HAND : CursorTypes.NOT_ALLOWED);
                    }

                    if (dragHighlighted != hovering) {
                        if (dragHighlighted != null) dragHighlighted.setDragHighlighted(false);
                        if (hovering instanceof ContentWidget.FolderContent) hovering.setDragHighlighted(true);

                        dragHighlighted = hovering;
                    }
                } else { // External Dragging
                    ExplorerPanel.TreeNode target = ep.getNodeAt(mouseX, mouseY);

                    if (target != null) {
                        boolean canDrop = ep.isCompatibleDrop(selectedNode != null ? selectedNode.processor : null, target);
                        graphics.requestCursor(canDrop ? CursorTypes.POINTING_HAND : CursorTypes.NOT_ALLOWED);
                        ep.setDragHighlightNode(canDrop ? target : null);
                    } else {
                        ep.setDragHighlightNode(null);
                    }
                }
            } else {
                ep.setDragHighlightNode(null);

                if (dragHighlighted != null) {
                    dragHighlighted.setDragHighlighted(false);
                    dragHighlighted = null;
                }
            }
        });
    }

    public List<ContentWidget<?>> getSelectedWidgets() {
        return selectedWidgets.stream().sorted(Comparator.comparing(w -> grid.children().indexOf(w))).toList();
    }

    public @Nullable ContentWidget<?> getHoveredContentWidget(double x, double y) {
        for (ContentWidget<?> w : grid.children()) {
            if (w.isMouseOver(x, y)) return w;
        }

        return null;
    }

    public void pickSelections(boolean removeCurrentSelections, Path path) {
        if (removeCurrentSelections) selectedWidgets.clear();

        for (ContentWidget<?> w : grid.children()) {
            if (path.equals(w.getPath())) {
                w.setSelected(true);
                selectedWidgets.add(w);
                return;
            };
        }
    }

    private void handleContentSelection(ContentWidget<?> clicked, MouseButtonEvent event) {
        if (event.hasControlDown()) {
            if (clicked.isSelected()) {
                clicked.setSelected(false);
                selectedWidgets.remove(clicked);
                grid.setFocused(null);
            } else {
                clicked.setSelected(true);
                selectedWidgets.add(clicked);
                shiftSelectAnchor = clicked;
                grid.setFocused(clicked);
            }
        } else if (event.hasShiftDown() && shiftSelectAnchor != null) {
            List<ContentWidget<?>> all = grid.children();
            int from = all.indexOf(shiftSelectAnchor);
            int to = all.indexOf(clicked);

            if (from >= 0 && to >= 0) {
                for (ContentWidget<?> w : selectedWidgets) w.setSelected(false);
                selectedWidgets.clear();

                int lo = Math.min(from, to);
                int hi = Math.max(from, to);

                for (int i = lo; i <= hi; i++) {
                    ContentWidget<?> w = all.get(i);
                    w.setSelected(true);
                    selectedWidgets.add(w);
                }
            }

            grid.setFocused(clicked);
        } else {
            if (!clicked.isSelected()) clearContentSelection();
            clicked.setSelected(true);
            selectedWidgets.add(clicked);
            shiftSelectAnchor = clicked;
            grid.setFocused(clicked);
        }
    }

    public void clearContentSelection() {
        for (ContentWidget<?> w : selectedWidgets) w.setSelected(false);
        selectedWidgets.clear();
        grid.setFocused(null);
        shiftSelectAnchor = null;
    }

    private void showDeleteSelectedPopup() {
        int count = selectedWidgets.size();
        int popupW = 300;
        int btnW = WidgetContainerModal.evenButtonWidth(popupW, 2);

        WidgetContainerModal popup = WidgetContainerModal.builder(font, Component.translatable(LangKeys.GUI_MAIN_SCREEN_CONTENT_BROWSER_POPUP_DELETE_TITLE), popupW)
            .textLines(Component.translatable(LangKeys.GUI_MAIN_SCREEN_CONTENT_BROWSER_POPUP_DELETE_BODY, count))
            .footerButton(
                AnchoredButton.buttonBuilder(font, Component.translatable(LangKeys.GUI_CANCEL),
                        _ -> AkytheraEditorLayoutManager.getInstance().closeModal())
                    .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
                    .build(),
                btnW
            )
            .footerButton(
                AnchoredButton.buttonBuilder(font, Component.translatable(LangKeys.GUI_MESSAGE_BUTTON_REMOVE), _ -> {
                    deleteSelectedItems();
                    AkytheraEditorLayoutManager.getInstance().closeModal();
                })
                .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
                .build(),
                btnW
            )
            .build();

        AkytheraEditorLayoutManager.getInstance().openModalWidgetAtCenter(popup);
    }

    private void deleteSelectedItems() {
        if (selectedWidgets.isEmpty()) return;

        Path pathToRecover = selectedNode == null ? null : selectedNode.directoryPath;
        AkytheraProject project = AkytheraProject.getCurrentProject();

        // Capture restore closures BEFORE any delete touches the registry/disk
        List<Path> paths = new ArrayList<>(selectedWidgets.size());
        List<Runnable> restores = new ArrayList<>(selectedWidgets.size());

        for (ContentWidget<?> widget : selectedWidgets) {
            Path path = widget.getPath();
            paths.add(path);
            restores.add(project.buildRestoreFor(path));
        }

        for (Path path : paths) project.deletePathRecursive(path);

        HistoryManager.getInstance().pushHistory(new FileDeletion(
            () -> {
                for (Path path : paths) project.deletePathRecursive(path);
                refreshExplorerAndBrowser(pathToRecover);
            },
            () -> {
                for (Runnable r : restores) r.run();
                refreshExplorerAndBrowser(pathToRecover);
            }
        ));

        clearContentSelection();
        rebuildGrid();
        refreshExplorerAndBrowser(pathToRecover);
    }

    private void refreshExplorerAndBrowser(@Nullable Path focus) {
        AkytheraEditorLayoutManager.getInstance().<MainEditorScreen>ifLastScreen(mainEditorScreen -> {
            mainEditorScreen.explorerPanel.rebuildTree();
            if (focus != null) mainEditorScreen.explorerPanel.focusNodeByPath(focus);
            mainEditorScreen.contentBrowserPanel.rebuildGrid();
        });
    }

    private void showMoveConfirmPopup(Path path) {
        AkytheraEditorLayoutManager.getInstance().<MainEditorScreen>ifLastScreen(mainEditorScreen -> {
            ExplorerPanel.TreeNode destNode = mainEditorScreen.explorerPanel.findNodeByPath(path);

            if (destNode == null) {
                return;
            }

            int count = selectedWidgets.size();
            String srcPath  = buildRelativePath(selectedNode);
            String destPath = buildRelativePath(destNode);

            int popupW = 280;
            int btnW = WidgetContainerModal.evenButtonWidth(popupW, 2);

            WidgetContainerModal popup = WidgetContainerModal.builder(font, Component.translatable(LangKeys.GUI_MAIN_SCREEN_CONTENT_BROWSER_POPUP_MOVE_TITLE), popupW)
                .textLines(Component.translatable(LangKeys.GUI_MAIN_SCREEN_CONTENT_BROWSER_POPUP_MOVE_BODY, count, srcPath, destPath))
                .footerButton(
                    AnchoredButton.buttonBuilder(font, Component.translatable(LangKeys.GUI_CANCEL),
                            _ -> AkytheraEditorLayoutManager.getInstance().closeModal())
                        .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
                        .build(),
                    btnW
                )
                .footerButton(
                    AnchoredButton.buttonBuilder(font, Component.translatable(LangKeys.GUI_MESSAGE_BUTTON_MOVE), _ -> {
                        moveSelectedItemsTo(destNode.directoryPath);
                        AkytheraEditorLayoutManager.getInstance().closeModal();
                    })
                    .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
                    .build(),
                    btnW
                )
                .build();

            AkytheraEditorLayoutManager.getInstance().openModalWidgetAtCenter(popup);
        });
    }

    // ******************************** External asset import ********************************

    @FunctionalInterface
    private interface AssetImporter {
        AkytheraProject.AssetLoadResult<?> load(Path source, Path destination);
    }

    /// Drives the sequential import of one batch of externally-sourced files. Held mutable
    /// across the user-driven modal chain (duplicate prompts, error prompts) so each resumed
    /// callback knows where it left off.
    private static final class ImportSession {
        private final List<Path> sources;
        private final Path targetDir;
        private final AssetImporter importer;
        private final boolean allowOverwriteAll;
        private int index;
        private boolean overwriteAll;
        private boolean anyImported;

        ImportSession(List<Path> sources, Path targetDir, AssetImporter importer) {
            this.sources = sources;
            this.targetDir = targetDir;
            this.importer = importer;
            // "Overwrite (to All)" is only meaningful when more than one file is being imported.
            this.allowOverwriteAll = sources.size() > 1;
        }
    }

    /// Imports externally-sourced asset files (a drag-drop batch or the file dialog behind the
    /// `+` button) into `targetDir`. Each file is copied into the project and parsed; a parse
    /// failure surfaces the loader's own message in an error modal, and a name collision in
    /// `targetDir` prompts the user to Overwrite / Overwrite-all / Cancel. Processing is
    /// sequential because each prompt is modal. No-ops for processors that don't back a
    /// file-based asset type.
    public void importExternalAssets(List<Path> sources, Path targetDir, ResourceProcessor<?> processor) {
        AkytheraProject project = AkytheraProject.getCurrentProject();
        AssetImporter importer;

        if (processor == ArmatureResourceProcessor.INSTANCE) {
            importer = project::loadArmatureToCurrentProject;
        } else if (processor == MeshResourceProcessor.INSTANCE) {
            importer = project::loadMeshToCurrentProject;
        } else if (processor == AnimationAssetResourceProcessor.INSTANCE) {
            importer = project::loadAnimationToCurrentProject;
        } else {
            return;
        }

        List<Path> valid = sources.stream()
            .filter(p -> {
                String name = p.getFileName().toString();
                return name.endsWith(".efasset") || name.endsWith(".json");
            })
            .toList();

        if (valid.isEmpty()) return;

        importNext(new ImportSession(valid, targetDir, importer));
    }

    private void importNext(ImportSession session) {
        if (session.index >= session.sources.size()) {
            finishImport(session);
            return;
        }

        Path source = session.sources.get(session.index);
        Path destination = session.targetDir.resolve(source.getFileName().toString());

        if (Files.exists(destination) && !session.overwriteAll) {
            showDuplicatePopup(session, source, destination);
        } else {
            importCurrentAndAdvance(session, source, destination);
        }
    }

    private void importCurrentAndAdvance(ImportSession session, Path source, Path destination) {
        AkytheraProject.AssetLoadResult<?> result = session.importer.load(source, destination);
        session.index++;

        if (result.failed()) {
            // Acknowledge the error before moving on so only one modal is ever open at a time.
            showLoadErrorPopup(result.error(), () -> importNext(session));
        } else {
            session.anyImported = true;
            importNext(session);
        }
    }

    private void finishImport(ImportSession session) {
        if (session.anyImported) {
            refreshExplorerAndBrowser(session.targetDir);
        }
    }

    private void showDuplicatePopup(ImportSession session, Path source, Path destination) {
        String fileName = source.getFileName().toString();
        boolean allowAll = session.allowOverwriteAll;

        int popupW = allowAll ? 360 : 300;
        int btnW = WidgetContainerModal.evenButtonWidth(popupW, allowAll ? 3 : 2);

        WidgetContainerModal.Builder builder = WidgetContainerModal.builder(
                font, Component.translatable(LangKeys.GUI_MAIN_SCREEN_CONTENT_BROWSER_POPUP_DUPLICATE_TITLE), popupW)
            .textLines(Component.translatable(LangKeys.GUI_MAIN_SCREEN_CONTENT_BROWSER_POPUP_DUPLICATE_BODY, fileName))
            // Footer buttons render right-to-left in insertion order, so add Cancel first
            // (rightmost) and Overwrite last (leftmost) to read "Overwrite ... Cancel".
            .footerButton(
                AnchoredButton.buttonBuilder(font, Component.translatable(LangKeys.GUI_CANCEL), _ -> {
                        AkytheraEditorLayoutManager.getInstance().closeModal();
                        finishImport(session);
                    })
                    .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
                    .build(),
                btnW
            );

        if (allowAll) {
            builder.footerButton(
                AnchoredButton.buttonBuilder(font, Component.translatable(LangKeys.GUI_MESSAGE_BUTTON_OVERWRITE_ALL), _ -> {
                        AkytheraEditorLayoutManager.getInstance().closeModal();
                        session.overwriteAll = true;
                        importCurrentAndAdvance(session, source, destination);
                    })
                    .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
                    .build(),
                btnW
            );
        }

        builder.footerButton(
            AnchoredButton.buttonBuilder(font, Component.translatable(LangKeys.GUI_MESSAGE_BUTTON_OVERWRITE), _ -> {
                    AkytheraEditorLayoutManager.getInstance().closeModal();
                    importCurrentAndAdvance(session, source, destination);
                })
                .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
                .build(),
            btnW
        );

        AkytheraEditorLayoutManager.getInstance().openModalWidgetAtCenter(builder.build());
    }

    private void showLoadErrorPopup(@Nullable String message, Runnable onAcknowledge) {
        int popupW = 320;
        int btnW = WidgetContainerModal.evenButtonWidth(popupW, 1);

        WidgetContainerModal popup = WidgetContainerModal.builder(
                font, Component.translatable(LangKeys.GUI_MAIN_SCREEN_CONTENT_BROWSER_POPUP_LOAD_ERROR_TITLE), popupW)
            .textLines(Component.literal(message != null ? message : ""))
            .footerButton(
                AnchoredButton.buttonBuilder(font, Component.translatable(LangKeys.GUI_OK), _ -> {
                        AkytheraEditorLayoutManager.getInstance().closeModal();
                        onAcknowledge.run();
                    })
                    .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
                    .build(),
                btnW
            )
            .build();

        AkytheraEditorLayoutManager.getInstance().openModalWidgetAtCenter(popup);
    }

    private static String buildRelativePath(ExplorerPanel.@Nullable TreeNode node) {
        if (node == null || node.directoryPath == null) return "/";

        List<String> parts = new ArrayList<>();
        ExplorerPanel.TreeNode cur = node;

        while (cur != null && cur.modifiable) {
            parts.addFirst(cur.name);
            cur = cur.parentNode;
        }

        return cur.name + "/" + String.join("/", parts);
    }

    private void moveSelectedItemsTo(Path destinationPath) {
        if (selectedNode == null || selectedNode.directoryPath == null || destinationPath == null) return;

        Path sourceDir = selectedNode.directoryPath;
        AkytheraProject project = AkytheraProject.getCurrentProject();

        // Collect (src, dst) pairs that actually moved, then push ONE FileMove for
        // the whole batch so a single Ctrl+Z moves every file back to its origin.
        List<Pair<Path, Path>> moved = new ArrayList<>();

        for (ContentWidget<?> widget : selectedWidgets) {
            Path sourcePath = findMatchingPath(sourceDir, widget.getContentName());
            if (sourcePath == null) continue;

            Path destPath = destinationPath.resolve(sourcePath.getFileName());

            if (doMove(project, sourcePath, destPath)) {
                moved.add(Pair.of(sourcePath, destPath));
            }
        }

        if (!moved.isEmpty()) {
            HistoryManager.getInstance().pushHistory(new FileMove(
                () -> {
                    for (var p : moved) doMove(project, p.getFirst(), p.getSecond());
                    refreshAfterMoveAndSelect(destinationPath, moved.stream().map(Pair::getSecond).toList());
                },
                () -> {
                    for (var p : moved) doMove(project, p.getSecond(), p.getFirst());
                    refreshAfterMoveAndSelect(sourceDir, moved.stream().map(Pair::getFirst).toList());
                }
            ));
        }

        refreshAfterMove(sourceDir);
    }

    private void refreshAfterMoveAndSelect(@Nullable Path focusDir, List<Path> toSelect) {
        AkytheraEditorLayoutManager.getInstance().<MainEditorScreen>ifLastScreen(mainEditorScreen -> {
            mainEditorScreen.explorerPanel.rebuildTree();
            if (focusDir != null) mainEditorScreen.explorerPanel.focusNodeByPath(focusDir);
            for (int i = 0; i < toSelect.size(); i++) {
                mainEditorScreen.contentBrowserPanel.pickSelections(i == 0, toSelect.get(i));
            }
        });
    }

    private static boolean doMove(AkytheraProject project, Path from, Path to) {
        Path assetsRoot = project.getProjectRoot().resolve("assets");
        Path dataRoot   = project.getProjectRoot().resolve("data");

        if (!FileSystemHelper.move(from, to)) return false;

        if (from.startsWith(assetsRoot)) project.renameAsset(from, to);
        else if (from.startsWith(dataRoot)) project.moveEntry(from, to);
        return true;
    }

    private void refreshAfterMove(@Nullable Path focus) {
        AkytheraEditorLayoutManager.getInstance().<MainEditorScreen>ifLastScreen(mainEditorScreen -> {
            mainEditorScreen.explorerPanel.rebuildTree();
            if (focus != null) mainEditorScreen.explorerPanel.focusNodeByPath(focus);
        });
    }

    private static @Nullable Path findMatchingPath(Path dir, String name) {
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.filter(p -> {
                String fileName = p.getFileName().toString();
                if (Files.isDirectory(p)) return fileName.equals(name);
                String nameNoExt = fileName.contains(".")
                    ? fileName.substring(0, fileName.lastIndexOf('.'))
                    : fileName;
                return nameNoExt.equals(name);
            }).findFirst().orElse(null);
        } catch (IOException e) {
            return null;
        }
    }
}