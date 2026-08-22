package com.yesman.akytheralabs.editor;

import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.client.gui.widget.*;
import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akythera.client.gui.widget.common.DataBoundWidget;
import com.yesman.akythera.core.animation.playable.AnimationSequence;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.EnumerableDataBlock;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.akythera.animation.AnimationRef;
import com.yesman.akytheralabs.akythera.animation.AnimatorScriptHolder;
import com.yesman.akytheralabs.akythera.asset.AnimationMetadataHolder;
import com.yesman.akytheralabs.akythera.asset.ReferencePose;
import com.yesman.akytheralabs.akythera.entitypatch.component.AnimatorScriptRef;
import com.yesman.akytheralabs.dataholder.DataHolder;
import com.yesman.akytheralabs.dataholder.EntityPreviewModel;
import com.yesman.akytheralabs.dataholder.TransformHolder;
import com.yesman.akytheralabs.editor.EditorFieldScanner.EditorField;
import com.yesman.akytheralabs.editor.historytrack.DataChange;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.WorkHistory;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.screen.NodeGraphTabHost;
import com.yesman.akytheralabs.gui.widget.*;
import com.yesman.akytheralabs.gui.widget.main.AccessorSelectionModal;
import com.yesman.akytheralabs.gui.widget.main.AnimationSelectModal;
import com.yesman.akytheralabs.gui.widget.main.AnimatorScriptSelectModal;
import com.yesman.akytheralabs.gui.widget.main.ItemSelectModal;
import com.yesman.akytheralabs.gui.widget.main.ReferencePoseSelectModal;
import com.yesman.akytheralabs.gui.widget.main.RegistryLookupModal;
import com.yesman.akytheralabs.gui.widget.script.ContextEntryNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.ScriptGraphData;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/// Maps annotated holder fields to anchored editor widgets and stuffs them
/// into a [WidgetTable] row. Instantiate once per panel (it caches per-class
/// row producers); call [#fillWidgetTable] every time the selection changes.
///
/// Editor widgets are dispatched by value type through a static type→[WidgetProducer]
/// registry. Built-in types are registered in the static initializer; custom blueprint
/// types can be added via [#registerWidgetProducer] — register during client init,
/// before the first editor screen builds rows for the holder class (resolved producers
/// are captured in per-factory row caches). Producers read and write one [ValueSlot],
/// never a [Field] directly, which is what lets a list element carry the same widget its
/// scalar-field equivalent would.
public final class EditorWidgetFactory {
    /// Minimum label column width; the effective width is proportional, see [#labelWidth].
    public static final int LABEL_W = 96;
    /// Fraction of the row width the label column takes (subject to the [#LABEL_W] floor).
    public static final float LABEL_RATIO = 0.35F;
    public static final int INDENT = 8;
    public static final int ROW_H = 20;
    public static final int COLUMN_GAP = 4;

    /// Pixels a list element row keeps clear on its right for the `✕` button — [#sectionButton]
    /// anchors it 4px off the margin at 16px wide, and the editor columns must stop short of it.
    public static final int ENTRY_BUTTON_GUTTER = 24;

    /// Produces the editor widget(s) — label excluded — for one [ValueSlot].
    /// Returned widgets must implement [AnchoredWidget] with a `TOP_HEIGHT` vertical
    /// anchor ([WidgetTable#addWidget]'s rule); use [#columnStart] / [#columnSize] for
    /// horizontal anchors so multi-widget rows split the editor area evenly and list
    /// element rows leave the `✕` gutter alone.
    ///
    /// [#appendRows] is what the row builders call. A composite value whose parts each want a
    /// labelled row of their own overrides it and opens those rows itself.
    @FunctionalInterface
    public interface WidgetProducer {
        List<? extends AbstractWidget> produce(EditorWidgetFactory factory, ValueSlot slot);

        /// Lays `slot` out as rows of `table`, inset `indent` pixels — the row that carries the
        /// value, and any the producer wants under it. The default is the one-row shape a scalar
        /// editor has: the label cell, then [#produce]'s widgets in the editor columns.
        ///
        /// The last row opened stays the current one, so a caller can append to it afterwards —
        /// that is where a list element's `✕` lands, see [#appendList].
        default void appendRows(EditorWidgetFactory factory, WidgetTable table, ValueSlot slot, int indent) {
            table.newRow(indent).addWidget(makeLabel(factory.font, slot.labelComponent()));

            for (AbstractWidget widget : produce(factory, slot)) {
                addProduced(table, widget);
            }
        }
    }

    /// One editable value: a holder field, or one element of a holder's [List] field.
    ///
    /// Producers dispatch on [#type] — the **declared** type, because the runtime value cannot
    /// supply it (an enum constant with a body reports its anonymous subclass; a `Holder<T>`'s `T`
    /// is erased) — and route history through [#pushSlotChange] / [#pushSlotCommit], so an element
    /// edit lands on the undo stack exactly like a field edit.
    ///
    /// [#owner] is the holder in both cases, never the element: owner-scoped lookups (the
    /// [EntityPreviewModel] field, [NodeGraphTitleProvider]) must keep resolving against the object
    /// that declares the property.
    public sealed interface ValueSlot permits FieldSlot, ElementSlot {
        Class<?> type();

        EditorEditable annotation();

        String label();

        /// The label cell's text: a field labels itself by lang key, a list element by its literal
        /// `#n` marker.
        Component labelComponent();

        Object owner();

        /// The declaring field, or null for a list element. Annotation-driven producers
        /// ([EditorNodeGraph]) read it defensively.
        @Nullable Field field();

        Object get();

        void set(Object value);

        /// Right-hand pixels the row's editor columns must leave free, see [#ENTRY_BUTTON_GUTTER].
        default int rightGutter() {
            return 0;
        }
    }

    /// What a widget reads and writes in place of its [ValueSlot]. Under
    /// [EditorEditable#setOnCommit] it buffers the in-flight value and the slot stays untouched until
    /// the commit lands it; otherwise it is the slot, pass-through.
    ///
    /// The buffer is what a deferred widget's data provider reads. [ClampedNumberBox] decides
    /// whether a commit changed anything by re-reading that provider, and a provider pointing at the
    /// still-unwritten slot would report "unchanged" and throw the edit away.
    private static final class PendingValue {
        /// Null while the value is being buffered — the deferred case.
        private final @Nullable ValueSlot slot;

        private Object value;

        private PendingValue(final @Nullable ValueSlot slot, final Object value) {
            this.slot = slot;
            this.value = value;
        }

        static PendingValue of(final ValueSlot slot) {
            return new PendingValue(slot.annotation().setOnCommit() ? null : slot, slot.get());
        }

        Object get() {
            return slot == null ? value : slot.get();
        }

        void set(final Object value) {
            this.value = value;

            if (slot != null) slot.set(value);
        }
    }

    /// A holder field. Writes prefer the bean setter, see [#findSetter].
    public record FieldSlot(EditorField editorField, Object owner) implements ValueSlot {
        @Override
        public Class<?> type() {
            return editorField.field().getType();
        }

        @Override
        public EditorEditable annotation() {
            return editorField.annotation();
        }

        @Override
        public String label() {
            return editorField.label();
        }

        @Override
        public Component labelComponent() {
            return Component.translatable(editorField.label());
        }

        @Override
        public Field field() {
            return editorField.field();
        }

        @Override
        public Object get() {
            return tryGet(editorField.field(), owner);
        }

        @Override
        public void set(Object value) {
            trySet(editorField.field(), owner, value);
        }
    }

    /// One element of a holder's [List] field: `type` is the list's element type ([#elementType]),
    /// `annotation` the list field's own — so `min` / `max` on a numeric list apply per element.
    /// Writes land straight in the backing list, which is the same live list the `+` / `✕` buttons
    /// mutate.
    public record ElementSlot(
        List<Object> list, int index, Class<?> type, EditorEditable annotation, Object owner
    ) implements ValueSlot {
        @Override
        public String label() {
            return "#" + (index + 1);
        }

        @Override
        public Component labelComponent() {
            return Component.literal(label());
        }

        @Override
        public @Nullable Field field() {
            return null;
        }

        @Override
        public Object get() {
            return list.get(index);
        }

        @Override
        public void set(Object value) {
            list.set(index, value);
        }

        @Override
        public int rightGutter() {
            return ENTRY_BUTTON_GUTTER;
        }
    }

    /// Keyed by [ParseUtils#box]-normalized classes so primitives and wrappers share one
    /// entry. Interface and superclass keys (e.g. [EnumerableDataBlock], [Enum]) are
    /// resolved by the hierarchy walk in [#findProducer].
    private static final Map<Class<?>, WidgetProducer> PRODUCERS = new ConcurrentHashMap<>();

    /// Registers an editor widget producer for a value type. Primitive and wrapper
    /// classes are interchangeable; interface / superclass keys match all implementors
    /// unless a nearer registration exists.
    public static void registerWidgetProducer(Class<?> type, WidgetProducer producer) {
        PRODUCERS.put(ParseUtils.box(type), producer);
    }

    static {
        registerWidgetProducer(Float.class, EditorWidgetFactory::floatBox);
        registerWidgetProducer(Double.class, EditorWidgetFactory::doubleBox);
        registerWidgetProducer(Integer.class, EditorWidgetFactory::intBox);
        registerWidgetProducer(Boolean.class, EditorWidgetFactory::checkBox);
        registerWidgetProducer(String.class, EditorWidgetFactory::stringBox);
        registerWidgetProducer(Vec3.class, EditorWidgetFactory::vec3Boxes);
        registerWidgetProducer(EnumerableDataBlock.class, EditorWidgetFactory::enumerableCombo);
        registerWidgetProducer(AssetAccessor.class, EditorWidgetFactory::accessorPopup);
        registerWidgetProducer(AnimatorScriptRef.class, EditorWidgetFactory::animatorScriptPopup);
        registerWidgetProducer(AnimationRef.class, EditorWidgetFactory::animationRefPopup);
        registerWidgetProducer(ReferencePose.class, new ReferencePoseProducer());
        registerWidgetProducer(Item.class, EditorWidgetFactory::itemPopup);
        registerWidgetProducer(Holder.class, EditorWidgetFactory::holderPopup);
        registerWidgetProducer(ResourceKey.class, EditorWidgetFactory::resourceKeyPopup);
        registerWidgetProducer(Enum.class, EditorWidgetFactory::enumCombo);
        registerWidgetProducer(ScriptGraphData.class, EditorWidgetFactory::nodeGraphButton);
        registerWidgetProducer(Quaterniond.class, EditorWidgetFactory::quaternionBoxes);
    }

    private final Map<Class<?>, List<RowBuilder>> rowCache = new ConcurrentHashMap<>();

    private final Font font;

    /// The object surrounding the instance being filled — a component holder's parent, for the patch
    /// editors' Details panels. Set for the duration of one [#fillWidgetTable] and read by
    /// [#resolveCandidates], so producers must consume it while they build, not later.
    @Nullable
    private Object context;

    public EditorWidgetFactory(Font font) {
        this.font = font;
    }

    public Font font() {
        return font;
    }

    /// Clears the table, then renders `instance`'s `@EditorEditable` rows — nested editor objects
    /// recurse one indent level deeper, see [#appendObject] — and runs [WidgetTable#initialize]
    /// so DataBoundWidgets pull their initial values. Without a `rebuild` callback, [List] rows are
    /// skipped (no way to re-render after add/remove).
    public void fillWidgetTable(WidgetTable table, Object instance) {
        fillWidgetTable(table, instance, null, null);
    }

    /// Variant for panels that can re-render themselves: `rebuild` re-fills the whole table after
    /// a list add/remove (and on its undo/redo), which enables [List] row rendering — the object's
    /// lists, and those of every nested object, inline at their depth.
    public void fillWidgetTable(WidgetTable table, Object instance, @Nullable Runnable rebuild) {
        fillWidgetTable(table, instance, rebuild, null);
    }

    /// Variant for panels that know what `instance` hangs off: `context` is offered to the rows that
    /// need their surroundings to know what to offer ([EditorEditable#candidates]). The Details panels
    /// pass the shown component's parent.
    public void fillWidgetTable(WidgetTable table, Object instance, @Nullable Runnable rebuild, @Nullable Object context) {
        this.context = context;

        try {
            table.clearWidgets();
            appendObject(table, instance, 0, rebuild);
            table.initialize(false);
        } finally {
            this.context = null;
        }
    }

    /// Append `instance`'s editor rows to `table` without clearing existing
    /// rows. Use after [#fillWidgetTable] to stack rows from a second object
    /// (e.g. a notify's wrapping `MutableNotify` then the inner holder).
    public void appendRowsFrom(WidgetTable table, Object instance) {
        appendObject(table, instance, 0, null);
    }

    /// Renders one object's editor rows at `indentLevel` — each level insets its rows by [#INDENT]
    /// pixels through [WidgetTable#newRow(int)], so where a row belongs reads off its left edge.
    /// Rows are grouped into [EditorEditable#category] blocks that indent below `indentLevel` on
    /// their own, see [#buildRowBuilders]; nested objects and [List] fields recurse, see
    /// [#builderFor].
    private void appendObject(WidgetTable table, Object instance, int indentLevel, @Nullable Runnable rebuild) {
        for (RowBuilder rb : rowCache.computeIfAbsent(instance.getClass(), this::buildRowBuilders)) {
            rb.apply(table, instance, indentLevel, rebuild);
        }
    }

    /// Folds the class's fields into the [EditorEditable#category] tree, then flattens it back into
    /// a flat row builder list — each builder carrying the extra indent depth its category block
    /// earned. Categories are laid out as a unit: a block sits where its lowest-ordered member
    /// would have gone, and every member follows it there even if other fields' `order` values
    /// interleave with them.
    private List<RowBuilder> buildRowBuilders(Class<?> type) {
        CategoryNode root = new CategoryNode("");

        // fieldsOf is pre-sorted ascending by order, and every sort below is stable, so a field's
        // rank within its block — and a block's rank among its siblings — follows from insertion.
        for (EditorField editorField : EditorFieldScanner.fieldsOf(type)) {
            root.insert(splitCategory(editorField.annotation().category()), 0, editorField);
        }

        List<RowBuilder> out = new ArrayList<>();
        appendCategory(root, out, 0);

        return List.copyOf(out);
    }

    /// Emits `node`'s rows at `depth`: leaf fields become their own row builders, nested categories
    /// a title row followed by their contents one level deeper. Siblings sort by [CategoryEntry#order]
    /// — a field's own `order`, a category's smallest — which is what makes a block travel to its
    /// earliest member instead of fragmenting around the fields ordered between its members.
    private void appendCategory(CategoryNode node, List<RowBuilder> out, int depth) {
        List<CategoryEntry> entries = new ArrayList<>(node.entries);
        entries.sort(Comparator.comparingInt(CategoryEntry::order));

        for (CategoryEntry entry : entries) {
            switch (entry) {
                case FieldEntry(EditorField editorField) -> out.add(builderFor(editorField, depth));
                case CategoryNode child -> {
                    out.add((table, _, indentLevel, _) -> table.newRow((indentLevel + depth) * INDENT)
                        .addWidget(headerLabel(Component.translatable(child.title))));

                    appendCategory(child, out, depth + 1);
                }
            }
        }
    }

    /// Segments of a category path. Each segment is a whole lang key, so the separator is `/` and
    /// not `.`; blank segments drop out, which leaves the default empty category as no path at all
    /// (an uncategorized, root-level row).
    private static String[] splitCategory(String category) {
        return Arrays.stream(category.split("/"))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toArray(String[]::new);
    }

    /// A row slot under one category: either a field of its own or a nested category.
    private sealed interface CategoryEntry {
        /// The value this entry sorts by among its siblings.
        int order();
    }

    private record FieldEntry(EditorField field) implements CategoryEntry {
        @Override
        public int order() {
            return field.annotation().order();
        }
    }

    /// One node of the category tree. `entries` interleaves this node's own fields with its child
    /// categories, so both compete for the same row slots; `children` is only the by-name index
    /// [#insert] rejoins later fields of the same category through — the coupling itself.
    private static final class CategoryNode implements CategoryEntry {
        private final String title;
        private final Map<String, CategoryNode> children = new LinkedHashMap<>();
        private final List<CategoryEntry> entries = new ArrayList<>();
        private int minOrder = Integer.MAX_VALUE;

        private CategoryNode(String title) {
            this.title = title;
        }

        /// A category ranks by its lowest-ordered member, at every depth: the minimum propagates
        /// down the whole path being inserted, so a deeply nested field drags its outermost
        /// category block up with it.
        @Override
        public int order() {
            return minOrder;
        }

        private void insert(String[] path, int index, EditorField field) {
            minOrder = Math.min(minOrder, field.annotation().order());

            if (index == path.length) {
                entries.add(new FieldEntry(field));
                return;
            }

            CategoryNode child = children.get(path[index]);

            // Only the first field of a category takes a row slot; the rest rejoin the node it
            // already claimed, which is exactly where the coupling happens.
            if (child == null) {
                child = new CategoryNode(path[index]);
                children.put(path[index], child);
                entries.add(child);
            }

            child.insert(path, index + 1, field);
        }
    }

    private RowBuilder builderFor(EditorField editorField, int depth) {
        Field f = editorField.field();
        String labelText = editorField.label();

        // A List field is the list editor itself, dispatched ahead of the producer registry. It
        // needs the rebuild callback to re-render after an add / remove; without one the whole row
        // drops out.
        if (List.class.isAssignableFrom(f.getType())) {
            return (table, inst, indentLevel, rebuild) -> {
                if (rebuild == null) return;
                appendList(table, new FieldSlot(editorField, inst), indentLevel + depth, rebuild);
            };
        }

        // A candidate list outranks the value's own type: the point of it is that the field's valid
        // values are narrower than everything its type can hold.
        WidgetProducer producer = editorField.annotation().candidates().isBlank()
            ? findProducer(f.getType())
            : EditorWidgetFactory::candidateCombo;

        if (producer == null) {
            // Nested editor object: no widget for the type itself, but the type declares its own
            // @EditorEditable rows — header label, then the value's rows one level deeper. The
            // declared type only gates detection; the recursion dispatches on the runtime class.
            if (!EditorFieldScanner.fieldsOf(f.getType()).isEmpty()) {
                return (table, inst, indentLevel, rebuild) -> {
                    table.newRow((indentLevel + depth) * INDENT).addWidget(headerLabel(Component.translatable(labelText)));

                    Object nested = tryGet(f, inst);
                    if (nested != null) appendObject(table, nested, indentLevel + depth + 1, rebuild);
                };
            }

            // TODO: editor for ${ft.getSimpleName()}
            return (table, inst, indentLevel, _) -> {
                FieldSlot slot = new FieldSlot(editorField, inst);
                table.newRow((indentLevel + depth) * INDENT)
                    .addWidget(makeTranslatableLabel(font, labelText))
                    .addWidget(readOnlyDisplay(slot));
            };
        }

        return (table, inst, indentLevel, _) ->
            producer.appendRows(this, table, new FieldSlot(editorField, inst), (indentLevel + depth) * INDENT);
    }

    // ***************************************************************
    // List rows
    // ***************************************************************

    /// One [List] field: a header row carrying the field's label and a `+` button, then one block
    /// per element at `listLevel + 1` — a scalar element is a single `#n` row (its widget plus a
    /// `✕`), an editor object a `#n` / `✕` marker row followed by the object's own rows. Adds and
    /// removes mutate the live backing list, land on the undo stack, and re-render through `rebuild`.
    ///
    /// A blank [EditorEditable#defaultElement] drops both buttons and leaves the elements read-only.
    private void appendList(WidgetTable table, ValueSlot listSlot, int listLevel, Runnable rebuild) {
        @SuppressWarnings("unchecked")
        List<Object> entries = (List<Object>) listSlot.get();
        if (entries == null) return;

        Class<?> declared = elementType(listSlot.field());
        Supplier<Object> elementFactory = defaultElementFactory(listSlot, declared);

        table.newRow(listLevel * INDENT).addWidget(headerLabel(Component.translatable(listSlot.label())));

        if (elementFactory != null) {
            table.addWidget(sectionButton(Component.literal("+"), () -> addEntry(entries, elementFactory.get(), rebuild)));
        }

        for (int i = 0; i < entries.size(); i++) {
            int index = i;
            Object value = entries.get(index);

            // The declared element type is authoritative; a raw or wildcard list falls back to the
            // element's runtime class, which is all the object recursion below needs anyway.
            Class<?> slotType = declared != null ? declared : (value == null ? Object.class : value.getClass());
            ElementSlot slot = new ElementSlot(entries, index, slotType, listSlot.annotation(), listSlot.owner());

            // The list field's own candidates apply per element, like its min / max do.
            WidgetProducer producer = listSlot.annotation().candidates().isBlank()
                ? findProducer(slotType)
                : EditorWidgetFactory::candidateCombo;

            if (producer == null) {
                // Editor object element: the marker row runs to the margin like a section title did,
                // then the object's own rows follow one level deeper.
                table.newRow((listLevel + 1) * INDENT).addWidget(headerLabel(Component.literal(slot.label())));
                if (elementFactory != null) {
                    table.addWidget(sectionButton(Component.literal("✕"), () -> removeEntry(entries, index, rebuild)));
                }

                if (value != null) appendObject(table, value, listLevel + 1, rebuild);
                continue;
            }

            // Scalar element: `#n` takes the label column and the widget the editor columns, so the
            // whole element is one row.
            producer.appendRows(this, table, slot, (listLevel + 1) * INDENT);

            if (elementFactory != null) {
                table.addWidget(sectionButton(Component.literal("✕"), () -> removeEntry(entries, index, rebuild)));
            }
        }
    }

    /// The element type of a [List] field, off its generic signature: `List<Holder<AnimationMontage>>`
    /// resolves to [Holder], since the producer registry is keyed by raw types. A raw, wildcard or
    /// `List<Object>` field resolves to null — [#appendList] then dispatches per element on the
    /// runtime class.
    private static @Nullable Class<?> elementType(@Nullable Field f) {
        if (f == null || !(f.getGenericType() instanceof ParameterizedType parameterized)) return null;

        Type[] args = parameterized.getActualTypeArguments();
        return args.length == 1 ? rawTypeOf(args[0]) : null;
    }

    private static @Nullable Class<?> rawTypeOf(Type type) {
        return switch (type) {
            case Class<?> c -> c == Object.class ? null : c;
            case ParameterizedType p -> rawTypeOf(p.getRawType());
            // Wildcards and type variables carry nothing the producer registry can key on.
            default -> null;
        };
    }

    /// The `+` button's element factory: [EditorEditable#defaultElement]'s static no-argument method
    /// on the class declaring the list. Null when the annotation leaves it blank, which renders the
    /// list read-only — see [#appendList]. A name matching no such method throws at row-build time.
    private static @Nullable Supplier<Object> defaultElementFactory(ValueSlot listSlot, @Nullable Class<?> elementType) {
        String name = listSlot.annotation().defaultElement();
        if (name.isBlank()) return null;

        Field f = listSlot.field();
        Class<?> declaring = f != null ? f.getDeclaringClass() : listSlot.owner().getClass();

        Method factory;

        try {
            factory = declaring.getDeclaredMethod(name);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(
                "@EditorEditable(defaultElement) names no no-argument method: " + declaring.getName() + "#" + name, e);
        }

        if (!Modifier.isStatic(factory.getModifiers())) {
            throw new IllegalStateException(
                "@EditorEditable(defaultElement) must name a *static* method: " + declaring.getName() + "#" + name);
        }

        if (elementType != null && !ParseUtils.box(elementType).isAssignableFrom(ParseUtils.box(factory.getReturnType()))) {
            throw new IllegalStateException(
                "@EditorEditable(defaultElement) returns " + factory.getReturnType().getName()
                    + ", which is not a " + elementType.getName() + ": " + declaring.getName() + "#" + name);
        }

        factory.setAccessible(true);

        return () -> {
            try {
                return factory.invoke(null);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        };
    }

    /// Header cell (list titles, nested-object labels, element markers) — unlike [#makeTranslatableLabel]
    /// it runs to the row's right margin, since these titles regularly outgrow the label column.
    private Static headerLabel(Component title) {
        return new Static(
            font,
            AnchoredWidget.pixel(INDENT), AnchoredWidget.pixel(INDENT + 20),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            title
        );
    }

    /// Read-only value display for a type no producer covers: fills the editor area, minus the
    /// slot's own right gutter so a list element's `✕` stays clickable.
    private Static readOnlyDisplay(ValueSlot slot) {
        return new Static(
            font,
            (rect, xy) -> labelWidth(rect) + 2, AnchoredWidget.pixel(INDENT + slot.rightGutter()),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            Component.literal(Objects.toString(slot.get()))
        );
    }

    private AnchoredButton sectionButton(Component label, Runnable onPress) {
        return AnchoredButton.buttonBuilder(font, label, _ -> onPress.run())
            .xUnits(AnchoredWidget.pixel(4), AnchoredWidget.pixel(16))
            .yUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(16))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.TOP_HEIGHT)
            .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
            .build();
    }

    private static void addEntry(List<Object> entries, Object entry, Runnable rebuild) {
        entries.add(entry);

        HistoryManager.getInstance().pushHistory(new WorkHistory() {
            @Override
            public void undo() {
                entries.remove(entry);
                rebuild.run();
            }

            @Override
            public void redo() {
                if (!entries.contains(entry)) entries.add(entry);
                rebuild.run();
            }
        });

        rebuild.run();
    }

    private static void removeEntry(List<Object> entries, int index, Runnable rebuild) {
        Object removed = entries.remove(index);

        HistoryManager.getInstance().pushHistory(new WorkHistory() {
            @Override
            public void undo() {
                entries.add(Math.min(index, entries.size()), removed);
                rebuild.run();
            }

            @Override
            public void redo() {
                entries.remove(removed);
                rebuild.run();
            }
        });

        rebuild.run();
    }

    /// Nearest registered producer for the (boxed) value type: the class itself, then its
    /// interfaces in declaration order, then the superclass — breadth-first, so proximity
    /// wins and interfaces beat the superclass on the same level (an enum implementing
    /// [EnumerableDataBlock] gets the data block combo, not the plain enum combo).
    private static @Nullable WidgetProducer findProducer(Class<?> valueType) {
        Deque<Class<?>> queue = new ArrayDeque<>();
        Set<Class<?>> visited = new HashSet<>();
        queue.add(ParseUtils.box(valueType));

        while (!queue.isEmpty()) {
            Class<?> c = queue.poll();
            if (!visited.add(c)) continue;

            WidgetProducer producer = PRODUCERS.get(c);
            if (producer != null) return producer;

            queue.addAll(List.of(c.getInterfaces()));
            if (c.getSuperclass() != null && c.getSuperclass() != Object.class) {
                queue.add(c.getSuperclass());
            }
        }

        return null;
    }

    /// [WidgetTable#addWidget] wants `AbstractWidget & AnchoredWidget`; producers hand
    /// back plain [AbstractWidget]s, so the intersection is restored here in one place.
    @SuppressWarnings("unchecked")
    private static <W extends AbstractWidget & AnchoredWidget> void addProduced(WidgetTable table, AbstractWidget widget) {
        table.addWidget((W) widget);
    }

    // ***************************************************************
    // Column anchor units
    // ***************************************************************

    /// The label column width for one row rectangle: proportional ([#LABEL_RATIO] of the row
    /// width) with [#LABEL_W] as the floor, so long labels get room on wide panels and narrow
    /// panels don't collapse the column. Resolved per row, so indented rows shrink it with them.
    public static int labelWidth(ScreenRectangle rect) {
        return Math.max(LABEL_W, (int) (rect.width() * LABEL_RATIO));
    }

    /// Horizontal `x1` unit for editor column `index` of `count` of one slot's row: the editor area
    /// right of the label, minus the slot's [ValueSlot#rightGutter], split into equal columns with
    /// [#COLUMN_GAP] between them. Resolved against the table rect at relocate time, which no
    /// [Pixel]/[Fraction] combination can express, the label split and the column split both being
    /// proportional. Pair with [AnchoredWidget.HorizontalAnchorType#LEFT_WIDTH] and [#columnSize].
    public static AnchoredWidget.Unit columnStart(ValueSlot slot, int index, int count) {
        return (rect, xy) -> labelWidth(rect) + 2
            + index * (columnWidth(rect, count, labelWidth(rect), slot.rightGutter()) + COLUMN_GAP);
    }

    /// Horizontal `x2` (width) unit for editor column `index` of `count` of one slot's row. The last
    /// column runs to the right margin minus the slot's gutter, absorbing the integer-division
    /// remainder.
    public static AnchoredWidget.Unit columnSize(ValueSlot slot, int index, int count) {
        return (rect, xy) -> {
            int label = labelWidth(rect);
            int gutter = slot.rightGutter();

            return index == count - 1
                ? rect.width() - INDENT - gutter - (label + 2 + index * (columnWidth(rect, count, label, gutter) + COLUMN_GAP))
                : columnWidth(rect, count, label, gutter);
        };
    }

    /// Gutter-free variant for tables that lay rows out themselves (e.g. the animator script
    /// variables Details panel).
    public static AnchoredWidget.Unit columnStart(int index, int count) {
        return (rect, xy) -> columnStart(index, count, labelWidth(rect)).pixels(rect, xy);
    }

    /// Variant for tables whose label cell is a fixed width instead of the proportional
    /// [#labelWidth] (e.g. the animator script variables Details panel).
    public static AnchoredWidget.Unit columnStart(int index, int count, int labelWidth) {
        return (rect, xy) -> labelWidth + 2 + index * (columnWidth(rect, count, labelWidth, 0) + COLUMN_GAP);
    }

    /// Gutter-free variant, see [#columnStart(int,int)].
    public static AnchoredWidget.Unit columnSize(int index, int count) {
        return (rect, xy) -> columnSize(index, count, labelWidth(rect)).pixels(rect, xy);
    }

    /// Variant for tables whose label cell is a fixed width, see [#columnStart].
    public static AnchoredWidget.Unit columnSize(int index, int count, int labelWidth) {
        return (rect, xy) -> index == count - 1
            ? rect.width() - INDENT - (labelWidth + 2 + index * (columnWidth(rect, count, labelWidth, 0) + COLUMN_GAP))
            : columnWidth(rect, count, labelWidth, 0);
    }

    private static int columnWidth(ScreenRectangle rect, int count, int labelWidth, int gutter) {
        return (rect.width() - gutter - (labelWidth + 2) - INDENT - (count - 1) * COLUMN_GAP) / count;
    }

    // ***************************************************************
    // Built-in producers
    // ***************************************************************

    private static List<? extends AbstractWidget> floatBox(EditorWidgetFactory factory, ValueSlot slot) {
        EditorEditable ann = slot.annotation();
        PendingValue pending = PendingValue.of(slot);

        ClampedNumberBox<Float> box = new ClampedNumberBox<>(
            factory.font,
            columnStart(slot, 0, 1), columnSize(slot, 0, 1),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            () -> (Float) pending.get(),
            pending::set,
            Component.empty(),
            false,
            ann.min(), ann.max(), 0.1F, -0.1F,
            Float::parseFloat,
            ParseUtils::floatingPointToString,
            Float::compareTo,
            Float::sum
        );

        box.setOnCommitted((oldVal, newVal) -> pushSlotCommit(slot, oldVal, newVal, box));

        return List.of(box);
    }

    private static List<? extends AbstractWidget> doubleBox(EditorWidgetFactory factory, ValueSlot slot) {
        EditorEditable ann = slot.annotation();
        PendingValue pending = PendingValue.of(slot);

        ClampedNumberBox<Double> box = new ClampedNumberBox<>(
            factory.font,
            columnStart(slot, 0, 1), columnSize(slot, 0, 1),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            () -> (Double) pending.get(),
            pending::set,
            Component.empty(),
            false,
            (double) ann.min(), (double) ann.max(), 0.1D, -0.1D,
            Double::parseDouble,
            d -> Double.toString(d),
            Double::compareTo,
            Double::sum
        );

        box.setOnCommitted((oldVal, newVal) -> pushSlotCommit(slot, oldVal, newVal, box));

        return List.of(box);
    }

    private static List<? extends AbstractWidget> intBox(EditorWidgetFactory factory, ValueSlot slot) {
        EditorEditable ann = slot.annotation();
        PendingValue pending = PendingValue.of(slot);

        ClampedNumberBox<Integer> box = new ClampedNumberBox<>(
            factory.font,
            columnStart(slot, 0, 1), columnSize(slot, 0, 1),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            () -> (Integer) pending.get(),
            pending::set,
            Component.empty(),
            false,
            (int) ann.min(), (int) ann.max(), 1, -1,
            Integer::parseInt, String::valueOf,
            Integer::compareTo, Integer::sum
        );

        box.setOnCommitted((oldVal, newVal) -> pushSlotCommit(slot, oldVal, newVal, box));

        return List.of(box);
    }

    private static List<? extends AbstractWidget> checkBox(EditorWidgetFactory factory, ValueSlot slot) {
        // The change consumer is a constructor argument, so it must late-bind the widget
        // it configures through a mutable cell — a lambda can't capture the local before
        // it is assigned. setOnCommitted-style widgets don't need this.
        CheckBox[] ref = new CheckBox[1];

        ref[0] = new CheckBox(
            factory.font,
            (rect, xy) -> labelWidth(rect) + 2, AnchoredWidget.pixel(12),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(12),
            AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            () -> (Boolean) slot.get(),
            v -> pushSlotChange(slot, v, ref[0]),
            Component.empty(),
            false
        );

        return List.of(ref[0]);
    }

    private static List<? extends AbstractWidget> stringBox(EditorWidgetFactory factory, ValueSlot slot) {
        PendingValue pending = PendingValue.of(slot);

        AdvancedEditBox<String> box = new AdvancedEditBox<>(
            factory.font,
            columnStart(slot, 0, 1), columnSize(slot, 0, 1),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            () -> (String) pending.get(),
            pending::set,
            _ -> true, s -> s, s -> s,
            Component.empty()
        );

        box.setTheme(AkytheraLabsEditBoxTheme.DEFAULT_EDIT_BOX);
        box.setOnCommitted((oldVal, newVal) -> pushSlotCommit(slot, oldVal, newVal, box));

        return List.of(box);
    }

    /// Axis component of an immutable [Vec3] (0 = x, 1 = y, 2 = z). Shared with other
    /// per-axis Vec3 editors (e.g. the variables Details panel's default-value row).
    public static double axisOf(Vec3 v, int axis) {
        return switch (axis) { case 0 -> v.x; case 1 -> v.y; default -> v.z; };
    }

    /// Copy of `v` with one axis replaced, see [#axisOf].
    public static Vec3 withAxis(Vec3 v, int axis, double value) {
        return switch (axis) {
            case 0 -> new Vec3(value, v.y, v.z);
            case 1 -> new Vec3(v.x, value, v.z);
            default -> new Vec3(v.x, v.y, value);
        };
    }

    /// Three per-axis double boxes over one immutable [Vec3] slot. Axis setters write a
    /// whole new vector live (like the float box writing through the slot while typing);
    /// a commit pushes one [DataChange] holding full old/new vectors, and its replay
    /// refreshes all three boxes.
    private static List<? extends AbstractWidget> vec3Boxes(EditorWidgetFactory factory, ValueSlot slot) {
        EditorEditable ann = slot.annotation();
        PendingValue pending = PendingValue.of(slot);

        @SuppressWarnings("unchecked")
        ClampedNumberBox<Double>[] boxes = new ClampedNumberBox[3];

        for (int i = 0; i < 3; i++) {
            int axis = i;

            boxes[axis] = new ClampedNumberBox<>(
                factory.font,
                columnStart(slot, axis, 3), columnSize(slot, axis, 3),
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
                AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH,
                AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
                () -> axisOf((Vec3) pending.get(), axis),
                v -> pending.set(withAxis((Vec3) pending.get(), axis, v)),
                Component.empty(),
                false,
                (double) ann.min(), (double) ann.max(), 0.1D, -0.1D,
                Double::parseDouble,
                d -> Double.toString(d),
                Double::compareTo,
                Double::sum
            );

            boxes[axis].setOnCommitted((oldVal, newVal) -> {
                Vec3 newVec = (Vec3) pending.get(); // the axis setter already wrote the new value
                Vec3 oldVec = withAxis(newVec, axis, oldVal);

                // Deferred: nothing has reached the slot yet, so the commit is what lands it.
                if (ann.setOnCommit()) slot.set(newVec);

                if (Objects.equals(oldVec, newVec)) return;

                HistoryManager.getInstance().pushHistory(new DataChange<>(
                    oldVec, newVec,
                    val -> {
                        slot.set(val);
                        for (int j = 0; j < boxes.length; j++) {
                            boxes[j].valueSetter().accept(axisOf(val, j));
                        }
                        focusUndoneWidget(boxes[axis]);
                    }
                ));
            });
        }

        return List.of(boxes);
    }

    /// Component of a [Quaterniond] (0 = x, 1 = y, 2 = z, 3 = w).
    public static double componentOf(Quaterniond q, int component) {
        return switch (component) { case 0 -> q.x; case 1 -> q.y; case 2 -> q.z; default -> q.w; };
    }

    /// Copy of `q` with one component replaced, see [#componentOf].
    public static Quaterniond withComponent(Quaterniond q, int component, double value) {
        return switch (component) {
            case 0 -> new Quaterniond(value, q.y, q.z, q.w);
            case 1 -> new Quaterniond(q.x, value, q.z, q.w);
            case 2 -> new Quaterniond(q.x, q.y, value, q.w);
            default -> new Quaterniond(q.x, q.y, q.z, value);
        };
    }

    /// Four per-component boxes over one [Quaterniond] slot — the [#vec3Boxes] treatment for the rotation
    /// half of a [TransformHolder].
    ///
    /// A [Quaterniond] is mutable, but each setter writes a whole new quaternion through the slot.
    /// An undo entry has to hold a value later edits cannot reach back into, and the holder shares
    /// its instance with the history stack.
    private static List<? extends AbstractWidget> quaternionBoxes(EditorWidgetFactory factory, ValueSlot slot) {
        EditorEditable ann = slot.annotation();
        PendingValue pending = PendingValue.of(slot);

        @SuppressWarnings("unchecked")
        ClampedNumberBox<Double>[] boxes = new ClampedNumberBox[4];

        for (int i = 0; i < 4; i++) {
            int component = i;

            boxes[component] = new ClampedNumberBox<>(
                factory.font,
                columnStart(slot, component, 4), columnSize(slot, component, 4),
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
                AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH,
                AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
                () -> componentOf((Quaterniond) pending.get(), component),
                v -> pending.set(withComponent((Quaterniond) pending.get(), component, v)),
                Component.empty(),
                false,
                (double) ann.min(), (double) ann.max(), 0.1D, -0.1D,
                Double::parseDouble,
                d -> Double.toString(d),
                Double::compareTo,
                Double::sum
            );

            boxes[component].setOnCommitted((oldVal, newVal) -> {
                Quaterniond newQuat = (Quaterniond) pending.get(); // the component setter already wrote it
                Quaterniond oldQuat = withComponent(newQuat, component, oldVal);

                // Deferred: nothing has reached the slot yet, so the commit is what lands it.
                if (ann.setOnCommit()) slot.set(newQuat);

                if (Objects.equals(oldQuat, newQuat)) return;

                HistoryManager.getInstance().pushHistory(new DataChange<>(
                    oldQuat, newQuat,
                    val -> {
                        slot.set(val);
                        for (int j = 0; j < boxes.length; j++) {
                            boxes[j].valueSetter().accept(componentOf(val, j));
                        }
                        focusUndoneWidget(boxes[component]);
                    }
                ));
            });
        }

        return List.of(boxes);
    }

    private static List<? extends AbstractWidget> enumerableCombo(EditorWidgetFactory factory, ValueSlot slot) {
        Collection<? extends EnumerableDataBlock> values =
            ((EnumerableDataBlock) slot.get()).getEnumManager().universalValues();

        // Late-bound array: the change consumer is a constructor argument (see checkBox)
        @SuppressWarnings("unchecked")
        AdvancedComboBox<Object>[] ref = new AdvancedComboBox[1];

        ref[0] = new AdvancedComboBox<>(
            factory.font,
            columnStart(slot, 0, 1), columnSize(slot, 0, 1),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            slot::get,
            v -> pushSlotChange(slot, v, ref[0]),
            Math.min(8, values.size()),
            Component.empty(),
            List.<Object>copyOf(values),
            v -> ((EnumerableDataBlock) v).identifier().toString()
        );

        return List.of(ref[0]);
    }

    /// The deliberate "nothing picked" key — never marked invalid by the popup validators.
    private static boolean isEmptyReference(ResourceKey<?> key) {
        return key.identifier().equals(DataHolder.EMPTY_REFERENCE);
    }

    private static List<? extends AbstractWidget> accessorPopup(EditorWidgetFactory factory, ValueSlot slot) {
        PopupBox<Object> box = new PopupBox<>(
            factory.font,
            columnStart(slot, 0, 1), columnSize(slot, 0, 1),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            slot::get,
            _ -> {},
            v -> v instanceof AssetAccessor<?> accessor ? AccessorSelectionModal.displayName(accessor) : Objects.toString(v),
            Component.empty(),
            b -> factory.openAccessorSearchModal(slot, b)
        ).withValidator(v -> !(v instanceof AssetAccessor<?> accessor)
            || accessor.identifier().equals(DataHolder.EMPTY_REFERENCE)
            || AccessorSelectionModal.resolves(accessor));

        return List.of(box);
    }

    private static List<? extends AbstractWidget> animatorScriptPopup(EditorWidgetFactory factory, ValueSlot slot) {
        PopupBox<Object> box = new PopupBox<>(
            factory.font,
            columnStart(slot, 0, 1), columnSize(slot, 0, 1),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            slot::get,
            _ -> {},
            v -> v instanceof AnimatorScriptRef scriptRef ? scriptRef.displayLabel() : Objects.toString(v),
            Component.empty(),
            b -> factory.openAnimatorScriptSelect(slot, b)
        ).withValidator(v -> !(v instanceof AnimatorScriptRef scriptRef) || switch (scriptRef) {
            case AnimatorScriptRef.ClassScript c ->
                AkytheraRegistries.Frozen.SCRIPT_TYPE.getKey(c.scriptType()) != null;
            case AnimatorScriptRef.DataPackScript d -> d.holder().isBound()
                || d.holder().unwrapKey().map(EditorWidgetFactory::isEmptyReference).orElse(false);
        });

        return List.of(box);
    }

    private static List<? extends AbstractWidget> animationRefPopup(EditorWidgetFactory factory, ValueSlot slot) {
        PopupBox<Object> box = new PopupBox<>(
            factory.font,
            columnStart(slot, 0, 1), columnSize(slot, 0, 1),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            slot::get,
            _ -> {},
            v -> v instanceof AnimationRef ref ? ref.displayName() : Objects.toString(v),
            Component.empty(),
            b -> factory.openAnimationRefSelect(slot, b)
        ).withValidator(v -> !(v instanceof AnimationRef ref) || switch (ref) {
            case AnimationRef.Empty ignored -> true;
            case AnimationRef.Sequence s -> s.holder().isBound();
            case AnimationRef.Montage m -> m.holder().isBound();
            case AnimationRef.Blend b -> b.holder().isBound();
        });

        return List.of(box);
    }

    /// The reference pose editor: a header row naming the value, then a labelled row per part — a
    /// search PopupBox naming the clip, and a **display-only** number box showing the timestamp on
    /// it. Both halves are written by one modal pick, never typed, so the number box is not editable
    /// and the history entry refreshes the pair together.
    private static final class ReferencePoseProducer implements WidgetProducer {
        /// One row after all, for a caller that has no rows to give (a list element, whose `✕` must
        /// stay on the value's own row): both parts share the editor area.
        @Override
        public List<? extends AbstractWidget> produce(EditorWidgetFactory factory, ValueSlot slot) {
            return parts(factory, slot, 2);
        }

        @Override
        public void appendRows(EditorWidgetFactory factory, WidgetTable table, ValueSlot slot, int indent) {
            List<? extends AbstractWidget> parts = parts(factory, slot, 1);

            table.newRow(indent).addWidget(factory.headerLabel(slot.labelComponent()));

            table.newRow(indent + INDENT)
                .addWidget(makeTranslatableLabel(factory.font, LangKeys.GUI_REFERENCE_POSE_ANIMATION));
            addProduced(table, parts.getFirst());

            table.newRow(indent + INDENT)
                .addWidget(makeTranslatableLabel(factory.font, LangKeys.GUI_REFERENCE_POSE_TIME_STAMP));
            addProduced(table, parts.getLast());
        }

        /// The clip box and the timestamp box, over `columns` editor columns each row: one apiece
        /// when [#appendRows] gives them a row of their own, two side by side when they share one.
        private static List<? extends AbstractWidget> parts(EditorWidgetFactory factory, ValueSlot slot, int columns) {
            int timeColumn = columns - 1;

            ClampedNumberBox<Float> timeBox = new ClampedNumberBox<>(
                factory.font,
                columnStart(slot, timeColumn, columns), columnSize(slot, timeColumn, columns),
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
                AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH,
                AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
                () -> ((ReferencePose) slot.get()).timeStamp(),
                _ -> {},
                Component.empty(),
                false,
                0.0F, Float.MAX_VALUE, 0.0F, 0.0F,
                Float::parseFloat,
                ParseUtils::floatingPointToString,
                Float::compareTo,
                Float::sum
            );

            timeBox.setEditable(false);

            PopupBox<Object> clipBox = new PopupBox<>(
                factory.font,
                columnStart(slot, 0, columns), columnSize(slot, 0, columns),
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
                AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH,
                AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
                slot::get,
                _ -> {},
                v -> v instanceof ReferencePose referencePose ? referencePose.displayName() : Objects.toString(v),
                Component.empty(),
                box -> openReferencePoseSelect(factory.font, slot, box, timeBox)
            ).withValidator(v -> !(v instanceof ReferencePose referencePose)
                || referencePose.clip() == null
                || AccessorSelectionModal.resolves(referencePose.clip()));

            return List.of(clipBox, timeBox);
        }
    }

    /// Opens the reference pose picker for a [ReferencePose] slot. [AnimationMetadataHolder] is the
    /// only owner such a slot has, and its accessor carries everything the modal reads; the button is
    /// inert on any other owner.
    private static void openReferencePoseSelect(
        Font font, ValueSlot slot, PopupBox<Object> clipBox, ClampedNumberBox<Float> timeBox
    ) {
        if (!(slot.owner() instanceof AnimationMetadataHolder holder)) return;

        ReferencePose current = (ReferencePose) slot.get();

        ReferencePoseSelectModal modal = new ReferencePoseSelectModal(font, holder.getAccessor(), picked -> {
            // Both halves land in one history entry, whose replay refreshes both widgets — the
            // shared pushSlotChange only knows about the one widget it is handed.
            slot.set(picked);
            HistoryManager.getInstance().pushHistory(new DataChange<>(
                current, picked,
                value -> {
                    slot.set(value);
                    clipBox.valueSetter().accept(value);
                    timeBox.valueSetter().accept(((ReferencePose) value).timeStamp());
                    focusUndoneWidget(clipBox);
                }
            ));

            clipBox.valueSetter().accept(picked);
            timeBox.valueSetter().accept(picked.timeStamp());
        });

        AkytheraEditorLayoutManager.getInstance().openModalWidgetAtCenter(modal);
    }

    /// [Items#AIR] is the "nothing picked" value of an [Item] slot, displayed as `<none>`.
    private static List<? extends AbstractWidget> itemPopup(EditorWidgetFactory factory, ValueSlot slot) {
        PopupBox<Object> box = new PopupBox<>(
            factory.font,
            columnStart(slot, 0, 1), columnSize(slot, 0, 1),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            slot::get,
            _ -> {},
            v -> v instanceof Item item ? displayItem(item) : Objects.toString(v),
            Component.empty(),
            b -> factory.openItemSelect(slot, b)
        );

        return List.of(box);
    }

    private static List<? extends AbstractWidget> holderPopup(EditorWidgetFactory factory, ValueSlot slot) {
        PopupBox<Object> box = new PopupBox<>(
            factory.font,
            columnStart(slot, 0, 1), columnSize(slot, 0, 1),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            slot::get,
            _ -> {},
            v -> v instanceof Holder<?> holder ? displayHolder(holder) : Objects.toString(v),
            Component.empty(),
            b -> factory.openHolderLookup(slot, b)
        ).withValidator(v -> !(v instanceof Holder<?> holder)
            || holder.isBound()
            || holder.unwrapKey().map(EditorWidgetFactory::isEmptyReference).orElse(false));

        return List.of(box);
    }

    private static List<? extends AbstractWidget> resourceKeyPopup(EditorWidgetFactory factory, ValueSlot slot) {
        PopupBox<Object> box = new PopupBox<>(
            factory.font,
            columnStart(slot, 0, 1), columnSize(slot, 0, 1),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            slot::get,
            _ -> {},
            v -> v instanceof ResourceKey<?> key
                ? (key.identifier().equals(DataHolder.EMPTY_REFERENCE) ? "<none>" : key.identifier().toString())
                : Objects.toString(v),
            Component.empty(),
            b -> factory.openRegistryLookup(slot, (ResourceKey<?>) slot.get(), b)
        ).withValidator(v -> !(v instanceof ResourceKey<?> key)
            || isEmptyReference(key)
            || AkytheraProject.getCurrentProject().getEntryFromProjectAndRegistry(key) != null);

        return List.of(box);
    }

    /// An "Edit Node Graph" button for a raw [ScriptGraphData] slot — the same storage convention as
    /// [AnimatorScriptHolder]'s flow graphs. Opens the slot's canvas as a closeable center tab of the hosting
    /// [NodeGraphTabHost] screen, shaped by the declaring field's [EditorNodeGraph] annotation (context pins,
    /// result type). The tab's flush writes the exported graph back through the slot (no history entry — the
    /// canvas manages its own edit history). Key: owner identity + the slot's field name (its `#n` label for
    /// a list element), so re-clicking refocuses the already-open tab.
    private static List<? extends AbstractWidget> nodeGraphButton(EditorWidgetFactory factory, ValueSlot slot) {
        Field f = slot.field();
        String labelText = slot.label();

        EditorNodeGraph shape = f == null ? null : f.getAnnotation(EditorNodeGraph.class);
        List<ContextEntryNodeWidget.ContextParam> params = new ArrayList<>();
        Class<?> resultType = shape == null ? boolean.class : shape.resultType();

        if (shape != null) {
            for (int i = 0; i < shape.contextParams().length; i++) {
                Class<?> type = i < shape.contextParamTypes().length ? shape.contextParamTypes()[i] : Object.class;
                params.add(new ContextEntryNodeWidget.ContextParam(shape.contextParams()[i], type));
            }
        }

        AnchoredButton button = AnchoredButton.buttonBuilder(factory.font, Component.literal("Edit Node Graph"), _ -> {
            if (!(AkytheraEditorLayoutManager.getInstance().getRecentEditorScreen() instanceof NodeGraphTabHost editor)) return;

            ScriptGraphData data = (ScriptGraphData) slot.get();
            String slotName = f == null ? slot.label() : f.getName();
            Component fallback = f == null ? Component.literal(labelText) : Component.translatable(labelText);
            Component title = slot.owner() instanceof NodeGraphTitleProvider titles
                ? titles.nodeGraphTabTitle(slotName, fallback)
                : fallback;

            editor.openResultGraphTab(
                System.identityHashCode(slot.owner()) + "/" + slotName,
                title,
                data == null ? ScriptGraphData.EMPTY : data,
                params,
                resultType,
                slot::set
            );
        })
            .xUnits(columnStart(slot, 0, 1), columnSize(slot, 0, 1))
            .yUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.TOP_HEIGHT)
            .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
            .build();

        return List.of(button);
    }

    private static List<? extends AbstractWidget> enumCombo(EditorWidgetFactory factory, ValueSlot slot) {
        Class<?> type = slot.type();
        Object[] constants = type.getEnumConstants();

        // An enum constant with a body reports its anonymous subclass, whose getEnumConstants() is
        // null — only reachable when a raw list forced runtime-class dispatch, but cheap to absorb.
        if (constants == null && type.getSuperclass() != null) {
            constants = type.getSuperclass().getEnumConstants();
        }

        Object[] values = constants == null ? new Object[0] : constants;

        // Late-bound array: the change consumer is a constructor argument (see checkBox)
        @SuppressWarnings("unchecked")
        AdvancedComboBox<Object>[] ref = new AdvancedComboBox[1];

        ref[0] = new AdvancedComboBox<>(
            factory.font,
            columnStart(slot, 0, 1), columnSize(slot, 0, 1),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            slot::get,
            v -> pushSlotChange(slot, v, ref[0]),
            Math.min(8, values.length),
            Component.empty(),
            List.of(values),
            v -> ParseUtils.toDisplayName(((Enum<?>) v).name())
        );

        return List.of(ref[0]);
    }

    /// Combo over the values [EditorEditable#candidates] resolves for this slot — for a field whose
    /// valid values are known only at edit time (a socket's bone name, off the parent component's
    /// armature). Empty candidates leave the box showing the annotation's reason message.
    ///
    /// The list is snapshotted here: the panel re-fills on every focus change, so the offered values
    /// follow the context that produced them. A stored value the list doesn't contain still displays.
    private static List<? extends AbstractWidget> candidateCombo(EditorWidgetFactory factory, ValueSlot slot) {
        List<Object> candidates = resolveCandidates(slot, factory.context);

        // Late-bound array: the change consumer is a constructor argument (see checkBox)
        @SuppressWarnings("unchecked")
        AdvancedComboBox<Object>[] ref = new AdvancedComboBox[1];

        ref[0] = new AdvancedComboBox<>(
            factory.font,
            columnStart(slot, 0, 1), columnSize(slot, 0, 1),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            slot::get,
            // The "(none)" row clears a String slot to blank rather than null: these are plain
            // properties whose codecs default to empty, and a null would break their encode.
            v -> pushSlotChange(slot, v == null && slot.type() == String.class ? "" : v, ref[0]),
            Math.min(8, candidates.size()),
            Component.empty(),
            candidates,
            EditorWidgetFactory::candidateLabel
        );

        ref[0].setEmptyMessage(Component.translatable(
            slot.annotation().emptyCandidates().isBlank()
                ? LangKeys.GUI_WIDGET_ADVANCED_COMBO_BOX_NO_CANDIDATES
                : slot.annotation().emptyCandidates()
        ));

        return List.of(ref[0]);
    }

    /// Invokes [EditorEditable#candidates]'s method on the slot's owner, resolved on the class
    /// declaring the field the way [#defaultElementFactory] resolves its factory. A name matching no
    /// such method throws. A context the method wants but this fill did not supply yields no
    /// candidates — an object edited outside a component tree has nothing to offer.
    private static List<Object> resolveCandidates(ValueSlot slot, @Nullable Object context) {
        String name = slot.annotation().candidates();
        Field f = slot.field();
        Class<?> declaring = f != null ? f.getDeclaringClass() : slot.owner().getClass();
        Method source = null;

        for (Class<?> c = declaring; c != null && c != Object.class && source == null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() <= 1) {
                    source = m;
                    break;
                }
            }
        }

        if (source == null) {
            throw new IllegalStateException(
                "@EditorEditable(candidates) names no no-argument or single-argument method: "
                    + declaring.getName() + "#" + name);
        }

        if (!List.class.isAssignableFrom(source.getReturnType())) {
            throw new IllegalStateException(
                "@EditorEditable(candidates) returns " + source.getReturnType().getName()
                    + ", which is not a List: " + declaring.getName() + "#" + name);
        }

        Object[] args = new Object[source.getParameterCount()];

        if (args.length == 1) {
            if (context != null && !source.getParameterTypes()[0].isInstance(context)) return List.of();
            args[0] = context;
        }

        source.setAccessible(true);

        try {
            List<?> values = (List<?>) source.invoke(slot.owner(), args);
            return values == null ? List.of() : List.<Object>copyOf(values);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    /// Dropdown label of a candidate — the enum / data block conventions the typed combos already
    /// use, and the value's own string form for everything else.
    private static String candidateLabel(Object value) {
        return switch (value) {
            case EnumerableDataBlock dataBlock -> dataBlock.identifier().toString();
            case Enum<?> constant -> ParseUtils.toDisplayName(constant.name());
            case null -> "";
            default -> value.toString();
        };
    }

    // ***************************************************************
    // Select modals of built-in producers
    // ***************************************************************

    /// Opens the pick modal for an [AssetAccessor] slot, routed by the accessor's runtime
    /// [AssetAccessor.Type] (generics are erased, so the type check must live on the value).
    /// The modal writes the slot through [#pushSlotChange] — so it lands in history and the
    /// [PopupBox] display refreshes — and persists the reported preview model, if any, on the
    /// slot's **owner** (a list element has no preview model field of its own).
    private void openAccessorSearchModal(ValueSlot slot, PopupBox<Object> box) {
        AssetAccessor<?> current = (AssetAccessor<?>) slot.get();

        switch (current.type()) {
            case ANIMATION -> {
                AnimationSelectModal modal = new AnimationSelectModal(font, getPreviewModelField(slot.owner()), (animClips, previewModel) -> {
                    var animClip = animClips.getFirst();

                    // Preview model first: the holder's clip-change listener (fired by the write
                    // below) reads it to refresh the editor previewer.
                    if (previewModel != null) setPreviewModelField(slot.owner(), previewModel);
                    pushSlotChange(slot, animClip, box);

                    // pushSlotChange only routes through valueSetter on undo/redo — the immediate
                    // path assumes the widget already shows the value, which holds for typed/toggled
                    // widgets but not for a modal-driven box. Refresh the display explicitly.
                    box.valueSetter().accept(animClip);
                });

                AkytheraEditorLayoutManager.getInstance().openModalWidgetAtCenter(modal);
            }
            case ARMATURE, SKELETAL_MESH -> {
                AccessorSelectionModal modal = new AccessorSelectionModal(font, current.type(), accessor -> {
                    pushSlotChange(slot, accessor, box);
                    box.valueSetter().accept(accessor);
                });

                AkytheraEditorLayoutManager.getInstance().openModalWidgetAtCenter(modal);
            }
            // TODO: STATIC_MESH -> ModelSelectModal, once ModelPreviewer.StaticModelComponent
            //  exists engine-side.
            case STATIC_MESH -> {}
        }
    }

    private static String displayItem(Item item) {
        return item == Items.AIR ? "<none>" : item.getDefaultInstance().getHoverName().getString();
    }

    /// Opens the item picker for an [Item] slot: [ItemSelectModal] as a centered window, which
    /// closes itself once something is picked.
    private void openItemSelect(ValueSlot slot, PopupBox<Object> box) {
        ItemSelectModal modal = new ItemSelectModal(font, item -> {
            AkytheraEditorLayoutManager.getInstance().closeModal();
            pushSlotChange(slot, item, box);
            box.valueSetter().accept(item);
        });

        AkytheraEditorLayoutManager.getInstance().openModalWidgetAtCenter(modal);
    }

    private static String displayHolder(Holder<?> holder) {
        return holder.unwrapKey()
            .map(k -> k.identifier().equals(DataHolder.EMPTY_REFERENCE) ? "<none>" : k.identifier().toString())
            .orElse("<none>");
    }

    /// Opens the data pack entry lookup for a [Holder] reference slot, dropped-down under the
    /// box. Like the [ResourceKey] branch, the target registry comes from the current value's
    /// key (the slot's generic is erased) — so Holder slots must never be null; holders default
    /// them to a dangling placeholder. The picked key is resolved back to the shared live holder
    /// (see [AkytheraProject#getEntryHolderOrDangling]) so the slot follows renames of its target.
    private <T> void openHolderLookup(ValueSlot slot, PopupBox<Object> box) {
        @SuppressWarnings("unchecked")
        Holder<T> current = (Holder<T>) slot.get();
        ResourceKey<T> currentKey = current.unwrapKey().orElseThrow();

        RegistryLookupModal<T> modal = RegistryLookupModal.dataPackEntries(
            font,
            Component.literal(currentKey.registryKey().identifier().getPath()),
            currentKey.registryKey(),
            false,
            key -> {
                Holder<T> picked = AkytheraProject.getCurrentProject().getEntryHolderOrDangling(key);
                pushSlotChange(slot, picked, box);
                box.valueSetter().accept(picked);
            }
        );

        AkytheraEditorLayoutManager.getInstance().openModalWidgetUnder(box, modal);
    }

    /// Opens the combined animator-script picker for an [AnimatorScriptRef] slot, dropped-down under
    /// the box. Lists both class-based scripts and data pack script blueprints; the picked ref is
    /// written through [#pushSlotChange] (history + display refresh). A data pack pick resolves to
    /// the shared live holder inside [AnimatorScriptRef#dataPack], so the slot follows renames.
    private void openAnimatorScriptSelect(ValueSlot slot, PopupBox<Object> box) {
        AnimatorScriptSelectModal modal = new AnimatorScriptSelectModal(font, box.getWidth(), picked -> {
            pushSlotChange(slot, picked, box);
            box.valueSetter().accept(picked);
        });

        AkytheraEditorLayoutManager.getInstance().openModalWidgetUnder(box, modal);
    }

    /// Opens the animation-sequence picker for an [AnimationRef] slot, dropped-down under the box.
    /// Mirrors the holder pickers: the picked sequence key resolves to the shared live project holder
    /// (so the node follows renames) and is written back as an [AnimationRef.Sequence] through
    /// [#pushSlotChange] (history entry + display refresh). Only sequences are pickable here.
    private void openAnimationRefSelect(ValueSlot slot, PopupBox<Object> box) {
        RegistryLookupModal<AnimationSequence> modal = RegistryLookupModal.dataPackEntries(
            font,
            Component.translatable(LangKeys.GUI_BLEND_SPACE_2D_SCREEN_WIDGET_ANIMATION),
            AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY,
            false,
            key -> {
                Holder<AnimationSequence> holder = AkytheraProject.getCurrentProject().getEntryHolderOrDangling(key);
                AnimationRef picked = new AnimationRef.Sequence(holder);
                pushSlotChange(slot, picked, box);
                box.valueSetter().accept(picked);
            }
        );

        AkytheraEditorLayoutManager.getInstance().openModalWidgetUnder(box, modal);
    }

    /// Opens the data pack entry lookup for a [ResourceKey] slot, dropped-down under the box. The
    /// target registry comes from the current **value**'s `registryKey()` (the slot's generic is
    /// erased) — so ResourceKey slots must never be null; holders default them to a dangling
    /// placeholder key. Lists project entries merged with the loaded world's registry access.
    private <T> void openRegistryLookup(ValueSlot slot, ResourceKey<T> current, PopupBox<Object> box) {
        RegistryLookupModal<T> modal = RegistryLookupModal.dataPackEntries(
            font,
            Component.literal(current.registryKey().identifier().getPath()),
            current.registryKey(),
            false,
            key -> {
                pushSlotChange(slot, key, box);
                box.valueSetter().accept(key);
            }
        );

        AkytheraEditorLayoutManager.getInstance().openModalWidgetUnder(box, modal);
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

    /// Builds the label cell — its width follows the proportional [#labelWidth] column.
    public static Static makeLabel(Font font, Component component) {
        return new Static(
            font,
            AnchoredWidget.pixel(INDENT), (rect, _) -> labelWidth(rect) - INDENT,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            component
        );
    }

    /// Builds the label cell from a lang key. Its width follows the proportional [#labelWidth]
    /// column.
    public static Static makeTranslatableLabel(Font font, String text) {
        return new Static(
            font,
            AnchoredWidget.pixel(INDENT), (rect, _) -> labelWidth(rect) - INDENT,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            Component.translatable(text)
        );
    }

    // ***************************************************************
    // Value access + history — shared with external [WidgetProducer]s
    // ***************************************************************

    public static Object tryGet(Field f, Object inst) {
        try {
            return f.get(inst);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /// For widgets that change and commit in the same motion — a checkbox, a picker, a combo box.
    /// [EditorEditable#setOnCommit] does not apply: there is no in-flight value to hold back.
    public static <T, W extends DataBoundWidget<T>> void pushSlotChange(ValueSlot slot, T newValue, W widget) {
        @SuppressWarnings("unchecked")
        T oldValue = (T) slot.get();
        if (Objects.equals(oldValue, newValue)) return;

        slot.set(newValue);

        HistoryManager.getInstance().pushHistory(new DataChange<>(
            oldValue, newValue,
            val -> {
                slot.set(val);
                widget.valueSetter().accept(val);
                focusUndoneWidget(widget);
            }
        ));
    }

    /// The end of an edit that had an in-flight value: an edit box losing focus or taking Enter, a
    /// stepper click. Under [EditorEditable#setOnCommit] this is also where the value first reaches
    /// the slot — nothing was written while it was being typed.
    public static <T, W extends DataBoundWidget<T>> void pushSlotCommit(ValueSlot slot, T oldValue, T newValue, W widget) {
        if (slot.annotation().setOnCommit()) slot.set(newValue);

        if (Objects.equals(oldValue, newValue)) return;

        HistoryManager.getInstance().pushHistory(new DataChange<>(
            oldValue, newValue,
            val -> {
                slot.set(val);
                widget.valueSetter().accept(val);
                focusUndoneWidget(widget);
            }
        ));
    }

    public static void focusUndoneWidget(DataBoundWidget<?> widget) {
        if (!(widget instanceof GuiEventListener listener)) return;
        Screen screen = AkytheraEditorLayoutManager.getInstance().getRecentEditorScreen();
        if (screen == null) return;
        screen.setFocused(listener);
    }

    public static void trySet(Field f, Object inst, Object value) {
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
        EditorEditable ann = f.getAnnotation(EditorEditable.class);
        String name = ann != null && !ann.setter().isBlank()
            ? ann.setter()
            : "set" + Character.toUpperCase(f.getName().charAt(0)) + f.getName().substring(1);

        try {
            Method m = type.getMethod(name, f.getType());
            return Modifier.isPublic(m.getModifiers()) ? m : null;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    @FunctionalInterface
    private interface RowBuilder {
        void apply(WidgetTable table, Object instance, int indentLevel, @Nullable Runnable rebuild);
    }
}
