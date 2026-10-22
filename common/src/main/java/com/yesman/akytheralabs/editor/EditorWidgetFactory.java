package com.yesman.akytheralabs.editor;

import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.api.data.serializable.RGBA;
import com.yesman.akythera.api.data.serializable.WidgetImage;
import com.yesman.akythera.api.world.ability.AbilityTag;
import com.yesman.akythera.api.world.ability.AbilityTagSet;
import com.yesman.akythera.client.api.gui.*;
import com.yesman.akythera.client.api.gui.BuiltInEditBoxTheme;
import com.yesman.akythera.client.api.gui.ColorPickerWidget;
import com.yesman.akythera.client.api.gui.DragValueBox;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.common.DataBoundWidget;
import com.yesman.akythera.client.api.gui.common.PlaceholderExpressionWidget;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewerComponent;
import com.yesman.akythera.core.animation.playable.AnimationSequence;
import com.yesman.akythera.core.blueprint.script.AnimatorScript;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.EnumerableDataBlock;
import com.yesman.akythera.core.util.EnumerableDataBlockManager;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akythera.core.util.helper.QuaternionUtils;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.akythera.animation.AnimationRef;
import com.yesman.akytheralabs.akythera.asset.AnimationMetadataHolder;
import com.yesman.akytheralabs.akythera.asset.EditorAssetAccessors;
import com.yesman.akytheralabs.akythera.asset.ReferencePose;
import com.yesman.akytheralabs.akythera.entitypatch.component.AnimatorScriptRef;
import com.yesman.akytheralabs.akythera.entitypatch.component.ComponentAttachableHolder;
import com.yesman.akytheralabs.akythera.particle.ParticleModuleList;
import com.yesman.akytheralabs.akythera.particle.ParticleModulesProducer;
import com.yesman.akytheralabs.akythera.particle.ParticleRef;
import com.yesman.akytheralabs.akythera.particle.ParticleRefProducer;
import com.yesman.akytheralabs.akythera.particle.ParticleSprite;
import com.yesman.akytheralabs.akythera.particle.ParticleSpriteProducer;
import com.yesman.akytheralabs.akythera.widget.WidgetGraphs;
import com.yesman.akytheralabs.akythera.widget.WidgetGraphsProducer;
import com.yesman.akytheralabs.akythera.widget.WidgetImageProducer;
import com.yesman.akytheralabs.akythera.widget.WidgetLayoutHolder;
import com.yesman.akytheralabs.akythera.widget.WidgetLayoutProducer;
import com.yesman.akytheralabs.dataholder.DataBlockRegistries;
import com.yesman.akytheralabs.dataholder.DataHolder;
import com.yesman.akytheralabs.dataholder.ProjectObjectRegistry;
import com.yesman.akytheralabs.dataholder.TransformHolder;
import com.yesman.akytheralabs.editor.EditorFieldScanner.EditorField;
import com.yesman.akytheralabs.editor.historytrack.DataChange;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.HistoryScoped;
import com.yesman.akytheralabs.editor.historytrack.WorkHistory;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.main.processors.ResourceProcessor;
import com.yesman.akytheralabs.gui.screen.AnimationPreviewHost;
import com.yesman.akytheralabs.gui.screen.NodeGraphTabHost;
import com.yesman.akytheralabs.gui.widget.*;
import com.yesman.akytheralabs.gui.widget.EditorDragValueBox;
import com.yesman.akytheralabs.gui.widget.main.AbilityTagSelectModal;
import com.yesman.akytheralabs.gui.widget.main.AccessorSelectionModal;
import com.yesman.akytheralabs.gui.widget.main.AnimationSelectModal;
import com.yesman.akytheralabs.gui.widget.main.CategorizedSelectModal;
import com.yesman.akytheralabs.gui.widget.main.ItemSelectModal;
import com.yesman.akytheralabs.gui.widget.main.ReferencePoseSelectModal;
import com.yesman.akytheralabs.gui.widget.main.RegistryLookupModal;
import com.yesman.akytheralabs.gui.widget.main.SoundSelectModal;
import com.yesman.akytheralabs.gui.widget.main.TextureSelectModal;
import com.yesman.akytheralabs.gui.widget.script.Compilation;
import com.yesman.akytheralabs.gui.widget.script.ContextEntryNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.ScriptGraphData;
import com.yesman.akytheralabs.gui.widget.script.ScriptNodeGraphWidget;
import com.yesman.akytheralabs.gui.widget.viewer.DirectionArrowComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.ClientAsset;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/// Builds [WidgetTable] rows of editor widgets from a holder's `@EditorEditable` fields.
/// Instantiate once per panel; call [#fillWidgetTable] on every selection change.
///
/// Widgets are dispatched by value type through a static type→[WidgetProducer] registry.
/// Custom types register through [#registerWidgetProducer] during client init, before the
/// first editor screen builds rows for the holder class — resolved producers are captured
/// in per-factory row caches. Producers read and write a [ValueSlot], never a [Field].
public final class EditorWidgetFactory {
    /// Narrowest title column of a filled [WidgetTable], see [WidgetTable#setMinTitleWidth].
    private static final int LABEL_W = 96;
    public static final int INDENT = 8;
    public static final int ROW_H = 20;
    public static final int COLUMN_GAP = 4;

    /// Right-hand pixels a list element row keeps clear for its `✕` button.
    public static final int ENTRY_BUTTON_GUTTER = 24;

    /// Label color of a row holding what it inherits.
    private static final int INHERITED_LABEL_COLOR = 0xFF7A7A7A;

    /// Text a floating-point box accepts while it is typed into, partial input ("-", "1.", "1e") included.
    private static final Predicate<String> DECIMAL_TEXT = s -> s.matches("-?\\d*\\.?\\d*([eE]-?\\d*)?");

    /// Text an integral box accepts while it is typed into, a lone "-" included.
    private static final Predicate<String> INTEGER_TEXT = s -> s.matches("-?\\d*");

    /// A float as a number box shows it. Infinity and NaN, which the box can neither type nor commit,
    /// show as an empty box, so any number typed replaces them.
    private static final Function<Float, String> FLOAT_TEXT = f -> Float.isFinite(f) ? Float.toString(f) : "";

    /// A double as a number box shows it, see [#FLOAT_TEXT].
    private static final Function<Double, String> DOUBLE_TEXT = d -> Double.isFinite(d) ? Double.toString(d) : "";

    /// Produces the editor widget(s) of one [ValueSlot], label excluded. Returned widgets must
    /// implement [AnchoredWidget] with a `TOP_HEIGHT` vertical anchor, and take their horizontal
    /// anchors from [EditorWidgetFactory#columnStart] / [EditorWidgetFactory#columnSize], which resolve
    /// against the area right of the table's title column.
    @FunctionalInterface
    public interface WidgetProducer {
        List<? extends AbstractWidget> produce(EditorWidgetFactory factory, ValueSlot slot);

        /// Lays `slot` out as rows of `table`, inset `indent` pixels. The default is one row: the
        /// label cell, then [#produce]'s widgets in the editor columns. The last row opened stays
        /// the current one, so a caller can append to it afterwards.
        default void appendRows(EditorWidgetFactory factory, WidgetTable table, ValueSlot slot, int indent) {
            table.newRow(indent, factory.slotLabel(slot));
            factory.bindViewerFocus(table, slot);

            boolean locked = factory.isLocked(slot);

            for (AbstractWidget widget : produce(factory, slot)) {
                if (locked) lock(widget);
                addProduced(table, widget);
            }
        }
    }

    /// One editable value: a holder field, one element of a holder's [List] field, or a value no field
    /// holds and a part of one, laid out by its [ValueShape].
    ///
    /// [#type] is the **declared** type — the runtime value cannot supply it, an enum constant with
    /// a body reporting its anonymous subclass and a `Holder<T>`'s `T` being erased. [#owner] is the
    /// holder in both cases, never the element.
    public sealed interface ValueSlot
        permits FieldSlot, ElementSlot, PropertySlot, ShapedElementSlot, ShapedComponentSlot, WritingSlot, RebuildingSlot
    {
        Class<?> type();

        /// How [#fillSlots] lays this slot out. A field slot is a leaf its type's producer edits.
        default ValueShape shape() {
            return new ValueShape.Leaf(type(), null);
        }

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

        /// Whether the slot may hold null, which a combo offers as its "(none)" row. True unless the slot
        /// says otherwise.
        default boolean nullable() {
            return true;
        }

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

        /// Whether the field's type is annotated [Nullable].
        @Override
        public boolean nullable() {
            return editorField.field().getAnnotatedType().isAnnotationPresent(Nullable.class);
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
        public boolean nullable() {
            return false;
        }

        @Override
        public int rightGutter() {
            return ENTRY_BUTTON_GUTTER;
        }
    }

    /// A value no field holds, read and written through `getter` / `setter`, such as a blueprint property.
    /// [#shape] lays it out.
    ///
    /// @param annotation  the row settings, as a field's would be
    /// @param rightGutter right-hand pixels the row's editor columns leave free
    public record PropertySlot(
        String label, ValueShape shape, Object owner, Supplier<@Nullable Object> getter, Consumer<@Nullable Object> setter,
        EditorEditable annotation, int rightGutter
    ) implements ValueSlot {
        /// A slot whose rows take the [EditorEditable] defaults and the full width.
        public PropertySlot(
            final String label, final ValueShape shape, final Object owner, final Supplier<@Nullable Object> getter, final Consumer<@Nullable Object> setter
        ) {
            this(label, shape, owner, getter, setter, DEFAULT_EDITABLE, 0);
        }

        @Override
        public Class<?> type() {
            return shape.type();
        }

        @Override
        public Component labelComponent() {
            return Component.literal(label);
        }

        @Override
        public @Nullable Field field() {
            return null;
        }

        @Override
        public @Nullable Object get() {
            return getter.get();
        }

        @Override
        public void set(@Nullable Object value) {
            setter.accept(value);
        }
    }

    /// Element `index` of the list a [ValueShape.Sequence] slot holds. A write replaces the whole list in
    /// `sequence`.
    public record ShapedElementSlot(ValueSlot sequence, int index) implements ValueSlot {
        @Override
        public ValueShape shape() {
            return ((ValueShape.Sequence) sequence.shape()).element();
        }

        @Override
        public Class<?> type() {
            return shape().type();
        }

        @Override
        public EditorEditable annotation() {
            return DEFAULT_EDITABLE;
        }

        @Override
        public String label() {
            return "#" + (index + 1);
        }

        @Override
        public Component labelComponent() {
            return Component.literal(label());
        }

        @Override
        public Object owner() {
            return sequence.owner();
        }

        @Override
        public @Nullable Field field() {
            return null;
        }

        @Override
        public @Nullable Object get() {
            return ((List<?>) sequence.get()).get(index);
        }

        @Override
        public void set(@Nullable Object value) {
            List<@Nullable Object> items = new ArrayList<>((List<?>) sequence.get());
            items.set(index, value);

            sequence.set(Collections.unmodifiableList(items));
        }

        @Override
        public int rightGutter() {
            return ENTRY_BUTTON_GUTTER;
        }
    }

    /// Component `index` of the [ValueShape.RecordValue] a [ValueShape.RecordShape] slot holds. A write
    /// replaces the whole value in `record`.
    public record ShapedComponentSlot(ValueSlot record, int index) implements ValueSlot {
        @Override
        public ValueShape shape() {
            return ((ValueShape.RecordShape) record.shape()).shapes().get(index);
        }

        @Override
        public Class<?> type() {
            return shape().type();
        }

        @Override
        public EditorEditable annotation() {
            return DEFAULT_EDITABLE;
        }

        @Override
        public String label() {
            return ParseUtils.camelToSpacedPascal(record.type().getRecordComponents()[index].getName());
        }

        @Override
        public Component labelComponent() {
            return Component.literal(label());
        }

        @Override
        public Object owner() {
            return record.owner();
        }

        @Override
        public @Nullable Field field() {
            return null;
        }

        @Override
        public @Nullable Object get() {
            return ((ValueShape.RecordValue) record.get()).components().get(index);
        }

        @Override
        public void set(@Nullable Object value) {
            record.set(((ValueShape.RecordValue) record.get()).with(index, value));
        }
    }

    /// Carries the [EditorEditable] defaults a [PropertySlot] row takes.
    @EditorEditable
    private static final Object DEFAULTS_CARRIER = new Object();

    private static final EditorEditable DEFAULT_EDITABLE;

    /// A slot that reports every write before it lands. What the Details panel claims an inherited
    /// component by: the rows are built over a copy the patch does not hold yet, and the first value
    /// written attaches it.
    public record WritingSlot(ValueSlot delegate, Runnable onWrite) implements ValueSlot {
        @Override
        public Class<?> type() {
            return delegate.type();
        }

        @Override
        public ValueShape shape() {
            return delegate.shape();
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
        public boolean nullable() {
            return delegate.nullable();
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
        public ValueShape shape() {
            return delegate.shape();
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
        public boolean nullable() {
            return delegate.nullable();
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
        try {
            DEFAULT_EDITABLE = EditorWidgetFactory.class.getDeclaredField("DEFAULTS_CARRIER").getAnnotation(EditorEditable.class);
        } catch (NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }

        registerWidgetProducer(Float.class, EditorWidgetFactory::floatBox);
        registerWidgetProducer(Double.class, EditorWidgetFactory::doubleBox);
        registerWidgetProducer(Integer.class, EditorWidgetFactory::intBox);
        registerWidgetProducer(Long.class, EditorWidgetFactory::longBox);
        registerWidgetProducer(Short.class, EditorWidgetFactory::shortBox);
        registerWidgetProducer(Byte.class, EditorWidgetFactory::byteBox);
        registerWidgetProducer(Boolean.class, EditorWidgetFactory::checkBox);
        registerWidgetProducer(String.class, EditorWidgetFactory::stringBox);
        registerWidgetProducer(LocalizedMessage.class, new LocalizedMessageProducer());
        registerWidgetProducer(Vec3.class, EditorWidgetFactory::vec3Boxes);
        registerWidgetProducer(EnumerableDataBlock.class, EditorWidgetFactory::enumerableCombo);
        registerWidgetProducer(AbilityTag.class, EditorWidgetFactory::abilityTagPopup);
        registerWidgetProducer(AbilityTagSet.class, EditorWidgetFactory::abilityTagSetPopup);
        registerWidgetProducer(AssetAccessor.class, EditorWidgetFactory::accessorPopup);
        registerWidgetProducer(AnimatorScriptRef.class, EditorWidgetFactory::animatorScriptPopup);
        registerWidgetProducer(AnimationRef.class, EditorWidgetFactory::animationRefPopup);
        registerWidgetProducer(ReferencePose.class, new ReferencePoseProducer());
        registerWidgetProducer(Item.class, EditorWidgetFactory::itemPopup);
        registerWidgetProducer(ClientAsset.Texture.class, EditorWidgetFactory::texturePopup);
        registerWidgetProducer(SoundEvent.class, EditorWidgetFactory::soundEventPopup);
        registerWidgetProducer(Holder.class, new HolderProducer());
        registerWidgetProducer(ResourceKey.class, EditorWidgetFactory::resourceKeyPopup);
        registerWidgetProducer(Enum.class, EditorWidgetFactory::enumCombo);
        registerWidgetProducer(ScriptGraphData.class, EditorWidgetFactory::nodeGraphButton);
        registerWidgetProducer(Quaterniond.class, EditorWidgetFactory::quaternionBoxes);
        registerWidgetProducer(ScriptType.class, EditorWidgetFactory::scriptTypeCombo);
        registerWidgetProducer(RGBA.class, EditorWidgetFactory::colorPicker);
        registerWidgetProducer(WidgetImage.class, new WidgetImageProducer());
        registerWidgetProducer(ParticleSprite.class, new ParticleSpriteProducer());
        registerWidgetProducer(WidgetLayoutHolder.class, new WidgetLayoutProducer());
        registerWidgetProducer(WidgetGraphs.class, new WidgetGraphsProducer());
        registerWidgetProducer(ParticleRef.class, new ParticleRefProducer());
        registerWidgetProducer(ParticleModuleList.class, new ParticleModulesProducer());
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

    /// Where an [EditorEditable#direction] row raises its arrow. Null leaves those rows without one.
    @Nullable
    private Model3DViewer viewer;

    /// The blueprint holder every fill of this factory belongs to, offered to [EditorEditable#candidates] where the
    /// method wants it and not the fill's context. Null where the fills belong to none.
    @Nullable
    private Object root;

    /// The viewer component types the rows of this factory's fills name in [EditorEditable#viewerFocus].
    private final Set<Class<? extends Model3DViewerComponent>> viewerFocusTypes = new HashSet<>();

    /// The [EditorEditable#viewerFocus] of the nested editor object whose rows are being laid out, which they take
    /// when they name none. Null outside such an object.
    private @Nullable Class<? extends Model3DViewerComponent> enclosingViewerFocus;

    public EditorWidgetFactory(Font font) {
        this.font = font;
    }

    /// Hands the fills of this factory the blueprint holder they belong to, see [EditorEditable#candidates].
    public EditorWidgetFactory root(final Object root) {
        this.root = root;
        return this;
    }

    /// Hands the fills of this factory a viewer, see [EditorEditable#direction] and [EditorEditable#viewerFocus].
    public EditorWidgetFactory viewer(final Model3DViewer viewer) {
        this.viewer = viewer;
        return this;
    }

    /// Makes the current row of `table` focus the viewer component [EditorEditable#viewerFocus] names on `slot`, when
    /// one of the row's widgets takes focus. A row that names none releases a component another row names.
    public void bindViewerFocus(final WidgetTable table, final ValueSlot slot) {
        Model3DViewer target = viewer;
        Class<? extends Model3DViewerComponent> named = slot.annotation().viewerFocus();
        Class<? extends Model3DViewerComponent> type = named == Model3DViewerComponent.class && enclosingViewerFocus != null ? enclosingViewerFocus : named;

        if (target == null) return;

        if (type != Model3DViewerComponent.class) {
            viewerFocusTypes.add(type);
            table.onRowFocused(() -> target.focus(target.getComponent(type)));
        } else {
            table.onRowFocused(() -> {
                Model3DViewerComponent focused = target.getFocused();

                if (focused != null && viewerFocusTypes.contains(focused.getClass())) target.focus(null);
            });
        }
    }

    /// The current fill's context, see [#context].
    public @Nullable Object context() {
        return context;
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
            table.setMinTitleWidth(LABEL_W);
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
    public static void lock(AbstractWidget widget) {
        widget.active = false;

        if (widget instanceof EditorEditBox<?> box) box.setEditable(false);
    }

    /// Installs what `slot` inherits on `widget`, for the widget to draw while it holds nothing of
    /// its own. A no-op when the slot inherits nothing.
    @SuppressWarnings("unchecked")
    public <T> void bindPlaceholder(ValueSlot slot, PlaceholderExpressionWidget<T> widget) {
        T placeholder = (T) placeholderFor(slot);
        if (placeholder == null) return;

        widget.setPlaceholderValue(placeholder);
    }

    /// Clears the table and renders the rows of every slot of `slots` by its [ValueSlot#shape], then runs
    /// [WidgetTable#initialize]. `rebuild` re-fills the table after an element is added or removed, and on
    /// its undo/redo.
    public void fillSlots(WidgetTable table, List<? extends ValueSlot> slots, Runnable rebuild) {
        table.clearWidgets();
        appendSlots(table, slots, rebuild);
        table.initialize(false);
    }

    /// Renders the rows of every slot of `slots` under the rows `table` already holds, see [#fillSlots].
    /// The caller runs [WidgetTable#initialize] afterwards.
    public void appendSlots(WidgetTable table, List<? extends ValueSlot> slots, Runnable rebuild) {
        table.setMinTitleWidth(LABEL_W);

        for (ValueSlot slot : slots) {
            appendShaped(table, slot, 0, rebuild, null);
        }
    }

    /// Renders the rows of `slot` at `indent` pixels under the rows `table` already holds, see [#appendSlots].
    public void appendSlot(WidgetTable table, ValueSlot slot, int indent, Runnable rebuild) {
        appendShaped(table, slot, indent, rebuild, null);
    }

    /// Renders `slot` at `indent` pixels by its shape:
    /// - a sequence: a header row with a `+` button, then each element one level deeper with a `✕`
    ///   button on its first row;
    /// - a record: a header row, then each component one level deeper;
    /// - anything else: its producer's rows, or its value read-only when no producer covers its type.
    ///
    /// @param remove what the `✕` button on the slot's first row does, or null for no button
    private void appendShaped(WidgetTable table, ValueSlot slot, int indent, Runnable rebuild, @Nullable Runnable remove) {
        switch (slot.shape()) {
            case ValueShape.Sequence sequence -> {
                table.newRow(indent).addWidget(headerLabel(slot.labelComponent()));
                if (remove != null) table.addWidget(sectionButton(LocalizedMessage.handwritten("✕"), remove));

                table.addWidget(sectionButton(LocalizedMessage.handwritten("+"), () -> {
                    List<@Nullable Object> items = new ArrayList<>((List<?>) slot.get());
                    items.add(sequence.element().blank());

                    pushValueChange(slot, Collections.unmodifiableList(items), rebuild);
                }));

                int size = ((List<?>) slot.get()).size();

                for (int i = 0; i < size; i++) {
                    int index = i;

                    appendShaped(table, new ShapedElementSlot(slot, index), indent + INDENT, rebuild, () -> {
                        List<@Nullable Object> items = new ArrayList<>((List<?>) slot.get());
                        items.remove(index);

                        pushValueChange(slot, Collections.unmodifiableList(items), rebuild);
                    });
                }
            }
            case ValueShape.RecordShape record -> {
                table.newRow(indent).addWidget(headerLabel(slot.labelComponent()));
                if (remove != null) table.addWidget(sectionButton(LocalizedMessage.handwritten("✕"), remove));

                for (int i = 0; i < record.shapes().size(); i++) {
                    appendShaped(table, new ShapedComponentSlot(slot, i), indent + INDENT, rebuild, null);
                }
            }
            default -> {
                WidgetProducer producer = findProducer(slot.type());

                if (producer == null) {
                    table.newRow(indent, slotLabel(slot)).addWidget(readOnlyDisplay(slot));
                } else {
                    producer.appendRows(this, table, slot, indent);
                }

                if (remove != null) table.addWidget(sectionButton(LocalizedMessage.handwritten("✕"), remove));
            }
        }
    }

    /// Writes `value` through `slot` as one undo step, re-rendering after it and after its undo/redo.
    private static void pushValueChange(ValueSlot slot, Object value, Runnable rebuild) {
        Object oldValue = slot.get();

        slot.set(value);

        HistoryManager.getInstance().pushHistory(historyScope(slot), new DataChange<>(oldValue, value, v -> {
            slot.set(v);
            rebuild.run();
        }));

        rebuild.run();
    }

    /// Appends `instance`'s editor rows to `table` without clearing existing rows, stacking them
    /// under those of a previous [#fillWidgetTable].
    public void appendRowsFrom(WidgetTable table, Object instance) {
        table.setMinTitleWidth(LABEL_W);
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
                case FieldEntry(EditorField editorField) -> {
                    RowBuilder rows = builderFor(editorField, depth);
                    String visibleWhen = editorField.annotation().visibleWhen();

                    if (visibleWhen.isBlank()) {
                        out.add(rows);
                        break;
                    }

                    Method visibility = findEditorMethod(editorField.field().getDeclaringClass(), visibleWhen, 0);

                    if (ParseUtils.box(visibility.getReturnType()) != Boolean.class) {
                        throw new IllegalStateException(
                            "@EditorEditable(visibleWhen) must return a boolean: "
                                + visibility.getDeclaringClass().getName() + "#" + visibleWhen);
                    }

                    out.add((table, inst, indentLevel, rebuild) -> {
                        if ((Boolean) invokeEditorMethod(visibility, inst)) rows.apply(table, inst, indentLevel, rebuild);
                    });
                }
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
                    table.newRow((indentLevel + depth) * INDENT, slotLabel(slot))
                        .addWidget(readOnlyDisplay(slot));
                    return;
                }

                table.newRow((indentLevel + depth) * INDENT).addWidget(headerLabel(Component.translatable(labelText)));

                Object nestedInherited = inherited == null ? null : tryGet(f, inherited);
                Class<? extends Model3DViewerComponent> outerViewerFocus = enclosingViewerFocus;

                if (editorField.annotation().viewerFocus() != Model3DViewerComponent.class) enclosingViewerFocus = editorField.annotation().viewerFocus();

                try {
                    appendObject(
                        table,
                        nested,
                        nestedInherited != null && nestedInherited.getClass() == nested.getClass() ? nestedInherited : null,
                        indentLevel + depth + 1,
                        rebuild);
                } finally {
                    enclosingViewerFocus = outerViewerFocus;
                }
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
        Supplier<Object> elementFactory = defaultElementFactory(listSlot, declared, entries);
        Runnable claim = onWrite;

        table.newRow(listLevel * INDENT).addWidget(headerLabel(Component.translatable(listSlot.label())));

        if (elementFactory != null) {
            table.addWidget(sectionButton(LocalizedMessage.handwritten("+"), () -> {
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
                    table.addWidget(sectionButton(LocalizedMessage.handwritten("✕"), () -> {
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
                table.addWidget(sectionButton(LocalizedMessage.handwritten("✕"), () -> {
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

    /// The `+` button's element factory: [EditorEditable#defaultElement]'s static method on the class
    /// declaring the list, handed `entries` when it takes them, or null when the annotation leaves it
    /// blank. A name matching no such method throws. The factory itself may answer null — nothing to
    /// build an element from yet.
    private static @Nullable Supplier<@Nullable Object> defaultElementFactory(
        ValueSlot listSlot, @Nullable Class<?> elementType, List<Object> entries
    ) {
        String name = listSlot.annotation().defaultElement();
        if (name.isBlank()) return null;

        Method factory = findEditorMethod(declaringClassOf(listSlot), name, 1);

        if (factory.getParameterCount() == 1 && !factory.getParameterTypes()[0].isAssignableFrom(List.class)) {
            throw new IllegalStateException(
                "@EditorEditable(defaultElement) must take nothing or the list: "
                    + factory.getDeclaringClass().getName() + "#" + name);
        }

        if (!Modifier.isStatic(factory.getModifiers())) {
            throw new IllegalStateException(
                "@EditorEditable(defaultElement) must name a *static* method: "
                    + factory.getDeclaringClass().getName() + "#" + name);
        }

        if (elementType != null && !ParseUtils.box(elementType).isAssignableFrom(ParseUtils.box(factory.getReturnType()))) {
            throw new IllegalStateException(
                "@EditorEditable(defaultElement) returns " + factory.getReturnType().getName()
                    + ", which is not a " + elementType.getName() + ": " + factory.getDeclaringClass().getName() + "#" + name);
        }

        return () -> factory.getParameterCount() == 0
            ? invokeEditorMethod(factory, null)
            : invokeEditorMethod(factory, null, entries);
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

    /// Read-only value display for a type no producer covers: the value as its codec's JSON, or as its
    /// string form without one. Fills the editor area, minus the slot's own right gutter.
    private Static readOnlyDisplay(ValueSlot slot) {
        String text;

        try {
            text = slot.shape().toJson(slot.get()).toString();
        } catch (RuntimeException e) {
            text = Objects.toString(slot.get());
        }

        return new Static(
            font,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(INDENT + slot.rightGutter()),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            Component.literal(text)
        );
    }

    public AkytheraButton sectionButton(LocalizedMessage label, Runnable onPress) {
        return AkytheraButton.buttonBuilder(font, label, onPress)
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

    /// The widgets `slot`'s type's producer makes for it, label excluded, or null when no producer
    /// covers the type. The caller places them and runs the [WidgetTable#initialize] step on them.
    public @Nullable List<? extends AbstractWidget> produce(ValueSlot slot) {
        WidgetProducer producer = findProducer(slot.type());

        return producer == null ? null : producer.produce(this, slot);
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

    /// Horizontal `x1` unit for editor column `index` of `count` on one slot's row: the row's editor
    /// area, minus the slot's [ValueSlot#rightGutter], split into equal columns with [#COLUMN_GAP]
    /// between them. Pair with [AnchoredWidget.HorizontalAnchorType#LEFT_WIDTH] and [#columnSize].
    public static AnchoredWidget.Unit columnStart(ValueSlot slot, int index, int count) {
        return columnStart(index, count, slot.rightGutter());
    }

    /// Horizontal `x2` (width) unit for editor column `index` of `count` on one slot's row. The
    /// last column runs to the right margin minus the slot's gutter, absorbing the
    /// integer-division remainder.
    public static AnchoredWidget.Unit columnSize(ValueSlot slot, int index, int count) {
        return columnSize(index, count, slot.rightGutter());
    }

    /// Gutter-free variant of [#columnStart(ValueSlot,int,int)].
    public static AnchoredWidget.Unit columnStart(int index, int count) {
        return columnStart(index, count, 0);
    }

    /// Gutter-free variant of [#columnSize(ValueSlot,int,int)].
    public static AnchoredWidget.Unit columnSize(int index, int count) {
        return columnSize(index, count, 0);
    }

    /// [#columnStart(ValueSlot,int,int)] for a row that keeps `gutter` right-hand pixels free.
    public static AnchoredWidget.Unit columnStart(int index, int count, int gutter) {
        return (rect, xy) -> index * (columnWidth(rect, count, gutter) + COLUMN_GAP);
    }

    /// [#columnSize(ValueSlot,int,int)] for a row that keeps `gutter` right-hand pixels free.
    public static AnchoredWidget.Unit columnSize(int index, int count, int gutter) {
        return (rect, xy) -> index == count - 1
            ? rect.width() - INDENT - gutter - index * (columnWidth(rect, count, gutter) + COLUMN_GAP)
            : columnWidth(rect, count, gutter);
    }

    private static int columnWidth(ScreenRectangle rect, int count, int gutter) {
        return (rect.width() - gutter - INDENT - (count - 1) * COLUMN_GAP) / count;
    }

    // ***************************************************************
    // Built-in producers
    // ***************************************************************

    /// Whether `ann` bounds its number on both sides, which makes the row a [DragValueBox].
    private static boolean isBounded(EditorEditable ann) {
        return ann.min() > -Float.MAX_VALUE && ann.max() < Float.MAX_VALUE;
    }

    /// A [DragValueBox] over `slot`, sliding between `min` and `max`.
    @SuppressWarnings("unchecked")
    private static <T extends Number> List<? extends AbstractWidget> dragBox(
        EditorWidgetFactory factory, ValueSlot slot, T min, T max,
        Function<String, T> parser, Function<T, String> serializer, DoubleFunction<T> fromDouble
    ) {
        PendingValue pending = PendingValue.of(slot);

        DragValueBox<T> box = new EditorDragValueBox<>(
            factory.font,
            columnStart(slot, 0, 1), columnSize(slot, 0, 1),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            () -> (T) pending.get(),
            pending::set,
            min, max,
            parser, serializer, fromDouble,
            Component.empty()
        );

        box.setOnCommitted((oldVal, newVal) -> pushSlotCommit(slot, oldVal, newVal, box));

        return List.of(box);
    }

    /// An edit box over one number in editor column `index` of `count` on `slot`'s row, read and written
    /// through `getter` / `setter`. `parser` clamps what it reads to the slot's `min` / `max`.
    ///
    /// Text is filtered while typed, keeping partial input such as "-" or "1.", and reverts on commit when
    /// it does not parse. The caller installs its commit through [#changesOnly].
    private static <T extends Number> EditorEditBox<T> numberBox(
        EditorWidgetFactory factory, ValueSlot slot, int index, int count,
        Callable<T> getter, Consumer<T> setter,
        Predicate<String> filter, Function<String, T> parser, Function<T, String> serializer
    ) {
        EditorEditBox<T> box = new EditorEditBox<>(
            factory.font,
            columnStart(slot, index, count), columnSize(slot, index, count),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            getter, setter,
            filter, parser, serializer,
            Component.empty()
        );

        box.setTheme(BuiltInEditBoxTheme.DEFAULT_EDIT_BOX);

        return box;
    }

    /// `onCommitted`, skipping a commit that leaves the value as it was. [EditorEditBox] reports those too,
    /// which a number row must not turn into a write or a history entry.
    private static <T> BiConsumer<T, T> changesOnly(BiConsumer<T, T> onCommitted) {
        return (oldVal, newVal) -> {
            if (!Objects.equals(oldVal, newVal)) onCommitted.accept(oldVal, newVal);
        };
    }

    private static List<? extends AbstractWidget> floatBox(EditorWidgetFactory factory, ValueSlot slot) {
        EditorEditable ann = slot.annotation();

        if (isBounded(ann)) {
            return dragBox(factory, slot, ann.min(), ann.max(), Float::parseFloat, ParseUtils::floatingPointToString, d -> (float) d);
        }

        PendingValue pending = PendingValue.of(slot);

        EditorEditBox<Float> box = numberBox(
            factory, slot, 0, 1,
            () -> (Float) pending.get(), pending::set,
            DECIMAL_TEXT, s -> Math.clamp(Float.parseFloat(s), ann.min(), ann.max()), FLOAT_TEXT
        );

        box.setOnCommitted(changesOnly((oldVal, newVal) -> pushSlotCommit(slot, oldVal, newVal, box)));
        factory.bindPlaceholder(slot, box);

        return List.of(box);
    }

    private static List<? extends AbstractWidget> doubleBox(EditorWidgetFactory factory, ValueSlot slot) {
        EditorEditable ann = slot.annotation();

        if (isBounded(ann)) {
            return dragBox(factory, slot, (double) ann.min(), (double) ann.max(), Double::parseDouble, d -> Double.toString(d), d -> d);
        }

        PendingValue pending = PendingValue.of(slot);

        EditorEditBox<Double> box = numberBox(
            factory, slot, 0, 1,
            () -> (Double) pending.get(), pending::set,
            DECIMAL_TEXT, s -> Math.clamp(Double.parseDouble(s), ann.min(), ann.max()), DOUBLE_TEXT
        );

        box.setOnCommitted(changesOnly((oldVal, newVal) -> pushSlotCommit(slot, oldVal, newVal, box)));
        factory.bindPlaceholder(slot, box);

        return List.of(box);
    }

    private static List<? extends AbstractWidget> intBox(EditorWidgetFactory factory, ValueSlot slot) {
        EditorEditable ann = slot.annotation();

        if (isBounded(ann)) {
            return dragBox(factory, slot, (int) ann.min(), (int) ann.max(), Integer::parseInt, String::valueOf, d -> (int) Math.round(d));
        }

        PendingValue pending = PendingValue.of(slot);

        EditorEditBox<Integer> box = numberBox(
            factory, slot, 0, 1,
            () -> (Integer) pending.get(), pending::set,
            INTEGER_TEXT, s -> Math.clamp(Long.parseLong(s), (int) ann.min(), (int) ann.max()), String::valueOf
        );

        box.setOnCommitted(changesOnly((oldVal, newVal) -> pushSlotCommit(slot, oldVal, newVal, box)));
        factory.bindPlaceholder(slot, box);

        return List.of(box);
    }

    private static List<? extends AbstractWidget> longBox(EditorWidgetFactory factory, ValueSlot slot) {
        EditorEditable ann = slot.annotation();
        PendingValue pending = PendingValue.of(slot);

        EditorEditBox<Long> box = numberBox(
            factory, slot, 0, 1,
            () -> (Long) pending.get(), pending::set,
            INTEGER_TEXT, s -> Math.clamp(Long.parseLong(s), (long) ann.min(), (long) ann.max()), String::valueOf
        );

        box.setOnCommitted(changesOnly((oldVal, newVal) -> pushSlotCommit(slot, oldVal, newVal, box)));
        factory.bindPlaceholder(slot, box);

        return List.of(box);
    }

    private static List<? extends AbstractWidget> shortBox(EditorWidgetFactory factory, ValueSlot slot) {
        EditorEditable ann = slot.annotation();
        PendingValue pending = PendingValue.of(slot);

        EditorEditBox<Short> box = numberBox(
            factory, slot, 0, 1,
            () -> (Short) pending.get(), pending::set,
            INTEGER_TEXT,
            s -> (short) Math.clamp(Long.parseLong(s), Math.max((int) ann.min(), Short.MIN_VALUE), Math.min((int) ann.max(), Short.MAX_VALUE)),
            String::valueOf
        );

        box.setOnCommitted(changesOnly((oldVal, newVal) -> pushSlotCommit(slot, oldVal, newVal, box)));
        factory.bindPlaceholder(slot, box);

        return List.of(box);
    }

    private static List<? extends AbstractWidget> byteBox(EditorWidgetFactory factory, ValueSlot slot) {
        EditorEditable ann = slot.annotation();
        PendingValue pending = PendingValue.of(slot);

        EditorEditBox<Byte> box = numberBox(
            factory, slot, 0, 1,
            () -> (Byte) pending.get(), pending::set,
            INTEGER_TEXT,
            s -> (byte) Math.clamp(Long.parseLong(s), Math.max((int) ann.min(), Byte.MIN_VALUE), Math.min((int) ann.max(), Byte.MAX_VALUE)),
            String::valueOf
        );

        box.setOnCommitted(changesOnly((oldVal, newVal) -> pushSlotCommit(slot, oldVal, newVal, box)));
        factory.bindPlaceholder(slot, box);

        return List.of(box);
    }

    private static List<? extends AbstractWidget> checkBox(EditorWidgetFactory factory, ValueSlot slot) {
        // Late-bound cell: the change consumer is a constructor argument and cannot capture
        // the widget it configures.
        CheckBox[] ref = new CheckBox[1];

        ref[0] = new CheckBox(
            factory.font,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(12),
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

        EditorEditBox<String> box = new EditorEditBox<>(
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

        box.setTheme(BuiltInEditBoxTheme.DEFAULT_EDIT_BOX);
        box.setOnCommitted((oldVal, newVal) -> pushSlotCommit(slot, oldVal, newVal, box));
        factory.bindPlaceholder(slot, box);

        return List.of(box);
    }

    /// The localized message editor: the label row ending in the italic `L` button, then a full-width text area
    /// row [#DEFAULT_LINES] lines tall, resizable by its bottom edge. The text area scrolls once the text runs
    /// past it.
    private static final class LocalizedMessageProducer implements WidgetProducer {
        /// Identifies one value across table rebuilds: the field labelled `label` on holders of type `owner`.
        private record RowKey(Class<?> owner, String label) {}
        /// Lines the text area shows until it is resized.
        private static final int DEFAULT_LINES = 5;

        /// Height of one text line, its area's inner padding, and the table's row margins, in pixels.
        private static final int LINE_H = 9;
        private static final int AREA_PADDING = 8;
        private static final int ROW_MARGINS = 4;

        /// The text area row heights the user dragged to this session, so a rebuilt table opens them as they were.
        private final Map<RowKey, Integer> rowHeights = new HashMap<>();

        /// The text area and the `L` button on one row, sharing the editor area — for a caller that has only
        /// the one row to give.
        @Override
        public List<? extends AbstractWidget> produce(EditorWidgetFactory factory, ValueSlot slot) {
            AnchoredWidget.Unit start = columnStart(slot, 0, 1);
            AnchoredWidget.Unit size = columnSize(slot, 0, 1);
            LocalizedMessageWidget widget = widget(factory, slot, start, (rect, xy) -> size.pixels(rect, xy) - ROW_H - COLUMN_GAP);
            AkytheraButton button = widget.getSelectButton();

            button.setX1((rect, xy) -> start.pixels(rect, xy) + size.pixels(rect, xy) - ROW_H);
            button.setX2(AnchoredWidget.pixel(ROW_H));
            button.setY2(AnchoredWidget.pixel(ROW_H));

            return List.of(widget, button);
        }

        @Override
        public void appendRows(EditorWidgetFactory factory, WidgetTable table, ValueSlot slot, int indent) {
            AnchoredWidget.Unit start = columnStart(slot, 0, 1);
            AnchoredWidget.Unit size = columnSize(slot, 0, 1);
            LocalizedMessageWidget widget = widget(factory, slot, AnchoredWidget.pixel(INDENT), (rect, xy) -> size.pixels(rect, xy) - INDENT);
            AkytheraButton button = widget.getSelectButton();
            boolean locked = factory.isLocked(slot);

            // The button closes the label row at the editor area's right edge.
            button.setX1((rect, xy) -> start.pixels(rect, xy) + size.pixels(rect, xy) - ROW_H);
            button.setX2(AnchoredWidget.pixel(ROW_H));
            button.setY2(AnchoredWidget.pixel(ROW_H));

            table.newRow(indent, factory.slotLabel(slot));
            factory.bindViewerFocus(table, slot);

            if (locked) lock(button);
            addProduced(table, button);

            // The text area spans the row under it, inset like the labels and ending where the editor area does.
            RowKey key = new RowKey(slot.owner().getClass(), slot.label());
            int height = rowHeights.getOrDefault(key, DEFAULT_LINES * LINE_H + AREA_PADDING + ROW_MARGINS);

            table.newRow(indent).resizable(height, LINE_H + AREA_PADDING + ROW_MARGINS, resized -> rowHeights.put(key, resized));

            if (locked) lock(widget);
            addProduced(table, widget);
        }

        /// The message widget over `slot`, spanning `x2` from `x1`, one row tall until a table row stretches it.
        private static LocalizedMessageWidget widget(EditorWidgetFactory factory, ValueSlot slot, AnchoredWidget.Unit x1, AnchoredWidget.Unit x2) {
            PendingValue pending = PendingValue.of(slot);

            LocalizedMessageWidget widget = new LocalizedMessageWidget(
                factory.font,
                x1, x2,
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
                AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH,
                AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
                () -> (LocalizedMessage) pending.get(),
                pending::set
            );

            widget.setOnCommitted((oldVal, newVal) -> pushSlotCommit(slot, oldVal, newVal, widget));

            return widget;
        }
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
    ///
    /// An [EditorEditable#direction] row commits the vector at unit length, and raises a
    /// [DirectionArrowComponent] in the fill's viewer when one of its boxes takes focus.
    private static List<? extends AbstractWidget> vec3Boxes(EditorWidgetFactory factory, ValueSlot slot) {
        EditorEditable ann = slot.annotation();
        PendingValue pending = PendingValue.of(slot);
        Vec3 placeholder = (Vec3) factory.placeholderFor(slot);
        Model3DViewer viewer = ann.direction() ? factory.viewer : null;

        @SuppressWarnings("unchecked")
        EditorEditBox<Double>[] boxes = new EditorEditBox[3];

        // Writes a whole vector into the slot and all three boxes
        Consumer<Vec3> write = val -> {
            slot.set(val);

            for (int j = 0; j < boxes.length; j++) {
                boxes[j].valueSetter().accept(axisOf(val, j));
            }
        };

        for (int i = 0; i < 3; i++) {
            int axis = i;

            boxes[axis] = numberBox(
                factory, slot, axis, 3,
                () -> axisOf((Vec3) pending.get(), axis),
                v -> pending.set(withAxis((Vec3) pending.get(), axis, v)),
                DECIMAL_TEXT, s -> Math.clamp(Double.parseDouble(s), ann.min(), ann.max()), DOUBLE_TEXT
            );

            boxes[axis].setPlaceholderValue(placeholder == null ? null : axisOf(placeholder, axis));

            boxes[axis].setOnCommitted(changesOnly((oldVal, newVal) -> {
                Vec3 typed = (Vec3) pending.get(); // the axis setter already wrote it
                Vec3 oldVec = withAxis(typed, axis, oldVal);
                Vec3 newVec = !ann.direction() ? typed : typed.lengthSqr() < 1.0E-12D ? oldVec : typed.normalize();

                if (ann.direction()) {
                    write.accept(newVec);
                } else if (ann.setOnCommit()) {
                    // Deferred: the commit is what lands the value in the slot.
                    slot.set(newVec);
                }

                if (Objects.equals(oldVec, newVec)) return;

                HistoryManager.getInstance().pushHistory(historyScope(slot), new DataChange<>(
                    oldVec, newVec,
                    val -> {
                        write.accept(val);
                        focusUndoneWidget(boxes[axis]);
                    }
                ));
            }));

            if (viewer != null) {
                boxes[axis].setOnFocused(() -> {
                    DirectionArrowComponent arrow = new DirectionArrowComponent(() -> (Vec3) slot.get(), write);

                    viewer.addComponent(arrow);
                    viewer.focus(arrow);
                });
            }
        }

        return List.of(boxes);
    }

    /// Three Euler angle boxes (XYZ, in degrees) over one [Quaterniond] slot, see
    /// [QuaternionUtils#toEulerDegreesXYZ]. Each edit writes a whole new, normalized quaternion.
    ///
    /// The row keeps the angles it shows, so typing one angle never re-reads the other two: the Euler
    /// angles of one rotation are not unique, and re-reading could make the other boxes jump. A
    /// quaternion written from outside the row (a gizmo drag, an undo) shows up as one the row has not
    /// seen, and the angles are re-read from it.
    private static List<? extends AbstractWidget> quaternionBoxes(EditorWidgetFactory factory, ValueSlot slot) {
        EditorEditable ann = slot.annotation();
        PendingValue pending = PendingValue.of(slot);
        Quaterniond placeholder = (Quaterniond) factory.placeholderFor(slot);
        Vector3d placeholderAngles = placeholder == null ? null : QuaternionUtils.toEulerDegreesXYZ(placeholder);
        Quaterniond[] seen = { (Quaterniond) pending.get() };
        Vector3d angles = QuaternionUtils.toEulerDegreesXYZ(seen[0]);

        // Re-reads the angles when the slot holds a quaternion the row did not write
        Runnable sync = () -> {
            Quaterniond current = (Quaterniond) pending.get();

            if (Objects.equals(current, seen[0])) return;

            seen[0] = current;
            angles.set(QuaternionUtils.toEulerDegreesXYZ(current));
        };

        @SuppressWarnings("unchecked")
        EditorEditBox<Double>[] boxes = new EditorEditBox[3];

        for (int i = 0; i < 3; i++) {
            int axis = i;

            boxes[axis] = numberBox(
                factory, slot, axis, 3,
                () -> {
                    sync.run();
                    return angles.get(axis);
                },
                v -> {
                    sync.run();
                    angles.setComponent(axis, v);
                    seen[0] = QuaternionUtils.fromEulerDegreesXYZ(angles);
                    pending.set(seen[0]);
                },
                DECIMAL_TEXT, Double::parseDouble, DOUBLE_TEXT
            );

            boxes[axis].setPlaceholderValue(placeholderAngles == null ? null : placeholderAngles.get(axis));

            boxes[axis].setOnCommitted(changesOnly((oldVal, newVal) -> {
                Quaterniond newQuat = (Quaterniond) pending.get(); // the angle setter already wrote it
                Quaterniond oldQuat = QuaternionUtils.fromEulerDegreesXYZ(new Vector3d(angles).setComponent(axis, oldVal));

                // Deferred: the commit is what lands the value in the slot.
                if (ann.setOnCommit()) slot.set(newQuat);

                if (Objects.equals(oldQuat, newQuat)) return;

                HistoryManager.getInstance().pushHistory(historyScope(slot), new DataChange<>(
                    oldQuat, newQuat,
                    val -> {
                        slot.set(val);
                        seen[0] = val;
                        angles.set(QuaternionUtils.toEulerDegreesXYZ(val));

                        for (int j = 0; j < boxes.length; j++) {
                            boxes[j].valueSetter().accept(angles.get(j));
                        }

                        focusUndoneWidget(boxes[axis]);
                    }
                ));
            }));
        }

        return List.of(boxes);
    }

    /// Combo over the merged data block set: everything compiled into the manager plus the current
    /// project's editor-created blocks, see [AkytheraProject#getMergedDataBlockIdentifiers].
    private static List<? extends AbstractWidget> enumerableCombo(EditorWidgetFactory factory, ValueSlot slot) {
        Object placeholder = factory.placeholderFor(slot);

        // An unset block names no manager; the inherited value names the one the field is over, and
        // failing that the declared type does.
        Object named = slot.get() != null ? slot.get() : placeholder;
        EnumerableDataBlockManager<?> manager = named != null
            ? ((EnumerableDataBlock) named).getEnumManager()
            : DataBlockRegistries.managerOf(slot.type());

        if (manager == null) return List.of(factory.readOnlyDisplay(slot));
        AkytheraProject project = AkytheraProject.getCurrentProject();
        List<Object> values = new ArrayList<>();

        for (Identifier id : project.getMergedDataBlockIdentifiers(manager)) {
            Holder<? extends EnumerableDataBlock> holder = project.getDataBlockHolder(manager, id);
            if (holder != null && holder.isBound()) values.add(holder.value());
        }

        // Late-bound cell: the change consumer is a constructor argument and cannot capture
        // the widget it configures.
        @SuppressWarnings("unchecked")
        EditorComboBox<Object>[] ref = new EditorComboBox[1];

        ref[0] = new EditorComboBox<>(
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

        ref[0].setClearable(slot.nullable());
        ref[0].setSearchable(true);
        factory.bindPlaceholder(slot, ref[0]);

        return List.of(ref[0]);
    }

    /// A search [PopupBox] over an [AbilityTag] slot, picked from the tag tree of [AbilityTagSelectModal].
    /// Cleared, the slot holds null.
    private static List<? extends AbstractWidget> abilityTagPopup(EditorWidgetFactory factory, ValueSlot slot) {
        PopupBox<Object> box = new PopupBox<>(
            factory.font,
            columnStart(slot, 0, 1), columnSize(slot, 0, 1),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            slot::get,
            _ -> {},
            v -> v instanceof AbilityTag tag ? tagName(tag) : Objects.toString(v),
            Component.empty(),
            b -> factory.openAbilityTagSelect(slot, b, false)
        ).withValidator(v -> !(v instanceof AbilityTag tag) || resolvesTag(tag))
        .setOnCleared(b -> clearSlot(slot, null, b));

        factory.bindPlaceholder(slot, box);

        return List.of(box);
    }

    /// A [PopupBox] over an [AbilityTagSet] slot, ticked in the tag tree of [AbilityTagSelectModal]. The
    /// box takes no typing, which would empty the set: the modal's own search filters it.
    private static List<? extends AbstractWidget> abilityTagSetPopup(EditorWidgetFactory factory, ValueSlot slot) {
        PopupBox<Object> box = new PopupBox<>(
            factory.font,
            columnStart(slot, 0, 1), columnSize(slot, 0, 1),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            slot::get,
            _ -> {},
            v -> !(v instanceof AbilityTagSet set) ? Objects.toString(v)
                : set.isEmpty() ? "<none>"
                : set.tags().stream().map(EditorWidgetFactory::tagName).sorted().collect(Collectors.joining(", ")),
            Component.empty(),
            b -> factory.openAbilityTagSelect(slot, b, true)
        ).withValidator(v -> !(v instanceof AbilityTagSet set) || set.tags().stream().allMatch(EditorWidgetFactory::resolvesTag));

        factory.bindPlaceholder(slot, box);

        return List.of(box);
    }

    /// A tag as its id spells its path, which an unresolved stand-in answers too.
    private static String tagName(AbilityTag tag) {
        Identifier id = tag.identifier();
        return id == null ? "" : id.getPath();
    }

    /// Whether `tag` names a tag the current project or the loaded game registers.
    private static boolean resolvesTag(AbilityTag tag) {
        Identifier id = tag.identifier();
        Holder<AbilityTag> holder = id == null ? null : AkytheraProject.getCurrentProject().getDataBlockHolder(AbilityTag.ENUM_MANAGER, id);

        return holder != null && holder.isBound();
    }

    /// Whether `key` is the deliberate "nothing picked" reference.
    private static boolean isEmptyReference(ResourceKey<?> key) {
        return key.identifier().equals(DataHolder.EMPTY_REFERENCE);
    }

    /// Writes `empty` to `slot` as one history entry and shows it on `box`, for [PopupBox#setOnCleared].
    private static void clearSlot(ValueSlot slot, Object empty, PopupBox<Object> box) {
        pushSlotChange(slot, empty, box);
        box.setValue(empty);
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
            || AccessorSelectionModal.resolves(accessor))
        .setOnCleared(b -> clearSlot(slot, EditorAssetAccessors.empty(((AssetAccessor<?>) slot.get()).type()), b));

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
        }).setOnCleared(b -> clearSlot(
            slot,
            AnimatorScriptRef.dataPack(ResourceKey.create(AkytheraRegistries.DataPack.ANIMATOR_SCRIPT_KEY, DataHolder.EMPTY_REFERENCE)),
            b
        ));

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
        }).setOnCleared(b -> clearSlot(slot, AnimationRef.EMPTY, b));

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

            table.newRow(indent + INDENT, makeTranslatableLabel(factory.font, LangKeys.GUI_REFERENCE_POSE_ANIMATION));
            factory.bindViewerFocus(table, slot);
            addProduced(table, parts.getFirst());

            table.newRow(indent + INDENT, makeTranslatableLabel(factory.font, LangKeys.GUI_REFERENCE_POSE_TIME_STAMP));
            factory.bindViewerFocus(table, slot);
            addProduced(table, parts.getLast());
        }

        /// The clip box and the timestamp box, laid over `columns` editor columns.
        private static List<? extends AbstractWidget> parts(EditorWidgetFactory factory, ValueSlot slot, int columns) {
            int timeColumn = columns - 1;
            ReferencePose placeholder = (ReferencePose) factory.placeholderFor(slot);

            EditorEditBox<Float> timeBox = numberBox(
                factory, slot, timeColumn, columns,
                () -> ((ReferencePose) slot.get()).timeStamp(), _ -> {},
                DECIMAL_TEXT, s -> Math.clamp(Float.parseFloat(s), 0.0F, Float.MAX_VALUE), FLOAT_TEXT
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
                || AccessorSelectionModal.resolves(referencePose.clip()))
            .setOnCleared(box -> writeReferencePose(slot, ReferencePose.EMPTY, box, timeBox));

            factory.bindPlaceholder(slot, clipBox);

            return List.of(clipBox, timeBox);
        }
    }

    /// Opens the reference pose picker for a [ReferencePose] slot, off the owning
    /// [AnimationMetadataHolder]'s accessor. Inert on an owner of any other type.
    private static void openReferencePoseSelect(
        Font font, ValueSlot slot, PopupBox<Object> clipBox, EditorEditBox<Float> timeBox
    ) {
        if (!(slot.owner() instanceof AnimationMetadataHolder holder)) return;

        ReferencePoseSelectModal modal = new ReferencePoseSelectModal(
            font, holder.getAccessor(), picked -> writeReferencePose(slot, picked, clipBox, timeBox));

        ModalManager.getInstance().openModalWidgetAtCenter(modal);
    }

    /// Writes `pose` to a [ReferencePose] slot. Both halves land in one history entry, whose replay
    /// refreshes both widgets.
    private static void writeReferencePose(
        ValueSlot slot, ReferencePose pose, PopupBox<Object> clipBox, EditorEditBox<Float> timeBox
    ) {
        ReferencePose old = (ReferencePose) slot.get();
        if (pose.equals(old)) return;

        slot.set(pose);
        HistoryManager.getInstance().pushHistory(historyScope(slot), new DataChange<>(
            old, pose,
            value -> {
                slot.set(value);
                clipBox.valueSetter().accept(value);
                timeBox.valueSetter().accept(((ReferencePose) value).timeStamp());
                focusUndoneWidget(clipBox);
            }
        ));

        clipBox.valueSetter().accept(pose);
        timeBox.valueSetter().accept(pose.timeStamp());
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
        ).setOnCleared(b -> clearSlot(slot, Items.AIR, b));

        factory.bindPlaceholder(slot, box);

        return List.of(box);
    }

    /// A search [PopupBox] over a [ClientAsset.Texture] slot, named by its full texture path. Cleared,
    /// the slot holds null.
    private static List<? extends AbstractWidget> texturePopup(EditorWidgetFactory factory, ValueSlot slot) {
        PopupBox<Object> box = new PopupBox<>(
            factory.font,
            columnStart(slot, 0, 1), columnSize(slot, 0, 1),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            slot::get,
            _ -> {},
            v -> v instanceof ClientAsset.Texture texture ? texture.texturePath().toString() : Objects.toString(v),
            Component.empty(),
            b -> factory.openTextureSelect(slot, b)
        ).setOnCleared(b -> clearSlot(slot, null, b));

        factory.bindPlaceholder(slot, box);

        return List.of(box);
    }

    /// A [ColorPickerWidget] swatch over an [RGBA] slot. The selector writes through while it is open, and closing it
    /// is one undo step.
    private static List<? extends AbstractWidget> colorPicker(EditorWidgetFactory factory, ValueSlot slot) {
        ColorPickerWidget swatch = new ColorPickerWidget(
            factory.font,
            columnStart(slot, 0, 1), columnSize(slot, 0, 1),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            () -> (RGBA) slot.get(),
            slot::set
        );

        swatch.setValue((RGBA) slot.get());
        swatch.setOnCommitted((from, to) -> HistoryManager.getInstance().pushHistory(historyScope(slot), new DataChange<>(from, to, color -> {
            slot.set(color);
            swatch.setValue(color);
        })));

        return List.of(swatch);
    }

    /// A search [PopupBox] over a [SoundEvent] slot, named by its id. Flagged when neither the open
    /// project nor the loaded resource packs define the event.
    private static List<? extends AbstractWidget> soundEventPopup(EditorWidgetFactory factory, ValueSlot slot) {
        PopupBox<Object> box = new PopupBox<>(
            factory.font,
            columnStart(slot, 0, 1), columnSize(slot, 0, 1),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            slot::get,
            _ -> {},
            v -> v instanceof SoundEvent soundEvent ? soundEvent.location().toString() : Objects.toString(v),
            Component.empty(),
            b -> factory.openSoundSelect(slot, b)
        ).withValidator(v -> !(v instanceof SoundEvent soundEvent)
            || Minecraft.getInstance().getSoundManager().getSoundEvent(soundEvent.location()) != null);

        factory.bindPlaceholder(slot, box);

        return List.of(box);
    }

    /// A search [PopupBox] over a [Holder] slot. A row that can hold a sequence plays it on the open
    /// screen's preview when pressed.
    private static final class HolderProducer implements WidgetProducer {
        @Override
        public List<? extends AbstractWidget> produce(EditorWidgetFactory factory, ValueSlot slot) {
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
                || holder.unwrapKey().map(EditorWidgetFactory::isEmptyReference).orElse(false))
            .setOnCleared(b -> {
                ResourceKey<?> key = ((Holder<?>) slot.get()).unwrapKey().orElseThrow();
                Object empty = AkytheraProject.getCurrentProject()
                    .getEntryHolderOrDangling(ResourceKey.create(key.registryKey(), DataHolder.EMPTY_REFERENCE));

                clearSlot(slot, empty, b);
            });

            factory.bindPlaceholder(slot, box);

            return List.of(box);
        }

        @Override
        public void appendRows(EditorWidgetFactory factory, WidgetTable table, ValueSlot slot, int indent) {
            WidgetProducer.super.appendRows(factory, table, slot, indent);

            Class<?> valueType = typeArgumentOf(slot.field());

            // A montage-only row is left alone: a montage plays through a MontageAnimator carrying its
            // section info, which its own editor installs.
            if (valueType != null && valueType.isAssignableFrom(AnimationSequence.class)) {
                table.onRowPressed(() -> previewAnimation(slot));
            }
        }
    }

    /// Plays what `slot` holds on the open screen's preview. Read per press rather than captured, so
    /// the row follows whatever the picker last put in it.
    ///
    /// Nothing happens where there is nothing to play: a screen with no preview, a dangling holder, or
    /// a [com.yesman.akythera.core.animation.playable.BlendSpace2D] — it poses only under a driver's
    /// control point, so it carries no clip of its own and is previewed in its own editor.
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
            || AkytheraProject.getCurrentProject().getEntryFromProjectAndRegistry(key) != null)
        .setOnCleared(b -> clearSlot(
            slot, ResourceKey.create(((ResourceKey<?>) slot.get()).registryKey(), DataHolder.EMPTY_REFERENCE), b));

        factory.bindPlaceholder(slot, box);

        return List.of(box);
    }

    /// A combo over every entry of the registry a [ValueShape.RegistryLeaf] slot picks from, the project's
    /// entries merged with the loaded ones, see [AkytheraProject#getMergedEntries]. The "(none)" row writes
    /// the unset key.
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<? extends AbstractWidget> registryCombo(ValueSlot slot) {
        ValueShape.RegistryLeaf shape = (ValueShape.RegistryLeaf) slot.shape();
        Set<Map.Entry<ResourceKey<?>, ?>> entries = AkytheraProject.getCurrentProject().getMergedEntries((ResourceKey) shape.registry());
        List<ResourceKey<?>> keys = entries.stream()
            .<ResourceKey<?>>map(Map.Entry::getKey)
            .sorted(Comparator.comparing(ResourceProcessor::displayId))
            .toList();

        // Late-bound cell: the change consumer is a constructor argument and cannot capture
        // the widget it configures.
        EditorComboBox<Object>[] ref = new EditorComboBox[1];

        ref[0] = new EditorComboBox<>(
            font,
            columnStart(slot, 0, 1), columnSize(slot, 0, 1),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            slot::get,
            v -> {
                pushSlotChange(slot, v == null ? shape.unset() : v, ref[0]);

                if (v == null) ref[0].setValueQuiet(shape.unset());
            },
            Math.min(8, keys.size()),
            Component.empty(),
            List.copyOf(keys),
            v -> isEmptyReference((ResourceKey<?>) v) ? "<none>" : ResourceProcessor.displayId((ResourceKey<?>) v)
        );

        ref[0].setEmptyMessage(Component.translatable(LangKeys.GUI_WIDGET_ADVANCED_COMBO_BOX_NO_CANDIDATES));
        ref[0].setClearable(slot.nullable());
        ref[0].setSearchable(true);
        bindPlaceholder(slot, ref[0]);

        return List.of(ref[0]);
    }

    /// The key an "Edit Node Graph" tab opens under: `owner`'s identity plus the slot's field name, so
    /// re-clicking refocuses the open tab.
    public static String nodeGraphTabKey(Object owner, String slotName) {
        return System.identityHashCode(owner) + "/" + slotName;
    }

    /// Whether `key` is a [#nodeGraphTabKey] of `owner`.
    public static boolean isNodeGraphTabKeyOf(String key, Object owner) {
        return key.startsWith(System.identityHashCode(owner) + "/");
    }

    /// The context pins the canvas of a field shaped by `shape` carries.
    private static List<ContextEntryNodeWidget.ContextParam> nodeGraphParams(EditorNodeGraph shape) {
        List<ContextEntryNodeWidget.ContextParam> params = new ArrayList<>();

        for (int i = 0; i < shape.contextParams().length; i++) {
            Class<?> type = i < shape.contextParamTypes().length ? shape.contextParamTypes()[i] : Object.class;
            params.add(new ContextEntryNodeWidget.ContextParam(shape.contextParams()[i], type));
        }

        return params;
    }

    /// Validates every authored [EditorNodeGraph] field of the components under `host` into `compilation`, each
    /// in a canvas shaped like its "Edit Node Graph" tab and under the [#nodeGraphTabKey] the tab opens with. An
    /// empty canvas is unauthored and checks nothing.
    public static void compileNodeGraphs(Compilation compilation, ComponentAttachableHolder host, Class<?> ownerType) {
        Font font = Minecraft.getInstance().font;

        ComponentAttachableHolder.flatten(host).forEach(component -> {
            for (Class<?> c = component.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field field : c.getDeclaredFields()) {
                    EditorNodeGraph shape = field.getAnnotation(EditorNodeGraph.class);

                    if (shape == null || field.getType() != ScriptGraphData.class) continue;

                    field.setAccessible(true);

                    if (!(tryGet(field, component) instanceof ScriptGraphData graph) || graph.isEmpty()) continue;

                    ScriptNodeGraphWidget canvas = ScriptNodeGraphWidget.create(font, ownerType);
                    canvas.setComponentRoot(host);
                    canvas.importGraph(graph, ContextEntryNodeWidget.factory(font, nodeGraphParams(shape)));
                    canvas.setResultType(shape.resultType());

                    EditorEditable editable = field.getAnnotation(EditorEditable.class);
                    Component fallback = editable == null || editable.label().isEmpty()
                        ? Component.literal(field.getName())
                        : Component.translatable(editable.label());
                    Component title = component instanceof NodeGraphTitleProvider titles
                        ? titles.nodeGraphTabTitle(field.getName(), fallback)
                        : fallback;

                    compilation.canvas(nodeGraphTabKey(component, field.getName()), canvas, title);
                }
            }
        });
    }

    /// An "Edit Node Graph" button for a [ScriptGraphData] slot. Opens the slot's canvas as a
    /// closeable center tab of the hosting [NodeGraphTabHost] screen, shaped by the declaring
    /// field's [EditorNodeGraph] annotation (context pins, result type); the tab's flush writes the
    /// exported graph back through the slot, with no history entry of its own. The tab opens under
    /// [#nodeGraphTabKey], with a list element's `#n` label as its slot name.
    private static List<? extends AbstractWidget> nodeGraphButton(EditorWidgetFactory factory, ValueSlot slot) {
        Field f = slot.field();
        String labelText = slot.label();

        EditorNodeGraph shape = f == null ? null : f.getAnnotation(EditorNodeGraph.class);
        List<ContextEntryNodeWidget.ContextParam> params = shape == null ? List.of() : nodeGraphParams(shape);
        Class<?> resultType = shape == null ? boolean.class : shape.resultType();

        AkytheraButton button = AkytheraButton.buttonBuilder(factory.font, LocalizedMessage.handwritten("Edit Node Graph"), () -> {
            if (!(AkytheraEditorLayoutManager.getInstance().getRecentEditorScreen() instanceof NodeGraphTabHost editor)) return;

            ScriptGraphData data = (ScriptGraphData) slot.get();
            String slotName = f == null ? slot.label() : f.getName();
            Component fallback = f == null ? Component.literal(labelText) : Component.translatable(labelText);
            Component title = slot.owner() instanceof NodeGraphTitleProvider titles
                ? titles.nodeGraphTabTitle(slotName, fallback)
                : fallback;

            editor.openResultGraphTab(
                nodeGraphTabKey(slot.owner(), slotName),
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

    /// Combo over every registered [ScriptType] that has a codec, by id. Its "(none)" row clears the slot.
    private static List<? extends AbstractWidget> scriptTypeCombo(EditorWidgetFactory factory, ValueSlot slot) {
        List<Object> types = AkytheraRegistries.Frozen.SCRIPT_TYPE.stream()
            .filter(type -> type.codec() != null)
            .sorted(Comparator.comparing(type -> Objects.toString(AkytheraRegistries.Frozen.SCRIPT_TYPE.getKey(type))))
            .<Object>map(type -> type)
            .toList();

        // Late-bound cell: the change consumer is a constructor argument and cannot capture
        // the widget it configures.
        @SuppressWarnings("unchecked")
        EditorComboBox<Object>[] ref = new EditorComboBox[1];

        ref[0] = new EditorComboBox<>(
            factory.font,
            columnStart(slot, 0, 1), columnSize(slot, 0, 1),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            slot::get,
            v -> pushSlotChange(slot, v, ref[0]),
            Math.min(8, types.size()),
            Component.empty(),
            types,
            v -> v instanceof ScriptType type
                ? Objects.toString(AkytheraRegistries.Frozen.SCRIPT_TYPE.getKey(type))
                : Objects.toString(v)
        );

        ref[0].setClearable(slot.nullable());
        ref[0].setSearchable(true);
        factory.bindPlaceholder(slot, ref[0]);

        return List.of(ref[0]);
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
        EditorComboBox<Object>[] ref = new EditorComboBox[1];

        ref[0] = new EditorComboBox<>(
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

        ref[0].setClearable(slot.nullable());
        factory.bindPlaceholder(slot, ref[0]);

        return List.of(ref[0]);
    }

    /// Combo over the values [EditorEditable#candidates] resolves for this slot. Empty candidates
    /// leave the box showing the annotation's reason message.
    ///
    /// The list is snapshotted per fill. A stored value it does not contain still displays.
    private static List<? extends AbstractWidget> candidateCombo(EditorWidgetFactory factory, ValueSlot slot) {
        List<Object> candidates = resolveListMethod(slot, factory.context, factory.root, slot.annotation().candidates());
        Function<Object, Component> label = candidateMapper(slot, slot.annotation().candidateLabel(), "candidateLabel");
        Function<Object, Component> category = candidateMapper(slot, slot.annotation().candidateCategory(), "candidateCategory");

        // Late-bound cell: the change consumer is a constructor argument and cannot capture
        // the widget it configures.
        @SuppressWarnings("unchecked")
        EditorComboBox<Object>[] ref = new EditorComboBox[1];

        ref[0] = new EditorComboBox<>(
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
            v -> label == null || v == null ? candidateLabel(v) : label.apply(v).getString()
        );

        ref[0].setEmptyMessage(Component.translatable(
            slot.annotation().emptyCandidates().isBlank()
                ? LangKeys.GUI_WIDGET_ADVANCED_COMBO_BOX_NO_CANDIDATES
                : slot.annotation().emptyCandidates()
        ));

        ref[0].setCategoryMapper(category);
        ref[0].setClearable(slot.nullable());
        ref[0].setSearchable(slot.annotation().searchable());
        factory.bindPlaceholder(slot, ref[0]);

        return List.of(ref[0]);
    }

    /// The method `name` names for `slot`, taking one candidate and returning a [Component], or null when
    /// `name` is blank. `member` is the [EditorEditable] member named in the error a malformed method
    /// throws.
    private static @Nullable Function<Object, Component> candidateMapper(ValueSlot slot, String name, String member) {
        if (name.isBlank()) return null;

        Method method = findEditorMethod(declaringClassOf(slot), name, 1);

        if (method.getParameterCount() != 1 || !Component.class.isAssignableFrom(method.getReturnType())) {
            throw new IllegalStateException(
                "@EditorEditable(" + member + ") must take one candidate and return a Component: "
                    + method.getDeclaringClass().getName() + "#" + name);
        }

        return candidate -> (Component) Objects.requireNonNull(invokeEditorMethod(method, slot.owner(), candidate));
    }

    /// Invokes the [EditorEditable] method `name` names on the slot's owner, resolved on the class
    /// declaring the field, with the fill's context, else the factory's root, when the method takes one. A name matching
    /// no such method throws; a parameter neither fits yields no values.
    private static List<Object> resolveListMethod(ValueSlot slot, @Nullable Object context, @Nullable Object root, String name) {
        Method source = findEditorMethod(declaringClassOf(slot), name, 1);

        if (!List.class.isAssignableFrom(source.getReturnType())) {
            throw new IllegalStateException(
                "@EditorEditable method returns " + source.getReturnType().getName()
                    + ", which is not a List: " + source.getDeclaringClass().getName() + "#" + name);
        }

        Object[] args = new Object[source.getParameterCount()];

        if (args.length == 1) {
            Class<?> parameter = source.getParameterTypes()[0];

            // isInstance(null) is false, so a fill with neither takes this exit too.
            if (parameter.isInstance(context)) args[0] = context;
            else if (parameter.isInstance(root)) args[0] = root;
            else return List.of();
        }

        List<?> values = (List<?>) invokeEditorMethod(source, slot.owner(), args);

        return values == null ? List.of() : List.<Object>copyOf(values);
    }

    /// The class an [EditorEditable] method of `slot` is resolved on: the one declaring its field, or the
    /// owner's for a slot no field holds.
    private static Class<?> declaringClassOf(ValueSlot slot) {
        Field f = slot.field();

        return f != null ? f.getDeclaringClass() : slot.owner().getClass();
    }

    /// The method named `name` taking at most `maxParameters` parameters, on `declaring` or its
    /// superclasses. A name matching no such method throws.
    private static Method findEditorMethod(Class<?> declaring, String name, int maxParameters) {
        for (Class<?> c = declaring; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() <= maxParameters) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }

        throw new IllegalStateException(
            "@EditorEditable names no method taking at most " + maxParameters + " parameter(s): "
                + declaring.getName() + "#" + name);
    }

    private static @Nullable Object invokeEditorMethod(Method method, Object target, @Nullable Object... args) {
        try {
            return method.invoke(target, args);
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
                AnimationSelectModal modal = new AnimationSelectModal(font, getPreviewModelField(slot.owner()), (animClips, previewEntityType) -> {
                    var animClip = animClips.getFirst();

                    // Preview model first: the clip-change listener fired by the write below
                    // reads it.
                    if (previewEntityType != null) setPreviewModelField(slot.owner(), previewEntityType);
                    pushSlotChange(slot, animClip, box);

                    // pushSlotChange routes through valueSetter only on undo/redo, so a
                    // modal-driven box refreshes its display here.
                    box.valueSetter().accept(animClip);
                });

                ModalManager.getInstance().openModalWidgetAtCenter(modal);
            }
            case ARMATURE, ARMATURE_PHYSICS -> {
                AccessorSelectionModal modal = new AccessorSelectionModal(font, current.type(), accessor -> {
                    pushSlotChange(slot, accessor, box);
                    box.valueSetter().accept(accessor);
                });

                ModalManager.getInstance().openModalWidgetAtCenter(modal);
            }
            // A mesh slot takes either kind — a MeshComponent draws a static mesh as readily as a
            // skeletal one — so both pools are offered as one list and the pick decides the type.
            case SKELETAL_MESH, STATIC_MESH -> {
                List<AssetAccessor<?>> meshes = AkytheraProject.getCurrentProject().listAllMeshAccessors()
                    .sorted(Comparator.comparing(accessor -> accessor.identifier().toString()))
                    .<AssetAccessor<?>>map(accessor -> accessor)
                    .toList();

                AccessorSelectionModal modal = new AccessorSelectionModal(font, false, meshes, accessor -> {
                    pushSlotChange(slot, accessor, box);
                    box.valueSetter().accept(accessor);
                });

                ModalManager.getInstance().openModalWidgetAtCenter(modal);
            }
        }
    }

    private static String displayItem(Item item) {
        return item == Items.AIR ? "<none>" : item.getDefaultInstance().getHoverName().getString();
    }

    /// Opens the item picker for an [Item] slot, as a centered [ItemSelectModal] that closes itself
    /// on a pick.
    private void openItemSelect(ValueSlot slot, PopupBox<Object> box) {
        ItemSelectModal modal = new ItemSelectModal(font, item -> {
            ModalManager.getInstance().closeModal();
            pushSlotChange(slot, item, box);
            box.valueSetter().accept(item);
        });

        ModalManager.getInstance().openModalWidgetAtCenter(modal);
    }

    /// Opens the sound picker for a [SoundEvent] slot, as a centered [SoundSelectModal] that closes
    /// itself on a pick.
    private void openSoundSelect(ValueSlot slot, PopupBox<Object> box) {
        SoundSelectModal modal = new SoundSelectModal(font, soundEvent -> {
            ModalManager.getInstance().closeModal();
            pushSlotChange(slot, soundEvent, box);
            box.valueSetter().accept(soundEvent);
        });

        ModalManager.getInstance().openModalWidgetAtCenter(modal);
    }

    /// Opens the texture picker for a [ClientAsset.Texture] slot, as a centered [TextureSelectModal]
    /// that closes itself on a pick.
    private void openTextureSelect(ValueSlot slot, PopupBox<Object> box) {
        TextureSelectModal modal = new TextureSelectModal(font, TextureSelectModal.Mode.TEXTURE, texturePath -> {
            ClientAsset.ResourceTexture texture = AkytheraProject.resourceTextureOf(texturePath);

            ModalManager.getInstance().closeModal();
            pushSlotChange(slot, texture, box);
            box.valueSetter().accept(texture);
        });

        ModalManager.getInstance().openModalWidgetAtCenter(modal);
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
                resolveListMethod(slot, context, root, named);

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

        ModalManager.getInstance().openModalWidgetUnder(box, modal);
    }

    /// Opens the tag tree for an [AbilityTag] slot, or for an [AbilityTagSet] slot when `multiple`, dropped
    /// down under the box. A set slot takes every tick as its own history entry.
    private void openAbilityTagSelect(ValueSlot slot, PopupBox<Object> box, boolean multiple) {
        List<AbilityTag> picked = switch (slot.get()) {
            case AbilityTagSet set -> List.copyOf(set.tags());
            case AbilityTag tag -> List.of(tag);
            case null, default -> List.of();
        };

        AbilityTagSelectModal modal = new AbilityTagSelectModal(
            font,
            box.getWidth(),
            Component.translatable(multiple ? LangKeys.GUI_ABILITY_TAG_SELECT_MODAL_TITLE_MULTIPLE : LangKeys.GUI_ABILITY_TAG_SELECT_MODAL_TITLE),
            picked,
            multiple,
            tags -> {
                Object value = multiple ? AbilityTagSet.of(tags) : tags.isEmpty() ? null : tags.getFirst();

                pushSlotChange(slot, value, box);
                box.valueSetter().accept(value);
            }
        );

        ModalManager.getInstance().openModalWidgetUnder(box, modal);
    }

    /// Opens the animator-script picker for an [AnimatorScriptRef] slot, dropped down under the box.
    /// Lists class-based scripts (frozen `SCRIPT_TYPE` entries implementing [AnimatorScript]), then data
    /// pack script blueprints (the project's entries merged with the loaded world's); a data pack pick
    /// resolves to the shared live holder inside [AnimatorScriptRef#dataPack].
    private void openAnimatorScriptSelect(ValueSlot slot, PopupBox<Object> box) {
        List<CategorizedSelectModal.Choice<AnimatorScriptRef>> classScripts = AkytheraRegistries.Frozen.SCRIPT_TYPE.entrySet().stream()
            .filter(entry -> AnimatorScript.class.isAssignableFrom(entry.getValue().type()))
            .map(entry -> new CategorizedSelectModal.Choice<AnimatorScriptRef>(
                entry.getKey().identifier().toString(), new AnimatorScriptRef.ClassScript(entry.getValue())))
            .toList();

        List<CategorizedSelectModal.Choice<AnimatorScriptRef>> dataPackScripts = AkytheraProject.getCurrentProject()
            .getMergedEntries(AkytheraRegistries.DataPack.ANIMATOR_SCRIPT_KEY).stream()
            .map(entry -> new CategorizedSelectModal.Choice<AnimatorScriptRef>(
                entry.getKey().identifier().toString(), AnimatorScriptRef.dataPack(entry.getKey())))
            .toList();

        CategorizedSelectModal<AnimatorScriptRef> modal = new CategorizedSelectModal<>(
            font,
            box.getWidth(),
            Component.translatable(LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_ANIM_SCRIPT),
            List.of(
                new CategorizedSelectModal.Category<>(
                    Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SELECT_MODAL_CATEGORY_CLASS), classScripts),
                new CategorizedSelectModal.Category<>(
                    Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SELECT_MODAL_CATEGORY_DATA), dataPackScripts)
            ),
            picked -> {
                pushSlotChange(slot, picked, box);
                box.valueSetter().accept(picked);
            }
        );

        ModalManager.getInstance().openModalWidgetUnder(box, modal);
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

        ModalManager.getInstance().openModalWidgetUnder(box, modal);
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

        ModalManager.getInstance().openModalWidgetUnder(box, modal);
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

    /// The field name every holder gives its preview entity type. Matched by name, not by type —
    /// [Identifier] fields are too common for a type match to be unambiguous.
    private static final String PREVIEW_ENTITY_TYPE_FIELD = "previewEntityType";

    /// Writes `entityType` to the holder's preview entity type field, when it declares one.
    private static void setPreviewModelField(Object inst, Identifier entityType) {
        Field field = findPreviewModelField(inst);
        if (field != null) trySet(field, inst, entityType);
    }

    /// The holder's current preview entity type. Null when it declares no such field, or has not
    /// picked one yet.
    private static @Nullable Identifier getPreviewModelField(Object inst) {
        Field field = findPreviewModelField(inst);
        return field == null ? null : (Identifier) tryGet(field, inst);
    }

    private static @Nullable Field findPreviewModelField(Object inst) {
        for (Class<?> c = inst.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (field.getType() == Identifier.class && field.getName().equals(PREVIEW_ENTITY_TYPE_FIELD)) {
                    field.setAccessible(true);
                    return field;
                }
            }
        }

        return null;
    }

    /// The label cell, filling its [WidgetTable] title cell.
    public static Static makeLabel(Font font, Component component) {
        return new Static(
            font,
            AnchoredWidget.pixel(INDENT), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            component
        );
    }

    /// The label cell built from a lang key.
    public static Static makeTranslatableLabel(Font font, String text) {
        return makeLabel(font, Component.translatable(text));
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

        HistoryManager.getInstance().pushHistory(historyScope(slot), new DataChange<>(
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

        HistoryManager.getInstance().pushHistory(historyScope(slot), new DataChange<>(
            oldValue, newValue,
            val -> {
                slot.set(val);
                widget.valueSetter().accept(val);
                focusUndoneWidget(widget);
            }
        ));
    }

    /// Focuses the widget whose value an undo or redo just restored. A widget an [EmbeddedWidgetHost] in the
    /// screen holds is focused through that host — which opens the tab it sits in — and the screen focuses
    /// the path down to the host, see [EmbeddedWidgetHost#focusIn].
    public static void focusUndoneWidget(DataBoundWidget<?> widget) {
        if (!(widget instanceof GuiEventListener listener)) return;
        Screen screen = AkytheraEditorLayoutManager.getInstance().getRecentEditorScreen();
        if (screen == null) return;

        GuiEventListener root = EmbeddedWidgetHost.focusIn(screen.children(), listener);

        screen.setFocused(root != null ? root : listener);
    }

    /// What a change to `slot` is recorded under: its owner's [HistoryScoped#historyScope], else the owner.
    public static Object historyScope(ValueSlot slot) {
        return slot.owner() instanceof HistoryScoped scoped ? scoped.historyScope() : slot.owner();
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
