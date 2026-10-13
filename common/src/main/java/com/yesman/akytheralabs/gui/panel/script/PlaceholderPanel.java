package com.yesman.akytheralabs.gui.panel.script;

import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akytheralabs.gui.panel.AdjustablePanelManager;
import com.yesman.akytheralabs.gui.widget.PanelHeader;
import com.yesman.akytheralabs.gui.widget.script.PlaceholderContent;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

/// Design-stage panel: a titled [PanelHeader] over a centered hint line. Stands in
/// for the Animator Script editor's not-yet-implemented side panels (Variables,
/// Node Details, Asset Browser) so the screen lays out as drawn while no editing
/// behaviour exists.
public final class PlaceholderPanel extends AdjustablePanelManager.PanelArea {
    private final PanelHeader header;
    private final PlaceholderContent body;

    public PlaceholderPanel(Font font, Component title, Component hint) {
        this.header = new PanelHeader(font, title);
        this.body = new PlaceholderContent(
            font,
            hint,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(PanelHeader.DEFAULT_HEADER_HEIGHT), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM
        );

        addWidget(header);
        addWidget(body);
    }

    @Override
    protected int contentHeight() {
        return getHeight();
    }

    @Override
    protected void onPanelResized(ScreenRectangle screenRectangle) {
        header.relocate(screenRectangle);
        body.relocate(screenRectangle);
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput narrationElementOutput) {
    }
}
