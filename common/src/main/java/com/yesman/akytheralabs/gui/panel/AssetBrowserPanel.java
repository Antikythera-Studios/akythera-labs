package com.yesman.akytheralabs.gui.panel;

import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/// "Asset Browser" tab page: a scrolling list of draggable assets. Pressing a row begins a drag whose
/// drop is handled by the owning screen.
///
/// Generic over the drag payload `T`. The panel itself knows nothing about where the rows come from or
/// what they are — the owner passes a `source` supplier of [Asset]s (label + payload) and an
/// `onBeginDrag` callback. The montage editor feeds it [com.yesman.akythera.api.asset.AssetAccessor]s
/// of clips; the blendspace editor feeds it sequence holders.
public final class AssetBrowserPanel<T> extends AbstractSelectionList<AssetBrowserPanel<T>.@NonNull Entry>
    implements AnchoredWidget {
    private static final int ROW_H = 18;
    private static final int SCROLLBAR_W = 5;

    private final Font font;

    /// Supplies the rows to show. Re-queried on every [#rebuild].
    private final Supplier<List<T>> source;

    /// Called when a row press starts a drag — the screen takes over from here.
    private final Consumer<T> onBeginDrag;

    /// Asset object -> String mapper
    private final Function<T, String> displayFunction;

    public AssetBrowserPanel(
        Font font,
        Supplier<List<T>> source,
        Function<T, String> displayFunction,
        Consumer<T> onBeginDrag,
        Unit x1, Unit x2, Unit y1, Unit y2,
        HorizontalAnchorType horizontalAnchorType,
        VerticalAnchorType verticalAnchorType
    ) {
        super(Minecraft.getInstance(), 0, 0, 0, ROW_H);

        this.font = font;
        this.source = source;
        this.displayFunction = displayFunction;
        this.onBeginDrag = onBeginDrag;
        this.x1 = x1;
        this.x2 = x2;
        this.y1 = y1;
        this.y2 = y2;
        this.horizontalAnchorType = horizontalAnchorType;
        this.verticalAnchorType = verticalAnchorType;

        rebuild();
    }

    public void rebuild() {
        clearEntries();

        for (T asset : source.get()) {
            addEntry(new Entry(asset));
        }

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
    protected void extractListBackground(@NonNull GuiGraphicsExtractor g) {
        g.fill(getX(), getY(), getRight(), getBottom(), 0xFF1E1E1E);
    }

    @Override
    protected void extractListSeparators(@NonNull GuiGraphicsExtractor g) {
    }

    @Override
    protected boolean entriesCanBeSelected() {
        return false;
    }

    @Override
    protected void extractScrollbar(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (!scrollable()) return;
        int sbX = scrollBarX();
        int sh = scrollerHeight();
        int sy = scrollBarY();
        boolean hov = mouseX >= sbX && mouseX < sbX + SCROLLBAR_W && mouseY >= sy && mouseY < sy + sh;
        g.fill(sbX, getY(), sbX + SCROLLBAR_W, getBottom() - 1, 0xFF1A1A1A);
        g.fill(sbX + 1, sy, sbX + SCROLLBAR_W - 1, sy + sh, hov ? 0xFF6A6A6A : 0xFF505050);
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput n) {
    }

    @Override
    public void relocate(ScreenRectangle screenRectangle) {
        AnchoredWidget.super.relocate(screenRectangle);
        updateSizeAndPosition(getWidth(), getHeight(), getX(), getY());
    }

    public final class Entry extends AbstractSelectionList.Entry<AssetBrowserPanel<T>.@NonNull Entry> {
        private final T asset;

        Entry(T asset) {
            this.asset = asset;
        }

        @Override
        public void extractContent(@NonNull GuiGraphicsExtractor g, int mx, int my, boolean hovered, float a) {
            if (hovered) g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xFF262626);
            g.text(font, Component.literal(displayFunction.apply(asset)), getX() + 6, getY() + (getHeight() - font.lineHeight) / 2, 0xFFCCCCCC, false);
        }

        @Override
        public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
            if (event.button() != 0) return false;
            onBeginDrag.accept(asset);
            return true;
        }
    }

    /// ***************************************************************
    /// [AnchoredWidget] implementations                              *
    /// ***************************************************************
    private Unit x1;
    private Unit x2;
    private Unit y1;
    private Unit y2;
    private final HorizontalAnchorType horizontalAnchorType;
    private final VerticalAnchorType verticalAnchorType;

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