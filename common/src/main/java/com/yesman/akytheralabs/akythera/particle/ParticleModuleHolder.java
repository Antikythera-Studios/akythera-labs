package com.yesman.akytheralabs.akythera.particle;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.client.api.particle.AkytheraParticle;
import com.yesman.akythera.client.api.particle.module.ModuleParameter;
import com.yesman.akythera.client.api.particle.module.ParticleInitializerModule;
import com.yesman.akythera.client.api.particle.module.ParticleUpdateModule;
import com.yesman.akythera.core.blueprint.particle.ParticleInitializerBlueprint;
import com.yesman.akythera.core.blueprint.particle.ParticleUpdaterBlueprint;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/// Modifiable data holder for one row of an emitter's initializer or updater list: the module picked
/// ([ModuleRef]), and for a built-in module its parameters.
///
/// A built-in module is a record whose [PureNode] components are its parameters, each declared by a
/// [ModuleParameter]. The rows follow from that record, so a new built-in module needs nothing here.
public final class ParticleModuleHolder {
    /// What each module type builds from a json with no field, see [#prototype]. Empty for a type that builds nothing.
    private static final Map<MapCodec<?>, Optional<Object>> PROTOTYPES = new ConcurrentHashMap<>();

    /// Whether this is a row of the updater list. Otherwise of the initializer list.
    private final boolean updater;

    private ModuleRef ref = ModuleRef.NONE;

    /// The parameters of a [ModuleRef.BuiltIn] module, in the order its record declares them. Empty otherwise.
    private List<ModuleParamHolder> params = List.of();

    public ParticleModuleHolder(final boolean updater) {
        this.updater = updater;
    }

    public boolean isUpdater() {
        return updater;
    }

    public ModuleRef getRef() {
        return ref;
    }

    /// Picks `ref`, with the parameters of a built-in module: one [ModuleParamHolder] per [PureNode] component of its
    /// record that declares a [ModuleParameter].
    public void setRef(final ModuleRef ref) {
        List<ModuleParamHolder> declared = new ArrayList<>();

        if (ref instanceof ModuleRef.BuiltIn builtIn && prototype(builtIn.type(), updater) instanceof Object prototype) {
            for (RecordComponent component : prototype.getClass().getRecordComponents()) {
                ModuleParameter parameter = component.getAnnotation(ModuleParameter.class);

                if (parameter != null && component.getType() == PureNode.class) declared.add(new ModuleParamHolder(component.getName(), parameter));
            }
        }

        this.ref = ref;
        this.params = declared;
    }

    public List<ModuleParamHolder> getParams() {
        return params;
    }

    /// The ids of the built-in modules of the initializer or updater type registry: the types that build with no field
    /// given.
    public static List<Identifier> builtInTypes(final boolean updater) {
        return typeRegistry(updater).keySet().stream().filter(id -> prototype(id, updater) != null).sorted().toList();
    }

    /// The particles the built-in module `type` applies to.
    public static Class<? extends AkytheraParticle> appliesTo(final Identifier type, final boolean updater) {
        return switch (prototype(type, updater)) {
            case ParticleInitializerModule module -> module.appliesTo();
            case ParticleUpdateModule module -> module.appliesTo();
            case null, default -> AkytheraParticle.class;
        };
    }

    /// Whether the module runs on particles of `particleClass`. Only a built-in module can refuse a class, and a null
    /// class refuses none.
    public boolean appliesTo(final @Nullable Class<? extends AkytheraParticle> particleClass) {
        return particleClass == null || !(ref instanceof ModuleRef.BuiltIn builtIn) || appliesTo(builtIn.type(), updater).isAssignableFrom(particleClass);
    }

    /// Writes `value` into `param` as its literal, kept within the parameters its [ModuleParameter#atMost] and
    /// [ModuleParameter#atLeast] name: each number, and each axis of a vector, on its own.
    public void writeLiteral(final ModuleParamHolder param, final Object value) {
        Object bounded = value;

        for (ModuleParamHolder other : params) {
            boolean ceiling = other.getName().equals(param.declared().atMost());
            boolean floor = other.getName().equals(param.declared().atLeast());

            if (!ceiling && !floor) continue;

            bounded = switch (bounded) {
                case Vec3 vector when other.getLiteral() instanceof Vec3 bound -> ceiling
                    ? new Vec3(Math.min(vector.x, bound.x), Math.min(vector.y, bound.y), Math.min(vector.z, bound.z))
                    : new Vec3(Math.max(vector.x, bound.x), Math.max(vector.y, bound.y), Math.max(vector.z, bound.z));
                case Float number when other.getLiteral() instanceof Float bound -> ceiling ? Math.min(number, bound) : Math.max(number, bound);
                case Integer number when other.getLiteral() instanceof Integer bound -> ceiling ? Math.min(number, bound) : Math.max(number, bound);
                default -> bounded;
            };
        }

        param.setLiteral(bounded);
    }

    /// The initializer this row names, or empty while no module is picked.
    ///
    /// @throws IllegalStateException when this is an updater row, or the module can't be built
    public Optional<ParticleInitializerBlueprint> makeInitializer() {
        if (updater) throw new IllegalStateException("An updater row makes no initializer");

        return switch (ref) {
            case ModuleRef.None _ -> Optional.empty();
            case ModuleRef.BuiltIn builtIn -> Optional.of((ParticleInitializerBlueprint)build(builtIn));
            case ModuleRef.ByClass byClass -> Optional.of(new ParticleInitializerBlueprint.ByClass(byClass.type()));
            case ModuleRef.ByData byData -> Optional.of(new ParticleInitializerBlueprint.ByData(ResourceKey.create(AkytheraRegistries.DataPack.PARTICLE_INITIALIZER_KEY, byData.id())));
        };
    }

    /// The updater this row names, or empty while no module is picked.
    ///
    /// @throws IllegalStateException when this is an initializer row, or the module can't be built
    public Optional<ParticleUpdaterBlueprint> makeUpdater() {
        if (!updater) throw new IllegalStateException("An initializer row makes no updater");

        return switch (ref) {
            case ModuleRef.None _ -> Optional.empty();
            case ModuleRef.BuiltIn builtIn -> Optional.of((ParticleUpdaterBlueprint)build(builtIn));
            case ModuleRef.ByClass byClass -> Optional.of(new ParticleUpdaterBlueprint.ByClass(byClass.type()));
            case ModuleRef.ByData byData -> Optional.of(new ParticleUpdaterBlueprint.ByData(ResourceKey.create(AkytheraRegistries.DataPack.PARTICLE_UPDATER_KEY, byData.id())));
        };
    }

    /// The built-in module's record, built with each parameter's node.
    private Object build(final ModuleRef.BuiltIn builtIn) {
        Object prototype = prototype(builtIn.type(), updater);

        if (prototype == null) throw new IllegalStateException("No built-in particle module " + builtIn.type());

        RecordComponent[] components = prototype.getClass().getRecordComponents();
        Object[] arguments = new Object[components.length];

        try {
            for (int i = 0; i < components.length; i++) {
                String name = components[i].getName();
                ModuleParamHolder param = params.stream().filter(candidate -> candidate.getName().equals(name)).findFirst().orElse(null);

                arguments[i] = param != null ? param.compile() : components[i].getAccessor().invoke(prototype);
            }

            return prototype.getClass().getDeclaredConstructor(Arrays.stream(components).map(RecordComponent::getType).toArray(Class[]::new)).newInstance(arguments);
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
            throw new IllegalStateException("Failed at building particle module " + builtIn.type(), e);
        }
    }

    /// The row for the module `blueprint`, an entry of an emitter's initializer or updater list.
    public static ParticleModuleHolder of(final Object blueprint, final boolean updater) {
        ParticleModuleHolder holder = new ParticleModuleHolder(updater);

        switch (blueprint) {
            case ParticleInitializerBlueprint.ByClass byClass -> holder.ref = new ModuleRef.ByClass(byClass.type());
            case ParticleUpdaterBlueprint.ByClass byClass -> holder.ref = new ModuleRef.ByClass(byClass.type());
            case ParticleInitializerBlueprint.ByData byData -> holder.ref = new ModuleRef.ByData(byData.module().identifier());
            case ParticleUpdaterBlueprint.ByData byData -> holder.ref = new ModuleRef.ByData(byData.module().identifier());
            default -> {
                MapCodec<?> mapCodec = blueprint instanceof ParticleInitializerBlueprint initializer
                    ? initializer.mapCodec()
                    : ((ParticleUpdaterBlueprint)blueprint).mapCodec();
                Identifier type = typeRegistry(updater).entrySet().stream()
                    .filter(entry -> entry.getValue() == mapCodec)
                    .map(entry -> entry.getKey().identifier())
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("The editor can't edit the particle module " + blueprint.getClass().getSimpleName()));

                holder.setRef(new ModuleRef.BuiltIn(type));

                for (RecordComponent component : blueprint.getClass().getRecordComponents()) {
                    for (ModuleParamHolder param : holder.params) {
                        if (!param.getName().equals(component.getName())) continue;

                        try {
                            param.readFrom((PureNode)component.getAccessor().invoke(blueprint));
                        } catch (InvocationTargetException | IllegalAccessException e) {
                            throw new IllegalArgumentException("Failed at reading particle module " + type, e);
                        }
                    }
                }
            }
        }

        return holder;
    }

    public Stored toStored() {
        Map<String, ModuleParamHolder.Stored> stored = new LinkedHashMap<>();

        params.forEach(param -> stored.put(param.getName(), param.toStored()));

        return new Stored(ref, stored);
    }

    /// Takes the module and parameters `stored` describes. Parameters the module no longer declares are dropped.
    public void load(final Stored stored) {
        setRef(stored.ref());

        for (ModuleParamHolder param : params) {
            ModuleParamHolder.Stored storedParam = stored.params().get(param.getName());

            if (storedParam != null) param.load(storedParam);
        }
    }

    /// The row `stored` describes.
    public static ParticleModuleHolder of(final Stored stored, final boolean updater) {
        ParticleModuleHolder holder = new ParticleModuleHolder(updater);

        holder.load(stored);

        return holder;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Registry<MapCodec<?>> typeRegistry(final boolean updater) {
        return (Registry)(updater ? AkytheraRegistries.Frozen.PARTICLE_UPDATER_TYPES : AkytheraRegistries.Frozen.PARTICLE_INITIALIZER_TYPES);
    }

    /// The module `type` builds from a json with no field: a built-in module. Null for a type that needs a field, as
    /// the class and data pack types do, and for an unknown type.
    private static @Nullable Object prototype(final Identifier type, final boolean updater) {
        MapCodec<?> mapCodec = typeRegistry(updater).getValue(type);

        if (mapCodec == null) return null;

        return PROTOTYPES.computeIfAbsent(
            mapCodec, codec -> codec.codec().parse(JsonOps.INSTANCE, new JsonObject()).result().filter(decoded -> decoded.getClass().isRecord()).map(decoded -> (Object)decoded)
        ).orElse(null);
    }

    /// A module row in the editor's json.
    ///
    /// @param params a built-in module's parameters, by name
    public record Stored(ModuleRef ref, Map<String, ModuleParamHolder.Stored> params) {
        public static final Codec<Stored> CODEC = RecordCodecBuilder.create(
            instance ->
                instance.group(
                    ModuleRef.CODEC.fieldOf("module").forGetter(Stored::ref),
                    Codec.unboundedMap(Codec.STRING, ModuleParamHolder.Stored.CODEC).optionalFieldOf("params", Map.of()).forGetter(Stored::params)
                ).apply(instance, Stored::new)
        );
    }
}
