package com.yesman.akytheralabs.mixinaccessor;

import com.yesman.akythera.core.math.physics.ragdoll.RagdollSimulation;
import com.yesman.akytheralabs.gui.widget.viewer.RagdollDragComponent;
import net.minecraft.world.phys.Vec3;

/// Holds a [RagdollSimulation] body by hand, for the [RagdollDragComponent] the editor tests rag dolls with.
public interface RagdollSimulationAccessor {
    /// Holds the body of `boneName` by the head of its bone at `worldTarget` until [#akytheralabs$release], with no give.
    /// A bone without a body hands the grip to its closest bodied ancestor, or to the body closest to the root.
    /// Grabbing again moves the target.
    void akytheralabs$grab(String boneName, Vec3 worldTarget);

    void akytheralabs$release();
}
