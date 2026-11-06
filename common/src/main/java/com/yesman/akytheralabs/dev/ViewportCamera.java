package com.yesman.akytheralabs.dev;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/// A world viewport camera apart from the local player. While placed, the game camera still follows the local player,
/// so input and possession stay on it, but the world renders from here, in third person.
public final class ViewportCamera {
    /// Where the camera stands: in world space, or as an offset from [#followed]'s position while it follows one.
    private @Nullable Vec3 position;

    /// Turn of a fixed camera, in degrees.
    private float yaw;
    private float pitch;

    /// The entity the camera moves with and faces, or null for a fixed camera.
    private @Nullable Entity followed;

    /// The camera type before the camera was placed, given back on [#release]. Null while not placed.
    private @Nullable CameraType replacedCameraType;

    /// Fixes the camera at `position`, turned to `yaw` / `pitch` in degrees.
    public void place(final Vec3 position, final float yaw, final float pitch) {
        detach();

        this.position = position;
        this.yaw = yaw;
        this.pitch = pitch;
        this.followed = null;
    }

    /// Keeps the camera `offset` away from `entity`'s position, facing its eyes, as the entity moves.
    public void follow(final Entity entity, final Vec3 offset) {
        detach();

        this.position = offset;
        this.followed = entity;
    }

    /// Gives the view back to the local player and restores the camera type.
    public void release() {
        if (replacedCameraType != null) Minecraft.getInstance().options.setCameraType(replacedCameraType);

        position = null;
        followed = null;
        replacedCameraType = null;
    }

    /// Where the camera stands at `partialTick` and where it looks, or null while it is not placed.
    public @Nullable Placement placement(final float partialTick) {
        if (position == null) return null;
        if (followed == null) return new Placement(position, yaw, pitch);

        Vec3 entityPosition = followed.getPosition(partialTick);

        return Placement.lookingAt(entityPosition.add(position), entityPosition.add(0.0D, followed.getEyeHeight(), 0.0D));
    }

    /// Switches to third person, so the world renders the local player and no hand, keeping the camera type to restore.
    private void detach() {
        Minecraft minecraft = Minecraft.getInstance();

        if (replacedCameraType == null) replacedCameraType = minecraft.options.getCameraType();

        minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
    }

    /// A camera position in world space and its turn in degrees.
    public record Placement(Vec3 position, float yaw, float pitch) {
        /// A placement at `from` facing `to`.
        public static Placement lookingAt(final Vec3 from, final Vec3 to) {
            Vec3 direction = to.subtract(from);
            float yaw = (float)(Mth.atan2(direction.z, direction.x) * Mth.RAD_TO_DEG) - 90.0F;
            float pitch = (float)(-Mth.atan2(direction.y, direction.horizontalDistance()) * Mth.RAD_TO_DEG);

            return new Placement(from, yaw, pitch);
        }
    }
}
