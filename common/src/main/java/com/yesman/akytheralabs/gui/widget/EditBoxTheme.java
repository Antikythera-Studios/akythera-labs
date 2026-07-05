package com.yesman.akytheralabs.gui.widget;

import com.yesman.akythera.client.gui.widget.common.WidgetTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/// Enum interface for [AdvancedEditBox] background styles.
///
/// Mirrors the [com.yesman.akythera.client.gui.widget.ButtonTheme] pattern.
/// Implementations are registered to [WidgetTheme#ENUM_MANAGER].
public interface EditBoxTheme extends WidgetTheme {
    void renderBackground(AdvancedEditBox<?> widget, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a);

    @FunctionalInterface
    interface EditBoxRenderer {
        void renderBackground(AdvancedEditBox<?> widget, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a);
    }
}