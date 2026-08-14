package com.yesman.akytheralabs.akythera.itempatch.component;

import com.yesman.akythera.core.Akythera;
import com.yesman.akytheralabs.akythera.entitypatch.component.EntityPatchComponentHolders;
import com.yesman.akytheralabs.dataholder.ComponentHolderRegistry;
import net.minecraft.resources.Identifier;

/// The item-patch [ComponentHolderRegistry] — the item-patch twin of
/// [EntityPatchComponentHolders].
public final class ItemPatchComponentHolders {
    private ItemPatchComponentHolders() {}

    public static final Identifier ATTRIBUTE_MODIFIERS = Akythera.identifier("attribute_modifiers");
    public static final Identifier REPLACE_ANIMATIONS = Akythera.identifier("replace_animations");

    public static final ComponentHolderRegistry REGISTRY = new ComponentHolderRegistry();

    static {
        REGISTRY.register(ATTRIBUTE_MODIFIERS, PatchAttributeModifiersHolder.class,
            PatchAttributeModifiersHolder::createDefault, PatchAttributeModifiersHolder.EDITOR_CODEC);
        REGISTRY.register(REPLACE_ANIMATIONS, ReplaceAnimationsHolder.class,
            ReplaceAnimationsHolder::createDefault, ReplaceAnimationsHolder.EDITOR_CODEC);
    }
}
