package com.yesman.akytheralabs.gui.panel.main;

import com.yesman.akythera.client.gui.widget.AnchoredButton;
import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.FileSystemHelper;
import com.yesman.akytheralabs.dataholder.datablock.DataBlockRegistries;
import com.yesman.akytheralabs.editor.historytrack.FileCreation;
import com.yesman.akytheralabs.editor.historytrack.FileDeletion;
import com.yesman.akytheralabs.editor.historytrack.FileMove;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.AdjustablePanelManager;
import com.yesman.akytheralabs.gui.panel.main.processors.ResourceProcessor;
import com.yesman.akytheralabs.gui.screen.MainEditorScreen;
import com.yesman.akytheralabs.gui.widget.AdvancedEditBox;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsButtonTheme;
import com.yesman.akytheralabs.gui.widget.ContextMenuContainer;
import com.yesman.akytheralabs.gui.widget.WidgetContainerModal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ExplorerPanel extends AdjustablePanelManager.PanelArea {
    private static final Component EXPLORER_TITLE = Component.translatable(LangKeys.GUI_MAIN_SCREEN_WIDGET_EXPLORER);
    private static final int ROW_H = 18;
    private static final int INDENT = 12;
    private static final int HEADER_H = 22;
    private static final int SEARCH_H = 18;
    private static final int BTN_W = 14;
    private static final int BTN_H = 14;
    private static final int BTN_PAD = 2;
    private static final int BTN_RIGHT = 8;

    private final Font font;
    private final List<TreeNode> roots;

    private final AdvancedEditBox<String> searchBox;
    private final AnchoredButton addButton;
    private final AnchoredButton removeButton;
    private TreeList treeList;

    private boolean lockNextFocus;

    public ExplorerPanel(Font font) {
        this.font = font;
        this.roots = buildTree();
        applyFolderStates(roots, AkytheraProject.getCurrentProject().getSavedFolderStates());

        this.searchBox = AdvancedEditBox.stringBox(
            font,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(1),
            AnchoredWidget.pixel(HEADER_H), AnchoredWidget.pixel(SEARCH_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            () -> "",
            _ -> treeList.rebuildEntries(),
            Component.translatable(LangKeys.GUI_MESSAGE_EMPTY_SEARCHBAR)
        );

        this.addButton = AnchoredButton.buttonBuilder(font, Component.literal("+"), _ -> onAddPressed())
            .xUnits(AnchoredWidget.pixel(BTN_RIGHT + BTN_PAD * 2 + BTN_W), AnchoredWidget.pixel(BTN_W))
            .yUnits(AnchoredWidget.pixel((HEADER_H - BTN_H) / 2), AnchoredWidget.pixel(BTN_H))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.TOP_HEIGHT)
            .theme(AkytheraLabsButtonTheme.AKYTHERA_EXPLORER_BUTTON)
            .build();

        this.removeButton = AnchoredButton.buttonBuilder(font, Component.literal("-"), _ -> onRemovePressed())
            .xUnits(AnchoredWidget.pixel(BTN_RIGHT + BTN_PAD), AnchoredWidget.pixel(BTN_W))
            .yUnits(AnchoredWidget.pixel((HEADER_H - BTN_H) / 2), AnchoredWidget.pixel(BTN_H))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.TOP_HEIGHT)
            .theme(AkytheraLabsButtonTheme.AKYTHERA_EXPLORER_BUTTON)
            .build();
        this.removeButton.active = false;

        this.treeList = new TreeList(font, roots, searchBox, this::onSelectionChanged, this::onAddPressed, this::showRemoveDirectoryPopup);

        addWidget(searchBox);
        addWidget(treeList);
        addWidget(addButton);
        addWidget(removeButton);
    }

    /// Focus to a tree node that matches to the given path, expanding collapsed ancestors so it becomes visible
    public void focusNodeByPath(Path path) {
        TreeNode node = findNodeByPath(path);
        if (node == null) return;

        for (TreeNode ancestor = node.parentNode; ancestor != null; ancestor = ancestor.parentNode) {
            ancestor.expanded = true;
        }

        treeList.rebuildEntries();
        treeList.focusByNode(node);
    }

    /// Find a tree node that matches to the given path, whether or not it is currently visible
    public @Nullable TreeNode findNodeByPath(Path path) {
        for (TreeNode root : roots) {
            TreeNode found = findNodeByPath(root, path);
            if (found != null) return found;
        }

        return null;
    }

    private static @Nullable TreeNode findNodeByPath(TreeNode node, Path path) {
        if (path.equals(node.directoryPath)) return node;

        for (TreeNode child : node.children) {
            TreeNode found = findNodeByPath(child, path);
            if (found != null) return found;
        }

        return null;
    }

    public Map<String, Boolean> collectFolderStates() {
        Map<String, Boolean> states = new LinkedHashMap<>();

        for (TreeNode root : roots) {
            collectFolderStatesRecursive(root, states);
        }

        return states;
    }

    public Path getFocusedPath() {
        return treeList.getSelected() == null
            ? null
            : findSavedParent(treeList.getSelected().node).directoryPath; // directoryPath can't be null here, as the top parents are always assigned with them.
                                                                          // It is only null for newly created nodes.
    }

    private void collectFolderStatesRecursive(TreeNode node, Map<String, Boolean> states) {
        if (node.foldable()) {
            states.put(node.name, node.expanded);
        }

        for (TreeNode child : node.children) {
            collectFolderStatesRecursive(child, states);
        }
    }

    private static void applyFolderStates(List<TreeNode> nodes, Map<String, Boolean> saved) {
        for (TreeNode node : nodes) {
            if (saved.containsKey(node.name)) {
                node.expanded = saved.get(node.name);
            }

            applyFolderStates(node.children, saved);
        }
    }

    private static List<TreeNode> buildTree() {
        AkytheraProject project = AkytheraProject.getCurrentProject();
        Path projectRoot = project.getProjectRoot();
        TreeNode assetsRoot = new TreeNode("assets", false, projectRoot.resolve("assets"), null);
        TreeNode dataRoot = new TreeNode("data", false, projectRoot.resolve("data"), null);

        AkytheraProject.ASSETS_FOLDERS.stream()
            .sorted()
            .forEach(directory -> {
                Path registryDir = projectRoot.resolve("assets").resolve(directory);
                String displayName = Path.of(directory).getFileName().toString();
                ResourceProcessor<?> processor = ResourceProcessor.forTag(directory);
                TreeNode registryNode = new TreeNode(displayName, false, registryDir, processor);
                registryNode.parentNode = assetsRoot;
                scanSubDirectories(registryDir, processor, registryNode);
                assetsRoot.children.add(registryNode);
            });

        AkytheraProject.DATAPACK_REGISTRIES.stream()
            .sorted(Comparator.comparing(key -> key.identifier().getPath()))
            .forEach(key -> {
                Path registryDir = projectRoot.resolve("data").resolve(key.identifier().getPath());
                ResourceProcessor<?> processor = ResourceProcessor.forTag(key);

                // Registry keys may share path prefixes (`script/animator`, `script/entity_patch`);
                // fold them under shared intermediate folder nodes instead of one flat node per key.
                String[] segments = key.identifier().getPath().split("/");
                TreeNode parent = dataRoot;
                Path parentDir = projectRoot.resolve("data");

                for (int i = 0; i < segments.length - 1; i++) {
                    parentDir = parentDir.resolve(segments[i]);
                    parent = getOrCreateGroupingFolder(parent, segments[i], parentDir);
                }

                TreeNode registryNode = new TreeNode(segments[segments.length - 1], false, registryDir, processor);
                registryNode.parentNode = parent;
                scanSubDirectories(registryDir, processor, registryNode);
                parent.children.add(registryNode);
            });

        // Editor-editable EnumerableDataBlock types — grouped under one foldable `datablock`
        // parent (each type's registry path is `datablock/<enumName>`).
        if (!DataBlockRegistries.all().isEmpty()) {
            TreeNode dataBlockRoot = new TreeNode("datablock", false, projectRoot.resolve("data").resolve("datablock"), null);
            dataBlockRoot.parentNode = dataRoot;

            DataBlockRegistries.all().stream()
                .sorted(Comparator.comparing(entry -> entry.registryKey().identifier().getPath()))
                .forEach(entry -> {
                    Path registryDir = projectRoot.resolve("data").resolve(entry.registryKey().identifier().getPath());
                    String displayName = Path.of(entry.registryKey().identifier().getPath()).getFileName().toString();
                    ResourceProcessor<?> processor = entry.processor();
                    TreeNode registryNode = new TreeNode(displayName, false, registryDir, processor);
                    registryNode.parentNode = dataBlockRoot;
                    scanSubDirectories(registryDir, processor, registryNode);
                    dataBlockRoot.children.add(registryNode);
                });

            dataRoot.children.add(dataBlockRoot);
        }

        return new ArrayList<>(List.of(assetsRoot, dataRoot));
    }

    /// Finds or creates a non-modifiable grouping folder (a shared registry-path prefix segment,
    /// like `script`) under `parent`. Grouping folders carry no processor, same as the datablock root.
    private static TreeNode getOrCreateGroupingFolder(TreeNode parent, String name, Path dir) {
        for (TreeNode child : parent.children) {
            if (!child.modifiable && child.processor == null && child.name.equals(name)) {
                return child;
            }
        }

        TreeNode folder = new TreeNode(name, false, dir, null);
        folder.parentNode = parent;
        folder.expanded = true; // Keep registry roots visible by default; saved folder states override this.
        parent.children.add(folder);

        return folder;
    }

    private static void scanSubDirectories(Path dir, @Nullable ResourceProcessor<?> inheritedProcessor, TreeNode parent) {
        if (!Files.isDirectory(dir)) return;

        try (Stream<Path> entries = Files.list(dir)) {
            entries.filter(Files::isDirectory)
                .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                .forEach(subdir -> {
                    String name = subdir.getFileName().toString();
                    TreeNode child = new TreeNode(name, true, subdir, inheritedProcessor);
                    child.parentNode = parent;
                    scanSubDirectories(subdir, inheritedProcessor, child);
                    parent.children.add(child);
                    child.expanded = !child.children.isEmpty();
                });
        } catch (IOException ignored) {
        }
    }

    private void onSelectionChanged(@Nullable TreeNode node) {
        removeButton.active = node != null && node.modifiable;
        addButton.active = node != null
            && node.processor != null
            && node.processor.canCreateSubDirectories();

        AkytheraEditorLayoutManager.getInstance().<MainEditorScreen>ifLastScreen(mainEditorScreen ->
            mainEditorScreen.contentBrowserPanel.onNodeSelected(node));
    }

    private static String findUniqueFolderName(List<TreeNode> siblings) {
        String base = "new_folder";
        Set<String> names = siblings.stream().map(n -> n.name).collect(Collectors.toSet());
        if (!names.contains(base)) return base;
        int i = 1;
        while (names.contains(base + "_" + i)) i++;
        return base + "_" + i;
    }

    private void onAddPressed() {
        TreeNode selected = getSelected();

        if (selected != null && (selected.processor == null || !selected.processor.canCreateSubDirectories())) return;

        List<TreeNode> siblings = selected == null ? roots : selected.children;
        TreeNode newNode = new TreeNode(
            findUniqueFolderName(siblings),
            true,
            null,
            selected != null ? selected.processor : null);
        newNode.parentNode = selected;

        if (selected == null) {
            roots.add(newNode);
        } else {
            selected.children.add(newNode);
            selected.expanded = true;
        }

        treeList.rebuildEntries();
        setFocused(treeList);
        lockNextFocus = true;
        treeList.startEditingNode(newNode);
    }

    private void onRemovePressed() {
        TreeNode selected = getSelected();
        if (selected == null || !selected.modifiable) return;

        int files = FileSystemHelper.countFiles(selected.directoryPath);

        if (files > 0) { // Ask the user before wiping a populated directory
            showRemoveDirectoryPopup();
            return;
        }

        // Empty leaf — no popup. Same buildRestoreFor pipeline as the popup branch;
        // for an empty directory the closure just runs `Files.createDirectories(dirPath)`.
        Path dirPath = selected.directoryPath;
        Path parentDirPath = selected.parentNode != null ? selected.parentNode.directoryPath : null;

        if (dirPath != null) {
            AkytheraProject project = AkytheraProject.getCurrentProject();
            Runnable restore = project.buildRestoreFor(dirPath);
            project.deletePathRecursive(dirPath);
            treeList.setSelected(null);
            removeFromTree(roots, selected);
            treeList.rebuildEntries();

            HistoryManager.getInstance().pushHistory(new FileDeletion(
                () -> {
                    project.deletePathRecursive(dirPath);
                    rebuildTree();
                    if (parentDirPath != null) focusNodeByPath(parentDirPath);
                },
                () -> {
                    restore.run();
                    rebuildTree();
                    focusNodeByPath(dirPath);
                }
            ));
        } else {
            treeList.setSelected(null);
            removeFromTree(roots, selected);
            treeList.rebuildEntries();
        }
    }

    private boolean removeFromTree(List<TreeNode> siblings, TreeNode target) {
        if (siblings.remove(target)) return true;

        for (TreeNode node : siblings) {
            if (removeFromTree(node.children, target)) return true;
        }

        return false;
    }

    public void rebuildTree() {
        roots.clear();
        roots.addAll(buildTree());

        applyFolderStates(roots, AkytheraProject.getCurrentProject().getSavedFolderStates());

        treeList.rebuildEntries();
    }

    // Draw background then header before children so buttons (in header area) render on top.
    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.fill(getX(), getY(), getRight(), getBottom(), bgColor);
        graphics.fill(getX(), getY(), getRight(), getY() + HEADER_H, 0xFF242424);
        graphics.text(font, EXPLORER_TITLE, getX() + 8, getY() + 7, 0xFF999999, false);
        graphics.fill(getRight() - 1, getY(), getRight(), getBottom(), 0xFF2A2A2A);

        for (GuiEventListener child : children()) {
            ((AbstractWidget)child).extractRenderState(graphics, mouseX, mouseY, a);
        }
    }

    @Override
    protected void onPanelResized(ScreenRectangle rect) {
        searchBox.relocate(rect);
        addButton.relocate(rect);
        removeButton.relocate(rect);
        treeList.updateSizeAndPosition(
            rect.width(),
            rect.height() - HEADER_H - SEARCH_H,
            rect.left(),
            rect.top() + HEADER_H + SEARCH_H
        );
    }

    @Override
    public void setFocused(final @Nullable GuiEventListener focused) {
        if (lockNextFocus) {
            lockNextFocus = false;
        } else {
            super.setFocused(focused);
        }
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_F2) {
            TreeNode selected = getSelected();

            if (selected != null && selected.modifiable) {
                treeList.focusByNode(selected);

                for (TreeList.TreeEntry entry : treeList.children()) {
                    if (entry.node == selected) {
                        treeList.startEditing(entry);
                        return true;
                    }
                }
            }
        }

        return super.keyPressed(event);
    }

    public @Nullable TreeNode getSelected() {
        if (treeList == null) return null;
        TreeList.TreeEntry entry = treeList.getSelected();
        return entry != null ? entry.node : null;
    }

    public void setDragHighlightNode(@Nullable TreeNode node) {
        treeList.dragHighlightNode = node;
    }

    public @Nullable TreeNode getNodeAt(int mouseX, int mouseY) {
        for (TreeList.TreeEntry entry : treeList.children()) {
            if (entry.getY() <= mouseY && mouseY < entry.getY() + entry.getHeight()
                    && treeList.getX() <= mouseX && mouseX < treeList.getRight()) {
                return entry.node;
            }
        }
        return null;
    }

    public boolean isCompatibleDrop(@Nullable ResourceProcessor<?> sourceProcessor, @Nullable TreeNode target) {
        if (target == null
            || target.processor == null
            || !target.processor.canCreateSubDirectories()) return false;
        return target.processor == sourceProcessor
            && (treeList.getSelected() == null || target != treeList.getSelected().node);
    }

    private void showRemoveDirectoryPopup() {
        TreeNode selected = getSelected();
        if (selected == null || !selected.modifiable) return;

        int fileCount = FileSystemHelper.countFiles(selected.directoryPath);

        int popupW = 280;
        int btnW = WidgetContainerModal.evenButtonWidth(popupW, 2);

        WidgetContainerModal popup = WidgetContainerModal.builder(font, Component.translatable(LangKeys.GUI_MAIN_SCREEN_EXPLORER_POPUP_REMOVE_DIR_TITLE), popupW)
            .textLines(Component.translatable(LangKeys.GUI_MAIN_SCREEN_EXPLORER_POPUP_REMOVE_DIR_BODY, fileCount))
            .footerButton(
                AnchoredButton.buttonBuilder(font, Component.translatable(LangKeys.GUI_CANCEL),
                        _ -> AkytheraEditorLayoutManager.getInstance().closeModal())
                    .theme(AkytheraLabsButtonTheme.AKYTHERA_EXPLORER_BUTTON)
                    .build(),
                btnW
            )
            .footerButton(
                AnchoredButton.buttonBuilder(font, Component.translatable(LangKeys.GUI_MESSAGE_BUTTON_REMOVE), _ -> {
                    AkytheraEditorLayoutManager.getInstance().closeModal();
                    if (selected.directoryPath == null) return;

                    Path dirPath = selected.directoryPath;
                    @Nullable Path parentDirPath = selected.parentNode != null ? selected.parentNode.directoryPath : null;
                    AkytheraProject project = AkytheraProject.getCurrentProject();

                    Runnable restore = project.buildRestoreFor(dirPath);
                    project.deletePathRecursive(dirPath);

                    treeList.setSelected(null);
                    removeFromTree(roots, selected);
                    treeList.rebuildEntries();
                    if (parentDirPath != null) focusNodeByPath(parentDirPath);

                    HistoryManager.getInstance().pushHistory(new FileDeletion(
                        () -> {
                            project.deletePathRecursive(dirPath);
                            rebuildTree();
                            if (parentDirPath != null) focusNodeByPath(parentDirPath);
                        },
                        () -> {
                            restore.run();
                            rebuildTree();
                            focusNodeByPath(dirPath);
                        }
                    ));
                })
                .theme(AkytheraLabsButtonTheme.AKYTHERA_EXPLORER_BUTTON)
                .build(),
                btnW
            )
            .build();

        AkytheraEditorLayoutManager.getInstance().openModalWidgetAtCenter(popup);
    }

    private static TreeNode findSavedParent(TreeNode from) {
        TreeNode toReturn = from;

        while (toReturn.parentNode != null && toReturn.directoryPath == null) {
            toReturn = toReturn.parentNode;
        }

        return toReturn;
    }

    public static class TreeNode {
        public String name;
        public final boolean modifiable;
        public final List<TreeNode> children;
        public boolean expanded;

        @Nullable
        public Path directoryPath;

        /// The processor that owns this node's asset type. Inherited from the
        /// registry-root ancestor; reference equality on `processor` therefore
        /// tests "same asset type". Null on the two top-level roots.
        @Nullable
        public ResourceProcessor<?> processor;

        @Nullable
        public TreeNode parentNode;

        TreeNode(
            String name,
            boolean modifiable,
            @Nullable Path directoryPath,
            @Nullable ResourceProcessor<?> processor
        ) {
            this.name = name;
            this.modifiable = modifiable;
            this.directoryPath = directoryPath;
            this.processor = processor;
            this.children = new ArrayList<>();
            this.expanded = false;
        }

        public boolean foldable() {
            return !children.isEmpty();
        }
    }

    private static class TreeList extends AbstractSelectionList<TreeList.@NonNull TreeEntry> {
        private static final int SCROLL_BAR_WIDTH = 5;
        private static final int ARROW_W = 10;
        private static final int ENTRY_PADDING = 4;

        private final Font font;
        private final List<TreeNode> roots;
        private final AdvancedEditBox<String> searchBox;
        private final Consumer<@Nullable TreeNode> onSelectionChanged;
        private final ContextMenuContainer contextMenu;

        @Nullable TreeNode dragHighlightNode = null;

        /// Shared name editor, only one entry can be edited at a time.
        private final AdvancedEditBox<String> nameEditor;

        @Nullable
        private TreeEntry editingEntry;

        TreeList(
            Font font,
            List<TreeNode> roots,
            AdvancedEditBox<String> searchBox,
            Consumer<@Nullable TreeNode> onSelectionChanged,
            Runnable onAddSubdirectory,
            Runnable onRemoveDirectory
        ) {
            super(Minecraft.getInstance(), 0, 0, 0, ROW_H);

            this.contextMenu = ContextMenuContainer.builder(Minecraft.getInstance()).pageWidth(140)
                .entry()
                    .message(Component.translatable(LangKeys.GUI_MAIN_SCREEN_EXPLORER_CONTEXT_ADD_SUBDIR))
                    .predicate(() -> {
                        TreeEntry sel = getSelected();
                        return sel == null
                            || (sel.node.processor != null && sel.node.processor.canCreateSubDirectories());
                    })
                    .onPress(_ -> onAddSubdirectory.run())
                    .done()
                .entry()
                    .message(Component.translatable(LangKeys.GUI_MAIN_SCREEN_EXPLORER_CONTEXT_REMOVE_DIR))
                    .predicate(() -> {
                        TreeEntry sel = getSelected();
                        return sel != null && sel.node.modifiable;
                    })
                    .onPress(_ -> onRemoveDirectory.run())
                    .done()
                .build();

            this.font = font;
            this.roots = roots;
            this.searchBox = searchBox;
            this.onSelectionChanged = onSelectionChanged;

            // Anchor units unused — position is set manually in startEditing().
            nameEditor = AdvancedEditBox.stringBox(
                font,
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
                AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
                AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
                () -> "",
                _ -> {},
                Component.empty()
            );

            nameEditor.visible = false;

            rebuildEntries();
        }

        void focusByNode(TreeNode node) {
            int idx = 0;

            for (TreeList.TreeEntry e : children()) {
                if (node.equals(e.node)) {
                    setSelected(e);
                    setScrollAmount((double) idx * ROW_H);

                    return;
                }

                idx++;
            }
        }

        void startEditing(TreeEntry entry) {
            stopEditing();
            setFocused(entry);
            editingEntry = entry;

            int nameX = entry.getX() + 6 + entry.depth * INDENT + ARROW_W;

            nameEditor.setPosition(nameX, entry.getY());
            nameEditor.setValue(entry.node.name);
            scaleNameEditorToFit(entry);
            nameEditor.setCursorPosRightmost();
            nameEditor.visible = true;
            nameEditor.setFocused(true);
        }

        void scaleNameEditorToFit(TreeEntry entry) {
            nameEditor.setSize(font.width(ParseUtils.nvl(nameEditor.getValue(), "")) + ENTRY_PADDING * 2, entry.getHeight());
        }

        void stopEditing() {
            if (editingEntry == null) return;

            nameEditor.setFocused(false);
            nameEditor.visible = false;

            String newName = nameEditor.getText();
            TreeNode node = editingEntry.node;

            // For creating node
            if (
                node.directoryPath == null &&
                node.parentNode != null &&
                node.parentNode.directoryPath != null
            ) {
                boolean deleteCreated = newName.isBlank() || !Identifier.isValidPath(newName);

                if (!deleteCreated) {
                    Path newDir = node.parentNode.directoryPath.resolve(newName);
                    Path parentDir = node.parentNode.directoryPath;

                    try {
                        Files.createDirectories(newDir);
                        node.directoryPath = newDir;
                        node.name = newName;

                        AkytheraProject project = AkytheraProject.getCurrentProject();
                        HistoryManager.getInstance().pushHistory(new FileCreation(
                            () -> {
                                try { Files.createDirectories(newDir); }
                                catch (IOException ex) {
                                    Akythera.LOGGER.error("Failed to recreate {}", newDir, ex);
                                    return;
                                }
                                refreshTreeAndFocus(newDir);
                            },
                            () -> {
                                project.deletePathRecursive(newDir);
                                refreshTreeAndFocus(parentDir);
                            }
                        ));
                    } catch (IOException e) {
                        Akythera.LOGGER.error("Failed to create directory {}", newDir, e);
                        deleteCreated = true;
                    }
                }

                if (deleteCreated) {
                    removeEntry(editingEntry);
                    node.parentNode.children.remove(node);

                    if (editingEntry.node.parentNode != null) {
                        for (TreeList.TreeEntry entry : children()) {
                            if (Objects.equals(editingEntry.node.parentNode.directoryPath, entry.node.directoryPath)) {
                                setSelected(entry);
                                break;
                            }
                        }
                    }
                }
            } else if ( // For renaming node
                node.directoryPath != null &&
                !newName.isBlank() &&
                Identifier.isValidPath(newName) &&
                !node.directoryPath.getFileName().toString().equals(newName)
            ) {
                Path oldDir = node.directoryPath;
                Path newDir = oldDir.getParent().resolve(newName);

                if (AkytheraProject.getCurrentProject().renameDirectory(oldDir, newDir)) {
                    node.directoryPath = newDir;
                    remapChildPaths(node.children, oldDir, newDir);
                    node.name = newName;

                    HistoryManager.getInstance().pushHistory(new FileMove(
                        () -> {
                            AkytheraProject.getCurrentProject().renameDirectory(oldDir, newDir);
                            refreshTreeAndFocus(newDir);
                        },
                        () -> {
                            AkytheraProject.getCurrentProject().renameDirectory(newDir, oldDir);
                            refreshTreeAndFocus(oldDir);
                        }
                    ));
                }
            }

            editingEntry = null;
        }

        private static void refreshTreeAndFocus(Path path) {
            AkytheraEditorLayoutManager.getInstance().<MainEditorScreen>ifLastScreen(s -> {
                s.explorerPanel.rebuildTree();
                s.explorerPanel.focusNodeByPath(path);
            });
        }

        private void remapChildPaths(List<TreeNode> children, Path oldRoot, Path newRoot) {
            for (TreeNode child : children) {
                if (child.directoryPath != null && child.directoryPath.startsWith(oldRoot)) {
                    child.directoryPath = newRoot.resolve(oldRoot.relativize(child.directoryPath));
                }

                remapChildPaths(child.children, oldRoot, newRoot);
            }
        }

        /// Called by ExplorerPanel after rebuildEntries() to immediately begin editing a new node.
        void startEditingNode(TreeNode node) {
            for (TreeEntry entry : children()) {
                if (entry.node == node) {
                    setSelected(entry);
                    startEditing(entry);
                    return;
                }
            }
        }

        void rebuildEntries() {
            setSelected(null);
            stopEditing();
            clearEntries();

            String query = searchBox.getText().toLowerCase();

            if (!query.isEmpty()) {
                List<TreeNode> all = new ArrayList<>();

                for (TreeNode root : roots) collectAll(root, all);

                for (TreeNode node : all) {
                    if (node.name.toLowerCase().contains(query)) {
                        addEntry(new TreeEntry(node, 0));
                    }
                }
            } else {
                for (TreeNode root : roots) addVisibleEntries(root, 0);
            }

            setScrollAmount(scrollAmount());
        }

        @Override
        public void setSelected(@Nullable TreeEntry entry) {
            super.setSelected(entry);

            onSelectionChanged.accept(entry != null ? entry.node : null);
        }

        private void addVisibleEntries(TreeNode node, int depth) {
            addEntry(new TreeEntry(node, depth));

            if (node.expanded) {
                for (TreeNode child : node.children) addVisibleEntries(child, depth + 1);
            }
        }

        private void collectAll(TreeNode node, List<TreeNode> out) {
            out.add(node);
            for (TreeNode child : node.children) collectAll(child, out);
        }

        @Override
        public int getRowWidth() {
            return getWidth();
        }

        @Override
        public int scrollbarWidth() {
            return SCROLL_BAR_WIDTH;
        }

        @Override
        protected int scrollBarX() {
            return getRight() - scrollbarWidth();
        }

        @Override
        protected void extractListBackground(@NonNull GuiGraphicsExtractor graphics) {
            // Render nothing
        }

        @Override
        protected void extractListSeparators(@NonNull GuiGraphicsExtractor graphics) {
            // Render nothing
        }

        @Override
        protected boolean entriesCanBeSelected() {
            return false;
        }

        @Override
        protected void extractScrollbar(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            if (!scrollable()) return;

            int sbX = getRight() - scrollbarWidth();
            int scrollerH = scrollerHeight();
            int scrollerY = scrollBarY();
            boolean hovering = mouseX >= sbX && mouseX < sbX + scrollbarWidth() && mouseY >= scrollerY && mouseY < scrollerY + scrollerH;

            graphics.fill(sbX, getY(), sbX + scrollbarWidth(), getBottom(), 0xFF1A1A1A);
            graphics.fill(sbX + 1, scrollerY, sbX + scrollbarWidth() - 1, scrollerY + scrollerH, hovering ? 0xFF6A6A6A : 0xFF505050);
        }

        @Override
        protected void updateWidgetNarration(@NonNull NarrationElementOutput narration) {}

        final class TreeEntry extends AbstractSelectionList.Entry<@NonNull TreeEntry> {
            final TreeNode node;
            final int depth;

            TreeEntry(TreeNode node, int depth) {
                this.node = node;
                this.depth = depth;
            }

            @Override
            public void extractContent(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
                boolean selected  = TreeList.this.getSelected() == this;
                boolean isEditing = TreeList.this.editingEntry == this;

                if (selected || isEditing) {
                    graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xFF1E4D72);
                } else if (hovered) {
                    graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xFF262626);
                }

                if (node == TreeList.this.dragHighlightNode) {
                    graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xFF1A3D1A);
                }

                int textX = getX() + 6 + depth * INDENT;
                int textY = getY() + (getHeight() - 8) / 2;

                if (node.foldable()) {
                    String arrow = node.expanded ? "ᐁ" : "ᐅ";
                    graphics.text(font, arrow, textX, textY, node.modifiable ? 0xFFCCCCCC : 0xFFE8C97A, false);
                }

                if (isEditing) {
                    nameEditor.setY(getContentY() - 2);
                    nameEditor.extractRenderState(graphics, mouseX, mouseY, a);
                } else {
                    int nameW = Math.max(0, getX() + getWidth() - textX - ARROW_W - ENTRY_PADDING);

                    graphics.text(font, font.plainSubstrByWidth(node.name, nameW), textX + ARROW_W, textY, node.modifiable ? 0xFFCCCCCC : 0xFFE8C97A, false);
                }
            }

            @Override
            public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
                if (event.button() == 1 && isMouseOver(event.x(), event.y())) {
                    TreeList.this.setSelected(this);
                    AkytheraEditorLayoutManager.getInstance().openModalWidget(
                        TreeList.this.contextMenu,
                        (int) event.x(),
                        (int) event.y()
                    );
                    return true;
                }

                // Clicking this entry while another is being edited commits that edit first.
                if (TreeList.this.editingEntry != null) {
                    if (TreeList.this.editingEntry != this || !nameEditor.isMouseOver(event.x(), event.y())) {
                        TreeList.this.stopEditing();
                    }
                }

                // Forward clicks into the editor for cursor placement.
                if (TreeList.this.editingEntry == this) {
                    nameEditor.onClick(event, doubleClick);
                    return true;
                }

                TreeEntry oldSelected = TreeList.this.getSelected();
                TreeList.this.setSelected(this);

                if (doubleClick && node.modifiable) {
                    if (oldSelected == this) TreeList.this.startEditing(this);
                } else if (node.foldable() && searchBox.getText().isEmpty()) {
                    int textX = getX() + 6 + depth * INDENT;

                    // Checks arrow hit
                    if (textX < event.x() && event.x() <= textX + 8) {
                        node.expanded = !node.expanded;
                        TreeList.this.rebuildEntries();
                        TreeList.this.focusByNode(node);
                    }
                }

                return true;
            }

            @Override
            public boolean keyPressed(@NonNull KeyEvent event) {
                if (TreeList.this.editingEntry != this) return false;

                boolean handled = nameEditor.keyPressed(event);

                // Enter / Escape inside AdvancedEditBox calls setFocused(false),
                // which fires commitOrRevert(). Detect this and close the editor.
                if (!nameEditor.isFocused()) {
                    stopEditing();
                    TreeList.this.editingEntry = null;
                    nameEditor.visible = false;
                }

                return handled;
            }

            @Override
            public boolean charTyped(@NonNull CharacterEvent event) {
                if (TreeList.this.editingEntry != this) return false;

                boolean b = nameEditor.charTyped(event);
                scaleNameEditorToFit(this);

                return b;
            }
        }
    }
}