package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.AdvancedEditBox;
import com.yesman.akytheralabs.gui.widget.script.node.LiteralNodeData;
import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/// Widget for a [com.yesman.akythera.api.data.modularize.node.LiteralNode]: a pure node
/// that holds a constant value, embeddable into any data input pin. No execution pins
/// and no inputs — a single typed output whose label shows the current value.
///
/// Only literal-encodable types are offered (see [LiteralValueSupport]): primitives /
/// wrappers, [String], and `enum`s, matching the engine's `LiteralNode` which serializes
/// the value directly through its [com.yesman.akythera.api.data.modularize.ScriptType]
/// codec. Editing happens on a body click:
/// - **boolean / enum** cycle in place (no typing required);
/// - **numeric / char / string** type into an edit box embedded in the node body
///   (UE-style), committing on Enter or when focus leaves.
public final class LiteralNodeWidget extends AbstractNodeWidget {
    private final Class<?> valueType;
    private Object value;

    /// Inline edit box for numeric / char / string literals; null for click-cyclable
    /// (boolean / enum) types, which never type. Non-null does not imply visible — it is
    /// rendered and fed input only while [#editing].
    private final @Nullable AdvancedEditBox<String> editBox;
    private boolean editing;

    public LiteralNodeWidget(Font font, Class<?> valueType, int graphX, int graphY) {
        super(font, Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_LITERAL), graphX, graphY);

        this.valueType = valueType;
        this.value = LiteralValueSupport.defaultValue(valueType);

        setTitleColor(NodeColors.titleTint(valueType));

        addOutput(new Pin(LiteralValueSupport.display(value), NodeColors.forType(valueType), false, valueType));

        if (LiteralValueSupport.isClickCyclable(valueType)) {
            this.editBox = null;
        } else {
            this.editBox = AdvancedEditBox.filteredStringBox(font, LiteralValueSupport.filterFor(valueType), Component.empty());
            this.editBox.setOnCommitted((_, _) -> onEditCommitted());
        }

        relayout();
    }

    @Override
    public boolean onBodyClick(int graphX, int graphY, double screenX, double screenY) {
        // Only the body (below the title) edits — the title stays a drag handle.
        if (graphY < getY() + titleBarHeight()) return false;

        if (LiteralValueSupport.isClickCyclable(valueType)) {
            value = LiteralValueSupport.cycle(valueType, value);
            refreshLabel();
            return true;
        }

        beginEditing();
        return true;
    }

    /// Opens the inline edit box seeded with the current raw value and focused, so the
    /// user can type immediately. The host graph then routes key/char input here.
    private void beginEditing() {
        if (editBox == null) return;

        editing = true;
        editBox.setValueByString(String.valueOf(value));
        editBox.setCursorPosRightmost();
        editBox.setFocused(true);
    }

    @Override
    public boolean isInlineEditing() {
        return editing;
    }

    @Override
    public boolean inlineKeyPressed(@NonNull KeyEvent event) {
        if (editBox == null) return false;

        editBox.keyPressed(event);
        // Enter / Escape unfocus the box (committing); mirror that into the editing flag so
        // the graph stops routing keys here. Always consume so node shortcuts (e.g. DELETE)
        // never fire while typing.
        syncEditingWithFocus();
        return true;
    }

    @Override
    public boolean inlineCharTyped(@NonNull CharacterEvent event) {
        if (editBox == null) return false;

        editBox.charTyped(event);

        return true;
    }

    @Override
    public void endInlineEdit() {
        if (editBox == null || !editing) return;

        editBox.setFocused(false); // commits via the box's focus-loss path
        editing = false;
    }

    /// Keeps [#editing] in step with the box focus, since the box unfocuses itself on
    /// Enter / Escape.
    private void syncEditingWithFocus() {
        if (editBox != null && !editBox.isFocused()) editing = false;
    }

    private void onEditCommitted() {
        if (editBox != null) commitText(editBox.getText());
    }

    private void commitText(String text) {
        try {
            value = LiteralValueSupport.parse(valueType, text);
            refreshLabel();
        } catch (Exception ignored) {
            // Keep the previous value on un-parseable / empty input.
        }
    }

    /// Re-renders the value into the output pin label and re-fits the node width.
    private void refreshLabel() {
        setOutputLabel(0, LiteralValueSupport.display(value));
        relayout();
    }

    @Override
    protected void extractBodyOverlay(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        if (!editing || editBox == null) return;

        // Span the first row's body, leaving the right edge clear for the output pin marker.
        int rowTop = getY() + titleBarHeight() + 3;
        int boxX = getX() + H_PAD;
        int boxRight = getX() + getWidth() - H_PAD - PIN;
        editBox.setX(boxX);
        editBox.setY(rowTop);
        editBox.setWidth(Math.max(0, boxRight - boxX));
        editBox.setHeight(ROW_H);

        editBox.extractRenderState(g, mouseX, mouseY, a);
    }

    public Class<?> valueType() {
        return valueType;
    }

    public Object value() {
        return value;
    }

    /// The current value in its raw, codec-free text form for persistence: a string verbatim, an
    /// enum by its constant name, everything else via [String#valueOf]. The inverse of
    /// [#applySerializedValue]. Quote-free (unlike the on-pin [LiteralValueSupport#display]) so it
    /// round-trips through [#applySerializedValue] without unwrapping.
    public String serializedValue() {
        if (value == null) return "";
        if (value instanceof Enum<?> e) return e.name();
        return String.valueOf(value);
    }

    /// Restores the value from its [#serializedValue] form when a saved graph is reloaded. Handles
    /// the click-cyclable types ([Boolean] / `enum`) that [LiteralValueSupport#parse] does not, and
    /// keeps the current value on un-parseable input.
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void applySerializedValue(String raw) {
        try {
            Class<?> boxed = LiteralValueSupport.boxed(valueType);
            if (boxed == Boolean.class) {
                value = Boolean.parseBoolean(raw);
            } else if (valueType.isEnum()) {
                value = Enum.valueOf((Class<? extends Enum>) valueType, raw);
            } else {
                value = LiteralValueSupport.parse(valueType, raw);
            }
            refreshLabel();
        } catch (Exception ignored) {
            // Keep the default value on malformed input.
        }
    }

    @Override
    public NodeData toData() {
        return new LiteralNodeData(scriptTypeOf(valueType), serializedValue());
    }
}