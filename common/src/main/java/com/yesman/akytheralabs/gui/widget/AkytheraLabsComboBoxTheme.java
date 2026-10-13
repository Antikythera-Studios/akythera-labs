package com.yesman.akytheralabs.gui.widget;

import com.yesman.akythera.client.api.gui.ComboBox;
import com.yesman.akythera.client.api.gui.ComboBoxTheme;
import com.yesman.akythera.client.api.gui.ComboIcon;
import com.yesman.akythera.client.api.gui.common.WidgetTheme;
import com.yesman.akytheralabs.gui.widget.script.NodeColors;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/// Editor [ComboBox] styles. Constant names are suffixed `_COMBO_BOX` so they do not collide
/// with the other [WidgetTheme] enums sharing [WidgetTheme#ENUM_MANAGER] (see
/// [AkytheraLabsEditBoxTheme]).
public enum AkytheraLabsComboBoxTheme implements ComboBoxTheme {
    /// Unreal-style variable type indicator: a dark rounded fill with the [ComboIcon] in front of the label
    /// (in the type color, matched to the node pins via [NodeColors]) and a muted dropdown arrow.
    UE_VARIABLE_COMBO_BOX(
        (widget, g, _, _, _) -> {
            int x = widget.getX(), y = widget.getY(), w = widget.getWidth(), h = widget.getHeight();
            int border = widget.isMarked()
                ? 0xFFAA2222 : widget.isFocused()
                    ? 0xFF5A8AAA : 0xFF202020;
            g.fill(x, y, x + w, y + h, widget.isActive() ? 0xFF383838 : 0xFF2A2A2A);
            g.outline(x, y, w, h, border);
        },
        (g, font, x, y, width, height, text, icon, textColor, drawArrow) -> {
            int pad = 5;
            int textLeft = x + pad;

            if (icon != null) {
                icon.draw(g, x + pad, y + height / 2 - ComboIcon.HEIGHT / 2);
                textLeft = x + pad + ComboIcon.WIDTH + 6;
            }

            int arrowSlot = drawArrow ? 12 : pad;
            String clipped = font.plainSubstrByWidth(text, Math.max(0, x + width - arrowSlot - textLeft));
            int textY = y + height / 2 - font.lineHeight / 2 + 1;
            g.text(font, Component.literal(clipped), textLeft, textY, textColor, false);

            if (drawArrow) {
                g.text(font, Component.literal("▼"), x + width - 9, textY, 0xFFB0B0B0, false);
            }
        },
        (font, text, icon, drawArrow) -> (icon != null ? 11 + ComboIcon.WIDTH : 5) + font.width(text) + (drawArrow ? 12 : 5)
    ),
    /// Node graph style, for a value picked on a node's input pin: the
    /// [AkytheraLabsEditBoxTheme#BLUEPRINT_EDIT_BOX] box with the label on the left and a muted dropdown
    /// arrow.
    BLUEPRINT_COMBO_BOX(
        (widget, g, _, _, _) -> {
            int x = widget.getX();
            int y = widget.getY() + 1;
            int w = widget.getWidth();
            int h = widget.getHeight() - 2;
            int border = widget.isMarked()
                ? 0xFFAA2222 : widget.isFocused()
                    ? 0xFF5A8AAA : widget.isHovered()
                        ? 0xFF7A7A7A : 0xFF4A4A4A;

            // Rounded corners: two crossed fills leave the corner pixels out.
            g.fill(x + 1, y, x + w - 1, y + h, border);
            g.fill(x, y + 1, x + w, y + h - 1, border);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF151515);
        },
        (g, font, x, y, width, height, text, _, textColor, drawArrow) -> {
            int pad = 4;
            int arrowSlot = drawArrow ? 11 : 0;
            String clipped = font.plainSubstrByWidth(text, Math.max(0, width - pad * 2 - arrowSlot));
            int textY = y + height / 2 - font.lineHeight / 2 + 1;

            g.text(font, Component.literal(clipped), x + pad, textY, textColor, false);

            if (drawArrow) {
                g.text(font, Component.literal("▼"), x + width - 9, textY, 0xFF9A9A9A, false);
            }
        },
        (font, text, _, drawArrow) -> 8 + font.width(text) + (drawArrow ? 11 : 0)
    );

    private final BackgroundRenderer backgroundRenderer;
    private final TextRenderer textRenderer;
    private final TextMeasurer textMeasurer;
    private final int id;

    AkytheraLabsComboBoxTheme(BackgroundRenderer backgroundRenderer, TextRenderer textRenderer, TextMeasurer textMeasurer) {
        this.backgroundRenderer = backgroundRenderer;
        this.textRenderer = textRenderer;
        this.textMeasurer = textMeasurer;
        this.id = WidgetTheme.ENUM_MANAGER.assign(this);
    }

    @Override
    public void renderBackground(ComboBox<?> widget, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        backgroundRenderer.render(widget, graphics, mouseX, mouseY, a);
    }

    @Override
    public void renderText(
        GuiGraphicsExtractor graphics, Font font,
        int x, int y, int width, int height,
        String text, @Nullable ComboIcon icon, int textColor, boolean drawArrow
    ) {
        textRenderer.render(graphics, font, x, y, width, height, text, icon, textColor, drawArrow);
    }

    @Override
    public int textCellWidth(Font font, String text, @Nullable ComboIcon icon, boolean drawArrow) {
        return textMeasurer.measure(font, text, icon, drawArrow);
    }

    @Override
    public int universalOrdinal() {
        return id;
    }
}
