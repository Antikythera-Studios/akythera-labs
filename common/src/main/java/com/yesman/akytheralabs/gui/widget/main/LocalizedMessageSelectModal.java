package com.yesman.akytheralabs.gui.widget.main;

import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.ProjectLocalization;
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
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/// Picks a lang key of the open project ([ProjectLocalization]). Each row shows its key, then its text
/// in the game's selected language, see [ProjectLocalization#resolve].
public final class LocalizedMessageSelectModal extends OverlayingWindowModal {
    private static final int WIDTH = 300;
    private static final int HEIGHT = 240;
    private static final int TITLE_H = 20;
    private static final int SEARCH_H = 18;
    private static final int LIST_TOP = TITLE_H + SEARCH_H;
    private static final int SCROLLBAR_W = 5;
    private static final int ROW_H = 16;
    private static final int KEY_COLOR = 0xFF8FC7FF;
    private static final int TEXT_COLOR = 0xFF888888;

    private final Font font;
    private final ProjectLocalization localization;
    private final Consumer<String> onKeySelected;
    private final EditorEditBox<String> searchBox;
    private KeyList keyList;

    public LocalizedMessageSelectModal(Font font, Consumer<String> onKeySelected) {
        super(AnchoredWidget.pixel(WIDTH), AnchoredWidget.pixel(HEIGHT), Component.translatable(LangKeys.GUI_LOCALIZED_MESSAGE_SELECT_MODAL_TITLE));

        this.font = font;
        this.localization = AkytheraProject.getCurrentProject().getLocalization();
        this.onKeySelected = onKeySelected;

        this.searchBox = EditorEditBox.stringBox(
            font,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(TITLE_H), AnchoredWidget.pixel(SEARCH_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            () -> "",
            query -> keyList.rebuild(query),
            Component.translatable(LangKeys.GUI_MESSAGE_EMPTY_SEARCHBAR)
        );

        this.keyList = new KeyList();
    }

    @Override
    public EditorEditBox<String> searchBox() {
        return searchBox;
    }

    @Override
    public boolean confirmSoleResult() {
        if (keyList.children().size() != 1) return false;

        onKeySelected.accept(keyList.children().getFirst().key);

        return true;
    }

    @Override
    public @NonNull List<? extends GuiEventListener> children() {
        return List.of(searchBox, keyList);
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
        return mouseX >= getX() && mouseY >= getY() && mouseX < getRight() && mouseY < getY() + TITLE_H;
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        // Route to children, then consume to prevent click-through to the screen below
        super.mouseClicked(event, doubleClick);

        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return keyList.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractWidgetRenderState(graphics, mouseX, mouseY, a);

        graphics.fill(getX(), getY(), getRight(), getBottom(), 0xFF1E1E1E);
        graphics.outline(getX(), getY(), getWidth(), getHeight(), 0xFF3A3A3A);
        graphics.fill(getX(), getY(), getRight(), getY() + TITLE_H, 0xFF2A2A2A);

        graphics.text(
            font,
            getMessage(),
            getX() + (getWidth() - font.width(getMessage())) / 2,
            getY() + (TITLE_H - font.lineHeight) / 2,
            0xFFCCCCCC,
            false
        );

        searchBox.extractRenderState(graphics, mouseX, mouseY, a);
        keyList.extractRenderState(graphics, mouseX, mouseY, a);
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput n) {
    }

    @Override
    protected boolean closeOnPressBackground() {
        return true;
    }

    private final class KeyList extends AbstractSelectionList<KeyList.@NonNull KeyEntry> implements AnchoredWidget {
        /// The query the rows are listed for. Null until the first [#rebuild].
        private @Nullable String query;

        KeyList() {
            super(Minecraft.getInstance(), WIDTH, HEIGHT - LIST_TOP, LIST_TOP, ROW_H);

            rebuild("");
        }

        /// Shows the keys whose key or text contains `query`, ignoring case. The same query again keeps
        /// the rows and the scroll as they are.
        void rebuild(String query) {
            if (query.equals(this.query)) return;

            this.query = query;
            String lower = query.toLowerCase(Locale.ROOT);
            String language = Minecraft.getInstance().getLanguageManager().getSelected();
            int rowIndex = 0;

            clearEntries();

            for (String key : localization.keys()) {
                String text = localization.resolve(key, language);

                if (key.toLowerCase(Locale.ROOT).contains(lower) || text.toLowerCase(Locale.ROOT).contains(lower)) {
                    addEntry(new KeyEntry(key, text, rowIndex++));
                }
            }

            setScrollAmount(0);
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
        protected int scrollerHeight() {
            return Mth.clamp((int) ((float) (height * height) / (float) contentHeight()), 8, height - 8);
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

        /// One key: the key, then its text greyed.
        final class KeyEntry extends AbstractSelectionList.Entry<@NonNull KeyEntry> {
            private final String key;
            private final String text;
            private final int rowIndex;

            KeyEntry(String key, String text, int rowIndex) {
                this.key = key;
                this.text = text;
                this.rowIndex = rowIndex;
            }

            @Override
            public void extractContent(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float a) {
                int background = hovered ? 0xFF1E4D72 : rowIndex % 2 == 0 ? 0xFF000000 : 0xFF111111;
                int textY = getY() + (ROW_H - font.lineHeight) / 2;
                int keyWidth = Math.min(font.width(key), getWidth() / 2);
                int textX = getX() + 4 + keyWidth + 8;

                g.fill(getX() - 2, getY(), getX() + getWidth(), getY() + getHeight(), background);
                g.text(font, font.plainSubstrByWidth(key, keyWidth), getX() + 4, textY, KEY_COLOR, false);
                g.text(font, font.plainSubstrByWidth(text.replace('\n', ' '), Math.max(0, getX() + getWidth() - textX - 4)), textX, textY, TEXT_COLOR, false);
            }

            @Override
            public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
                onKeySelected.accept(key);

                return true;
            }
        }

        // ***************************************************************
        // [AnchoredWidget] implementations
        // ***************************************************************
        private Unit x1 = AnchoredWidget.pixel(0);
        private Unit x2 = AnchoredWidget.pixel(0);
        private Unit y1 = AnchoredWidget.pixel(LIST_TOP);
        private Unit y2 = AnchoredWidget.pixel(0);
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
