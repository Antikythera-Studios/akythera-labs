package com.yesman.akytheralabs.dataholder;

import com.mojang.serialization.Codec;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;

import java.util.HashMap;
import java.util.Map;

/// Mutable editor mirror of [Transform] — location, rotation, scale as reassignable fields the
/// Details panel can bind rows to, where the runtime record exposes only components of an immutable
/// value.
///
/// A holder with a transform property declares one of these. The panel renders it as a nested
/// editor object by walking its [EditorEditable] fields, so a transform reads the same everywhere it
/// appears — location / rotation / scale rows under the property's own label — and each row's
/// writes and undo entries land on this object.
///
/// [#CODEC] is [Transform#CODEC] mapped through, so the editor and the engine persist a transform
/// in exactly one shape — declared once, by the engine.
public final class TransformHolder extends AbstractDataHolder<Transform> {
    public static final Codec<TransformHolder> CODEC =
        Transform.CODEC.xmap(TransformHolder::of, TransformHolder::makeObject);

    @EditorEditable(label = LangKeys.GUI_TRANSFORM_LOCATION, order = 0)
    private Vec3 location;

    /// Raw quaternion components, matching what [Transform] stores. The row shows it as Euler angles
    /// and writes a whole replacement quaternion rather than mutating this one.
    @EditorEditable(label = LangKeys.GUI_TRANSFORM_ROTATION, order = 1)
    private Quaterniond rotation;

    @EditorEditable(label = LangKeys.GUI_TRANSFORM_SCALE, order = 2)
    private Vec3 scale;

    public TransformHolder(Vec3 location, Quaterniond rotation, Vec3 scale) {
        this.location = location;
        this.rotation = rotation;
        this.scale = scale;
    }

    /// The no-op transform — what a freshly added transform property starts at.
    public static TransformHolder identity() {
        return of(Transform.IDENTITY);
    }

    /// A holder over a copy of `transform`'s components. The quaternion is copied because JOML's is
    /// mutable and the source may be a shared constant ([Transform#IDENTITY]).
    public static TransformHolder of(Transform transform) {
        return new TransformHolder(transform.loc(), new Quaterniond(transform.rot()), transform.sca());
    }

    public Vec3 getLocation() {
        return location;
    }

    public Quaterniond getRotation() {
        return rotation;
    }

    public Vec3 getScale() {
        return scale;
    }

    public void setLocation(Vec3 location) {
        this.location = location;
    }

    public void setRotation(Quaterniond rotation) {
        this.rotation = rotation;
    }

    public void setScale(Vec3 scale) {
        this.scale = scale;
    }

    @Override
    public void readFrom(Transform object) {
        location = object.loc();
        rotation = new Quaterniond(object.rot());
        scale = object.sca();
    }

    /// Tokens for the `transform` macro of `source_templates/transform.ftl`: the transform flattened into its ten
    /// scalars.
    public Map<String, Object> sourceTokens() {
        Map<String, Object> tokens = new HashMap<>();

        tokens.put("location_x", location.x);
        tokens.put("location_y", location.y);
        tokens.put("location_z", location.z);
        tokens.put("rotation_x", rotation.x());
        tokens.put("rotation_y", rotation.y());
        tokens.put("rotation_z", rotation.z());
        tokens.put("rotation_w", rotation.w());
        tokens.put("scale_x", scale.x);
        tokens.put("scale_y", scale.y);
        tokens.put("scale_z", scale.z);

        return tokens;
    }

    /// The runtime value. The quaternion is copied out, so a transform already handed to a built
    /// blueprint never changes under it when the author edits the rows again.
    @Override
    public Transform makeObject() {
        return new Transform(location, new Quaterniond(rotation), scale);
    }
}