package com.yesman.akytheralabs.dataholder.entitypatch.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.asset.armature.ArmatureManager;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.blueprint.ArmatureComponentBlueprint;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.dataholder.AbstractDataHolder;
import com.yesman.akytheralabs.dataholder.EditorReference;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public final class ArmatureComponentHolder extends AbstractDataHolder<ArmatureComponentBlueprint>
    implements ComponentSourceProvider
{
    /// Editor persistence (a [MapCodec] so [EntityPatchComponentHolders#CODEC] can dispatch it by
    /// the component type id). The armature is stored as its trimmed identifier and resolved back
    /// through [#resolveArmature] — NOT through [ArmatureManager#CODEC], whose decode is a plain
    /// map lookup that yields null for accessors nothing has issued yet at project-load time.
    /// The script is an [AnimatorScriptRef] union (class type or live data pack [net.minecraft.core.Holder]);
    /// its data pack variant serializes its key but resolves to a shared holder, so the path always
    /// reflects the referenced entry's current id.
    public static final MapCodec<ArmatureComponentHolder> EDITOR_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
            Identifier.CODEC.fieldOf("armature")
                .forGetter(h -> trimDirectory(h.armature.identifier())),
            AnimatorScriptRef.CODEC.fieldOf("script")
                .forGetter(h -> h.animatorScript),
            Codec.BOOL.fieldOf("use_root_motion")
                .forGetter(ArmatureComponentHolder::getUseRootMotion)
        ).apply(instance, (armatureId, script, useRootMotion) ->
            new ArmatureComponentHolder(resolveArmature(armatureId), script, useRootMotion))
    );

    @EditorEditable(label = LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_ARMATURE, order = 0)
    private AssetAccessor<? extends Armature> armature;

    @EditorEditable(label = LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_ANIM_SCRIPT, order = 1)
    private AnimatorScriptRef animatorScript;

    @EditorEditable(label = LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_ROOT_MOTION, order = 2)
    private boolean useRootMotion;

    public ArmatureComponentHolder(
        AssetAccessor<? extends Armature> armature,
        AnimatorScriptRef animatorScript,
        boolean useRootMotion
    ) {
        this.armature = armature;
        this.animatorScript = animatorScript;
        this.useRootMotion = useRootMotion;
    }

    /// A freshly added (not yet configured) armature component. The armature is the
    /// [com.yesman.akytheralabs.dataholder.DataHolder#EMPTY_REFERENCE] placeholder and the script is
    /// a dangling data pack ref — deliberately non-null, because the editor widgets read the
    /// accessor's runtime `type()` / the ref's union kind to route their pick modals. Both display as
    /// `<none>`.
    public static ArmatureComponentHolder createDefault() {
        return new ArmatureComponentHolder(
            resolveArmature(EMPTY_REFERENCE),
            AnimatorScriptRef.dataPack(ResourceKey.create(AkytheraRegistries.DataPack.ANIMATOR_SCRIPT_KEY, EMPTY_REFERENCE)),
            false
        );
    }

    /// Trimmed armature id → accessor. [ArmatureManager#accessor] issues one when absent, and the
    /// labs mixin reroutes project-namespaced ids to the project's editor accessors — so this never
    /// returns null, even for ids saved before their asset was ever browsed.
    public static AssetAccessor<? extends Armature> resolveArmature(Identifier trimmedId) {
        return ArmatureManager.accessor(trimmedId.getNamespace(), trimmedId.getPath(), Armature::new);
    }

    private static Identifier trimDirectory(Identifier id) {
        return Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath().replace(ArmatureManager.DIRECTORY, ""));
    }

    public AssetAccessor<? extends Armature> getArmature() {
        return armature;
    }

    public AnimatorScriptRef getAnimatorScript() {
        return animatorScript;
    }

    public boolean getUseRootMotion() {
        return useRootMotion;
    }

    public void setArmature(AssetAccessor<? extends Armature> armature) {
        this.armature = armature;
    }

    public void setAnimatorScript(AnimatorScriptRef animatorScript) {
        this.animatorScript = animatorScript;
    }

    public void setUseRootMotion(boolean useRootMotion) {
        this.useRootMotion = useRootMotion;
    }

    /// The shared reference instances this component holds — the armature accessor (when
    /// editor-owned) and, for a data pack script, its `ProjectObjectHolder` (when a project entry) —
    /// so renaming either re-saves the owning entity patch's JSON. A class script has no renameable
    /// reference. Matched by identity in the rename scan (via
    /// [com.yesman.akytheralabs.dataholder.entitypatch.EntityPatchBlueprintHolder#referencedReferences]).
    @Override
    public Stream<EditorReference> referencedReferences() {
        return Stream.of(armature, scriptReference())
            .filter(r -> r instanceof EditorReference)
            .map(r -> (EditorReference) r);
    }

    /// The shared data pack script holder when this component points at one (renameable); null for a
    /// class script.
    private @Nullable Object scriptReference() {
        return animatorScript instanceof AnimatorScriptRef.DataPackScript dataPackScript ? dataPackScript.holder() : null;
    }

    @Override
    public void readFrom(ArmatureComponentBlueprint object) {
        armature = object.armature();
        animatorScript = fromFactory(object.scriptFactory());
        useRootMotion = object.useRootMotion();
    }

    @Override
    public ArmatureComponentBlueprint makeObject() {
        return new ArmatureComponentBlueprint(armature, toFactory(animatorScript), useRootMotion);
    }

    /// ***************************************************************
    /// Source export (File > Export > As Source Code)
    /// ***************************************************************

    @Override
    public String sourceTemplate() {
        return "components/armature.ftl";
    }

    /// Tokens for `components/armature.ftl`: the trimmed armature id (namespace/path, rebuilt into an
    /// [ArmatureManager#accessor] call — mirroring [#resolveArmature]/[#EDITOR_CODEC]), the animator
    /// script as a `script_kind` (`class`/`datapack`) + `script_id` pair the fragment lowers to the
    /// matching `ArmatureComponentBlueprint.ByClass`/`ByDataPack`, and the root-motion flag. A script
    /// whose id can't be resolved (dangling `<none>`) emits an empty id — a compile-time TODO for the
    /// author, not an export failure.
    @Override
    public Map<String, Object> getDTO() {
        Identifier armatureId = trimDirectory(armature.identifier());

        Map<String, Object> dto = new HashMap<>();
        dto.put("armature_namespace", armatureId.getNamespace());
        dto.put("armature_path", armatureId.getPath());
        dto.put("use_root_motion", useRootMotion);

        switch (animatorScript) {
            case AnimatorScriptRef.ClassScript classScript -> {
                Identifier id = AkytheraRegistries.Frozen.SCRIPT_TYPE.getKey(classScript.scriptType());
                dto.put("script_kind", "class");
                dto.put("script_id", id == null ? "" : id.toString());
            }
            case AnimatorScriptRef.DataPackScript dataPackScript -> {
                dto.put("script_kind", "datapack");
                dto.put("script_id", dataPackScript.holder().unwrapKey()
                    .map(key -> key.identifier().toString()).orElse(""));
            }
        }

        return dto;
    }

    /// Engine factory → editor union. A [ArmatureComponentBlueprint.ByDataPack] key is re-resolved to
    /// the shared live holder so the loaded component follows renames like a freshly picked one.
    private static AnimatorScriptRef fromFactory(ArmatureComponentBlueprint.AnimatorScriptFactory factory) {
        return switch (factory) {
            case ArmatureComponentBlueprint.ByClass byClass -> new AnimatorScriptRef.ClassScript(byClass.animatorType());
            case ArmatureComponentBlueprint.ByDataPack byDataPack -> AnimatorScriptRef.dataPack(byDataPack.resourceKey());
            default -> throw new IllegalArgumentException("Unknown animator script factory " + factory.getClass().getName());
        };
    }

    /// Editor union → engine factory.
    private static ArmatureComponentBlueprint.AnimatorScriptFactory toFactory(AnimatorScriptRef ref) {
        return switch (ref) {
            case AnimatorScriptRef.ClassScript classScript ->
                new ArmatureComponentBlueprint.ByClass(classScript.scriptType());
            case AnimatorScriptRef.DataPackScript dataPackScript ->
                new ArmatureComponentBlueprint.ByDataPack(dataPackScript.holder().unwrapKey().orElseThrow());
        };
    }
}
