package com.yesman.akytheralabs.akythera.dataholder.datablock;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.world.behavior.MobBehaviorState;
import com.yesman.akytheralabs.dataholder.DataBlockHolder;
import net.minecraft.resources.Identifier;

/// [DataBlockHolder] for the `MobBehaviorState` editable data block, made like [FSMAnimationStateHolder]'s.
public final class MobBehaviorStateHolder extends DataBlockHolder<MobBehaviorState> {
    /// Property-less — encodes to an empty object `{}`, decodes to a fresh holder.
    public static final Codec<MobBehaviorStateHolder> CODEC = MapCodec.unit(MobBehaviorStateHolder::new).codec();

    @Override
    protected MobBehaviorState instantiate() {
        return new MobBehaviorState() {
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
