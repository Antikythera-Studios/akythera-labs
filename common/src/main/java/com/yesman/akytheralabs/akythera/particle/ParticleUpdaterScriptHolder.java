package com.yesman.akytheralabs.akythera.particle;

import com.mojang.serialization.Codec;
import com.yesman.akythera.client.api.particle.module.ParticleUpdateModule;
import com.yesman.akythera.core.blueprint.particle.ParticleUpdaterScriptBlueprint;
import com.yesman.akytheralabs.akythera.blueprint.BlueprintScriptData;

/// Modifiable data holder for [ParticleUpdaterScriptBlueprint].
public final class ParticleUpdaterScriptHolder extends ParticleModuleScriptHolder<ParticleUpdaterScriptBlueprint> {
    public static final Codec<ParticleUpdaterScriptHolder> EDITOR_CODEC = BlueprintScriptData.MAP_CODEC.codec().xmap(
        script -> {
            ParticleUpdaterScriptHolder holder = new ParticleUpdaterScriptHolder();

            holder.script = script;

            return holder;
        },
        ParticleModuleScriptHolder::getScript
    );

    public ParticleUpdaterScriptHolder() {
        super(ParticleUpdateModule.class);
    }

    @Override
    public void readFrom(final ParticleUpdaterScriptBlueprint object) {
        readBody(object.body());
    }

    @Override
    public ParticleUpdaterScriptBlueprint makeObject() {
        return new ParticleUpdaterScriptBlueprint(compileBody());
    }
}
