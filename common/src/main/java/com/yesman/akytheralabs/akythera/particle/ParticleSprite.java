package com.yesman.akytheralabs.akythera.particle;

import com.mojang.serialization.Codec;
import com.yesman.akytheralabs.AkytheraProject;
import net.minecraft.resources.Identifier;

/// A particle atlas sprite, the texture `assets/<namespace>/textures/particle/<path>.png` named by its id
/// `<namespace>:<path>`.
public record ParticleSprite(AkytheraProject.EditorTextureAccessor texture) {
    public static final Codec<ParticleSprite> CODEC = Identifier.CODEC.xmap(ParticleSprite::of, ParticleSprite::id);

    public static ParticleSprite of(final Identifier id) {
        return new ParticleSprite(AkytheraProject.spriteOf(AkytheraProject.PARTICLE_SPRITES_DIRECTORY, id));
    }

    public Identifier id() {
        return texture.spriteId(AkytheraProject.PARTICLE_SPRITES_DIRECTORY);
    }
}
