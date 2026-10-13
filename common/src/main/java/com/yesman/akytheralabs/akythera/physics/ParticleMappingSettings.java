package com.yesman.akytheralabs.akythera.physics;

import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.world.phys.Vec3;

/// The auto pairing the Mapping Tool Panel edits.
public final class ParticleMappingSettings {
    /// Which way an offset vertex lies from its simulating vertex, in the mesh's bind space. Unit length.
    @EditorEditable(label = LangKeys.GUI_CLOTH_MAPPING_DIRECTION, order = 0, min = -1.0F, max = 1.0F, direction = true)
    private Vec3 direction = new Vec3(0.0D, 0.0D, 1.0D);

    /// Widest angle between [#direction] and a pair, in degrees
    @EditorEditable(label = LangKeys.GUI_CLOTH_MAPPING_MAX_ANGLE, order = 1, min = 0.0F, max = 89.0F)
    private float maxAngle = 45.0F;

    public Vec3 getDirection() {
        return direction;
    }

    public float getMaxAngle() {
        return maxAngle;
    }
}
