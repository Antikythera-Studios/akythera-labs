package com.yesman.akytheralabs.akythera.dataholder.datablock;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.yesman.akythera.core.animation.AnimationLayerSignature;
import com.yesman.akytheralabs.dataholder.DataBlockHolder;
import net.minecraft.resources.Identifier;

/// [DataBlockHolder] for the `AnimationLayerSignature` editable data block. See
/// [FSMAnimationStateHolder] — same shape, different enum type.
public final class AnimationLayerSignatureHolder extends DataBlockHolder<AnimationLayerSignature> {
    public static final Codec<AnimationLayerSignatureHolder> CODEC = MapCodec.unit(AnimationLayerSignatureHolder::new).codec();

    @Override
    protected AnimationLayerSignature instantiate() {
        return new AnimationLayerSignature() {
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