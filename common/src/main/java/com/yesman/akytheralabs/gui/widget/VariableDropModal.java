package com.yesman.akytheralabs.gui.widget;

import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.gui.widget.script.ScriptVariableGetterNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.ScriptVariableSetterNodeWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/// Tiny chooser opened where a variable is dropped on the node graph: a faded header naming the variable,
/// then a "Get <var>" and (where execution flow exists) a "Set <var>" row. Picking one spawns the matching
/// [ScriptVariableGetterNodeWidget] / [ScriptVariableSetterNodeWidget] at the drop point.
///
/// A data-flow-only canvas (the Anim Graph, a state's pose graph, a transition's condition graph) has
/// no execution flow, so it uses the get-only constructor and the Set row is omitted.
///
/// Mirrors UE's drag-a-variable-onto-the-graph popup. Closes on an outside click or Escape.
public final class VariableDropModal extends AbstractWidget implements Modal {
    private static final int WIDTH = 160;
    private static final int HEADER_H = 18;
    private static final int ROW_H = 18;
    private static final int PAD = 3;

    private final Font font;
    private final String headerText;
    private final Component getLabel;
    @Nullable
    private final Component setLabel;
    private final Runnable onGet;
    @Nullable
    private final Runnable onSet;

    /// Get-only chooser, for a data-flow-only canvas where a Set node would have no execution flow.
    public VariableDropModal(Font font, String variableName, Component getLabel, Runnable onGet) {
        this(font, variableName, getLabel, null, onGet, null);
    }

    public VariableDropModal(
        Font font, String variableName,
        Component getLabel, @Nullable Component setLabel,
        Runnable onGet, @Nullable Runnable onSet
    ) {
        super(0, 0, WIDTH, HEADER_H + ROW_H * (onSet != null ? 2 : 1) + PAD * 2, Component.literal(variableName));

        this.font = font;
        this.headerText = variableName.toUpperCase(Locale.ROOT);
        this.getLabel = getLabel;
        this.setLabel = setLabel;
        this.onGet = onGet;
        this.onSet = onSet;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        // Cover the screen so any click is intercepted (close-on-outside).
        return isActive();
    }

    private int getRowTop() {
        return getY() + HEADER_H + PAD;
    }

    private int setRowTop() {
        return getRowTop() + ROW_H;
    }

    private boolean overPage(double x, double y) {
        return x >= getX() && x < getX() + getWidth() && y >= getY() && y < getY() + getHeight();
    }

    private boolean overRow(double x, double y, int rowTop) {
        return x >= getX() && x < getX() + getWidth() && y >= rowTop && y < rowTop + ROW_H;
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (!overPage(event.x(), event.y())) {
            AkytheraEditorLayoutManager.getInstance().closeModal();
            return true;
        }

        if (overRow(event.x(), event.y(), getRowTop())) {
            AkytheraEditorLayoutManager.getInstance().closeModal();
            onGet.run();
            return true;
        }

        if (onSet != null && overRow(event.x(), event.y(), setRowTop())) {
            AkytheraEditorLayoutManager.getInstance().closeModal();
            onSet.run();
            return true;
        }

        return true;
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (event.isEscape()) {
            AkytheraEditorLayoutManager.getInstance().closeModal();
            return true;
        }

        return false;
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();

        graphics.fill(x, y, x + w, y + h, 0xFF1E1E1E);
        graphics.outline(x, y, w, h, 0xFF3A3A3A);

        // Header: a faded variable name with a thin rule trailing it.
        int headerY = y + (HEADER_H - font.lineHeight) / 2;
        graphics.text(font, Component.literal(headerText), x + 8, headerY, 0xFF777777, false);
        int ruleX = x + 8 + font.width(headerText) + 6;
        int ruleY = y + HEADER_H / 2;
        if (ruleX < x + w - 8) {
            graphics.fill(ruleX, ruleY, x + w - 8, ruleY + 1, 0xFF444444);
        }

        drawRow(graphics, getRowTop(), getLabel, overRow(mouseX, mouseY, getRowTop()));

        if (setLabel != null) {
            drawRow(graphics, setRowTop(), setLabel, overRow(mouseX, mouseY, setRowTop()));
        }
    }

    private void drawRow(GuiGraphicsExtractor graphics, int rowTop, Component label, boolean hovered) {
        if (hovered) {
            graphics.fill(getX() + 1, rowTop, getX() + getWidth() - 1, rowTop + ROW_H, 0xFF333333);
        }

        graphics.text(
            font, label,
            getX() + 14, rowTop + (ROW_H - font.lineHeight) / 2,
            hovered ? 0xFFFFFFFF : 0xFFCCCCCC, false
        );
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput narrationElementOutput) {
    }
}
