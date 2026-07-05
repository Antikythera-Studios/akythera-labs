package com.yesman.akytheralabs.gui.widget;

import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akythera.client.gui.widget.common.PressableWidget;
import com.yesman.akythera.client.gui.widget.common.ThemeApplicableWidget;
import com.yesman.akythera.client.gui.widget.common.WarningMarkableWidget;
import com.yesman.akythera.client.gui.widget.common.WidgetTheme;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

/// Modal-driven combo box used inside the Akythera Labs editor.
public final class AdvancedComboBox<T> extends AbstractWidget
    implements AnchoredWidget, WarningMarkableWidget<T>, PressableWidget, ThemeApplicableWidget<ComboBoxTheme> {

    private static final int ENTRY_H = 14;

    private final Font font;
    private final int maxRows;
    private final List<T> items;
    private final Function<T, String> displayStringMapper;

    /// Optional ARGB color for the type-indicator swatch a [ComboBoxTheme] may draw in front of an
    /// item's label (e.g. the Variables type combo paints each [com.yesman.akythera.api.data.modularize.ScriptType]
    /// in its node-pin color). Null — the default — means no indicator.
    @Nullable
    private Function<T, Integer> indicatorColorMapper;

    private ComboBoxTheme theme = AkytheraLabsComboBoxTheme.DEFAULT_COMBO_BOX;

    @Nullable
    private T selected;
    private boolean marked;

    public AdvancedComboBox(
        Font font,
        Unit x1,
        Unit x2,
        Unit y1,
        Unit y2,
        HorizontalAnchorType horizontalAnchor,
        VerticalAnchorType verticalAnchor,
        Callable<T> dataProvider,
        Consumer<T> onWidgetChanged,
        int maxRows,
        Component title,
        Collection<T> items,
        Function<T, String> displayStringMapper
    ) {
        super(0, 0, 0, 0, title);

        this.font = font;
        this.x1 = x1;
        this.x2 = x2;
        this.y1 = y1;
        this.y2 = y2;
        this.horizontalAnchorType = horizontalAnchor;
        this.verticalAnchorType = verticalAnchor;
        this.dataProvider = dataProvider;
        this.onWidgetChanged = onWidgetChanged;
        this.maxRows = Math.min(maxRows, items.size());
        this.items = List.copyOf(items);
        this.displayStringMapper = displayStringMapper;
    }

    @Override
    public void mark() {
        this.marked = true;
    }

    @Override
    public void unmark() {
        this.marked = false;
    }

    @Override
    public void extractWidgetRenderState(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        theme.renderBackground(this, g, mouseX, mouseY, a);

        int fontColor = isActive() ? 0xFFFFFFFF : 0xFF404040;
        theme.renderText(
            g, font, getX(), getY(), getWidth(), getHeight(),
            displayString(), indicatorColor(), fontColor, true
        );
    }

    /// The label text for the current selection (empty when nothing is selected).
    public String displayString() {
        return selected == null ? "" : displayStringMapper.apply(selected);
    }

    /// The type-indicator color for the current selection, or null when no mapper is set
    /// or nothing is selected. Resolved through [#indicatorColorFor].
    @Nullable
    public Integer indicatorColor() {
        return indicatorColorFor(selected);
    }

    /// The indicator color for an arbitrary `value` (used by the dropdown rows), or null when
    /// no mapper is set or `value` is null.
    @Nullable
    Integer indicatorColorFor(@Nullable T value) {
        return value == null || indicatorColorMapper == null ? null : indicatorColorMapper.apply(value);
    }

    /// Sets the per-item indicator color source; a [ComboBoxTheme] that draws a swatch (e.g.
    /// [AkytheraLabsComboBoxTheme#UE_VARIABLE_COMBO_BOX]) reads it. Returns `this` for chaining.
    public AdvancedComboBox<T> setIndicatorColorMapper(@Nullable Function<T, Integer> mapper) {
        this.indicatorColorMapper = mapper;
        return this;
    }

    public boolean isMarked() {
        return marked;
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (!active || !visible) return false;
        if (!isOverBody(event.x(), event.y())) return false;
        if (!isValidClickButton(event.buttonInfo())) return false;

        onPressed();
        playDownSound(Minecraft.getInstance().getSoundManager());

        return true;
    }

    @Override
    public void onPressed() {
        AkytheraEditorLayoutManager akytheraLayoutManager = AkytheraEditorLayoutManager.getInstance();

        if (akytheraLayoutManager.getModal() instanceof DropdownModal<?> dm && dm.owner == this) {
            akytheraLayoutManager.closeModal();
            return;
        }

        DropdownModal<T> modal = new DropdownModal<>(this);
        ScreenRectangle windowRect = akytheraLayoutManager.getRecentEditorScreen().getRectangle();
        int x = getX();
        int y = chooseDropdownY(modal.getHeight(), windowRect);

        modal.updateSizeAndPosition(getWidth(), modal.getHeight(), x, y);
        akytheraLayoutManager.openModalWidget(modal, x, y);
    }

    /// Place the dropdown below the combo if there is room in the active
    /// screen, otherwise above.
    private int chooseDropdownY(int dropdownHeight, ScreenRectangle windowRect) {
        int below = getY() + getHeight();
        int spaceBelow = windowRect.bottom() - below;
        int spaceAbove = getY() - windowRect.top();

        return (spaceBelow < dropdownHeight && spaceAbove >= dropdownHeight)
            ? getY() - dropdownHeight - 1
            : below;
    }

    private boolean isOverBody(double x, double y) {
        return active && visible
            && x >= getX() && y >= getY()
            && x < getX() + getWidth() && y < getY() + getHeight();
    }

    public void setValue(@Nullable T value) {
        this.selected = value;
        this.onWidgetChanged.accept(value);
    }

    /// Updates the displayed selection without firing the change callback.
    public void setValueQuiet(@Nullable T value) {
        this.selected = value;
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput n) {
    }

    /// *****************************************************************
    /// [DataBoundWidget] implementations                               *
    /// *****************************************************************
    private final Callable<T> dataProvider;
    private final Consumer<T> onWidgetChanged;

    @Override
    public Callable<T> getDataProvider() {
        return this.dataProvider;
    }

    @Override
    public Consumer<T> valueChangeCallback() {
        return this.onWidgetChanged;
    }

    @Override
    public Consumer<T> valueSetter() {
        return this::setValue;
    }

    @Override
    public void reset() {
        this.selected = null;
    }

    /// ***************************************************************
    /// [ThemeApplicableWidget] implementations                       *
    /// ***************************************************************
    @Override
    public boolean isSupportedTheme(WidgetTheme theme) {
        return ComboBoxTheme.class.isAssignableFrom(theme.getClass());
    }

    @Override
    public @NonNull ComboBoxTheme getTheme() {
        return theme;
    }

    @Override
    public void setTheme(ComboBoxTheme theme) {
        this.theme = theme;
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

    @Override
    public void relocate(ScreenRectangle screenRectangle) {
        AnchoredWidget.super.relocate(screenRectangle);

        if (AkytheraEditorLayoutManager.getInstance().getModal() instanceof DropdownModal<?> dm && dm.owner == this) {
            ScreenRectangle windowRect = AkytheraEditorLayoutManager.getInstance().getRecentEditorScreen().getRectangle();
            int x = getX();
            int y = chooseDropdownY(dm.getHeight(), windowRect);
            dm.updateSizeAndPosition(getWidth(), dm.getHeight(), x, y);
        }
    }

    private static final class DropdownModal<T> extends AbstractSelectionList<DropdownModal<T>.@NonNull Row>
        implements Modal
    {
        final AdvancedComboBox<T> owner;
        private final Map<T, Row> rowByValue = new HashMap<>();

        DropdownModal(AdvancedComboBox<T> owner) {
            super(Minecraft.getInstance(), owner.getWidth(), ENTRY_H * (owner.maxRows + 1), 0, ENTRY_H);

            this.owner = owner;

            // Sentinel "(none)" row at the top — selecting it clears the binding.
            addEntry(new Row(null));

            for (T item : owner.items) {
                Row row = new Row(item);
                rowByValue.put(item, row);
                addEntry(row);
            }

            // Pre-select current value so keyboard nav has a starting row.
            if (owner.selected != null) setSelected(rowByValue.get(owner.selected));
        }

        /// Cover the whole screen so this widget intercepts every click —
        /// outside-clicks reach `mouseClicked` below and close the modal.
        @Override
        public boolean isMouseOver(double mx, double my) {
            return isActive();
        }

        @Override
        public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
            boolean inside = event.x() >= getX() && event.x() < getRight() && event.y() >= getY() && event.y() < getBottom();

            if (!inside) {
                AkytheraEditorLayoutManager.getInstance().closeModal();
                return true;
            }

            return super.mouseClicked(event, doubleClick);
        }

        @Override
        public boolean keyPressed(@NonNull KeyEvent event) {
            if (event.isEscape()) {
                AkytheraEditorLayoutManager.getInstance().closeModal();
                return true;
            }

            return super.keyPressed(event);
        }

        @Override public int getRowWidth() { return getWidth() - 2; }
        @Override public int getRowLeft() { return getX() + 1; }
        @Override public int scrollbarWidth() { return 5; }
        @Override protected int scrollBarX() { return getRight() - scrollbarWidth() - 1; }
        @Override protected boolean entriesCanBeSelected() { return false; }
        @Override protected void updateWidgetNarration(@NonNull NarrationElementOutput n) {}

        @Override
        protected int contentHeight() {
            int totalHeight = 0;

            for (Row child : children()) {
                totalHeight += child.getHeight();
            }

            return totalHeight;
        }

        /// Frame: 1-px white outline + black body. Matches the inline
        /// dropdown look from the engine-side `ComboBox`.
        @Override
        protected void extractListBackground(@NonNull GuiGraphicsExtractor g) {
            g.fill(getX(), getY(), getRight(), getBottom(), 0xFF000000);
        }

        /// Background outline, but drawn in [#extractListSeparators] to draw last
        @Override
        protected void extractListSeparators(@NonNull GuiGraphicsExtractor g) {
            g.outline(getX(), getY(), getWidth(), getHeight(), 0xFFFFFFFF);
        }

        @Override
        protected void extractScrollbar(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY) {
            if (!scrollable()) return;

            int sbX = scrollBarX();
            int sh = scrollerHeight();
            int sy = scrollBarY();
            boolean hov = mouseX >= sbX && mouseX < sbX + scrollbarWidth() && mouseY >= sy && mouseY < sy + sh;

            g.fill(sbX, getY(), sbX + scrollbarWidth(), getBottom() - 1, 0xFF1A1A1A);
            g.fill(sbX + 1, sy, sbX + scrollbarWidth() - 1, sy + sh, hov ? 0xFF6A6A6A : 0xFF505050);
        }

        final class Row extends AbstractSelectionList.Entry<@NonNull Row> {
            @Nullable
            private final T value;

            Row(@Nullable T value) { this.value = value; }

            @Override
            public void extractContent(@NonNull GuiGraphicsExtractor g, int mx, int my, boolean hovered, float a) {
                boolean isSelected = value == null ? owner.selected == null : getSelected() == this;
                int bg = isSelected ? 0xFF1E4D72 : hovered ? 0xFF262626 : 0xFF000000;

                g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bg);

                String label = value == null
                    ? Component.translatable(LangKeys.GUI_WIDGET_ADVANCED_COMBO_BOX_NONE).getString()
                    : owner.displayStringMapper.apply(value);
                int color = value == null ? 0xFF888888 : 0xFFFFFFFF;

                // Same renderer as the collapsed box (sans arrow), so the type swatch is embedded
                // identically in the open list and the closed display.
                owner.theme.renderText(
                    g, owner.font, getX(), getY(), getWidth(), getHeight(),
                    label, owner.indicatorColorFor(value), color, false
                );
            }

            @Override
            public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
                if (event.button() != 0) return false;

                DropdownModal.this.setSelected(this);
                owner.setValue(value);
                AkytheraEditorLayoutManager.getInstance().closeModal();

                return true;
            }
        }
    }
}