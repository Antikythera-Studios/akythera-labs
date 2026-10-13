package com.yesman.akytheralabs.gui.widget.main;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.client.api.gui.AkytheraButton;
import com.yesman.akythera.client.api.gui.BuiltInEditBoxTheme;
import com.yesman.akythera.client.api.gui.CheckBox;
import com.yesman.akythera.client.api.gui.CloseableWidget;
import com.yesman.akythera.client.api.gui.ContextMenuContainer;
import com.yesman.akythera.client.api.gui.TextArea;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.FileSystemHelper;
import com.yesman.akytheralabs.ProjectLocalization;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.*;
import com.yesman.akytheralabs.gui.widget.EditorTextArea;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.LanguageInfo;
import net.minecraft.client.resources.language.LanguageManager;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/// `Edit > Localization`: edits the project's lang entries ([ProjectLocalization]), one language at a
/// time.
///
/// The left side lists the keys as a tree split at their dots, each level sorted by name. A key with
/// no text in the selected language is greyed. The right side shows the focused key and edits its text.
/// Lang files dropped while the modal is up are imported into it.
public final class LocalizationManagerModal extends OverlayingWindowModal implements CloseableWidget {
    private static final int TITLE_H = 20;
    private static final int PAD = 8;
    private static final int GAP = 4;
    private static final int ROW_H = 16;
    private static final int LABEL_H = 12;
    private static final int COMBO_W = 200;
    private static final int BUTTON_W = 16;
    private static final int EXPORT_W = 60;
    private static final int CHECK_BOX_H = 12;
    private static final int TREE_TOP = TITLE_H + PAD + ROW_H + GAP + ROW_H + GAP;
    private static final int KEY_TOP = TITLE_H + PAD + LABEL_H;
    private static final int CONTEXT_TOP = KEY_TOP + ROW_H + PAD + LABEL_H;

    /// Right inset of the left side, and left inset of the right side.
    private static final Unit HALF_INSET = AnchoredWidget.fraction(51);

    /// Name of an added key's last segment, suffixed by a number while taken.
    private static final String NEW_ENTRY = "new_entry";

    private static final int CONTEXT_MENU_W = 120;

    /// The key tree's right-click menu.
    private static final List<UniversalMenu.Row<LocalizationContextOption>> CONTEXT_MENU_ROWS = List.of(
        new UniversalMenu.OptionRow<>(LocalizationContextOption.ADD_ENTRY),
        new UniversalMenu.OptionRow<>(LocalizationContextOption.REMOVE_ENTRY)
    );

    private final Font font;
    private final ProjectLocalization localization;

    private final AkytheraButton closeButton;
    private final EditorComboBox<String> languageCombo;
    private final AkytheraButton addButton;
    private final AkytheraButton removeButton;
    private final EditorEditBox<String> searchBox;
    private KeyTree keyTree;
    private final EditorEditBox<String> keyBox;
    private final TextArea contextArea;
    private final CheckBox exportAllCheckBox;
    private final AkytheraButton exportButton;

    public static void open(Font font) {
        ModalManager.getInstance().openModalWidgetAtCenter(new LocalizationManagerModal(font));
    }

    /// Imports `paths` into the open modal, see [#importLangFiles]. False when no modal is up.
    public static boolean onFilesDrop(List<Path> paths) {
        for (AbstractWidget modal : ModalManager.getInstance().getModals()) {
            if (modal instanceof LocalizationManagerModal localizationModal) {
                localizationModal.importLangFiles(paths);
                return true;
            }
        }

        return false;
    }

    private LocalizationManagerModal(Font font) {
        super(AnchoredWidget.fraction(80), AnchoredWidget.fraction(80), Component.translatable(LangKeys.GUI_LOCALIZATION_MODAL_TITLE));

        this.font = font;
        this.localization = AkytheraProject.getCurrentProject().getLocalization();

        this.closeButton = AkytheraButton.buttonBuilder(font, LocalizedMessage.handwritten("X"), () -> ModalManager.getInstance().closeModal())
            .xUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(TITLE_H))
            .yUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(TITLE_H))
            .horizontalAnchorType(HorizontalAnchorType.RIGHT_WIDTH)
            .verticalAnchorType(VerticalAnchorType.TOP_HEIGHT)
            .theme(AkytheraLabsButtonTheme.TOP_MENU_BUTTON)
            .build();

        LanguageManager languageManager = Minecraft.getInstance().getLanguageManager();

        this.languageCombo = new EditorComboBox<>(
            font,
            AnchoredWidget.pixel(PAD), AnchoredWidget.pixel(COMBO_W),
            AnchoredWidget.pixel(TITLE_H + PAD), AnchoredWidget.pixel(ROW_H),
            HorizontalAnchorType.LEFT_WIDTH,
            VerticalAnchorType.TOP_HEIGHT,
            localization::getLanguage,
            code -> {
                if (code == null) return;

                localization.setLanguage(code);
                refreshDetails();
            },
            12,
            Component.empty(),
            languageManager.getLanguages().keySet(),
            code -> {
                LanguageInfo info = languageManager.getLanguage(code);
                return info == null ? code : info.toComponent().getString();
            }
        );
        this.languageCombo.setClearable(false).setSearchable(true);
        this.languageCombo.setValueQuiet(localization.getLanguage());

        this.addButton = AkytheraButton.buttonBuilder(font, LocalizedMessage.handwritten("+"), () -> onAddPressed())
            .xUnits(AnchoredWidget.pixel(PAD + COMBO_W + GAP), AnchoredWidget.pixel(BUTTON_W))
            .yUnits(AnchoredWidget.pixel(TITLE_H + PAD), AnchoredWidget.pixel(ROW_H))
            .horizontalAnchorType(HorizontalAnchorType.LEFT_WIDTH)
            .verticalAnchorType(VerticalAnchorType.TOP_HEIGHT)
            .theme(AkytheraLabsButtonTheme.AKYTHERA_EXPLORER_BUTTON)
            .build();

        this.removeButton = AkytheraButton.buttonBuilder(font, LocalizedMessage.handwritten("-"), () -> onRemovePressed())
            .xUnits(AnchoredWidget.pixel(PAD + COMBO_W + GAP + BUTTON_W + GAP), AnchoredWidget.pixel(BUTTON_W))
            .yUnits(AnchoredWidget.pixel(TITLE_H + PAD), AnchoredWidget.pixel(ROW_H))
            .horizontalAnchorType(HorizontalAnchorType.LEFT_WIDTH)
            .verticalAnchorType(VerticalAnchorType.TOP_HEIGHT)
            .theme(AkytheraLabsButtonTheme.AKYTHERA_EXPLORER_BUTTON)
            .build();

        this.searchBox = EditorEditBox.stringBox(
            font,
            AnchoredWidget.pixel(PAD), HALF_INSET,
            AnchoredWidget.pixel(TITLE_H + PAD + ROW_H + GAP), AnchoredWidget.pixel(ROW_H),
            HorizontalAnchorType.LEFT_RIGHT,
            VerticalAnchorType.TOP_HEIGHT,
            () -> "",
            _ -> {
                keyTree.deselectAll();
                keyTree.rebuild();
                refreshDetails();
            },
            Component.translatable(LangKeys.GUI_MESSAGE_EMPTY_SEARCHBAR)
        );

        this.keyTree = new KeyTree();

        this.keyBox = EditorEditBox.stringBox(
            font,
            HALF_INSET, AnchoredWidget.pixel(PAD),
            AnchoredWidget.pixel(KEY_TOP), AnchoredWidget.pixel(ROW_H),
            HorizontalAnchorType.LEFT_RIGHT,
            VerticalAnchorType.TOP_HEIGHT,
            () -> "",
            _ -> {},
            Component.empty()
        );
        this.keyBox.setTheme(BuiltInEditBoxTheme.DEFAULT_EDIT_BOX);
        this.keyBox.setEditable(false);

        this.contextArea = new EditorTextArea(
            font,
            HALF_INSET, AnchoredWidget.pixel(PAD),
            AnchoredWidget.pixel(CONTEXT_TOP), AnchoredWidget.pixel(PAD + ROW_H + GAP),
            HorizontalAnchorType.LEFT_RIGHT,
            VerticalAnchorType.TOP_BOTTOM,
            () -> "",
            text -> {
                if (keyTree.focusedKey != null) localization.setText(keyTree.focusedKey, localization.getLanguage(), text);
            },
            Component.translatable(LangKeys.GUI_LOCALIZATION_MODAL_NO_VALUE)
        );

        this.exportButton = AkytheraButton.buttonBuilder(font, LocalizedMessage.translatable(LangKeys.GUI_LOCALIZATION_MODAL_EXPORT), () -> onExportPressed())
            .xUnits(AnchoredWidget.pixel(PAD), AnchoredWidget.pixel(EXPORT_W))
            .yUnits(AnchoredWidget.pixel(PAD), AnchoredWidget.pixel(ROW_H))
            .horizontalAnchorType(HorizontalAnchorType.RIGHT_WIDTH)
            .verticalAnchorType(VerticalAnchorType.BOTTOM_HEIGHT)
            .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
            .build();

        Component exportAllMessage = Component.translatable(LangKeys.GUI_LOCALIZATION_MODAL_EXPORT_ALL_LANGUAGES);

        this.exportAllCheckBox = new CheckBox(
            font,
            AnchoredWidget.pixel(PAD + EXPORT_W + PAD), AnchoredWidget.pixel(CHECK_BOX_H + GAP + font.width(exportAllMessage)),
            AnchoredWidget.pixel(PAD + (ROW_H - CHECK_BOX_H) / 2), AnchoredWidget.pixel(CHECK_BOX_H),
            HorizontalAnchorType.RIGHT_WIDTH,
            VerticalAnchorType.BOTTOM_HEIGHT,
            localization::isExportAllLanguages,
            localization::setExportAllLanguages,
            exportAllMessage,
            true
        );
        this.exportAllCheckBox.valueSetter().accept(localization.isExportAllLanguages());

        refreshDetails();
    }

    /// Shows the focused key and its text on the right side, or empties it while no key is focused.
    private void refreshDetails() {
        String key = keyTree.focusedKey;

        keyBox.setValue(Objects.requireNonNullElse(key, ""));
        contextArea.active = key != null;
        contextArea.setValue(key == null ? "" : localization.text(key, localization.getLanguage()));
        removeButton.active = !keyTree.selectedKeys.isEmpty();
    }

    /// Adds a key under the focused one, or at the top level while none is focused, and opens its
    /// name editor.
    private void onAddPressed() {
        String parent = keyTree.focusedKey;
        String base = parent == null ? NEW_ENTRY : parent + "." + NEW_ENTRY;
        String key = base;

        for (int i = 1; keyTree.nodes.containsKey(key); i++) {
            key = base + "_" + i;
        }

        localization.add(key);
        keyTree.deselectAll();
        keyTree.selectedKeys.add(key);
        keyTree.focusedKey = key;
        keyTree.reveal(key);
        refreshDetails();

        setFocused(keyTree);
        keyTree.startEditing(key);
    }

    /// Removes the selected keys and every key under them, asking first when that is more than one row.
    private void onRemovePressed() {
        Set<String> removedRows = new HashSet<>();

        for (String key : keyTree.selectedKeys) {
            KeyNode node = keyTree.nodes.get(key);
            if (node != null) node.collectKeys(removedRows);
        }

        if (removedRows.isEmpty()) return;

        Runnable remove = () -> {
            keyTree.selectedKeys.forEach(localization::removeTree);
            keyTree.deselectAll();
            keyTree.rebuild();
            refreshDetails();
        };

        if (removedRows.size() == 1) {
            remove.run();
            return;
        }

        int popupW = 280;
        int buttonW = WidgetContainerModal.evenButtonWidth(popupW, 2);

        WidgetContainerModal popup = WidgetContainerModal.builder(font, Component.translatable(LangKeys.GUI_LOCALIZATION_MODAL_POPUP_REMOVE_TITLE), popupW)
            .textLines(Component.translatable(LangKeys.GUI_LOCALIZATION_MODAL_POPUP_REMOVE_BODY, removedRows.size()))
            .footerButton(
                AkytheraButton.buttonBuilder(font, LocalizedMessage.translatable(LangKeys.GUI_CANCEL), () -> ModalManager.getInstance().closeModal())
                    .theme(AkytheraLabsButtonTheme.AKYTHERA_EXPLORER_BUTTON)
                    .build(),
                buttonW
            )
            .footerButton(
                AkytheraButton.buttonBuilder(font, LocalizedMessage.translatable(LangKeys.GUI_MESSAGE_BUTTON_REMOVE), () -> {
                        ModalManager.getInstance().closeModal();
                        remove.run();
                    })
                    .theme(AkytheraLabsButtonTheme.AKYTHERA_EXPLORER_BUTTON)
                    .build(),
                buttonW
            )
            .build();

        ModalManager.getInstance().openModalWidgetAtCenter(popup);
    }

    /// Writes the lang files into a picked resource root, see [AkytheraProject#exportLangFiles]. The
    /// folder is remembered as the `Export as Source Code` resource path.
    private void onExportPressed() {
        AkytheraProject project = AkytheraProject.getCurrentProject();
        String current = project.getLastSourceResourcePath().isBlank()
            ? project.getProjectRoot().toString()
            : FileSystemHelper.toOsString(project.getLastSourceResourcePath());

        CompletableFuture
            .supplyAsync(() -> TinyFileDialogs.tinyfd_selectFolderDialog("Choose Export Folder", current))
            .thenAcceptAsync(result -> {
                if (result == null) return;

                Path resourceRoot = Path.of(result);

                project.saveSourceExportPaths(project.getLastSourceDataPath(), result);
                project.exportLangFiles(resourceRoot);
                Util.getPlatform().openFile(resourceRoot.toFile());
            }, Minecraft.getInstance());
    }

    /// Fills the project's keys from lang files. A file named after a language (`ko_kr.json`) fills
    /// that language and selects it; any other fills the selected language.
    private void importLangFiles(List<Path> paths) {
        Map<String, LanguageInfo> languages = Minecraft.getInstance().getLanguageManager().getLanguages();

        for (Path path : paths) {
            String fileName = path.getFileName().toString();
            if (!fileName.endsWith(".json")) continue;

            JsonObject langFile = FileSystemHelper.readJsonObject(path);
            if (langFile == null) continue;

            String code = FileSystemHelper.stem(fileName).toLowerCase(Locale.ROOT);
            if (languages.containsKey(code)) localization.setLanguage(code);

            for (Map.Entry<String, JsonElement> entry : langFile.entrySet()) {
                if (ProjectLocalization.isValidKey(entry.getKey()) && entry.getValue().isJsonPrimitive()) {
                    localization.setText(entry.getKey(), localization.getLanguage(), entry.getValue().getAsString());
                }
            }
        }

        languageCombo.setValueQuiet(localization.getLanguage());
        keyTree.rebuild();
        refreshDetails();
    }

    @Override
    public void destroy() {
        AkytheraProject.getCurrentProject().saveLocalization();
    }

    @Override
    public @NonNull List<? extends GuiEventListener> children() {
        return List.of(closeButton, languageCombo, addButton, removeButton, searchBox, keyTree, keyBox, contextArea, exportAllCheckBox, exportButton);
    }

    @Override
    protected int contentHeight() {
        return getHeight();
    }

    @Override
    public void setFocused(final @Nullable GuiEventListener focused) {
        // Re-focusing the already-focused child clears the focus in vanilla's implementation.
        if (getFocused() == focused) {
            return;
        }

        super.setFocused(focused);
    }

    @Override
    public boolean isMouseOverHeader(double mouseX, double mouseY) {
        return mouseX >= getX() && mouseY >= getY() && mouseX < closeButton.getX() && mouseY < getY() + TITLE_H;
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        // Route to children, then consume to prevent click-through to the screen below
        super.mouseClicked(event, doubleClick);

        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return getChildAt(mouseX, mouseY).filter(child -> child.mouseScrolled(mouseX, mouseY, scrollX, scrollY)).isPresent();
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        GuiEventListener focused = getFocused();
        if (focused != null && focused.keyPressed(event)) return true;

        if (event.isEscape()) {
            ModalManager.getInstance().closeModal();
            return true;
        }

        return false;
    }

    @Override
    public boolean charTyped(@NonNull CharacterEvent event) {
        GuiEventListener focused = getFocused();
        return focused != null && focused.charTyped(event);
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractWidgetRenderState(graphics, mouseX, mouseY, a);

        graphics.fill(getX(), getY(), getRight(), getBottom(), 0xFF1E1E1E);
        graphics.outline(getX(), getY(), getWidth(), getHeight(), 0xFF3A3A3A);
        graphics.fill(getX(), getY(), getRight(), getY() + TITLE_H, 0xFF2A2A2A);
        graphics.text(font, getMessage(), getX() + PAD, getY() + (TITLE_H - font.lineHeight) / 2, 0xFFCCCCCC, false);

        graphics.text(font, Component.translatable(LangKeys.GUI_LOCALIZATION_MODAL_KEY), keyBox.getX(), keyBox.getY() - LABEL_H, 0xFFAAAAAA, false);
        graphics.text(font, Component.translatable(LangKeys.GUI_LOCALIZATION_MODAL_CONTEXT), contextArea.getX(), contextArea.getY() - LABEL_H, 0xFFAAAAAA, false);
        graphics.outline(keyTree.getX() - 1, keyTree.getY() - 1, keyTree.getWidth() + 2, keyTree.getHeight() + 2, 0xFF555555);

        for (GuiEventListener child : children()) {
            ((AbstractWidget) child).extractRenderState(graphics, mouseX, mouseY, a);
        }
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput n) {
    }

    /// One node of the key tree: a key, or a prefix the keys under it share. Named after its last
    /// segment.
    private record KeyNode(String key, String name, SortedMap<String, KeyNode> children) {
        KeyNode(String key, String name) {
            this(key, name, new TreeMap<>());
        }

        /// Whether this key or one under it contains `query`.
        boolean matches(String query) {
            return key.toLowerCase(Locale.ROOT).contains(query) || children.values().stream().anyMatch(child -> child.matches(query));
        }

        /// Adds this key and every key under it to `out`.
        void collectKeys(Set<String> out) {
            out.add(key);
            children.values().forEach(child -> child.collectKeys(out));
        }
    }

    private final class KeyTree extends AbstractSelectionList<KeyTree.@NonNull Row> implements AnchoredWidget {
        private static final int SCROLLBAR_W = 5;
        private static final int INDENT = 12;
        private static final int ARROW_W = 10;
        private static final int TEXT_LEFT = 4;

        private static final int FOCUSED_COLOR = 0xFF1E4D72;
        private static final int SELECTED_COLOR = 0xFF15293A;
        private static final int HOVERED_COLOR = 0xFF262626;
        private static final int FILLED_COLOR = 0xFFCCCCCC;
        private static final int EMPTY_COLOR = 0xFF6F6F6F;

        /// Every node of the last [#rebuild] by key, the folded ones included.
        private final Map<String, KeyNode> nodes = new HashMap<>();

        /// Keys whose children are folded away.
        private final Set<String> collapsedKeys = new HashSet<>();

        private final Set<String> selectedKeys = new LinkedHashSet<>();

        /// The last selected key, which the right side edits. Null while none is.
        private @Nullable String focusedKey;

        /// The name editor of [#editingRow], placed over its name.
        private final EditorEditBox<String> nameEditor;

        private @Nullable Row editingRow;

        private final ContextMenuContainer contextMenu;

        KeyTree() {
            super(Minecraft.getInstance(), 0, 0, 0, ROW_H);

            this.contextMenu = UniversalMenu.buildPage(
                CONTEXT_MENU_ROWS,
                UniversalMenu.<LocalizationContextOption>actions()
                    .on(LocalizationContextOption.ADD_ENTRY, LocalizationManagerModal.this::onAddPressed)
                    .on(LocalizationContextOption.REMOVE_ENTRY, () -> !selectedKeys.isEmpty(), LocalizationManagerModal.this::onRemovePressed),
                CONTEXT_MENU_W
            );

            this.nameEditor = EditorEditBox.filteredStringBox(
                font,
                s -> s.indexOf('.') < 0 && s.chars().noneMatch(Character::isWhitespace),
                Component.empty()
            );
            this.nameEditor.setTheme(BuiltInEditBoxTheme.DEFAULT_EDIT_BOX);
            this.nameEditor.visible = false;

            rebuild();
        }

        /// Lists the project's keys again, keeping only the ones matching [#searchBox] while it holds text.
        void rebuild() {
            stopEditing();
            clearEntries();
            nodes.clear();

            KeyNode root = new KeyNode("", "");

            for (String key : localization.keys()) {
                KeyNode parent = root;
                int from = 0;

                while (true) {
                    int dot = key.indexOf('.', from);
                    String prefix = dot < 0 ? key : key.substring(0, dot);
                    String name = prefix.substring(from);
                    KeyNode node = parent.children().computeIfAbsent(name, _ -> new KeyNode(prefix, name));

                    nodes.putIfAbsent(prefix, node);
                    parent = node;

                    if (dot < 0) break;

                    from = dot + 1;
                }
            }

            String query = searchBox.getText().toLowerCase(Locale.ROOT);

            for (KeyNode node : root.children().values()) {
                addRows(node, 0, query);
            }

            setScrollAmount(scrollAmount());
        }

        private void addRows(KeyNode node, int depth, String query) {
            if (!query.isEmpty() && !node.matches(query)) return;

            addEntry(new Row(node, depth));

            if (query.isEmpty() && collapsedKeys.contains(node.key())) return;

            for (KeyNode child : node.children().values()) {
                addRows(child, depth + 1, query);
            }
        }

        void deselectAll() {
            selectedKeys.clear();
            focusedKey = null;
        }

        private static void remapKeys(Set<String> keys, String from, String to) {
            List<String> moved = keys.stream().filter(key -> ProjectLocalization.isUnder(key, from)).toList();

            moved.forEach(keys::remove);
            moved.forEach(key -> keys.add(to + key.substring(from.length())));
        }

        /// Unfolds the parents of `key`, lists the keys again and scrolls its row into view.
        void reveal(String key) {
            for (int dot = key.indexOf('.'); dot >= 0; dot = key.indexOf('.', dot + 1)) {
                collapsedKeys.remove(key.substring(0, dot));
            }

            rebuild();

            Row row = rowOf(key);
            if (row == null) return;

            int index = children().indexOf(row);
            int rowTop = getRowTop(index);
            int rowBottom = getRowBottom(index);

            if (rowTop < getY()) {
                setScrollAmount(scrollAmount() - (getY() - rowTop));
            } else if (rowBottom > getBottom()) {
                setScrollAmount(scrollAmount() + (rowBottom - getBottom()));
            }
        }

        private @Nullable Row rowOf(String key) {
            for (Row row : children()) {
                if (row.node.key().equals(key)) return row;
            }

            return null;
        }

        void startEditing(String key) {
            Row row = rowOf(key);
            if (row == null) return;

            stopEditing();
            editingRow = row;
            setFocused(row);

            int nameX = row.getX() + TEXT_LEFT + row.depth * INDENT + ARROW_W - 4;

            nameEditor.setPosition(nameX, row.getY());
            nameEditor.setSize(Math.max(0, row.getX() + row.getWidth() - nameX), row.getHeight());
            nameEditor.setValue(row.node.name());
            nameEditor.selectAll();
            nameEditor.visible = true;
            nameEditor.setFocused(true);
        }

        /// Closes the name editor, renaming its key's last segment to what was typed.
        void stopEditing() {
            if (editingRow == null) return;

            KeyNode node = editingRow.node;
            String name = nameEditor.getText();

            editingRow = null;
            nameEditor.visible = false;
            nameEditor.setFocused(false);

            if (name.isEmpty() || name.equals(node.name())) return;

            String oldKey = node.key();
            int dot = oldKey.lastIndexOf('.');
            String newKey = dot < 0 ? name : oldKey.substring(0, dot + 1) + name;

            if (!localization.moveTree(oldKey, newKey)) {
                RefusalPopup.open(
                    Component.translatable(LangKeys.GUI_LOCALIZATION_MODAL_POPUP_KEY_TAKEN_TITLE),
                    Component.translatable(LangKeys.GUI_LOCALIZATION_MODAL_POPUP_KEY_TAKEN_BODY, newKey)
                );
                return;
            }

            remapKeys(selectedKeys, oldKey, newKey);
            remapKeys(collapsedKeys, oldKey, newKey);

            if (focusedKey != null && ProjectLocalization.isUnder(focusedKey, oldKey)) {
                focusedKey = newKey + focusedKey.substring(oldKey.length());
            }

            reveal(newKey);
            refreshDetails();
        }

        @Override
        public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
            if (super.mouseClicked(event, doubleClick)) return true;
            if (!isMouseOver(event.x(), event.y())) return false;

            // A press under the last row
            stopEditing();
            deselectAll();
            refreshDetails();

            if (event.button() == 1) openContextMenu(event);

            return true;
        }

        @Override
        public boolean keyPressed(@NonNull KeyEvent event) {
            if (super.keyPressed(event)) return true;
            if (editingRow != null) return false;

            if (event.key() == GLFW.GLFW_KEY_DELETE) {
                onRemovePressed();
                return true;
            }

            if (event.key() == GLFW.GLFW_KEY_F2 && focusedKey != null) {
                startEditing(focusedKey);
                return true;
            }

            return false;
        }

        private void openContextMenu(MouseButtonEvent event) {
            ModalManager.getInstance().openModalWidget(contextMenu, (int) event.x(), (int) event.y());
        }

        @Override
        public int getRowWidth() {
            return getWidth() - SCROLLBAR_W;
        }

        @Override
        public int getRowLeft() {
            return getX();
        }

        @Override
        public int scrollbarWidth() {
            return SCROLLBAR_W;
        }

        @Override
        protected int scrollerHeight() {
            return Mth.clamp((int) ((float) (height * height) / (float) contentHeight()), 8, height - 8);
        }

        @Override
        protected int scrollBarX() {
            return getRight() - scrollbarWidth();
        }

        @Override
        protected void extractListBackground(@NonNull GuiGraphicsExtractor graphics) {
            graphics.fill(getX(), getY(), getRight(), getBottom(), 0xFF141414);
        }

        @Override
        protected void extractListSeparators(@NonNull GuiGraphicsExtractor graphics) {
        }

        @Override
        protected boolean entriesCanBeSelected() {
            return false;
        }

        @Override
        protected void extractScrollbar(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            if (!scrollable()) return;

            int sbX = scrollBarX();
            int scrollerH = scrollerHeight();
            int scrollerY = scrollBarY();
            boolean hovered = mouseX >= sbX && mouseX < sbX + SCROLLBAR_W && mouseY >= scrollerY && mouseY < scrollerY + scrollerH;

            graphics.fill(sbX, getY(), sbX + SCROLLBAR_W, getBottom(), 0xFF1A1A1A);
            graphics.fill(sbX + 1, scrollerY, sbX + SCROLLBAR_W - 1, scrollerY + scrollerH, hovered ? 0xFF6A6A6A : 0xFF505050);
        }

        @Override
        protected void updateWidgetNarration(@NonNull NarrationElementOutput n) {
        }

        /// One node of the tree: its fold arrow, then its name, greyed while the selected language
        /// leaves its key empty.
        final class Row extends AbstractSelectionList.Entry<@NonNull Row> {
            private final KeyNode node;
            private final int depth;

            Row(KeyNode node, int depth) {
                this.node = node;
                this.depth = depth;
            }

            private int arrowX() {
                return getX() + TEXT_LEFT + depth * INDENT;
            }

            @Override
            public void extractContent(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float a) {
                int background = node.key().equals(focusedKey)
                    ? FOCUSED_COLOR
                    : selectedKeys.contains(node.key())
                        ? SELECTED_COLOR
                        : hovered ? HOVERED_COLOR : 0;

                if (background != 0) g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), background);

                int textY = getY() + (getHeight() - 8) / 2;

                if (!node.children().isEmpty()) {
                    boolean folded = searchBox.getText().isEmpty() && collapsedKeys.contains(node.key());
                    g.text(font, folded ? "ᐅ" : "ᐁ", arrowX(), textY, FILLED_COLOR, false);
                }

                if (editingRow == this) {
                    nameEditor.setY(getY());
                    nameEditor.extractRenderState(g, mouseX, mouseY, a);
                    return;
                }

                int nameX = arrowX() + ARROW_W;
                int color = localization.text(node.key(), localization.getLanguage()).isEmpty() ? EMPTY_COLOR : FILLED_COLOR;

                g.text(font, font.plainSubstrByWidth(node.name(), Math.max(0, getX() + getWidth() - nameX)), nameX, textY, color, false);
            }

            @Override
            public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
                if (editingRow == this && nameEditor.isMouseOver(event.x(), event.y())) {
                    nameEditor.onClick(event, doubleClick);
                    return true;
                }

                // A press elsewhere only closes the name editor.
                if (editingRow != null) {
                    stopEditing();
                    return true;
                }

                String key = node.key();

                // A right press on a row outside the selection selects that row alone.
                if (event.button() == 1) {
                    if (!selectedKeys.contains(key)) {
                        selectedKeys.clear();
                        selectedKeys.add(key);
                        focusedKey = key;
                        refreshDetails();
                    }

                    openContextMenu(event);
                    return true;
                }

                if (event.button() != 0) return false;

                if (!node.children().isEmpty() && searchBox.getText().isEmpty() && event.x() >= arrowX() && event.x() < arrowX() + ARROW_W) {
                    if (!collapsedKeys.remove(key)) collapsedKeys.add(key);

                    rebuild();
                    return true;
                }

                boolean wasFocused = key.equals(focusedKey);

                if (event.hasShiftDown() && focusedKey != null && rowOf(focusedKey) != null) {
                    int from = children().indexOf(rowOf(focusedKey));
                    int to = children().indexOf(this);

                    selectedKeys.clear();

                    for (int i = Math.min(from, to); i <= Math.max(from, to); i++) {
                        selectedKeys.add(children().get(i).node.key());
                    }

                    focusedKey = key;
                } else if (event.hasControlDownWithQuirk()) {
                    if (selectedKeys.remove(key)) {
                        if (wasFocused) focusedKey = null;
                    } else {
                        selectedKeys.add(key);
                        focusedKey = key;
                    }
                } else {
                    selectedKeys.clear();
                    selectedKeys.add(key);
                    focusedKey = key;
                }

                refreshDetails();

                if (doubleClick && wasFocused) startEditing(key);

                return true;
            }

            @Override
            public boolean keyPressed(@NonNull KeyEvent event) {
                if (editingRow != this) return false;

                boolean handled = nameEditor.keyPressed(event);

                // Enter and Escape unfocus the editor.
                if (!nameEditor.isFocused()) stopEditing();

                return handled;
            }

            @Override
            public boolean charTyped(@NonNull CharacterEvent event) {
                return editingRow == this && nameEditor.charTyped(event);
            }
        }

        // ***************************************************************
        // [AnchoredWidget] implementations
        // ***************************************************************
        private Unit x1 = AnchoredWidget.pixel(PAD);
        private Unit x2 = HALF_INSET;
        private Unit y1 = AnchoredWidget.pixel(TREE_TOP);
        private Unit y2 = AnchoredWidget.pixel(PAD);
        private HorizontalAnchorType horizontalAnchorType = HorizontalAnchorType.LEFT_RIGHT;
        private VerticalAnchorType verticalAnchorType = VerticalAnchorType.TOP_BOTTOM;

        @Override public Unit getX1() { return x1; }
        @Override public Unit getX2() { return x2; }
        @Override public Unit getY1() { return y1; }
        @Override public Unit getY2() { return y2; }
        @Override public void setX1(Unit unit) { x1 = unit; }
        @Override public void setX2(Unit unit) { x2 = unit; }
        @Override public void setY1(Unit unit) { y1 = unit; }
        @Override public void setY2(Unit unit) { y2 = unit; }
        @Override public HorizontalAnchorType getHorizontalAnchorType() { return horizontalAnchorType; }
        @Override public VerticalAnchorType getVerticalAnchorType() { return verticalAnchorType; }
        @Override public void setHorizontalAnchorType(HorizontalAnchorType horizontalAnchorType) { this.horizontalAnchorType = horizontalAnchorType; }
        @Override public void setVerticalAnchorType(VerticalAnchorType verticalAnchorType) { this.verticalAnchorType = verticalAnchorType; }
    }
}
