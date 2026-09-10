package com.yesman.akytheralabs.editor;

import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.client.gui.widget.WidgetTable;
import com.yesman.akytheralabs.gui.widget.AdvancedComboBox;
import net.minecraft.world.phys.Vec3;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

/// Marks a mutable holder field that should be rendered into an editor
/// [WidgetTable] by [EditorWidgetFactory].
///
/// Field types the factory understands today: `float` / `int` (numeric box),
/// `boolean` (checkbox), `String` (edit box), `enum` (ComboBox), [Vec3] (three
/// per-axis number boxes), [AssetAccessor] (a search PopupBox routed by the
/// accessor's runtime type), plus `EnumerableDataBlock` / `Holder` / `ResourceKey` /
/// `AnimatorScriptRef` pickers. Custom types register through
/// [EditorWidgetFactory#registerWidgetProducer]. Other types fall back to a
/// read-only display.
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface EditorEditable {
    /// Optional override for the displayed label. Falls back to the field name.
    String label() default "";

    /// Optional properties binder in one indented block in [WidgetTable]. Every field sharing a
    /// category is gathered under one translated title row and indented one level below it.
    ///
    /// Use `/` to separate categories, to express multi-layered depth.
    ///
    /// e.g. `"…category.animations/…category.attack"` becomes
    ///
    /// ```
    /// Animations
    /// L Attack
    ///   L property1
    ///   L property2
    ///   ...
    /// ```
    ///
    /// A category outranks [#order] when the two disagree: fields of one category are always
    /// coupled, and the group as a whole takes the position of its lowest-ordered member (so
    /// `order` decides where the block lands and how its rows sort *within* the block, never
    /// whether the block is split apart). Uncategorized fields order against the blocks by
    /// their own `order`.
    String category() default "";

    /// Optional override for the setter method the factory writes through, resolved on the runtime
    /// instance's class against the field's type. Falls back to the `set<FieldName>` bean convention
    /// (then to direct field access when even that is absent). Use it when the canonical bean setter
    /// isn't the one the editor should call — e.g. a coordinate field whose `setX` snaps to a grid,
    /// while the numeric inspector must write the raw value through `setXUnsnapped`.
    String setter() default "";

    /// For [List] fields only; ignored otherwise. Names the **static, no-argument method** — resolved
    /// by name on the class declaring the list field, the same way [#setter] is resolved — that
    /// builds one fresh element for the list row's `+` button.
    ///
    /// A method name rather than a type: an annotation member has to be a compile-time constant, and
    /// the elements this editor creates — a first-registered `EnumerableDataBlock`, a dangling project
    /// `Holder` — come from neither a `Class` literal nor a no-argument constructor.
    ///
    /// The named method may answer null, meaning there is nothing to build an element from yet. The
    /// `+` then reports [#emptyCandidates] instead of adding a row.
    ///
    /// Left blank, the list renders read-only: its elements are still shown and editable, but without
    /// the `+` / `✕` buttons.
    String defaultElement() default "";

    /// Fills an [AdvancedComboBox] from the named method, resolved like [#setter] but on the class
    /// declaring the field, which returns the values the field may hold as a [List]. The method takes
    /// no parameter, or one the fill's **context** object is assignable to — a Details row's context
    /// being the shown holder's parent.
    String candidates() default "";

    /// Lang key naming the reason there is nothing to pick: shown in place of an empty [#candidates]
    /// dropdown, and by a list row's `+` when [#defaultElement] answers null. Blank falls back to a
    /// generic message.
    String emptyCandidates() default "";

    /// For [net.minecraft.core.Holder] slots. Names the **static, no-argument method** — resolved
    /// like [#setter], on the class declaring the field — returning the registries the slot's search
    /// picker lists, as a `List<ResourceKey<? extends Registry<?>>>`.
    ///
    /// Blank — the default — lists only the registry the slot's current key addresses.
    String registries() default "";

    /// Whether the value reaches [#setter] only once the row commits — an edit box losing focus, or
    /// taking Enter — instead of on every keystroke. The widget keeps the in-flight value meanwhile,
    /// so the row still shows what is being typed.
    ///
    /// Set it where a half-typed value costs something: an identifier naming a texture reaches the
    /// texture manager, which chases the path, fails, and caches the miss — once per character.
    ///
    /// Honored by the widgets that have an in-flight value at all: the number boxes, the string box,
    /// and the [Vec3] / quaternion rows. A checkbox, a combo box and the pickers change and commit in
    /// the same motion, and write through either way.
    boolean setOnCommit() default false;

    /// Whether a write into this row re-renders the block it sits in. Set it where the value decides
    /// which other rows exist — a shape combo whose pick swaps the dimensions block under it.
    ///
    /// Honored only in a fill that was given a rebuild callback, see
    /// [EditorWidgetFactory#fillWidgetTable].
    boolean rebuildOnWrite() default false;

    /// For numeric types only; ignored otherwise.
    float min() default -Float.MAX_VALUE;

    float max() default Float.MAX_VALUE;

    /// Display order — ascending. Equal values are tie-broken by declaration
    /// order within a class.
    int order() default 0;

    /// Whether this row takes part in inheritance when the block is shown over an inherited
    /// counterpart ([EditorWidgetFactory#fillWidgetTable]): its label greys while it holds what the
    /// counterpart holds. Reverting is a whole-component gesture in the Components panel, so the row
    /// carries nothing of its own for it.
    ///
    /// A field that can be unset also draws the inherited value as its widget's placeholder while it
    /// is.
    boolean inheritable() default false;

    /// Whether the field belongs to the patch that first declared the component. A patch overriding
    /// that component copies the value verbatim, draws the row grey and takes no input on it —
    /// changing it would re-point which declaration is being overridden. With nothing inherited
    /// behind the block, the row is editable like any other.
    boolean declarerOwned() default false;
}