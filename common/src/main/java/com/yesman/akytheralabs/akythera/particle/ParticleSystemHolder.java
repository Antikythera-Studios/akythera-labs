package com.yesman.akytheralabs.akythera.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.core.blueprint.particle.ParticleEmitterBlueprint;
import com.yesman.akythera.core.blueprint.particle.ParticleSystemBlueprint;
import com.yesman.akytheralabs.akythera.blueprint.BlueprintScriptData;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.FreemarkerExporter;
import com.yesman.akytheralabs.NodeTreeSourceRenderer;
import com.yesman.akytheralabs.ParticleSourceWriter;
import com.yesman.akytheralabs.dataholder.AbstractDataHolder;
import com.yesman.akytheralabs.dataholder.DTOProvider;
import net.minecraft.resources.ResourceKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/// Modifiable data holder for a [ParticleSystemBlueprint]: its emitters and its parameters. It also keeps
/// when the editor's preview ends, which is no part of the system.
public final class ParticleSystemHolder extends AbstractDataHolder<ParticleSystemBlueprint> implements DTOProvider {
    private static final String NEW_EMITTER_NAME = "emitter";

    /// How deep the lists of [#getDTO] sit in the generated provider, in indents.
    private static final int DTO_INDENT = 4;

    /// The earliest fixed end of the editor's preview, in seconds.
    public static final float MIN_PREVIEW_END = 0.05F;
    private static final float DEFAULT_PREVIEW_END = 1.0F;

    public static final Codec<ParticleSystemHolder> EDITOR_CODEC = RecordCodecBuilder.create(
        instance ->
            instance.group(
                ParticleEmitterHolder.CODEC.listOf().optionalFieldOf("emitters", List.of()).forGetter(holder -> holder.emitters),
                BlueprintScriptData.Variable.CODEC.listOf().optionalFieldOf("variables", List.of()).forGetter(holder -> holder.variables),
                Codec.FLOAT.optionalFieldOf("preview_end").forGetter(holder -> Optional.of(holder.previewEnd))
            ).apply(instance, (emitters, variables, previewEnd) -> {
                ParticleSystemHolder holder = new ParticleSystemHolder();
                holder.emitters.addAll(emitters);
                holder.variables.addAll(variables);
                holder.previewEnd = previewEnd.orElse(holder.previewEnd);
                if (previewEnd.isEmpty() && !emitters.isEmpty()) holder.setPreviewEnd(holder.getLength());

                return holder;
            })
    );

    /// In the order they tick.
    private final List<ParticleEmitterHolder> emitters = new ArrayList<>();

    /// The parameters a play sets.
    private final List<BlueprintScriptData.Variable> variables = new ArrayList<>();

    /// When the editor's preview ends, in seconds since it started. A system with emitters that is read without one
    /// gets [#getLength].
    private float previewEnd = DEFAULT_PREVIEW_END;

    @Override
    public void readFrom(final ParticleSystemBlueprint object) {
        emitters.clear();
        variables.clear();

        for (ParticleEmitterBlueprint emitter : object.emitters()) {
            ParticleEmitterHolder holder = new ParticleEmitterHolder(emitter.name());
            holder.readFrom(emitter);
            emitters.add(holder);
        }

        object.variables().stream().map(BlueprintScriptData.Variable::of).forEach(variables::add);
        if (!emitters.isEmpty()) setPreviewEnd(getLength());
    }

    /// @throws IllegalStateException when an emitter names no particle, or the system fails [ParticleSystemBlueprint#validate]
    @Override
    public ParticleSystemBlueprint makeObject() {
        ParticleSystemBlueprint system = new ParticleSystemBlueprint(
            emitters.stream().map(ParticleEmitterHolder::makeObject).toList(),
            variables.stream().map(BlueprintScriptData.Variable::toInfo).toList()
        );

        return system.validate().getOrThrow(IllegalStateException::new);
    }

    /// The `emitters` and `variables` lists of the system's constructor, as source. A module node graph the open
    /// project holds is named by the class [FreemarkerExporter#particleModuleClass] gives it.
    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getDTO() {
        AkytheraProject project = AkytheraProject.getCurrentProject();
        ParticleSystemBlueprint system = makeObject();

        return Map.of(
            "emitters", ParticleSourceWriter.listOf(
                system.emitters().stream()
                    .map(emitter -> ParticleSourceWriter.emitter(
                        emitter, module -> project.getDataHolder((ResourceKey<Object>)module).isPresent() ? FreemarkerExporter.particleModuleClass(module) : null, DTO_INDENT + 1
                    ))
                    .toList(),
                DTO_INDENT
            ),
            "variables", ParticleSourceWriter.listOf(
                system.variables().stream().map(variable -> NodeTreeSourceRenderer.variableInfo(variable, DTO_INDENT + 1)).toList(), DTO_INDENT
            )
        );
    }

    public List<ParticleEmitterHolder> getEmitters() {
        return emitters;
    }

    public List<BlueprintScriptData.Variable> getVariables() {
        return variables;
    }

    public void setVariables(final List<BlueprintScriptData.Variable> variables) {
        this.variables.clear();
        this.variables.addAll(variables);
    }

    /// A new emitter with a name no other emitter has.
    public ParticleEmitterHolder newEmitter() {
        Set<String> taken = emitters.stream().map(ParticleEmitterHolder::getName).collect(Collectors.toSet());
        String name = NEW_EMITTER_NAME;

        for (int suffix = 2; taken.contains(name); suffix++) {
            name = NEW_EMITTER_NAME + "_" + suffix;
        }

        return new ParticleEmitterHolder(name);
    }

    /// When the play ends, in seconds since it started: the latest time span's end, or an instant emitter's start.
    public float getLength() {
        return (float)emitters.stream().mapToDouble(emitter -> emitter.getStart() + emitter.getSpan()).max().orElse(0.0D);
    }

    /// When the editor's preview ends, in seconds since it started, whatever the emitters span.
    public float getPreviewEnd() {
        return previewEnd;
    }

    /// Ends the editor's preview at `seconds` since it started, at least [#MIN_PREVIEW_END].
    public void setPreviewEnd(final float seconds) {
        previewEnd = Math.max(seconds, MIN_PREVIEW_END);
    }

    /// Whether the play never ends: one of the emitters loops.
    public boolean isEndless() {
        return emitters.stream().anyMatch(ParticleEmitterHolder::loops);
    }
}
