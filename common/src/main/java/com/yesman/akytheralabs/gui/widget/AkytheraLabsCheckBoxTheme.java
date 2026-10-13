package com.yesman.akytheralabs.gui.widget;

import com.yesman.akythera.client.api.gui.CheckBox;
import com.yesman.akythera.client.api.gui.CheckBoxTheme;
import com.yesman.akythera.client.api.gui.common.WidgetTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/// [CheckBox] styles. Names are suffixed by '_CHECK_BOX' to avoid crash with the other [WidgetTheme] enums.
public enum AkytheraLabsCheckBoxTheme implements CheckBoxTheme {
    /// Node graph style, for a boolean on a node's input pin: the [AkytheraLabsEditBoxTheme#BLUEPRINT_EDIT_BOX]
    /// frame as a square, with an inset mark when checked.
    /// Border color priority: focused (blue) > hovered > idle.
    BLUEPRINT_CHECK_BOX(
        (widget, graphics, size, markColor) -> {
            int x = widget.getX();
            int y = widget.getY();
            int border = widget.isFocused()
                ? 0xFF5A8AAA : widget.isHovered()
                    ? 0xFF7A7A7A : 0xFF4A4A4A;

            // Rounded corners: two crossed fills leave the corner pixels out.
            graphics.fill(x + 1, y, x + size - 1, y + size, border);
            graphics.fill(x, y + 1, x + size, y + size - 1, border);
            graphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0xFF151515);

            if (widget.getWidgetValue()) {
                graphics.fill(x + 3, y + 3, x + size - 3, y + size - 3, markColor);
            }
        }
    );

    private final BoxRenderer renderer;
    private final int id;

    AkytheraLabsCheckBoxTheme(BoxRenderer renderer) {
        this.renderer = renderer;
        this.id = WidgetTheme.ENUM_MANAGER.assign(this);
    }

    @Override
    public void renderBox(CheckBox widget, GuiGraphicsExtractor graphics, int size, int markColor) {
        renderer.renderBox(widget, graphics, size, markColor);
    }

    @Override
    public int universalOrdinal() {
        return id;
    }
}
