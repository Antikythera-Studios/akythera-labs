package com.yesman.akytheralabs.gui.widget.main;

import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.client.api.gui.AkytheraButton;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsButtonTheme;
import com.yesman.akytheralabs.gui.widget.EditorEditBox;
import com.yesman.akytheralabs.gui.widget.OverlayingWindowModal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
import net.minecraft.world.item.Items;
import org.apache.commons.lang3.mutable.MutableInt;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

/// The item patch creation picker: an item to bind a patch to, or a name to create a **meta** patch
/// under — one bound to no item, existing only to be inherited through a child patch's parent
/// reference.
///
/// The item selection stays live in the meta section: whatever row is selected when `Enter` is
/// pressed becomes the meta patch's preview item.
public final class ItemPatchGenerateModal extends OverlayingWindowModal {
    private static final int WIDTH = 220;
    private static final int HEIGHT = 240;
    private static final int TITLE_H = 20;
    private static final int SEARCH_H = 18;
    private static final int LIST_TOP = TITLE_H + SEARCH_H;
    private static final int SCROLLBAR_W = 5;
    private static final int ROW_H = 18;
    private static final int ITEM_ICON_SIZE = 12;
    private static final int BUTTON_W = 54;
    private static final int BUTTON_H = 18;
    private static final int NAME_H = 18;
    private static final int SECTION_H = 16;
    private static final int PAD = 4;

    /// Offsets from the modal's bottom edge, bottom row up — the stack under the item list.
    private static final int ENTER_BOTTOM = PAD;
    private static final int NAME_BOTTOM = ENTER_BOTTOM + BUTTON_H + PAD;
    private static final int SECTION_BOTTOM = NAME_BOTTOM + NAME_H + PAD;
    private static final int SELECT_BOTTOM = SECTION_BOTTOM + SECTION_H + PAD;
    private static final int LIST_BOTTOM = SELECT_BOTTOM + BUTTON_H + PAD;

    /// The identifier path charset minus `/`: a meta name is one flat segment under the project's
    /// namespace segment.
    private static final Predicate<String> NAME_FILTER = name -> name.matches("[a-z0-9_.-]*");

    private final Font font;
    private final Consumer<Item> onItemPicked;
    private final BiConsumer<String, Item> onMetaCreated;
    private final Predicate<String> nameAvailable;

    private final ItemList itemList;
    private final EditorEditBox<String> searchBox;
    private final EditorEditBox<String> nameBox;
    private final AkytheraButton selectButton;
    private final AkytheraButton enterButton;

    /// `itemFilter` narrows the pick list. `nameAvailable` answers whether a typed meta name is still
    /// free — a taken one marks the box instead of creating anything. `onMetaCreated` receives the
    /// name and the selected preview item, [Items#AIR] when no row is selected.
    public ItemPatchGenerateModal(
        Font font,
        Predicate<Item> itemFilter,
        Predicate<String> nameAvailable,
        Consumer<Item> onItemPicked,
        BiConsumer<String, Item> onMetaCreated
    ) {
        super(
            AnchoredWidget.pixel(WIDTH),
            AnchoredWidget.pixel(HEIGHT),
            Component.translatable(LangKeys.GUI_MAIN_SCREEN_WIDGET_ITEM_PATCH_GENERATE_MODAL)
        );

        this.font = font;
        this.nameAvailable = nameAvailable;
        this.onItemPicked = onItemPicked;
        this.onMetaCreated = onMetaCreated;

        List<Item> allItems = BuiltInRegistries.ITEM.stream()
            .filter(itemFilter)
            .sorted(Comparator.comparing(item -> item.getDefaultInstance().getHoverName().getString()))
            .toList();

        this.itemList = new ItemList(allItems);

        this.searchBox = EditorEditBox.stringBox(
            font,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(TITLE_H), AnchoredWidget.pixel(SEARCH_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            () -> "",
            itemList::rebuild,
            Component.translatable(LangKeys.GUI_MESSAGE_EMPTY_SEARCHBAR)
        );
        searchBox.setWidth(WIDTH);
        searchBox.setHeight(SEARCH_H);

        this.selectButton = AkytheraButton
            .buttonBuilder(
                font,
                LocalizedMessage.translatable(LangKeys.GUI_MAIN_SCREEN_WIDGET_ITEM_PATCH_GENERATE_MODAL_SELECT),
                () -> pickSelectedItem())
            .xUnits(AnchoredWidget.pixel(PAD), AnchoredWidget.pixel(BUTTON_W))
            .yUnits(AnchoredWidget.pixel(SELECT_BOTTOM), AnchoredWidget.pixel(BUTTON_H))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.BOTTOM_HEIGHT)
            .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
            .build();

        this.nameBox = EditorEditBox.filteredStringBox(
            font,
            NAME_FILTER,
            Component.translatable(LangKeys.GUI_MAIN_SCREEN_WIDGET_ITEM_PATCH_GENERATE_MODAL_NAME),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.BOTTOM_HEIGHT
        );
        nameBox.addCharReplacement(' ', '_');
        nameBox.setX1(AnchoredWidget.pixel(PAD));
        nameBox.setX2(AnchoredWidget.pixel(PAD));
        nameBox.setY1(AnchoredWidget.pixel(NAME_BOTTOM));
        nameBox.setY2(AnchoredWidget.pixel(NAME_H));

        this.enterButton = AkytheraButton
            .buttonBuilder(
                font,
                LocalizedMessage.translatable(LangKeys.GUI_MAIN_SCREEN_WIDGET_ITEM_PATCH_GENERATE_MODAL_ENTER),
                () -> createMetaPatch())
            .xUnits(AnchoredWidget.pixel(PAD), AnchoredWidget.pixel(BUTTON_W))
            .yUnits(AnchoredWidget.pixel(ENTER_BOTTOM), AnchoredWidget.pixel(BUTTON_H))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.BOTTOM_HEIGHT)
            .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
            .build();
    }

    private void pickSelectedItem() {
        Item selected = itemList.selectedItem();

        if (selected != null) {
            onItemPicked.accept(selected);
        }
    }

    private void createMetaPatch() {
        String name = nameBox.getText();

        if (name.isBlank() || !nameAvailable.test(name)) {
            nameBox.mark();
            return;
        }

        Item previewItem = itemList.selectedItem();

        onMetaCreated.accept(name, previewItem == null ? Items.AIR : previewItem);
    }

    @Override
    public EditorEditBox<String> searchBox() {
        return searchBox;
    }

    @Override
    public @NonNull List<? extends GuiEventListener> children() {
        return List.of(searchBox, itemList, selectButton, nameBox, enterButton);
    }

    @Override
    protected int contentHeight() {
        return HEIGHT;
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

        selectButton.active = itemList.selectedItem() != null;
        enterButton.active = !nameBox.getText().isBlank();

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

        Component sectionTitle = Component.translatable(LangKeys.GUI_MAIN_SCREEN_WIDGET_ITEM_PATCH_GENERATE_MODAL_META);
        int sectionTop = getBottom() - SECTION_BOTTOM - SECTION_H;

        graphics.fill(getX() + PAD, sectionTop, getRight() - PAD, sectionTop + 1, 0xFF3A3A3A);
        graphics.text(
            font,
            sectionTitle,
            getX() + PAD,
            sectionTop + 1 + (SECTION_H - font.lineHeight) / 2,
            0xFFAAAAAA,
            false
        );

        searchBox.extractRenderState(graphics, mouseX, mouseY, a);
        itemList.extractRenderState(graphics, mouseX, mouseY, a);
        selectButton.extractRenderState(graphics, mouseX, mouseY, a);
        nameBox.extractRenderState(graphics, mouseX, mouseY, a);
        enterButton.extractRenderState(graphics, mouseX, mouseY, a);
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
            super(Minecraft.getInstance(), WIDTH, HEIGHT - LIST_TOP - LIST_BOTTOM, LIST_TOP, ROW_H);
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

        /// The item of the selected row, null while nothing is selected.
        @Nullable Item selectedItem() {
            ItemEntry selected = getSelected();

            return selected == null ? null : selected.item;
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

        /// The selection highlight is drawn per row instead.
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
                int bg = ItemList.this.getSelected() == this
                    ? 0xFF1E4D72
                    : hovered
                        ? 0xFF2A2A2A
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
                ItemList.this.setSelected(this);

                if (doubleClick) {
                    onItemPicked.accept(item);
                }

                return true;
            }
        }

        // ***************************************************************
        // [AnchoredWidget] implementations
        // ***************************************************************
        private Unit x1 = AnchoredWidget.pixel(0);
        private Unit x2 = AnchoredWidget.pixel(0);
        private Unit y1 = AnchoredWidget.pixel(LIST_TOP);
        private Unit y2 = AnchoredWidget.pixel(LIST_BOTTOM);
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
