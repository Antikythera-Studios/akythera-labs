package com.yesman.akytheralabs.gui.panel.entitypatch;

import com.yesman.akythera.client.gui.widget.Static;
import com.yesman.akythera.client.gui.widget.WidgetTable;
import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.WorkHistory;
import com.yesman.akytheralabs.gui.panel.AdjustablePanelManager;
import com.yesman.akytheralabs.gui.widget.ContextMenuContainer;
import com.yesman.akytheralabs.gui.widget.PanelHeader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/// "Components" panel (top-left of the entity-patch editor): a [PanelHeader] with a `+` add button,
/// over a [WidgetTable] listing one row per component the blueprint has. Determines
/// [com.yesman.akythera.core.blueprint.EntityPatchBlueprint]'s component list.
///
/// A component is distinguished by its class, so the list holds one registry id per attached
/// component type and the add menu offers only the types not already present. The `+` button opens a
/// [ContextMenuContainer] of the available
/// [com.yesman.akythera.core.registry.AkytheraRegistries.Frozen#COMPONENT_BLUEPRINT_TYPES]; a
/// right-click on a row offers "Remove". Every edit goes onto the [HistoryManager] undo stack (so the
/// title's unsaved-changes `*` tracks it) and calls [#onChanged]; nothing is written to the holder
/// until "Save".
///
/// Note on filtering: the blueprint only accepts components whose owner is `EntityPatch<Entity>`, but
/// that bound (`ComponentBlueprint<?, EntityPatch<Entity>>`) is erased at runtime, so a `MapCodec`
/// cast cannot select them (see the stage report). Instead the add menu offers only the types tagged
/// [ComponentBlueprint#ENTITY_PATCH_COMPONENT]. The tag is bound from data pack tag files
/// (`data/<namespace>/tags/component_blueprint_key/entity_patch.json`) on world load, and the editor
/// always runs with a singleplayer world loaded, so it is available here.
public final class ComponentEditorPanel extends AdjustablePanelManager.PanelArea {
    private static final int ADD_BTN = 16;
    private static final int ROW_H = 18;

    private final Font font;
    private final Screen parentScreen;

    /// Live working list of chosen component type ids (the screen's copy, flushed to the holder on
    /// save). Mutated in place so undo/redo restores the exact ordering.
    private final List<Identifier> components;
    private final Runnable onChanged;

    /// Fired when the focused (left-clicked) component changes; null = nothing focused. The screen
    /// routes this into the Details panel.
    private final Consumer<@Nullable Identifier> onComponentFocused;

    private final PanelHeader header;
    private final WidgetTable table;

    @Nullable
    private Identifier selectedId;

    @Nullable
    private ScreenRectangle lastRect;
    private int addBtnX;
    private int addBtnY;

    public ComponentEditorPanel(
        Font font,
        Screen parentScreen,
        List<Identifier> components,
        Runnable onChanged,
        Consumer<@Nullable Identifier> onComponentFocused
    ) {
        this.font = font;
        this.parentScreen = parentScreen;
        this.components = components;
        this.onChanged = onChanged;
        this.onComponentFocused = onComponentFocused;
        this.header = new PanelHeader(font, Component.literal("Components"));
        this.table = new WidgetTable(
            parentScreen,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(PanelHeader.DEFAULT_HEADER_HEIGHT), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM,
            ROW_H
        );

        addWidget(header);
        addWidget(table);

        rebuildList();
    }

    public void rebuildList() {
        table.clearWidgets();

        for (Identifier id : components) {
            table.newRow().addWidget(new Static(
                font,
                AnchoredWidget.pixel(8), AnchoredWidget.pixel(8),
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
                AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
                AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
                Component.literal(id.getPath())
            ));
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
        graphics.fill(getX(), getY(), getRight(), getBottom(), bgColor);

        // Focused-row highlight, under the table's row text.
        int selectedRow = selectedId == null ? -1 : components.indexOf(selectedId);
        if (selectedRow >= 0) {
            int rowTop = table.getY() + 2 + selectedRow * ROW_H;
            graphics.fill(getX(), rowTop, getRight(), rowTop + ROW_H, 0xFF0A2833);
        }

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
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (overAddButton(event.x(), event.y())) {
            openAddMenu(addBtnX, addBtnY + ADD_BTN);
            return true;
        }

        int row = rowIndexAt(event.y());

        if (event.button() == 1 && row >= 0) { // right-click → remove
            openRemoveMenu(components.get(row), (int) event.x(), (int) event.y());
            return true;
        }

        if (event.button() == 0 && event.y() >= table.getY()) { // left-click → focus row (or clear)
            select(row >= 0 ? components.get(row) : null);
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    private void select(@Nullable Identifier id) {
        if (Objects.equals(selectedId, id)) return;
        selectedId = id;
        onComponentFocused.accept(id);
    }

    /// Builds the add menu from every entity-patch-tagged component blueprint type not already present.
    private void openAddMenu(int x, int y) {
        List<Identifier> available = AkytheraRegistries.Frozen.COMPONENT_BLUEPRINT_TYPES.listElements()
            .filter(entry -> entry.is(ComponentBlueprint.ENTITY_PATCH_COMPONENT))
            .map(entry -> entry.key().identifier())
            .filter(id -> !components.contains(id))
            .sorted(Comparator.comparing(Identifier::toString))
            .toList();

        ContextMenuContainer.PageBuilder builder = ContextMenuContainer.builder(Minecraft.getInstance()).pageWidth(160);

        if (available.isEmpty()) {
            builder.entry().message(Component.literal("(no components available)")).onPress(_ -> {}).done();
        } else {
            for (Identifier id : available) {
                builder.entry().message(Component.literal(id.getPath())).onPress(_ -> addComponent(id)).done();
            }
        }

        AkytheraEditorLayoutManager.getInstance().openModalWidget(builder.build(), x, y);
    }

    private void openRemoveMenu(Identifier id, int x, int y) {
        ContextMenuContainer menu = ContextMenuContainer.builder(Minecraft.getInstance()).pageWidth(120)
            .entry()
                .message(Component.literal("Remove"))
                .onPress(_ -> removeComponent(id))
                .done()
            .build();

        AkytheraEditorLayoutManager.getInstance().openModalWidget(menu, x, y);
    }

    private void addComponent(Identifier id) {
        if (components.contains(id)) return;
        int index = components.size();
        components.add(id);

        HistoryManager.getInstance().pushHistory(new WorkHistory() {
            @Override
            public void undo() {
                components.remove(id);
                refresh();
            }

            @Override
            public void redo() {
                if (!components.contains(id)) components.add(Math.min(index, components.size()), id);
                refresh();
            }
        });

        refresh();
    }

    private void removeComponent(Identifier id) {
        int index = components.indexOf(id);
        if (index < 0) return;
        components.remove(index);

        HistoryManager.getInstance().pushHistory(new WorkHistory() {
            @Override
            public void undo() {
                if (!components.contains(id)) components.add(Math.min(index, components.size()), id);
                refresh();
            }

            @Override
            public void redo() {
                components.remove(id);
                refresh();
            }
        });

        refresh();
    }

    private void refresh() {
        // A removed (or undone-add) component can no longer stay focused.
        if (selectedId != null && !components.contains(selectedId)) select(null);

        rebuildList();
        onChanged.run();
    }

    private int rowIndexAt(double mouseY) {
        int bodyTop = table.getY() + 2;
        if (mouseY < bodyTop) return -1;

        int idx = (int) ((mouseY - bodyTop) / ROW_H);
        return (idx >= 0 && idx < components.size()) ? idx : -1;
    }

    private boolean overAddButton(double mouseX, double mouseY) {
        return mouseX >= addBtnX && mouseX < addBtnX + ADD_BTN
            && mouseY >= addBtnY && mouseY < addBtnY + ADD_BTN;
    }
}
