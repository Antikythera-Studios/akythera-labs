package com.yesman.akytheralabs.akythera.particle;

import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;

/// A particle atlas sprite, named by its id: `<namespace>:<path>` for `assets/<namespace>/textures/particle/<path>.png`.
public record ParticleSprite(Identifier id) {
    public static final Codec<ParticleSprite> CODEC = Identifier.CODEC.xmap(ParticleSprite::new, ParticleSprite::id);
}
