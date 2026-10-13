package com.yesman.akytheralabs.gui.panel.script;

import com.google.gson.JsonElement;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.api.data.modularize.impl.ScriptComparators;
import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.client.api.gui.AkytheraButton;
import com.yesman.akythera.client.api.gui.BuiltInEditBoxTheme;
import com.yesman.akythera.client.api.gui.ComboIcon;
import com.yesman.akythera.client.api.gui.WidgetTable;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget.HorizontalAnchorType;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget.Unit;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget.VerticalAnchorType;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.core.blueprint.script.ScriptVariables;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.akythera.blueprint.BlueprintScriptData;
import com.yesman.akytheralabs.editor.EditorWidgetFactory;
import com.yesman.akytheralabs.editor.ValueShape;
import com.yesman.akytheralabs.editor.historytrack.DataChange;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.WorkHistory;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.*;
import com.yesman.akytheralabs.gui.widget.script.TypeIcon;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/// Shared edit logic + widget factory for the blueprint variables UI.
///
/// The Variables list and the Details panel both build name boxes and type combos
/// from here, and every mutation (add / remove / rename / retype / default-value)
/// is routed through here so it goes onto the [HistoryManager] undo stack and
/// triggers a rebuild of the views. Nothing is written to the holder until "Save".
public final class VariableEditController {
    /// Narrowest title column of the Details table, see [WidgetTable#setMinTitleWidth].
    static final int LABEL_W = 144;
    static final int INDENT = 8;
    static final int ROW_H = 20;

    /// Width of the container combo, which shows its icon only.
    private static final int CONTAINER_W = 30;
    private static final int GAP = 4;

    private final Font font;
    private final EditorWidgetFactory factory;
    private final List<VariableDraft> drafts;

    /// Selectable types a variable is made of, primitives first. A list, set or map is picked by the
    /// container combo, so the raw container types are left out.
    private final List<ScriptType> baseTypes;

    /// Node-graph refactor hook. Called `(oldName, newName)` on every rename so the
    /// blueprint nodes referencing the variable can be retargeted. A stub for now.
    private final BiConsumer<String, String> onRenamed;

    /// Node-graph retype hook. Called `(name, newType)` when a variable's type changes,
    /// so the blueprint's Get / Set nodes for it are re-typed and their now-incompatible wires
    /// dropped. Returns the reversible node-side edit (already applied), or null when no node
    /// referenced the variable; [#changeType] folds it into one undo step with the draft change.
    private final BiFunction<String, ScriptType, @Nullable WorkHistory> onRetyped;

    /// Rebuilds both views (list + details). Set by the screen after panels exist.
    private Runnable rebuildAll = () -> {};
    /// Refreshes the Details panel only (selection change / default-value edit).
    private Runnable refreshDetails = () -> {};

    @Nullable
    private VariableDraft selected;

    /// The popup title and reason [#addVariable] shows instead of adding, or null while it adds.
    @Nullable
    private Component addRefusalTitle;
    @Nullable
    private Component addRefusal;

    /// The popup title and lines [#removeVariable] asks to confirm with, for a variable that gives any lines.
    private Component removalWarningTitle = Component.empty();
    private Function<VariableDraft, List<Component>> removalWarning = _ -> List.of();

    public VariableEditController(
        Font font, List<VariableDraft> drafts,
        BiConsumer<String, String> onRenamed,
        BiFunction<String, ScriptType, @Nullable WorkHistory> onRetyped
    ) {
        this.font = font;
        this.factory = new EditorWidgetFactory(font);
        this.drafts = drafts;
        this.onRenamed = onRenamed;
        this.onRetyped = onRetyped;
        this.baseTypes = AkytheraRegistries.Frozen.SCRIPT_TYPE.stream()
            .filter(t -> !Collection.class.isAssignableFrom(t.type()) && !Map.class.isAssignableFrom(t.type()))
            .sorted(Comparator.comparing((ScriptType t) -> !isPrimitive(t)).thenComparing(this::typeName))
            .toList();
    }

    public void setViews(Runnable rebuildAll, Runnable refreshDetails) {
        this.rebuildAll = rebuildAll;
        this.refreshDetails = refreshDetails;
    }

    public List<VariableDraft> drafts() {
        return drafts;
    }

    public @Nullable VariableDraft getSelected() {
        return selected;
    }

    public void select(@Nullable VariableDraft draft) {
        this.selected = draft;
        refreshDetails.run();
    }

    /// Makes [#addVariable] show a refusal popup instead of adding.
    public void refuseAdding(Component title, Component reason) {
        this.addRefusalTitle = title;
        this.addRefusal = reason;
    }

    public @Nullable Component getAddRefusal() {
        return addRefusal;
    }

    /// Makes [#removeVariable] ask first, in a popup showing the lines `warning` gives for the variable. A variable
    /// with no lines is removed without asking.
    public void warnOnRemoving(Component title, Function<VariableDraft, List<Component>> warning) {
        this.removalWarningTitle = title;
        this.removalWarning = warning;
    }

    public String typeName(ScriptType type) {
        Identifier id = AkytheraRegistries.Frozen.SCRIPT_TYPE.getKey(type);
        return id == null ? "?" : id.getPath();
    }

    /// Whether values of `type` are Java primitives, which the type combos list first.
    private static boolean isPrimitive(ScriptType type) {
        return ParseUtils.unbox(type.type()).isPrimitive();
    }

    /// The type a map maps to, or [ScriptType#base] for any other container.
    private static ScriptType mapValueOf(ScriptType type) {
        return type.container() == ScriptType.Container.MAP ? Objects.requireNonNull(type.element()) : type.base();
    }

    // *****************************************************************
    // Structural edits (add / remove)
    // *****************************************************************
    public void addVariable() {
        if (addRefusal != null) {
            RefusalPopup.open(Objects.requireNonNull(addRefusalTitle), addRefusal);
            return;
        }

        if (baseTypes.isEmpty()) return;

        ScriptType type = baseTypes.stream()
            .filter(t -> ParseUtils.unbox(t.type()) == float.class)
            .findFirst()
            .orElse(baseTypes.getFirst());

        VariableDraft draft = new VariableDraft(
            new BlueprintScriptData.Variable(nextFreeName(), type, Optional.empty(), ScriptVariables.SyncDirection.NONE));
        draft.setDefaultVal(defaultValueFor(type));

        int index = drafts.size();
        drafts.add(draft);
        selected = draft;

        HistoryManager.getInstance().pushHistory(new WorkHistory() {
            @Override
            public void undo() {
                drafts.remove(draft);
                if (selected == draft) selected = null;
                rebuildAll.run();
            }

            @Override
            public void redo() {
                if (!drafts.contains(draft)) drafts.add(Math.min(index, drafts.size()), draft);
                selected = draft;
                rebuildAll.run();
            }
        });

        rebuildAll.run();
    }

    /// Removes `draft` as one undo step, after the popup of [#warnOnRemoving] is confirmed when it has lines for it.
    public void removeVariable(VariableDraft draft) {
        List<Component> warning = removalWarning.apply(draft);

        if (warning.isEmpty()) {
            removeConfirmed(draft);
            return;
        }

        int popupWidth = 300;
        int buttonWidth = WidgetContainerModal.evenButtonWidth(popupWidth, 2);
        WidgetContainerModal.Builder popup = WidgetContainerModal.builder(font, removalWarningTitle, popupWidth);

        warning.forEach(popup::textLines);
        ModalManager.getInstance().openModalWidgetAtCenter(
            popup
                .footerButton(
                    AkytheraButton.buttonBuilder(font, LocalizedMessage.translatable(LangKeys.GUI_CANCEL), () -> ModalManager.getInstance().closeModal())
                        .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
                        .build(),
                    buttonWidth
                )
                .footerButton(
                    AkytheraButton.buttonBuilder(font, LocalizedMessage.translatable(LangKeys.GUI_MESSAGE_BUTTON_REMOVE), () -> {
                            ModalManager.getInstance().closeModal();
                            removeConfirmed(draft);
                        })
                        .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
                        .build(),
                    buttonWidth
                )
                .build()
        );
    }

    private void removeConfirmed(VariableDraft draft) {
        int index = drafts.indexOf(draft);
        if (index < 0) return;

        drafts.remove(index);
        if (selected == draft) selected = null;

        HistoryManager.getInstance().pushHistory(new WorkHistory() {
            @Override
            public void undo() {
                drafts.add(Math.min(index, drafts.size()), draft);
                selected = draft;
                rebuildAll.run();
            }

            @Override
            public void redo() {
                drafts.remove(draft);
                if (selected == draft) selected = null;
                rebuildAll.run();
            }
        });

        rebuildAll.run();
    }

    private String nextFreeName() {
        String base = "new_var";
        if (drafts.stream().noneMatch(d -> d.getName().equals(base))) return base;

        int i = 1;
        while (true) {
            String candidate = base + "_" + i;
            if (drafts.stream().noneMatch(d -> d.getName().equals(candidate))) return candidate;
            i++;
        }
    }

    // *****************************************************************
    // Widget factory: name box + type combos (used by both panels)
    // *****************************************************************
    public EditorEditBox<String> nameBox(
        VariableDraft draft, Unit x1, Unit x2, Unit y1, Unit y2,
        HorizontalAnchorType h, VerticalAnchorType v
    ) {
        EditorEditBox<String> box = new EditorEditBox<>(
            font, x1, x2, y1, y2, h, v,
            draft::getName,
            _ -> {},
            VariableEditController::isValidJavaIdentifier,
            s -> s, s -> s,
            Component.empty()
        );
        // Label until focused: the name shows as plain text in the row and only turns into a
        // bordered edit box while the user is renaming it (UE-style).
        box.setTheme(AkytheraLabsEditBoxTheme.TRANSPARENT_EDIT_BOX);
        box.addCharReplacement(' ', '_');
        box.setOnCommitted((_, _) -> commitRename(draft, box));
        return box;
    }

    /// Name-box input filter: accepts only a legal Java identifier — first char a
    /// Java identifier start, the rest identifier parts (so no digit leads, no
    /// special characters). Case is not constrained, so camelCase/PascalCase/snake
    /// are all permitted. Empty is allowed so the box can be cleared mid-edit; the
    /// empty commit is rejected in [#commitRename].
    private static boolean isValidJavaIdentifier(String s) {
        if (s.isEmpty()) return true;
        if (!Character.isJavaIdentifierStart(s.charAt(0))) return false;

        for (int i = 1; i < s.length(); i++) {
            if (!Character.isJavaIdentifierPart(s.charAt(i))) return false;
        }

        return true;
    }

    private void commitRename(VariableDraft draft, EditorEditBox<String> box) {
        String oldName = draft.getName();
        String newName = box.getText().trim();

        if (newName.equals(oldName)) return;

        if (newName.isEmpty() || drafts.stream().anyMatch(d -> d != draft && d.getName().equals(newName))) {
            box.setValue(oldName); // reject: revert display
            return;
        }

        draft.setName(newName);
        onRenamed.accept(oldName, newName);

        HistoryManager.getInstance().pushHistory(new DataChange<>(oldName, newName, n -> {
            String prev = draft.getName();
            draft.setName(n);
            onRenamed.accept(prev, n);
            rebuildAll.run();
        }));

        rebuildAll.run();
    }

    /// A combo picking the type `draft` is made of, see [ScriptType#base]. Its swatch shows the variable's
    /// container.
    public EditorComboBox<ScriptType> typeCombo(
        VariableDraft draft, Unit x1, Unit x2, Unit y1, Unit y2,
        HorizontalAnchorType h, VerticalAnchorType v
    ) {
        Function<ScriptType, ScriptType> retype = base -> draft.getType().container().wrap(base, mapValueOf(draft.getType()));

        return codecTypeCombo(
            draft, x1, x2, y1, y2, h, v,
            () -> draft.getType().base(),
            retype,
            base -> TypeIcon.of(retype.apply(base))
        );
    }

    /// A combo picking the type the map `draft` maps to.
    public EditorComboBox<ScriptType> valueTypeCombo(
        VariableDraft draft, Unit x1, Unit x2, Unit y1, Unit y2,
        HorizontalAnchorType h, VerticalAnchorType v
    ) {
        return codecTypeCombo(
            draft, x1, x2, y1, y2, h, v,
            () -> mapValueOf(draft.getType()),
            value -> ScriptType.mapOf(draft.getType().base(), value),
            TypeIcon::of
        );
    }

    /// A combo over [#baseTypes] under "Primitives" and "References" headings, retyping `draft` to what
    /// `retype` makes of the picked type.
    private EditorComboBox<ScriptType> codecTypeCombo(
        VariableDraft draft, Unit x1, Unit x2, Unit y1, Unit y2,
        HorizontalAnchorType h, VerticalAnchorType v,
        Callable<ScriptType> getter, Function<ScriptType, ScriptType> retype, Function<ScriptType, ComboIcon> icon
    ) {
        EditorComboBox<ScriptType> combo = new EditorComboBox<>(
            font, x1, x2, y1, y2, h, v,
            getter,
            picked -> {
                if (picked != null) changeType(draft, retype.apply(picked));
            },
            Math.min(8, baseTypes.size()),
            Component.empty(),
            baseTypes,
            this::typeName
        );

        combo.setTheme(AkytheraLabsComboBoxTheme.UE_VARIABLE_COMBO_BOX);
        combo.setClearable(false);
        combo.setSearchable(true);
        combo.setIconMapper(icon);
        combo.setCategoryMapper(type -> Component.translatable(isPrimitive(type)
            ? LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_VARIABLE_TYPE_PRIMITIVES
            : LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_VARIABLE_TYPE_REFERENCES));

        return combo;
    }

    /// A combo picking the [ScriptType.Container] of `draft`, keeping the types it is made of. Shows its icon
    /// only while collapsed.
    public EditorComboBox<ScriptType.Container> containerCombo(
        VariableDraft draft, Unit x1, Unit x2, Unit y1, Unit y2,
        HorizontalAnchorType h, VerticalAnchorType v
    ) {
        List<ScriptType.Container> containers = List.of(ScriptType.Container.values());
        Function<ScriptType.Container, ScriptType> retype = container ->
            container.wrap(draft.getType().base(), mapValueOf(draft.getType()));

        EditorComboBox<ScriptType.Container> combo = new EditorComboBox<>(
            font, x1, x2, y1, y2, h, v,
            () -> draft.getType().container(),
            container -> {
                if (container != null) changeType(draft, retype.apply(container));
            },
            containers.size(),
            Component.empty(),
            containers,
            container -> Component.translatable(switch (container) {
                case SINGLE -> LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_VARIABLE_CONTAINER_SINGLE;
                case LIST -> LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_VARIABLE_CONTAINER_LIST;
                case SET -> LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_VARIABLE_CONTAINER_SET;
                case MAP -> LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_VARIABLE_CONTAINER_MAP;
            }).getString()
        );

        combo.setTheme(AkytheraLabsComboBoxTheme.UE_VARIABLE_COMBO_BOX);
        combo.setClearable(false);
        combo.setIconMapper(container -> TypeIcon.of(retype.apply(container)));

        return combo;
    }

    private void changeType(VariableDraft draft, ScriptType newType) {
        if (newType == draft.getType()) return;

        BlueprintScriptData.Variable oldState = draft.toData();
        Object newDefault = defaultValueFor(newType);

        draft.setType(newType, newDefault);

        // Retarget the blueprint's Get / Set nodes for this variable to the new type and
        // drop the wires it can no longer satisfy. The returned handle reverses that node-side
        // edit; folding it into this history entry keeps the whole retype one undo step.
        WorkHistory nodeEdit = onRetyped.apply(draft.getName(), newType);

        HistoryManager.getInstance().pushHistory(new WorkHistory() {
            @Override
            public void undo() {
                draft.load(oldState);
                if (nodeEdit != null) nodeEdit.undo();
                rebuildAll.run();
            }

            @Override
            public void redo() {
                draft.setType(newType, newDefault);
                if (nodeEdit != null) nodeEdit.redo();
                rebuildAll.run();
            }
        });

        rebuildAll.run();
    }

    // *****************************************************************
    // Details rows
    // *****************************************************************

    /// The Details rows every variable shows: its name, its type and its default value. A type with no
    /// codec has no default value to persist, so it shows none.
    public void addSharedRows(WidgetTable table, VariableDraft draft) {
        EditorEditBox<String> nameBox = nameBox(
            draft,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(INDENT),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
            HorizontalAnchorType.LEFT_RIGHT, VerticalAnchorType.TOP_HEIGHT
        );

        nameBox.setTheme(BuiltInEditBoxTheme.DEFAULT_EDIT_BOX);
        table.newRow(EditorWidgetFactory.makeTranslatableLabel(font, LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_VARIABLE_NAME))
            .addWidget(nameBox);

        table.newRow(EditorWidgetFactory.makeTranslatableLabel(font, LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_VARIABLE_TYPE));

        if (draft.getType().container() == ScriptType.Container.MAP) {
            // The key and value combos share the width the container combo leaves
            Unit half = (rect, _) -> (rect.width() - INDENT - CONTAINER_W - GAP * 2) / 2;

            table.addWidget(typeCombo(
                draft,
                AnchoredWidget.pixel(0), half,
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
                HorizontalAnchorType.LEFT_WIDTH, VerticalAnchorType.TOP_HEIGHT
            ));
            table.addWidget(containerCombo(
                draft,
                (rect, xy) -> half.pixels(rect, xy) + GAP, AnchoredWidget.pixel(CONTAINER_W),
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
                HorizontalAnchorType.LEFT_WIDTH, VerticalAnchorType.TOP_HEIGHT
            ));
            table.addWidget(valueTypeCombo(
                draft,
                AnchoredWidget.pixel(INDENT), half,
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
                HorizontalAnchorType.RIGHT_WIDTH, VerticalAnchorType.TOP_HEIGHT
            ));
        } else {
            table.addWidget(typeCombo(
                draft,
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(INDENT + CONTAINER_W + GAP),
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
                HorizontalAnchorType.LEFT_RIGHT, VerticalAnchorType.TOP_HEIGHT
            ));
            table.addWidget(containerCombo(
                draft,
                AnchoredWidget.pixel(INDENT), AnchoredWidget.pixel(CONTAINER_W),
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
                HorizontalAnchorType.RIGHT_WIDTH, VerticalAnchorType.TOP_HEIGHT
            ));
        }

        if (draft.getType().codec() == null) return;

        // A list, set or map lays out one row per element under a `+` header. A stored default that does not
        // decode shows as its JSON, read-only.
        String label = Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_VARIABLE_DEFAULT).getString();
        EditorWidgetFactory.PropertySlot slot = draft.isDefaultUnreadable()
            ? new EditorWidgetFactory.PropertySlot(
                label, new ValueShape.Leaf(JsonElement.class, null), draft, draft::getDefaultJson, _ -> {})
            : new EditorWidgetFactory.PropertySlot(
                label, ValueShape.of(draft.getType()), draft, draft::getDefaultVal, draft::setDefaultVal);

        factory.appendSlots(table, List.of(slot), () -> refreshDetails.run());
    }

    /// The sync direction row, for a variable whose type can cross the wire.
    public void addSyncRow(WidgetTable table, VariableDraft draft) {
        List<ScriptVariables.SyncDirection> directions = List.of(ScriptVariables.SyncDirection.values());

        table.newRow(EditorWidgetFactory.makeTranslatableLabel(font, LangKeys.GUI_ABILITY_SCREEN_VARIABLE_SYNC))
            .addWidget(new EditorComboBox<>(
                font,
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(INDENT),
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
                HorizontalAnchorType.LEFT_RIGHT, VerticalAnchorType.TOP_HEIGHT,
                draft::getSync,
                direction -> {
                    ScriptVariables.SyncDirection oldDirection = draft.getSync();
                    if (direction == null || direction == oldDirection) return;

                    draft.setSync(direction);

                    HistoryManager.getInstance().pushHistory(new DataChange<>(oldDirection, direction, value -> {
                        draft.setSync(value);
                        refreshDetails.run();
                    }));
                },
                directions.size(),
                Component.empty(),
                directions,
                direction -> ParseUtils.toDisplayName(direction.name())
            ).setClearable(false));
    }

    /// Seeds a type-appropriate default as the editor holds it, see [ValueShape#blank]: zero, false, an empty
    /// string, list, set or map, and the unset value for a type with no natural zero, such as a registry entry.
    public static @Nullable Object defaultValueFor(ScriptType type) {
        if (type.type() == Comparator.class) return ScriptComparators.INTEGER_COMPARATOR;

        return ValueShape.of(type).blank();
    }
}
