package com.yesman.akytheralabs.gui.widget;

import com.yesman.akythera.client.gui.widget.common.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/// Modeled after [net.minecraft.client.gui.components.EditBox] with extra functionality
/// and switchable background style via [EditBoxTheme].
public final class AdvancedEditBox<T> extends AbstractWidget
    implements AnchoredWidget, DataBoundWidget<T>, ThemeApplicableWidget<EditBoxTheme>, WarningMarkableWidget<T>
{
    private static final int TEXT_PADDING = 4;
    private static final int CURSOR_BLINK_INTERVAL_MS = 600;

    /// Convenience factory for a plain string edit box (T = String, no bound data, no conversion).
    public static AdvancedEditBox<String> stringBox(
        Font font,
        Unit x1, Unit x2, Unit y1, Unit y2,
        HorizontalAnchorType horizontalAnchor,
        VerticalAnchorType verticalAnchor,
        Callable<String> dataProvider,
        Consumer<String> onWidgetChanged,
        Component message
    ) {
        return new AdvancedEditBox<>(
            font,
            x1, x2, y1, y2, horizontalAnchor, verticalAnchor,
            dataProvider, onWidgetChanged,
            _ -> true, s -> s, s -> s,
            message
        );
    }

    /// Creates a plain string edit box not bound to the anchor layout system.
    /// Position and size must be set explicitly (e.g. by a parent widget's layout pass).
    public static AdvancedEditBox<String> filteredStringBox(
        Font font,
        Predicate<String> filter,
        Component placeholder
    ) {
        return filteredStringBox(font, filter, placeholder, HorizontalAnchorType.LEFT_WIDTH, VerticalAnchorType.TOP_HEIGHT);
    }

    /// Variant that lets the caller pick anchor types (e.g. `LEFT_RIGHT` for boxes that should
    /// span from a fixed left offset to the row's right edge). Anchor units default to `pixel(0)`
    /// — the caller sets them via [#setX1] / [#setX2] / [#setY1] / [#setY2] before the first relocate.
    public static AdvancedEditBox<String> filteredStringBox(
        Font font,
        Predicate<String> filter,
        Component placeholder,
        HorizontalAnchorType horizontalAnchor,
        VerticalAnchorType verticalAnchor
    ) {
        return new AdvancedEditBox<>(
            font,
            AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0),
            horizontalAnchor,
            verticalAnchor,
            () -> "",
            _ -> {},
            filter,
            s -> s,
            s -> s,
            placeholder
        );
    }

    private EditBoxTheme theme = AkytheraLabsEditBoxTheme.AKYTHERA_EDITOR_EDIT_BOX;

    private final Font font;

    /// Input string filter to accept or discard newly entered string.
    /// Receives the candidate full string after the edit. Return true to accept.
    private final Predicate<String> filter;

    /// Converts the display string into the bound data type.
    /// May throw (e.g. NumberFormatException) to signal invalid input;
    /// the edit box will silently reject such mutations.
    private final Function<String, T> deserializer;

    /// Converts the bound data type into a display string.
    private final Function<T, String> serializer;

    private @Nullable T value;
    private String text = "";

    /// Called when editing is done (focus-loss / Enter), not per keystroke. Receives the value
    /// bound at focus-gain (the `old` argument) and the freshly committed value (`new`). Fires on
    /// every successful commit, including unchanged values; consumers that care dedupe themselves.
    @Nullable
    private BiConsumer<T, T> onCommitted;

    /// Bound value snapshotted on focus-gain; passed as the `old` argument to [#onCommitted].
    @Nullable
    private T preEditValue;

    /// Index into [#text] where the editing cursor sits (0 = before first char).
    private int cursorPos;

    /// The start index of the visible portion of text, used for horizontal scrolling.
    private int displayOffset;

    /// Fixed end of a read-only drag selection (the moving end is [#cursorPos]). Equal to
    /// [#cursorPos] when nothing is selected. Drives the highlight + the view scroll while the user
    /// drags a read-only box to read overflowing text. See [#mouseDragged].
    private int selectionAnchor;

    /// Timestamp of the last focus-gain, used for cursor blink timing.
    private long focusTimestamp;

    /// True when the box is flagged with [#mark] for an exceptional condition (e.g. validation failure).
    /// Cleared automatically by [#onTextMutated] on every accepted text change.
    private boolean warned = false;

    /// When false, the box behaves like a non-focusable label: still renders its text but
    /// refuses focus, suppresses the cursor, and ignores key/char input.
    private boolean editable = true;

    /// Per-character input substitutions applied in [#charTyped] before the filter check.
    /// Lazily initialized via [#addCharReplacement].
    private @Nullable Map<Character, Character> charReplacements = null;

    /// Display-only decoration of [#text] — the last thing between the string and the screen (e.g.
    /// suffixing a component's name with its type). It never feeds back into [#text], the filter, or
    /// the bound value, so what a formatted box commits is exactly what was typed into it.
    /// Lazily initialized via [#setFormatter]; null = draw [#text] verbatim.
    private @Nullable Function<String, String> formatter = null;

    public AdvancedEditBox(
        final Font font,
        final Unit x1,
        final Unit x2,
        final Unit y1,
        final Unit y2,
        final AnchoredWidget.HorizontalAnchorType horizontalAnchor,
        final AnchoredWidget.VerticalAnchorType verticalAnchor,
        final Callable<T> dataProvider,
        final Consumer<T> onWidgetChanged,
        final Predicate<String> filter,
        final Function<String, T> deserializer,
        final Function<T, String> serializer,
        final Component message
    ) {
        super(0, 0, 0, 0, message);

        this.font = font;
        this.x1 = x1;
        this.x2 = x2;
        this.y1 = y1;
        this.y2 = y2;
        this.horizontalAnchorType = horizontalAnchor;
        this.verticalAnchorType = verticalAnchor;
        this.dataProvider = dataProvider;
        this.onValueChanged = onWidgetChanged;
        this.filter = filter;
        this.deserializer = deserializer;
        this.serializer = serializer;
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean focused = isFocused();
        theme.renderBackground(this, graphics, mouseX, mouseY, a);
        int textY = y + (h - 8) / 2;

        if (text.isEmpty() && !focused) {
            // Placeholder message
            graphics.text(font, getMessage(), x + TEXT_PADDING, textY, 0xFF555555, false);
        } else {
            // Clamp displayOffset so the active end of input — the editing caret, or the dragged end
            // of a read-only selection — stays visible. The view therefore follows the drag.
            ensureCursorVisible();

            // The offsets index [#text]; a formatter only ever decorates an unfocused box, so they
            // are re-clamped against the drawn string rather than driving it.
            String display = displayText();
            int from = Math.min(displayOffset, display.length());
            String visible = font.plainSubstrByWidth(display.substring(from), w - TEXT_PADDING * 2);

            // Read-only drag selection highlight (drawn under the text).
            if (!editable && selectionAnchor != cursorPos) {
                int selStart = Math.min(selectionAnchor, cursorPos);
                int selEnd = Math.max(selectionAnchor, cursorPos);
                int visStart = Math.clamp(selStart, from, from + visible.length());
                int visEnd = Math.clamp(selEnd, from, from + visible.length());

                if (visEnd > visStart) {
                    int hx1 = x + TEXT_PADDING + font.width(display.substring(from, visStart));
                    int hx2 = x + TEXT_PADDING + font.width(display.substring(from, visEnd));
                    graphics.fill(hx1, textY - 1, hx2, textY + 9, 0xFF2F5C8F);
                }
            }

            int textColor = editable ? 0xFFCCCCCC : 0xFF7A7A7A;
            graphics.text(font, visible, x + TEXT_PADDING, textY, textColor, false);

            // Blinking cursor (only when editable + focused — where display is the raw text)
            if (editable && focused && cursorBlinkOn()) {
                int cursorScreenX = x + TEXT_PADDING + font.width(text.substring(displayOffset, cursorPos));
                graphics.fill(cursorScreenX, textY - 1, cursorScreenX + 1, textY + 9, 0xFFCCCCCC);
            }
        }
    }

    /// The string that actually reaches the screen: [#text] through [#formatter], if one is set.
    /// A focused box is exempt and shows the raw text — edit mode is where the caret, the cursor
    /// keys and the commit all work on [#text], so decoration there would put the cursor among
    /// characters nobody typed. The formatted form is therefore what an *unfocused* box reads as.
    private String displayText() {
        return (formatter == null || isFocused()) ? text : formatter.apply(text);
    }

    /// Installs (or clears, with null) the display-only text transform. See [#formatter].
    public void setFormatter(@Nullable Function<String, String> formatter) {
        this.formatter = formatter;
    }

    private boolean cursorBlinkOn() {
        return ((System.currentTimeMillis() - focusTimestamp) % (CURSOR_BLINK_INTERVAL_MS * 2)) < CURSOR_BLINK_INTERVAL_MS;
    }

    private void ensureCursorVisible() {
        // Enforce 0 <= displayOffset <= cursorPos <= text.length() before any substring; callers
        // (and stale state from a box opened before it was laid out) can otherwise leave these
        // out of range and crash the substring below.
        int len = text.length();
        cursorPos = Math.clamp(cursorPos, 0, len);
        displayOffset = Math.clamp(displayOffset, 0, cursorPos);

        int innerWidth = getWidth() - TEXT_PADDING * 2;
        if (innerWidth <= 0) return; // not laid out yet — don't scroll text off-screen

        if (font.width(text.substring(displayOffset, cursorPos)) > innerWidth) {
            // Advance displayOffset until cursor is within visible area
            while (displayOffset < cursorPos && font.width(text.substring(displayOffset, cursorPos)) > innerWidth) {
                displayOffset++;
            }
        }
    }

    public void setOnCommitted(@Nullable BiConsumer<T, T> onCommitted) {
        this.onCommitted = onCommitted;
    }

    public void setCursorPosRightmost() {
        cursorPos = text.length();
        ensureCursorVisible();
    }

    @Override
    public void onClick(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (!editable) {
            // Read-only: a press sets the selection anchor; dragging extends it (see mouseDragged).
            cursorPos = cursorIndexAt(event.x());
            selectionAnchor = cursorPos;
            return;
        }

        focusTimestamp = System.currentTimeMillis();

        // Place cursor at click position
        cursorPos = cursorIndexAt(event.x());
        selectionAnchor = cursorPos;
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
        // Editable boxes keep their default behaviour (text selection isn't implemented there); a
        // read-only box uses the drag to extend a selection, scrolling the view toward the cursor —
        // the dragged end — so the offset follows the drag direction like a normal text field.
        if (editable) return super.mouseDragged(event, dx, dy);

        int innerLeft = getX() + TEXT_PADDING;
        int innerRight = getX() + getWidth() - TEXT_PADDING;

        // Past an edge, step the cursor one character that way so the view keeps scrolling toward the
        // drag direction; otherwise drop it under the pointer.
        if (event.x() >= innerRight) {
            cursorPos = Math.min(text.length(), cursorPos + 1);
        } else if (event.x() <= innerLeft) {
            cursorPos = Math.max(0, cursorPos - 1);
        } else {
            cursorPos = cursorIndexAt(event.x());
        }

        return true; // ensureCursorVisible() during render scrolls displayOffset to follow cursorPos
    }

    /// Character index under the given screen x, mapped within the currently visible text window.
    private int cursorIndexAt(double mouseX) {
        int innerW = getWidth() - TEXT_PADDING * 2;
        String visible = font.plainSubstrByWidth(text.substring(displayOffset), Math.max(0, innerW));
        int relX = (int) mouseX - getX() - TEXT_PADDING;
        String trimmed = font.plainSubstrByWidth(visible, Math.max(0, relX));

        return displayOffset + trimmed.length();
    }

    @Override
    public boolean charTyped(@NonNull CharacterEvent event) {
        if (!editable || !isFocused()) return false;

        String charStr = event.codepointAsString();

        if (charStr.length() == 1 && charReplacements != null) {
            Character replacement = charReplacements.get(charStr.charAt(0));
            if (replacement != null) charStr = String.valueOf(replacement);
        }

        String candidate = text.substring(0, cursorPos) + charStr + text.substring(cursorPos);

        if (filter.test(candidate)) {
            text = candidate;
            cursorPos++;
            onTextMutated();
        }

        return true;
    }

    /// Registers a single-character substitution applied on input before the filter check.
    /// Useful when the filter would otherwise reject a key that the user almost certainly
    /// meant as an alias (e.g. space → underscore for identifier-like inputs).
    public void addCharReplacement(char from, char to) {
        if (charReplacements == null) charReplacements = new HashMap<>();
        charReplacements.put(from, to);
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (!editable || !isFocused()) return false;

        boolean ctrl = (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;

        switch (event.key()) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (ctrl) {
                    deleteWordBackward();
                } else if (cursorPos > 0) {
                    String candidate = text.substring(0, cursorPos - 1) + text.substring(cursorPos);
                    if (filter.test(candidate)) {
                        text = candidate;
                        cursorPos--;
                        onTextMutated();
                    }
                }
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (ctrl) {
                    deleteWordForward();
                } else if (cursorPos < text.length()) {
                    String candidate = text.substring(0, cursorPos) + text.substring(cursorPos + 1);
                    if (filter.test(candidate)) {
                        text = candidate;
                        onTextMutated();
                    }
                }
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                cursorPos = ctrl ? wordBoundaryBackward() : Math.max(0, cursorPos - 1);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                cursorPos = ctrl ? wordBoundaryForward() : Math.min(text.length(), cursorPos + 1);
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                cursorPos = 0;
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                cursorPos = text.length();
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                // Commit: unfocus
                setFocused(false);
                return true;
            }
        }

        if (event.isEscape()) {
            setFocused(false);
            return true;
        }

        return false;
    }

    private void deleteWordBackward() {
        if (cursorPos == 0) return;
        int to = wordBoundaryBackward();
        String candidate = text.substring(0, to) + text.substring(cursorPos);
        if (filter.test(candidate)) {
            text = candidate;
            cursorPos = to;
            onTextMutated();
        }
    }

    private void deleteWordForward() {
        if (cursorPos >= text.length()) return;
        int to = wordBoundaryForward();
        String candidate = text.substring(0, cursorPos) + text.substring(to);
        if (filter.test(candidate)) {
            text = candidate;
            onTextMutated();
        }
    }

    private int wordBoundaryBackward() {
        int i = cursorPos - 1;
        while (i > 0 && text.charAt(i - 1) == ' ') i--;
        while (i > 0 && text.charAt(i - 1) != ' ') i--;
        return i;
    }

    private int wordBoundaryForward() {
        int i = cursorPos;
        int len = text.length();
        while (i < len && text.charAt(i) != ' ') i++;
        while (i < len && text.charAt(i) == ' ') i++;
        return i;
    }

    @Override
    public void setFocused(boolean focused) {
        if (focused && !editable) return;       // never gain focus when read-only

        boolean wasFocused = isFocused();
        super.setFocused(focused);

        if (focused && !wasFocused) {
            focusTimestamp = System.currentTimeMillis();
            preEditValue = value;
        }

        // On losing focus, try to commit the current text to the bound value.
        // If deserialization fails, revert to the last known good value.
        if (!focused && wasFocused) {
            commitOrRevert();
        }
    }

    /// Called after every accepted text mutation (char typed, delete, etc.).
    /// Attempts to deserialize and push the value to the bound data.
    private void onTextMutated() {
        unmark();
        try {
            T parsed = deserializer.apply(text);
            value = parsed;
            onValueChanged.accept(parsed);
        } catch (Exception ignored) {
            // Text is not yet a valid T (e.g. partial number input "-").
            // Keep the text but don't push to bound data.
        }
    }

    /// On focus loss, try to finalize the text. If invalid, revert to last good value.
    private void commitOrRevert() {
        try {
            T parsed = deserializer.apply(text);
            value = parsed;
            // Re-serialize to normalize display (e.g. "03" -> "3")
            text = serializer.apply(parsed);
            cursorPos = text.length();
            displayOffset = 0;
            onValueChanged.accept(parsed);
            if (onCommitted != null) onCommitted.accept(preEditValue, parsed);
        } catch (Exception e) {
            // Revert to the last successfully committed value
            if (value != null) {
                text = serializer.apply(value);
            } else {
                text = "";
            }
            cursorPos = text.length();
            displayOffset = 0;
        }
    }

    /// Sets the value from a typed T, updating both internal state and display text.
    public void setValue(T value) {
        this.value = value;
        this.text = serializer.apply(value);
        this.cursorPos = Math.min(cursorPos, text.length());
        this.displayOffset = 0;
    }

    /// Sets the edit box text directly from a raw string.
    /// The string is validated by the filter and deserialized to update the bound value.
    public void setValueByString(String text) {
        if (!filter.test(text)) return;

        this.text = text;
        this.cursorPos = Math.min(cursorPos, text.length());
        this.displayOffset = 0;

        try {
            T parsed = deserializer.apply(text);
            this.value = parsed;
            onValueChanged.accept(parsed);
        } catch (Exception ignored) {
        }
    }

    public String getText() {
        return text;
    }

    public AdvancedEditBox<T> setEditable(boolean editable) {
        this.editable = editable;

        // Drop focus immediately so the cursor doesn't keep blinking after going read-only.
        if (!editable && isFocused()) super.setFocused(false);

        return this;
    }

    public boolean isEditable() {
        return editable;
    }

    public @Nullable T getValue() {
        return value;
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput narrationElementOutput) {
    }

    public boolean isWarned() {
        return warned;
    }

    @Override
    public void mark() {
        warned = true;
    }

    @Override
    public void unmark() {
        warned = false;
    }

    /// ***************************************************************
    /// [ThemeApplicableWidget] implementations                       *
    /// ***************************************************************
    @Override
    public boolean isSupportedTheme(WidgetTheme theme) {
        return EditBoxTheme.class.isAssignableFrom(theme.getClass());
    }

    @Override
    public @NonNull EditBoxTheme getTheme() {
        return theme;
    }

    @Override
    public void setTheme(EditBoxTheme theme) {
        this.theme = theme;
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

    /// *****************************************************************
    /// [DataBoundWidget] implementations                               *
    /// *****************************************************************
    private final Callable<T> dataProvider;
    private final Consumer<T> onValueChanged;

    @Override
    public Callable<T> getDataProvider() {
        return this.dataProvider;
    }

    @Override
    public Consumer<T> valueChangeCallback() {
        return this.onValueChanged;
    }

    @Override
    public Consumer<T> valueSetter() {
        return this::setValue;
    }

    @Override
    public void reset() {
        this.value = null;
        this.text = "";
        this.cursorPos = 0;
        this.displayOffset = 0;
    }
}
