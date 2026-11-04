package com.yesman.akytheralabs.gui.widget.main;

import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.client.api.gui.AkytheraButton;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.DropdownSizeMemory;
import com.yesman.akythera.client.api.gui.modal.FrameResizer;
import com.yesman.akythera.client.api.gui.modal.Modal;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.client.api.gui.modal.ResizableFrame;
import com.yesman.akytheralabs.editor.keybind.EditorKeyMappings;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.*;
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
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/// Search-and-pick modal over choices split into categories, each listed under its own header row in
/// the given order. Single-click confirms and reports the picked value. Drops down under the
/// [PopupBox] it opens from. Its title bar takes buttons ([#titleButton]), such as a `+` that makes a new choice, and
/// its bottom one more ([#footerButton]).
///
/// A category with a [RowEditor] edits its rows in place: [#creatingButton] adds a row named in an edit box,
/// [EditorKeyMappings#RENAME] renames the row under the cursor there, and a `✕` on the row removes it. A double-click
/// opens the row ([RowEditor#open]), so a single click on such a row confirms once the double-click time has passed.
///
/// @param <T> the picked value
public final class CategorizedSelectModal<T> extends AbstractContainerWidget
    implements Modal, AnchoredWidget, ResizableFrame {

    private static final int TITLE_H = 18;
    private static final int SEARCH_H = 16;
    private static final int ROW_H = 14;
    private static final int SCROLLBAR_W = 5;
    private static final int MODAL_H = 200;
    private static final int TITLE_BUTTON = 14;
    private static final int FOOTER_H = 20;
    private static final int ICON_SIZE = 10;
    private static final int DEFAULT_CHOICE_COLOR = 0xFFCCCCCC;
    private static final int REMOVE_COLOR = 0xFF888888;
    private static final int REMOVE_HOVER_COLOR = 0xFFE05050;

    /// The longest gap between two clicks that make a double-click, in milliseconds. Vanilla's `MouseHandler`
    /// threshold.
    private static final long DOUBLE_CLICK_MILLIS = 250L;

    /// Width of the popped-up page, matched to the [PopupBox] it drops from.
    private final int modalW;
    private final Font font;
    private final Consumer<T> onConfirm;
    private final EditorEditBox<String> searchBox;
    private final EntryList list;

    /// The title bar's buttons, right to left.
    private final List<AkytheraButton> titleButtons = new ArrayList<>();

    /// The button across the bottom, under the list. Null for none.
    private @Nullable AkytheraButton footerButton;

    /// The page is resizable by dragging its frame; the opening size is its floor, and a [DropdownSizeMemory]
    /// anchor keeps the dragged one for the next open.
    private final FrameResizer resizer;

    /// The categories, each holding its own copy of the choices so an edit can add, rename and remove them.
    private final List<Category<T>> categories;

    /// The row being named in [#rowBox], or null while none is.
    private @Nullable RowEdit<T> edit;

    /// The edit a click asked for, started once the click is over. The container focuses the clicked widget after
    /// its press, which would take the focus off a [#rowBox] opened during the press and commit it at once.
    private @Nullable RowEdit<T> requestedEdit;

    /// The edit box naming [#edit]'s row. Null while no row is named.
    private @Nullable EditorEditBox<String> rowBox;

    /// The row a single click picked, confirmed once [#DOUBLE_CLICK_MILLIS] passed without a second click.
    private @Nullable Choice<T> pendingConfirm;

    /// When [#pendingConfirm] was clicked, in milliseconds.
    private long pendingSince;

    /// A header row and the choices listed under it. A category with no choice matching the search is
    /// hidden.
    ///
    /// @param editor what the category does with rows added, renamed and removed in place. Null for none
    public record Category<T>(Component title, List<Choice<T>> choices, @Nullable RowEditor<T> editor) {
        public Category(final Component title, final List<Choice<T>> choices) {
            this(title, choices, null);
        }
    }

    /// What a category does with the rows the user adds, renames and removes in place.
    public interface RowEditor<T> {
        /// Whether `name` may name a row.
        boolean accepts(String name);

        /// The name `choice` is edited from.
        String nameOf(Choice<T> choice);

        /// The row named `name`, made new. Null when making it failed.
        @Nullable Choice<T> create(String name);

        /// `choice` renamed to `name`. Null when renaming failed.
        @Nullable Choice<T> rename(Choice<T> choice, String name);

        void remove(Choice<T> choice);

        /// Opens `choice` for editing, after the modal closed.
        void open(Choice<T> choice);
    }

    /// A row being named: a new one of `category` when `choice` is null, else `choice` being renamed.
    private record RowEdit<T>(Category<T> category, @Nullable Choice<T> choice) {}

    /// One pickable row: its label, also what the search matches, and the value it reports.
    ///
    /// @param color the label's color, which also tints the icon. ARGB
    /// @param icon  a GUI sprite drawn left of the label, or null for none
    public record Choice<T>(String label, T value, int color, @Nullable Identifier icon) {
        public Choice(final String label, final T value) {
            this(label, value, DEFAULT_CHOICE_COLOR, null);
        }
    }

    public CategorizedSelectModal(Font font, int width, Component title, List<Category<T>> categories, Consumer<T> onConfirm) {
        super(0, 0, width, MODAL_H, title, AbstractScrollArea.defaultSettings(0));

        this.modalW = width;
        this.resizer = new FrameResizer(this, width, MODAL_H);
        this.x2 = AnchoredWidget.pixel(width);
        this.font = font;
        this.onConfirm = onConfirm;
        this.categories = new ArrayList<>();

        categories.forEach(category -> this.categories.add(new Category<>(category.title(), new ArrayList<>(category.choices()), category.editor())));

        this.list = new EntryList();
        this.list.rebuild("");

        this.searchBox = EditorEditBox.stringBox(
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

    /// Adds a button to the title bar, left of the ones already there.
    public CategorizedSelectModal<T> titleButton(final LocalizedMessage label, final Runnable onPress) {
        titleButtons.add(
            AkytheraButton.buttonBuilder(font, label, onPress)
                .xUnits(AnchoredWidget.pixel(2 + titleButtons.size() * (TITLE_BUTTON + 2)), AnchoredWidget.pixel(TITLE_BUTTON))
                .yUnits(AnchoredWidget.pixel((TITLE_H - TITLE_BUTTON) / 2), AnchoredWidget.pixel(TITLE_BUTTON))
                .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH)
                .verticalAnchorType(AnchoredWidget.VerticalAnchorType.TOP_HEIGHT)
                .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
                .build()
        );

        return this;
    }

    /// Adds a title bar button that starts a new row of `category`, named in place.
    public CategorizedSelectModal<T> creatingButton(final LocalizedMessage label, final Category<T> category) {
        return titleButton(label, () -> categories.stream()
            .filter(candidate -> candidate.editor() != null && candidate.editor() == category.editor())
            .findFirst()
            .ifPresent(editable -> requestedEdit = new RowEdit<>(editable, null)));
    }

    /// Opens [#rowBox] over the row `edit` names, focused.
    private void startEdit(final RowEdit<T> edit) {
        endEdit();

        RowEditor<T> editor = Objects.requireNonNull(edit.category().editor());
        String initial = edit.choice() == null ? "" : editor.nameOf(edit.choice());
        EditorEditBox<String> box = EditorEditBox.stringBox(
            font,
            AnchoredWidget.pixel(2), AnchoredWidget.pixel(2),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM,
            () -> initial,
            text -> {
                if (rowBox == null) return;

                if (text.isEmpty() || text.equals(initial) || editor.accepts(text)) {
                    rowBox.unmark();
                } else {
                    rowBox.mark();
                }
            },
            Component.empty()
        );

        box.setValue(initial);
        box.setOnCommitted((_, text) -> commitEdit(text));

        this.edit = edit;
        this.rowBox = box;
        pendingConfirm = null;
        list.refresh();
        setFocused(box);
        box.setFocused(true);
    }

    /// Applies what [#rowBox] names, when it is a name the category accepts and not the row's own.
    private void commitEdit(final String text) {
        RowEdit<T> committed = edit;

        if (committed == null) return;

        edit = null;
        rowBox = null;

        RowEditor<T> editor = Objects.requireNonNull(committed.category().editor());
        List<Choice<T>> choices = committed.category().choices();
        Choice<T> renamed = committed.choice();

        if (!text.isBlank() && (renamed == null || !text.equals(editor.nameOf(renamed))) && editor.accepts(text)) {
            if (renamed == null) {
                Choice<T> created = editor.create(text);

                if (created != null) choices.add(created);
            } else {
                Choice<T> result = editor.rename(renamed, text);

                if (result != null) choices.set(choices.indexOf(renamed), result);
            }
        }

        list.refresh();
    }

    /// Drops [#rowBox] without applying it.
    private void endEdit() {
        EditorEditBox<String> box = rowBox;

        edit = null;
        rowBox = null;

        if (box != null) {
            box.setFocused(false);
            list.refresh();
        }
    }

    /// Removes `choice`, a row of `category`.
    private void remove(final Category<T> category, final Choice<T> choice) {
        endEdit();
        Objects.requireNonNull(category.editor()).remove(choice);
        category.choices().remove(choice);
        list.refresh();
    }

    /// Puts a button across the bottom, under the list.
    public CategorizedSelectModal<T> footerButton(final LocalizedMessage label, final Runnable onPress) {
        footerButton = AkytheraButton.buttonBuilder(font, label, onPress)
            .xUnits(AnchoredWidget.pixel(2), AnchoredWidget.pixel(2))
            .yUnits(AnchoredWidget.pixel(2), AnchoredWidget.pixel(FOOTER_H - 4))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.BOTTOM_HEIGHT)
            .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
            .build();

        return this;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        // Cover the screen so we always intercept clicks (close-on-outside).
        return isActive();
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        // The frame grip is tested first: it lies just outside the page, where a press would otherwise
        // dismiss the modal.
        if (event.button() == 0 && resizer.mousePressed(event.x(), event.y())) {
            return true;
        }

        boolean overPage = event.x() >= getX() && event.x() < getRight() && event.y() >= getY() && event.y() < getBottom();

        if (!overPage) {
            ModalManager.getInstance().closeModal();
            return true;
        }

        super.mouseClicked(event, doubleClick);

        if (requestedEdit != null) {
            RowEdit<T> requested = requestedEdit;

            requestedEdit = null;
            startEdit(requested);
        }

        return true;
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
        return resizer.mouseDragged(event.x(), event.y()) || super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        return resizer.mouseReleased() || super.mouseReleased(event);
    }

    @Override
    public FrameResizer frameResizer() {
        return resizer;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return list.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (edit == null && EditorKeyMappings.RENAME.matches(event)) {
            EntryList.ChoiceEntry row = list.renameTarget();

            if (row != null) {
                startEdit(new RowEdit<>(row.category, row.choice));
                return true;
            }
        }

        if (event.isEscape()) {
            if (edit != null) {
                endEdit();
            } else {
                ModalManager.getInstance().closeModal();
            }

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
    public EditorEditBox<String> searchBox() {
        return searchBox;
    }

    @Override
    public boolean confirmSoleResult() {
        List<EntryList.ChoiceEntry> rows = new ArrayList<>();

        for (EntryList.Entry entry : list.children()) {
            if (entry instanceof EntryList.ChoiceEntry row) rows.add(row);
        }

        if (rows.size() != 1) return false;

        rows.getFirst().confirm();

        return true;
    }

    @Override
    public @NonNull List<? extends GuiEventListener> children() {
        List<GuiEventListener> children = new ArrayList<>();

        if (rowBox != null) children.add(rowBox);

        children.addAll(titleButtons);

        children.add(searchBox);
        children.add(list);

        if (footerButton != null) children.add(footerButton);

        return children;
    }

    @Override
    protected int contentHeight() {
        return getHeight();
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        if (pendingConfirm != null && System.currentTimeMillis() - pendingSince > DOUBLE_CLICK_MILLIS) {
            Choice<T> confirmed = pendingConfirm;

            pendingConfirm = null;
            confirm(confirmed);

            return;
        }

        resizer.fitWidthToVisibleRows(list, list.children(), EntryList.Entry::textWidth, SCROLLBAR_W + 2);

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

        titleButtons.forEach(button -> button.extractRenderState(graphics, mouseX, mouseY, a));
        searchBox.extractRenderState(graphics, mouseX, mouseY, a);
        list.extractRenderState(graphics, mouseX, mouseY, a);

        if (rowBox != null) {
            EntryList.EditEntry row = list.editEntry();

            if (row != null && row.getY() >= list.getY() && row.getY() + row.getHeight() <= list.getBottom()) {
                rowBox.relocate(new ScreenRectangle(row.getX(), row.getY(), row.getWidth(), row.getHeight()));
                rowBox.extractRenderState(graphics, mouseX, mouseY, a);
            }
        }

        if (footerButton != null) footerButton.extractRenderState(graphics, mouseX, mouseY, a);

        resizer.requestCursor(graphics, mouseX, mouseY);
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput n) {
    }

    /// Closes the modal, then reports `choice`.
    private void confirm(final Choice<T> choice) {
        // Closed first, so a pick may open a modal of its own
        ModalManager.getInstance().closeModal();
        onConfirm.accept(choice.value());
    }

    private final class EntryList extends AbstractSelectionList<EntryList.@NonNull Entry> {
        /// The query the rows are listed for. Null until the first [#rebuild].
        private @Nullable String query;

        EntryList() {
            super(Minecraft.getInstance(), modalW - 2, MODAL_H - TITLE_H - SEARCH_H - 1, 0, ROW_H);
        }

        /// Lists the rows again for the same query, after an edit changed them.
        void refresh() {
            String current = query == null ? "" : query;
            double scroll = scrollAmount();

            query = null;
            rebuild(current);
            setScrollAmount(scroll);

            EditEntry row = editEntry();

            if (row != null) scrollToEntry(row);
        }

        /// The editable row under the cursor, or null when there is none.
        @Nullable ChoiceEntry renameTarget() {
            return getHovered() instanceof ChoiceEntry row && row.category.editor() != null ? row : null;
        }

        /// The row [#rowBox] names, or null when none is listed.
        @Nullable EditEntry editEntry() {
            for (Entry entry : children()) {
                if (entry instanceof EditEntry row) return row;
            }

            return null;
        }

        /// The same query again keeps the rows and the scroll as they are.
        void rebuild(String query) {
            if (query.equals(this.query)) return;

            this.query = query;
            clearEntries();

            String lower = query.toLowerCase();

            for (Category<T> category : categories) {
                List<Choice<T>> matches = category.choices().stream()
                    .filter(choice -> lower.isEmpty() || choice.label().toLowerCase().contains(lower) || edit != null && edit.choice() == choice)
                    .sorted(Comparator.comparing(Choice::label))
                    .toList();
                boolean creating = edit != null && edit.category() == category && edit.choice() == null;

                if (matches.isEmpty() && !creating) continue;

                addEntry(new TitleEntry(category.title()));

                if (creating) addEntry(new EditEntry(""));

                for (Choice<T> choice : matches) {
                    addEntry(edit != null && edit.choice() == choice ? new EditEntry(choice.label()) : new ChoiceEntry(category, choice));
                }
            }

            setScrollAmount(0.0D);
            updateSizeAndPosition(getWidth(), getHeight(), getX(), getY());
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

        abstract class Entry extends AbstractSelectionList.Entry<@NonNull Entry> {
            /// Width the row needs to show its label unclipped.
            abstract int textWidth();
        }

        /// Non-selectable category header, ignoring clicks so it can't be picked.
        final class TitleEntry extends Entry {
            private final Component label;

            TitleEntry(Component label) {
                this.label = label;
            }

            @Override
            int textWidth() {
                return 6 + font.width(label) + 6;
            }

            @Override
            public void extractContent(@NonNull GuiGraphicsExtractor g, int mx, int my, boolean hovered, float a) {
                g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xFF141414);
                g.text(font, label, getX() + 6, getY() + (getHeight() - font.lineHeight) / 2, 0xFF888888, false);
            }

            @Override
            public boolean mouseClicked(@NonNull MouseButtonEvent ev, boolean dc) {
                return false;
            }
        }

        /// The row [#rowBox] is drawn over.
        final class EditEntry extends Entry {
            private final String label;

            EditEntry(String label) {
                this.label = label;
            }

            @Override
            int textWidth() {
                return 6 + font.width(label) + 6;
            }

            @Override
            public void extractContent(@NonNull GuiGraphicsExtractor g, int mx, int my, boolean hovered, float a) {
            }

            @Override
            public boolean mouseClicked(@NonNull MouseButtonEvent ev, boolean dc) {
                return false;
            }
        }

        final class ChoiceEntry extends Entry {
            private final Category<T> category;
            private final Choice<T> choice;

            ChoiceEntry(Category<T> category, Choice<T> choice) {
                this.category = category;
                this.choice = choice;
            }

            /// Whether `x` lies on the `✕` an editable row draws at its right end.
            private boolean onRemove(double x) {
                return category.editor() != null && x >= getContentRight() - ROW_H;
            }

            /// Where the label starts, past the icon when there is one.
            private int labelInset() {
                return choice.icon() == null ? 6 : 6 + ICON_SIZE + 4;
            }

            @Override
            int textWidth() {
                return labelInset() + font.width(choice.label()) + 6 + (category.editor() == null ? 0 : ROW_H);
            }

            @Override
            public void extractContent(@NonNull GuiGraphicsExtractor g, int mx, int my, boolean hovered, float a) {
                if (getSelected() != this && hovered) {
                    g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xFF262626);
                }

                if (choice.icon() != null) {
                    g.blitSprite(RenderPipelines.GUI_TEXTURED, choice.icon(), getX() + 6, getY() + (getHeight() - ICON_SIZE) / 2, ICON_SIZE, ICON_SIZE, choice.color());
                }

                g.text(
                    font,
                    Component.literal(choice.label()),
                    getX() + labelInset(),
                    getY() + (getHeight() - font.lineHeight) / 2,
                    getSelected() == this ? 0xFFFFFFFF : choice.color(),
                    false
                );

                if (category.editor() != null) {
                    String cross = "✕";

                    g.text(
                        font,
                        cross,
                        getContentRight() - (ROW_H + font.width(cross)) / 2,
                        getY() + (getHeight() - font.lineHeight) / 2,
                        hovered && onRemove(mx) ? REMOVE_HOVER_COLOR : REMOVE_COLOR,
                        false
                    );
                }
            }

            @Override
            public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClicked) {
                if (category.editor() == null) {
                    confirm();
                } else if (onRemove(event.x())) {
                    pendingConfirm = null;
                    remove(category, choice);
                } else if (doubleClicked && pendingConfirm == choice) {
                    pendingConfirm = null;
                    ModalManager.getInstance().closeModal();
                    category.editor().open(choice);
                } else {
                    setSelected(this);
                    pendingConfirm = choice;
                    pendingSince = System.currentTimeMillis();
                }

                return true;
            }

            void confirm() {
                setSelected(this);
                CategorizedSelectModal.this.confirm(choice);
            }
        }
    }

    // ***************************************************************
    // [AnchoredWidget] implementations
    // ***************************************************************
    private Unit x1;
    private Unit x2;
    private Unit y1;
    private Unit y2 = AnchoredWidget.pixel(MODAL_H);
    private AnchoredWidget.HorizontalAnchorType horizontalAnchorType = HorizontalAnchorType.LEFT_WIDTH;
    private AnchoredWidget.VerticalAnchorType verticalAnchorType = VerticalAnchorType.TOP_HEIGHT;

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

    /// The constructor's `width` is only the starting size: the opener may widen the modal to its anchor (see
    /// [ModalManager#openModalWidgetUnder]), so the list follows the rect the units just
    /// produced.
    @Override
    public void relocate(ScreenRectangle screenRectangle) {
        AnchoredWidget.super.relocate(screenRectangle);

        titleButtons.forEach(button -> button.relocate(getRectangle()));
        searchBox.relocate(getRectangle());
        list.updateSizeAndPosition(
            getWidth() - 2, getHeight() - TITLE_H - SEARCH_H - 1 - (footerButton == null ? 0 : FOOTER_H), getX() + 1, getY() + TITLE_H + SEARCH_H);

        if (footerButton != null) footerButton.relocate(getRectangle());
    }
}
