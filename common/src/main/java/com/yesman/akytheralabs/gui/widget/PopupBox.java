package com.yesman.akytheralabs.gui.widget;

import com.yesman.akythera.client.gui.widget.AnchoredButton;
import com.yesman.akythera.client.gui.widget.ClampedNumberBox;
import com.yesman.akythera.client.gui.widget.WidgetTable;
import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akythera.client.gui.widget.common.PlaceholderExpressionWidget;
import com.yesman.akythera.client.gui.widget.common.WarningMarkableWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/// A search-and-pick editor cell (modeled on [ClampedNumberBox]'s composite layout): a **read-only**
/// [AdvancedEditBox] showing the bound value's display name, plus a magnifier [AnchoredButton] on the right
/// whose press action is an injected callback — so any modal (animation picker, model picker, registry
/// lookup, ...) can be popped up by the owner.
///
/// The box never mutates the bound value itself; the modal opened by `onSearchPressed` is expected to
/// write the value and push [#valueSetter] to refresh the displayed name.
///
/// As a [WarningMarkableWidget], an optional [#withValidator] predicate flags an invalid bound value (a
/// dangling holder, a stale key) with a red border on the display box — re-evaluated on every [#setValue],
/// i.e. at screen build ([WidgetTable]) and on every modal pick.
public final class PopupBox<T> extends AbstractContainerWidget
    implements AnchoredWidget, WarningMarkableWidget<T>, PlaceholderExpressionWidget<T>, DropdownSizeMemory {

    private static final int PADDING = 2;

    private final AdvancedEditBox<String> displayBox;
    private final AnchoredButton searchButton;

    /// Size this box's picker was last dragged to, or 0 while it has never been resized — see
    /// [DropdownSizeMemory]. Kept here so reopening the picker restores the size the user chose, for
    /// as long as this cell lives.
    private int dropdownWidth;
    private int dropdownHeight;

    /// Converts the bound value into the string shown in the read-only box.
    private final Function<T, String> nameFunction;

    /// Pressed when the value area — the display box, not the magnifier — is clicked. Null leaves the
    /// area inert, which is the default: a picker cell answers the magnifier alone.
    @Nullable
    private Runnable onValuePressed;

    public PopupBox(
        Font font,
        Unit x1, Unit x2, Unit y1, Unit y2,
        HorizontalAnchorType horizontalAnchor, VerticalAnchorType verticalAnchor,
        Callable<T> dataProvider,
        Consumer<T> onWidgetChanged,
        Function<T, String> nameFunction,
        Component message,
        Consumer<PopupBox<T>> onSearchPressed
    ) {
        super(0, 0, 0, 0, message, AbstractScrollArea.defaultSettings(0)); // No scrolling — one line of children

        this.x1 = x1;
        this.x2 = x2;
        this.y1 = y1;
        this.y2 = y2;
        this.horizontalAnchorType = horizontalAnchor;
        this.verticalAnchorType = verticalAnchor;
        this.dataProvider = dataProvider;
        this.onWidgetChanged = onWidgetChanged;
        this.nameFunction = nameFunction;

        displayBox = AdvancedEditBox.stringBox(
            font,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            HorizontalAnchorType.LEFT_WIDTH,
            VerticalAnchorType.TOP_HEIGHT,
            () -> "",
            _ -> {},
            message
        );
        displayBox.setTheme(AkytheraLabsEditBoxTheme.DEFAULT_EDIT_BOX);
        displayBox.setEditable(false);

        searchButton = AnchoredButton
            .buttonBuilder(font, Component.empty(), _ -> onSearchPressed.accept(this))
            .xUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(0))
            .yUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(0))
            .horizontalAnchorType(HorizontalAnchorType.LEFT_WIDTH)
            .verticalAnchorType(VerticalAnchorType.TOP_HEIGHT)
            .theme(AkytheraLabsButtonTheme.SEARCH_ITEM_BUTTON)
            .build();
    }

    /// Positions the read-only box and the square magnifier button inside this widget's current
    /// bounds. Runs on every position and size mutation.
    private void layoutChildren() {
        int buttonSize = getHeight();
        int buttonX = getX() + getWidth() - buttonSize;

        searchButton.setX(buttonX);
        searchButton.setY(getY());
        searchButton.setWidth(buttonSize);
        searchButton.setHeight(buttonSize);

        displayBox.setX(getX());
        displayBox.setY(getY());
        displayBox.setWidth(Math.max(0, buttonX - PADDING - getX()));
        displayBox.setHeight(getHeight());
    }

    @Override
    public void setX(int x) {
        super.setX(x);
        layoutChildren();
    }

    @Override
    public void setY(int y) {
        super.setY(y);
        layoutChildren();
    }

    @Override
    public void relocate(ScreenRectangle screenRectangle) {
        AnchoredWidget.super.relocate(screenRectangle);
        layoutChildren();
    }

    @Override
    protected int contentHeight() {
        return getHeight();
    }

    @Override
    public int dropdownWidth() {
        return dropdownWidth;
    }

    @Override
    public int dropdownHeight() {
        return dropdownHeight;
    }

    @Override
    public void rememberDropdownSize(int width, int height) {
        this.dropdownWidth = width;
        this.dropdownHeight = height;
    }

    @Override
    public @NonNull List<? extends GuiEventListener> children() {
        return List.of(displayBox, searchButton);
    }

    /// Installs the action the value area answers a press with, see [#onValuePressed].
    public PopupBox<T> setOnValuePressed(@Nullable Runnable onValuePressed) {
        this.onValuePressed = onValuePressed;

        return this;
    }

    /// Whether the cursor is over the display box — the row minus the magnifier.
    private boolean overValueArea(double mouseX, double mouseY) {
        return mouseX >= displayBox.getX() && mouseX < displayBox.getX() + displayBox.getWidth()
            && mouseY >= displayBox.getY() && mouseY < displayBox.getY() + displayBox.getHeight();
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (!active || !visible) return false;

        // Ahead of the children: the display box takes the press otherwise, only to refuse the focus
        if (onValuePressed != null && event.button() == 0 && overValueArea(event.x(), event.y())) {
            onValuePressed.run();
            return true;
        }

        for (GuiEventListener child : children()) {
            if (child.mouseClicked(event, doubleClick)) {
                // The read-only display box refuses widget focus, but the container still records it
                // as the focused child — with the dragging flag set, the default container
                // mouseDragged forwards drags to it (out-of-bound text scroll-by-drag).
                setFocused(child);

                if (event.button() == 0) {
                    setDragging(true);
                }

                return true;
            }
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        // Nothing to scroll. Skip.
        return false;
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);

        if (getFocused() != null && !focused) {
            getFocused().setFocused(false);
            setFocused(null);
        }
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        displayBox.extractRenderState(graphics, mouseX, mouseY, a);
        searchButton.extractRenderState(graphics, mouseX, mouseY, a);
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput narrationElementOutput) {
        defaultButtonNarrationText(narrationElementOutput);
    }

    // ***************************************************************
    // [AnchoredWidget] implementations
    // ***************************************************************
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

    // ***************************************************************
    // [DataBoundWidget] implementations
    // ***************************************************************
    private final Callable<T> dataProvider;
    private final Consumer<T> onWidgetChanged;

    @Override
    public @NonNull Callable<T> getDataProvider() {
        return dataProvider;
    }

    @Override
    public @NonNull Consumer<T> valueChangeCallback() {
        return onWidgetChanged;
    }

    @Override
    public @NonNull Consumer<T> valueSetter() {
        return this::setValue;
    }

    /// Shown when the bound value is null — nothing selected yet, or an optional pick cleared.
    private static final String NONE_DISPLAY = "<none>";

    /// The value the display box currently names, or null while it is unset.
    @Nullable
    private T value;

    /// Named in [#displayBox]'s own placeholder slot while [#value] is unset. Null = no placeholder.
    @Nullable
    private T placeholderValue;

    /// Flags an invalid bound value (returns false) for the red-border mark. Null = no validation
    /// (never marked). A null *value* is always valid — that's the deliberate `<none>`.
    @Nullable
    private Predicate<T> validator;

    /// Builder-style: sets the per-site validity predicate and re-evaluates it against the
    /// currently displayed value's absence (a fresh box starts unmarked; the table's initialize
    /// pushes the real value right after construction).
    public PopupBox<T> withValidator(Predicate<T> validator) {
        this.validator = validator;
        return this;
    }

    /// Refreshes the displayed name from `value`. Does not fire [#valueChangeCallback] — the
    /// modal flow that changed the bound data already handled it. Re-evaluates [#validator]:
    /// an invalid value marks the display box red; a valid one (or `<none>`) clears the mark.
    public void setValue(@Nullable T value) {
        this.value = value;
        refreshDisplay();

        if (validator == null || value == null || validator.test(value)) {
            unmark();
        } else {
            mark();
        }
    }

    /// Writes what the read-only box shows: the value's name, or — while it is unset — an empty box
    /// carrying the placeholder's name, falling back to `<none>` when there is no placeholder.
    private void refreshDisplay() {
        displayBox.setPlaceholderValue(placeholderValue == null ? null : nameFunction.apply(placeholderValue));
        displayBox.setValueByString(
            value != null
                ? nameFunction.apply(value)
                : placeholderValue != null ? "" : NONE_DISPLAY
        );
    }

    // ***************************************************************
    // [PlaceholderExpressionWidget] implementations
    // ***************************************************************
    @Override
    public @Nullable T getPlaceholderValue() {
        return placeholderValue;
    }

    @Override
    public void setPlaceholderValue(@Nullable T value) {
        this.placeholderValue = value;
        refreshDisplay();
    }

    @Override
    public boolean hasPlaceholderValue() {
        return value == null && placeholderValue != null;
    }


    @Override
    public void mark() {
        displayBox.mark();
    }

    @Override
    public void unmark() {
        displayBox.unmark();
    }

    @Override
    public void reset() {
        this.value = null;
        refreshDisplay();
        unmark();
    }
}
