package com.yesman.akytheralabs.gui.widget;

import com.yesman.akythera.client.gui.widget.AnchoredButton;
import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/// A modal popup box for warnings or simple user input.
///
/// Always centered on screen with a full-screen dim overlay. Use [Builder] to assemble a title, body lines,
/// child widgets, and [AnchoredButton]s.
///
/// Layout follows the [AnchoredWidget] design: each row's anchor units are written **only** during
/// construction or [Builder#build]. [#relocate] is a pure cascade — it never writes anchor units.
///
/// Visible decorations split across rows:
///   - [TitleRow] paints the title bar fill, bottom separator, and title text.
///   - [FooterRow] paints the top separator and the post-action buttons.
///   - [WidgetContainerModal] only paints the outer border and inner fill.
///
/// The hosting screen is responsible for:
///   - Rendering the popup last (on top of everything else).
///   - Passing (-1, -1) as mouse coordinates to background widgets while the popup is
///     active, to suppress hover effects and prevent click-through.
///
/// The popup always returns true from [#mouseClicked] to consume all clicks.
public final class WidgetContainerModal extends OverlayingWindowModal {
    static final int CONTENT_PADDING = 8;
    static final int BUTTON_H = 16;
    static final int BUTTON_GAP = 6;
    private static final int SCROLLBAR_W = 5;
    private static final int SCROLL_RATE = 10;

    /// Modal can never exceed (screen height - this) so it always leaves a gutter
    private static final int VIEWPORT_GUTTER = 40;

    private final Font font;
    private final List<AnchoredRow> rows;
    private final int contentHeight;

    /// When `true`, the layout manager treats this popup like a sub-screen:
    /// brackets its edits with a `ScreenSavePoint` on open, pops them on close.
    /// Set via [Builder#saveHistory].
    private final boolean saveHistory;

    private WidgetContainerModal(Font font, Unit x2, Unit y2, Component title, List<AnchoredRow> rows, int contentHeight, boolean saveHistory) {
        super(x2, y2, title);

        this.font = font;
        this.rows = rows;
        this.contentHeight = contentHeight;
        this.saveHistory = saveHistory;
    }

    public boolean saveHistory() {
        return saveHistory;
    }

    @Override
    public void relocate(ScreenRectangle screenRectangle) {
        super.relocate(screenRectangle);

        // Re-clamp the existing scroll amount to the new max
        refreshScrollAmount();
    }

    @Override
    protected int contentHeight() {
        return contentHeight;
    }

    @Override
    protected double scrollRate() {
        return SCROLL_RATE;
    }

    /// Scroll only affects the body rows (between Title and Footer)
    private int bodyTop() {
        for (AnchoredRow row : rows) {
            if (row instanceof TitleRow) return row.getBottom();
        }
        return getY();
    }

    private int bodyBottom() {
        for (int i = rows.size() - 1; i >= 0; i--) {
            if (rows.get(i) instanceof FooterRow) return rows.get(i).getY();
        }
        return getBottom();
    }

    @Override
    protected boolean scrollable() {
        return maxScrollAmount() > 0;
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractWidgetRenderState(graphics, mouseX, mouseY, a);

        int boxX = getX();
        int boxY = getY();
        int boxWidth = getWidth();
        int boxHeight = getHeight();

        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xFF555555);
        graphics.fill(boxX + 1, boxY + 1, boxX + boxWidth - 1, boxY + boxHeight - 1, 0xFF040404);

        int scroll = (int) scrollAmount();
        int bodyTop = bodyTop();
        int bodyBottom = bodyBottom();

        // Shift body rows by -scroll (including their child widgets) so both
        // rendering and click hit-testing land on the visible position. The
        // offset is relative to the modal's current top so the math survives
        // header-drag movement (no relocate required).
        for (AnchoredRow row : rows) {
            if (row instanceof TitleRow || row instanceof FooterRow) continue;

            int targetY = getY() + row.naturalYOffset - scroll;
            int dy = targetY - row.getY();

            if (dy != 0) {
                shiftRow(row, dy);
            }
        }

        // Title and footer first stays unclipped
        graphics.enableScissor(boxX, bodyTop, boxX + boxWidth, bodyBottom);

        for (AnchoredRow row : rows) {
            if (row instanceof TitleRow || row instanceof FooterRow) continue;
            row.extractRow(this, graphics, mouseX, mouseY, a);
        }

        graphics.disableScissor();

        for (AnchoredRow row : rows) {
            if (row instanceof TitleRow || row instanceof FooterRow) {
                row.extractRow(this, graphics, mouseX, mouseY, a);
            }
        }

        if (scrollable()) {
            extractBodyScrollbar(graphics, bodyTop, bodyBottom);
        }
    }

    private static void shiftRow(AnchoredRow row, int dy) {
        row.setY(row.getY() + dy);

        for (GuiEventListener child : row.children()) {
            shiftWidgetRecursively(child, dy);
        }
    }

    private static void shiftWidgetRecursively(GuiEventListener e, int dy) {
        if (e instanceof AbstractWidget aw) {
            if (aw instanceof AbstractSelectionList<?> selectionList) {
                selectionList.updateSizeAndPosition(aw.getWidth(), aw.getHeight(), aw.getX(), aw.getY() + dy);
            } else {
                aw.setY(aw.getY() + dy);

                if (aw instanceof AbstractContainerWidget acw) {
                    for (GuiEventListener c : acw.children()) {
                        shiftWidgetRecursively(c, dy);
                    }
                }
            }
        }
    }

    private void extractBodyScrollbar(GuiGraphicsExtractor g, int bodyTop, int bodyBottom) {
        int barX = getX() + getWidth() - SCROLLBAR_W - 1;
        int trackH = bodyBottom - bodyTop;
        if (trackH <= 0) return;

        int barH = Mth.clamp((int) ((float) trackH * trackH / contentHeight), 16, Math.max(16, trackH - 4));
        int maxScroll = maxScrollAmount();
        int barY = maxScroll == 0 ? bodyTop : bodyTop + (int) (scrollAmount() * (trackH - barH) / maxScroll);

        g.fill(barX, bodyTop, barX + SCROLLBAR_W, bodyBottom, 0xFF1A1A1A);
        g.fill(barX + 1, barY, barX + SCROLLBAR_W - 1, barY + barH, 0xFF505050);
    }

    @Override
    public boolean isMouseOverHeader(double mouseX, double mouseY) {
        return !rows.isEmpty() && rows.getFirst() instanceof TitleRow titleRow && titleRow.isMouseOver(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        // Route to children, then consume to prevent click-through to the screen below
        super.mouseClicked(event, doubleClick);

        return true;
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (event.isEscape()) {
            if (AkytheraEditorLayoutManager.getInstance().isActive()) {
                AkytheraEditorLayoutManager.getInstance().closeModal();
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
    public void setFocused(final @Nullable GuiEventListener focused) {
        // Re-focusing the already-focused child clears the focus in vanilla's implementation.
        if (getFocused() == focused) {
            return;
        }

        super.setFocused(focused);
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput narrationElementOutput) {
    }

    @Override
    public @NonNull List<? extends GuiEventListener> children() {
        return rows;
    }

    public static Builder builder(Font font, Component title) {
        return new Builder(font, title, Builder.DEFAULT_WIDTH);
    }

    public static Builder builder(Font font, Component title, int width) {
        return new Builder(font, title, width);
    }

    /// Computes a per-button width that evenly distributes [n] buttons across a popup of width [popupWidth],
    /// honoring [#CONTENT_PADDING] gutters and [#BUTTON_GAP] spacing.
    public static int evenButtonWidth(int popupWidth, int n) {
        return (popupWidth - 2 * CONTENT_PADDING - (n + 1) * BUTTON_GAP) / n;
    }

    public static final class Builder {
        private static final int TEXT_PADDING = 2;
        private static final int DEFAULT_WIDTH = 280;

        private final Font font;
        private final Component title;
        private final List<AnchoredRow> items = new ArrayList<>();
        private final List<AnchoredButton> buttons = new ArrayList<>();

        /// Running cursor for the next button's `x1`. Starts at the row's left gutter ([#BUTTON_GAP])
        /// and advances by `width + BUTTON_GAP` per [#footerButton] call.
        private int footerButtonX = BUTTON_GAP;

        private final int boxWidth;
        private int lastHeight;
        private boolean saveHistory = false;

        private Builder(Font font, Component title, int width) {
            this.font = font;
            this.title = title;
            this.boxWidth = width;

            lastHeight = title.getContents() != PlainTextContents.EMPTY
                ? Math.max(TitleRow.TITLE_BAR_H, font.lineHeight * font.split(title, width).size() + TEXT_PADDING * 2)
                : 0;
        }

        public Builder space(int height) {
            lastHeight += height;
            return this;
        }

        public Builder textLines(Component text) {
            TextLines textLines = new TextLines(text);
            textLines.naturalYOffset = lastHeight;
            textLines.setY1(AnchoredWidget.pixel(lastHeight));
            int textHeight = textLines.buildHeight(font, boxWidth - CONTENT_PADDING * 2);
            int rowHeight = textHeight + CONTENT_PADDING * 2;
            textLines.setY2(AnchoredWidget.pixel(rowHeight));
            items.add(textLines);
            lastHeight += rowHeight;

            return this;
        }

        public <T extends AbstractWidget & AnchoredWidget> Builder singleWidgetRow(int height, T widget) {
            WidgetsContainerRow widgetsContainerRow = new WidgetsContainerRow();
            widgetsContainerRow.naturalYOffset = lastHeight;
            widgetsContainerRow.setY1(AnchoredWidget.pixel(lastHeight));
            widgetsContainerRow.setY2(AnchoredWidget.pixel(height));
            widgetsContainerRow.addWidget(widget);
            items.add(widgetsContainerRow);
            lastHeight += height;

            return this;
        }

        public Builder widgetContainerRow(int height, WidgetsContainerRow widgetsContainerRow) {
            widgetsContainerRow.setVerticalAnchorType(VerticalAnchorType.TOP_HEIGHT);
            widgetsContainerRow.naturalYOffset = lastHeight;
            widgetsContainerRow.setY1(AnchoredWidget.pixel(lastHeight));
            widgetsContainerRow.setY2(AnchoredWidget.pixel(height));
            items.add(widgetsContainerRow);
            lastHeight += height;

            return this;
        }

        /// Mark this popup as a sub-screen for the history stack: edits made
        /// inside it are bracketed by a `ScreenSavePoint` and discarded on
        /// close. Use for interactive multi-field config modals; do NOT use
        /// for pickers / context menus / confirmations.
        public Builder saveHistory() {
            this.saveHistory = true;
            return this;
        }

        public Builder footerButton(AnchoredButton button, int width) {
            button.setHorizontalAnchorType(HorizontalAnchorType.RIGHT_WIDTH);
            button.setVerticalAnchorType(VerticalAnchorType.CENTER_HEIGHT);
            button.setX1(AnchoredWidget.pixel(footerButtonX));
            button.setX2(AnchoredWidget.pixel(width));
            button.setY1(AnchoredWidget.pixel(0));
            button.setY2(AnchoredWidget.pixel(BUTTON_H));

            buttons.add(button);
            footerButtonX += width + BUTTON_GAP;

            return this;
        }

        public WidgetContainerModal build() {
            List<AnchoredRow> finalItems = new ArrayList<>(items.size() + 2);

            // Title row
            if (title.getContents() != PlainTextContents.EMPTY) {
                finalItems.add(new TitleRow(font, title));
            }

            // Contents
            finalItems.addAll(items);

            // Footer buttons row
            if (!buttons.isEmpty()) {
                finalItems.add(new FooterRow(new ArrayList<>(buttons)));
                lastHeight += FooterRow.FOOTER_ROW_H;
            }

            // Cap the modal box height so the body scrolls instead of overflowing the screen
            int screenH = AkytheraEditorLayoutManager.getInstance().isActive()
                ? AkytheraEditorLayoutManager.getInstance().getRecentEditorScreen().height
                : Minecraft.getInstance().gui.screen().height;

            int boxHeight = Math.min(lastHeight, screenH - VIEWPORT_GUTTER);

            return new WidgetContainerModal(
                font,
                AnchoredWidget.pixel(boxWidth),
                AnchoredWidget.pixel(boxHeight),
                title,
                finalItems,
                lastHeight,
                saveHistory
            );
        }
    }

    public static abstract class AnchoredRow extends AbstractContainerWidget implements AnchoredWidget {
        /// Offset from the modal top at natural placement (no scroll). Set once by
        /// [Builder#build]. The modal shifts body rows by `(modalY + naturalYOffset - scrollAmount) - getY()`
        /// each render so they track the scroll while playing nicely with modal drag.
        int naturalYOffset;

        protected AnchoredRow() {
            super(0, 0, 0, 0, Component.empty(), AbstractScrollArea.defaultSettings(0));
        }

        @Override
        protected final void extractWidgetRenderState(@NonNull GuiGraphicsExtractor guiGraphicsExtractor, int mouseX, int mouseY, float a) {
            // DO nothing, rows are drawn by `extractRow`
        }

        protected final void extractRow(WidgetContainerModal popup, GuiGraphicsExtractor g, int mx, int my, float a) {
            extractRowContent(popup, g, getContentRectangle(), mx, my, a);
            for (AbstractWidget w : children()) w.extractRenderState(g, mx, my, a);
        }

        /// Extract non-widget components
        protected void extractRowContent(WidgetContainerModal popup, GuiGraphicsExtractor g, ScreenRectangle rowRectangle, int mx, int my, float a) {}

        /// Apply padding for sub widgets
        @Override
        public ScreenRectangle getContentRectangle() {
            ScreenRectangle rowRectangle = getRectangle();

            return new ScreenRectangle(
                rowRectangle.left() + CONTENT_PADDING,
                rowRectangle.top() + CONTENT_PADDING,
                rowRectangle.width() - 2 * CONTENT_PADDING,
                rowRectangle.height() - 2 * CONTENT_PADDING
            );
        }

        @Override
        protected int contentHeight() {
            return getHeight();
        }

        @Override
        public abstract @NonNull List<? extends AbstractWidget> children();

        @Override
        protected void updateWidgetNarration(@NonNull NarrationElementOutput n) {
        }

        /// ***************************************************************
        /// [AnchoredWidget] implementations                              *
        /// ***************************************************************
        private Unit x1 = AnchoredWidget.pixel(0);
        private Unit x2 = AnchoredWidget.pixel(0);
        private Unit y1 = AnchoredWidget.pixel(0);
        private Unit y2 = AnchoredWidget.pixel(0);
        private HorizontalAnchorType horizontalAnchorType = HorizontalAnchorType.LEFT_RIGHT;
        private VerticalAnchorType verticalAnchorType = VerticalAnchorType.TOP_HEIGHT;

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
        public void setHorizontalAnchorType(HorizontalAnchorType horizontalAnchorType) { this.horizontalAnchorType = horizontalAnchorType; }
        public void setVerticalAnchorType(VerticalAnchorType verticalAnchorType) { this.verticalAnchorType = verticalAnchorType; }
    }

    /// Override [AnchoredRow#extractRowContent] from subclasses to draw non-widget components
    public static class WidgetsContainerRow extends AnchoredRow {
        private final List<AbstractWidget> widgets = new ArrayList<>();

        // modifiable
        @Override
        public final @NonNull List<? extends AbstractWidget> children() {
            return widgets;
        }

        public <T extends AbstractWidget & AnchoredWidget> void addWidget(T widget) {
            widgets.add(widget);
        }
    }

    private static final class TextLines extends AnchoredRow {
        private final Component text;

        public TextLines(Component text) {
            this.text = text;
        }

        public int buildHeight(Font font, int width) {
            List<FormattedCharSequence> lines = font.split(text, Math.max(width, 1));
            return Math.max(lines.size(), 1) * font.lineHeight;
        }

        @Override
        public @NonNull List<? extends AbstractWidget> children() {
            return List.of();
        }

        @Override
        protected void extractRowContent(WidgetContainerModal popup, GuiGraphicsExtractor g, ScreenRectangle rowRectangle, int mouseX, int mouseY, float a) {
            List<FormattedCharSequence> lines = popup.font.split(text, rowRectangle.width());
            int y = rowRectangle.top();

            for (FormattedCharSequence cs : lines) {
                g.text(popup.font, cs, rowRectangle.left(), y, 0xFFCCCCCC);
                y += popup.font.lineHeight;
            }
        }
    }

    public static final class TitleRow extends AnchoredRow {
        private static final int TITLE_BAR_H = 20;

        private final Font font;
        private final Component title;

        TitleRow(Font font, Component title) {
            this.font = font;
            this.title = title;

            setX1(AnchoredWidget.pixel(0));
            setX2(AnchoredWidget.pixel(0));
            setY1(AnchoredWidget.pixel(0));
            setY2(AnchoredWidget.pixel(TITLE_BAR_H));
        }

        @Override
        protected void extractRowContent(WidgetContainerModal popup, GuiGraphicsExtractor g, ScreenRectangle rowRectangle, int mx, int my, float a) {
            // Title bar fill
            g.fill(getX() + 1, getY() + 1, getRight() - 1, getBottom(), 0xFF0A0A0A);
            // Bottom separator
            g.fill(getX() + 1, getBottom(), getRight() - 1, getBottom() + 1, 0xFF555555);

            List<FormattedCharSequence> lines = font.split(title, Math.max(rowRectangle.width(), 1));
            int y = getY() + (TITLE_BAR_H - lines.size() * font.lineHeight) / 2;

            for (FormattedCharSequence line : lines) {
                g.text(font, line, rowRectangle.left(), y, 0xFFEEEEEE, false);
                y += font.lineHeight;
            }
        }

        @Override
        public @NonNull List<? extends AbstractWidget> children() {
            return List.of();
        }
    }

    public static final class FooterRow extends AnchoredRow {
        private static final int FOOTER_ROW_H = 28;

        private final List<AnchoredButton> buttons;

        FooterRow(List<AnchoredButton> buttons) {
            this.buttons = buttons;

            setHorizontalAnchorType(HorizontalAnchorType.LEFT_RIGHT);
            setVerticalAnchorType(VerticalAnchorType.BOTTOM_HEIGHT);
            setX1(AnchoredWidget.pixel(0));
            setX2(AnchoredWidget.pixel(0));
            setY1(AnchoredWidget.pixel(0));
            setY2(AnchoredWidget.pixel(FOOTER_ROW_H));
        }

        @Override
        protected void extractRowContent(WidgetContainerModal popup, GuiGraphicsExtractor g, ScreenRectangle rowRectangle, int mx, int my, float a) {
            g.fill(getX() + 1, getY(), getRight() - 1, getY() + 1, 0xFF555555);
        }

        @Override
        public @NonNull List<? extends AbstractWidget> children() {
            return buttons;
        }
    }
}