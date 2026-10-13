package com.yesman.akytheralabs.akythera.particle;

import com.yesman.akythera.client.api.particle.AkytheraMeshParticle;
import com.yesman.akythera.client.api.particle.AkytheraParticle;
import com.yesman.akythera.client.api.particle.AkytheraSpriteParticle;
import com.yesman.akythera.client.api.particle.AkytheraTrailParticle;
import com.yesman.akythera.client.api.particle.ParticleHost;
import com.yesman.akythera.core.blueprint.particle.ParticleBlueprint;
import com.yesman.akythera.core.blueprint.particle.SpriteParticleBlueprint;
import com.yesman.akythera.core.blueprint.particle.TrailParticleBlueprint;
import com.yesman.akytheralabs.AkytheraProject;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

/// The particle an emitter spawns: a particle piece, a vanilla particle type spawned as a sprite particle, or none
/// picked yet.
public sealed interface ParticleRef {
    ParticleRef NONE = new None();

    /// What the particle select box shows.
    String displayLabel();

    /// The class of the particles spawned, which decides the modules that apply. Null when it can't be told.
    @Nullable Class<? extends AkytheraParticle> particleClass();

    record None() implements ParticleRef {
        @Override
        public String displayLabel() {
            return "<none>";
        }

        @Override
        public @Nullable Class<? extends AkytheraParticle> particleClass() {
            return null;
        }
    }

    /// A particle piece of the open project or the loaded registry.
    record Piece(ResourceKey<ParticleBlueprint> key) implements ParticleRef {
        @Override
        public String displayLabel() {
            return key.identifier().toString();
        }

        @Override
        public @Nullable Class<? extends AkytheraParticle> particleClass() {
            ParticleBlueprintHolder projectPiece = AkytheraProject.getCurrentProject().<ParticleBlueprint, ParticleBlueprintHolder>getDataHolder(key).orElse(null);

            if (projectPiece != null) {
                return switch (projectPiece.getType()) {
                    case SPRITE -> AkytheraSpriteParticle.class;
                    case STATIC_MESH, SKELETAL_MESH -> AkytheraMeshParticle.class;
                    case TRAIL -> AkytheraTrailParticle.class;
                };
            }

            return switch (ParticleHost.LEVEL.piece(key)) {
                case null -> null;
                case SpriteParticleBlueprint _ -> AkytheraSpriteParticle.class;
                case TrailParticleBlueprint _ -> AkytheraTrailParticle.class;
                default -> AkytheraMeshParticle.class;
            };
        }
    }

    /// A vanilla particle type that has sprites.
    record Vanilla(Identifier id) implements ParticleRef {
        @Override
        public String displayLabel() {
            return id.toString();
        }

        @Override
        public Class<? extends AkytheraParticle> particleClass() {
            return AkytheraSpriteParticle.class;
        }
    }
}
