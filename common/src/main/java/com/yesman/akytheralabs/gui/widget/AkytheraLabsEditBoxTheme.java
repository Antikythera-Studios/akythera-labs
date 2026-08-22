package com.yesman.akytheralabs.gui.widget;

import com.yesman.akythera.client.gui.widget.BuiltInButtonTheme;
import com.yesman.akythera.client.gui.widget.common.WidgetTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;

/// Names are suffixed by '_EDIT_BOX' to avoid crash with [BuiltInButtonTheme]
public enum AkytheraLabsEditBoxTheme implements EditBoxTheme {
    /// Vanilla [EditBox] style: 1px border (white when focused, gray otherwise), black fill. Border color
    /// priority: warned (red) > focused > editable.
    DEFAULT_EDIT_BOX(
        (widget, graphics, _, _, _) -> {
            int x = widget.getX();
            int y = widget.getY();
            int w = widget.getWidth();
            int h = widget.getHeight();
            boolean focused = widget.isFocused();
            int borderColor = widget.isWarned()
                ? 0xFFAA2222
                : focused
                    ? 0xFFFFFFFF
                    : widget.isEditable()
                        ? 0xFFA0A0A0
                        : 0xFF6F6F6F;
            graphics.fill(x, y, x + w, y + h, 0xFF000000);
            graphics.outline(x, y, w, h, borderColor);
        }
    ),
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
    );

    final EditBoxRenderer renderer;
    final int id;

    AkytheraLabsEditBoxTheme(EditBoxRenderer renderer) {
        this.renderer = renderer;
        this.id = WidgetTheme.ENUM_MANAGER.assign(this);
    }

    @Override
    public void renderBackground(AdvancedEditBox<?> widget, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        renderer.renderBackground(widget, graphics, mouseX, mouseY, a);
    }

    @Override
    public int universalOrdinal() {
        return id;
    }
}