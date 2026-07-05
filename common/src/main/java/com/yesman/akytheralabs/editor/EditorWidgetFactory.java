package com.yesman.akytheralabs.editor;

import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.client.gui.widget.CheckBox;
import com.yesman.akythera.client.gui.widget.ClampedNumberBox;
import com.yesman.akythera.client.gui.widget.Static;
import com.yesman.akythera.client.gui.widget.WidgetTable;
import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akythera.client.gui.widget.common.DataBoundWidget;
import com.yesman.akythera.core.util.EnumerableDataBlock;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.dataholder.DataHolder;
import com.yesman.akytheralabs.dataholder.EntityPreviewModel;
import com.yesman.akytheralabs.dataholder.entitypatch.component.AnimatorScriptRef;
import com.yesman.akytheralabs.editor.EditorFieldScanner.EditorField;
import com.yesman.akytheralabs.editor.historytrack.DataChange;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.gui.widget.AdvancedComboBox;
import com.yesman.akytheralabs.gui.widget.AdvancedEditBox;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsEditBoxTheme;
import com.yesman.akytheralabs.gui.widget.PopupBox;
import com.yesman.akytheralabs.gui.widget.main.AccessorSelectionModal;
import com.yesman.akytheralabs.gui.widget.main.AnimationSelectModal;
import com.yesman.akytheralabs.gui.widget.main.AnimatorScriptSelectModal;
import com.yesman.akytheralabs.gui.widget.main.RegistryLookupModal;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/// Maps annotated holder fields to anchored editor widgets and stuffs them
/// into a [WidgetTable] row. Instantiate once per panel (it caches per-class
/// row producers); call [#fillWidgetTable] every time the selection changes.
public final class EditorWidgetFactory {
    private static final int LABEL_W = 96;
    private static final int INDENT = 8;
    private static final int ROW_H = 20;

    private final Map<Class<?>, List<RowBuilder>> rowCache = new ConcurrentHashMap<>();

    private final Font font;

    public EditorWidgetFactory(Font font) {
        this.font = font;
    }

    /// Clears the table, then for each `@EditorEditable` field on `instance`'s
    /// concrete class adds a label + editor row. After all rows are added,
    /// runs [WidgetTable#initialize] so DataBoundWidgets pull their initial
    /// values.
    public void fillWidgetTable(WidgetTable table, Object instance) {
        table.clearWidgets();

        for (RowBuilder rb : rowCache.computeIfAbsent(instance.getClass(), this::buildRowBuilders)) {
            rb.apply(table, instance);
        }

        table.initialize(false);
    }

    /// Append `instance`'s editor rows to `table` without clearing existing
    /// rows. Use after [#fillWidgetTable] to stack rows from a second object
    /// (e.g. a notify's wrapping `MutableNotify` then the inner holder).
    public void appendRowsFrom(WidgetTable table, Object instance) {
        for (RowBuilder rb : rowCache.computeIfAbsent(instance.getClass(), this::buildRowBuilders)) {
            rb.apply(table, instance);
        }
    }

    private List<RowBuilder> buildRowBuilders(Class<?> type) {
        return EditorFieldScanner.fieldsOf(type).stream()
            .map(this::builderFor)
            .toList();
    }

    private RowBuilder builderFor(EditorField editorField) {
        Field f = editorField.field();
        Class<?> clz = f.getType();
        EditorEditable ann = editorField.annotation();
        String labelText = editorField.label();

        if (clz == float.class || clz == Float.class) {
            return (table, inst) -> {
                @SuppressWarnings("unchecked")
                ClampedNumberBox<Float>[] ref = new ClampedNumberBox[1];

                ref[0] = new ClampedNumberBox<>(
                    font,
                    AnchoredWidget.pixel(LABEL_W + 2), AnchoredWidget.pixel(INDENT),
                    AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
                    AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
                    AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
                    () -> (Float)tryGet(f, inst),
                    v -> trySet(f, inst, v),
                    Component.empty(),
                    false,
                    ann.min(), ann.max(), 0.1F, -0.1F,
                    Float::parseFloat,
                    ParseUtils::floatingPointToString,
                    Float::compareTo,
                    Float::sum
                );

                ref[0].setOnCommitted((oldVal, newVal) -> pushFieldCommit(f, inst, oldVal, newVal, ref));

                table.newRow()
                    .addWidget(makeLabel(labelText))
                    .addWidget(ref[0]);
            };
        }

        if (clz == int.class || clz == Integer.class) {
            return (table, inst) -> {
                @SuppressWarnings("unchecked")
                ClampedNumberBox<Integer>[] ref = new ClampedNumberBox[1];

                ref[0] = new ClampedNumberBox<>(
                    font,
                    AnchoredWidget.pixel(LABEL_W + 2), AnchoredWidget.pixel(INDENT),
                    AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
                    AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
                    AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
                    () -> (Integer)tryGet(f, inst),
                    v -> trySet(f, inst, v),
                    Component.empty(),
                    false,
                    (int)ann.min(), (int)ann.max(), 1, -1,
                    Integer::parseInt, String::valueOf,
                    Integer::compareTo, Integer::sum
                );

                ref[0].setOnCommitted((oldVal, newVal) -> pushFieldCommit(f, inst, oldVal, newVal, ref));

                table.newRow()
                    .addWidget(makeLabel(labelText))
                    .addWidget(ref[0]);
            };
        }

        if (clz == boolean.class || clz == Boolean.class) {
            return (table, inst) -> {
                CheckBox[] ref = new CheckBox[1];

                ref[0] = new CheckBox(
                    font,
                    AnchoredWidget.pixel(LABEL_W + 2), AnchoredWidget.pixel(12),
                    AnchoredWidget.pixel(0), AnchoredWidget.pixel(12),
                    AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH,
                    AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
                    () -> (Boolean)tryGet(f, inst),
                    v -> pushFieldChange(f, inst, v, ref),
                    Component.empty(),
                    false
                );

                table.newRow()
                    .addWidget(makeLabel(labelText))
                    .addWidget(ref[0]);
            };
        }

        if (clz == String.class) {
            return (table, inst) -> {
                @SuppressWarnings("unchecked")
                AdvancedEditBox<String>[] ref = new AdvancedEditBox[1];

                ref[0] = new AdvancedEditBox<>(
                    font,
                    AnchoredWidget.pixel(LABEL_W + 2), AnchoredWidget.pixel(INDENT),
                    AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
                    AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
                    AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
                    () -> (String)tryGet(f, inst),
                    v -> trySet(f, inst, v),
                    _ -> true, s -> s, s -> s,
                    Component.empty()
                );

                ref[0].setTheme(AkytheraLabsEditBoxTheme.DEFAULT_EDIT_BOX);
                ref[0].setOnCommitted((oldVal, newVal) -> pushFieldCommit(f, inst, oldVal, newVal, ref));

                table.newRow()
                    .addWidget(makeLabel(labelText))
                    .addWidget(ref[0]);
            };
        }

        if (EnumerableDataBlock.class.isAssignableFrom(clz)) {
            return (table, inst) -> {
                @SuppressWarnings("unchecked")
                AdvancedComboBox<Object>[] ref = new AdvancedComboBox[1];

                Collection<? extends EnumerableDataBlock> values =
                    ((EnumerableDataBlock) tryGet(f, inst)).getEnumManager().universalValues();

                ref[0] = new AdvancedComboBox<>(
                    font,
                    AnchoredWidget.pixel(LABEL_W + 2), AnchoredWidget.pixel(INDENT),
                    AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
                    AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
                    AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
                    () -> tryGet(f, inst),
                    v -> pushFieldChange(f, inst, v, ref),
                    Math.min(8, values.size()),
                    Component.empty(),
                    List.<Object>copyOf(values),
                    v -> ((EnumerableDataBlock) v).identifier().toString()
                );

                table.newRow()
                    .addWidget(makeLabel(labelText))
                    .addWidget(ref[0]);
            };
        }

        if (AssetAccessor.class.isAssignableFrom(clz)) {
            return (table, inst) -> {
                @SuppressWarnings("unchecked")
                PopupBox<Object>[] ref = new PopupBox[1];

                ref[0] = new PopupBox<>(
                    font,
                    AnchoredWidget.pixel(LABEL_W + 2), AnchoredWidget.pixel(INDENT),
                    AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
                    AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
                    AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
                    () -> tryGet(f, inst),
                    _ -> {},
                    v -> v instanceof AssetAccessor<?> accessor ? AccessorSelectionModal.displayName(accessor) : Objects.toString(v),
                    Component.empty(),
                    box -> openAccessorSearchModal(f, inst, box)
                );

                table.newRow()
                    .addWidget(makeLabel(labelText))
                    .addWidget(ref[0]);
            };
        }

        if (clz == AnimatorScriptRef.class) {
            return (table, inst) -> {
                @SuppressWarnings("unchecked")
                PopupBox<Object>[] ref = new PopupBox[1];

                ref[0] = new PopupBox<>(
                    font,
                    AnchoredWidget.pixel(LABEL_W + 2), AnchoredWidget.pixel(INDENT),
                    AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
                    AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
                    AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
                    () -> tryGet(f, inst),
                    _ -> {},
                    v -> v instanceof AnimatorScriptRef scriptRef ? scriptRef.displayLabel() : Objects.toString(v),
                    Component.empty(),
                    box -> openAnimatorScriptSelect(f, inst, box)
                );

                table.newRow()
                    .addWidget(makeLabel(labelText))
                    .addWidget(ref[0]);
            };
        }

        if (Holder.class.isAssignableFrom(clz)) {
            return (table, inst) -> {
                @SuppressWarnings("unchecked")
                PopupBox<Object>[] ref = new PopupBox[1];

                ref[0] = new PopupBox<>(
                    font,
                    AnchoredWidget.pixel(LABEL_W + 2), AnchoredWidget.pixel(INDENT),
                    AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
                    AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
                    AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
                    () -> tryGet(f, inst),
                    _ -> {},
                    v -> v instanceof Holder<?> holder ? displayHolder(holder) : Objects.toString(v),
                    Component.empty(),
                    box -> openHolderLookup(f, inst, box)
                );

                table.newRow()
                    .addWidget(makeLabel(labelText))
                    .addWidget(ref[0]);
            };
        }

        if (clz == ResourceKey.class) {
            return (table, inst) -> {
                @SuppressWarnings("unchecked")
                PopupBox<Object>[] ref = new PopupBox[1];

                ref[0] = new PopupBox<>(
                    font,
                    AnchoredWidget.pixel(LABEL_W + 2), AnchoredWidget.pixel(INDENT),
                    AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
                    AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
                    AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
                    () -> tryGet(f, inst),
                    _ -> {},
                    v -> v instanceof ResourceKey<?> key
                        ? (key.identifier().equals(DataHolder.EMPTY_REFERENCE) ? "<none>" : key.identifier().toString())
                        : Objects.toString(v),
                    Component.empty(),
                    box -> openRegistryLookup(f, inst, (ResourceKey<?>) tryGet(f, inst), box)
                );

                table.newRow()
                    .addWidget(makeLabel(labelText))
                    .addWidget(ref[0]);
            };
        }

        if (clz.isEnum()) {
            @SuppressWarnings({"rawtypes", "unchecked"})
            Class<? extends Enum> enumType = (Class<? extends Enum>) clz;
            Object[] constants = enumType.getEnumConstants();

            return (table, inst) -> {
                @SuppressWarnings("unchecked")
                AdvancedComboBox<Object>[] ref = new AdvancedComboBox[1];

                ref[0] = new AdvancedComboBox<>(
                    font,
                    AnchoredWidget.pixel(LABEL_W + 2), AnchoredWidget.pixel(INDENT),
                    AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
                    AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
                    AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
                    () -> tryGet(f, inst),
                    v -> pushFieldChange(f, inst, v, ref),
                    Math.min(8, constants.length),
                    Component.empty(),
                    List.of(constants),
                    v -> ((Enum<?>) v).name().toLowerCase(Locale.ROOT)
                );

                table.newRow()
                    .addWidget(makeLabel(labelText))
                    .addWidget(ref[0]);
            };
        }

        // TODO: editor for ${ft.getSimpleName()}
        return (table, inst) -> {
            Object v = tryGet(f, inst);
            table.newRow()
                .addWidget(makeLabel(labelText))
                .addWidget(new Static(
                    font,
                    AnchoredWidget.pixel(LABEL_W + 2), AnchoredWidget.pixel(INDENT),
                    AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
                    AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
                    AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
                    Component.literal(Objects.toString(v))
                ));
        };
    }

    /// Opens the pick modal for an [AssetAccessor] field, routed by the accessor's runtime
    /// [AssetAccessor.Type] (generics are erased, so the type check must live on the value).
    /// The modal writes the field through [#pushFieldChange] — so it lands in history and the
    /// [PopupBox] display refreshes — and persists the reported preview model, if any.
    private void openAccessorSearchModal(Field f, Object inst, PopupBox<Object> box) {
        AssetAccessor<?> current = (AssetAccessor<?>) tryGet(f, inst);
        @SuppressWarnings("unchecked")
        PopupBox<Object>[] ref = new PopupBox[] { box };

        switch (current.type()) {
            case ANIMATION -> {
                AnimationSelectModal modal = new AnimationSelectModal(font, getPreviewModelField(inst), (animClip, previewModel) -> {
                    // Preview model first: the holder's clip-change listener (fired by the field
                    // write below) reads it to refresh the editor previewer.
                    if (previewModel != null) setPreviewModelField(inst, previewModel);
                    pushFieldChange(f, inst, animClip, ref);

                    // pushFieldChange only routes through valueSetter on undo/redo — the immediate
                    // path assumes the widget already shows the value, which holds for typed/toggled
                    // widgets but not for a modal-driven box. Refresh the display explicitly.
                    ref[0].valueSetter().accept(animClip);
                });

                AkytheraEditorLayoutManager.getInstance().openModalWidgetAtCenter(modal);
            }
            case ARMATURE -> {
                AccessorSelectionModal modal = new AccessorSelectionModal(font, current.type(), accessor -> {
                    pushFieldChange(f, inst, accessor, ref);
                    ref[0].valueSetter().accept(accessor);
                });

                AkytheraEditorLayoutManager.getInstance().openModalWidgetAtCenter(modal);
            }
            // TODO (next stage): STATIC_MESH / SKELETAL_MESH → ModelSelectModal (static meshes need
            //  the engine-side ModelPreviewer.StaticModelComponent first).
            case STATIC_MESH, SKELETAL_MESH -> {}
        }
    }

    private static String displayHolder(Holder<?> holder) {
        return holder.unwrapKey()
            .map(k -> k.identifier().equals(DataHolder.EMPTY_REFERENCE) ? "<none>" : k.identifier().toString())
            .orElse("<none>");
    }

    /// Opens the data pack entry lookup for a [Holder] reference field, dropped-down under the
    /// box. Like the [ResourceKey] branch, the target registry comes from the current value's
    /// key (the field's generic is erased) — so Holder fields must never be null; holders default
    /// them to a dangling placeholder. The picked key is resolved back to the shared live holder
    /// (see [AkytheraProject#getEntryHolderOrDangling]) so the field follows renames of its target.
    private <T> void openHolderLookup(Field f, Object inst, PopupBox<Object> box) {
        @SuppressWarnings("unchecked")
        Holder<T> current = (Holder<T>) tryGet(f, inst);
        ResourceKey<T> currentKey = current.unwrapKey().orElseThrow();
        @SuppressWarnings("unchecked")
        PopupBox<Object>[] ref = new PopupBox[] { box };

        RegistryLookupModal<T> modal = RegistryLookupModal.dataPackEntries(
            font,
            Component.literal(currentKey.registryKey().identifier().getPath()),
            currentKey.registryKey(),
            false,
            key -> {
                Holder<T> picked = AkytheraProject.getCurrentProject().getEntryHolderOrDangling(key);
                pushFieldChange(f, inst, picked, ref);
                ref[0].valueSetter().accept(picked);
            }
        );

        int x = box.getX();
        int y = box.getY() + box.getHeight();
        modal.setX1(AnchoredWidget.pixel(x));
        modal.setY1(AnchoredWidget.pixel(y));

        AkytheraEditorLayoutManager.getInstance().openModalWidget(modal, x, y);
    }

    /// Opens the combined animator-script picker for an [AnimatorScriptRef] field, dropped-down under
    /// the box. Lists both class-based scripts and data pack script blueprints; the picked ref is
    /// written through [#pushFieldChange] (history + display refresh). A data pack pick resolves to
    /// the shared live holder inside [AnimatorScriptRef#dataPack], so the field follows renames.
    private void openAnimatorScriptSelect(Field f, Object inst, PopupBox<Object> box) {
        @SuppressWarnings("unchecked")
        PopupBox<Object>[] ref = new PopupBox[] { box };

        AnimatorScriptSelectModal modal = new AnimatorScriptSelectModal(font, box.getWidth(), picked -> {
            pushFieldChange(f, inst, picked, ref);
            ref[0].valueSetter().accept(picked);
        });

        int x = box.getX();
        int y = box.getY() + box.getHeight();
        modal.setX1(AnchoredWidget.pixel(x));
        modal.setY1(AnchoredWidget.pixel(y));

        AkytheraEditorLayoutManager.getInstance().openModalWidget(modal, x, y);
    }

    /// Opens the data pack entry lookup for a [ResourceKey] field, dropped-down under the box. The
    /// target registry comes from the current **value**'s `registryKey()` (the field's generic is
    /// erased) — so ResourceKey fields must never be null; holders default them to a dangling
    /// placeholder key. Lists project entries merged with the loaded world's registry access.
    private <T> void openRegistryLookup(Field f, Object inst, ResourceKey<T> current, PopupBox<Object> box) {
        @SuppressWarnings("unchecked")
        PopupBox<Object>[] ref = new PopupBox[] { box };

        RegistryLookupModal<T> modal = RegistryLookupModal.dataPackEntries(
            font,
            Component.literal(current.registryKey().identifier().getPath()),
            current.registryKey(),
            false,
            key -> {
                pushFieldChange(f, inst, key, ref);
                ref[0].valueSetter().accept(key);
            }
        );

        int x = box.getX();
        int y = box.getY() + box.getHeight();
        modal.setX1(AnchoredWidget.pixel(x));
        modal.setY1(AnchoredWidget.pixel(y));

        AkytheraEditorLayoutManager.getInstance().openModalWidget(modal, x, y);
    }

    /// Reflectively finds the holder's [EntityPreviewModel] field (if it declares one) and writes
    /// the model a select modal reported, so the picked preview model persists with the entry.
    private static void setPreviewModelField(Object inst, EntityPreviewModel model) {
        Field field = findPreviewModelField(inst);
        if (field != null) trySet(field, inst, model);
    }

    /// Read-side twin of [#setPreviewModelField]: the holder's current [EntityPreviewModel], used
    /// to open select modals pre-selected on the entry's preview model. Null when the holder has
    /// no such field or hasn't picked a model yet.
    private static @Nullable EntityPreviewModel getPreviewModelField(Object inst) {
        Field field = findPreviewModelField(inst);
        return field == null ? null : (EntityPreviewModel) tryGet(field, inst);
    }

    private static @Nullable Field findPreviewModelField(Object inst) {
        for (Class<?> c = inst.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (field.getType() == EntityPreviewModel.class) {
                    field.setAccessible(true);
                    return field;
                }
            }
        }

        return null;
    }

    /// Builds the label cell
    private Static makeLabel(String text) {
        return new Static(
            font,
            AnchoredWidget.pixel(INDENT), AnchoredWidget.pixel(LABEL_W - INDENT),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            Component.translatable(text)
        );
    }

    private static Object tryGet(Field f, Object inst) {
        try {
            return f.get(inst);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static <T, W extends DataBoundWidget<T>> void pushFieldChange(
        Field f, Object inst, T newValue, W[] ref
    ) {
        @SuppressWarnings("unchecked")
        T oldValue = (T) tryGet(f, inst);
        if (Objects.equals(oldValue, newValue)) return;

        trySet(f, inst, newValue);

        HistoryManager.getInstance().pushHistory(new DataChange<>(
            oldValue, newValue,
            val -> {
                trySet(f, inst, val);
                ref[0].valueSetter().accept(val);
                focusUndoneWidget(ref[0]);
            }
        ));
    }

    private static <T, W extends DataBoundWidget<T>> void pushFieldCommit(
        Field f, Object inst, T oldValue, T newValue, W[] ref
    ) {
        if (Objects.equals(oldValue, newValue)) return;

        HistoryManager.getInstance().pushHistory(new DataChange<>(
            oldValue, newValue,
            val -> {
                trySet(f, inst, val);
                ref[0].valueSetter().accept(val);
                focusUndoneWidget(ref[0]);
            }
        ));
    }

    private static void focusUndoneWidget(DataBoundWidget<?> widget) {
        if (!(widget instanceof GuiEventListener listener)) return;
        Screen screen = AkytheraEditorLayoutManager.getInstance().getRecentEditorScreen();
        if (screen == null) return;
        screen.setFocused(listener);
    }

    private static void trySet(Field f, Object inst, Object value) {
        try {
            Method setter = findSetter(inst.getClass(), f);

            if (setter != null) {
                setter.invoke(inst, value);
            } else {
                f.set(inst, value);
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    private static @Nullable Method findSetter(Class<?> type, Field f) {
        String name = "set" + Character.toUpperCase(f.getName().charAt(0)) + f.getName().substring(1);

        try {
            Method m = type.getMethod(name, f.getType());
            return Modifier.isPublic(m.getModifiers()) ? m : null;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    @FunctionalInterface
    private interface RowBuilder {
        void apply(WidgetTable table, Object instance);
    }
}