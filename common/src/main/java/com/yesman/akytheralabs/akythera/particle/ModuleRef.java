package com.yesman.akytheralabs.akythera.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.helper.ParseUtils;
import net.minecraft.resources.Identifier;

/// The module one row of an emitter's initializer or updater list runs: a built-in module, a Java class, a data pack
/// entry, or none picked yet. Whether it names an initializer or an updater is the list's to say.
public sealed interface ModuleRef {
    ModuleRef NONE = new None();

    Codec<ModuleRef> CODEC = Codec.STRING.dispatch(
        ref -> switch (ref) {
            case None _ -> "none";
            case BuiltIn _ -> "built_in";
            case ByClass _ -> "class";
            case ByData _ -> "data";
        },
        kind -> switch (kind) {
            case "none" -> None.MAP_CODEC;
            case "built_in" -> BuiltIn.MAP_CODEC;
            case "class" -> ByClass.MAP_CODEC;
            case "data" -> ByData.MAP_CODEC;
            default -> throw new IllegalArgumentException("Unknown particle module kind " + kind);
        }
    );

    /// What the module select box shows.
    String displayLabel();

    record None() implements ModuleRef {
        static final MapCodec<None> MAP_CODEC = MapCodec.unit(None::new);

        @Override
        public String displayLabel() {
            return "<none>";
        }
    }

    /// A built-in module with parameters.
    ///
    /// @param type its id in the particle initializer or updater type registry
    record BuiltIn(Identifier type) implements ModuleRef {
        static final MapCodec<BuiltIn> MAP_CODEC = Identifier.CODEC.fieldOf("module").xmap(BuiltIn::new, BuiltIn::type);

        @Override
        public String displayLabel() {
            return ParseUtils.toDisplayName(type.getPath());
        }
    }

    /// A module class registered as a script type.
    record ByClass(ScriptType type) implements ModuleRef {
        static final MapCodec<ByClass> MAP_CODEC = AkytheraRegistries.Frozen.SCRIPT_TYPE.byNameCodec().fieldOf("class").xmap(ByClass::new, ByClass::type);

        @Override
        public String displayLabel() {
            Identifier id = AkytheraRegistries.Frozen.SCRIPT_TYPE.getKey(type);

            return id == null ? "<none>" : id.toString();
        }
    }

    /// A module written as a node graph: an entry of the particle initializer or updater data pack registry.
    record ByData(Identifier id) implements ModuleRef {
        static final MapCodec<ByData> MAP_CODEC = Identifier.CODEC.fieldOf("module").xmap(ByData::new, ByData::id);

        @Override
        public String displayLabel() {
            return id.toString();
        }
    }
}
