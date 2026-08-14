package com.yesman.akytheralabs.gui.widget.main;

import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.AdvancedEditBox;
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
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.apache.commons.lang3.mutable.MutableInt;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/// Picks one entity out of the client level. The caller supplies the filter, so each editor screen
/// lists only what it can debug.
public final class EntitySelectModal extends OverlayingWindowModal {
    private static final int WIDTH = 220;
    private static final int HEIGHT = 180;
    private static final int TITLE_H = 20;
    private static final int SEARCH_H = 18;
    private static final int LIST_TOP = TITLE_H + SEARCH_H;
    private static final int SCROLLBAR_W = 5;
    private static final int ROW_H = 22;

    public static void open(final Font font, final Predicate<Entity> filter, final Consumer<Entity> onEntitySelected) {
        AkytheraEditorLayoutManager.getInstance().openModalWidgetAtCenter(new EntitySelectModal(font, filter, onEntitySelected));
    }

    private final Font font;
    private final Consumer<Entity> onEntitySelected;
    private final AdvancedEditBox<String> searchBox;
    private final EntityList entityList;

    private EntitySelectModal(final Font font, final Predicate<Entity> filter, final Consumer<Entity> onEntitySelected) {
        super(
            AnchoredWidget.pixel(WIDTH),
            AnchoredWidget.pixel(HEIGHT),
            Component.translatable(LangKeys.GUI_ENTITY_SELECT_MODAL_TITLE)
        );

        this.font = font;
        this.onEntitySelected = onEntitySelected;
        this.entityList = new EntityList(collectEntities(filter));

        this.searchBox = AdvancedEditBox.stringBox(
            font,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(TITLE_H), AnchoredWidget.pixel(SEARCH_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            () -> "",
            entityList::rebuild,
            Component.translatable(LangKeys.GUI_MESSAGE_EMPTY_SEARCHBAR)
        );
        searchBox.setWidth(WIDTH);
        searchBox.setHeight(SEARCH_H);
    }

    /// Every entity of the client level the filter accepts, in display-name order.
    private static List<Entity> collectEntities(final Predicate<Entity> filter) {
        ClientLevel level = Minecraft.getInstance().level;

        if (level == null) {
            return List.of();
        }

        List<Entity> entities = new ArrayList<>();

        for (Entity entity : level.entitiesForRendering()) {
            if (!entity.isRemoved() && filter.test(entity)) {
                entities.add(entity);
            }
        }

        entities.sort(Comparator.comparing(Entity::getPlainTextName));

        return entities;
    }

    @Override
    public @NonNull List<? extends GuiEventListener> children() {
        return List.of(searchBox, entityList);
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
        return entityList.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (event.isEscape() && AkytheraEditorLayoutManager.getInstance().isActive()) {
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
        entityList.extractRenderState(graphics, mouseX, mouseY, a);
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput n) {
    }

    @Override
    protected boolean closeOnPressBackground() {
        return true;
    }

    private void onSelect(final Entity entity) {
        AkytheraEditorLayoutManager.getInstance().closeModal();
        onEntitySelected.accept(entity);
    }

    private final class EntityList extends AbstractSelectionList<EntityList.@NonNull EntityEntry> implements AnchoredWidget {
        private final List<Entity> allEntities;

        EntityList(List<Entity> allEntities) {
            super(Minecraft.getInstance(), WIDTH, HEIGHT - LIST_TOP, LIST_TOP, ROW_H);
            this.allEntities = allEntities;
            rebuild("");
        }

        void rebuild(String query) {
            clearEntries();
            String lower = query.toLowerCase();
            MutableInt rowIndex = new MutableInt(0);

            allEntities.stream()
                .filter(entity -> lower.isEmpty() || matches(entity, lower))
                .forEach(entity -> addEntry(new EntityEntry(entity, rowIndex.getAndIncrement())));
        }

        private static boolean matches(Entity entity, String lower) {
            return entity.getPlainTextName().toLowerCase().contains(lower)
                || typeId(entity).toString().contains(lower);
        }

        private static Identifier typeId(Entity entity) {
            return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
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
        protected void updateWidgetNarration(@NonNull NarrationElementOutput n) {
        }

        final class EntityEntry extends AbstractSelectionList.Entry<@NonNull EntityEntry> {
            private final Entity entity;
            private final int rowIndex;

            EntityEntry(Entity entity, int rowIndex) {
                this.entity = entity;
                this.rowIndex = rowIndex;
            }

            @Override
            public void extractContent(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float a) {
                int bg = hovered
                    ? 0xFF1E4D72
                    : rowIndex % 2 == 0
                        ? 0xFF000000
                        : 0xFF111111;

                g.fill(getX() - 2, getY(), getX() + getWidth(), getY() + getHeight(), bg);

                String name = font.plainSubstrByWidth(entity.getPlainTextName(), getWidth() - 8);
                String type = font.plainSubstrByWidth(typeId(entity).toString(), getWidth() - 8);

                g.text(font, name, getX() + 4, getY() + 2, 0xFFCCCCCC, false);
                g.text(font, type, getX() + 4, getY() + 2 + font.lineHeight, 0xFF7A7A7A, false);
            }

            @Override
            public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
                onSelect(entity);
                return true;
            }
        }

        /// ***************************************************************
        /// [AnchoredWidget] implementations                              *
        /// ***************************************************************
        private Unit x1 = AnchoredWidget.pixel(0);
        private Unit x2 = AnchoredWidget.pixel(0);
        private Unit y1 = AnchoredWidget.pixel(LIST_TOP);
        private Unit y2 = AnchoredWidget.pixel(0);
        private final AnchoredWidget.HorizontalAnchorType horizontalAnchorType = HorizontalAnchorType.LEFT_RIGHT;
        private final AnchoredWidget.VerticalAnchorType verticalAnchorType = VerticalAnchorType.TOP_BOTTOM;

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
    }
}
