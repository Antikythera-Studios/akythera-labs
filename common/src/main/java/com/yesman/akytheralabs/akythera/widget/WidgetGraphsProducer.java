package com.yesman.akytheralabs.akythera.widget;

import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.client.api.gui.AkytheraButton;
import com.yesman.akythera.client.api.gui.WidgetTable;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.akythera.blueprint.BlueprintSignature;
import com.yesman.akytheralabs.editor.EditorWidgetFactory;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.screen.WidgetEditorScreen;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsButtonTheme;
import net.minecraft.client.gui.components.AbstractWidget;

import java.util.List;
import java.util.Map;

/// The Details rows of a widget's [WidgetGraphs]: a header, then one row per event and binding of the widget. Its
/// button opens the graph's canvas on the [WidgetEditorScreen]: `+` while the widget has none, `Open` once it has.
public final class WidgetGraphsProducer implements EditorWidgetFactory.WidgetProducer {
    /// Nothing for a caller with one row to give: the rows need a table.
    @Override
    public List<? extends AbstractWidget> produce(final EditorWidgetFactory factory, final EditorWidgetFactory.ValueSlot slot) {
        return List.of();
    }

    @Override
    public void appendRows(final EditorWidgetFactory factory, final WidgetTable table, final EditorWidgetFactory.ValueSlot slot, final int indent) {
        if (!(slot.owner() instanceof WidgetComponentHolder<?> widget)) return;

        Map<String, BlueprintSignature> signatures = widget.graphSignatures();

        if (signatures.isEmpty()) return;

        table.newRow(indent, EditorWidgetFactory.makeTranslatableLabel(factory.font(), LangKeys.GUI_WIDGET_SCREEN_ROW_GRAPHS));

        signatures.forEach((name, signature) -> {
            String label = widget.getGraphs().map().containsKey(name) ? LangKeys.GUI_WIDGET_SCREEN_BUTTON_OPEN_GRAPH : LangKeys.GUI_WIDGET_SCREEN_BUTTON_ADD_GRAPH;
            AkytheraButton button = AkytheraButton.buttonBuilder(factory.font(), LocalizedMessage.translatable(label), () -> {
                    if (AkytheraEditorLayoutManager.getInstance().getRecentEditorScreen() instanceof WidgetEditorScreen screen) {
                        screen.openWidgetGraph(widget, name);
                    }
                })
                .xUnits(EditorWidgetFactory.columnStart(slot, 0, 1), EditorWidgetFactory.columnSize(slot, 0, 1))
                .yUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(EditorWidgetFactory.ROW_H))
                .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH)
                .verticalAnchorType(AnchoredWidget.VerticalAnchorType.TOP_HEIGHT)
                .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
                .build();

            table.newRow(indent, EditorWidgetFactory.makeLabel(factory.font(), signature.title()));
            table.addWidget(button);
        });
    }
}
