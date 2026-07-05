package com.yesman.akytheralabs.gui.panel.script;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.JsonOps;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.api.data.modularize.impl.NumericComparators;
import com.yesman.akythera.client.gui.widget.CheckBox;
import com.yesman.akythera.client.gui.widget.ClampedNumberBox;
import com.yesman.akythera.client.gui.widget.Static;
import com.yesman.akythera.client.gui.widget.WidgetTable;
import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akythera.client.gui.widget.common.AnchoredWidget.HorizontalAnchorType;
import com.yesman.akythera.client.gui.widget.common.AnchoredWidget.Unit;
import com.yesman.akythera.client.gui.widget.common.AnchoredWidget.VerticalAnchorType;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akythera.core.blueprint.script.ScriptVariables;
import com.yesman.akytheralabs.editor.historytrack.DataChange;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.WorkHistory;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.AdvancedComboBox;
import com.yesman.akytheralabs.gui.widget.AdvancedEditBox;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsComboBoxTheme;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsEditBoxTheme;
import com.yesman.akytheralabs.gui.widget.script.NodeColors;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/// Shared edit logic + widget factory for the Animator Script variables UI.
///
/// The Variables list and the Details panel both build name boxes and type combos
/// from here, and every mutation (add / remove / rename / retype / default-value)
/// is routed through here so it goes onto the [HistoryManager] undo stack and
/// triggers a rebuild of the views. Nothing is written to the holder until "Save".
public final class VariableEditController {
    static final int LABEL_W = 88;
    static final int INDENT = 8;
    static final int ROW_H = 20;

    private final Font font;
    private final List<VariableDraft> drafts;

    /// Selectable variable types: only those whose [ScriptType#codec] is non-null,
    /// because a default value can only be (de)serialized when a codec exists.
    private final List<ScriptType> codecTypes;

    /// Node-graph refactor hook. Called `(oldName, newName)` on every rename so the
    /// blueprint nodes referencing the variable can be retargeted. A stub for now.
    private final BiConsumer<String, String> onRenamed;

    /// Node-graph retype hook. Called `(name, newJavaType)` when a variable's type changes,
    /// so the blueprint's Get / Set nodes for it are re-typed and their now-incompatible wires
    /// dropped. Returns the reversible node-side edit (already applied), or null when no node
    /// referenced the variable; [#changeType] folds it into one undo step with the draft change.
    private final BiFunction<String, Class<?>, @Nullable WorkHistory> onRetyped;

    /// Rebuilds both views (list + details). Set by the screen after panels exist.
    private Runnable rebuildAll = () -> {};
    /// Refreshes the Details panel only (selection change / default-value edit).
    private Runnable refreshDetails = () -> {};

    @Nullable
    private VariableDraft selected;

    public VariableEditController(
        Font font, List<VariableDraft> drafts,
        BiConsumer<String, String> onRenamed,
        BiFunction<String, Class<?>, @Nullable WorkHistory> onRetyped
    ) {
        this.font = font;
        this.drafts = drafts;
        this.onRenamed = onRenamed;
        this.onRetyped = onRetyped;
        this.codecTypes = AkytheraRegistries.Frozen.SCRIPT_TYPE.stream()
            .filter(t -> t.codec() != null)
            .sorted(Comparator.comparing(this::typeName))
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

    public String typeName(ScriptType type) {
        Identifier id = AkytheraRegistries.Frozen.SCRIPT_TYPE.getKey(type);
        return id == null ? "?" : id.getPath();
    }

    /// *****************************************************************
    /// Structural edits (add / remove)                                *
    /// *****************************************************************
    public void addVariable() {
        if (codecTypes.isEmpty()) return;

        ScriptType type = codecTypes.stream()
            .filter(t -> t.type() == float.class)
            .findFirst()
            .orElse(codecTypes.getFirst());

        VariableDraft draft = new VariableDraft(nextFreeName(), type, defaultValueFor(type));
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

    public void removeVariable(VariableDraft draft) {
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

    /// *****************************************************************
    /// Widget factory: name box + type combo (used by both panels)    *
    /// *****************************************************************
    public AdvancedEditBox<String> nameBox(
        VariableDraft draft, Unit x1, Unit x2, Unit y1, Unit y2,
        HorizontalAnchorType h, VerticalAnchorType v
    ) {
        AdvancedEditBox<String> box = new AdvancedEditBox<>(
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

    private void commitRename(VariableDraft draft, AdvancedEditBox<String> box) {
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

    public AdvancedComboBox<ScriptType> typeCombo(
        VariableDraft draft, Unit x1, Unit x2, Unit y1, Unit y2,
        HorizontalAnchorType h, VerticalAnchorType v
    ) {
        AdvancedComboBox<ScriptType> combo = new AdvancedComboBox<>(
            font, x1, x2, y1, y2, h, v,
            draft::getType,
            newType -> changeType(draft, newType),
            Math.min(8, codecTypes.size()),
            Component.empty(),
            codecTypes,
            this::typeName
        );
        // UE-style: a type-color swatch in front of the type name, matched to the variable's
        // Get / Set node pin color so the two read as the same type at a glance.
        combo.setTheme(AkytheraLabsComboBoxTheme.UE_VARIABLE_COMBO_BOX);
        combo.setIndicatorColorMapper(type -> NodeColors.forType(type.type()));
        return combo;
    }

    /// The type-indicator color for a script type, shared by the type combo and the drag ghost
    /// so a dragged variable is painted in the same color its node pins use.
    public static int indicatorColor(ScriptType type) {
        return NodeColors.forType(type.type());
    }

    private void changeType(VariableDraft draft, @Nullable ScriptType newType) {
        if (newType == null || newType == draft.getType()) return;

        Pair<ScriptType, Object> oldState = Pair.of(draft.getType(), draft.getDefaultVal());
        Pair<ScriptType, Object> newState = Pair.of(newType, defaultValueFor(newType));

        applyTypeState(draft, newState);

        // Retarget the blueprint's Get / Set nodes for this variable to the new java type and
        // drop the wires it can no longer satisfy. The returned handle reverses that node-side
        // edit; folding it into this history entry keeps the whole retype one undo step.
        WorkHistory nodeEdit = onRetyped.apply(draft.getName(), newType.type());

        HistoryManager.getInstance().pushHistory(new WorkHistory() {
            @Override
            public void undo() {
                applyTypeState(draft, oldState);
                if (nodeEdit != null) nodeEdit.undo();
                rebuildAll.run();
            }

            @Override
            public void redo() {
                applyTypeState(draft, newState);
                if (nodeEdit != null) nodeEdit.redo();
                rebuildAll.run();
            }
        });

        rebuildAll.run();
    }

    private void applyTypeState(VariableDraft draft, Pair<ScriptType, Object> state) {
        draft.setType(state.getFirst());
        draft.setDefaultVal(state.getSecond());
    }

    /// *****************************************************************
    /// Widget factory: per-type default value editor (Details only)   *
    /// *****************************************************************
    public void addDefaultValueRow(WidgetTable table, VariableDraft draft) {
        table.newRow().addWidget(label(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_VARIABLE_DEFAULT));

        Class<?> c = ParseUtils.unwrapPrimitive(draft.getType().type());

        if (c == Float.class) c = float.class;
        if (c == Double.class) c = double.class;
        if (c == Integer.class) c = int.class;
        if (c == Long.class) c = long.class;
        if (c == Short.class) c = short.class;
        if (c == Byte.class) c = byte.class;
        if (c == Boolean.class) c = boolean.class;
        if (c == Character.class) c = char.class;

        if (c == float.class) {
            table.addWidget(numberBox(draft, -Float.MAX_VALUE, Float.MAX_VALUE, 0.1F, -0.1F,
                Float::parseFloat, ParseUtils::floatingPointToString, Float::compareTo, Float::sum));
        } else if (c == double.class) {
            table.addWidget(numberBox(draft, -Double.MAX_VALUE, Double.MAX_VALUE, 0.1D, -0.1D,
                Double::parseDouble, d -> ParseUtils.floatingPointToString(d.floatValue()), Double::compareTo, Double::sum));
        } else if (c == int.class) {
            table.addWidget(numberBox(draft, Integer.MIN_VALUE, Integer.MAX_VALUE, 1, -1,
                Integer::parseInt, String::valueOf, Integer::compareTo, Integer::sum));
        } else if (c == long.class) {
            table.addWidget(numberBox(draft, Long.MIN_VALUE, Long.MAX_VALUE, 1L, -1L,
                Long::parseLong, String::valueOf, Long::compareTo, Long::sum));
        } else if (c == short.class) {
            table.addWidget(numberBox(draft, Short.MIN_VALUE, Short.MAX_VALUE, (short) 1, (short) -1,
                Short::parseShort, String::valueOf, Short::compareTo, (a, b) -> (short) (a + b)));
        } else if (c == byte.class) {
            table.addWidget(numberBox(draft, Byte.MIN_VALUE, Byte.MAX_VALUE, (byte) 1, (byte) -1,
                Byte::parseByte, String::valueOf, Byte::compareTo, (a, b) -> (byte) (a + b)));
        } else if (c == boolean.class) {
            table.addWidget(new CheckBox(
                font,
                AnchoredWidget.pixel(LABEL_W + 2), AnchoredWidget.pixel(14),
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(14),
                HorizontalAnchorType.LEFT_WIDTH, VerticalAnchorType.TOP_HEIGHT,
                () -> (Boolean) draft.getDefaultVal(),
                v -> pushDefaultChange(draft, v),
                Component.empty(), false
            ));
        } else if (c == String.class) {
            AdvancedEditBox<String> box = new AdvancedEditBox<>(
                font,
                AnchoredWidget.pixel(LABEL_W + 2), AnchoredWidget.pixel(INDENT),
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
                HorizontalAnchorType.LEFT_RIGHT, VerticalAnchorType.TOP_HEIGHT,
                () -> (String) draft.getDefaultVal(),
                _ -> {},
                _ -> true, s -> s, s -> s,
                Component.empty()
            );
            box.setOnCommitted((_, _) -> {
                Object oldV = draft.getDefaultVal();
                String now = box.getText();
                if (Objects.equals(oldV, now)) return;
                draft.setDefaultVal(now);
                pushDefaultHistory(draft, oldV, now);
            });
            table.addWidget(box);
        } else {
            // Vec3, comparator and any other codec type: read-only JSON view for this stage.
            table.addWidget(new Static(
                font,
                AnchoredWidget.pixel(LABEL_W + 2), AnchoredWidget.pixel(INDENT),
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
                HorizontalAnchorType.LEFT_RIGHT, VerticalAnchorType.TOP_HEIGHT,
                Component.literal(encodeDefault(draft))
            ));
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends Number & Comparable<T>> ClampedNumberBox<T> numberBox(
        VariableDraft draft, T min, T max, T inc, T dec,
        Function<String, T> parse, Function<T, String> toStr, Comparator<T> cmp, BiFunction<T, T, T> sum
    ) {
        ClampedNumberBox<T> box = new ClampedNumberBox<>(
            font,
            AnchoredWidget.pixel(LABEL_W + 2), AnchoredWidget.pixel(INDENT),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
            HorizontalAnchorType.LEFT_RIGHT, VerticalAnchorType.TOP_HEIGHT,
            () -> (T) draft.getDefaultVal(),
            draft::setDefaultVal,
            Component.empty(), false,
            min, max, inc, dec, parse, toStr, cmp, sum
        );
        box.setOnCommitted((oldV, newV) -> {
            if (Objects.equals(oldV, newV)) return;
            pushDefaultHistory(draft, oldV, newV);
        });
        return box;
    }

    private void pushDefaultChange(VariableDraft draft, Object newV) {
        Object oldV = draft.getDefaultVal();
        if (Objects.equals(oldV, newV)) return;
        draft.setDefaultVal(newV);
        pushDefaultHistory(draft, oldV, newV);
    }

    private void pushDefaultHistory(VariableDraft draft, @Nullable Object oldV, @Nullable Object newV) {
        HistoryManager.getInstance().pushHistory(new DataChange<>(oldV, newV, v -> {
            draft.setDefaultVal(v);
            refreshDetails.run();
        }));
    }

    @SuppressWarnings("unchecked")
    private String encodeDefault(VariableDraft draft) {
        Object value = draft.getDefaultVal();
        if (value == null || draft.getType().codec() == null) return "";
        try {
            return ((com.mojang.serialization.Codec<Object>) draft.getType().codec())
                .encodeStart(JsonOps.INSTANCE, value).getOrThrow().toString();
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    public Static label(String langKey) {
        return new Static(
            font,
            AnchoredWidget.pixel(INDENT), AnchoredWidget.pixel(LABEL_W - INDENT),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
            HorizontalAnchorType.LEFT_WIDTH, VerticalAnchorType.TOP_HEIGHT,
            Component.translatable(langKey)
        );
    }

    /// Seeds a type-appropriate non-null default. Codec types must hold a non-null
    /// value so [ScriptVariables.VariableInfo]'s
    /// codec can encode it on save.
    public static @Nullable Object defaultValueFor(ScriptType type) {
        Class<?> c = ParseUtils.unwrapPrimitive(type.type());
        if (c == byte.class) return (byte) 0;
        if (c == short.class) return (short) 0;
        if (c == int.class) return 0;
        if (c == long.class) return 0L;
        if (c == float.class) return 0.0F;
        if (c == double.class) return 0.0D;
        if (c == boolean.class) return false;
        if (c == String.class) return "";
        if (c == Vec3.class) return Vec3.ZERO;
        if (c == Comparator.class) return NumericComparators.INTEGER_COMPARATOR;
        return null;
    }
}
