package com.yesman.akytheralabs.editor;

import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.client.api.gui.DragValueBox;
import com.yesman.akythera.client.api.gui.WidgetTable;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewerComponent;
import com.yesman.akythera.core.util.EnumerableDataBlock;
import com.yesman.akytheralabs.gui.widget.EditorComboBox;
import com.yesman.akytheralabs.gui.widget.viewer.DirectionArrowComponent;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.Vec3;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

/// Marks a mutable holder field that should be rendered into an editor
/// [WidgetTable] by [EditorWidgetFactory].
///
/// Field types the factory understands today: `float` / `int` / `double` (numeric box, or a
/// [DragValueBox] when both [#min] and [#max] are set),
/// `boolean` (checkbox), `String` (edit box), [LocalizedMessage] (text area with a lang key picker),
/// `enum` (ComboBox), [Vec3] (three
/// per-axis number boxes), [AssetAccessor] (a search PopupBox routed by the
/// accessor's runtime type), plus `EnumerableDataBlock` / `Holder` / `ResourceKey` /
/// `AnimatorScriptRef` / `ClientAsset.Texture` / `SoundEvent` pickers. Custom types register through
/// [EditorWidgetFactory#registerWidgetProducer]. Other types fall back to a
/// read-only display.
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface EditorEditable {
    /// Optional override for the displayed label. Falls back to the field name in spaced words,
    /// `keyMapping` showing as "Key Mapping".
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

    /// For [List] fields only; ignored otherwise. Names the **static method** — resolved by name on
    /// the class declaring the list field, the same way [#setter] is resolved — that builds one fresh
    /// element for the list row's `+` button. It takes no parameter, or the list itself, to build from
    /// what the list already holds.
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

    /// Fills an [EditorComboBox] from the named method, resolved like [#setter] but on the class
    /// declaring the field, which returns the values the field may hold as a [List]. The method takes
    /// no parameter, or one the fill's **context** object is assignable to — a Details row's context
    /// being the shown holder's parent.
    String candidates() default "";

    /// For [#candidates] rows only; ignored otherwise. Names the method, resolved like [#candidates],
    /// that takes one candidate and returns its dropdown label as a [Component].
    ///
    /// Blank shows an [EnumerableDataBlock]'s identifier, an enum constant's display name, or the
    /// value's own string form.
    String candidateLabel() default "";

    /// For [#candidates] rows only; ignored otherwise. Names the method, resolved like [#candidates],
    /// that takes one candidate and returns the heading it is listed under in the dropdown as a
    /// [Component]. [#candidates] must list the candidates of one heading next to each other.
    ///
    /// Blank lists the candidates without headings.
    String candidateCategory() default "";

    /// For [#candidates] rows only; ignored otherwise. Whether the dropdown shows a search box over the
    /// candidates. Set it where they are entries of a registry, such as every registered key mapping.
    ///
    /// The registry pickers ([Holder], [ResourceKey], [EnumerableDataBlock], [ScriptType]) always show
    /// one, and enum combos never do.
    boolean searchable() default false;

    /// Names the **no-argument `boolean` method**, resolved like [#candidates] and invoked on the
    /// instance being filled, that decides whether this row is shown. The row and everything nested
    /// under it are left out while it returns false.
    ///
    /// The method is asked on every fill. Set [#rebuildOnWrite] on the field it reads, so the rows
    /// follow that field's edits.
    ///
    /// Blank always shows the row.
    String visibleWhen() default "";

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

    /// For [Vec3] rows only; ignored otherwise. Marks the value as a direction: a box commit scales it
    /// to unit length (a zero vector keeps the value it had), and in a fill given a viewer
    /// ([EditorWidgetFactory#viewer]) focusing one of its boxes raises a [DirectionArrowComponent]
    /// there that turns it.
    boolean direction() default false;

    /// For [WidgetImage] rows only; ignored otherwise. Whether the texture is a GUI sprite id, picked among
    /// `textures/gui/sprites/**`, rather than a whole texture's path.
    boolean sprite() default false;

    /// In a fill given a viewer ([EditorWidgetFactory#viewer]), the type of the viewer component the row focuses when
    /// one of its widgets takes focus. [Model3DViewerComponent], the default, focuses nothing, and releases a component
    /// another row of the fill names. On a nested editor object, its rows take it unless they name their own. Ignored
    /// by the widget layout and widget graph rows, which lay out rows of their own.
    Class<? extends Model3DViewerComponent> viewerFocus() default Model3DViewerComponent.class;

    /// For numeric types only; ignored otherwise. With both [#min] and [#max] set, the row is a
    /// [DragValueBox] sliding between them instead of a typed number box.
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