package com.yesman.akytheralabs.akythera.entitypatch.component;

import com.yesman.akythera.core.Akythera;
import com.yesman.akytheralabs.dataholder.ComponentHolderRegistry;
import net.minecraft.resources.Identifier;

/// The entity-patch [ComponentHolderRegistry] — the editor holders for the component types tagged
/// [com.yesman.akythera.core.blueprint.ComponentBlueprint#ENTITY_PATCH_COMPONENT_TAG]. Register
/// through [#REGISTRY] directly; compat plugins do so during client init.
///
/// A tagged type is not automatically offerable at the root of a patch; only holders implementing
/// [RootAttachableComponent] are. `akythera:bone_socket` is the standing example — an entity-patch
/// component reachable only by attaching it under an [ArmatureComponentHolder].
public final class EntityPatchComponentHolders {
    private EntityPatchComponentHolders() {}

    public static final Identifier ARMATURE = Akythera.identifier("armature");
    public static final Identifier BONE_SOCKET = Akythera.identifier("bone_socket");
    public static final Identifier ENTITY_MODEL = Akythera.identifier("entity_model");
    public static final Identifier EQUIPMENT = Akythera.identifier("equipment");
    public static final Identifier MESH = Akythera.identifier("mesh");

    public static final ComponentHolderRegistry REGISTRY = new ComponentHolderRegistry();

    static {
        REGISTRY.register(ARMATURE, ArmatureComponentHolder.class,
            ArmatureComponentHolder::createDefault, ArmatureComponentHolder.EDITOR_CODEC);
        REGISTRY.register(BONE_SOCKET, BoneSocketHolder.class,
            BoneSocketHolder::createDefault, BoneSocketHolder.EDITOR_CODEC);
        REGISTRY.register(ENTITY_MODEL, EntityModelComponentHolder.class,
            EntityModelComponentHolder::createDefault, EntityModelComponentHolder.EDITOR_CODEC);
        REGISTRY.register(EQUIPMENT, EquipmentComponentHolder.class,
            EquipmentComponentHolder::createDefault, EquipmentComponentHolder.EDITOR_CODEC);
        REGISTRY.register(MESH, MeshComponentHolder.class,
            MeshComponentHolder::createDefault, MeshComponentHolder.EDITOR_CODEC);
    }
}
