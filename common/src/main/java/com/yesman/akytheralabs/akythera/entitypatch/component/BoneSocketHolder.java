package com.yesman.akytheralabs.akythera.entitypatch.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.core.asset.armature.BoneSocket;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.dataholder.AbstractDataHolder;
import com.yesman.akytheralabs.dataholder.TransformHolder;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.resources.Identifier;
import org.joml.Quaterniond;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedMap;

/// Editor holder for an `akythera:bone_socket` ([BoneSocket]) — a named point riding along one bone
/// of an armature, at a fixed offset from it, that other things (an item model, a particle emitter,
/// a child component) can be bound to.
///
/// Not a [RootAttachableComponent] — a socket names a bone, so it is only reachable through
/// [ArmatureComponentHolder]'s attach menu.
///
/// It is itself a [ComponentAttachableHolder]: whatever hangs off it is placed by the bone it rides.
/// See [#attachableTypes].
public final class BoneSocketHolder extends ComponentDataHolder<BoneSocket> implements ComponentAttachableHolder {
    /// Editor persistence. The transform rides [TransformHolder#CODEC], which is [Transform#CODEC]
    /// mapped through, so a socket's editor JSON and its datapack JSON carry the same transform
    /// block.
    ///
    /// The transform decodes through an [Optional]. A codec's default value is a single instance
    /// shared by every decode that falls back to it, and these holders are mutable, so a missing
    /// `transform` would wire every socket in the project to the same one.
    ///
    /// The attached components ride [ComponentAttachableHolder#componentsField], which dispatches
    /// back through the same registry this codec is registered in. [Codec#lazyInitialized] breaks
    /// that cycle, the registry's static initializer being what registers this very field.
    public static final MapCodec<BoneSocketHolder> EDITOR_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
            Codec.STRING.optionalFieldOf("bone_name", "").forGetter(BoneSocketHolder::getBoneName),
            TransformHolder.CODEC.optionalFieldOf("transform")
                .forGetter(h -> Optional.of(h.localTransform)),
            ComponentAttachableHolder.componentsField(() -> EntityPatchComponentHolders.REGISTRY)
                .forGetter(ComponentAttachableHolder::componentsOf)
        ).apply(instance, (boneName, transform, components) -> {
            BoneSocketHolder holder = new BoneSocketHolder(boneName, transform.orElseGet(TransformHolder::identity));
            ComponentAttachableHolder.applyComponents(holder, components);
            return holder;
        })
    );

    /// The armature bone this socket rides, picked from [#boneCandidates] — the bones of the armature
    /// its parent component points at.
    @EditorEditable(
        label = LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_BONE_NAME,
        order = 0,
        candidates = "boneCandidates",
        emptyCandidates = LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_BONE_NAME_NO_ARMATURE
    )
    private String boneName;

    /// Offset from the bound bone. A nested editor object, rendered as location / rotation / scale
    /// rows under one "Local Transform" heading.
    @EditorEditable(label = LangKeys.GUI_ENTITY_PATCH_SCREEN_SECTION_LOCAL_TRANSFORM, order = 1)
    private TransformHolder localTransform;

    /// The attached component holders by name — the socket's [ComponentAttachableHolder] children,
    /// edited as structure in the Components tree rather than as Details rows.
    private final SequencedMap<String, ComponentDataHolder<?>> components = ComponentAttachableHolder.newComponentMap();

    public BoneSocketHolder(String boneName, TransformHolder localTransform) {
        this.boneName = boneName;
        this.localTransform = localTransform;
    }

    /// A freshly attached socket: a blank `boneName`, sitting exactly on its bone.
    public static BoneSocketHolder createDefault() {
        return new BoneSocketHolder("", TransformHolder.identity());
    }

    /// [#boneName]'s [EditorEditable#candidates] source: the bones of the armature this socket hangs off.
    private List<String> boneCandidates(@Nullable Object parent) {
        return parent instanceof ArmatureComponentHolder armature ? armature.boneNames() : List.of();
    }

    public String getBoneName() {
        return boneName;
    }

    public TransformHolder getLocalTransform() {
        return localTransform;
    }

    public void setBoneName(String boneName) {
        this.boneName = boneName;
    }

    public void setLocalTransform(TransformHolder localTransform) {
        this.localTransform = localTransform;
    }

    /// What a socket hosts on the bone it rides: an [EquipmentComponentHolder] drawing one equipment
    /// slot's item, and a [MeshComponentHolder] drawing a scripted mesh.
    @Override
    public List<Class<?>> attachableTypes() {
        return List.of(EquipmentComponentHolder.class, MeshComponentHolder.class);
    }

    @Override
    public SequencedMap<String, ComponentDataHolder<?>> getComponents() {
        return components;
    }

    /// The first attached component the decode guard salvaged, if any.
    @Override
    public @Nullable Exception getNestedFailureException() {
        return AbstractDataHolder.firstNestedFailure(components.values());
    }

    @Override
    public void readFrom(BoneSocket object) {
        boneName = object.getBoneName();
        localTransform.readFrom(object.getLocalTransform());

        components.clear();
        object.getChildBlueprints().forEach((name, blueprint) -> {
            Identifier typeId = AkytheraRegistries.Frozen.COMPONENT_BLUEPRINT_TYPES.getKey(blueprint.mapCodec());
            if (typeId == null) return;

            ComponentDataHolder<?> holder = EntityPatchComponentHolders.REGISTRY.createDefault(typeId);
            if (holder == null) return;

            readInto(holder, blueprint);
            components.put(name, holder);
        });
    }

    /// The runtime component records are their own blueprints, so a holder's `readFrom` accepts them
    /// directly. The cast recovers what the wildcard erased.
    @SuppressWarnings("unchecked")
    private static <T> void readInto(AbstractDataHolder<T> holder, ComponentBlueprint<?, ?> blueprint) {
        holder.readFrom((T) blueprint);
    }

    @Override
    public BoneSocket makeObject() {
        return new BoneSocket(
            localTransform.makeObject(), boneName, ComponentAttachableHolder.lowerComponents(components));
    }

    /// A [BoneSocket] is its own blueprint — it builds itself.
    @Override
    public Class<? extends PatchComponent> generateEventually() {
        return BoneSocket.class;
    }

    // ***************************************************************
    // Source export (File > Export > As Source Code)
    // ***************************************************************

    @Override
    public String sourceTemplate() {
        return "components/bone_socket.ftl";
    }

    /// Tokens for `components/bone_socket.ftl`: the bone name, the transform flattened into its ten
    /// scalars, and the attached components as name → constructor expression rows.
    @Override
    public Map<String, Object> getDTO() {
        Transform transform = localTransform.makeObject();
        Quaterniond rotation = transform.rot();

        Map<String, Object> dto = new HashMap<>();
        dto.put("bone_name", boneName);
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
        dto.put("components", ComponentDataHolder.sourcesOf(components));

        return dto;
    }
}
