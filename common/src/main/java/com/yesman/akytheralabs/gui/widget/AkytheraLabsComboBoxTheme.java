package com.yesman.akytheralabs.gui.widget;

import com.yesman.akythera.client.gui.widget.common.WidgetTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/// [AdvancedComboBox] styles. Constant names are suffixed `_COMBO_BOX` so they do not collide
/// with the other [WidgetTheme] enums sharing [WidgetTheme#ENUM_MANAGER] (see
/// [AkytheraLabsEditBoxTheme]).
public enum AkytheraLabsComboBoxTheme implements ComboBoxTheme {
    /// Original look: black fill, state-colored 1-px outline, white label, no type indicator.
    DEFAULT_COMBO_BOX(
        (widget, g, _, _, _) -> {
            int outlineColor = widget.isMarked()
                ? 0xFFFF5555 : widget.isFocused()
                    ? -1 : widget.isActive()
                        ? -6250336 : -12566463;
            g.fill(widget.getX(), widget.getY(), widget.getX() + widget.getWidth(), widget.getY() + widget.getHeight(), -16777216);
            g.outline(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight(), outlineColor);
        },
        (g, font, x, y, width, height, text, _, textColor, drawArrow) -> {
            int pad = 4;
            int arrowSlot = drawArrow ? 10 : 0;
            String clipped = font.plainSubstrByWidth(text, Math.max(0, width - pad * 2 - arrowSlot));
            int textY = y + height / 2 - font.lineHeight / 2 + 1;
            g.text(font, Component.literal(clipped), x + pad, textY, textColor, false);
            if (drawArrow) {
                g.text(font, Component.literal("▼"), x + width - 8, textY, textColor, false);
            }
        }
    ),
    /// Unreal-style variable type indicator: a dark rounded fill with a colored capsule swatch in
    /// front of the label (the type color, matched to the node pins via
    /// [com.yesman.akytheralabs.gui.widget.script.NodeColors]) and a muted dropdown arrow.
    UE_VARIABLE_COMBO_BOX(
        (widget, g, _, _, _) -> {
            int x = widget.getX(), y = widget.getY(), w = widget.getWidth(), h = widget.getHeight();
            int border = widget.isMarked()
                ? 0xFFAA2222 : widget.isFocused()
                    ? 0xFF5A8AAA : 0xFF202020;
            g.fill(x, y, x + w, y + h, widget.isActive() ? 0xFF383838 : 0xFF2A2A2A);
            g.outline(x, y, w, h, border);
        },
        (g, font, x, y, width, height, text, indicatorColor, textColor, drawArrow) -> {
            int pad = 5;
            int textLeft = x + pad;

            if (indicatorColor != null) {
                int pillW = 14, pillH = 8;
                int px = x + pad;
                int py = y + height / 2 - pillH / 2;
                // Rounded capsule: the full bar with its four corner pixels trimmed.
                g.fill(px + 1, py, px + pillW - 1, py + pillH, indicatorColor);
                g.fill(px, py + 1, px + pillW, py + pillH - 1, indicatorColor);
                textLeft = px + pillW + 6;
            }

            int arrowSlot = drawArrow ? 12 : pad;
            String clipped = font.plainSubstrByWidth(text, Math.max(0, x + width - arrowSlot - textLeft));
            int textY = y + height / 2 - font.lineHeight / 2 + 1;
            g.text(font, Component.literal(clipped), textLeft, textY, textColor, false);

            if (drawArrow) {
                g.text(font, Component.literal("▼"), x + width - 9, textY, 0xFFB0B0B0, false);
            }
        }
    );

    private final BackgroundRenderer backgroundRenderer;
    private final TextRenderer textRenderer;
    private final int id;

    AkytheraLabsComboBoxTheme(BackgroundRenderer backgroundRenderer, TextRenderer textRenderer) {
        this.backgroundRenderer = backgroundRenderer;
        this.textRenderer = textRenderer;
        this.id = WidgetTheme.ENUM_MANAGER.assign(this);
    }

    @Override
    public void renderBackground(AdvancedComboBox<?> widget, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        backgroundRenderer.render(widget, graphics, mouseX, mouseY, a);
    }

    @Override
    public void renderText(
        GuiGraphicsExtractor graphics, Font font,
        int x, int y, int width, int height,
        String text, Integer indicatorColor, int textColor, boolean drawArrow
    ) {
        textRenderer.render(graphics, font, x, y, width, height, text, indicatorColor, textColor, drawArrow);
    }

    @Override
    public int universalOrdinal() {
        return id;
    }
}
