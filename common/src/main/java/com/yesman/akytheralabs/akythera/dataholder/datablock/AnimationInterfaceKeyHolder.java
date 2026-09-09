package com.yesman.akytheralabs.akythera.dataholder.datablock;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.animation.animator.AnimationInterfaceKey;
import com.yesman.akytheralabs.dataholder.DataBlockHolder;
import net.minecraft.resources.Identifier;

/// [DataBlockHolder] for the `AnimationInterfaceKey` editable data block. See
/// [FSMAnimationStateHolder] — same shape.
public final class AnimationInterfaceKeyHolder extends DataBlockHolder<AnimationInterfaceKey> {
    /// Property-less — encodes to an empty object `{}`, decodes to a fresh holder.
    public static final Codec<AnimationInterfaceKeyHolder> CODEC =
        MapCodec.unit(AnimationInterfaceKeyHolder::new).codec();

    @Override
    protected AnimationInterfaceKey instantiate() {
        return new AnimationInterfaceKey() {
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
