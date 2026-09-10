package com.yesman.akytheralabs.akythera.entitypatch.component;

import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.akythera.collider.ColliderComponentHolder;
import com.yesman.akytheralabs.dataholder.ComponentHolderRegistry;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/// The entity-patch [ComponentHolderRegistry] — the editor holders for the component types of
/// [AkytheraRegistries.Frozen#ENTITY_PATCH_COMPONENT_TYPES]. Register through [#REGISTRY] directly;
/// compat plugins do so during client init.
///
/// A registered type is not automatically offerable at the root of a patch; only holders implementing
/// [RootAttachableComponent] are. `akythera:bone_socket` is the standing example — an entity-patch
/// component reachable only by attaching it under an [ArmatureComponentHolder].
public final class EntityPatchComponentHolders {
    private EntityPatchComponentHolders() {}

    public static final Identifier ARMATURE = Akythera.identifier("armature");
    public static final Identifier BONE_SOCKET = Akythera.identifier("bone_socket");
    public static final Identifier COLLIDER = Akythera.identifier("collider");
    public static final Identifier ENTITY_MODEL = Akythera.identifier("entity_model");
    public static final Identifier EQUIPMENT = Akythera.identifier("equipment");
    public static final Identifier MESH = Akythera.identifier("mesh");

    public static final ComponentHolderRegistry REGISTRY =
        new ComponentHolderRegistry(AkytheraRegistries.Frozen.ENTITY_PATCH_COMPONENT_TYPES);

    static {
        REGISTRY.register(ARMATURE, ArmatureComponentHolder.class,
            _ -> ArmatureComponentHolder.createDefault(), ArmatureComponentHolder.EDITOR_CODEC);
        REGISTRY.register(BONE_SOCKET, BoneSocketHolder.class,
            _ -> BoneSocketHolder.createDefault(), BoneSocketHolder.EDITOR_CODEC);
        // A collider on an entity is placed by the bone its socket rides; the patch root places
        // nothing, so it takes none.
        REGISTRY.register(COLLIDER, ColliderComponentHolder.class,
            _ -> ColliderComponentHolder.createDefault(), ColliderComponentHolder.EDITOR_CODEC,
            host -> host instanceof BoneSocketHolder
                ? null
                : Component.translatable(LangKeys.GUI_ENTITY_PATCH_SCREEN_MENU_REFUSED_COLLIDER_HOST));
        REGISTRY.register(ENTITY_MODEL, EntityModelComponentHolder.class,
            _ -> EntityModelComponentHolder.createDefault(), EntityModelComponentHolder.EDITOR_CODEC);
        REGISTRY.register(EQUIPMENT, EquipmentComponentHolder.class,
            _ -> EquipmentComponentHolder.createDefault(), EquipmentComponentHolder.EDITOR_CODEC);
        REGISTRY.register(MESH, MeshComponentHolder.class,
            _ -> MeshComponentHolder.createDefault(), MeshComponentHolder.EDITOR_CODEC);
    }
}
