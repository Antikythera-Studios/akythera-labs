package com.yesman.akytheralabs.gui.widget;

import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractWidget;
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
import java.util.Collections;
import java.util.List;

/// A container that stacks clickable tab buttons along the top edge and shows
/// exactly one content child below them. Switching a tab swaps the visible body.
///
/// Nothing app-specific lives here: callers feed tabs via [#addTab]. Only the
/// active tab's content is exposed through [#children], so inactive tabs get no
/// focus, key events, or narration.
public final class TabPanel extends AbstractContainerWidget implements AnchoredWidget {
    public static final int HEADER_H = 20;
    private static final int TAB_PADDING_X = 10;
    private static final int TAB_GAP = 1;
    /// Width reserved after a closeable tab's title for its "✕" close button.
    private static final int CLOSE_BTN_W = 12;
    /// Pixels the tab strip slides per mouse-wheel notch when it overflows.
    private static final int HEADER_SCROLL_STEP = 24;
    private static final int COLOR_CLOSE = 0xFFAAAAAA;
    private static final int COLOR_CLOSE_HOVER = 0xFFFF6060;
    private static final int COLOR_BODY = 0xFF1E1E1E;
    private static final int COLOR_STRIP = 0xFF252525;
    private static final int COLOR_DIVIDER = 0xFF404040;
    private static final int COLOR_TAB_ACTIVE = 0xFF333333;
    private static final int COLOR_TAB_HOVER = 0xFF2A2A2A;
    private static final int COLOR_TEXT_ACTIVE = 0xFFFFFFFF;
    private static final int COLOR_TEXT_INACTIVE = 0xFFAAAAAA;

    private final Font font;
    private final List<Tab> tabs = new ArrayList<>();
    private int activeIndex = -1;
    /// Index of the tab header being drag-reordered; -1 when none. Set on a header press,
    /// cleared on release. Live-swaps with a neighbour when the cursor crosses its midpoint.
    private int draggingTabIndex = -1;
    /// How far the tab strip is slid left, in pixels. Always 0 when the strip fits.
    private int headerScrollX = 0;
    /// Largest valid [#headerScrollX] given the current strip and panel widths.
    private int maxHeaderScroll = 0;

    public TabPanel(
        Font font,
        Unit x1,
        Unit x2,
        Unit y1,
        Unit y2,
        AnchoredWidget.HorizontalAnchorType horizontalAnchorType,
        AnchoredWidget.VerticalAnchorType verticalAnchorType
    ) {
        super(0, 0, 0, 0, Component.empty(), AbstractScrollArea.defaultSettings(0)); // No scroll

        this.font = font;
        this.x1 = x1;
        this.x2 = x2;
        this.y1 = y1;
        this.y2 = y2;
        this.horizontalAnchorType = horizontalAnchorType;
        this.verticalAnchorType = verticalAnchorType;
    }

    /// Append a tab. The first tab added becomes active automatically.
    public TabPanel addTab(Component title, AbstractWidget content) {
        return addTab(title, content, false, null);
    }

    /// Append a closeable tab (one carrying a "✕" button). `onClose` runs just before the tab is
    /// removed (e.g. to flush its editor's data back to the holder), and may be null.
    public TabPanel addCloseableTab(Component title, AbstractWidget content, @Nullable Runnable onClose) {
        return addTab(title, content, true, onClose);
    }

    private TabPanel addTab(Component title, AbstractWidget content, boolean closeable, @Nullable Runnable onClose) {
        Tab tab = new Tab(title, content);
        tab.closeable = closeable;
        tab.onClose = onClose;
        tabs.add(tab);

        if (activeIndex < 0) {
            activeIndex = 0;
        }

        relocateHeaderRects();
        relocateBody();

        return this;
    }

    /// The index of the tab whose content is `content`, or -1 if none. Lets a host avoid opening a
    /// duplicate tab for the same editor (it activates the existing one instead).
    public int indexOfContent(AbstractWidget content) {
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).content == content) return i;
        }
        return -1;
    }

    /// Removes the tab at `index` (running its `onClose` first). Adjusts the active tab so one stays
    /// selected: a tab at or before the active index shifts it left, never below 0.
    public void removeTab(int index) {
        if (index < 0 || index >= tabs.size()) return;

        Tab removed = tabs.remove(index);
        if (removed.onClose != null) removed.onClose.run();

        if (tabs.isEmpty()) {
            activeIndex = -1;
        } else if (activeIndex >= index) {
            activeIndex = Math.max(0, activeIndex - 1);
        }

        relocateHeaderRects();
        relocateBody();
    }

    public void setActiveIndex(int idx) {
        if (idx < 0 || idx >= tabs.size() || idx == activeIndex) {
            return;
        }

        activeIndex = idx;
        relocateBody();
    }

    public int getActiveIndex() {
        return activeIndex;
    }

    public AbstractWidget getActiveContent() {
        return tabs.get(activeIndex).content;
    }

    @Override
    protected int contentHeight() {
        return getHeight();
    }

    @Override
    public @NonNull List<? extends GuiEventListener> children() {
        // Only the active tab's content is a live child. Inactive tabs are invisible
        // to focus traversal, key events, and narration without flipping any flag.
        return activeIndex < 0
            ? Collections.emptyList()
            : Collections.singletonList(tabs.get(activeIndex).content);
    }

    @Override
    public void relocate(ScreenRectangle screenRectangle) {
        AnchoredWidget.super.relocate(screenRectangle);
        relocateHeaderRects();
        relocateBody();
    }

    private void relocateHeaderRects() {
        // Total span of all tabs (gaps between, none trailing) decides how far the strip may slide.
        int total = 0;

        for (Tab t : tabs) {
            total += tabWidth(t) + TAB_GAP;
        }

        if (total > 0) total -= TAB_GAP;

        maxHeaderScroll = Math.max(0, total - getWidth());
        headerScrollX = Math.clamp(headerScrollX, 0, maxHeaderScroll);

        int x = getX() - headerScrollX;

        for (Tab t : tabs) {
            int w = tabWidth(t);
            t.headerLeft = x;
            t.headerRight = x + w;
            t.closeLeft = x + w - TAB_PADDING_X - CLOSE_BTN_W + 2;
            x += w + TAB_GAP;
        }
    }

    private int tabWidth(Tab t) {
        return font.width(t.title) + TAB_PADDING_X * 2 + (t.closeable ? CLOSE_BTN_W : 0);
    }

    /// The rectangle the active tab's content is laid out in — the panel minus the header strip. Lets
    /// a host re-relocate its tab content after mutating it (e.g. re-filling a table on selection)
    /// without recomputing the header offset — and without passing the content's own rect back in,
    /// which would re-apply its anchor insets and drift it every call.
    public ScreenRectangle getBodyRectangle() {
        return new ScreenRectangle(getX(), getY() + HEADER_H, getWidth(), Math.max(0, getHeight() - HEADER_H));
    }

    private void relocateBody() {
        if (activeIndex < 0) {
            return;
        }

        AbstractWidget content = tabs.get(activeIndex).content;
        ScreenRectangle body = getBodyRectangle();

        if (content instanceof AnchoredWidget aw) {
            aw.relocate(body);
        } else {
            // Non-anchored content: stretch to the body rect by raw setters.
            content.setX(body.left());
            content.setY(body.top());
            content.setWidth(body.width());
            content.setHeight(body.height());
        }
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        // Whole-panel background (also the body fill).
        graphics.fill(getX(), getY(), getRight(), getBottom(), COLOR_BODY);

        // Header strip background
        graphics.fill(getX(), getY(), getRight(), getY() + HEADER_H - 1, COLOR_STRIP);
        graphics.fill(getX(), getY() + HEADER_H - 1, getRight(), getY() + HEADER_H, COLOR_DIVIDER);

        int textY = getY() + (HEADER_H - 1 - font.lineHeight) / 2;

        // Tabs may slide past the panel edges when the strip overflows; clip them to the strip.
        graphics.enableScissor(getX(), getY(), getRight(), getY() + HEADER_H);

        for (int i = 0; i < tabs.size(); i++) {
            Tab t = tabs.get(i);
            boolean active = i == activeIndex;
            boolean hover = mouseX >= t.headerLeft && mouseX < t.headerRight && mouseY >= getY() && mouseY < getY() + HEADER_H;

            if (active) {
                graphics.fill(t.headerLeft, getY(), t.headerRight, getY() + HEADER_H, COLOR_TAB_ACTIVE);
            } else if (hover) {
                graphics.fill(t.headerLeft, getY(), t.headerRight, getY() + HEADER_H - 1, COLOR_TAB_HOVER);
            }

            graphics.text(
                font,
                t.title,
                t.headerLeft + TAB_PADDING_X,
                textY,
                active ? COLOR_TEXT_ACTIVE : COLOR_TEXT_INACTIVE,
                false
            );

            if (t.closeable) {
                boolean closeHover = mouseX >= t.closeLeft && mouseX < t.closeLeft + CLOSE_BTN_W
                    && mouseY >= getY() && mouseY < getY() + HEADER_H;
                graphics.text(font, Component.literal("✕"), t.closeLeft + 2, textY,
                    closeHover ? COLOR_CLOSE_HOVER : COLOR_CLOSE, false);
            }
        }

        graphics.disableScissor();

        // Active body content renders through the public render entry, like PanelArea.
        if (activeIndex >= 0) {
            tabs.get(activeIndex).content.extractRenderState(graphics, mouseX, mouseY, a);
        }
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();

        // Header strip → swap tabs (or close, when the "✕" of a closeable tab is hit).
        if (my >= getY() && my < getY() + HEADER_H) {
            for (int i = 0; i < tabs.size(); i++) {
                Tab t = tabs.get(i);

                if (mx >= t.headerLeft && mx < t.headerRight) {
                    if (t.closeable && mx >= t.closeLeft && mx < t.closeLeft + CLOSE_BTN_W) {
                        removeTab(i);
                    } else {
                        setActiveIndex(i);
                        draggingTabIndex = i;
                    }
                    return true;
                }
            }

            return false;
        }

        // Body → forward to active content.
        if (activeIndex >= 0) {
            AbstractWidget content = tabs.get(activeIndex).content;

            if (content.mouseClicked(event, doubleClick) && content.shouldTakeFocusAfterInteraction()) {
                setFocused(content);

                if (event.button() == 0) {
                    setDragging(true);
                }
            }

            return content.isMouseOver(mx, my);
        }

        return false;
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        draggingTabIndex = -1;
        setDragging(false);

        return activeIndex >= 0 && tabs.get(activeIndex).content.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
        if (draggingTabIndex >= 0) {
            reorderDraggedTab(event.x());
            return true;
        }

        return activeIndex >= 0 && tabs.get(activeIndex).content.mouseDragged(event, dx, dy);
    }

    /// Swaps the dragged tab with the neighbour whose header midpoint the cursor has crossed.
    /// The active tab follows the dragged one (tabs are only draggable after being activated by
    /// the initiating press).
    private void reorderDraggedTab(double mouseX) {
        int target = draggingTabIndex;

        if (target > 0) {
            Tab prev = tabs.get(target - 1);
            if (mouseX < (prev.headerLeft + prev.headerRight) / 2.0) target--;
        }

        if (target == draggingTabIndex && target < tabs.size() - 1) {
            Tab next = tabs.get(target + 1);
            if (mouseX > (next.headerLeft + next.headerRight) / 2.0) target++;
        }

        if (target == draggingTabIndex) return;

        Tab dragged = tabs.remove(draggingTabIndex);
        tabs.add(target, dragged);
        activeIndex = target;
        draggingTabIndex = target;
        relocateHeaderRects();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        // Over the header strip: slide the overflowing tab list left/right instead of forwarding.
        if (isMouseOver(mx, my) && my >= getY() && my < getY() + HEADER_H) {
            if (maxHeaderScroll <= 0) return false;

            // Either wheel axis slides the strip; scrolling down/right reveals later tabs.
            double notches = scrollY != 0 ? scrollY : scrollX;
            int prev = headerScrollX;
            headerScrollX = Math.clamp(headerScrollX - (int) (notches * HEADER_SCROLL_STEP), 0, maxHeaderScroll);

            if (headerScrollX == prev) return false;

            relocateHeaderRects();
            return true;
        }

        return activeIndex >= 0
            && my >= getY() + HEADER_H
            && isMouseOver(mx, my)
            && tabs.get(activeIndex).content.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        return activeIndex >= 0 && tabs.get(activeIndex).content.keyPressed(event);
    }

    @Override
    public boolean keyReleased(@NonNull KeyEvent event) {
        return activeIndex >= 0 && tabs.get(activeIndex).content.keyReleased(event);
    }

    @Override
    public boolean charTyped(@NonNull CharacterEvent event) {
        return activeIndex >= 0 && tabs.get(activeIndex).content.charTyped(event);
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

    /// A single tab: its header title, its body content, and the cached pixel span of
    /// its clickable header rect (recomputed on every relocate). Mutable header span is
    /// why this is an inner class rather than a Pair.
    private static final class Tab {
        final Component title;
        final AbstractWidget content;
        boolean closeable;
        @Nullable Runnable onClose;
        int headerLeft;
        int headerRight;
        /// Left edge of the "✕" close button (only meaningful when [#closeable]).
        int closeLeft;

        Tab(Component title, AbstractWidget content) {
            this.title = title;
            this.content = content;
        }
    }

    /// ***************************************************************
    /// [AnchoredWidget] implementations                              *
    /// ***************************************************************
    private Unit x1;
    private Unit x2;
    private Unit y1;
    private Unit y2;
    private final AnchoredWidget.HorizontalAnchorType horizontalAnchorType;
    private final AnchoredWidget.VerticalAnchorType verticalAnchorType;

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