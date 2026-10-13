package com.yesman.akytheralabs.akythera.entitypatch.component;

import com.yesman.akythera.api.world.patch.entity.LivingEntityPatch;
import com.yesman.akythera.compat.CompatibleMod;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.platform.ModPlatformProvider;
import com.yesman.akytheralabs.akythera.collider.ColliderComponentHolder;
import com.yesman.akytheralabs.akythera.entitypatch.EntityPatchBlueprintHolder;
import com.yesman.akytheralabs.dataholder.ComponentHolderRegistry;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/// The entity-patch [ComponentHolderRegistry] — the editor holders for the component types of
/// [AkytheraRegistries.Frozen#ENTITY_PATCH_COMPONENT_TYPES]. Register through [#REGISTRY] directly;
/// compat plugins do so during client init.
///
/// A registered type is not automatically offerable at the root of a patch; only holders implementing
/// [RootAttachableComponent] are. `akythera:item_in_slot` is the standing example — an entity-patch
/// component reachable only by attaching it under a [TransformSocketHolder].
public final class EntityPatchComponentHolders {
    private EntityPatchComponentHolders() {}

    public static final Identifier ARMATURE = Akythera.identifier("armature");
    public static final Identifier ENTITY_MOVEMENT = Akythera.identifier("entity_movement");
    public static final Identifier ABILITY_SYSTEM = Akythera.identifier("ability_system");
    public static final Identifier TRANSFORM_SOCKET = Akythera.identifier("transform_socket");
    public static final Identifier COLLIDER = Akythera.identifier("collider");
    public static final Identifier ITEM_IN_SLOT = Akythera.identifier("item_in_slot");
    public static final Identifier SKINNED_ARMOR = Akythera.identifier("skinned_armor");
    public static final Identifier MESH = Akythera.identifier("mesh");
    public static final Identifier PAL_POSE_BINDER = Akythera.identifier("pal_pose_binder");

    public static final ComponentHolderRegistry REGISTRY =
        new ComponentHolderRegistry(AkytheraRegistries.Frozen.ENTITY_PATCH_COMPONENT_TYPES);

    static {
        REGISTRY.register(ARMATURE, ArmatureComponentHolder.class,
            _ -> ArmatureComponentHolder.createDefault(), ArmatureComponentHolder.EDITOR_CODEC);
        REGISTRY.register(ENTITY_MOVEMENT, EntityMovementComponentHolder.class,
            _ -> EntityMovementComponentHolder.createDefault(), EntityMovementComponentHolder.EDITOR_CODEC);
        REGISTRY.register(ABILITY_SYSTEM, AbilitySystemComponentHolder.class,
            _ -> AbilitySystemComponentHolder.createDefault(), AbilitySystemComponentHolder.EDITOR_CODEC,
            (host, _) -> host instanceof EntityPatchBlueprintHolder blueprint
                && LivingEntityPatch.class.isAssignableFrom(blueprint.ownerType())
                ? null
                : Component.translatable(LangKeys.GUI_ENTITY_PATCH_SCREEN_MENU_REFUSED_NOT_LIVING));
        REGISTRY.register(TRANSFORM_SOCKET, TransformSocketHolder.class,
            _ -> TransformSocketHolder.createDefault(() -> REGISTRY),
            TransformSocketHolder.editorCodec(() -> REGISTRY));
        // A collider on an entity is placed by the bone its socket rides; the patch root places
        // nothing, so it takes none.
        REGISTRY.register(COLLIDER, ColliderComponentHolder.class,
            _ -> ColliderComponentHolder.createDefault(), ColliderComponentHolder.EDITOR_CODEC,
            (host, _) -> host instanceof TransformSocketHolder
                ? null
                : Component.translatable(LangKeys.GUI_ENTITY_PATCH_SCREEN_MENU_REFUSED_COLLIDER_HOST));
        REGISTRY.register(ITEM_IN_SLOT, ItemInSlotRendererHolder.class,
            _ -> ItemInSlotRendererHolder.createDefault(), ItemInSlotRendererHolder.EDITOR_CODEC);
        REGISTRY.register(SKINNED_ARMOR, SkinnedArmorRendererHolder.class,
            _ -> SkinnedArmorRendererHolder.createDefault(), SkinnedArmorRendererHolder.EDITOR_CODEC);
        REGISTRY.register(MESH, MeshComponentHolder.class,
            _ -> MeshComponentHolder.createDefault(), MeshComponentHolder.EDITOR_CODEC);
        // The vanilla model is posed by Player Animation Library, so without that mod the component
        // cannot be built at all.
        REGISTRY.register(PAL_POSE_BINDER, PALPoseBinderHolder.class,
            _ -> PALPoseBinderHolder.createDefault(), PALPoseBinderHolder.EDITOR_CODEC,
            (_, _) -> ModPlatformProvider.get().isModLoaded(CompatibleMod.PLAYER_ANIMATION_LIBRARY.getModId())
                ? null
                : Component.translatable(LangKeys.GUI_ENTITY_PATCH_SCREEN_MENU_REFUSED_NO_PAL));
    }
}
