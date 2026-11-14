package com.yesman.akytheralabs.akythera.cinema;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.core.animation.playable.AnimationMontage;
import com.yesman.akythera.core.blueprint.particle.KeyframeTrack;
import com.yesman.akythera.core.cinema.CinemaScene;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.dataholder.AbstractDataHolder;
import com.yesman.akytheralabs.dataholder.DataHolder;
import com.yesman.akytheralabs.dataholder.EditorHolderCodecs;
import com.yesman.akytheralabs.dataholder.EditorReference;
import com.yesman.akytheralabs.dataholder.EntityPreviewModelMapping;
import com.yesman.akytheralabs.dataholder.TransformHolder;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/// Modifiable data holder for one [CinemaScene.Actor] of a cinema scene. The fields are the actor's Details rows,
/// and its camera keys are edited on the cinema track. [#previewEntityType] is editor-only.
///
/// A camera with keys plays its keys alone: [#camera] is then a scratch value the editor writes the keyed transform
/// at the playhead into ([#moveToTime]), and an edit to it shows until the playhead moves or a key takes it
/// ([#insertCameraKey]).
public final class CinemaActorHolder extends AbstractDataHolder<CinemaScene.Actor> {
    /// Two keys closer than this, in seconds, are at the same time.
    private static final float KEY_EPSILON = 1.0E-4F;

    /// Where a new actor's camera sits, in the actor's model space: behind the actor, at eye height.
    private static final Transform DEFAULT_CAMERA = Transform.fromTranslation(new Vec3(0.0D, 1.6D, 3.0D));

    public static final Codec<CinemaActorHolder> CODEC = RecordCodecBuilder.create(
        instance ->
            instance.group(
                Codec.STRING.fieldOf("role").forGetter(holder -> holder.role),
                EditorHolderCodecs.entry(AkytheraRegistries.DataPack.ANIM_MONTAGE_KEY).optionalFieldOf("montage")
                    .forGetter(holder -> holder.hasMontage() ? Optional.of(holder.montage) : Optional.empty()),
                Identifier.CODEC.optionalFieldOf("preview_entity_type").forGetter(ParseUtils.provideOptional(holder -> holder.previewEntityType)),
                TransformHolder.CODEC.optionalFieldOf("transform").forGetter(holder -> Optional.of(holder.transform)),
                Codec.BOOL.optionalFieldOf("ignore_block_collision", false).forGetter(holder -> holder.ignoreBlockCollision),
                Codec.BOOL.optionalFieldOf("ignore_entity_collision", false).forGetter(holder -> holder.ignoreEntityCollision),
                TransformHolder.CODEC.optionalFieldOf("camera").forGetter(holder -> Optional.of(holder.camera)),
                Codec.BOOL.optionalFieldOf("force_third_person", false).forGetter(holder -> holder.forceThirdPerson),
                Codec.BOOL.optionalFieldOf("camera_follows_actor", true).forGetter(holder -> holder.cameraFollowsActor),
                KeyframeTrack.codec(Transform.CODEC).optionalFieldOf("camera_keys", KeyframeTrack.empty()).forGetter(holder -> holder.cameraKeys)
            ).apply(instance, (role, montage, previewEntityType, transform, ignoreBlockCollision, ignoreEntityCollision, camera, forceThirdPerson, cameraFollowsActor, cameraKeys) -> {
                CinemaActorHolder holder = new CinemaActorHolder(role);

                montage.ifPresent(value -> holder.montage = value);
                previewEntityType.ifPresent(value -> holder.previewEntityType = value);
                transform.ifPresent(value -> holder.transform = value.hideScale());
                holder.ignoreBlockCollision = ignoreBlockCollision;
                holder.ignoreEntityCollision = ignoreEntityCollision;
                camera.ifPresent(value -> holder.camera = value.hideScale());
                holder.forceThirdPerson = forceThirdPerson;
                holder.cameraFollowsActor = cameraFollowsActor;
                holder.cameraKeys = cameraKeys;

                return holder;
            })
    );

    @EditorEditable(label = LangKeys.GUI_CINEMA_SCENE_SCREEN_WIDGET_ROLE, order = 0)
    private String role;

    @EditorEditable(label = LangKeys.GUI_CINEMA_SCENE_SCREEN_WIDGET_MONTAGE, order = 1)
    private Holder<AnimationMontage> montage = EditorHolderCodecs.resolve(AkytheraRegistries.DataPack.ANIM_MONTAGE_KEY, DataHolder.EMPTY_REFERENCE);

    /// The entity type the preview draws the actor as. Null draws it as the montage's preview entity type.
    @EditorEditable(
        label = LangKeys.GUI_CINEMA_SCENE_SCREEN_WIDGET_PREVIEW_MODEL, candidates = "previewEntityTypes", searchable = true, order = 2
    )
    private @Nullable Identifier previewEntityType;

    /// Where the actor stands when the scene starts, in the scene's space.
    @EditorEditable(label = LangKeys.GUI_CINEMA_SCENE_SCREEN_WIDGET_TRANSFORM, order = 3)
    private TransformHolder transform = TransformHolder.identity().hideScale();

    @EditorEditable(label = LangKeys.GUI_CINEMA_SCENE_SCREEN_WIDGET_IGNORE_BLOCK_COLLISION, order = 4)
    private boolean ignoreBlockCollision;

    @EditorEditable(label = LangKeys.GUI_CINEMA_SCENE_SCREEN_WIDGET_IGNORE_ENTITY_COLLISION, order = 5)
    private boolean ignoreEntityCollision;

    /// Where the actor's camera sits, looking along its -Z: in the actor's model space with [#cameraFollowsActor], in the
    /// scene's space without.
    @EditorEditable(label = LangKeys.GUI_CINEMA_SCENE_SCREEN_WIDGET_CAMERA, order = 6)
    private TransformHolder camera = TransformHolder.of(DEFAULT_CAMERA).hideScale();

    @EditorEditable(label = LangKeys.GUI_CINEMA_SCENE_SCREEN_WIDGET_FORCE_THIRD_PERSON, order = 7)
    private boolean forceThirdPerson;

    @EditorEditable(label = LangKeys.GUI_CINEMA_SCENE_SCREEN_WIDGET_CAMERA_FOLLOWS_ACTOR, order = 8)
    private boolean cameraFollowsActor = true;

    /// Keyed by seconds since the scene started.
    private KeyframeTrack<Transform> cameraKeys = KeyframeTrack.empty();

    public CinemaActorHolder(final String role) {
        this.role = role;
    }

    @Override
    public void readFrom(final CinemaScene.Actor object) {
        montage = EditorHolderCodecs.resolve(
            AkytheraRegistries.DataPack.ANIM_MONTAGE_KEY, object.montage().map(key -> key.identifier()).orElse(DataHolder.EMPTY_REFERENCE)
        );
        transform = TransformHolder.of(object.transform()).hideScale();
        ignoreBlockCollision = object.ignoreBlockCollision();
        ignoreEntityCollision = object.ignoreEntityCollision();
        forceThirdPerson = object.camera().forceThirdPerson();
        cameraFollowsActor = object.camera().followsActor();
        cameraKeys = object.camera().keys();

        if (!cameraKeys.isEmpty()) camera = TransformHolder.of(cameraKeys.keys().getFirst().value()).hideScale();
    }

    /// The scale of the actor's transform and of its camera keys is left at one.
    @Override
    public CinemaScene.Actor makeObject() {
        return new CinemaScene.Actor(
            transform.makeObject(),
            hasMontage() ? montage.unwrapKey() : Optional.empty(),
            new CinemaScene.Camera(cameraKeys, forceThirdPerson, cameraFollowsActor),
            ignoreBlockCollision,
            ignoreEntityCollision
        );
    }

    @Override
    public Stream<EditorReference> referencedReferences() {
        return hasMontage() ? EditorHolderCodecs.referenceOf(montage) : Stream.empty();
    }

    public String getRole() {
        return role;
    }

    public Holder<AnimationMontage> getMontage() {
        return montage;
    }

    public @Nullable Identifier getPreviewEntityType() {
        return previewEntityType;
    }

    /// The entity types the preview can draw an actor as.
    public static List<Identifier> previewEntityTypes() {
        return EntityPreviewModelMapping.previewableTypes().stream().map(BuiltInRegistries.ENTITY_TYPE::getKey).toList();
    }

    public boolean isIgnoreBlockCollision() {
        return ignoreBlockCollision;
    }

    public boolean isIgnoreEntityCollision() {
        return ignoreEntityCollision;
    }

    public boolean isForceThirdPerson() {
        return forceThirdPerson;
    }

    public boolean isCameraFollowsActor() {
        return cameraFollowsActor;
    }

    /// Whether a montage is picked.
    public boolean hasMontage() {
        return !EditorHolderCodecs.entryId(montage).equals(DataHolder.EMPTY_REFERENCE);
    }

    public TransformHolder getTransform() {
        return transform;
    }

    public TransformHolder getCamera() {
        return camera;
    }

    public KeyframeTrack<Transform> getCameraKeys() {
        return cameraKeys;
    }

    public void setCameraKeys(final KeyframeTrack<Transform> cameraKeys) {
        this.cameraKeys = cameraKeys;
    }

    /// Keys the camera at `seconds` since the scene started with [#camera], replacing a key at the same time.
    public void insertCameraKey(final float seconds) {
        insertCameraKey(seconds, camera.makeObject());
    }

    /// Keys the camera at `seconds` since the scene started with `value`, replacing a key at the same time.
    public void insertCameraKey(final float seconds, final Transform value) {
        List<KeyframeTrack.Key<Transform>> keys = new ArrayList<>(cameraKeys.keys());

        keys.removeIf(key -> Math.abs(key.time() - seconds) < KEY_EPSILON);
        keys.add(new KeyframeTrack.Key<>(Math.max(seconds, 0.0F), new Transform(value.loc(), new Quaterniond(value.rot()), Transform.IDENTITY.sca())));
        cameraKeys = new KeyframeTrack<>(keys);
    }

    /// Removes the camera key at `seconds` since the scene started, if there is one.
    public void removeCameraKey(final float seconds) {
        cameraKeys = new KeyframeTrack<>(cameraKeys.keys().stream().filter(key -> Math.abs(key.time() - seconds) >= KEY_EPSILON).toList());
    }

    /// Writes the keyed camera transform at `seconds` since the scene started into [#camera].
    public void moveToTime(final float seconds) {
        if (!cameraKeys.isEmpty()) camera.readFrom(cameraKeys.sample(seconds, Transform.IDENTITY, Transform::slerp));
    }
}
