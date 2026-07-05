package com.yesman.akytheralabs.dataholder.entitypatch.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.core.blueprint.script.DataDrivenAnimatorScript;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.dataholder.DataHolder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/// A picked animator script for an [ArmatureComponentHolder], the editor-side mirror of the engine's
/// `ArmatureComponentBlueprint.AnimatorScriptFactory` union: either a **class-based** script (a
/// frozen `SCRIPT_TYPE` entry — [ClassScript]) or a **data pack** script blueprint ([DataPackScript]).
///
/// The data pack variant holds a *live* [Holder] — the shared
/// [com.yesman.akytheralabs.dataholder.ProjectObjectHolder] for project entries — so the reference
/// follows a rename in place (surfaced via [ArmatureComponentHolder#referencedReferences]). The class
/// variant is a frozen engine type and never renames.
public sealed interface AnimatorScriptRef {
    /// Editor persistence dispatch, `{ "type": "class"|"datapack", "animator_type": ... }` — the same
    /// shape the engine factory serializes. On decode the data pack key is resolved back to the shared
    /// live holder, so the component always re-serializes the referenced entry's current id.
    Codec<AnimatorScriptRef> CODEC = Codec.STRING.dispatch(
        ref -> switch (ref) {
            case ClassScript _ -> "class";
            case DataPackScript _ -> "datapack";
        },
        literal -> switch (literal) {
            case "class" -> ClassScript.MAP_CODEC;
            case "datapack" -> DataPackScript.MAP_CODEC;
            default -> throw new IllegalArgumentException("Unknown animator script kind " + literal);
        }
    );

    /// Id shown in the editor [com.yesman.akytheralabs.gui.widget.PopupBox] (`<none>` when unbound).
    String displayLabel();

    /// A data pack script ref over `key`, resolved to the shared live holder (project ∪
    /// `RegistryAccess`; a dangling id gets the project registry's placeholder — see
    /// [AkytheraProject#getEntryHolderOrDangling]).
    static DataPackScript dataPack(ResourceKey<DataDrivenAnimatorScript.AnimatorScriptBlueprint> key) {
        return new DataPackScript(AkytheraProject.getCurrentProject().getEntryHolderOrDangling(key));
    }

    /// A class-based animator script backed by the frozen `SCRIPT_TYPE` entry `scriptType`.
    record ClassScript(ScriptType scriptType) implements AnimatorScriptRef {
        static final MapCodec<ClassScript> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                AkytheraRegistries.Frozen.SCRIPT_TYPE.byNameCodec().fieldOf("animator_type")
                    .forGetter(ClassScript::scriptType)
            ).apply(instance, ClassScript::new)
        );

        @Override
        public String displayLabel() {
            Identifier id = AkytheraRegistries.Frozen.SCRIPT_TYPE.getKey(scriptType);
            return id == null ? "<none>" : id.toString();
        }
    }

    /// A data pack script blueprint reference, held as a live [Holder] so a rename of the target
    /// propagates in place.
    record DataPackScript(Holder<DataDrivenAnimatorScript.AnimatorScriptBlueprint> holder) implements AnimatorScriptRef {
        static final MapCodec<DataPackScript> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                ResourceKey.codec(AkytheraRegistries.DataPack.ANIMATOR_SCRIPT_KEY).fieldOf("animator_type")
                    .forGetter(ref -> ref.holder.unwrapKey().orElseThrow())
            ).apply(instance, AnimatorScriptRef::dataPack)
        );

        @Override
        public String displayLabel() {
            return holder.unwrapKey()
                .map(k -> k.identifier().equals(DataHolder.EMPTY_REFERENCE) ? "<none>" : k.identifier().toString())
                .orElse("<none>");
        }
    }
}