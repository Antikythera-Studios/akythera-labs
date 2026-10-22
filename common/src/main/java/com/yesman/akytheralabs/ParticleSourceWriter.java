package com.yesman.akytheralabs;

import com.yesman.akythera.api.asset.armature.ArmatureManager;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.core.blueprint.ArmatureComponentBlueprint;
import com.yesman.akythera.core.blueprint.particle.InstantSpawnRule;
import com.yesman.akythera.core.blueprint.particle.KeyframeTrack;
import com.yesman.akythera.core.blueprint.particle.ParticleBlueprint;
import com.yesman.akythera.core.blueprint.particle.ParticleEmitterBlueprint;
import com.yesman.akythera.core.blueprint.particle.ParticleInitializerBlueprint;
import com.yesman.akythera.core.blueprint.particle.ParticleProperties;
import com.yesman.akythera.core.blueprint.particle.ParticleUpdaterBlueprint;
import com.yesman.akythera.core.blueprint.particle.SkeletalMeshParticleBlueprint;
import com.yesman.akythera.core.blueprint.particle.SpawnRateRule;
import com.yesman.akythera.core.blueprint.particle.SpriteParticleBlueprint;
import com.yesman.akythera.core.blueprint.particle.StaticMeshParticleBlueprint;
import com.yesman.akythera.core.blueprint.particle.TrailParticleBlueprint;
import com.yesman.akythera.core.blueprint.particle.TrailSpawnRule;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/// Writes particle pieces, and the emitters of a particle system, as the constructor expressions their generated
/// data providers register.
@NullMarked
public final class ParticleSourceWriter {
    private ParticleSourceWriter() {}

    private static final String INDENT = "    ";

    /// `emitter` as its [ParticleEmitterBlueprint] constructor expression, one argument per line, closing at `indent`.
    ///
    /// @param moduleClasses the generated class of a module node graph, by its entry, or null for an entry that stays
    ///                      a data pack entry
    public static String emitter(
        ParticleEmitterBlueprint emitter, Function<ResourceKey<?>, @Nullable String> moduleClasses, int indent
    ) {
        return ctor("ParticleEmitterBlueprint", indent, List.of(
            ScriptNodeSourceCompiler.valueLiteral(emitter.name()),
            emitter.particle().map(
                piece -> "Either.left(" + BlueprintSourceWriter.registryKey(AkytheraRegistries.DataPack.PARTICLE_PIECE_KEY, piece.identifier().toString()) + ")",
                vanilla -> "Either.right(Identifier.parse(" + ScriptNodeSourceCompiler.valueLiteral(vanilla.toString()) + "))"
            ),
            SourceTemplateEngine.floatLiteral(emitter.start()),
            switch (emitter.spawn()) {
                case InstantSpawnRule instant -> "new InstantSpawnRule(" + instant.count() + ")";
                case SpawnRateRule rate -> "new SpawnRateRule(" + SourceTemplateEngine.floatLiteral(rate.rate()) + ", "
                    + SourceTemplateEngine.floatLiteral(rate.duration()) + ", " + rate.loop() + ")";
                case TrailSpawnRule trail -> "new TrailSpawnRule(" + SourceTemplateEngine.floatLiteral(trail.duration()) + ")";
                default -> throw new IllegalStateException("No source form for the spawn rule " + emitter.spawn().getClass().getSimpleName());
            },
            String.valueOf(emitter.hideParticlesOnFinish()),
            transform(emitter.pivot()),
            transform(emitter.transform()),
            track(emitter.pivotKeys(), indent + 1),
            track(emitter.transformKeys(), indent + 1),
            listOf(emitter.initializers().stream().map(initializer -> module(initializer, moduleClasses, indent + 2)).toList(), indent + 1),
            listOf(emitter.updaters().stream().map(updater -> module(updater, moduleClasses, indent + 2)).toList(), indent + 1)
        ));
    }

    /// `piece` as its constructor expression, one argument per line, closing at `indent`.
    ///
    /// @throws IllegalStateException for a piece class with no source form
    public static String piece(ParticleBlueprint piece, int indent) {
        return switch (piece) {
            case SpriteParticleBlueprint sprite -> ctor("SpriteParticleBlueprint", indent, List.of(
                properties(sprite.properties()),
                SourceTemplateEngine.floatLiteral(sprite.size()),
                ScriptNodeSourceCompiler.valueLiteral(sprite.color()),
                SourceTemplateEngine.floatLiteral(sprite.roll()),
                sprite.sprites().map(
                    ids -> "Either.left(List.of(" + ids.stream().map(ParticleSourceWriter::identifier).collect(Collectors.joining(", ")) + "))",
                    vanilla -> "Either.right(" + identifier(vanilla) + ")"
                )
            ));
            case SkeletalMeshParticleBlueprint mesh -> ctor("SkeletalMeshParticleBlueprint", indent, List.of(
                properties(mesh.properties()),
                NodeTreeSourceRenderer.meshAccessor(mesh.mesh()),
                mesh.armature().map(armature -> "Optional.of(" + armature(armature, indent + 1) + ")").orElse("Optional.empty()"),
                String.valueOf(mesh.copyAttachedPose()),
                optionalIdentifier(mesh.texture()),
                ScriptNodeSourceCompiler.valueLiteral(mesh.color()),
                transform(mesh.transform())
            ));
            case StaticMeshParticleBlueprint mesh -> ctor("StaticMeshParticleBlueprint", indent, List.of(
                properties(mesh.properties()),
                NodeTreeSourceRenderer.meshAccessor(mesh.mesh()),
                optionalIdentifier(mesh.texture()),
                ScriptNodeSourceCompiler.valueLiteral(mesh.color()),
                transform(mesh.transform())
            ));
            case TrailParticleBlueprint trail -> ctor("TrailParticleBlueprint", indent, List.of(
                properties(trail.properties()),
                SourceTemplateEngine.floatLiteral(trail.length()),
                ScriptNodeSourceCompiler.valueLiteral(trail.color()),
                String.valueOf(trail.fadeOut()),
                String.valueOf(trail.interpolation()),
                SourceTemplateEngine.floatLiteral(trail.interpolationSpacing()),
                String.valueOf(trail.trailLifetime()),
                identifier(trail.texture())
            ));
            default -> throw new IllegalStateException("No source form for the particle piece " + piece.getClass().getSimpleName());
        };
    }

    private static String properties(ParticleProperties properties) {
        return "new ParticleProperties(" + properties.hasPhysics() + ", "
            + SourceTemplateEngine.floatLiteral(properties.bbWidth()) + ", " + SourceTemplateEngine.floatLiteral(properties.bbHeight()) + ", "
            + properties.lifetime() + ", "
            + SourceTemplateEngine.floatLiteral(properties.gravity()) + ", " + SourceTemplateEngine.floatLiteral(properties.friction()) + ", "
            + SourceTemplateEngine.floatLiteral(properties.brightness()) + ")";
    }

    /// A skeletal mesh particle's own armature component, which has no child component.
    private static String armature(ArmatureComponentBlueprint armature, int indent) {
        Identifier id = armature.armature().identifier();
        String path = id.getPath().replace(ArmatureManager.DIRECTORY, "");

        return ctor("ArmatureComponentBlueprint", indent, List.of(
            "ArmatureManager.accessor(" + ScriptNodeSourceCompiler.valueLiteral(id.getNamespace()) + ", " + ScriptNodeSourceCompiler.valueLiteral(path) + ", Armature::new)",
            switch (armature.scriptFactory()) {
                case ArmatureComponentBlueprint.ByClass byClass ->
                    "new ArmatureComponentBlueprint.ByClass(" + NodeTreeSourceRenderer.scriptType(byClass.animatorType()) + ")";
                case ArmatureComponentBlueprint.ByDataPack byDataPack -> "new ArmatureComponentBlueprint.ByDataPack("
                    + BlueprintSourceWriter.registryKey(AkytheraRegistries.DataPack.ANIMATOR_SCRIPT_KEY, byDataPack.resourceKey().identifier().toString()) + ")";
                default -> throw new IllegalStateException("No source form for the animator script factory " + armature.scriptFactory().getClass().getSimpleName());
            },
            transform(armature.transform()),
            "TransformInheritPolicy." + armature.transformInheritPolicy().name(),
            "Map.of()",
            String.valueOf(armature.enabled())
        ));
    }

    private static String identifier(Identifier id) {
        return "Identifier.parse(" + ScriptNodeSourceCompiler.valueLiteral(id.toString()) + ")";
    }

    private static String optionalIdentifier(Optional<Identifier> id) {
        return id.map(present -> "Optional.of(" + identifier(present) + ")").orElse("Optional.empty()");
    }

    private static String transform(Transform transform) {
        if (transform.equals(Transform.IDENTITY)) return "Transform.IDENTITY";

        return "new Transform("
            + "new Vec3(" + transform.loc().x + ", " + transform.loc().y + ", " + transform.loc().z + "), "
            + "new Quaterniond(" + transform.rot().x() + ", " + transform.rot().y() + ", " + transform.rot().z() + ", " + transform.rot().w() + "), "
            + "new Vec3(" + transform.sca().x + ", " + transform.sca().y + ", " + transform.sca().z + "))";
    }

    private static String track(KeyframeTrack<Transform> track, int indent) {
        if (track.isEmpty()) return "KeyframeTrack.empty()";

        List<String> keys = track.keys().stream()
            .map(key -> "new KeyframeTrack.Key<>(" + SourceTemplateEngine.floatLiteral(key.time()) + ", " + transform(key.value()) + ")")
            .toList();

        return "new KeyframeTrack<>(" + listOf(keys, indent) + ")";
    }

    /// One entry of an emitter's initializer or updater list: a class, a data pack entry or its generated class, or a
    /// built-in module's record with each parameter as its node tree.
    private static String module(Object module, Function<ResourceKey<?>, @Nullable String> moduleClasses, int indent) {
        return switch (module) {
            case ParticleInitializerBlueprint.ByClass byClass ->
                "new ParticleInitializerBlueprint.ByClass(" + NodeTreeSourceRenderer.scriptType(byClass.type()) + ")";
            case ParticleUpdaterBlueprint.ByClass byClass ->
                "new ParticleUpdaterBlueprint.ByClass(" + NodeTreeSourceRenderer.scriptType(byClass.type()) + ")";
            case ParticleInitializerBlueprint.ByData byData -> dataModule("ParticleInitializerBlueprint", byData.module(), moduleClasses);
            case ParticleUpdaterBlueprint.ByData byData -> dataModule("ParticleUpdaterBlueprint", byData.module(), moduleClasses);
            default -> {
                List<String> arguments = new ArrayList<>();

                try {
                    for (RecordComponent component : module.getClass().getRecordComponents()) {
                        Object value = component.getAccessor().invoke(module);

                        arguments.add(value instanceof PureNode node ? NodeTreeSourceRenderer.pure(node, indent + 1) : ScriptNodeSourceCompiler.valueLiteral(value));
                    }
                } catch (InvocationTargetException | IllegalAccessException e) {
                    throw new IllegalStateException("Unreadable particle module " + module.getClass().getSimpleName(), e);
                }

                yield arguments.isEmpty() ? "new " + SourceNames.typeName(module.getClass()) + "()" : ctor(SourceNames.typeName(module.getClass()), indent, arguments);
            }
        };
    }

    private static String dataModule(String blueprintType, ResourceKey<?> key, Function<ResourceKey<?>, @Nullable String> moduleClasses) {
        String generated = moduleClasses.apply(key);

        return generated != null
            ? "new " + blueprintType + ".ByClass(ScriptType.of(" + generated + ".class))"
            : "new " + blueprintType + ".ByData(" + BlueprintSourceWriter.registryKey(key.registryKey(), key.identifier().toString()) + ")";
    }

    /// `List.of(...)` with one element per line, closing at `indent`. An empty list stays inline.
    public static String listOf(List<String> elements, int indent) {
        if (elements.isEmpty()) return "List.of()";

        String pad = INDENT.repeat(indent + 1);

        return "List.of(\n" + pad + String.join(",\n" + pad, elements) + "\n" + INDENT.repeat(indent) + ")";
    }

    /// `new <className>(...)` with one argument per line, closing at `indent`.
    private static String ctor(String className, int indent, List<String> arguments) {
        String pad = INDENT.repeat(indent + 1);

        return "new " + className + "(\n" + pad + String.join(",\n" + pad, arguments) + "\n" + INDENT.repeat(indent) + ")";
    }
}
