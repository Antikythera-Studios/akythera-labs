package com.yesman.akytheralabs.editor;

import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.client.gui.widget.*;
import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akythera.client.gui.widget.common.DataBoundWidget;
import com.yesman.akythera.client.gui.widget.common.PlaceholderExpressionWidget;
import com.yesman.akythera.core.animation.playable.AnimationSequence;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.EnumerableDataBlock;
import com.yesman.akythera.core.util.EnumerableDataBlockManager;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.akythera.animation.AnimationRef;
import com.yesman.akytheralabs.akythera.asset.AnimationMetadataHolder;
import com.yesman.akytheralabs.akythera.asset.ReferencePose;
import com.yesman.akytheralabs.akythera.entitypatch.component.AnimatorScriptRef;
import com.yesman.akytheralabs.dataholder.DataHolder;
import com.yesman.akytheralabs.dataholder.ProjectObjectRegistry;
import com.yesman.akytheralabs.dataholder.EntityPreviewModel;
import com.yesman.akytheralabs.dataholder.TransformHolder;
import com.yesman.akytheralabs.editor.EditorFieldScanner.EditorField;
import com.yesman.akytheralabs.editor.historytrack.DataChange;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.WorkHistory;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.main.processors.ResourceProcessor;
import com.yesman.akytheralabs.gui.screen.AnimationPreviewHost;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
import java.lang.reflect.WildcardType;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/// Builds [WidgetTable] rows of editor widgets from a holder's `@EditorEditable` fields.
/// Instantiate once per panel; call [#fillWidgetTable] on every selection change.
///
/// Widgets are dispatched by value type through a static type→[WidgetProducer] registry.
/// Custom types register through [#registerWidgetProducer] during client init, before the
/// first editor screen builds rows for the holder class — resolved producers are captured
/// in per-factory row caches. Producers read and write a [ValueSlot], never a [Field].
public final class EditorWidgetFactory {
    /// Minimum label column width, see [#labelWidth].
    private static final int LABEL_W = 96;
    /// Fraction of the row width the label column takes, floored at [#LABEL_W].
    private static final float LABEL_RATIO = 0.35F;
    private static final int INDENT = 8;
    private static final int ROW_H = 20;
    private static final int COLUMN_GAP = 4;

    /// Right-hand pixels a list element row keeps clear for its `✕` button.
    private static final int ENTRY_BUTTON_GUTTER = 24;

    /// Label color of a row holding what it inherits.
    private static final int INHERITED_LABEL_COLOR = 0xFF7A7A7A;

    /// Produces the editor widget(s) of one [ValueSlot], label excluded. Returned widgets must
    /// implement [AnchoredWidget] with a `TOP_HEIGHT` vertical anchor, and take their horizontal
    /// anchors from [EditorWidgetFactory#columnStart] / [EditorWidgetFactory#columnSize].
    @FunctionalInterface
    public interface WidgetProducer {
        List<? extends AbstractWidget> produce(EditorWidgetFactory factory, ValueSlot slot);

        /// Lays `slot` out as rows of `table`, inset `indent` pixels. The default is one row: the
        /// label cell, then [#produce]'s widgets in the editor columns. The last row opened stays
        /// the current one, so a caller can append to it afterwards.
        default void appendRows(EditorWidgetFactory factory, WidgetTable table, ValueSlot slot, int indent) {
            table.newRow(indent).addWidget(factory.slotLabel(slot));

            boolean locked = factory.isLocked(slot);

            for (AbstractWidget widget : produce(factory, slot)) {
                if (locked) lock(widget);
                addProduced(table, widget);
            }
        }
    }

    /// One editable value: a holder field, or one element of a holder's [List] field.
    ///
    /// [#type] is the **declared** type — the runtime value cannot supply it, an enum constant with
    /// a body reporting its anonymous subclass and a `Holder<T>`'s `T` being erased. [#owner] is the
    /// holder in both cases, never the element.
    public sealed interface ValueSlot permits FieldSlot, ElementSlot, WritingSlot, RebuildingSlot {
        Class<?> type();

        EditorEditable annotation();

        String label();

        /// The label cell's text: a lang key for a field, the literal `#n` marker for a list
        /// element.
        Component labelComponent();

        Object owner();

        /// The declaring field, or null for a list element.
        @Nullable Field field();

        Object get();

        void set(Object value);

        /// Right-hand pixels the row's editor columns must leave free, see
        /// [EditorWidgetFactory#ENTRY_BUTTON_GUTTER].
        default int rightGutter() {
            return 0;
        }
    }

    /// What a widget reads and writes in place of its [ValueSlot]. Under
    /// [EditorEditable#setOnCommit] it buffers the in-flight value and the slot stays untouched
    /// until the commit lands it; otherwise it is the slot, pass-through.
    private static final class PendingValue {
        /// Null while the value is buffered — the [EditorEditable#setOnCommit] case.
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

    /// A holder field. Writes prefer the bean setter, see [EditorWidgetFactory#findSetter].
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

    /// One element of a holder's [List] field: [#type] is the list's element type, [#annotation]
    /// the list field's own — so `min` / `max` on a numeric list apply per element. Writes land
    /// straight in the backing list.
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

    /// A slot that reports every write before it lands. What the Details panel claims an inherited
    /// component by: the rows are built over a copy the patch does not hold yet, and the first value
    /// written attaches it.
    public record WritingSlot(ValueSlot delegate, Runnable onWrite) implements ValueSlot {
        @Override
        public Class<?> type() {
            return delegate.type();
        }

        @Override
        public EditorEditable annotation() {
            return delegate.annotation();
        }

        @Override
        public String label() {
            return delegate.label();
        }

        @Override
        public Component labelComponent() {
            return delegate.labelComponent();
        }

        @Override
        public Object owner() {
            return delegate.owner();
        }

        @Override
        public @Nullable Field field() {
            return delegate.field();
        }

        @Override
        public Object get() {
            return delegate.get();
        }

        @Override
        public void set(Object value) {
            onWrite.run();
            delegate.set(value);
        }

        @Override
        public int rightGutter() {
            return delegate.rightGutter();
        }
    }

    /// A slot that re-renders its block after every write, see [EditorEditable#rebuildOnWrite].
    ///
    /// The rebuild is deferred a tick: the write arrives from inside the row widget's own callback,
    /// and the rebuild replaces that widget.
    public record RebuildingSlot(ValueSlot delegate, Runnable rebuild) implements ValueSlot {
        @Override
        public Class<?> type() {
            return delegate.type();
        }

        @Override
        public EditorEditable annotation() {
            return delegate.annotation();
        }

        @Override
        public String label() {
            return delegate.label();
        }

        @Override
        public Component labelComponent() {
            return delegate.labelComponent();
        }

        @Override
        public Object owner() {
            return delegate.owner();
        }

        @Override
        public @Nullable Field field() {
            return delegate.field();
        }

        @Override
        public Object get() {
            return delegate.get();
        }

        @Override
        public void set(Object value) {
            delegate.set(value);
            Minecraft.getInstance().execute(rebuild);
        }

        @Override
        public int rightGutter() {
            return delegate.rightGutter();
        }
    }

    /// Widget producers keyed by [ParseUtils#box]-normalized value type. Interface and superclass
    /// keys are resolved by the hierarchy walk in [#findProducer].
    private static final Map<Class<?>, WidgetProducer> PRODUCERS = new ConcurrentHashMap<>();

    /// Registers an editor widget producer for a value type. Primitive and wrapper classes are
    /// interchangeable; an interface or superclass key matches every implementor with no nearer
    /// registration.
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

    /// The object surrounding the instance being filled, offered to [EditorEditable#candidates].
    /// Set for the duration of one [#fillWidgetTable], so a producer must consume it while it
    /// builds.
    @Nullable
    private Object context;

    /// The instance an [EditorEditable#inheritable] row falls back to while its own value is unset,
    /// of the same class as the block being appended. [#appendObject] re-points it per nesting level,
    /// so a nested object resolves against its own counterpart.
    @Nullable
    private Object inherited;

    /// Reported before the first value of this fill lands, so the panel can attach a component the
    /// patch does not declare yet. Captured by every row built during one [#fillWidgetTable].
    @Nullable
    private Runnable onWrite;

    /// The current fill's re-render callback. Set for the duration of one [#fillWidgetTable].
    @Nullable
    private Runnable rebuild;

    public EditorWidgetFactory(Font font) {
        this.font = font;
    }

    public Font font() {
        return font;
    }

    /// Clears the table, renders `instance`'s `@EditorEditable` rows, then runs
    /// [WidgetTable#initialize] so [DataBoundWidget]s pull their initial values. Without a
    /// `rebuild` callback, [List] rows are skipped.
    public void fillWidgetTable(WidgetTable table, Object instance) {
        fillWidgetTable(table, instance, null, null);
    }

    /// Variant for panels that can re-render themselves: `rebuild` re-fills the whole table after a
    /// list add/remove and on its undo/redo, which enables [List] row rendering.
    public void fillWidgetTable(WidgetTable table, Object instance, @Nullable Runnable rebuild) {
        fillWidgetTable(table, instance, rebuild, null);
    }

    /// Variant for panels that know what `instance` hangs off: `context` is offered to
    /// [EditorEditable#candidates].
    public void fillWidgetTable(WidgetTable table, Object instance, @Nullable Runnable rebuild, @Nullable Object context) {
        fillWidgetTable(table, instance, rebuild, context, null, null);
    }

    /// Variant for a holder laid over another: an [EditorEditable#inheritable] field left unset
    /// draws `inheritedInstance`'s value as its widget's placeholder, see
    /// [PlaceholderExpressionWidget].
    public void fillWidgetTable(
        WidgetTable table, Object instance, @Nullable Runnable rebuild,
        @Nullable Object context, @Nullable Object inheritedInstance
    ) {
        fillWidgetTable(table, instance, rebuild, context, inheritedInstance, null);
    }

    /// Variant that reports the first value written into the filled block. `onWrite` is what attaches
    /// a component the patch is only previewing, see [WritingSlot].
    public void fillWidgetTable(
        WidgetTable table, Object instance, @Nullable Runnable rebuild,
        @Nullable Object context, @Nullable Object inheritedInstance, @Nullable Runnable onWrite
    ) {
        this.context = context;
        this.rebuild = rebuild;
        this.onWrite = onWrite;

        try {
            table.clearWidgets();
            appendObject(table, instance, inheritedInstance, 0, rebuild);
            table.initialize(false);
        } finally {
            this.context = null;
            this.rebuild = null;
            this.onWrite = null;
        }
    }

    /// The value `slot` inherits, whether or not it holds one of its own. Null when nothing was
    /// inherited behind the block, `slot` is a list element, or the field takes no part in
    /// inheritance.
    public @Nullable Object inheritedValue(ValueSlot slot) {
        Field f = slot.field();
        EditorEditable ann = slot.annotation();

        if (inherited == null || f == null || !(ann.inheritable() || ann.declarerOwned())) return null;

        return tryGet(f, inherited);
    }

    /// What `slot`'s widget draws while the slot itself is unset: the value it inherits.
    public @Nullable Object placeholderFor(ValueSlot slot) {
        return slot.get() == null ? inheritedValue(slot) : null;
    }

    /// Whether `slot` holds what it inherits — what greys its label. A slot inheriting nothing is its
    /// own declaration and never greys.
    public boolean inheritsValue(ValueSlot slot) {
        EditorEditable ann = slot.annotation();

        return inherited != null
            && (ann.inheritable() || ann.declarerOwned())
            && Objects.equals(slot.get(), inheritedValue(slot));
    }

    /// Whether `slot`'s widgets take no input: the slot is the declarer's and something is inherited
    /// behind it.
    public boolean isLocked(ValueSlot slot) {
        return inherited != null && slot.annotation().declarerOwned();
    }

    /// The label cell of one slot, greyed while [#inheritsValue] holds.
    public Static slotLabel(ValueSlot slot) {
        Static label = makeLabel(font, slot.labelComponent());

        if (inheritsValue(slot)) label.setColor(INHERITED_LABEL_COLOR);

        return label;
    }

    /// Turns one produced widget read-only.
    private static void lock(AbstractWidget widget) {
        widget.active = false;

        if (widget instanceof AdvancedEditBox<?> box) box.setEditable(false);
    }

    /// Installs what `slot` inherits on `widget`, for the widget to draw while it holds nothing of
    /// its own. A no-op when the slot inherits nothing.
    @SuppressWarnings("unchecked")
    public <T> void bindPlaceholder(ValueSlot slot, PlaceholderExpressionWidget<T> widget) {
        T placeholder = (T) placeholderFor(slot);
        if (placeholder == null) return;

        widget.setPlaceholderValue(placeholder);
    }

    /// Appends `instance`'s editor rows to `table` without clearing existing rows, stacking them
    /// under those of a previous [#fillWidgetTable].
    public void appendRowsFrom(WidgetTable table, Object instance) {
        appendObject(table, instance, null, 0, null);
    }

    /// Renders one object's editor rows at `indentLevel`, each level inset by [#INDENT] pixels.
    /// [EditorEditable#category] blocks indent below `indentLevel` on their own; nested objects and
    /// [List] fields recurse, each carrying the counterpart it inherits from.
    private void appendObject(
        WidgetTable table, Object instance, @Nullable Object inheritedInstance, int indentLevel, @Nullable Runnable rebuild
    ) {
        Object outer = inherited;
        inherited = inheritedInstance;

        try {
            for (RowBuilder rb : rowCache.computeIfAbsent(instance.getClass(), this::buildRowBuilders)) {
                rb.apply(table, instance, indentLevel, rebuild);
            }
        } finally {
            inherited = outer;
        }
    }

    /// `raw`, or the wrapper that reports this fill's writes. Every slot the factory builds goes
    /// through here.
    private ValueSlot slot(ValueSlot raw) {
        ValueSlot reported = onWrite == null ? raw : new WritingSlot(raw, onWrite);

        return rebuild != null && reported.annotation().rebuildOnWrite()
            ? new RebuildingSlot(reported, rebuild)
            : reported;
    }

    /// Folds the class's fields into the [EditorEditable#category] tree, then flattens it into a
    /// row builder list, each builder carrying the extra indent depth its category block earned. A
    /// category is laid out as a unit: the block sits where its lowest-ordered member would have
    /// gone, and every member follows it there.
    private List<RowBuilder> buildRowBuilders(Class<?> type) {
        CategoryNode root = new CategoryNode("");

        // fieldsOf is pre-sorted ascending by order and every sort below is stable, so ranks
        // follow from insertion order.
        for (EditorField editorField : EditorFieldScanner.fieldsOf(type)) {
            root.insert(splitCategory(editorField.annotation().category()), 0, editorField);
        }

        List<RowBuilder> out = new ArrayList<>();
        appendCategory(root, out, 0);

        return List.copyOf(out);
    }

    /// Emits `node`'s rows at `depth`: a leaf field becomes its own row builder, a nested category
    /// a title row followed by its contents one level deeper. Siblings sort by
    /// [CategoryEntry#order] — a field's own `order`, a category's smallest.
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

    /// Segments of a category path, split on `/` since each segment is a whole lang key. Blank
    /// segments drop out, so the default empty category is no path at all.
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
    /// categories, both competing for the same row slots; `children` is the by-name index
    /// [#insert] rejoins later fields of the same category through.
    private static final class CategoryNode implements CategoryEntry {
        private final String title;
        private final Map<String, CategoryNode> children = new LinkedHashMap<>();
        private final List<CategoryEntry> entries = new ArrayList<>();
        private int minOrder = Integer.MAX_VALUE;

        private CategoryNode(String title) {
            this.title = title;
        }

        /// A category ranks by its lowest-ordered member at every depth: the minimum propagates
        /// down the whole path being inserted.
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
            // already claimed.
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

        // A List field is the list editor itself, dispatched ahead of the producer registry, and
        // renders only when there is a rebuild callback to re-render through.
        if (List.class.isAssignableFrom(f.getType())) {
            return (table, inst, indentLevel, rebuild) -> {
                if (rebuild == null) return;
                appendList(table, slot(new FieldSlot(editorField, inst)), indentLevel + depth, rebuild);
            };
        }

        // A candidate list outranks the value's own type.
        WidgetProducer producer = editorField.annotation().candidates().isBlank()
            ? findProducer(f.getType())
            : EditorWidgetFactory::candidateCombo;

        if (producer == null) {
            // Nested editor object: a header label, then the value's own @EditorEditable rows one
            // level deeper. The runtime value decides — a field declared as an abstract carrier owns
            // no rows itself, and the recursion dispatches on the runtime class anyway.
            return (table, inst, indentLevel, rebuild) -> {
                Object nested = tryGet(f, inst);

                if (nested == null || EditorFieldScanner.fieldsOf(nested.getClass()).isEmpty()) {
                    // TODO: editor for f.getType()
                    ValueSlot slot = slot(new FieldSlot(editorField, inst));
                    table.newRow((indentLevel + depth) * INDENT)
                        .addWidget(slotLabel(slot))
                        .addWidget(readOnlyDisplay(slot));
                    return;
                }

                table.newRow((indentLevel + depth) * INDENT).addWidget(headerLabel(Component.translatable(labelText)));

                Object nestedInherited = inherited == null ? null : tryGet(f, inherited);

                appendObject(
                    table,
                    nested,
                    nestedInherited != null && nestedInherited.getClass() == nested.getClass() ? nestedInherited : null,
                    indentLevel + depth + 1,
                    rebuild);
            };
        }

        return (table, inst, indentLevel, _) ->
            producer.appendRows(this, table, slot(new FieldSlot(editorField, inst)), (indentLevel + depth) * INDENT);
    }

    // ***************************************************************
    // List rows
    // ***************************************************************

    /// One [List] field: a header row carrying the field's label and a `+` button, then one block
    /// per element at `listLevel + 1` — a scalar element is a single `#n` row, an editor object a
    /// `#n` marker row followed by the object's own rows. Adds and removes mutate the live backing
    /// list, land on the undo stack and re-render through `rebuild`.
    ///
    /// A blank [EditorEditable#defaultElement] drops both buttons and leaves the elements
    /// read-only.
    private void appendList(WidgetTable table, ValueSlot listSlot, int listLevel, Runnable rebuild) {
        @SuppressWarnings("unchecked")
        List<Object> entries = (List<Object>) listSlot.get();
        if (entries == null) return;

        Class<?> declared = typeArgumentOf(listSlot.field());
        Supplier<Object> elementFactory = defaultElementFactory(listSlot, declared);
        Runnable claim = onWrite;

        table.newRow(listLevel * INDENT).addWidget(headerLabel(Component.translatable(listSlot.label())));

        if (elementFactory != null) {
            table.addWidget(sectionButton(Component.literal("+"), () -> {
                Object element = elementFactory.get();

                if (element == null) {
                    String reason = listSlot.annotation().emptyCandidates();

                    RefusalPopup.open(
                        Component.translatable(LangKeys.GUI_DETAILS_PANEL_POPUP_CANNOT_ADD_TITLE),
                        Component.translatable(reason.isBlank()
                            ? LangKeys.GUI_DETAILS_PANEL_POPUP_CANNOT_ADD_REASON
                            : reason));
                    return;
                }

                if (claim != null) claim.run();
                addEntry(entries, element, rebuild);
            }));
        }

        for (int i = 0; i < entries.size(); i++) {
            int index = i;
            Object value = entries.get(index);

            // The declared element type is authoritative; a raw or wildcard list falls back to
            // the element's runtime class.
            Class<?> slotType = declared != null ? declared : (value == null ? Object.class : value.getClass());
            ValueSlot slot = slot(new ElementSlot(entries, index, slotType, listSlot.annotation(), listSlot.owner()));

            // The list field's own candidates apply per element.
            WidgetProducer producer = listSlot.annotation().candidates().isBlank()
                ? findProducer(slotType)
                : EditorWidgetFactory::candidateCombo;

            if (producer == null) {
                // Editor object element: a `#n` marker row, then the object's own rows one level
                // deeper.
                table.newRow((listLevel + 1) * INDENT).addWidget(headerLabel(Component.literal(slot.label())));
                if (elementFactory != null) {
                    table.addWidget(sectionButton(Component.literal("✕"), () -> {
                        if (claim != null) claim.run();
                        removeEntry(entries, index, rebuild);
                    }));
                }

                if (value != null) {
                    appendObject(table, value, inheritedElementAt(listSlot, index), listLevel + 1, rebuild);
                }
                continue;
            }

            // Scalar element: `#n` in the label column, the widget in the editor columns.
            producer.appendRows(this, table, slot, (listLevel + 1) * INDENT);

            if (elementFactory != null) {
                table.addWidget(sectionButton(Component.literal("✕"), () -> {
                    if (claim != null) claim.run();
                    removeEntry(entries, index, rebuild);
                }));
            }
        }
    }

    /// The inherited list's element at `index`, paired by position — a claimed list is a copy of the
    /// one it overrides, so the two run in step until the author adds or removes a row.
    private @Nullable Object inheritedElementAt(ValueSlot listSlot, int index) {
        return inheritedValue(listSlot) instanceof List<?> base && index < base.size() ? base.get(index) : null;
    }

    /// The single type argument of a field, off its generic signature: `List<Holder<AnimationMontage>>`
    /// resolves to the raw [Holder], `Holder<? extends PlayableAnimation>` to [PlayableAnimation].
    /// Null for a raw or `<Object>` field, and for one carrying more than one argument.
    private static @Nullable Class<?> typeArgumentOf(@Nullable Field f) {
        if (f == null || !(f.getGenericType() instanceof ParameterizedType parameterized)) return null;

        Type[] args = parameterized.getActualTypeArguments();
        return args.length == 1 ? rawTypeOf(args[0]) : null;
    }

    private static @Nullable Class<?> rawTypeOf(Type type) {
        return switch (type) {
            case Class<?> c -> c == Object.class ? null : c;
            case ParameterizedType p -> rawTypeOf(p.getRawType());
            // A wildcard is its upper bound: `Holder<? extends PlayableAnimation>` is over that type.
            case WildcardType w -> w.getUpperBounds().length == 1 ? rawTypeOf(w.getUpperBounds()[0]) : null;
            // A type variable carries nothing the producer registry can key on.
            default -> null;
        };
    }

    /// The `+` button's element factory: [EditorEditable#defaultElement]'s static no-argument
    /// method on the class declaring the list, or null when the annotation leaves it blank. A name
    /// matching no such method throws. The factory itself may answer null — nothing to build an
    /// element from yet.
    private static @Nullable Supplier<@Nullable Object> defaultElementFactory(ValueSlot listSlot, @Nullable Class<?> elementType) {
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

    /// Header cell for list titles, nested-object labels and element markers. Runs to the row's
    /// right margin instead of stopping at the label column.
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

    /// Read-only value display for a type no producer covers. Fills the editor area, minus the
    /// slot's own right gutter.
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

    /// Nearest registered producer for the boxed value type, breadth-first over the class itself,
    /// its interfaces in declaration order, then its superclass.
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

    /// Restores the `AbstractWidget & AnchoredWidget` intersection [WidgetTable#addWidget] wants
    /// from a producer's plain [AbstractWidget].
    @SuppressWarnings("unchecked")
    private static <W extends AbstractWidget & AnchoredWidget> void addProduced(WidgetTable table, AbstractWidget widget) {
        table.addWidget((W) widget);
    }

    // ***************************************************************
    // Column anchor units
    // ***************************************************************

    /// The label column width for one row rectangle: [#LABEL_RATIO] of the row width, floored at
    /// [#LABEL_W]. Resolved per row, so an indented row shrinks it.
    public static int labelWidth(ScreenRectangle rect) {
        return Math.max(LABEL_W, (int) (rect.width() * LABEL_RATIO));
    }

    /// Horizontal `x1` unit for editor column `index` of `count` on one slot's row: the editor area
    /// right of the label, minus the slot's [ValueSlot#rightGutter], split into equal columns with
    /// [#COLUMN_GAP] between them. Resolved against the table rect at relocate time. Pair with
    /// [AnchoredWidget.HorizontalAnchorType#LEFT_WIDTH] and [#columnSize].
    public static AnchoredWidget.Unit columnStart(ValueSlot slot, int index, int count) {
        return (rect, xy) -> labelWidth(rect) + 2
            + index * (columnWidth(rect, count, labelWidth(rect), slot.rightGutter()) + COLUMN_GAP);
    }

    /// Horizontal `x2` (width) unit for editor column `index` of `count` on one slot's row. The
    /// last column runs to the right margin minus the slot's gutter, absorbing the
    /// integer-division remainder.
    public static AnchoredWidget.Unit columnSize(ValueSlot slot, int index, int count) {
        return (rect, xy) -> {
            int label = labelWidth(rect);
            int gutter = slot.rightGutter();

            return index == count - 1
                ? rect.width() - INDENT - gutter - (label + 2 + index * (columnWidth(rect, count, label, gutter) + COLUMN_GAP))
                : columnWidth(rect, count, label, gutter);
        };
    }

    /// Gutter-free variant of [#columnStart(ValueSlot,int,int)].
    public static AnchoredWidget.Unit columnStart(int index, int count) {
        return (rect, xy) -> columnStart(index, count, labelWidth(rect)).pixels(rect, xy);
    }

    /// Variant of [#columnStart(int,int)] for a fixed-width label cell.
    public static AnchoredWidget.Unit columnStart(int index, int count, int labelWidth) {
        return (rect, xy) -> labelWidth + 2 + index * (columnWidth(rect, count, labelWidth, 0) + COLUMN_GAP);
    }

    /// Gutter-free variant of [#columnSize(ValueSlot,int,int)].
    public static AnchoredWidget.Unit columnSize(int index, int count) {
        return (rect, xy) -> columnSize(index, count, labelWidth(rect)).pixels(rect, xy);
    }

    /// Variant of [#columnSize(int,int)] for a fixed-width label cell.
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
        factory.bindPlaceholder(slot, box);

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
        factory.bindPlaceholder(slot, box);

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
        factory.bindPlaceholder(slot, box);

        return List.of(box);
    }

    private static List<? extends AbstractWidget> checkBox(EditorWidgetFactory factory, ValueSlot slot) {
        // Late-bound cell: the change consumer is a constructor argument and cannot capture
        // the widget it configures.
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

        // Placeholder only: a checkbox has no room for the revert affordance.
        ref[0].setPlaceholderValue((Boolean) factory.placeholderFor(slot));

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
        factory.bindPlaceholder(slot, box);

        return List.of(box);
    }

    /// Axis component of a [Vec3] (0 = x, 1 = y, 2 = z).
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

    /// Three per-axis double boxes over one [Vec3] slot. An axis setter writes a whole new vector
    /// live; a commit pushes one [DataChange] holding full old/new vectors, whose replay refreshes
    /// all three boxes.
    private static List<? extends AbstractWidget> vec3Boxes(EditorWidgetFactory factory, ValueSlot slot) {
        EditorEditable ann = slot.annotation();
        PendingValue pending = PendingValue.of(slot);
        Vec3 placeholder = (Vec3) factory.placeholderFor(slot);

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

            boxes[axis].setPlaceholderValue(placeholder == null ? null : axisOf(placeholder, axis));

            boxes[axis].setOnCommitted((oldVal, newVal) -> {
                Vec3 newVec = (Vec3) pending.get(); // the axis setter already wrote it
                Vec3 oldVec = withAxis(newVec, axis, oldVal);

                // Deferred: the commit is what lands the value in the slot.
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

    /// Four per-component boxes over one [Quaterniond] slot — the [#vec3Boxes] treatment for the
    /// rotation half of a [TransformHolder].
    ///
    /// A [Quaterniond] is mutable, yet each setter writes a whole new quaternion through the slot:
    /// the holder shares its instance with the history stack, whose entries must hold values later
    /// edits cannot reach back into.
    private static List<? extends AbstractWidget> quaternionBoxes(EditorWidgetFactory factory, ValueSlot slot) {
        EditorEditable ann = slot.annotation();
        PendingValue pending = PendingValue.of(slot);
        Quaterniond placeholder = (Quaterniond) factory.placeholderFor(slot);

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

            boxes[component].setPlaceholderValue(placeholder == null ? null : componentOf(placeholder, component));

            boxes[component].setOnCommitted((oldVal, newVal) -> {
                Quaterniond newQuat = (Quaterniond) pending.get(); // the component setter already wrote it
                Quaterniond oldQuat = withComponent(newQuat, component, oldVal);

                // Deferred: the commit is what lands the value in the slot.
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

    /// Combo over the merged data block set: everything compiled into the manager plus the current
    /// project's editor-created blocks, see [AkytheraProject#getMergedDataBlockIdentifiers].
    private static List<? extends AbstractWidget> enumerableCombo(EditorWidgetFactory factory, ValueSlot slot) {
        Object placeholder = factory.placeholderFor(slot);

        // An unset block names no manager; the inherited value names the one the field is over.
        EnumerableDataBlockManager<?> manager =
            ((EnumerableDataBlock) (slot.get() != null ? slot.get() : placeholder)).getEnumManager();
        AkytheraProject project = AkytheraProject.getCurrentProject();
        List<Object> values = new ArrayList<>();

        for (Identifier id : project.getMergedDataBlockIdentifiers(manager)) {
            Holder<? extends EnumerableDataBlock> holder = project.getDataBlockHolder(manager, id);
            if (holder != null && holder.isBound()) values.add(holder.value());
        }

        // Late-bound cell: the change consumer is a constructor argument and cannot capture
        // the widget it configures.
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
            List.copyOf(values),
            v -> ((EnumerableDataBlock) v).identifier().toString()
        );

        factory.bindPlaceholder(slot, ref[0]);

        return List.of(ref[0]);
    }

    /// Whether `key` is the deliberate "nothing picked" reference.
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

        factory.bindPlaceholder(slot, box);

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

        factory.bindPlaceholder(slot, box);

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

        factory.bindPlaceholder(slot, box);

        return List.of(box);
    }

    /// The reference pose editor: a header row naming the value, then a labelled row per part — a
    /// search [PopupBox] naming the clip, and a display-only number box showing the timestamp on it.
    /// One modal pick writes both halves, and one history entry refreshes the pair.
    private static final class ReferencePoseProducer implements WidgetProducer {
        /// Both parts on one row, sharing the editor area — for a caller that has only the one row
        /// to give.
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

        /// The clip box and the timestamp box, laid over `columns` editor columns.
        private static List<? extends AbstractWidget> parts(EditorWidgetFactory factory, ValueSlot slot, int columns) {
            int timeColumn = columns - 1;
            ReferencePose placeholder = (ReferencePose) factory.placeholderFor(slot);

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
            // Placeholder only: the clip box carries the revert.
            timeBox.setPlaceholderValue(placeholder == null ? null : placeholder.timeStamp());

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

            factory.bindPlaceholder(slot, clipBox);

            return List.of(clipBox, timeBox);
        }
    }

    /// Opens the reference pose picker for a [ReferencePose] slot, off the owning
    /// [AnimationMetadataHolder]'s accessor. Inert on an owner of any other type.
    private static void openReferencePoseSelect(
        Font font, ValueSlot slot, PopupBox<Object> clipBox, ClampedNumberBox<Float> timeBox
    ) {
        if (!(slot.owner() instanceof AnimationMetadataHolder holder)) return;

        ReferencePose current = (ReferencePose) slot.get();

        ReferencePoseSelectModal modal = new ReferencePoseSelectModal(font, holder.getAccessor(), picked -> {
            // Both halves land in one history entry, whose replay refreshes both widgets.
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

        factory.bindPlaceholder(slot, box);

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

        Class<?> valueType = typeArgumentOf(slot.field());

        // A row that can hold a sequence. A montage-only row is left alone: a montage plays through a
        // MontageAnimator carrying its section info, which its own editor installs.
        if (valueType != null && valueType.isAssignableFrom(AnimationSequence.class)) {
            box.setOnValuePressed(() -> previewAnimation(slot));
        }

        factory.bindPlaceholder(slot, box);

        return List.of(box);
    }

    /// Plays what `slot` holds on the open screen's preview. Read per press rather than captured, so
    /// the row follows whatever the picker last put in it.
    ///
    /// Nothing happens where there is nothing to play: a screen with no preview, a dangling holder, or
    /// a [com.yesman.akythera.core.animation.playable.BlendSpace2D] — it poses only under a driver's
    /// control point, so it carries no clip of its own and is previewed in its own editor. The row
    /// still takes the press, the two sharing one slot.
    private static void previewAnimation(ValueSlot slot) {
        if (!(AkytheraEditorLayoutManager.getInstance().getRecentEditorScreen() instanceof AnimationPreviewHost host)) return;
        if (!(slot.get() instanceof Holder<?> holder) || !holder.isBound()) return;
        if (!(holder.value() instanceof AnimationSequence sequence)) return;

        host.previewAnimation(sequence.animClip());
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
                ? (isEmptyReference(key) ? "<none>" : ResourceProcessor.displayId(key))
                : Objects.toString(v),
            Component.empty(),
            b -> factory.openRegistryLookup(slot, (ResourceKey<?>) slot.get(), b)
        ).withValidator(v -> !(v instanceof ResourceKey<?> key)
            || isEmptyReference(key)
            || AkytheraProject.getCurrentProject().getEntryFromProjectAndRegistry(key) != null);

        factory.bindPlaceholder(slot, box);

        return List.of(box);
    }

    /// An "Edit Node Graph" button for a [ScriptGraphData] slot. Opens the slot's canvas as a
    /// closeable center tab of the hosting [NodeGraphTabHost] screen, shaped by the declaring
    /// field's [EditorNodeGraph] annotation (context pins, result type); the tab's flush writes the
    /// exported graph back through the slot, with no history entry of its own. The tab key is owner
    /// identity plus the slot's field name — its `#n` label for a list element — so re-clicking
    /// refocuses the already-open tab.
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

        // An enum constant with a body reports its anonymous subclass, whose getEnumConstants()
        // is null.
        if (constants == null && type.getSuperclass() != null) {
            constants = type.getSuperclass().getEnumConstants();
        }

        Object[] values = constants == null ? new Object[0] : constants;

        // Late-bound cell: the change consumer is a constructor argument and cannot capture
        // the widget it configures.
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

        factory.bindPlaceholder(slot, ref[0]);

        return List.of(ref[0]);
    }

    /// Combo over the values [EditorEditable#candidates] resolves for this slot. Empty candidates
    /// leave the box showing the annotation's reason message.
    ///
    /// The list is snapshotted per fill. A stored value it does not contain still displays.
    private static List<? extends AbstractWidget> candidateCombo(EditorWidgetFactory factory, ValueSlot slot) {
        List<Object> candidates = resolveListMethod(slot, factory.context, slot.annotation().candidates());

        // Late-bound cell: the change consumer is a constructor argument and cannot capture
        // the widget it configures.
        @SuppressWarnings("unchecked")
        AdvancedComboBox<Object>[] ref = new AdvancedComboBox[1];

        ref[0] = new AdvancedComboBox<>(
            factory.font,
            columnStart(slot, 0, 1), columnSize(slot, 0, 1),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            slot::get,
            // The "(none)" row clears a String slot to blank rather than null, which its codec
            // cannot encode.
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

        factory.bindPlaceholder(slot, ref[0]);

        return List.of(ref[0]);
    }

    /// Invokes the [EditorEditable] method `name` names on the slot's owner, resolved on the class
    /// declaring the field. A name matching no such method throws; a context the method wants but
    /// this fill did not supply yields no values.
    private static List<Object> resolveListMethod(ValueSlot slot, @Nullable Object context, String name) {
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
                "@EditorEditable names no no-argument or single-argument method: "
                    + declaring.getName() + "#" + name);
        }

        if (!List.class.isAssignableFrom(source.getReturnType())) {
            throw new IllegalStateException(
                "@EditorEditable method returns " + source.getReturnType().getName()
                    + ", which is not a List: " + declaring.getName() + "#" + name);
        }

        Object[] args = new Object[source.getParameterCount()];

        if (args.length == 1) {
            // isInstance(null) is false, so a fill with no context takes this exit too.
            if (!source.getParameterTypes()[0].isInstance(context)) return List.of();
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

    /// Dropdown label of a candidate: an [EnumerableDataBlock]'s identifier, an enum constant's
    /// display name, the value's own string form otherwise.
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
    /// [AssetAccessor.Type] since the slot's generic is erased. The modal writes the slot through
    /// [#pushSlotChange] and persists the reported preview model, if any, on the slot's **owner**.
    private void openAccessorSearchModal(ValueSlot slot, PopupBox<Object> box) {
        AssetAccessor<?> current = (AssetAccessor<?>) slot.get();

        switch (current.type()) {
            case ANIMATION -> {
                AnimationSelectModal modal = new AnimationSelectModal(font, getPreviewModelField(slot.owner()), (animClips, previewModel) -> {
                    var animClip = animClips.getFirst();

                    // Preview model first: the clip-change listener fired by the write below
                    // reads it.
                    if (previewModel != null) setPreviewModelField(slot.owner(), previewModel);
                    pushSlotChange(slot, animClip, box);

                    // pushSlotChange routes through valueSetter only on undo/redo, so a
                    // modal-driven box refreshes its display here.
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

    /// Opens the item picker for an [Item] slot, as a centered [ItemSelectModal] that closes itself
    /// on a pick.
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

    /// Opens the data pack entry lookup for a [Holder] slot, dropped down under the box. The rows are
    /// every entry of the registries [EditorEditable#registries] names, or of the registry the current
    /// value's key addresses when it names none — the slot's generic being erased, so a [Holder] slot
    /// is never null, holders defaulting it to a dangling placeholder. The picked key resolves back to
    /// the shared live holder, see [AkytheraProject#getEntryHolderOrDangling].
    @SuppressWarnings("unchecked")
    private <T> void openHolderLookup(ValueSlot slot, PopupBox<Object> box) {
        Holder<T> current = (Holder<T>) slot.get();
        ResourceKey<T> currentKey = current.unwrapKey().orElseThrow();
        AkytheraProject project = AkytheraProject.getCurrentProject();
        String named = slot.annotation().registries();
        List<ResourceKey<? extends Registry<T>>> registries;

        if (named.isBlank()) {
            registries = List.of(currentKey.registryKey());
        } else {
            registries = (List<ResourceKey<? extends Registry<T>>>) (List<?>)
                resolveListMethod(slot, context, named);

            // A slot that names a registry list and gets none back is miswired, not in a state.
            if (registries.isEmpty()) {
                throw new IllegalStateException(
                    "@EditorEditable(registries) lists no registry: " + slot.owner().getClass().getName()
                        + "#" + named);
            }
        }

        List<ResourceKey<T>> keys = registries.stream()
            .flatMap(registryKey -> project.getMergedEntries(registryKey).stream().map(Map.Entry::getKey))
            .toList();

        Component title = registries.size() == 1
            ? Component.literal(currentKey.registryKey().identifier().getPath())
            : slot.labelComponent();

        RegistryLookupModal<T> modal = new RegistryLookupModal<>(
            font,
            title,
            keys,
            key -> {
                Holder<T> picked = project.getEntryHolderOrDangling(key);
                pushSlotChange(slot, picked, box);
                box.valueSetter().accept(picked);
            }
        );

        AkytheraEditorLayoutManager.getInstance().openModalWidgetUnder(box, modal);
    }

    /// Opens the animator-script picker for an [AnimatorScriptRef] slot, dropped down under the box.
    /// Lists both class-based scripts and data pack script blueprints; a data pack pick resolves to
    /// the shared live holder inside [AnimatorScriptRef#dataPack].
    private void openAnimatorScriptSelect(ValueSlot slot, PopupBox<Object> box) {
        AnimatorScriptSelectModal modal = new AnimatorScriptSelectModal(font, box.getWidth(), picked -> {
            pushSlotChange(slot, picked, box);
            box.valueSetter().accept(picked);
        });

        AkytheraEditorLayoutManager.getInstance().openModalWidgetUnder(box, modal);
    }

    /// Opens the animation-sequence picker for an [AnimationRef] slot, dropped down under the box.
    /// The picked key resolves to the shared live project holder and is written back as an
    /// [AnimationRef.Sequence]. Only sequences are pickable here.
    private void openAnimationRefSelect(ValueSlot slot, PopupBox<Object> box) {
        RegistryLookupModal<AnimationSequence> modal = RegistryLookupModal.dataPackEntries(
            font,
            Component.translatable(LangKeys.GUI_BLEND_SPACE_2D_SCREEN_WIDGET_ANIMATION),
            AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY,
            false,
            Set.of(),
            key -> {
                Holder<AnimationSequence> holder = AkytheraProject.getCurrentProject().getEntryHolderOrDangling(key);
                AnimationRef picked = new AnimationRef.Sequence(holder);
                pushSlotChange(slot, picked, box);
                box.valueSetter().accept(picked);
            }
        );

        AkytheraEditorLayoutManager.getInstance().openModalWidgetUnder(box, modal);
    }

    /// Opens the data pack entry lookup for a [ResourceKey] slot, dropped down under the box. The
    /// target registry comes from the current value's `registryKey()`, the slot's generic being
    /// erased — so a [ResourceKey] slot is never null, holders defaulting it to a dangling
    /// placeholder key. Lists project entries merged with the loaded world's registry access, minus
    /// the entry being edited.
    private <T> void openRegistryLookup(ValueSlot slot, ResourceKey<T> current, PopupBox<Object> box) {
        RegistryLookupModal<T> modal = RegistryLookupModal.dataPackEntries(
            font,
            Component.literal(current.registryKey().identifier().getPath()),
            current.registryKey(),
            false,
            cyclicPicks(slot, current.registryKey()),
            key -> {
                pushSlotChange(slot, key, box);
                box.valueSetter().accept(key);
            }
        );

        AkytheraEditorLayoutManager.getInstance().openModalWidgetUnder(box, modal);
    }

    /// The entries picking would make `slot`'s owner its own ancestor: the owner itself, plus every
    /// entry whose chain of this same field already leads back to it. Ids come out as
    /// [ResourceProcessor#displayId], the form the lookup matches on.
    ///
    /// The chain is walked through the slot's own [Field] on each candidate's holder, so this stays
    /// blind to what the reference means — any holder pointing into the registry it lives in gets the
    /// guard. Empty when the owner is no entry of `registryKey`, a component holder edited inside one
    /// being the usual case.
    @SuppressWarnings("unchecked")
    private static <T> Set<String> cyclicPicks(ValueSlot slot, ResourceKey<? extends Registry<T>> registryKey) {
        ProjectObjectRegistry<T, DataHolder<T>> registry =
            AkytheraProject.getCurrentProject().getModifiableRegistry(registryKey);

        if (registry == null || !(slot.owner() instanceof DataHolder<?> owner)) return Set.of();

        ResourceKey<T> ownerKey = registry.getResourceKeyByHolder((DataHolder<T>) owner).orElse(null);

        if (ownerKey == null) return Set.of();

        Set<String> excluded = new HashSet<>();
        excluded.add(ResourceProcessor.displayId(ownerKey));

        Field field = slot.field();

        if (field == null) return excluded;

        for (ResourceKey<T> candidate : registry.typedRegistryKeySet()) {
            Set<ResourceKey<T>> walked = new HashSet<>();
            ResourceKey<T> current = candidate;

            // A chain already looping among other entries ends on `walked`, not on the owner.
            while (current != null && walked.add(current)) {
                if (current.equals(ownerKey)) {
                    excluded.add(ResourceProcessor.displayId(candidate));
                    break;
                }

                DataHolder<T> holder = registry.getDataHolder(current);

                current = holder != null && tryGet(field, holder) instanceof ResourceKey<?> parent
                    ? (ResourceKey<T>) parent
                    : null;
            }
        }

        return excluded;
    }

    /// Writes `model` to the holder's [EntityPreviewModel] field, when it declares one.
    private static void setPreviewModelField(Object inst, EntityPreviewModel model) {
        Field field = findPreviewModelField(inst);
        if (field != null) trySet(field, inst, model);
    }

    /// The holder's current [EntityPreviewModel]. Null when it declares no such field, or has not
    /// picked a model yet.
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

    /// The label cell, its width following the proportional [#labelWidth] column.
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

    /// The label cell built from a lang key, its width following the proportional [#labelWidth]
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
    // Value access + history
    // ***************************************************************

    public static Object tryGet(Field f, Object inst) {
        try {
            return f.get(inst);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /// Writes `newValue` through `slot` and pushes the [DataChange] for it, for a widget that
    /// changes and commits in the same motion. [EditorEditable#setOnCommit] does not apply: there
    /// is no in-flight value to hold back.
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
    /// the slot.
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
