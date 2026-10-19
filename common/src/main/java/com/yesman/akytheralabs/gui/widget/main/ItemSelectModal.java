package com.yesman.akytheralabs.gui.widget.main;

import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.EditorEditBox;
import com.yesman.akytheralabs.gui.widget.OverlayingWindowModal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.mutable.MutableInt;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class ItemSelectModal extends OverlayingWindowModal {
    private static final int WIDTH  = 200;
    private static final int HEIGHT = 160;
    private static final int TITLE_H = 20;
    private static final int SEARCH_H = 18;
    private static final int LIST_TOP = TITLE_H + SEARCH_H;
    private static final int SCROLLBAR_W = 5;
    private static final int ROW_H = 18;
    private static final int ITEM_ICON_SIZE = 12;

    /// Top of the [#header] row, from the modal's top, in pixels.
    public static final int HEADER_Y = TITLE_H;

    /// Height of the [#header] row, in pixels.
    public static final int HEADER_H = 24;

    private final Font font;
    private final Consumer<Item> onItemSelected;
    private final EditorEditBox<String> searchBox;
    private ItemList itemList;

    /// A row between the title and the search box, labeled [#headerLabel], or null for none.
    private final @Nullable AbstractWidget header;
    private final Component headerLabel;

    /// The height [#header] takes, zero without one.
    private final int headerHeight;

    /// Lists every registered item.
    public ItemSelectModal(Font font, Consumer<Item> onItemSelected) {
        this(font, _ -> true, onItemSelected);
    }

    /// Lists the registered items `filter` accepts, in hover-name order.
    public ItemSelectModal(Font font, Predicate<Item> filter, Consumer<Item> onItemSelected) {
        this(font, filter, onItemSelected, Component.empty(), null);
    }

    /// [#ItemSelectModal(Font, Predicate, Consumer)] with `header`, labeled `headerLabel`, in a row of [#HEADER_H]
    /// above the search box. `header` is anchored inside the modal, its `y1` counted from the modal's top.
    public <W extends AbstractWidget & AnchoredWidget> ItemSelectModal(
        Font font, Predicate<Item> filter, Consumer<Item> onItemSelected, Component headerLabel, @Nullable W header
    ) {
        super(
            AnchoredWidget.pixel(WIDTH),
            AnchoredWidget.pixel(HEIGHT + (header == null ? 0 : HEADER_H)),
            Component.translatable(LangKeys.GUI_MAIN_SCREEN_WIDGET_ITEM_SELECT_MODAL)
        );
        this.font = font;
        this.onItemSelected = onItemSelected;
        this.header = header;
        this.headerLabel = headerLabel;
        this.headerHeight = header == null ? 0 : HEADER_H;

        this.searchBox = EditorEditBox.stringBox(
            font,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(TITLE_H + headerHeight), AnchoredWidget.pixel(SEARCH_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            () -> "",
            str -> itemList.rebuild(str),
            Component.translatable(LangKeys.GUI_MESSAGE_EMPTY_SEARCHBAR)
        );
        searchBox.setWidth(WIDTH);
        searchBox.setHeight(SEARCH_H);

        List<Item> allItems = BuiltInRegistries.ITEM.stream()
            .filter(filter)
            .sorted(Comparator.comparing(item -> item.getDefaultInstance().getHoverName().getString()))
            .toList();

        this.itemList = new ItemList(allItems);
    }

    @Override
    public EditorEditBox<String> searchBox() {
        return searchBox;
    }

    @Override
    public boolean confirmSoleResult() {
        if (itemList.children().size() != 1) return false;

        onItemSelected.accept(itemList.children().getFirst().item);

        return true;
    }

    @Override
    public @NonNull List<? extends GuiEventListener> children() {
        return header == null ? List.of(searchBox, itemList) : List.of(header, searchBox, itemList);
    }

    @Override
    protected int contentHeight() {
        return HEIGHT + headerHeight;
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
        return mouseX >= (double)getX()
            && mouseY >= (double)getY()
            && mouseX < (double)getRight()
            && mouseY < (double)getY() + TITLE_H;
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        // Route to children, then consume to prevent click-through to the screen below
        super.mouseClicked(event, doubleClick);

        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return itemList.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (event.isEscape()) {
            if (AkytheraEditorLayoutManager.getInstance().isActive()) {
                ModalManager.getInstance().closeModal();
                return true;
            }
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
    public boolean isMouseOver(double mouseX, double mouseY) {
        // This modal covers the whole screen, always return true to interrupt original screen inputs
        return isActive();
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractWidgetRenderState(graphics, mouseX, mouseY, a);

        graphics.fill(getX(), getY(), getRight(), getBottom(), 0xFF1E1E1E);
        graphics.fill(getX(), getY(), getRight(), getY() + 1, 0xFF3A3A3A);
        graphics.fill(getX(), getBottom() - 1, getRight(), getBottom(), 0xFF3A3A3A);
        graphics.fill(getX(), getY(), getX() + 1, getBottom(), 0xFF3A3A3A);
        graphics.fill(getRight() - 1, getY(), getRight(), getBottom(), 0xFF3A3A3A);
        graphics.fill(getX(), getY(), getRight(), getY() + TITLE_H, 0xFF2A2A2A);

        graphics.text(
            font,
            getMessage(),
            getX() + (WIDTH - font.width(getMessage())) / 2,
            getY() + (TITLE_H - font.lineHeight) / 2,
            0xFFCCCCCC,
            false
        );

        searchBox.extractRenderState(graphics, mouseX, mouseY, a);
        itemList.extractRenderState(graphics, mouseX, mouseY, a);

        // Drawn last, so an open dropdown lies over the list
        if (header != null) {
            graphics.text(font, headerLabel, getX() + 6, getY() + TITLE_H + (HEADER_H - font.lineHeight) / 2, 0xFFAAAAAA, false);
            header.extractRenderState(graphics, mouseX, mouseY, a);
        }
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput n) {
    }

    @Override
    protected boolean closeOnPressBackground() {
        return true;
    }

    private final class ItemList extends AbstractSelectionList<ItemList.@NonNull ItemEntry> implements AnchoredWidget {
        private final List<Item> allItems;

        ItemList(List<Item> allItems) {
            super(Minecraft.getInstance(), WIDTH, HEIGHT - LIST_TOP, LIST_TOP + headerHeight, ROW_H);
            this.allItems = allItems;
            rebuild("");
        }

        void rebuild(String query) {
            clearEntries();
            String lower = query.toLowerCase();
            MutableInt mi = new MutableInt(0);

            allItems.stream()
                .filter(item -> lower.isEmpty() || item.getDefaultInstance().getHoverName().getString().toLowerCase().contains(lower))
                .forEach(item -> addEntry(new ItemEntry(item, mi.getAndIncrement())));
        }

        @Override
        public int getRowWidth() {
            return getWidth() - SCROLLBAR_WIDTH;
        }

        @Override
        public int scrollbarWidth() {
            return SCROLLBAR_W;
        }

        @Override
        protected int scrollerHeight() {
            return Mth.clamp((int)((float)(this.height * this.height) / (float)this.contentHeight()), 8, this.height - 8);
        }

        @Override
        protected int scrollBarX() {
            return getRight() - scrollbarWidth();
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
            int sbX = scrollBarX(), scrollerH = scrollerHeight(), scrollerY = scrollBarY();
            boolean hovered = mouseX >= sbX && mouseX < sbX + SCROLLBAR_W && mouseY >= scrollerY && mouseY < scrollerY + scrollerH;

            graphics.fill(sbX, getY(), sbX + SCROLLBAR_W, getBottom(), 0xFF1A1A1A);
            graphics.fill(sbX + 1, scrollerY, sbX + SCROLLBAR_W - 1, scrollerY + scrollerH, hovered ? 0xFF6A6A6A : 0xFF505050);
        }

        @Override
        protected void updateWidgetNarration(@NonNull NarrationElementOutput n) {}

        final class ItemEntry extends AbstractSelectionList.Entry<@NonNull ItemEntry> {
            private final Item item;
            private final int rowIndex;

            ItemEntry(Item item, int rowIndex) {
                this.item = item;
                this.rowIndex = rowIndex;
            }

            @Override
            public void extractContent(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float a) {
                int bg = hovered
                    ? 0xFF1E4D72
                    : rowIndex % 2 == 0
                        ? 0xFF000000
                        : 0xFF111111;

                g.fill(getX()-2, getY(), getX() + getWidth(), getY() + getHeight(), bg);

                ItemStack stack = item.getDefaultInstance();
                g.item(stack, getX() + 2, getY() + (ROW_H - ITEM_ICON_SIZE) / 2);

                String label = font.plainSubstrByWidth(stack.getHoverName().getString(), getWidth() - ITEM_ICON_SIZE - 8);

                g.text(font, label, getX() + ITEM_ICON_SIZE + 8, getY() + (ROW_H - font.lineHeight) / 2, 0xFFCCCCCC, false);
            }

            @Override
            public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
                onItemSelected.accept(item);
                return true;
            }
        }

        // ***************************************************************
        // [AnchoredWidget] implementations
        // ***************************************************************
        private Unit x1 = AnchoredWidget.pixel(0);
        private Unit x2 = AnchoredWidget.pixel(0);
        private Unit y1 = AnchoredWidget.pixel(TITLE_H + SEARCH_H + headerHeight);
        private Unit y2 = AnchoredWidget.pixel(0);
        private AnchoredWidget.HorizontalAnchorType horizontalAnchorType = HorizontalAnchorType.LEFT_RIGHT;
        private AnchoredWidget.VerticalAnchorType verticalAnchorType = VerticalAnchorType.TOP_BOTTOM;

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