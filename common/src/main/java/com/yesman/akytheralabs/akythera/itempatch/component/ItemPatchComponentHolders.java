package com.yesman.akytheralabs.akythera.itempatch.component;

import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.akythera.collider.ColliderComponentHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.EntityPatchComponentHolders;
import com.yesman.akytheralabs.dataholder.ComponentHolderRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/// The item-patch [ComponentHolderRegistry] — the item-patch twin of
/// [EntityPatchComponentHolders].
public final class ItemPatchComponentHolders {
    private ItemPatchComponentHolders() {}

    public static final Identifier ATTRIBUTE_MODIFIERS = Akythera.identifier("attribute_modifiers");
    public static final Identifier COLLIDER = Akythera.identifier("collider");
    public static final Identifier REPLACE_ANIMATIONS = Akythera.identifier("replace_animations");
    public static final Identifier WEAPON_STANCE_VARIATION_ADAPTER = Akythera.identifier("weapon_stance_variation_adapter");

    public static final ComponentHolderRegistry REGISTRY = new ComponentHolderRegistry(AkytheraRegistries.Frozen.ITEM_PATCH_COMPONENT_TYPES);

    static {
        REGISTRY.register(ATTRIBUTE_MODIFIERS, AttributeModifiersComponentHolder.class,
            _ -> AttributeModifiersComponentHolder.createDefault(), AttributeModifiersComponentHolder.EDITOR_CODEC);
        REGISTRY.register(COLLIDER, ColliderComponentHolder.class,
            _ -> ColliderComponentHolder.createDefault(), ColliderComponentHolder.EDITOR_CODEC);
        REGISTRY.register(REPLACE_ANIMATIONS, ReplaceAnimationsHolder.class,
            _ -> ReplaceAnimationsHolder.createDefault(), ReplaceAnimationsHolder.EDITOR_CODEC);
        REGISTRY.register(WEAPON_STANCE_VARIATION_ADAPTER, WeaponStanceVariationAdapterHolder.class,
            WeaponStanceVariationAdapterHolder::createDefault, WeaponStanceVariationAdapterHolder.EDITOR_CODEC,
            WeaponStanceVariationAdapterHolder::attachRefusal);
    }
}
