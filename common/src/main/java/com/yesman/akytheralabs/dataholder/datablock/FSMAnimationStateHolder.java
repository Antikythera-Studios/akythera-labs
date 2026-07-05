package com.yesman.akytheralabs.dataholder.datablock;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.yesman.akythera.core.animation.driver.FSMAnimationState;
import net.minecraft.resources.Identifier;

/// [DataBlockHolder] for the `FSMAnimationState` editable data block. The made object is a
/// standalone `FSMAnimationState` whose `identifier()` reads the holder's live id (so it follows a
/// rekey) and whose `universalOrdinal()` is `-1` (a labs preview object is never network-synced;
/// the real ordinal is assigned at runtime when the exported `{}` json is loaded by the manager).
public final class FSMAnimationStateHolder extends DataBlockHolder<FSMAnimationState> {
    /// Property-less: encodes to an empty object `{}`, decodes to a fresh holder.
    public static final Codec<FSMAnimationStateHolder> CODEC = MapCodec.unit(FSMAnimationStateHolder::new).codec();

    @Override
    protected FSMAnimationState instantiate() {
        return new FSMAnimationState() {
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