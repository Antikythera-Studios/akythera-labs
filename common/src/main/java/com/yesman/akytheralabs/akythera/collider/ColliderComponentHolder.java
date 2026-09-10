package com.yesman.akytheralabs.akythera.collider;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.math.physics.collider.Collider;
import com.yesman.akythera.core.math.physics.collider.ColliderType;
import com.yesman.akythera.core.math.physics.collider.component.ColliderComponent;
import com.yesman.akytheralabs.akythera.entitypatch.component.BoneSocketHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.ComponentDataHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.RootAttachableComponent;
import com.yesman.akytheralabs.akythera.itempatch.ItemPatchBlueprintHolder;
import com.yesman.akytheralabs.dataholder.TransformHolder;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;
import org.joml.Quaterniond;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/// Editor holder for an `akythera:collider` ([Collider]) — one authored hit shape, pivoted inside
/// whatever hosts it.
///
/// [Collider#MAP_CODEC] dispatches the shapes on `shape` inside the one component type, so the shape
/// is a row here: [#shape] picks the [ColliderType] and [#dimensions] holds that shape's own
/// numbers, swapped through [ColliderShapeHolders] when the pick changes.
///
/// A [RootAttachableComponent] for [ItemPatchBlueprintHolder], which has no sockets to hang one off.
/// On an entity patch the root places nothing, so that registry's attach rule sends it under a
/// [BoneSocketHolder] instead.
public final class ColliderComponentHolder extends ComponentDataHolder<Collider>
    implements RootAttachableComponent
{
    /// Editor persistence: [Collider#MAP_CODEC] mapped through, so a collider's editor JSON and its
    /// datapack JSON carry the same block.
    public static final MapCodec<ColliderComponentHolder> EDITOR_CODEC =
        Collider.MAP_CODEC.xmap(ColliderComponentHolder::of, ColliderComponentHolder::makeObject);

    /// The shape [#dimensions] holds. Writing it swaps [#dimensions] for that shape's holder, so the
    /// row re-renders the block behind it.
    @EditorEditable(
        label = LangKeys.GUI_COLLIDER_SHAPE,
        order = 0,
        candidates = "shapeCandidates",
        rebuildOnWrite = true
    )
    private ColliderType shape;

    /// Where the shape sits inside its host. Read by every shape, and what a preview gizmo drags.
    @EditorEditable(label = LangKeys.GUI_COLLIDER_PIVOT_TRANSFORM, order = 1)
    private TransformHolder pivotTransform;

    /// [#shape]'s own numbers.
    @EditorEditable(label = LangKeys.GUI_COLLIDER_DIMENSIONS, order = 2)
    private ColliderShapeHolder dimensions;

    public ColliderComponentHolder(TransformHolder pivotTransform, ColliderShapeHolder dimensions) {
        this.pivotTransform = pivotTransform;
        this.dimensions = dimensions;
        this.shape = dimensions.type();
    }

    /// A freshly attached collider: a unit box sitting on its host.
    public static ColliderComponentHolder createDefault() {
        return new ColliderComponentHolder(TransformHolder.identity(), new OrientedBoxShapeHolder());
    }

    /// A holder over `collider`'s pivot and dimensions.
    public static ColliderComponentHolder of(Collider collider) {
        ColliderComponentHolder holder = createDefault();
        holder.readFrom(collider);

        return holder;
    }

    /// [#shape]'s [EditorEditable#candidates] source: the shapes [ColliderShapeHolders] can author.
    private List<ColliderType> shapeCandidates() {
        return ColliderShapeHolders.shapes();
    }

    public ColliderType getShape() {
        return shape;
    }

    public TransformHolder getPivotTransform() {
        return pivotTransform;
    }

    public ColliderShapeHolder getDimensions() {
        return dimensions;
    }

    /// Swaps [#dimensions] for `shape`'s holder, at that shape's defaults. A shape with no registered
    /// holder is ignored — the combo offers none such.
    public void setShape(ColliderType shape) {
        if (this.shape == shape) return;

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
        setShape(object.type());
        dimensions.readFrom(object);
    }

    @Override
    public Collider makeObject() {
        return dimensions.makeCollider(pivotTransform.makeObject());
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

    /// Tokens for `components/collider.ftl`: the shape's [Collider] class, the pivot flattened into
    /// its ten scalars, and the shape's own constructor arguments.
    @Override
    public Map<String, Object> getDTO() {
        Transform transform = pivotTransform.makeObject();
        Quaterniond rotation = transform.rot();

        Map<String, Object> dto = new HashMap<>();
        dto.put("collider_type", dimensions.sourceType());
        dto.put("location_x", transform.loc().x);
        dto.put("location_y", transform.loc().y);
        dto.put("location_z", transform.loc().z);
        dto.put("rotation_x", rotation.x());
        dto.put("rotation_y", rotation.y());
        dto.put("rotation_z", rotation.z());
        dto.put("rotation_w", rotation.w());
        dto.put("scale_x", transform.sca().x);
        dto.put("scale_y", transform.sca().y);
        dto.put("scale_z", transform.sca().z);
        dto.put("arguments", dimensions.sourceArguments());

        return dto;
    }
}
