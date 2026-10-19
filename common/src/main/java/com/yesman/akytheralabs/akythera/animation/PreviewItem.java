package com.yesman.akytheralabs.akythera.animation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.impl.EquipmentSlotBinder;
import com.yesman.akytheralabs.akythera.entitypatch.component.TransformSocketHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

/// An item an animation editor's preview holds under a socket, its item patch's transform components attached beside
/// it as an [EquipmentSlotBinder] attaches them. Editor-only.
///
/// @param socket the path of the [TransformSocketHolder] the item is held under, in the preview entity type's patch
public record PreviewItem(String socket, Item item) {
    public static final Codec<PreviewItem> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            Codec.STRING.fieldOf("socket").forGetter(PreviewItem::socket),
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(PreviewItem::item)
        ).apply(instance, PreviewItem::new)
    );
}
