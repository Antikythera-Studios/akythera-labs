package com.yesman.akytheralabs.gui.panel.script;

import com.yesman.akythera.client.api.gui.ContextMenuContainer;
import com.yesman.akythera.client.api.gui.Static;
import com.yesman.akythera.client.api.gui.WidgetTable;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.AdjustablePanelManager;
import com.yesman.akytheralabs.gui.widget.PanelHeader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;

/// "Functions" panel: a [PanelHeader] with an `Override` and a `+` button on its right, over one row per
/// function the blueprint overrides or declares, styled after Unreal's function list.
///
/// A click opens the function's canvas and selects it, and a right-click opens a [ContextMenuContainer] with
/// "Open" / "Remove". `Override` lists the parent's functions not overridden yet, and `+` declares a new
/// one. Every edit routes through the [BlueprintEditorSession].
public final class FunctionPanel extends AdjustablePanelManager.PanelArea {
    private static final int BUTTON_H = 16;
    private static final int BUTTON_PAD = 4;
    private static final int OVERRIDE_TEXT_COLOR = 0xFFE0C070;
    private static final int FUNCTION_TEXT_COLOR = 0xFFCCCCCC;
    private static final int SELECTED_ROW_COLOR = 0xFF2F3B47;
    private static final int HOVERED_ROW_COLOR = 0xFF2A2A2A;

    private final Font font;
    private final BlueprintEditorSession session;
    private final PanelHeader header;
    private final WidgetTable table;
    private final Component overrideLabel = Component.translatable(LangKeys.GUI_BLUEPRINT_PANEL_OVERRIDE);

    /// The function each row lists, in row order.
    private List<String> rows = List.of();

    @Nullable
    private ScreenRectangle lastRect;

    private int addButtonX;
    private int overrideButtonX;
    private int buttonY;

    public FunctionPanel(Font font, Screen parentScreen, BlueprintEditorSession session) {
        this.font = font;
        this.session = session;
        this.header = new PanelHeader(font, Component.translatable(LangKeys.GUI_BLUEPRINT_PANEL_FUNCTIONS));
        this.table = new WidgetTable(
            parentScreen,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(PanelHeader.DEFAULT_HEADER_HEIGHT), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM,
            VariableEditController.ROW_H
        );

        addWidget(header);
        addWidget(table);

        rebuildList();
    }

    public void rebuildList() {
        table.clearWidgets();
        rows = session.functionNames();

        for (String name : rows) {
            Static label = new Static(
                font,
                AnchoredWidget.pixel(6), AnchoredWidget.pixel(6),
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(VariableEditController.ROW_H),
                AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
                AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
                Component.literal(ParseUtils.camelToSpacedPascal(name))
            );

            label.setColor(session.isOverride(name) ? OVERRIDE_TEXT_COLOR : FUNCTION_TEXT_COLOR);
            table.newRow().addWidget(label);
        }

        table.initialize(false);
        if (lastRect != null) table.relocate(lastRect);
    }

    @Override
    protected int contentHeight() {
        return getHeight();
    }

    @Override
    protected void onPanelResized(ScreenRectangle screenRectangle) {
        lastRect = screenRectangle;
        header.relocate(screenRectangle);
        table.relocate(screenRectangle);

        addButtonX = screenRectangle.right() - BUTTON_H - BUTTON_PAD;
        overrideButtonX = addButtonX - BUTTON_PAD - (font.width(overrideLabel) + BUTTON_PAD * 2);
        buttonY = screenRectangle.top() + (PanelHeader.DEFAULT_HEADER_HEIGHT - BUTTON_H) / 2;
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        // The selection and hover bands sit behind the row labels
        graphics.fill(getX(), getY(), getRight(), getBottom(), bgColor);

        int bodyTop = table.getY() + 2;
        int hovered = rowIndexAt(mouseY);

        for (int i = 0; i < rows.size(); i++) {
            int top = bodyTop + i * VariableEditController.ROW_H;
            if (top >= getBottom()) break;

            int bottom = Math.min(top + VariableEditController.ROW_H, getBottom());

            if (rows.get(i).equals(session.selectedFunction())) {
                graphics.fill(table.getX(), top, table.getRight(), bottom, SELECTED_ROW_COLOR);
            } else if (i == hovered && mouseX >= table.getX() && mouseX < table.getRight()) {
                graphics.fill(table.getX(), top, table.getRight(), bottom, HOVERED_ROW_COLOR);
            }
        }

        header.extractRenderState(graphics, mouseX, mouseY, a);
        table.extractRenderState(graphics, mouseX, mouseY, a);

        int overrideW = font.width(overrideLabel) + BUTTON_PAD * 2;

        graphics.fill(overrideButtonX, buttonY, overrideButtonX + overrideW, buttonY + BUTTON_H,
            over(mouseX, mouseY, overrideButtonX, overrideW) ? 0xFF3A3A3A : 0xFF2A2A2A);
        graphics.text(font, overrideLabel, overrideButtonX + BUTTON_PAD, buttonY + (BUTTON_H - font.lineHeight) / 2, 0xFFCCCCCC, false);

        graphics.fill(addButtonX, buttonY, addButtonX + BUTTON_H, buttonY + BUTTON_H,
            over(mouseX, mouseY, addButtonX, BUTTON_H) ? 0xFF3A3A3A : 0xFF2A2A2A);
        graphics.text(
            font, Component.literal("+"),
            addButtonX + (BUTTON_H - font.width("+")) / 2, buttonY + (BUTTON_H - font.lineHeight) / 2,
            0xFFCCCCCC, false
        );
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (over(event.x(), event.y(), addButtonX, BUTTON_H)) {
            session.addFunction();
            return true;
        }

        if (over(event.x(), event.y(), overrideButtonX, font.width(overrideLabel) + BUTTON_PAD * 2)) {
            ContextMenuContainer.PageBuilder menu = ContextMenuContainer.builder(Minecraft.getInstance()).pageWidth(140);
            List<String> overridable = session.overridableFunctions();

            if (overridable.isEmpty()) {
                menu.entry()
                    .message(Component.translatable(LangKeys.GUI_BLUEPRINT_MENU_NOTHING_TO_OVERRIDE))
                    .onPress(_ -> {})
                    .done();
            }

            for (String name : overridable) {
                menu.entry()
                    .message(Component.literal(ParseUtils.camelToSpacedPascal(name)))
                    .onPress(_ -> session.overrideFunction(name))
                    .done();
            }

            ModalManager.getInstance().openModalWidget(menu.build(), (int) event.x(), (int) event.y());
            return true;
        }

        int row = rowIndexAt(event.y());

        if (row < 0) return super.mouseClicked(event, doubleClick);

        String name = rows.get(row);

        if (event.button() == 1) {
            ContextMenuContainer menu = ContextMenuContainer.builder(Minecraft.getInstance()).pageWidth(120)
                .entry()
                    .message(Component.translatable(LangKeys.GUI_BLUEPRINT_MENU_OPEN))
                    .onPress(_ -> session.openFunction(name))
                    .done()
                .entry()
                    .message(Component.translatable(LangKeys.GUI_BLUEPRINT_MENU_REMOVE))
                    .onPress(_ -> session.removeFunction(name))
                    .done()
                .build();

            ModalManager.getInstance().openModalWidget(menu, (int) event.x(), (int) event.y());
            return true;
        }

        if (event.button() == 0) {
            session.openFunction(name);
            return true;
        }

        return false;
    }

    private boolean over(double mouseX, double mouseY, int buttonX, int buttonW) {
        return mouseX >= buttonX && mouseX < buttonX + buttonW && mouseY >= buttonY && mouseY < buttonY + BUTTON_H;
    }

    private int rowIndexAt(double mouseY) {
        int bodyTop = table.getY() + 2;
        if (mouseY < bodyTop) return -1;

        int index = (int) ((mouseY - bodyTop) / VariableEditController.ROW_H);
        return index >= 0 && index < rows.size() ? index : -1;
    }
}
