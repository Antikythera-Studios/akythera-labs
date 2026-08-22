package com.yesman.akytheralabs.gui.panel.components;

import com.yesman.akythera.client.gui.widget.WidgetTable;
import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.editor.EditorWidgetFactory;
import com.yesman.akytheralabs.gui.panel.AdjustablePanelManager;
import com.yesman.akytheralabs.gui.widget.PanelHeader;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/// Right-hand "Details" panel of the entity-patch editor: shows the focused component's [EditorEditable]
/// fields as [EditorWidgetFactory]-generated rows (asset accessors and registry keys become search
/// PopupBoxes, booleans become checkboxes, ...). Nested editor objects and `List` fields render inline, one
/// indent level per depth — the rendering (and list add/remove history) lives in the factory; this panel
/// supplies the rebuild callback and the placeholder look (a centered grey "Details" label when nothing is
/// focused).
public final class ComponentDetailsPanel extends AdjustablePanelManager.PanelArea {
    private static final Component TITLE = Component.literal("Details");
    private static final int ROW_H = 20;

    private final Font font;
    private final PanelHeader header;
    private final WidgetTable table;
    private final EditorWidgetFactory factory;

    @Nullable
    private Object shown;

    /// What [#shown] hangs off, handed to the factory as the rows' context and kept for the rebuilds.
    @Nullable
    private Object shownContext;

    @Nullable
    private ScreenRectangle lastRect;

    public ComponentDetailsPanel(Font font, Screen parentScreen) {
        this.font = font;
        this.header = new PanelHeader(font, TITLE);
        this.factory = new EditorWidgetFactory(font);
        this.table = new WidgetTable(
            parentScreen,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(PanelHeader.DEFAULT_HEADER_HEIGHT + 2), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM,
            ROW_H
        );

        addWidget(header);
        addWidget(table);
    }

    /// Fills the table with `componentHolder`'s editor rows, or clears back to the placeholder
    /// when null (no component focused / component without editor support).
    public void showComponent(@Nullable Object componentHolder) {
        showComponent(componentHolder, null);
    }

    /// Variant carrying what the shown holder hangs off — its host in the component tree. Rows whose
    /// candidates come from their surroundings read it ([EditorWidgetFactory#fillWidgetTable]), which
    /// is how a bone socket lists the bones of its parent component's armature.
    public void showComponent(@Nullable Object componentHolder, @Nullable Object context) {
        shown = componentHolder;
        shownContext = context;

        if (componentHolder == null) {
            table.clearWidgets();
        } else {
            factory.fillWidgetTable(table, componentHolder, () -> refreshIfShown(componentHolder), context);
        }

        if (lastRect != null) table.relocate(lastRect);
    }

    /// Section add/remove (and its undo/redo replay) rebuilds the rows only when this holder is
    /// still the one on display.
    private void refreshIfShown(Object owner) {
        if (shown == owner) showComponent(owner, shownContext);
    }

    @Override
    protected void onPanelResized(ScreenRectangle screenRectangle) {
        lastRect = screenRectangle;
        header.relocate(screenRectangle);
        table.relocate(screenRectangle);
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.fill(getX(), getY(), getRight(), getBottom(), bgColor);
        header.extractRenderState(graphics, mouseX, mouseY, a);
        table.extractRenderState(graphics, mouseX, mouseY, a);

        if (shown == null) {
            int bodyTop = getY() + PanelHeader.DEFAULT_HEADER_HEIGHT;
            int cx = getX() + (getWidth() - font.width(TITLE)) / 2;
            int cy = bodyTop + (getBottom() - bodyTop - font.lineHeight) / 2;
            graphics.text(font, TITLE, cx, cy, 0xFF666666, false);
        }
    }
}