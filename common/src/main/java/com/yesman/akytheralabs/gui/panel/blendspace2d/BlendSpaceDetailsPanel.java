package com.yesman.akytheralabs.gui.panel.blendspace2d;

import com.yesman.akythera.client.gui.widget.WidgetTable;
import com.yesman.akytheralabs.gui.panel.AdjustablePanelManager;
import com.yesman.akytheralabs.gui.widget.PanelHeader;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/// Details panel for the BlendSpace2D editor.
/// Shows global asset settings (editable via the passed WidgetTable) and
/// the selected node's properties (read-only, rebuilt on selection change).
public final class BlendSpaceDetailsPanel extends AdjustablePanelManager.PanelArea {
    private final PanelHeader header;
    private final WidgetTable blendSpaceDetailsTable;

    public BlendSpaceDetailsPanel(Font font, WidgetTable blendSpaceDetailsTable) {
        this.header = new PanelHeader(font, Component.translatable(LangKeys.GUI_WIDGET_DETAILS));
        this.blendSpaceDetailsTable = blendSpaceDetailsTable;

        addWidget(header);
        addWidget(blendSpaceDetailsTable);
    }

    @Override
    protected int contentHeight() {
        return blendSpaceDetailsTable.getHeight();
    }

    @Override
    protected void onPanelResized(ScreenRectangle screenRectangle) {
        blendSpaceDetailsTable.relocate(screenRectangle);
        header.relocate(screenRectangle);
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput narration) {
    }
}