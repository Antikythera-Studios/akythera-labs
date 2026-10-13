package com.yesman.akytheralabs.akythera.collider;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.component.TransformInheritPolicy;
import com.yesman.akythera.core.asset.physics.ArmaturePhysicsData;
import com.yesman.akythera.core.math.physics.collider.Collider;
import com.yesman.akythera.core.math.physics.collider.ColliderType;
import com.yesman.akythera.core.math.physics.collider.component.ColliderComponent;
import com.yesman.akytheralabs.akythera.entitypatch.component.TransformComponentHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.TransformSocketHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.ComponentDataHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.RootAttachableComponent;
import com.yesman.akytheralabs.akythera.itempatch.ItemPatchBlueprintHolder;
import com.yesman.akytheralabs.dataholder.TransformHolder;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// Editor holder for an `akythera:collider` ([Collider]) — one authored hit shape, pivoted inside
/// whatever hosts it.
///
/// [Collider#MAP_CODEC] dispatches the shapes on `shape` inside the one component type, so the shape
/// is a row here: [#shape] picks the [ColliderType] and [#dimensions] holds that shape's own
/// numbers, swapped through [ColliderShapeHolders] when the pick changes.
///
/// A [RootAttachableComponent] for [ItemPatchBlueprintHolder], which has no sockets to hang one off.
/// On an entity patch the root places nothing, so that registry's attach rule sends it under a
/// [TransformSocketHolder] instead.
public final class ColliderComponentHolder extends ComponentDataHolder<Collider>
    implements RootAttachableComponent, TransformComponentHolder
{
    /// Editor persistence: the pivot under `transform`, and the dimensions through
    /// [ColliderShapeHolders#CODEC], which keys the shape under `shape`.
    public static final MapCodec<ColliderComponentHolder> EDITOR_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
            TransformHolder.CODEC.fieldOf("transform").forGetter(ColliderComponentHolder::getLocalTransform),
            TransformInheritPolicy.CODEC.optionalFieldOf("transform_inherit_policy", TransformInheritPolicy.LOC_ROT_SCA)
                .forGetter(TransformComponentHolder::getTransformInheritPolicy),
            ColliderShapeHolders.CODEC.forGetter(ColliderComponentHolder::getDimensions)
        ).apply(instance, ColliderComponentHolder::new)
    );

    /// The shape [#dimensions] holds. Writing it swaps [#dimensions] for that shape's holder, so the
    /// row re-renders the block behind it.
    @EditorEditable(
        label = LangKeys.GUI_COLLIDER_SHAPE,
        order = 0,
        candidates = "shapeCandidates",
        rebuildOnWrite = true
    )
    private ColliderType shape;

    /// The elements of its host's space the shape takes on. Hidden on a bone body, which rides its bone in full.
    @EditorEditable(
        label = LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_TRANSFORM_INHERIT_POLICY,
        order = 1,
        visibleWhen = "isComponent"
    )
    private TransformInheritPolicy transformInheritPolicy;

    /// Where the shape sits inside its host. Read by every shape, and what a preview gizmo drags.
    @EditorEditable(label = LangKeys.GUI_COLLIDER_PIVOT_TRANSFORM, order = 2)
    private TransformHolder pivotTransform;

    /// [#shape]'s own numbers.
    @EditorEditable(label = LangKeys.GUI_COLLIDER_DIMENSIONS, order = 3)
    private ColliderShapeHolder dimensions;

    /// The shapes [#shape] may take, or null for every shape [ColliderShapeHolders] can author. A bone
    /// body narrows it to [ArmaturePhysicsData#BODY_SHAPES].
    private final @Nullable Set<ColliderType> allowedShapes;

    public ColliderComponentHolder(
        TransformHolder pivotTransform,
        TransformInheritPolicy transformInheritPolicy,
        ColliderShapeHolder dimensions
    ) {
        this(pivotTransform, transformInheritPolicy, dimensions, null);
    }

    public ColliderComponentHolder(
        TransformHolder pivotTransform,
        TransformInheritPolicy transformInheritPolicy,
        ColliderShapeHolder dimensions,
        @Nullable Set<ColliderType> allowedShapes
    ) {
        this.pivotTransform = pivotTransform;
        this.transformInheritPolicy = transformInheritPolicy;
        this.dimensions = dimensions;
        this.shape = dimensions.type();
        this.allowedShapes = allowedShapes;
    }

    /// A freshly attached collider: a unit box sitting on its host.
    public static ColliderComponentHolder createDefault() {
        return new ColliderComponentHolder(TransformHolder.identity(), TransformInheritPolicy.LOC_ROT_SCA, new OrientedBoxShapeHolder());
    }

    /// [#shape]'s [EditorEditable#candidates] source: the shapes [ColliderShapeHolders] can author.
    private List<ColliderType> shapeCandidates() {
        List<ColliderType> shapes = ColliderShapeHolders.shapes();

        return allowedShapes == null ? shapes : shapes.stream().filter(allowedShapes::contains).toList();
    }

    /// [#transformInheritPolicy]'s [EditorEditable#visibleWhen] source: a patch component, not a bone body.
    private boolean isComponent() {
        return allowedShapes == null;
    }

    public ColliderType getShape() {
        return shape;
    }

    /// The pivot transform.
    @Override
    public TransformHolder getLocalTransform() {
        return pivotTransform;
    }

    @Override
    public TransformInheritPolicy getTransformInheritPolicy() {
        return transformInheritPolicy;
    }

    public void setTransformInheritPolicy(TransformInheritPolicy transformInheritPolicy) {
        this.transformInheritPolicy = transformInheritPolicy;
    }

    public ColliderShapeHolder getDimensions() {
        return dimensions;
    }

    /// Swaps [#dimensions] for `shape`'s holder, at that shape's defaults. A shape with no registered
    /// holder, or one this collider may not take, is ignored — the combo offers none such.
    public void setShape(ColliderType shape) {
        if (this.shape == shape || (allowedShapes != null && !allowedShapes.contains(shape))) return;

        ColliderShapeHolder swapped = ColliderShapeHolders.create(shape);

        if (swapped == null) return;

        this.shape = shape;
        this.dimensions = swapped;
    }

    public void setPivotTransform(TransformHolder pivotTransform) {
        this.pivotTransform = pivotTransform;
    }

    @Override
    public void readFrom(Collider object) {
        pivotTransform.readFrom(object.getPivotTransform());
        transformInheritPolicy = object.getTransformInheritPolicy();
        setShape(object.type());
        dimensions.readFrom(object);
    }

    @Override
    public Collider makeObject() {
        return dimensions.makeCollider(pivotTransform.makeObject(), transformInheritPolicy, isEnabled());
    }

    /// Every shape builds a [ColliderComponent].
    @Override
    public Class<? extends PatchComponent> generateEventually() {
        return ColliderComponent.class;
    }

    // ***************************************************************
    // Source export (File > Export > As Source Code)
    // ***************************************************************

    @Override
    public String sourceTemplate() {
        return "components/collider.ftl";
    }

    /// Tokens for `components/collider.ftl`: the shape's [Collider] class, the pivot's
    /// [TransformHolder#sourceTokens], the inherit policy's constant name, and the shape's own constructor arguments.
    @Override
    public Map<String, Object> getDTO() {
        Map<String, Object> dto = new HashMap<>();
        dto.put("collider_type", dimensions.sourceType());
        dto.put("transform", pivotTransform.sourceTokens());
        dto.put("transform_inherit_policy", transformInheritPolicy.name());
        dto.put("arguments", dimensions.sourceArguments());

        return dto;
    }
}
