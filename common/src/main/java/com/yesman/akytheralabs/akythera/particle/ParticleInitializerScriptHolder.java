package com.yesman.akytheralabs.akythera.particle;

import com.mojang.serialization.Codec;
import com.yesman.akythera.client.api.particle.module.ParticleInitializerModule;
import com.yesman.akythera.core.blueprint.particle.ParticleInitializerScriptBlueprint;
import com.yesman.akytheralabs.akythera.blueprint.BlueprintScriptData;

/// Modifiable data holder for [ParticleInitializerScriptBlueprint].
public final class ParticleInitializerScriptHolder extends ParticleModuleScriptHolder<ParticleInitializerScriptBlueprint> {
    public static final Codec<ParticleInitializerScriptHolder> EDITOR_CODEC = BlueprintScriptData.MAP_CODEC.codec().xmap(
        script -> {
            ParticleInitializerScriptHolder holder = new ParticleInitializerScriptHolder();

            holder.script = script;

            return holder;
        },
        ParticleModuleScriptHolder::getScript
    );

    public ParticleInitializerScriptHolder() {
        super(ParticleInitializerModule.class);
    }

    @Override
    public void readFrom(final ParticleInitializerScriptBlueprint object) {
        readBody(object.body());
    }

    @Override
    public ParticleInitializerScriptBlueprint makeObject() {
        return new ParticleInitializerScriptBlueprint(compileBody());
    }
}
