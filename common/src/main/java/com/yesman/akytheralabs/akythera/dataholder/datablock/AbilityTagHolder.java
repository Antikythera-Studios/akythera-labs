package com.yesman.akytheralabs.akythera.dataholder.datablock;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.world.ability.AbilityTag;
import com.yesman.akytheralabs.dataholder.DataBlockHolder;
import net.minecraft.resources.Identifier;

/// [DataBlockHolder] for the `AbilityTag` editable data block. The made object reads its path live off
/// this holder's id, so it follows a rekey. The namespace is ignored, since a tag's identity is its path.
public final class AbilityTagHolder extends DataBlockHolder<AbilityTag> {
    /// Property-less — encodes to an empty object `{}`, decodes to a fresh holder.
    public static final Codec<AbilityTagHolder> CODEC = MapCodec.unit(AbilityTagHolder::new).codec();

    @Override
    protected AbilityTag instantiate() {
        return new AbilityTag() {
            @Override
            public int universalOrdinal() {
                return -1;
            }

            @Override
            public String path() {
                return currentId().getPath();
            }

            @Override
            public Identifier identifier() {
                return currentId();
            }

            @Override
            public String toString() {
                return path();
            }
        };
    }
}
