package com.yesman.akytheralabs.gui.widget.main;

import com.mojang.blaze3d.platform.InputConstants;
import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akythera.client.gui.widget.common.DataBoundWidget;
import com.yesman.akytheralabs.editor.keybind.EditorKeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

/// A focusable cell that displays an [EditorKeyMapping] and rebinds it on the next
/// non-modifier key press while focused.
///
/// Focus visual: yellow outline while capturing, gray otherwise.
/// Modifier-only presses (Shift/Ctrl/Alt/Super alone) are swallowed so users can
/// chord keys naturally — the binding commits on the first real key.
public final class KeyMappingHolder extends AbstractWidget implements AnchoredWidget, DataBoundWidget<EditorKeyMapping> {
    private final Font font;
    private final EditorKeyMapping mapping;
    private boolean capturing;

    public KeyMappingHolder(
        Font font,
        EditorKeyMapping mapping,
        Unit x1,
        Unit x2,
        Unit y1,
        Unit y2,
        AnchoredWidget.HorizontalAnchorType horizontalAnchorType,
        AnchoredWidget.VerticalAnchorType verticalAnchorType
    ) {
        super(0, 0, 0, 0, mapping.getName());

        this.font = font;
        this.x1 = x1;
        this.x2 = x2;
        this.y1 = y1;
        this.y2 = y2;
        this.horizontalAnchorType = horizontalAnchorType;
        this.verticalAnchorType = verticalAnchorType;
        this.mapping = mapping;
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (!isMouseOver(event.x(), event.y())) return false;

        capturing = true;
        setFocused(true);

        return true;
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (!capturing) return false;

        // Wait for a non-modifier key so chords like Ctrl+Shift+Z commit on the Z press
        if (isModifierOnly(event.key())) return true;

        // Esc cancels the capture without rebinding
        if (event.key() == InputConstants.KEY_ESCAPE) {
            capturing = false;
            setFocused(false);
            return true;
        }

        mapping.set(event.key(), event.modifiers());
        capturing = false;
        setFocused(false);

        return true;
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused) capturing = false;
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int outline = capturing ? 0xFFFFD800 : isFocused() ? 0xFFCCCCCC : 0xFF6E6E6E;

        graphics.fill(getX(), getY(), getRight(), getBottom(), outline);
        graphics.fill(getX() + 1, getY() + 1, getRight() - 1, getBottom() - 1, 0xFF1A1A1A);

        Component label = capturing ? Component.literal("...") : mapping.getDisplayName();
        int tx = getX() + (getWidth() - font.width(label)) / 2;
        int ty = getY() + (getHeight() - font.lineHeight) / 2 + 1;

        graphics.text(font, label, tx, ty, 0xFFCCCCCC, false);
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput narrationElementOutput) {
    }

    private static boolean isModifierOnly(int key) {
        return key == InputConstants.KEY_LSHIFT || key == InputConstants.KEY_RSHIFT
            || key == InputConstants.KEY_LCONTROL || key == InputConstants.KEY_RCONTROL
            || key == InputConstants.KEY_LALT || key == InputConstants.KEY_RALT
            || key == InputConstants.KEY_LSUPER || key == InputConstants.KEY_RSUPER;
    }

    // ***************************************************************
    // [AnchoredWidget] implementations
    // ***************************************************************
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

    // *****************************************************************
    // [DataBoundWidget] implementations
    // *****************************************************************
    @Override
    public Callable<EditorKeyMapping> getDataProvider() {
        return () -> mapping;
    }

    @Override
    public Consumer<EditorKeyMapping> valueChangeCallback() {
        return _ -> {};
    }

    @Override
    public Consumer<EditorKeyMapping> valueSetter() {
        return _ -> {};
    }

    @Override
    public void reset() {
        mapping.reset();
    }
}