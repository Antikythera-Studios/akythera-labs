package com.yesman.akytheralabs.akythera.entitypatch.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.component.TransformInheritPolicy;
import com.yesman.akythera.core.asset.armature.TransformSocket;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akytheralabs.akythera.collider.ColliderComponentHolder;
import com.yesman.akytheralabs.dataholder.AbstractDataHolder;
import com.yesman.akytheralabs.dataholder.ComponentHolderRegistry;
import com.yesman.akytheralabs.dataholder.TransformHolder;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.function.Supplier;

/// Editor holder for an `akythera:transform_socket` ([TransformSocket]) — a named point other things
/// are bound to, at a fixed offset from whatever places it.
///
/// Under an [ArmatureComponentHolder] it rides one bone of that armature. At a patch root — an item
/// patch's, or an entity patch's — it rides no bone and is its offset alone, and [#boneName] stays
/// blank.
///
/// It is itself a [ComponentAttachableHolder]: whatever hangs off it is placed by it. See
/// [#attachableTypes].
public final class TransformSocketHolder extends ComponentDataHolder<TransformSocket>
    implements ComponentAttachableHolder, RootAttachableComponent, TransformComponentHolder
{
    /// Editor persistence for the sockets of one patch kind. The transform rides
    /// [TransformHolder#CODEC], which is [Transform#CODEC] mapped through, so a socket's editor JSON
    /// and its datapack JSON carry the same transform block.
    ///
    /// The transform decodes through an [Optional]. A codec's default value is a single instance
    /// shared by every decode that falls back to it, and these holders are mutable, so a missing
    /// `transform` would wire every socket in the project to the same one.
    ///
    /// `registry` is the patch kind's own [ComponentHolderRegistry]; the attached components ride
    /// [ComponentAttachableHolder#componentsField], which dispatches back through it.
    /// [Codec#lazyInitialized] breaks the cycle, that registry's static initializer being what
    /// registers this very codec.
    public static MapCodec<TransformSocketHolder> editorCodec(Supplier<ComponentHolderRegistry> registry) {
        return RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                Codec.STRING.optionalFieldOf("bone_name", "").forGetter(TransformSocketHolder::getBoneName),
                TransformHolder.CODEC.optionalFieldOf("transform")
                    .forGetter(h -> Optional.of(h.localTransform)),
                TransformInheritPolicy.CODEC.optionalFieldOf("transform_inherit_policy", TransformInheritPolicy.LOC_ROT_SCA)
                    .forGetter(TransformComponentHolder::getTransformInheritPolicy),
                ComponentAttachableHolder.componentsField(registry)
                    .forGetter(ComponentAttachableHolder::componentsOf)
            ).apply(instance, (boneName, transform, transformInheritPolicy, components) -> {
                TransformSocketHolder holder = new TransformSocketHolder(
                    registry, boneName, transform.orElseGet(TransformHolder::identity), transformInheritPolicy);
                ComponentAttachableHolder.applyComponents(holder, components);
                return holder;
            })
        );
    }

    /// The patch kind this socket belongs to — what its children decode, attach and re-import through.
    private final Supplier<ComponentHolderRegistry> registry;

    /// The armature bone this socket rides, picked from [#boneCandidates] — the bones of the armature
    /// its parent component points at. Blank where it rides none.
    @EditorEditable(
        label = LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_BONE_NAME,
        order = 0,
        candidates = "boneCandidates",
        emptyCandidates = LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_BONE_NAME_NO_ARMATURE
    )
    private String boneName;

    /// The elements of the bound bone, or of whatever places this socket, the socket takes on.
    @EditorEditable(label = LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_TRANSFORM_INHERIT_POLICY, order = 1)
    private TransformInheritPolicy transformInheritPolicy;

    /// Offset from the bound bone, or from whatever places this socket. A nested editor object,
    /// rendered as location / rotation / scale rows under one "Local Transform" heading.
    @EditorEditable(label = LangKeys.GUI_ENTITY_PATCH_SCREEN_SECTION_LOCAL_TRANSFORM, order = 2)
    private TransformHolder localTransform;

    /// The attached component holders by name — the socket's [ComponentAttachableHolder] children,
    /// edited as structure in the Components tree rather than as Details rows.
    private final SequencedMap<String, ComponentDataHolder<?>> components = ComponentAttachableHolder.newComponentMap();

    public TransformSocketHolder(
        Supplier<ComponentHolderRegistry> registry,
        String boneName,
        TransformHolder localTransform,
        TransformInheritPolicy transformInheritPolicy
    ) {
        this.registry = registry;
        this.boneName = boneName;
        this.localTransform = localTransform;
        this.transformInheritPolicy = transformInheritPolicy;
    }

    /// A freshly attached socket: a blank `boneName`, sitting exactly where its host places it.
    public static TransformSocketHolder createDefault(Supplier<ComponentHolderRegistry> registry) {
        return new TransformSocketHolder(registry, "", TransformHolder.identity(), TransformInheritPolicy.LOC_ROT_SCA);
    }

    /// [#boneName]'s [EditorEditable#candidates] source: the bones of the armature this socket hangs
    /// off, none where it hangs off anything else.
    private List<String> boneCandidates(@Nullable Object parent) {
        return parent instanceof ArmatureComponentHolder armature ? armature.boneNames() : List.of();
    }

    public String getBoneName() {
        return boneName;
    }

    @Override
    public TransformHolder getLocalTransform() {
        return localTransform;
    }

    @Override
    public TransformInheritPolicy getTransformInheritPolicy() {
        return transformInheritPolicy;
    }

    public void setTransformInheritPolicy(TransformInheritPolicy transformInheritPolicy) {
        this.transformInheritPolicy = transformInheritPolicy;
    }

    public void setBoneName(String boneName) {
        this.boneName = boneName;
    }

    public void setLocalTransform(TransformHolder localTransform) {
        this.localTransform = localTransform;
    }

    /// What a socket hosts: an [EquipmentSlotBinderHolder] drawing one equipment slot's item, a
    /// [SkinnedArmorRendererHolder] drawing one armor slot's piece, a [MeshComponentHolder] drawing a mesh,
    /// and a [ColliderComponentHolder]. Whichever of them the patch kind does not register is simply never
    /// offered.
    @Override
    public List<Class<?>> attachableTypes() {
        return List.of(
            EquipmentSlotBinderHolder.class, SkinnedArmorRendererHolder.class, MeshComponentHolder.class, ColliderComponentHolder.class
        );
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
    public void readFrom(TransformSocket object) {
        boneName = object.getBoneName();
        localTransform.readFrom(object.localTransform());
        transformInheritPolicy = object.transformInheritPolicy();

        ComponentAttachableHolder.readComponents(this, registry.get(), object.getChildBlueprints());
    }

    @Override
    public TransformSocket makeObject() {
        return new TransformSocket(
            localTransform.makeObject(), transformInheritPolicy, boneName, ComponentAttachableHolder.lowerComponents(components), isEnabled());
    }

    /// A [TransformSocket] is its own blueprint — it builds itself.
    @Override
    public Class<? extends PatchComponent> generateEventually() {
        return TransformSocket.class;
    }

    // ***************************************************************
    // Source export (File > Export > As Source Code)
    // ***************************************************************

    @Override
    public String sourceTemplate() {
        return "components/transform_socket.ftl";
    }

    /// Tokens for `components/transform_socket.ftl`: the bone name, the transform's [TransformHolder#sourceTokens]
    /// and the inherit policy's constant name.
    @Override
    public Map<String, Object> getDTO() {
        Map<String, Object> dto = new HashMap<>();
        dto.put("bone_name", boneName);
        dto.put("transform", localTransform.sourceTokens());
        dto.put("transform_inherit_policy", transformInheritPolicy.name());

        return dto;
    }
}
