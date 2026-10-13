package com.yesman.akytheralabs.gui.widget;

import com.yesman.akythera.client.api.gui.AkytheraEditBox;
import com.yesman.akythera.client.api.gui.BuiltInButtonTheme;
import com.yesman.akythera.client.api.gui.EditBoxTheme;
import com.yesman.akythera.client.api.gui.common.WidgetTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/// Names are suffixed by '_EDIT_BOX' to avoid crash with [BuiltInButtonTheme]
public enum AkytheraLabsEditBoxTheme implements EditBoxTheme {
    /// Unreal "variable name" style: draws nothing until focused, so the box reads as a plain
    /// text label (the field's bright text shows over the row) and only reveals a bordered black
    /// edit box while it is being edited — matching UE's rename-in-place fields.
    TRANSPARENT_EDIT_BOX(
        (widget, graphics, _, _, _) -> {
            if (!widget.isFocused()) return;
            int x = widget.getX();
            int y = widget.getY();
            int w = widget.getWidth();
            int h = widget.getHeight();
            graphics.fill(x, y, x + w, y + h, 0xFF000000);
            graphics.outline(x, y, w, h, 0xFFFFFFFF);
        }
    ),
    /// Akythera editor style: dark fill, colored underline strip.
    /// Strip color priority: warned (red) > focused (blue) > unfocused (invisible).
    AKYTHERA_EDITOR_EDIT_BOX(
        (widget, graphics, _, _, _) -> {
            int x = widget.getX();
            int y = widget.getY();
            int w = widget.getWidth();
            int h = widget.getHeight();
            boolean focused = widget.isFocused();
            boolean warned = widget.isWarned();
            graphics.fill(x, y, x + w, y + h, 0xFF1A1A1A);
            int stripColor = warned
                ? 0xFFAA2222 : focused
                    ? 0xFF5A8AAA : 0xFF1A1A1A;
            graphics.fill(x, y + h - 1, x + w, y + h, stripColor);
        }
    ),
    /// Node graph style, for a value typed into a node's input pin: a dark rounded box one pixel shorter
    /// than the widget on each side, so boxes on stacked pin rows never touch.
    /// Border color priority: warned (red) > focused (blue) > hovered > idle.
    BLUEPRINT_EDIT_BOX(
        (widget, graphics, _, _, _) -> {
            int x = widget.getX();
            int y = widget.getY() + 1;
            int w = widget.getWidth();
            int h = widget.getHeight() - 2;
            int border = widget.isWarned()
                ? 0xFFAA2222 : widget.isFocused()
                    ? 0xFF5A8AAA : widget.isHovered()
                        ? 0xFF7A7A7A : 0xFF4A4A4A;

            // Rounded corners: two crossed fills leave the corner pixels out.
            graphics.fill(x + 1, y, x + w - 1, y + h, border);
            graphics.fill(x, y + 1, x + w, y + h - 1, border);
            graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF151515);
        }
    );

    final EditBoxRenderer renderer;
    final int id;

    AkytheraLabsEditBoxTheme(EditBoxRenderer renderer) {
        this.renderer = renderer;
        this.id = WidgetTheme.ENUM_MANAGER.assign(this);
    }

    @Override
    public void renderBackground(AkytheraEditBox<?> widget, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        renderer.renderBackground(widget, graphics, mouseX, mouseY, a);
    }

    @Override
    public int universalOrdinal() {
        return id;
    }
}