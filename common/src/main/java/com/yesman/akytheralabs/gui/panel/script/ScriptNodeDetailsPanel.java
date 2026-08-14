package com.yesman.akytheralabs.gui.panel.script;

import com.yesman.akythera.client.gui.widget.Static;
import com.yesman.akythera.client.gui.widget.WidgetTable;
import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.AdjustablePanelManager;
import com.yesman.akytheralabs.gui.widget.AdvancedComboBox;
import com.yesman.akytheralabs.gui.widget.AdvancedEditBox;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsEditBoxTheme;
import com.yesman.akytheralabs.gui.widget.PanelHeader;
import com.yesman.akytheralabs.gui.widget.PopupBox;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/// "Details" panel: editors for the variable selected in the [VariablePanel] —
/// name, type, and (for codec types) default value. Rebuilt on every selection or
/// model change via [#showVariable]. All editors are produced by the shared
/// [VariableEditController].
public final class ScriptNodeDetailsPanel extends AdjustablePanelManager.PanelArea {
    private final Font font;
    private final VariableEditController controller;
    private final PanelHeader header;
    private final WidgetTable table;

    @Nullable
    private ScreenRectangle lastRect;

    public ScriptNodeDetailsPanel(Font font, Screen parentScreen, VariableEditController controller) {
        this.font = font;
        this.controller = controller;
        this.header = new PanelHeader(font, Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_PANEL_DETAILS));
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

        showVariable(controller.getSelected());
    }

    public void showVariable(@Nullable VariableDraft draft) {
        table.clearWidgets();

        if (draft == null) {
            table.newRow().addWidget(new Static(
                font,
                AnchoredWidget.pixel(VariableEditController.INDENT), AnchoredWidget.pixel(VariableEditController.INDENT),
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(VariableEditController.ROW_H),
                AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
                AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
                Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_PANEL_DETAILS_HINT)
            ));
        } else {
            table.newRow()
                .addWidget(controller.label(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_VARIABLE_NAME))
                .addWidget(controller.nameBox(
                    draft,
                    AnchoredWidget.pixel(VariableEditController.LABEL_W + 2), AnchoredWidget.pixel(VariableEditController.INDENT),
                    AnchoredWidget.pixel(0), AnchoredWidget.pixel(VariableEditController.ROW_H),
                    AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
                    AnchoredWidget.VerticalAnchorType.TOP_HEIGHT
                ));

            table.newRow()
                .addWidget(controller.label(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_VARIABLE_TYPE))
                .addWidget(controller.typeCombo(
                    draft,
                    AnchoredWidget.pixel(VariableEditController.LABEL_W + 2), AnchoredWidget.pixel(VariableEditController.INDENT),
                    AnchoredWidget.pixel(0), AnchoredWidget.pixel(VariableEditController.ROW_H),
                    AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
                    AnchoredWidget.VerticalAnchorType.TOP_HEIGHT
                ));

            if (draft.getType().codec() != null) {
                controller.addDefaultValueRow(table, draft);
            }
        }

        table.initialize(false);
        if (lastRect != null) table.relocate(lastRect);
    }

    /// Shows a single editable "Name" row for the selected Anim Graph state machine. `name` reads the
    /// machine's current name; `rename` is given the trimmed new name and returns false to reject it
    /// (blank / duplicate), in which case the box reverts.
    public void showStateMachine(Supplier<String> name, Predicate<String> rename) {
        table.clearWidgets();

        AdvancedEditBox<String> box = new AdvancedEditBox<>(
            font,
            AnchoredWidget.pixel(VariableEditController.LABEL_W + 2), AnchoredWidget.pixel(VariableEditController.INDENT),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(VariableEditController.ROW_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT, AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            name::get, _ -> {}, _ -> true, s -> s, s -> s, Component.empty()
        );
        // Label until focused — matches the variable name box (UE-style inline rename).
        box.setTheme(AkytheraLabsEditBoxTheme.DEFAULT_EDIT_BOX);
        box.setOnCommitted((_, _) -> {
            String now = box.getText().trim();
            if (now.equals(name.get())) return;
            if (!rename.test(now)) box.setValue(name.get()); // rejected: revert display
        });

        table.newRow()
            .addWidget(controller.label(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_VARIABLE_NAME))
            .addWidget(box);

        table.initialize(false);

        if (lastRect != null) table.relocate(lastRect);
    }

    /// One identifier-valued property of the selected node, shown as a [PopupBox] row: the label to
    /// name it by, the value it displays (null renders as `<none>`), whether that value still resolves
    /// — an invalid one marks the box red, matching the node's own `No such asset` error — and the
    /// picker its magnifier opens.
    ///
    /// The row never writes the node itself: `openPicker` gets the box, and the modal it opens commits
    /// the pick and pushes the new value back through [PopupBox#setValue] (see
    /// [com.yesman.akytheralabs.gui.screen.AnimatorScriptEditorScreen]).
    public record IdentifierRow(
        String labelKey,
        Supplier<@Nullable Identifier> current,
        Predicate<Identifier> valid,
        Consumer<PopupBox<Identifier>> openPicker
    ) {}

    /// Shows one [PopupBox] row per identifier property of the selected node — a pose player's bound
    /// asset, an Animation Interface's key **and** its default animation. Every such property is
    /// editable here; the node canvas only ever creates the node.
    public void showIdentifierRows(List<IdentifierRow> rows) {
        table.clearWidgets();

        for (IdentifierRow row : rows) {
            PopupBox<Identifier> box = new PopupBox<>(
                font,
                AnchoredWidget.pixel(VariableEditController.LABEL_W + 2), AnchoredWidget.pixel(VariableEditController.INDENT),
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(VariableEditController.ROW_H),
                AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT, AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
                row.current()::get,
                _ -> {},
                Identifier::toString,
                Component.empty(),
                row.openPicker()
            ).withValidator(row.valid());

            // The table's initialize doesn't push bound values, so seed the display (and its red mark).
            box.setValue(row.current().get());

            table.newRow()
                .addWidget(controller.label(row.labelKey()))
                .addWidget(box);
        }

        table.initialize(false);

        if (lastRect != null) table.relocate(lastRect);
    }

    /// Shows the selected state-machine **state**'s single "Animation State" row: a combo over the
    /// available [com.yesman.akythera.core.animation.driver.FSMAnimationState] signatures in `options`
    /// — already stripped of the reserved Entry / Exit states and of every signature another state on
    /// the same machine already carries, so the list can never introduce a duplicate. `current` reads
    /// the state's present signature; `onPicked` is handed the chosen id and returns false to reject it
    /// — a rejected pick (including the `(none)` row, since a state must keep a signature) reverts the
    /// box to `current`.
    public void showAnimationState(
        List<Identifier> options,
        Supplier<@Nullable Identifier> current,
        Predicate<@Nullable Identifier> onPicked
    ) {
        table.clearWidgets();

        // The change consumer needs to revert the box on a rejected pick, so it must reference the box
        // it is being wired into — resolved through this holder set right after construction.
        AtomicReference<AdvancedComboBox<Identifier>> ref = new AtomicReference<>();
        AdvancedComboBox<Identifier> box = new AdvancedComboBox<>(
            font,
            AnchoredWidget.pixel(VariableEditController.LABEL_W + 2), AnchoredWidget.pixel(VariableEditController.INDENT),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(VariableEditController.ROW_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT, AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            current::get,
            picked -> {
                if (!onPicked.test(picked)) ref.get().setValueQuiet(current.get());
            },
            8, Component.empty(), options, Identifier::toString
        );
        ref.set(box);
        box.setValueQuiet(current.get());

        table.newRow()
            .addWidget(controller.label(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_DETAILS_ANIMATION_STATE))
            .addWidget(box);

        table.initialize(false);

        if (lastRect != null) table.relocate(lastRect);
    }

    @Override
    protected void onPanelResized(ScreenRectangle screenRectangle) {
        lastRect = screenRectangle;
        header.relocate(screenRectangle);
        table.relocate(screenRectangle);
    }
}
