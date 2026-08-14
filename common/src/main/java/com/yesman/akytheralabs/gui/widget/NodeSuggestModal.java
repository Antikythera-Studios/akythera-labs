package com.yesman.akytheralabs.gui.widget;

import com.yesman.akythera.client.gui.widget.CloseableWidget;
import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/// Search-and-pick modal over an arbitrary list of suggestions. Opens at the cursor
/// (no dimmer, no centering — like [com.yesman.akytheralabs.gui.widget.ContextMenuContainer])
/// and confirms on a single-click of a row. Generic over the picked value so it is
/// reusable for any kind of suggestion, not just script nodes.
///
/// `onCancel` (when supplied) fires if the modal closes without a pick — via Escape,
/// an outside click, or the editor tearing the modal layer down — letting callers
/// undo anything staged while the modal was open.
public final class NodeSuggestModal<T> extends AbstractContainerWidget implements Modal, CloseableWidget, AnchoredWidget {
    private static final int TITLE_H = 18;
    private static final int SEARCH_H = 16;
    private static final int ROW_H = 14;
    private static final int SCROLLBAR_W = 5;
    private static final int MODAL_W = 220;
    private static final int MODAL_H = 240;

    private static final int ARROW_W = 10;
    private static final int INDENT = 12;

    /// The screen-Y a modal opened at press-point `pressY` should use so it does not clip off
    /// the bottom: opens downward when it fits below the press, flips above the press when it
    /// does not but fits there, and otherwise clamps to keep as much on-screen as possible.
    public static int openYFor(int pressY, int screenHeight) {
        if (pressY + MODAL_H <= screenHeight) return pressY;

        int above = pressY - MODAL_H;
        if (above >= 0) return above;

        return Math.max(0, screenHeight - MODAL_H);
    }

    /// A pickable entry: a display `label` and the `value` handed to `onConfirm`.
    ///
    /// `category` is the `/`-separated folder path the entry is filed under in the tree
    /// (e.g. `"Math/Vector"`); a null/blank category files the entry at the root level.
    public record Suggestion<T>(Component label, T value, @Nullable String category) {
        public Suggestion(Component label, T value) {
            this(label, value, null);
        }
    }

    private final Font font;
    private final Consumer<T> onConfirm;
    @Nullable
    private final Runnable onCancel;
    private final AdvancedEditBox<String> searchBox;
    private final EntryList list;

    private boolean confirmed;

    public NodeSuggestModal(
        Font font,
        Component title,
        List<Suggestion<T>> suggestions,
        Consumer<T> onConfirm
    ) {
        this(font, title, suggestions, onConfirm, null);
    }

    public NodeSuggestModal(
        Font font,
        Component title,
        List<Suggestion<T>> suggestions,
        Consumer<T> onConfirm,
        @Nullable Runnable onCancel
    ) {
        super(0, 0, MODAL_W, MODAL_H, title, AbstractScrollArea.defaultSettings(0));

        this.font = font;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
        this.list = new EntryList(suggestions);
        this.list.rebuild("");

        this.searchBox = AdvancedEditBox.stringBox(
            font,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(TITLE_H), AnchoredWidget.pixel(SEARCH_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            () -> "",
            list::rebuild,
            Component.translatable(LangKeys.GUI_MESSAGE_EMPTY_SEARCHBAR)
        );
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        // Cover the screen so we always intercept clicks (close-on-outside).
        return isActive();
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        boolean overPage = event.x() >= getX() && event.x() < getRight() && event.y() >= getY() && event.y() < getBottom();

        if (!overPage) {
            AkytheraEditorLayoutManager.getInstance().closeModal();
            return true;
        }

        super.mouseClicked(event, doubleClick);

        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return list.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (event.isEscape()) {
            AkytheraEditorLayoutManager.getInstance().closeModal();
            return true;
        }

        GuiEventListener focused = getFocused();

        return focused != null && focused.keyPressed(event);
    }

    @Override
    public boolean charTyped(@NonNull CharacterEvent event) {
        GuiEventListener focused = getFocused();

        return focused != null && focused.charTyped(event);
    }

    @Override
    public @NonNull List<? extends GuiEventListener> children() {
        return List.of(searchBox, list);
    }

    @Override
    protected int contentHeight() {
        return getHeight();
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        // Page background + 1-posX border (no dimmer)
        graphics.fill(getX(), getY(), getRight(), getBottom(), 0xFF1E1E1E);
        graphics.fill(getX(), getY(), getRight(), getY() + 1, 0xFF3A3A3A);
        graphics.fill(getX(), getBottom() - 1, getRight(), getBottom(), 0xFF3A3A3A);
        graphics.fill(getX(), getY(), getX() + 1, getBottom(), 0xFF3A3A3A);
        graphics.fill(getRight() - 1, getY(), getRight(), getBottom(), 0xFF3A3A3A);

        // Title bar
        graphics.fill(getX(), getY(), getRight(), getY() + TITLE_H, 0xFF2A2A2A);
        graphics.text(
            font,
            getMessage(),
            getX() + 6,
            getY() + (TITLE_H - font.lineHeight) / 2,
            0xFFCCCCCC,
            false
        );

        searchBox.extractRenderState(graphics, mouseX, mouseY, a);
        list.extractRenderState(graphics, mouseX, mouseY, a);
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput n) {
    }

    /// Fired by the layout manager when the modal is popped. When no row was picked,
    /// notify `onCancel` so the caller can roll back staged state (e.g. a pending wire).
    @Override
    public void destroy() {
        if (!confirmed && onCancel != null) {
            onCancel.run();
        }
    }

    /// A folder in the suggestion tree: its display `name`, its full `/`-separated `path`
    /// (the expansion key), child folders (insertion order; sorted at render), and the leaf
    /// suggestions filed directly under it.
    private final class CategoryNode {
        private final String name;
        private final String path;
        private final Map<String, CategoryNode> children = new LinkedHashMap<>();
        private final List<Suggestion<T>> leaves = new ArrayList<>();

        CategoryNode(String name, String path) {
            this.name = name;
            this.path = path;
        }

        CategoryNode child(String segment, String fullPath) {
            return children.computeIfAbsent(segment, _ -> new CategoryNode(segment, fullPath));
        }
    }

    private final class EntryList extends AbstractSelectionList<EntryList.@NonNull Entry> {
        private final List<Suggestion<T>> suggestions;

        /// Full paths of the folders currently expanded. A search overrides this (everything
        /// is shown expanded while a query is active).
        private final Set<String> expanded = new HashSet<>();

        private String query = "";
        private boolean searching;

        EntryList(List<Suggestion<T>> suggestions) {
            super(Minecraft.getInstance(), MODAL_W - 2, MODAL_H - TITLE_H - SEARCH_H - 1, 0, ROW_H);
            this.suggestions = suggestions;
        }

        /// Rebuilds for a new search query: the scroll resets to the top since the visible
        /// set changes wholesale.
        void rebuild(String query) {
            this.query = query;
            populate();
            setScrollAmount(0.0D);
            updateSizeAndPosition(getWidth(), getHeight(), getX(), getY());
        }

        /// Rebuilds in place after a fold toggle, preserving the scroll position so the
        /// clicked folder stays put.
        private void refresh() {
            double scroll = scrollAmount();
            populate();
            updateSizeAndPosition(getWidth(), getHeight(), getX(), getY());
            setScrollAmount(scroll);
        }

        private void toggle(String path) {
            if (!expanded.remove(path)) expanded.add(path);
            refresh();
        }

        /// Files the (query-filtered) suggestions into a category tree and flattens it into
        /// rows honouring the fold state — folders first then leaves at each level, both
        /// sorted case-insensitively.
        private void populate() {
            clearEntries();

            String lower = query.toLowerCase();
            searching = !lower.isEmpty();

            CategoryNode root = new CategoryNode("", "");
            for (Suggestion<T> suggestion : suggestions) {
                if (searching && !suggestion.label().getString().toLowerCase().contains(lower)) continue;

                CategoryNode target = root;
                String category = suggestion.category();
                if (category != null && !category.isBlank()) {
                    StringBuilder path = new StringBuilder();
                    for (String raw : category.split("/")) {
                        String segment = raw.trim();
                        if (segment.isEmpty()) continue;
                        if (path.length() > 0) path.append('/');
                        path.append(segment);
                        target = target.child(segment, path.toString());
                    }
                }
                target.leaves.add(suggestion);
            }

            addNodeEntries(root, 0);
        }

        private void addNodeEntries(CategoryNode node, int depth) {
            node.children.values().stream()
                .sorted(Comparator.comparing(c -> c.name, String.CASE_INSENSITIVE_ORDER))
                .forEach(child -> {
                    addEntry(new Entry(child, depth));
                    if (searching || expanded.contains(child.path)) addNodeEntries(child, depth + 1);
                });

            node.leaves.stream()
                .sorted(Comparator.comparing(s -> s.label().getString(), String.CASE_INSENSITIVE_ORDER))
                .forEach(s -> addEntry(new Entry(s, depth)));
        }

        @Override
        public int getRowWidth() {
            return getWidth() - SCROLLBAR_W;
        }

        @Override
        public int scrollbarWidth() {
            return SCROLLBAR_W;
        }

        @Override
        protected int scrollBarX() {
            return getRight() - SCROLLBAR_W;
        }

        @Override
        protected void extractListBackground(@NonNull GuiGraphicsExtractor graphics) {
        }

        @Override
        protected void extractListSeparators(@NonNull GuiGraphicsExtractor graphics) {
        }

        @Override
        protected boolean entriesCanBeSelected() {
            return true;
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
        protected void extractSelection(GuiGraphicsExtractor graphics, Entry entry, int outlineColor) {
            int x = entry.getX();
            int y = entry.getY();
            int right = entry.getContentRight();
            int bottom = y + entry.getContentHeight();

            graphics.fill(x, y, right, bottom, 0xFF0A2833);
            graphics.outline(x, y, entry.getContentWidth(), entry.getHeight(), 0xFF37D5FF);
        }

        @Override
        protected void updateWidgetNarration(@NonNull NarrationElementOutput n) {
        }

        /// A flattened tree row: a `folder` (collapsible category) when `folder != null`,
        /// otherwise a pickable leaf carrying `suggestion`. `depth` is the indentation level.
        final class Entry extends AbstractSelectionList.Entry<@NonNull Entry> {
            @Nullable
            private final CategoryNode folder;
            @Nullable
            private final Suggestion<T> suggestion;
            private final int depth;

            Entry(CategoryNode folder, int depth) {
                this.folder = folder;
                this.suggestion = null;
                this.depth = depth;
            }

            Entry(Suggestion<T> suggestion, int depth) {
                this.folder = null;
                this.suggestion = suggestion;
                this.depth = depth;
            }

            @Override
            public void extractContent(@NonNull GuiGraphicsExtractor g, int mx, int my, boolean hovered, float a) {
                int textX = getX() + 6 + depth * INDENT;
                int textY = getY() + (getHeight() - font.lineHeight) / 2;

                if (folder != null) {
                    if (hovered) {
                        g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xFF262626);
                    }

                    String arrow = searching || expanded.contains(folder.path) ? "ᐁ" : "ᐅ";
                    g.text(font, arrow, textX, textY, 0xFFB0B0B0, false);
                    g.text(font, Component.literal(folder.name), textX + ARROW_W, textY, 0xFFE8C97A, false);

                    return;
                }

                if (getSelected() != this && hovered) {
                    g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xFF262626);
                }

                g.text(
                    font,
                    suggestion.label(),
                    textX + ARROW_W,
                    textY,
                    getSelected() == this ? 0xFFFFFFFF : 0xFFCCCCCC,
                    false
                );
            }

            @Override
            public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClicked) {
                if (folder != null) {
                    toggle(folder.path);
                    return true;
                }

                setSelected(this);
                confirmed = true;
                onConfirm.accept(suggestion.value());
                AkytheraEditorLayoutManager.getInstance().closeModal();

                return true;
            }
        }
    }

    /// ***************************************************************
    /// [AnchoredWidget] implementations                              *
    /// ***************************************************************
    private Unit x1;
    private Unit x2 = AnchoredWidget.pixel(MODAL_W);
    private Unit y1;
    private Unit y2 = AnchoredWidget.pixel(MODAL_H);
    private final AnchoredWidget.HorizontalAnchorType horizontalAnchorType = HorizontalAnchorType.LEFT_WIDTH;
    private final AnchoredWidget.VerticalAnchorType verticalAnchorType = VerticalAnchorType.TOP_HEIGHT;

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

    @Override
    public void relocate(ScreenRectangle screenRectangle) {
        AnchoredWidget.super.relocate(screenRectangle);

        searchBox.relocate(getRectangle());
        list.updateSizeAndPosition(MODAL_W - 2, MODAL_H - TITLE_H - SEARCH_H - 1, getX() + 1, getY() + TITLE_H + SEARCH_H);
    }
}
