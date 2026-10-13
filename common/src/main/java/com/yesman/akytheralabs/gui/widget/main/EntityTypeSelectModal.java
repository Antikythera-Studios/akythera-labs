package com.yesman.akytheralabs.gui.widget.main;

import com.mojang.blaze3d.platform.Window;
import com.yesman.akythera.client.api.gui.CloseableWidget;
import com.yesman.akythera.client.api.gui.ResizeCompleteCallbackListener;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.client.api.gui.viewer.BackgroundComponent;
import com.yesman.akythera.client.api.gui.viewer.CameraComponent;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.dataholder.EntityPreviewModelMapping;
import com.yesman.akytheralabs.generated.LangKeys;
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
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/// Picks the entity type a holder is previewed as. What it is drawn with — mesh and armature — comes
/// from [EntityPreviewModelMapping]; an entity with no mesh bound reads dim in the list and previews
/// empty.
public final class EntityTypeSelectModal extends OverlayingWindowModal
    implements ResizeCompleteCallbackListener, CloseableWidget
{
    private static final int TITLE_H = 20;
    private static final int SEARCH_H = 18;
    private static final int ROW_H = 18;
    private static final int SCROLLBAR_W = 5;
    private static final int DIMMER_COLOR = 0x88000000;

    private final Font font;
    private final Consumer<Identifier> onConfirm;
    private final EditorEditBox<String> searchBox;
    private final TypeList typeList;
    private final Model3DViewer preview;

    public EntityTypeSelectModal(Font font, @Nullable Identifier initialType, Consumer<Identifier> onConfirm) {
        super(
            AnchoredWidget.fraction(75), AnchoredWidget.fraction(80),
            Component.translatable(LangKeys.GUI_ENTITY_TYPE_SELECT_MODAL_TITLE)
        );

        this.font = font;
        this.onConfirm = onConfirm;
        this.typeList = new TypeList();
        this.typeList.rebuild("");

        this.searchBox = EditorEditBox.stringBox(
            font,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(TITLE_H), AnchoredWidget.pixel(SEARCH_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            () -> "",
            typeList::rebuild,
            Component.translatable(LangKeys.GUI_MESSAGE_EMPTY_SEARCHBAR)
        );

        this.preview = new Model3DViewer(
            AnchoredWidget.pixel(0), AnchoredWidget.fraction(50),
            AnchoredWidget.pixel(TITLE_H + SEARCH_H), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM,
            AkytheraEditorLayoutManager.getInstance()::getLayoutGuiScale
        );

        preview.addComponent(new CameraComponent(true));
        preview.addComponent(new BackgroundComponent(18, 18, 18, 255));

        if (initialType != null) {
            getRowById(initialType).ifPresent(typeList::setSelected);
        }
    }

    private Optional<TypeList.Entry> getRowById(Identifier id) {
        return typeList.children().stream()
            .filter(e -> BuiltInRegistries.ENTITY_TYPE.getKey(e.type()).equals(id))
            .findFirst();
    }

    @Override
    public void onResizeCompleted() {
        preview.onResizeCompleted();
    }

    @Override
    public void destroy() {
        preview.destroy();
    }

    @Override
    public EditorEditBox<String> searchBox() {
        return searchBox;
    }

    @Override
    public boolean confirmSoleResult() {
        if (typeList.children().size() != 1) return false;

        typeList.children().getFirst().confirm();

        return true;
    }

    @Override
    public @NonNull List<? extends GuiEventListener> children() {
        return List.of(searchBox, typeList, preview);
    }

    @Override
    protected int contentHeight() {
        return getHeight();
    }

    @Override
    public void setFocused(final @Nullable GuiEventListener focused) {
        if (getFocused() == focused) {
            return;
        }

        super.setFocused(focused);
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (!super.isMouseOver(event.x(), event.y())) {
            // Close modal if pressed out of the box
            ModalManager.getInstance().closeModal();
            return true;
        }

        super.mouseClicked(event, doubleClick);

        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return typeList.mouseScrolled(mouseX, mouseY, scrollX, scrollY) || preview.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
    public boolean isMouseOverHeader(double mouseX, double mouseY) {
        return getY() <= mouseY && mouseY <= getY() + TITLE_H;
    }

    @Override
    protected boolean closeOnPressBackground() {
        return true;
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        Window window = Minecraft.getInstance().getWindow();
        AkytheraEditorLayoutManager layoutManager = AkytheraEditorLayoutManager.getInstance();

        // Dimmer spanning the whole viewport
        graphics.fill(
            0, 0,
            window.getScreenWidth() / layoutManager.getLayoutGuiScale(),
            window.getScreenHeight() / layoutManager.getLayoutGuiScale(),
            DIMMER_COLOR
        );

        // Modal frame
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
            getX() + 8,
            getY() + (TITLE_H - font.lineHeight) / 2,
            0xFFCCCCCC,
            false
        );

        searchBox.extractRenderState(graphics, mouseX, mouseY, a);
        typeList.extractRenderState(graphics, mouseX, mouseY, a);
        preview.extractRenderState(graphics, mouseX, mouseY, a);
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput n) {
    }

    private final class TypeList extends AbstractSelectionList<TypeList.@NonNull Entry> implements AnchoredWidget {
        TypeList() {
            super(Minecraft.getInstance(), 0, 0, 0, ROW_H);
        }

        void rebuild(String query) {
            clearEntries();
            String lower = query.toLowerCase();

            for (EntityType<?> type : EntityPreviewModelMapping.previewableTypes()) {
                if (!lower.isEmpty() && !EntityPreviewModelMapping.label(type).toLowerCase().contains(lower)) continue;
                addEntry(new Entry(type));
            }

            // Effectively calls repositionEntries
            updateSizeAndPosition(getWidth(), getHeight(), getX(), getY());
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
            if (!isMouseOver(mx, my)) return false;
            return super.mouseScrolled(mx, my, scrollX, scrollY);
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
            return Mth.clamp((int) ((float) (this.height * this.height) / (float) this.contentHeight()), 8, this.height - 8);
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
            return true;
        }

        @Override
        protected void extractScrollbar(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            if (!scrollable()) return;
            int sbX = scrollBarX(), scrollerH = scrollerHeight(), scrollerY = scrollBarY();
            boolean hovered = mouseX >= sbX && mouseX < sbX + SCROLLBAR_W && mouseY >= scrollerY && mouseY < scrollerY + scrollerH;

            graphics.fill(sbX, getY(), sbX + SCROLLBAR_W, getBottom() - 1, 0xFF1A1A1A);
            graphics.fill(sbX + 1, scrollerY, sbX + SCROLLBAR_W - 1, scrollerY + scrollerH, hovered ? 0xFF6A6A6A : 0xFF505050);
        }

        @Override
        protected void extractSelection(GuiGraphicsExtractor graphics, Entry entry, int outlineColor) {
            int x = entry.getX(), y = entry.getY();
            int right = entry.getContentRight();
            int bottom = y + entry.getContentHeight();
            graphics.fill(x, y, right, bottom, 0xFF0A2833);
            graphics.outline(x, y, entry.getContentWidth(), entry.getHeight(), 0xFF37D5FF);
        }

        @Override
        public void setSelected(@Nullable Entry selected) {
            super.setSelected(selected);

            if (selected != null) {
                EntityPreviewModelMapping.display(
                    preview, BuiltInRegistries.ENTITY_TYPE.getKey(selected.type()), true, true);
            }
        }

        @Override
        protected void updateWidgetNarration(@NonNull NarrationElementOutput n) {
        }

        final class Entry extends AbstractSelectionList.Entry<@NonNull Entry> {
            private final EntityType<?> type;

            Entry(EntityType<?> type) {
                this.type = type;
            }

            EntityType<?> type() {
                return type;
            }

            @Override
            public void extractContent(@NonNull GuiGraphicsExtractor g, int mx, int my, boolean hovered, float a) {
                if (getSelected() != this && hovered) {
                    g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xFF262626);
                }

                // An entity with no mesh bound cannot be previewed, so it reads dim.
                boolean bound = EntityPreviewModelMapping.getMesh(type) != null;
                int color = getSelected() == this ? 0xFFFFFFFF : bound ? 0xFFCCCCCC : 0xFF777777;

                g.text(font, EntityPreviewModelMapping.label(type), getX() + 12, getY() + (getHeight() - 8) / 2, color, false);
            }

            @Override
            public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClicked) {
                setSelected(this);

                if (doubleClicked) confirm();

                return true;
            }

            void confirm() {
                onConfirm.accept(BuiltInRegistries.ENTITY_TYPE.getKey(type));
                ModalManager.getInstance().closeModal();
            }
        }

        // ***************************************************************
        // [AnchoredWidget] implementations
        // ***************************************************************
        private Unit x1 = AnchoredWidget.pixel(0);
        private Unit x2 = AnchoredWidget.fraction(50);
        private Unit y1 = AnchoredWidget.pixel(TITLE_H + SEARCH_H);
        private Unit y2 = AnchoredWidget.fraction(0);
        private HorizontalAnchorType horizontalAnchorType = HorizontalAnchorType.LEFT_WIDTH;
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
