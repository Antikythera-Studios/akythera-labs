package com.yesman.akytheralabs.gui.widget.main;

import com.yesman.akythera.api.world.ability.AbilityTag;
import com.yesman.akythera.api.world.ability.AbilityTagSet;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.FrameResizer;
import com.yesman.akythera.client.api.gui.modal.Modal;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.client.api.gui.modal.ResizableFrame;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.EditorEditBox;
import com.yesman.akytheralabs.gui.widget.PopupBox;
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
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

/// Tag tree picker over every [AbilityTag] the current project and the loaded game know, one row per path
/// segment. Drops down under the [PopupBox] it opens from.
///
/// Picking many ticks a checkbox per row: a ticked tag shows its parents ticked, and unticking a tag
/// unticks everything beneath it while its parent stays ticked. Picking one ticks the clicked tag in
/// place of the pick, and unticking it empties the pick. Only a press outside the page closes the modal.
public final class AbilityTagSelectModal extends AbstractContainerWidget
    implements Modal, AnchoredWidget, ResizableFrame {

    private static final int TITLE_H = 18;
    private static final int SEARCH_H = 16;
    private static final int ROW_H = 14;
    private static final int SCROLLBAR_W = 5;
    private static final int MODAL_H = 200;
    private static final int INDENT = 10;
    private static final int ARROW_W = 10;
    private static final int CHECK_SIZE = 8;

    private static final int COLOR_CHECK_OUTLINE = 0xFF8A8A8A;
    private static final int COLOR_CHECK_FILL = 0xFF37D5FF;
    private static final int COLOR_LABEL = 0xFFCCCCCC;

    private final int modalW;
    private final Font font;
    private final boolean multiple;
    private final Consumer<List<AbilityTag>> onChange;
    private final EditorEditBox<String> searchBox;
    private final EntryList list;
    private final FrameResizer resizer;

    /// Every tag the tree shows, sorted by path. A tag's parent paths are tags too.
    private final TreeMap<String, AbilityTag> tagsByPath = new TreeMap<>();

    /// Paths whose children are listed while nothing is searched.
    private final Set<String> expanded = new HashSet<>();

    /// The picked tags, none of them above another.
    private List<AbilityTag> picked;

    /// @param multiple whether many tags are picked, see the class doc
    /// @param onChange takes the picked tags after every change; empty once cleared
    public AbilityTagSelectModal(
        Font font, int width, Component title, List<AbilityTag> picked, boolean multiple, Consumer<List<AbilityTag>> onChange
    ) {
        super(0, 0, width, MODAL_H, title, AbstractScrollArea.defaultSettings(0));

        this.modalW = width;
        this.resizer = new FrameResizer(this, width, MODAL_H);
        this.x2 = AnchoredWidget.pixel(width);
        this.font = font;
        this.multiple = multiple;
        this.onChange = onChange;
        this.picked = List.copyOf(picked);

        AkytheraProject project = AkytheraProject.getCurrentProject();

        for (Identifier id : project.getMergedDataBlockIdentifiers(AbilityTag.ENUM_MANAGER)) {
            Holder<AbilityTag> holder = project.getDataBlockHolder(AbilityTag.ENUM_MANAGER, id);
            if (holder == null || !holder.isBound()) continue;

            tagsByPath.put(id.getPath(), holder.value());
        }

        for (AbilityTag tag : this.picked) {
            String path = pathOf(tag);

            for (int cut = path.lastIndexOf('/'); cut > 0; cut = path.lastIndexOf('/', cut - 1)) {
                expanded.add(path.substring(0, cut));
            }
        }

        this.list = new EntryList();
        this.list.relist();

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

    /// A tag's path as its id spells it, which an unresolved stand-in answers too.
    private static String pathOf(AbilityTag tag) {
        Identifier id = tag.identifier();
        return id == null ? "" : id.getPath();
    }

    /// Whether `path` is picked, or sits above a picked tag.
    private boolean isTicked(String path) {
        for (AbilityTag tag : picked) {
            String tagPath = pathOf(tag);
            if (tagPath.equals(path) || tagPath.startsWith(path + "/")) return true;
        }

        return false;
    }

    /// Ticks or unticks `path`.
    private void press(String path) {
        List<AbilityTag> next = new ArrayList<>();

        if (!multiple) {
            if (!isTicked(path)) next.add(tagsByPath.get(path));
        } else if (isTicked(path)) {
            for (AbilityTag held : picked) {
                String heldPath = pathOf(held);
                if (!heldPath.equals(path) && !heldPath.startsWith(path + "/")) next.add(held);
            }

            int cut = path.lastIndexOf('/');
            AbilityTag parent = cut > 0 ? tagsByPath.get(path.substring(0, cut)) : null;

            if (parent != null && next.stream().noneMatch(held -> pathOf(held).startsWith(pathOf(parent) + "/"))) {
                next.add(parent);
            }
        } else {
            next.addAll(picked);
            next.add(tagsByPath.get(path));
        }

        picked = List.copyOf(AbilityTagSet.of(next).tags());
        onChange.accept(picked);
    }

    private void clear() {
        picked = List.of();
        onChange.accept(picked);
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
        if (event.isEscape()) {
            ModalManager.getInstance().closeModal();
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

    /// Picks the only registered tag the search matches, when picking one.
    @Override
    public boolean confirmSoleResult() {
        if (multiple) return false;

        List<String> matches = new ArrayList<>();

        for (EntryList.Entry entry : list.children()) {
            if (entry instanceof EntryList.TagEntry row && row.matched) {
                matches.add(row.path);
            }
        }

        if (matches.size() != 1) return false;

        press(matches.getFirst());

        return true;
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
        resizer.fitWidthToVisibleRows(list, list.children(), EntryList.Entry::textWidth, SCROLLBAR_W + 2);

        graphics.fill(getX(), getY(), getRight(), getBottom(), 0xFF1E1E1E);
        graphics.fill(getX(), getY(), getRight(), getY() + 1, 0xFF3A3A3A);
        graphics.fill(getX(), getBottom() - 1, getRight(), getBottom(), 0xFF3A3A3A);
        graphics.fill(getX(), getY(), getX() + 1, getBottom(), 0xFF3A3A3A);
        graphics.fill(getRight() - 1, getY(), getRight(), getBottom(), 0xFF3A3A3A);

        graphics.fill(getX(), getY(), getRight(), getY() + TITLE_H, 0xFF2A2A2A);
        graphics.text(font, getMessage(), getX() + 6, getY() + (TITLE_H - font.lineHeight) / 2, 0xFFCCCCCC, false);

        searchBox.extractRenderState(graphics, mouseX, mouseY, a);
        list.extractRenderState(graphics, mouseX, mouseY, a);

        resizer.requestCursor(graphics, mouseX, mouseY);
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput n) {
    }

    private final class EntryList extends AbstractSelectionList<EntryList.@NonNull Entry> {
        private String query = "";

        EntryList() {
            super(Minecraft.getInstance(), modalW - 2, MODAL_H - TITLE_H - SEARCH_H - 1, 0, ROW_H);
        }

        /// Lists the clear row, then every path under an expanded parent, or while `query` is typed,
        /// every path containing it along with its parents. The same query again keeps the rows and
        /// the scroll as they are.
        void rebuild(String query) {
            String lower = query.toLowerCase();
            if (lower.equals(this.query)) return;

            this.query = lower;
            relist();
            setScrollAmount(0.0D);
        }

        /// Lists the rows again for the current query, keeping the scroll.
        void relist() {
            double scroll = scrollAmount();
            clearEntries();

            addEntry(new ClearEntry());

            Set<String> shown = new HashSet<>();

            for (String path : tagsByPath.keySet()) {
                if (!query.isEmpty() && !path.toLowerCase().contains(query)) continue;

                shown.add(path);

                for (int cut = path.lastIndexOf('/'); cut > 0; cut = path.lastIndexOf('/', cut - 1)) {
                    shown.add(path.substring(0, cut));
                }
            }

            for (String path : tagsByPath.keySet()) {
                if (!shown.contains(path) || query.isEmpty() && !parentsExpanded(path)) continue;

                addEntry(new TagEntry(path, !query.isEmpty() && path.toLowerCase().contains(query)));
            }

            updateSizeAndPosition(getWidth(), getHeight(), getX(), getY());
            setScrollAmount(scroll);
        }

        private boolean parentsExpanded(String path) {
            for (int cut = path.lastIndexOf('/'); cut > 0; cut = path.lastIndexOf('/', cut - 1)) {
                if (!expanded.contains(path.substring(0, cut))) return false;
            }

            return true;
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

        abstract class Entry extends AbstractSelectionList.Entry<@NonNull Entry> {
            /// Width the row needs to show its label unclipped.
            abstract int textWidth();
        }

        /// Empties the pick.
        final class ClearEntry extends Entry {
            private final Component label = Component.translatable(LangKeys.GUI_ABILITY_TAG_SELECT_MODAL_CLEAR);

            @Override
            int textWidth() {
                return 6 + font.width(label) + 6;
            }

            @Override
            public void extractContent(@NonNull GuiGraphicsExtractor g, int mx, int my, boolean hovered, float a) {
                g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), hovered ? 0xFF262626 : 0xFF141414);
                g.text(font, label, getX() + 6, getY() + (getHeight() - font.lineHeight) / 2, 0xFF888888, false);
            }

            @Override
            public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClicked) {
                clear();

                return true;
            }
        }

        /// One path segment: a fold arrow while it has children, a checkbox, and the segment's name.
        final class TagEntry extends Entry {
            private final String path;
            private final String label;
            private final int depth;
            private final boolean hasChildren;

            /// Whether the path itself contains the search, rather than being listed as a match's parent.
            private final boolean matched;

            TagEntry(String path, boolean matched) {
                this.path = path;
                this.label = path.substring(path.lastIndexOf('/') + 1);
                this.depth = (int) path.chars().filter(c -> c == '/').count();
                this.matched = matched;

                String next = tagsByPath.higherKey(path);
                this.hasChildren = next != null && next.startsWith(path + "/");
            }

            private int arrowX() {
                return getX() + 4 + depth * INDENT;
            }

            @Override
            int textWidth() {
                return 4 + depth * INDENT + ARROW_W + CHECK_SIZE + 4 + font.width(label) + 6;
            }

            @Override
            public void extractContent(@NonNull GuiGraphicsExtractor g, int mx, int my, boolean hovered, float a) {
                if (hovered) g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xFF262626);

                int textY = getY() + (getHeight() - font.lineHeight) / 2;

                if (hasChildren) {
                    boolean open = !query.isEmpty() || expanded.contains(path);
                    g.text(font, open ? "▼" : "▶", arrowX(), textY, 0xFF8A8A8A, false);
                }

                int checkX = arrowX() + ARROW_W;
                int checkY = getY() + (getHeight() - CHECK_SIZE) / 2;

                g.outline(checkX, checkY, CHECK_SIZE, CHECK_SIZE, COLOR_CHECK_OUTLINE);
                if (isTicked(path)) g.fill(checkX + 2, checkY + 2, checkX + CHECK_SIZE - 2, checkY + CHECK_SIZE - 2, COLOR_CHECK_FILL);

                g.text(font, label, checkX + CHECK_SIZE + 4, textY, COLOR_LABEL, false);
            }

            @Override
            public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClicked) {
                if (hasChildren && query.isEmpty() && event.x() >= arrowX() && event.x() < arrowX() + ARROW_W) {
                    if (!expanded.remove(path)) expanded.add(path);

                    relist();

                    return true;
                }

                press(path);

                return true;
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

        searchBox.relocate(getRectangle());
        list.updateSizeAndPosition(
            getWidth() - 2, getHeight() - TITLE_H - SEARCH_H - 1, getX() + 1, getY() + TITLE_H + SEARCH_H);
    }
}
