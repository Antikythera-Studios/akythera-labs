package com.yesman.akytheralabs.akythera.dataholder.datablock;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.world.ability.AbilitySlotGroup;
import com.yesman.akytheralabs.dataholder.DataBlockHolder;
import net.minecraft.resources.Identifier;

/// [DataBlockHolder] for the `AbilitySlotGroup` editable data block. See [FSMAnimationStateHolder] —
/// same shape.
public final class AbilitySlotGroupHolder extends DataBlockHolder<AbilitySlotGroup> {
    /// Property-less — encodes to an empty object `{}`, decodes to a fresh holder.
    public static final Codec<AbilitySlotGroupHolder> CODEC = MapCodec.unit(AbilitySlotGroupHolder::new).codec();

    @Override
    protected AbilitySlotGroup instantiate() {
        return new AbilitySlotGroup() {
            @Override
            public int universalOrdinal() {
                return -1;
            }

            @Override
            public Identifier identifier() {
                return currentId();
            }

            @Override
            public String toString() {
                return currentId().toString();
            }
        };
    }
}
