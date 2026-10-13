package com.yesman.akytheralabs.gui.panel.main;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.mojang.datafixers.util.Pair;
import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.client.api.gui.AkytheraButton;
import com.yesman.akythera.client.api.gui.Grid;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.core.asset.loader.AssetLoader;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.FileSystemHelper;
import com.yesman.akytheralabs.akythera.asset.AnimationMetadataHolder;
import com.yesman.akytheralabs.editor.ContentClipboard;
import com.yesman.akytheralabs.editor.historytrack.FileCreation;
import com.yesman.akytheralabs.editor.historytrack.FileDeletion;
import com.yesman.akytheralabs.editor.historytrack.FileMove;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.keybind.EditorKeyMappings;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.AdjustablePanelManager;
import com.yesman.akytheralabs.gui.panel.main.ExplorerPanel.TreeNode;
import com.yesman.akytheralabs.gui.panel.main.processors.ResourceProcessor;
import com.yesman.akytheralabs.gui.panel.main.processors.SoundResourceProcessor;
import com.yesman.akytheralabs.gui.screen.MainEditorScreen;
import com.yesman.akytheralabs.gui.widget.*;
import com.yesman.akytheralabs.gui.widget.RefusalPopup;
import com.yesman.akytheralabs.gui.widget.main.ContentWidget;
import com.yesman.akytheralabs.gui.widget.main.EntityTypeSelectModal;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
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
    private static final int CONTEXT_MENU_W = 140;

    /// The browser's right-click menu, with every option at a fixed position. Empty space, a folder
    /// and a content widget differ only in which of them they answer (see
    /// [ResourceProcessor#backgroundActions] / [ResourceProcessor#folderActions] /
    /// [ResourceProcessor#contextActions]); the rest stay in place, greyed out and unpressable.
    private static final List<UniversalMenu.Row<ContentContextOption>> CONTEXT_MENU_ROWS = List.of(
        new UniversalMenu.OptionRow<>(ContentContextOption.OPEN),
        new UniversalMenu.OptionRow<>(ContentContextOption.OPEN_IN_FILE_EXPLORER),
        new UniversalMenu.OptionRow<>(ContentContextOption.COMPILE),
        new UniversalMenu.GroupRow<ContentContextOption>(Component.translatable(LangKeys.GUI_MAIN_SCREEN_CONTENT_BROWSER_CONTEXT_CREATE), List.of(
            new UniversalMenu.OptionRow<>(ContentContextOption.CREATE_ANIM_SEQUENCE),
            new UniversalMenu.OptionRow<>(ContentContextOption.CREATE_ANIM_MONTAGE),
            new UniversalMenu.OptionRow<>(ContentContextOption.CREATE_ARMATURE_PHYSICS),
            new UniversalMenu.OptionRow<>(ContentContextOption.CREATE_CLOTH_PHYSICS)
        )),
        new UniversalMenu.GroupRow<ContentContextOption>(Component.translatable(LangKeys.GUI_MAIN_SCREEN_CONTENT_BROWSER_CONTEXT_ADD_NEW), List.of(
            new UniversalMenu.OptionRow<>(ContentContextOption.NEW_FOLDER),
            new UniversalMenu.OptionRow<>(ContentContextOption.NEW_ITEM)
        )),
        new UniversalMenu.OptionRow<>(ContentContextOption.CUT),
        new UniversalMenu.OptionRow<>(ContentContextOption.COPY),
        new UniversalMenu.OptionRow<>(ContentContextOption.PASTE),
        new UniversalMenu.OptionRow<>(ContentContextOption.RENAME),
        new UniversalMenu.OptionRow<>(ContentContextOption.DELETE_SELECTED)
    );

    private final Font font;
    private final PanelHeader header;
    private final EditorEditBox<String> searchBox;
    private final AkytheraButton addButton;
    private final Grid grid;

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
        this.searchBox = EditorEditBox.stringBox(
            font,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(ADD_BUTTON_W + PADDING + 6),
            AnchoredWidget.pixel((HEADER_H - HEADER_WIDGET_H) / 2), AnchoredWidget.pixel(HEADER_WIDGET_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            () -> "",
            _ -> onSearchChanged(),
            Component.translatable(LangKeys.GUI_MESSAGE_EMPTY_SEARCHBAR)
        );

        this.addButton = AkytheraButton
            .buttonBuilder(font, LocalizedMessage.EMPTY, () -> onAddPressed())
            .xUnits(AnchoredWidget.pixel(PADDING), AnchoredWidget.pixel(ADD_BUTTON_W))
            .yUnits(AnchoredWidget.pixel((HEADER_H - HEADER_WIDGET_H) / 2), AnchoredWidget.pixel(HEADER_WIDGET_H))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.TOP_HEIGHT)
            .theme(AkytheraLabsButtonTheme.AKYTHERA_CONTENT_ADD_BUTTON)
            .build();

        this.grid = new Grid(
            AnchoredWidget.pixel(GRID_PADDING),
            AnchoredWidget.pixel(GRID_PADDING),
            AnchoredWidget.pixel(GRID_PADDING + HEADER_H),
            AnchoredWidget.pixel(GRID_PADDING),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM,
            0,
            0,
            GRID_PADDING,
            Comparator.<AbstractWidget, Boolean>comparing(w -> !(w instanceof ContentWidget.FolderContent))
                .thenComparing(w -> ((ContentWidget<?>) w).getContentName(), String.CASE_INSENSITIVE_ORDER)
        ) {
            @Override
            protected int cellWidth(ScreenRectangle gridRectangle) {
                return Math.clamp((gridRectangle.width() - SCROLLBAR_WIDTH) / 4 - GRID_PADDING, 40, 70);
            }

            @Override
            protected int cellHeight(ScreenRectangle gridRectangle) {
                return cellWidth(gridRectangle);
            }
        };

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

    /// Public accessor for [ResourceProcessor#backgroundActions] and any other
    /// path that needs to fire the green-cross "+" handler externally.
    public void triggerAddPressed() {
        onAddPressed();
    }

    /// Whether the browsed directory takes a new subdirectory — the category the browser is
    /// currently showing has to allow subdirectories.
    public boolean canCreateFolder() {
        return selectedNode != null
            && selectedNode.directoryPath != null
            && selectedNode.processor != null
            && selectedNode.processor.canCreateSubDirectories();
    }

    /// Creates a subdirectory under the browsed directory and opens its rename box, with the
    /// creation pushed to [HistoryManager] so Ctrl+Z removes it again.
    public void triggerCreateFolder() {
        if (!canCreateFolder()) return;

        Path parentDir = selectedNode.directoryPath;
        Path newDir = parentDir.resolve(FileSystemHelper.findNextAvailableName(parentDir, "new_folder", ""));

        if (!createDirectory(newDir)) return;

        AkytheraProject project = AkytheraProject.getCurrentProject();

        HistoryManager.getInstance().pushHistory(new FileCreation(
            () -> {
                if (createDirectory(newDir)) refreshAfterFolderCreated(parentDir, null);
            },
            () -> {
                project.deletePathRecursive(newDir);
                refreshAfterFolderCreated(parentDir, null);
            }
        ));

        refreshAfterFolderCreated(parentDir, newDir);
    }

    private static boolean createDirectory(Path dir) {
        try {
            Files.createDirectories(dir);
            return true;
        } catch (IOException e) {
            AkytheraLabs.LOGGER.error("Failed to create directory {}", dir, e);
            return false;
        }
    }

    /// Rebuilds the tree and the grid around `parentDir`, then selects `editPath` and opens its
    /// rename box when one is given.
    private void refreshAfterFolderCreated(Path parentDir, @Nullable Path editPath) {
        AkytheraEditorLayoutManager.getInstance().<MainEditorScreen>ifLastScreen(mainEditorScreen -> {
            mainEditorScreen.explorerPanel.rebuildTree();
            mainEditorScreen.explorerPanel.focusNodeByPath(parentDir);

            if (editPath == null) return;

            for (ContentWidget<?> widget : contentWidgets()) {
                if (!editPath.equals(widget.getPath())) continue;

                pickSelections(true, editPath);
                setFocused(grid);
                grid.setFocused(widget);
                widget.startEditing();

                return;
            }
        });
    }

    /// Public accessor for processor widget menus that need to invoke the
    /// shared "Delete Selected" popup flow.
    public void triggerDeleteSelected() {
        if (!canDeleteSelection()) return;
        showDeleteSelectedPopup();
    }

    /// Whether the selection can be deleted: it holds no reserved directory.
    public boolean canDeleteSelection() {
        return !selectedWidgets.isEmpty() && !selectionHoldsReservedDirectory();
    }

    /// Whether the selection holds one of [AkytheraProject#RESERVED_DIRECTORIES], or a directory on the
    /// way to one.
    private boolean selectionHoldsReservedDirectory() {
        AkytheraProject project = AkytheraProject.getCurrentProject();

        return selectedWidgets.stream().anyMatch(widget -> project.isReservedDirectory(widget.getPath()));
    }

    private void rebuildGrid() {
        clearContentSelection();
        grid.clearComponents();
        String query = searchBox.getText().toLowerCase();

        if (selectedNode != null && selectedNode.directoryPath != null && Files.isDirectory(selectedNode.directoryPath)) {
            List<ContentWidget<?>> folders = new ArrayList<>();
            List<ContentWidget<?>> files = new ArrayList<>();

            AkytheraProject project = AkytheraProject.getCurrentProject();
            Path assetsRoot = project.getProjectRoot().resolve("assets");

            if (selectedNode.directoryPath.equals(assetsRoot)) {
                collectAssetRegistryFolders(assetsRoot, query, folders);
            } else {
                try (Stream<Path> entries = Files.list(selectedNode.directoryPath)) {
                    entries.sorted(Comparator.comparing(p -> p.getFileName().toString()))
                        .forEach(path -> {
                            String name = path.getFileName().toString();

                            if (Files.isDirectory(path)) {
                                ContentWidget<?> w = new ContentWidget.FolderContent(font, name, path, !project.isReservedDirectory(path));

                                if (query.isEmpty() || name.toLowerCase().contains(query)) {
                                    folders.add(w);
                                }
                            } else if (Files.isRegularFile(path) && (!name.endsWith(AssetLoader.METADATA_EXTENSION) || project.isSoundEvent(path))) {
                                // A sidecar belongs to the asset next to it, not to the grid — the
                                // processors parse whatever file they are handed, and a sidecar is not
                                // an asset of any of their types. A sound event is no sidecar.
                                ContentWidget<?> w = null;

                                if (selectedNode.processor != null) {
                                    try {
                                        w = selectedNode.processor.createContentWidget(font, path);
                                    } catch (NoSuchElementException e) {
                                        AkytheraLabs.LOGGER.error(
                                            "Couldn't find registered object parsed from {}. This error means the data file is corrupted",
                                            path);
                                    }
                                }

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
            }

            folders.forEach(widget -> grid.addComponent(widget.getPath().toString(), widget));
            files.forEach(widget -> grid.addComponent(widget.getPath().toString(), widget));
        }

        grid.sort();
        header.setMessage(Component.translatable(LangKeys.GUI_MAIN_SCREEN_WIDGET_CONTENT_BROWSER, contentWidgets().size()));

        int titleWidth = font.width(header.getMessage().getString());
        searchBox.setX1(AnchoredWidget.pixel(6 + titleWidth + 10));

        ScreenRectangle rect = getRectangle();
        grid.relocate(rect);
        searchBox.relocate(rect);
        addButton.relocate(rect);
    }

    /// The `assets` registry folders live one directory deeper, under the shared `animmodels`
    /// directory, so the browser collects them from [AkytheraProject#ASSETS_FOLDERS] instead of
    /// listing `assets` itself. They are fixed project layout, hence no rename box.
    private void collectAssetRegistryFolders(Path assetsRoot, String query, List<ContentWidget<?>> folders) {
        for (String directory : AkytheraProject.ASSETS_FOLDERS) {
            Path path = assetsRoot.resolve(directory);
            String name = path.getFileName().toString();

            if (query.isEmpty() || name.toLowerCase().contains(query)) {
                folders.add(new ContentWidget.FolderContent(font, name, path, false));
            }
        }
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
        grid.addComponent(widget.getPath().toString(), widget);
        header.setMessage(Component.translatable(LangKeys.GUI_MAIN_SCREEN_WIDGET_CONTENT_BROWSER, contentWidgets().size()));
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

                UniversalMenu.Actions<ContentContextOption> actions = (hovered != null)
                    ? hovered instanceof ContentWidget.FolderContent
                        ? ResourceProcessor.folderActions(this, hovered)
                        : selectedNode.processor.contextActions(this, hovered)
                    : ResourceProcessor.backgroundActions(this);

                ModalManager.getInstance().openModalWidget(
                    UniversalMenu.buildPage(CONTEXT_MENU_ROWS, actions, CONTEXT_MENU_W),
                    (int) event.x(),
                    (int) event.y()
                );

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
        if (event.button() == 0 && canCutSelection()) {
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
        ContentWidget<?> editing = (ContentWidget<?>) grid.getFocused();

        // While a rename box is open the panel yields the whole keyboard to it — the shortcuts
        // below overlap what a text field does with the same keys. The explorer's tree rename box
        // counts as well: key presses fan out to every panel, focused or not.
        if ((editing != null && editing.isEditing()) || isExplorerEditingName()) {
            return super.keyPressed(event);
        }

        // Global shortcut: select every content widget regardless of which panel is focused.
        if (EditorKeyMappings.SELECT_ALL.matches(event) && !contentWidgets().isEmpty()) {
            for (ContentWidget<?> w : contentWidgets()) {
                w.setSelected(true);
                selectedWidgets.add(w);
            }

            shiftSelectAnchor = contentWidgets().getFirst();

            return true;
        }

        if (EditorKeyMappings.COPY.matches(event)) {
            return putSelectionOnClipboard(false);
        }

        if (EditorKeyMappings.CUT.matches(event)) {
            return putSelectionOnClipboard(true);
        }

        if (EditorKeyMappings.PASTE.matches(event)) {
            return pasteInto(getBrowsedDirectory());
        }

        // Rename the single selected widget (skipped for multi-select / non-editable).
        if (EditorKeyMappings.RENAME.matches(event) && selectedWidgets.size() == 1) {
            ContentWidget<?> target = selectedWidgets.iterator().next();
            if (target.isEditable()) {
                setFocused(grid);
                grid.setFocused(target);
                target.startEditing();

                return true;
            }
        }

        return super.keyPressed(event);
    }

    private boolean isExplorerEditingName() {
        MutableBoolean editing = new MutableBoolean(false);

        AkytheraEditorLayoutManager.getInstance().<MainEditorScreen>ifLastScreen(mainEditorScreen ->
            editing.setValue(mainEditorScreen.explorerPanel.isEditingName()));

        return editing.booleanValue();
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

    /// The grid's cells, which are all [ContentWidget]s.
    @SuppressWarnings("unchecked")
    private List<ContentWidget<?>> contentWidgets() {
        return (List<ContentWidget<?>>) (List<?>) grid.children();
    }

    public List<ContentWidget<?>> getSelectedWidgets() {
        return selectedWidgets.stream().sorted(Comparator.comparing(w -> contentWidgets().indexOf(w))).toList();
    }

    public @Nullable ContentWidget<?> getHoveredContentWidget(double x, double y) {
        for (ContentWidget<?> w : contentWidgets()) {
            if (w.isMouseOver(x, y)) return w;
        }

        return null;
    }

    public void pickSelections(boolean removeCurrentSelections, Path path) {
        if (removeCurrentSelections) selectedWidgets.clear();

        for (ContentWidget<?> w : contentWidgets()) {
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
            List<ContentWidget<?>> all = contentWidgets();
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

    // ******************************** Clipboard ********************************

    /// The directory the grid is listing, or null while no node is selected.
    public @Nullable Path getBrowsedDirectory() {
        return selectedNode == null ? null : selectedNode.directoryPath;
    }

    /// Whether the selection can be cut, or dragged to another directory. One grid shows one category,
    /// so this asks the browsed node once rather than each widget. A reserved directory never moves.
    public boolean canCutSelection() {
        return selectedNode != null
            && selectedNode.processor != null
            && !selectedWidgets.isEmpty()
            && !selectionHoldsReservedDirectory();
    }

    /// Whether the selection can be copied — [ResourceProcessor#canDuplicate] on top of a cut's rule.
    public boolean canCopySelection() {
        return canCutSelection() && selectedNode.processor.canDuplicate();
    }

    /// Puts the selection on [ContentClipboard]. Returns whether anything was put, so the shortcut
    /// falls through to the other panels when it wasn't.
    public boolean putSelectionOnClipboard(boolean cut) {
        if (cut ? !canCutSelection() : !canCopySelection()) return false;

        List<Path> paths = getSelectedWidgets().stream()
            .map(ContentWidget::getPath)
            .filter(Files::exists)
            .toList();

        if (paths.isEmpty()) return false;

        ContentClipboard.getInstance().put(paths, cut, selectedNode.processor);

        return true;
    }

    /// Whether the clipboard can land in `targetDir`: the browsed category owns the copied entries,
    /// the target is a real directory outside everything being pasted, and a directory on the
    /// clipboard needs a category that takes subdirectories.
    public boolean canPasteInto(@Nullable Path targetDir) {
        ContentClipboard clipboard = ContentClipboard.getInstance();

        if (targetDir == null || clipboard.isEmpty()) return false;
        if (selectedNode == null || selectedNode.processor != clipboard.processor()) return false;
        if (!Files.isDirectory(targetDir)) return false;

        // A category whose id path opens with the entry's namespace has no room for an entry
        // directly in its registry root.
        if (selectedNode.processor.pathEncodesNamespace()) {
            ExplorerPanel.TreeNode regRoot = ResourceProcessor.regRootOf(selectedNode);

            if (regRoot != null && targetDir.equals(regRoot.directoryPath)) return false;
        }

        for (Path source : clipboard.paths()) {
            if (targetDir.startsWith(source)) return false;
            if (Files.isDirectory(source) && !selectedNode.processor.canCreateSubDirectories()) return false;
        }

        return true;
    }

    /// Pastes the clipboard into `targetDir`. A cut runs the drag-drop move and empties the
    /// clipboard; a copy duplicates each entry under a free name and pushes one [FileCreation] for
    /// the batch, so a single Ctrl+Z takes the whole paste back.
    public boolean pasteInto(@Nullable Path targetDir) {
        if (!canPasteInto(targetDir)) return false;

        ContentClipboard clipboard = ContentClipboard.getInstance();
        List<Path> sources = clipboard.paths().stream().filter(Files::exists).toList();

        if (sources.isEmpty()) return false;

        if (clipboard.isCut()) {
            List<Path> toMove = sources.stream().filter(p -> !targetDir.equals(p.getParent())).toList();

            if (!toMove.isEmpty()) movePathsTo(toMove, toMove.getFirst().getParent(), targetDir);

            clipboard.clear();

            return true;
        }

        AkytheraProject project = AkytheraProject.getCurrentProject();
        List<Path> pasted = new ArrayList<>(sources.size());

        for (Path source : sources) {
            String refusedTagPath = project.refusedTagPath(source, project.copyTarget(source, targetDir), true);

            if (refusedTagPath != null) {
                RefusalPopup.open(
                    Component.translatable(LangKeys.GUI_MAIN_SCREEN_CONTENT_BROWSER_POPUP_DATA_BLOCK_TAKEN_TITLE),
                    Component.translatable(LangKeys.GUI_MAIN_SCREEN_CONTENT_BROWSER_POPUP_DATA_BLOCK_TAKEN_BODY, refusedTagPath)
                );

                break;
            }

            Path created = project.copyInto(source, targetDir);
            if (created != null) pasted.add(created);
        }

        if (pasted.isEmpty()) return false;

        // Captured AFTER the register walk, so the closures hold the registered holder / accessor
        // instances and a redo brings the same ones back.
        List<Runnable> restores = pasted.stream().map(project::buildRestoreFor).toList();

        HistoryManager.getInstance().pushHistory(new FileCreation(
            () -> {
                restores.forEach(Runnable::run);
                refreshAndSelect(targetDir, pasted);
            },
            () -> {
                pasted.forEach(project::deletePathRecursive);
                refreshExplorerAndBrowser(targetDir);
            }
        ));

        refreshAndSelect(targetDir, pasted);

        return true;
    }

    private void showDeleteSelectedPopup() {
        int count = selectedWidgets.size();
        int popupW = 300;
        int btnW = WidgetContainerModal.evenButtonWidth(popupW, 2);

        WidgetContainerModal popup = WidgetContainerModal.builder(font, Component.translatable(LangKeys.GUI_MAIN_SCREEN_CONTENT_BROWSER_POPUP_DELETE_TITLE), popupW)
            .textLines(Component.translatable(LangKeys.GUI_MAIN_SCREEN_CONTENT_BROWSER_POPUP_DELETE_BODY, count))
            .footerButton(
                AkytheraButton.buttonBuilder(font, LocalizedMessage.translatable(LangKeys.GUI_CANCEL),
                        () -> ModalManager.getInstance().closeModal())
                    .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
                    .build(),
                btnW
            )
            .footerButton(
                AkytheraButton.buttonBuilder(font, LocalizedMessage.translatable(LangKeys.GUI_MESSAGE_BUTTON_REMOVE), () -> {
                    deleteSelectedItems();
                    ModalManager.getInstance().closeModal();
                })
                .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
                .build(),
                btnW
            )
            .build();

        ModalManager.getInstance().openModalWidgetAtCenter(popup);
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
                    AkytheraButton.buttonBuilder(font, LocalizedMessage.translatable(LangKeys.GUI_CANCEL),
                            () -> ModalManager.getInstance().closeModal())
                        .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
                        .build(),
                    btnW
                )
                .footerButton(
                    AkytheraButton.buttonBuilder(font, LocalizedMessage.translatable(LangKeys.GUI_MESSAGE_BUTTON_MOVE), () -> {
                        ModalManager.getInstance().closeModal();
                        moveSelectedItemsTo(destNode.directoryPath);
                    })
                    .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
                    .build(),
                    btnW
                )
                .build();

            ModalManager.getInstance().openModalWidgetAtCenter(popup);
        });
    }

    // ******************************** External asset import ********************************

    /// Drives the sequential import of one batch of externally-sourced files. Held mutable
    /// across the user-driven modal chain (duplicate prompts, error prompts) so each resumed
    /// callback knows where it left off.
    private static final class ImportSession {
        private final List<Path> sources;
        private final Path targetDir;
        private final ResourceProcessor<?> processor;
        private final boolean allowOverwriteAll;
        /// The animations this batch landed, in import order.
        private final List<AkytheraProject.EditorAnimationAccessor> importedAnimations = new ArrayList<>();
        /// The sound files this batch landed, in import order.
        private final List<Path> importedSoundFiles = new ArrayList<>();
        private int index;
        private boolean overwriteAll;
        private boolean anyImported;

        ImportSession(List<Path> sources, Path targetDir, ResourceProcessor<?> processor) {
            this.sources = sources;
            this.targetDir = targetDir;
            this.processor = processor;
            // "Overwrite (to All)" is only meaningful when more than one file is being imported.
            this.allowOverwriteAll = sources.size() > 1;
        }
    }

    /// Imports externally-sourced asset files (a drag-drop batch or the file dialog behind the
    /// `+` button) into `targetDir`. Each file is copied into the project and parsed; a parse
    /// failure surfaces the loader's own message in an error modal, and a name collision in
    /// `targetDir` prompts the user to Overwrite / Overwrite-all / Cancel. Processing is
    /// sequential because each prompt is modal. Files `processor` doesn't
    /// [ResourceProcessor#canImport] are left out. A copy is named in lowercase, with every character an
    /// [Identifier] path refuses replaced by `_`.
    public void importExternalAssets(List<Path> sources, Path targetDir, ResourceProcessor<?> processor) {
        List<Path> valid = sources.stream().filter(file -> processor.canImport(file, sources)).toList();

        if (valid.isEmpty()) return;

        importNext(new ImportSession(valid, targetDir, processor));
    }

    private void importNext(ImportSession session) {
        if (session.index >= session.sources.size()) {
            finishImport(session);
            return;
        }

        Path source = session.sources.get(session.index);
        String fileName = Util.sanitizeName(source.getFileName().toString(), Identifier::validPathChar);
        Path destination = session.targetDir.resolve(fileName);

        if (Files.exists(destination) && !session.overwriteAll) {
            showDuplicatePopup(
                fileName,
                session.allowOverwriteAll,
                () -> finishImport(session),
                () -> importCurrentAndAdvance(session, source, destination),
                () -> {
                    session.overwriteAll = true;
                    importCurrentAndAdvance(session, source, destination);
                }
            );
        } else {
            importCurrentAndAdvance(session, source, destination);
        }
    }

    private void importCurrentAndAdvance(ImportSession session, Path source, Path destination) {
        AkytheraProject.AssetLoadResult<?> result = session.processor.importFile(source, destination);
        session.index++;

        if (result.failed()) {
            // Acknowledge the error before moving on so only one modal is ever open at a time.
            showLoadErrorPopup(result.error(), () -> importNext(session));
        } else {
            session.anyImported = true;

            if (result.accessor() instanceof AkytheraProject.EditorAnimationAccessor animation) {
                session.importedAnimations.add(animation);
            }

            if (result.accessor() instanceof Path file && AkytheraProject.getCurrentProject().isSoundAsset(file)) {
                session.importedSoundFiles.add(file);
            }

            importNext(session);
        }
    }

    private void finishImport(ImportSession session) {
        if (!session.anyImported) return;

        refreshExplorerAndBrowser(session.targetDir);

        if (!session.importedAnimations.isEmpty()) askPreviewModelFor(session.importedAnimations);
        if (!session.importedSoundFiles.isEmpty()) SoundResourceProcessor.askEventLayout(font, session.importedSoundFiles);
    }

    /// Asks which entity an animation asset screen previews `animations` on, and writes the pick
    /// into every one of their sidecars. Dismissing the modal leaves them without a preview entity.
    private void askPreviewModelFor(List<AkytheraProject.EditorAnimationAccessor> animations) {
        EntityTypeSelectModal modal = new EntityTypeSelectModal(font, null, previewEntityType -> {
            for (AkytheraProject.EditorAnimationAccessor animation : animations) {
                AnimationMetadataHolder metadata = animation.metadata();
                metadata.setPreviewEntityType(previewEntityType);
                metadata.save();
            }
        });

        ModalManager.getInstance().openModalWidgetAtCenter(modal);
    }

    /// The "file already exists" prompt: `Overwrite`, `Overwrite All` while `allowAll`, and `Cancel`,
    /// which abandons the rest of the batch. Every button closes the modal before its handler runs,
    /// so only one is ever open at a time.
    private void showDuplicatePopup(
        String fileName, boolean allowAll, Runnable onCancel, Runnable onOverwrite, Runnable onOverwriteAll
    ) {
        int popupW = allowAll ? 360 : 300;
        int btnW = WidgetContainerModal.evenButtonWidth(popupW, allowAll ? 3 : 2);

        WidgetContainerModal.Builder builder = WidgetContainerModal.builder(
                font, Component.translatable(LangKeys.GUI_MAIN_SCREEN_CONTENT_BROWSER_POPUP_DUPLICATE_TITLE), popupW)
            .textLines(Component.translatable(LangKeys.GUI_MAIN_SCREEN_CONTENT_BROWSER_POPUP_DUPLICATE_BODY, fileName))
            // Footer buttons render right-to-left in insertion order, so add Cancel first
            // (rightmost) and Overwrite last (leftmost) to read "Overwrite ... Cancel".
            .footerButton(closingFooterButton(LangKeys.GUI_CANCEL, onCancel), btnW);

        if (allowAll) {
            builder.footerButton(closingFooterButton(LangKeys.GUI_MESSAGE_BUTTON_OVERWRITE_ALL, onOverwriteAll), btnW);
        }

        builder.footerButton(closingFooterButton(LangKeys.GUI_MESSAGE_BUTTON_OVERWRITE, onOverwrite), btnW);

        ModalManager.getInstance().openModalWidgetAtCenter(builder.build());
    }

    /// A modal footer button that closes its modal, then runs `onPress`.
    private AkytheraButton closingFooterButton(String langKey, Runnable onPress) {
        return AkytheraButton.buttonBuilder(font, LocalizedMessage.translatable(langKey), () -> {
                ModalManager.getInstance().closeModal();
                onPress.run();
            })
            .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
            .build();
    }

    private void showLoadErrorPopup(@Nullable String message, Runnable onAcknowledge) {
        int popupW = 320;
        int btnW = WidgetContainerModal.evenButtonWidth(popupW, 1);

        WidgetContainerModal popup = WidgetContainerModal.builder(
                font, Component.translatable(LangKeys.GUI_MAIN_SCREEN_CONTENT_BROWSER_POPUP_LOAD_ERROR_TITLE), popupW)
            .textLines(Component.literal(message != null ? message : ""))
            .footerButton(
                AkytheraButton.buttonBuilder(font, LocalizedMessage.translatable(LangKeys.GUI_OK), () -> {
                        ModalManager.getInstance().closeModal();
                        onAcknowledge.run();
                    })
                    .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
                    .build(),
                btnW
            )
            .build();

        ModalManager.getInstance().openModalWidgetAtCenter(popup);
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
        List<Path> sources = selectedWidgets.stream()
            .map(widget -> findMatchingPath(sourceDir, widget.getContentName()))
            .filter(Objects::nonNull)
            .toList();

        movePathsTo(sources, sourceDir, destinationPath);
    }

    /// Drives one batch of moves through the duplicate prompts. Held mutable across the modal chain,
    /// like [ImportSession], so each resumed callback knows where it left off.
    private static final class MoveSession {
        private final List<Path> sources;
        private final Path sourceDir;
        private final Path targetDir;
        private final boolean allowOverwriteAll;
        /// The pairs that landed, and — at the same index — the restore of whatever each one
        /// overwrote. A free destination captures an empty closure.
        private final List<Pair<Path, Path>> moved = new ArrayList<>();
        private final List<Runnable> overwritten = new ArrayList<>();
        private int index;
        private boolean overwriteAll;

        MoveSession(List<Path> sources, Path sourceDir, Path targetDir) {
            this.sources = sources;
            this.sourceDir = sourceDir;
            this.targetDir = targetDir;
            // "Overwrite (to All)" is only meaningful when more than one file is being moved.
            this.allowOverwriteAll = sources.size() > 1;
        }
    }

    /// Moves `sources` into `destinationPath`, prompting per occupied name, and pushes one [FileMove]
    /// for whatever landed — so a single Ctrl+Z puts every file back and restores what it overwrote.
    private void movePathsTo(List<Path> sources, Path sourceDir, Path destinationPath) {
        moveNext(new MoveSession(sources, sourceDir, destinationPath));
    }

    private void moveNext(MoveSession session) {
        if (session.index >= session.sources.size()) {
            finishMove(session);
            return;
        }

        Path source = session.sources.get(session.index);
        Path destination = session.targetDir.resolve(source.getFileName());
        String refusedTagPath = AkytheraProject.getCurrentProject().refusedTagPath(source, destination, false);

        if (refusedTagPath != null) {
            finishMove(session);
            RefusalPopup.open(
                Component.translatable(LangKeys.GUI_MAIN_SCREEN_CONTENT_BROWSER_POPUP_DATA_BLOCK_TAKEN_TITLE),
                Component.translatable(LangKeys.GUI_MAIN_SCREEN_CONTENT_BROWSER_POPUP_DATA_BLOCK_TAKEN_BODY, refusedTagPath)
            );
        } else if (Files.exists(destination) && !session.overwriteAll) {
            showDuplicatePopup(
                source.getFileName().toString(),
                session.allowOverwriteAll,
                () -> finishMove(session),
                () -> moveCurrentAndAdvance(session, source, destination),
                () -> {
                    session.overwriteAll = true;
                    moveCurrentAndAdvance(session, source, destination);
                }
            );
        } else {
            moveCurrentAndAdvance(session, source, destination);
        }
    }

    private void moveCurrentAndAdvance(MoveSession session, Path source, Path destination) {
        AkytheraProject project = AkytheraProject.getCurrentProject();
        session.index++;

        // Captured before the destination is cleared, so undo can bring back what this overwrote.
        Runnable restore = project.buildRestoreFor(destination);
        project.deletePathRecursive(destination);

        if (doMove(project, source, destination)) {
            session.moved.add(Pair.of(source, destination));
            session.overwritten.add(restore);
        } else {
            restore.run();
        }

        moveNext(session);
    }

    private void finishMove(MoveSession session) {
        AkytheraProject project = AkytheraProject.getCurrentProject();
        List<Pair<Path, Path>> moved = List.copyOf(session.moved);
        List<Runnable> overwritten = List.copyOf(session.overwritten);
        Path sourceDir = session.sourceDir;
        Path targetDir = session.targetDir;

        if (!moved.isEmpty()) {
            HistoryManager.getInstance().pushHistory(new FileMove(
                () -> {
                    for (Pair<Path, Path> pair : moved) {
                        // The undo put the overwritten file back; clear it again before re-moving.
                        project.deletePathRecursive(pair.getSecond());
                        doMove(project, pair.getFirst(), pair.getSecond());
                    }

                    refreshAndSelect(targetDir, moved.stream().map(Pair::getSecond).toList());
                },
                () -> {
                    for (int i = 0; i < moved.size(); i++) {
                        // Restore only once the moved file has vacated the destination.
                        if (doMove(project, moved.get(i).getSecond(), moved.get(i).getFirst())) {
                            overwritten.get(i).run();
                        }
                    }

                    refreshAndSelect(sourceDir, moved.stream().map(Pair::getFirst).toList());
                }
            ));
        }

        refreshAfterMove(moved.isEmpty() ? sourceDir : targetDir);
    }

    /// Rebuilds the tree, focuses `focusDir` and selects `toSelect` in the grid.
    private void refreshAndSelect(@Nullable Path focusDir, List<Path> toSelect) {
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