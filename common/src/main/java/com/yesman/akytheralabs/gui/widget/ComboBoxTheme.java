package com.yesman.akytheralabs.gui.widget;

import com.yesman.akythera.client.gui.widget.common.WidgetTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;

/// Enum interface for [AdvancedComboBox] styles.
///
/// Mirrors the [com.yesman.akytheralabs.gui.widget.EditBoxTheme] / [com.yesman.akythera.client.gui.widget.ButtonTheme]
/// pattern: a theme owns both the box background and — unlike the other widget themes — the
/// **text render** itself, so a theme can choose whether and how to draw a type-indicator swatch
/// in front of the label. The same [#renderText] is reused for the collapsed display and for each
/// row of the open dropdown, so the two stay visually consistent.
///
/// Implementations are registered to [WidgetTheme#ENUM_MANAGER].
public interface ComboBoxTheme extends WidgetTheme {
    /// Draws the collapsed combo's box: fill + border, styled by the widget's focus / active /
    /// marked state.
    void renderBackground(AdvancedComboBox<?> widget, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a);

    /// Draws a label inside a `[x, y, width, height]` cell: an optional type-indicator swatch
    /// (when `indicatorColor` is non-null), the (clipped) `text`, and — for the collapsed display
    /// only (`drawArrow`) — the dropdown arrow on the right edge. Shared by the collapsed box and
    /// the dropdown rows so the indicator is embedded identically in both.
    void renderText(
        GuiGraphicsExtractor graphics, Font font,
        int x, int y, int width, int height,
        String text, @Nullable Integer indicatorColor, int textColor, boolean drawArrow
    );

    @FunctionalInterface
    interface BackgroundRenderer {
        void render(AdvancedComboBox<?> widget, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a);
    }

    @FunctionalInterface
    interface TextRenderer {
        void render(
            GuiGraphicsExtractor graphics, Font font,
            int x, int y, int width, int height,
            String text, @Nullable Integer indicatorColor, int textColor, boolean drawArrow
        );
    }
}
