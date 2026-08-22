package com.yesman.akytheralabs.gui.panel.script;

import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.client.gui.widget.WidgetTable;
import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akythera.mixinaccessor.client.GuiGraphicsAccessor;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.AdjustablePanelManager;
import com.yesman.akytheralabs.gui.widget.AdvancedComboBox;
import com.yesman.akytheralabs.gui.widget.AdvancedEditBox;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsEditBoxTheme;
import com.yesman.akytheralabs.gui.widget.ContextMenuContainer;
import com.yesman.akytheralabs.gui.widget.PanelHeader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/// "Variables" panel: a [PanelHeader] with a `+` add button on its right, over a
/// [WidgetTable] listing one row per variable as `name | type combo`, styled after
/// Unreal's variable list.
///
/// The variable name shows as plain text (its edit box uses [AkytheraLabsEditBoxTheme#TRANSPARENT_EDIT_BOX],
/// so the box only appears while renaming); the type combo carries a type-color indicator. A single click
/// selects the row (drives the Details panel); a double-click renames inline; a right-click opens a
/// [ContextMenuContainer] with "Rename" / "Remove". Pressing a row and dragging onto the node graph drags the
/// variable out — a type-colored ghost follows the cursor and the drop is handed to the host screen via
/// [DropTarget]. All edits route through the shared [VariableEditController].
public final class VariablePanel extends AdjustablePanelManager.PanelArea {
    private static final int ADD_BTN = 16;
    /// Screen-pixel slop a press may move before it becomes a drag instead of a click.
    private static final int DRAG_THRESHOLD = 4;

    /// Handed a variable released over the canvas so the host screen can spawn a Get / Set node.
    @FunctionalInterface
    public interface DropTarget {
        /// @return true when the drop landed on a valid target (e.g. the node graph).
        boolean onDropped(VariableDraft draft, double screenX, double screenY);
    }

    private final Font font;
    private final VariableEditController controller;
    private final PanelHeader header;
    private final WidgetTable table;

    /// Name boxes by draft, so "Rename" can focus the right row's box for inline editing.
    private final Map<VariableDraft, AdvancedEditBox<String>> nameBoxes = new HashMap<>();
    /// Type combos by draft, used to tell a name-area press (drag / rename) from a combo press.
    private final Map<VariableDraft, AdvancedComboBox<ScriptType>> typeCombos = new HashMap<>();

    @Nullable
    private DropTarget dropTarget;

    /// Drag-to-graph state. `pressDraft` is the row a left-press began on (armed but not yet a
    /// drag); once the cursor moves past [#DRAG_THRESHOLD] it promotes to `draggingDraft` and a
    /// ghost follows the cursor. `dragX/Y` track the cursor for the ghost.
    @Nullable
    private VariableDraft pressDraft;
    @Nullable
    private VariableDraft draggingDraft;
    private double pressX;
    private double pressY;
    private double dragX;
    private double dragY;

    @Nullable
    private ScreenRectangle lastRect;
    private int addBtnX;
    private int addBtnY;

    public VariablePanel(Font font, Screen parentScreen, VariableEditController controller) {
        this.font = font;
        this.controller = controller;
        this.header = new PanelHeader(font, Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_PANEL_VARIABLES));
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

    /// Wires the callback consulted when a variable is dropped outside the panel (over the graph).
    public void setDropTarget(@Nullable DropTarget dropTarget) {
        this.dropTarget = dropTarget;
    }

    public void rebuildList() {
        table.clearWidgets();
        nameBoxes.clear();
        typeCombos.clear();

        for (VariableDraft draft : controller.drafts()) {
            AdvancedEditBox<String> nameBox = controller.nameBox(
                draft,
                AnchoredWidget.pixel(6), AnchoredWidget.fraction(50),
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(VariableEditController.ROW_H),
                AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH,
                AnchoredWidget.VerticalAnchorType.TOP_HEIGHT
            );
            AdvancedComboBox<ScriptType> typeCombo = controller.typeCombo(
                draft,
                AnchoredWidget.pixel(6), AnchoredWidget.fraction(44),
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(VariableEditController.ROW_H),
                AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH,
                AnchoredWidget.VerticalAnchorType.TOP_HEIGHT
            );

            nameBoxes.put(draft, nameBox);
            typeCombos.put(draft, typeCombo);
            table.newRow().addWidget(nameBox).addWidget(typeCombo);
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

        addBtnX = screenRectangle.right() - ADD_BTN - 4;
        addBtnY = screenRectangle.top() + (PanelHeader.DEFAULT_HEADER_HEIGHT - ADD_BTN) / 2;
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        // Rendered manually (rather than via super) so the selection / hover band sits *behind*
        // the row content — the name text and type combo draw on top and stay readable.
        graphics.fill(getX(), getY(), getRight(), getBottom(), bgColor);
        drawRowHighlights(graphics, mouseX, mouseY);
        header.extractRenderState(graphics, mouseX, mouseY, a);
        table.extractRenderState(graphics, mouseX, mouseY, a);

        boolean hovered = overAddButton(mouseX, mouseY);
        graphics.fill(addBtnX, addBtnY, addBtnX + ADD_BTN, addBtnY + ADD_BTN, hovered ? 0xFF3A3A3A : 0xFF2A2A2A);
        graphics.text(
            font, Component.literal("+"),
            addBtnX + (ADD_BTN - font.width("+")) / 2,
            addBtnY + (ADD_BTN - font.lineHeight) / 2,
            0xFFCCCCCC, false
        );

        // The drag ghost is deferred to the frame's tail so it draws on top of every panel —
        // including the node graph the variable is being dragged onto.
        if (draggingDraft != null) {
            VariableDraft ghost = draggingDraft;
            ((GuiGraphicsAccessor) graphics).akythera$setDeferredWidgetRenderer(() -> drawDragGhost(graphics, ghost));
        }
    }

    /// Draws the selection band (blue-grey) for the selected row and a lighter hover band for the
    /// row under the cursor, both behind the row content.
    private void drawRowHighlights(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        List<VariableDraft> drafts = controller.drafts();
        VariableDraft selected = controller.getSelected();
        int bodyTop = table.getY() + 2;

        for (int i = 0; i < drafts.size(); i++) {
            int ry = bodyTop + i * VariableEditController.ROW_H;
            if (ry >= getBottom()) break;
            int rb = Math.min(ry + VariableEditController.ROW_H, getBottom());

            boolean isSelected = drafts.get(i) == selected;
            boolean isHovered = !isSelected
                && mouseX >= table.getX() && mouseX < table.getRight()
                && mouseY >= Math.max(ry, bodyTop) && mouseY < rb;

            if (isSelected) {
                graphics.fill(table.getX(), ry, table.getRight(), rb, 0xFF2F3B47);
            } else if (isHovered) {
                graphics.fill(table.getX(), ry, table.getRight(), rb, 0xFF2A2A2A);
            }
        }
    }

    /// Draws the dragged variable's ghost near the cursor: a small node-like capsule with the
    /// type-color swatch and the variable name (mirrors the dropped node's header).
    private void drawDragGhost(GuiGraphicsExtractor graphics, VariableDraft draft) {
        String name = draft.getName();
        int color = VariableEditController.indicatorColor(draft.getType());

        int pad = 6, pillW = 14, pillH = 8, gap = 6, boxH = 18;
        int boxW = pad + pillW + gap + font.width(name) + pad;
        int bx = (int) dragX + 8;
        int by = (int) dragY + 8;

        graphics.fill(bx - 1, by - 1, bx + boxW + 1, by + boxH + 1, 0xAA000000);
        graphics.fill(bx, by, bx + boxW, by + boxH, 0xFF2B2B2B);
        graphics.outline(bx, by, boxW, boxH, 0xFF555555);

        int px = bx + pad;
        int py = by + boxH / 2 - pillH / 2;
        graphics.fill(px + 1, py, px + pillW - 1, py + pillH, color);
        graphics.fill(px, py + 1, px + pillW, py + pillH - 1, color);

        graphics.text(
            font, Component.literal(name),
            px + pillW + gap, by + boxH / 2 - font.lineHeight / 2 + 1,
            0xFFFFFFFF, false
        );
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (overAddButton(event.x(), event.y())) {
            controller.addVariable();
            return true;
        }

        int row = rowIndexAt(event.y());

        if (event.button() == 1) { // right-click → context menu
            if (row >= 0) {
                VariableDraft draft = controller.drafts().get(row);
                controller.select(draft);
                openContextMenu(draft, (int) event.x(), (int) event.y());
            }
            return true;
        }

        if (event.button() != 0 || row < 0) {
            return false;
        }

        VariableDraft draft = controller.drafts().get(row);
        controller.select(draft);

        AdvancedComboBox<ScriptType> combo = typeCombos.get(draft);
        AdvancedEditBox<String> box = nameBoxes.get(draft);

        // A press on the type combo opens its dropdown; a press on a name box already being
        // edited repositions its cursor — both go to the child widget via super.
        if ((combo != null && overWidget(combo, event.x(), event.y()))
            || (box != null && box.isFocused())) {
            return super.mouseClicked(event, doubleClick);
        }

        if (doubleClick) {
            pressDraft = null;
            beginRename(draft);
            return true;
        }

        // Single press on the name / row body: arm a potential drag to the graph.
        pressDraft = draft;
        pressX = event.x();
        pressY = event.y();
        dragX = event.x();
        dragY = event.y();
        return true;
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
        if (draggingDraft != null) {
            dragX = event.x();
            dragY = event.y();
            return true;
        }

        if (pressDraft != null) {
            dragX = event.x();
            dragY = event.y();

            if (Math.abs(event.x() - pressX) + Math.abs(event.y() - pressY) >= DRAG_THRESHOLD) {
                draggingDraft = pressDraft;
                pressDraft = null;
            }

            return true;
        }

        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        if (draggingDraft != null) {
            VariableDraft dropped = draggingDraft;
            draggingDraft = null;
            pressDraft = null;

            if (dropTarget != null) {
                dropTarget.onDropped(dropped, event.x(), event.y());
            }

            return true;
        }

        pressDraft = null;
        return super.mouseReleased(event);
    }

    /// Whether `(x, y)` is inside `widget`'s laid-out bounds.
    private static boolean overWidget(AbstractWidget widget, double x, double y) {
        return x >= widget.getX() && x < widget.getX() + widget.getWidth()
            && y >= widget.getY() && y < widget.getY() + widget.getHeight();
    }

    private void openContextMenu(VariableDraft draft, int x, int y) {
        ContextMenuContainer menu = ContextMenuContainer.builder(Minecraft.getInstance()).pageWidth(120)
            .entry()
                .message(Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_VARIABLE_RENAME))
                .onPress(_ -> beginRename(draft))
                .done()
            .entry()
                .message(Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_VARIABLE_REMOVE))
                .onPress(_ -> controller.removeVariable(draft))
                .done()
            .build();

        AkytheraEditorLayoutManager.getInstance().openModalWidget(menu, x, y);
    }

    /// Focuses the variable's name box so the user can type a new name inline,
    /// mirroring the explorer's directory rename.
    private void beginRename(VariableDraft draft) {
        controller.select(draft);

        AdvancedEditBox<String> box = nameBoxes.get(draft);
        int index = controller.drafts().indexOf(draft);
        if (box == null || index < 0 || index >= table.children().size()) return;

        GuiEventListener entry = table.children().get(index);
        setFocused(table);
        table.setFocused(entry);
        if (entry instanceof ContainerEventHandler container) {
            container.setFocused(box);
        }
        box.setFocused(true);
        box.setCursorPosRightmost();
    }

    private int rowIndexAt(double mouseY) {
        int bodyTop = table.getY() + 2;
        if (mouseY < bodyTop) return -1;

        int idx = (int) ((mouseY - bodyTop) / VariableEditController.ROW_H);
        return (idx >= 0 && idx < controller.drafts().size()) ? idx : -1;
    }

    private boolean overAddButton(double mouseX, double mouseY) {
        return mouseX >= addBtnX && mouseX < addBtnX + ADD_BTN
            && mouseY >= addBtnY && mouseY < addBtnY + ADD_BTN;
    }
}
