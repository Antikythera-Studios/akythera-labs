package com.yesman.akytheralabs.akythera.widget;

import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.client.api.gui.AkytheraButton;
import com.yesman.akythera.client.api.gui.AkytheraWidget;
import com.yesman.akythera.client.api.gui.BlueprintWidgets;
import com.yesman.akythera.client.api.gui.WidgetTable;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.core.blueprint.widget.WidgetLayout;
import com.yesman.akytheralabs.akythera.entitypatch.component.ComponentAttachableHolder;
import com.yesman.akytheralabs.editor.EditorWidgetFactory;
import com.yesman.akytheralabs.editor.historytrack.DataChange;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsButtonTheme;
import com.yesman.akytheralabs.gui.widget.AnchorSelectModal;
import com.yesman.akytheralabs.gui.widget.EditorComboBox;
import com.yesman.akytheralabs.gui.widget.EditorEditBox;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/// The Details rows of a widget's [WidgetLayoutHolder]: the anchors as one combo opening an [AnchorSelectModal],
/// then X1 / X2 / Y1 / Y2 as a number and a `px` / `%` combo each, labelled by what the anchor types make of them.
/// A widget holding widgets ends with buttons aligning every one of them.
///
/// An anchor pick and a `px` / `%` switch keep the widget where the canvas last laid it out
/// ([WidgetLayoutHolder#getPlacement]). Every edit is one undo step.
public final class WidgetLayoutProducer implements EditorWidgetFactory.WidgetProducer {
    private static final int KIND_W = 36; // the px / % combo

    private static final List<AnchorSelectModal.Anchors> EVERY_ANCHORS = Arrays.stream(WidgetLayout.Vertical.values())
        .flatMap(vertical -> Arrays.stream(WidgetLayout.Horizontal.values()).map(horizontal -> new AnchorSelectModal.Anchors(horizontal, vertical)))
        .toList();

    /// The anchors combo alone, for a caller with one row to give.
    @Override
    public List<? extends AbstractWidget> produce(final EditorWidgetFactory factory, final EditorWidgetFactory.ValueSlot slot) {
        return List.of(anchorsCombo(factory, slot));
    }

    @Override
    public void appendRows(final EditorWidgetFactory factory, final WidgetTable table, final EditorWidgetFactory.ValueSlot slot, final int indent) {
        boolean locked = factory.isLocked(slot);
        WidgetLayout layout = layoutOf(slot);
        EditorComboBox<AnchorSelectModal.Anchors> anchors = anchorsCombo(factory, slot);

        if (locked) EditorWidgetFactory.lock(anchors);

        table.newRow(indent, EditorWidgetFactory.makeTranslatableLabel(factory.font(), LangKeys.GUI_WIDGET_SCREEN_ROW_ANCHORS)).addWidget(anchors);

        // A linear container sizes itself from its children, so a child's length along its axis is in pixels
        ComponentAttachableHolder host = factory.context() instanceof ComponentAttachableHolder root
            ? Stream.concat(Stream.of(root), ComponentAttachableHolder.flatten(root).filter(ComponentAttachableHolder.class::isInstance).map(ComponentAttachableHolder.class::cast))
                .filter(candidate -> candidate.getComponents().values().stream().anyMatch(child -> child == slot.owner()))
                .findFirst()
                .orElse(null)
            : null;
        int pixelOnly = host instanceof LinearContainerHolder container ? (container.vertical() ? 3 : 1) : -1;

        for (int index = 0; index < 4; index++) {
            int axis = index;
            boolean offset = isOffset(layout, axis);
            WidgetLayout.Unit unit = unitAt(layout, axis);
            AnchoredWidget.Unit start = EditorWidgetFactory.columnStart(slot, 0, 1);
            AnchoredWidget.Unit size = EditorWidgetFactory.columnSize(slot, 0, 1);

            EditorEditBox<Integer> value = new EditorEditBox<>(
                factory.font(),
                start, (rect, xy) -> size.pixels(rect, xy) - KIND_W - EditorWidgetFactory.COLUMN_GAP,
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(EditorWidgetFactory.ROW_H),
                AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH,
                AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
                () -> unitAt(layoutOf(slot), axis).value(),
                typed -> {
                    WidgetLayout current = layoutOf(slot);
                    boolean fraction = unitAt(current, axis).fraction();

                    ((WidgetLayoutHolder) slot.get()).readFrom(withUnit(current, axis, new WidgetLayout.Unit(clamp(typed, fraction, isOffset(current, axis)), fraction)));
                },
                text -> text.matches(offset ?"-?\\d*" : "\\d*"),
                Integer::parseInt,
                String::valueOf,
                Component.empty()
            );

            // Typing writes through, so the canvas follows; the commit is the undo step from the value before
            value.setOnCommitted((before, _) -> {
                WidgetLayout current = layoutOf(slot);

                commit(slot, withUnit(current, axis, new WidgetLayout.Unit(before, unitAt(current, axis).fraction())), current);
            });

            EditorComboBox<Boolean> kind = new EditorComboBox<>(
                factory.font(),
                (rect, xy) -> start.pixels(rect, xy) + size.pixels(rect, xy) - KIND_W, AnchoredWidget.pixel(KIND_W),
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(EditorWidgetFactory.ROW_H),
                AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH,
                AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
                () -> unitAt(layoutOf(slot), axis).fraction(),
                fraction -> {
                    WidgetLayout current = layoutOf(slot);
                    WidgetLayout.Unit from = unitAt(current, axis);

                    if (fraction == null || fraction == from.fraction()) return;

                    WidgetLayoutHolder.Placement placement = ((WidgetLayoutHolder) slot.get()).getPlacement();
                    int length = placement == null ? 0 : axis < 2 ? placement.area().width() : placement.area().height();
                    int converted = length <= 0 ? from.value() : fraction ? Math.round(from.value() * 100.0F / length) : Math.round(from.value() * length / 100.0F);

                    commit(slot, current, withUnit(current, axis, new WidgetLayout.Unit(clamp(converted, fraction, isOffset(current, axis)), fraction)));
                },
                2,
                Component.empty(),
                List.of(false, true),
                fraction -> fraction ? "%" : "px"
            );

            kind.setClearable(false);

            // Locked to pixels: a percentage left from before stays switchable back
            if (axis == pixelOnly && !unit.fraction()) kind.active = false;

            if (locked) {
                EditorWidgetFactory.lock(value);
                EditorWidgetFactory.lock(kind);
            }

            table.newRow(indent, EditorWidgetFactory.makeTranslatableLabel(factory.font(), unitLabel(layout, axis)));
            table.addWidget(value);
            table.addWidget(kind);
        }

        if (!(slot.owner() instanceof ComponentAttachableHolder container) || locked) return;

        table.newRow(indent, EditorWidgetFactory.makeTranslatableLabel(factory.font(), LangKeys.GUI_WIDGET_SCREEN_ROW_ALIGN_CHILDREN));

        WidgetLayout.Horizontal[] horizontals = {WidgetLayout.Horizontal.LEFT_WIDTH, WidgetLayout.Horizontal.CENTER_WIDTH, WidgetLayout.Horizontal.RIGHT_WIDTH};

        for (int index = 0; index < horizontals.length; index++) {
            WidgetLayout.Horizontal horizontal = horizontals[index];

            table.addWidget(alignButton(factory, slot, index, horizontalLabel(horizontal), () -> alignChildren(
                container, child -> new WidgetLayout(WidgetLayout.Unit.ZERO, child.x2(), child.y1(), child.y2(), horizontal, child.verticalAnchor())
            )));
        }

        table.newRow(indent);

        WidgetLayout.Vertical[] verticals = {WidgetLayout.Vertical.TOP_HEIGHT, WidgetLayout.Vertical.CENTER_HEIGHT, WidgetLayout.Vertical.BOTTOM_HEIGHT};

        for (int index = 0; index < verticals.length; index++) {
            WidgetLayout.Vertical vertical = verticals[index];

            table.addWidget(alignButton(factory, slot, index, verticalLabel(vertical), () -> alignChildren(
                container, child -> new WidgetLayout(child.x1(), child.x2(), WidgetLayout.Unit.ZERO, child.y2(), child.horizontalAnchor(), vertical)
            )));
        }
    }

    /// The anchors combo over `slot`. A pick keeps the widget's rect: the new anchor types, then the units backtracked
    /// against the parent rect it was laid out in.
    private static EditorComboBox<AnchorSelectModal.Anchors> anchorsCombo(final EditorWidgetFactory factory, final EditorWidgetFactory.ValueSlot slot) {
        EditorComboBox<AnchorSelectModal.Anchors> combo = new EditorComboBox<>(
            factory.font(),
            EditorWidgetFactory.columnStart(slot, 0, 1), EditorWidgetFactory.columnSize(slot, 0, 1),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(EditorWidgetFactory.ROW_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            () -> {
                WidgetLayout layout = layoutOf(slot);
                return new AnchorSelectModal.Anchors(layout.horizontalAnchor(), layout.verticalAnchor());
            },
            anchors -> {
                if (anchors == null) return;

                WidgetLayout current = layoutOf(slot);
                WidgetLayout picked = new WidgetLayout(current.x1(), current.x2(), current.y1(), current.y2(), anchors.horizontal(), anchors.vertical());
                WidgetLayoutHolder.Placement placement = ((WidgetLayoutHolder) slot.get()).getPlacement();

                if (placement != null) {
                    AkytheraWidget probe = BlueprintWidgets.widget(picked);
                    ScreenRectangle rect = placement.rect();

                    probe.setRectangle(rect.width(), rect.height(), rect.left(), rect.top());
                    probe.backtrack(placement.area());
                    picked = BlueprintWidgets.layoutOf(probe);
                }

                commit(slot, current, picked);
            },
            EVERY_ANCHORS.size(),
            Component.empty(),
            EVERY_ANCHORS,
            anchors -> Component.translatable(horizontalLabel(anchors.horizontal())).getString() + " / " + Component.translatable(verticalLabel(anchors.vertical())).getString()
        );

        combo.setClearable(false);
        combo.setDropdownFactory(AnchorSelectModal::new);

        return combo;
    }

    /// Column `index` of 3 on an Align Children row, running `align` on press.
    private static AkytheraButton alignButton(
        final EditorWidgetFactory factory, final EditorWidgetFactory.ValueSlot slot, final int index, final String label, final Runnable align
    ) {
        return AkytheraButton.buttonBuilder(factory.font(), LocalizedMessage.translatable(label), align)
            .xUnits(EditorWidgetFactory.columnStart(slot, index, 3), EditorWidgetFactory.columnSize(slot, index, 3))
            .yUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(EditorWidgetFactory.ROW_H))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.TOP_HEIGHT)
            .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
            .build();
    }

    /// Rewrites the layout of every widget `container` holds through `align`, as one undo step.
    private static void alignChildren(final ComponentAttachableHolder container, final UnaryOperator<WidgetLayout> align) {
        List<WidgetLayoutHolder> layouts = container.getComponents().values().stream()
            .filter(WidgetComponentHolder.class::isInstance)
            .map(child -> ((WidgetComponentHolder<?>) child).getLayout())
            .toList();
        List<WidgetLayout> before = layouts.stream().map(WidgetLayoutHolder::makeObject).toList();
        List<WidgetLayout> after = before.stream().map(align).toList();
        Consumer<List<WidgetLayout>> writer = written -> {
            for (int index = 0; index < layouts.size(); index++) layouts.get(index).readFrom(written.get(index));
        };

        if (before.equals(after)) return;

        HistoryManager.getInstance().pushHistory(new DataChange<>(before, after, writer));
        writer.accept(after);
    }

    /// Writes `after` into the layout `slot` holds as one undo step back to `before`. Writing through the slot
    /// re-renders the rows.
    private static void commit(final EditorWidgetFactory.ValueSlot slot, final WidgetLayout before, final WidgetLayout after) {
        WidgetLayoutHolder holder = (WidgetLayoutHolder) slot.get();
        Consumer<WidgetLayout> writer = layout -> {
            holder.readFrom(layout);
            slot.set(holder);
        };

        if (before.equals(after)) return;

        HistoryManager.getInstance().pushHistory(EditorWidgetFactory.historyScope(slot), new DataChange<>(before, after, writer));
        writer.accept(after);
    }

    private static WidgetLayout layoutOf(final EditorWidgetFactory.ValueSlot slot) {
        return ((WidgetLayoutHolder) slot.get()).makeObject();
    }

    /// Unit `axis` of `layout`: 0 = X1, 1 = X2, 2 = Y1, 3 = Y2.
    private static WidgetLayout.Unit unitAt(final WidgetLayout layout, final int axis) {
        return switch (axis) {
            case 0 -> layout.x1();
            case 1 -> layout.x2();
            case 2 -> layout.y1();
            default -> layout.y2();
        };
    }

    /// `layout` with unit `axis` replaced by `unit`, see [#unitAt].
    private static WidgetLayout withUnit(final WidgetLayout layout, final int axis, final WidgetLayout.Unit unit) {
        return new WidgetLayout(
            axis == 0 ? unit : layout.x1(), axis == 1 ? unit : layout.x2(), axis == 2 ? unit : layout.y1(), axis == 3 ? unit : layout.y2(),
            layout.horizontalAnchor(), layout.verticalAnchor()
        );
    }

    /// Whether unit `axis` is an offset from the parent's center, the only unit that goes negative.
    private static boolean isOffset(final WidgetLayout layout, final int axis) {
        return axis == 0 && layout.horizontalAnchor() == WidgetLayout.Horizontal.CENTER_WIDTH
            || axis == 2 && layout.verticalAnchor() == WidgetLayout.Vertical.CENTER_HEIGHT;
    }

    /// `value` within what a unit takes: a percentage within 0–100, an offset's within −50–50, a pixel length
    /// from 0, an offset in pixels unbounded.
    private static int clamp(final int value, final boolean fraction, final boolean offset) {
        if (fraction) return offset ? Math.clamp(value, -50, 50) : Math.clamp(value, 0, 100);

        return offset ? value : Math.max(0, value);
    }

    /// The lang key naming unit `axis` under `layout`'s anchor types.
    private static String unitLabel(final WidgetLayout layout, final int axis) {
        return switch (axis) {
            case 0 -> switch (layout.horizontalAnchor()) {
                case LEFT_WIDTH, LEFT_RIGHT -> LangKeys.GUI_WIDGET_SCREEN_ROW_LEFT_SPACING;
                case RIGHT_WIDTH -> LangKeys.GUI_WIDGET_SCREEN_ROW_RIGHT_SPACING;
                case CENTER_WIDTH -> LangKeys.GUI_WIDGET_SCREEN_ROW_HORIZONTAL_OFFSET;
            };
            case 1 -> layout.horizontalAnchor() == WidgetLayout.Horizontal.LEFT_RIGHT ? LangKeys.GUI_WIDGET_SCREEN_ROW_RIGHT_SPACING : LangKeys.GUI_WIDGET_SCREEN_ROW_WIDTH;
            case 2 -> switch (layout.verticalAnchor()) {
                case TOP_HEIGHT, TOP_BOTTOM -> LangKeys.GUI_WIDGET_SCREEN_ROW_TOP_SPACING;
                case BOTTOM_HEIGHT -> LangKeys.GUI_WIDGET_SCREEN_ROW_BOTTOM_SPACING;
                case CENTER_HEIGHT -> LangKeys.GUI_WIDGET_SCREEN_ROW_VERTICAL_OFFSET;
            };
            default -> layout.verticalAnchor() == WidgetLayout.Vertical.TOP_BOTTOM ? LangKeys.GUI_WIDGET_SCREEN_ROW_BOTTOM_SPACING : LangKeys.GUI_WIDGET_SCREEN_ROW_HEIGHT;
        };
    }

    private static String horizontalLabel(final WidgetLayout.Horizontal horizontal) {
        return switch (horizontal) {
            case LEFT_WIDTH -> LangKeys.GUI_WIDGET_SCREEN_ANCHOR_LEFT;
            case CENTER_WIDTH -> LangKeys.GUI_WIDGET_SCREEN_ANCHOR_CENTER;
            case RIGHT_WIDTH -> LangKeys.GUI_WIDGET_SCREEN_ANCHOR_RIGHT;
            case LEFT_RIGHT -> LangKeys.GUI_WIDGET_SCREEN_ANCHOR_STRETCH;
        };
    }

    private static String verticalLabel(final WidgetLayout.Vertical vertical) {
        return switch (vertical) {
            case TOP_HEIGHT -> LangKeys.GUI_WIDGET_SCREEN_ANCHOR_TOP;
            case CENTER_HEIGHT -> LangKeys.GUI_WIDGET_SCREEN_ANCHOR_CENTER;
            case BOTTOM_HEIGHT -> LangKeys.GUI_WIDGET_SCREEN_ANCHOR_BOTTOM;
            case TOP_BOTTOM -> LangKeys.GUI_WIDGET_SCREEN_ANCHOR_STRETCH;
        };
    }
}
