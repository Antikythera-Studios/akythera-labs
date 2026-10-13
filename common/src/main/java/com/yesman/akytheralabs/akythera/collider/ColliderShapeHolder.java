package com.yesman.akytheralabs.akythera.collider;

import com.yesman.akythera.api.component.TransformInheritPolicy;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.math.physics.collider.Collider;
import com.yesman.akythera.core.math.physics.collider.ColliderType;
import com.yesman.akytheralabs.editor.EditorEditable;

/// Editor twin of one [ColliderType]'s dimensions — the numbers a [Collider] subclass carries on top
/// of the pivot transform, as [EditorEditable] fields the Details panel binds rows to.
///
/// The pivot belongs to [ColliderComponentHolder] and is shared by every shape, so a shape holder
/// only ever reads and writes its own dimensions.
public abstract class ColliderShapeHolder {
    /// The shape this holder authors — the [ColliderType] it is registered under in
    /// [ColliderShapeHolders].
    public abstract ColliderType type();

    /// Overwrites the dimensions from `collider`, which is of this holder's shape.
    public abstract void readFrom(final Collider collider);

    /// The authored shape, pivoted at `pivotTransform`, whose components start switched as `enabled`.
    public abstract Collider makeCollider(
        final Transform pivotTransform,
        final TransformInheritPolicy transformInheritPolicy,
        final boolean enabled
    );

    /// The [Collider] class name this shape builds, for `components/collider.ftl`.
    public abstract String sourceType();

    /// The constructor arguments after the pivot transform, as one Java expression list, for
    /// `components/collider.ftl`.
    public abstract String sourceArguments();
}
