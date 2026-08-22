package com.yesman.akytheralabs.akythera.entitypatch.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.impl.ArmatureComponent;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.asset.armature.ArmatureManager;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.armature.BoneSocket;
import com.yesman.akythera.core.blueprint.ArmatureComponentBlueprint;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.akythera.entitypatch.EntityPatchBlueprintHolder;
import com.yesman.akytheralabs.dataholder.AbstractDataHolder;
import com.yesman.akytheralabs.dataholder.DataHolder;
import com.yesman.akytheralabs.dataholder.EditorReference;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.stream.Stream;

public final class ArmatureComponentHolder extends ComponentDataHolder<ArmatureComponentBlueprint>
    implements ComponentAttachableHolder, RootAttachableComponent
{
    /// Editor persistence, as a [MapCodec] so the entity patch registry's codec dispatches it by the
    /// component type id.
    ///
    /// The armature is stored as its trimmed identifier and resolved back through [#resolveArmature].
    /// [ArmatureManager#CODEC] decodes through a plain map lookup, which yields null at project-load
    /// time for an accessor nothing has issued yet.
    ///
    /// The script is an [AnimatorScriptRef] union — a class type, or a live data pack [Holder]. The data pack
    /// variant serializes its key but resolves to a shared holder, so the path always reflects the referenced
    /// entry's current id.
    ///
    /// The attached sockets ride [ComponentAttachableHolder#componentsField], which dispatches back
    /// through the same registry this codec is registered in. [Codec#lazyInitialized] breaks that
    /// cycle, the registry's static initializer being what registers this very field.
    public static final MapCodec<ArmatureComponentHolder> EDITOR_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
            Identifier.CODEC.fieldOf("armature")
                .forGetter(h -> trimDirectory(h.armature.identifier())),
            AnimatorScriptRef.CODEC.fieldOf("script")
                .forGetter(h -> h.animatorScript),
            Codec.BOOL.fieldOf("use_root_motion")
                .forGetter(ArmatureComponentHolder::getUseRootMotion),
            ComponentAttachableHolder.componentsField(() -> EntityPatchComponentHolders.REGISTRY)
                .forGetter(ComponentAttachableHolder::componentsOf)
        ).apply(instance, (armatureId, script, useRootMotion, sockets) -> {
            ArmatureComponentHolder holder =
                new ArmatureComponentHolder(resolveArmature(armatureId), script, useRootMotion);
            ComponentAttachableHolder.applyComponents(holder, sockets);
            return holder;
        })
    );

    @EditorEditable(label = LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_ARMATURE, order = 0)
    private AssetAccessor<? extends Armature> armature;

    @EditorEditable(label = LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_ANIM_SCRIPT, order = 1)
    private AnimatorScriptRef animatorScript;

    @EditorEditable(label = LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_ROOT_MOTION, order = 2)
    private boolean useRootMotion;

    /// The attached [BoneSocketHolder]s by name — the armature's [ComponentAttachableHolder]
    /// children, edited as structure in the Components tree rather than as Details rows.
    private final SequencedMap<String, ComponentDataHolder<?>> sockets = ComponentAttachableHolder.newComponentMap();

    public ArmatureComponentHolder(
        AssetAccessor<? extends Armature> armature,
        AnimatorScriptRef animatorScript,
        boolean useRootMotion
    ) {
        this.armature = armature;
        this.animatorScript = animatorScript;
        this.useRootMotion = useRootMotion;
    }

    /// A freshly added, unconfigured armature component. The armature is the [DataHolder#EMPTY_REFERENCE]
    /// placeholder and the script a dangling data pack ref; both are non-null and display as `<none>`, so the
    /// editor widgets can still read the accessor's `type()` and the ref's union kind to route their pick
    /// modals.
    public static ArmatureComponentHolder createDefault() {
        return new ArmatureComponentHolder(
            resolveArmature(EMPTY_REFERENCE),
            AnimatorScriptRef.dataPack(ResourceKey.create(AkytheraRegistries.DataPack.ANIMATOR_SCRIPT_KEY, EMPTY_REFERENCE)),
            false
        );
    }

    /// Trimmed armature id → accessor, never null. [ArmatureManager#accessor] issues one when absent,
    /// and the labs mixin reroutes project-namespaced ids to the project's editor accessors.
    public static AssetAccessor<? extends Armature> resolveArmature(Identifier trimmedId) {
        return ArmatureManager.accessor(trimmedId.getNamespace(), trimmedId.getPath(), Armature::new);
    }

    /// The referenced armature's bone names, sorted — the candidate list an attached
    /// [BoneSocketHolder] binds to. Empty while the component points at the `<none>` placeholder or
    /// the armature fails to load.
    ///
    /// Loads the armature, the accessor being lazy.
    public List<String> boneNames() {
        if (trimDirectory(armature.identifier()).equals(EMPTY_REFERENCE)) return List.of();

        Armature resolved = armature.getOrHandleException(e ->
            AkytheraLabs.LOGGER.warn("Failed to load armature '{}' for its bone list", armature.identifier(), e));

        if (resolved == null) return List.of();

        List<String> names = new ArrayList<>();
        resolved.visitBones(bone -> names.add(bone.getName()));
        names.sort(Comparator.naturalOrder());

        return names;
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

    /// Sockets only — the runtime [ArmatureComponent] types its child map as [BoneSocket]s.
    @Override
    public List<Class<?>> attachableTypes() {
        return List.of(BoneSocketHolder.class);
    }

    @Override
    public SequencedMap<String, ComponentDataHolder<?>> getComponents() {
        return sockets;
    }

    /// The attached sockets lowered to runtime [BoneSocket]s, keyed by the name the engine looks
    /// them up under. A socket the guard salvaged as a raw placeholder has no runtime object and is
    /// skipped.
    private Map<String, BoneSocket> lowerSockets() {
        Map<String, BoneSocket> lowered = new LinkedHashMap<>();

        sockets.forEach((name, holder) -> {
            if (holder instanceof BoneSocketHolder socket) lowered.put(name, socket.makeObject());
        });

        return lowered;
    }

    /// The shared reference instances this component holds: the armature accessor when editor-owned,
    /// and a data pack script's `ProjectObjectHolder` when it points at a project entry. A class
    /// script carries no renameable reference.
    @Override
    public Stream<EditorReference> referencedReferences() {
        return Stream.concat(
            Stream.of(armature, scriptReference())
                .filter(r -> r instanceof EditorReference)
                .map(r -> (EditorReference) r),
            sockets.values().stream().flatMap(DataHolder::referencedReferences)
        );
    }

    /// The first attached socket the decode guard salvaged, if any.
    @Override
    public @Nullable Exception getNestedFailureException() {
        return AbstractDataHolder.firstNestedFailure(sockets.values());
    }

    /// The shared data pack script holder when this component points at one, else null.
    private @Nullable Object scriptReference() {
        return animatorScript instanceof AnimatorScriptRef.DataPackScript dataPackScript ? dataPackScript.holder() : null;
    }

    @Override
    public void readFrom(ArmatureComponentBlueprint object) {
        armature = object.armature();
        animatorScript = fromFactory(object.scriptFactory());
        useRootMotion = object.useRootMotion();

        sockets.clear();
        object.sockets().forEach((name, socket) -> {
            BoneSocketHolder holder = BoneSocketHolder.createDefault();
            holder.readFrom(socket);
            sockets.put(name, holder);
        });
    }

    @Override
    public ArmatureComponentBlueprint makeObject() {
        return new ArmatureComponentBlueprint(armature, toFactory(animatorScript), useRootMotion, lowerSockets());
    }

    @Override
    public Class<? extends PatchComponent> generateEventually() {
        return ArmatureComponent.class;
    }

    // ***************************************************************
    // Source export (File > Export > As Source Code)
    // ***************************************************************

    @Override
    public String sourceTemplate() {
        return "components/armature.ftl";
    }

    /// Tokens for `components/armature.ftl`: the trimmed armature namespace and path the fragment
    /// rebuilds into an [ArmatureManager#accessor] call, the animator script as a `script_kind`
    /// (`class` / `datapack`) and `script_id` pair lowering to the matching
    /// `ArmatureComponentBlueprint.ByClass` / `ByDataPack`, the root-motion flag, and the attached
    /// sockets as name → constructor expression rows. A script whose id resolves nowhere emits an
    /// empty `script_id`.
    @Override
    public Map<String, Object> getDTO() {
        Identifier armatureId = trimDirectory(armature.identifier());

        Map<String, Object> dto = new HashMap<>();
        dto.put("armature_namespace", armatureId.getNamespace());
        dto.put("armature_path", armatureId.getPath());
        dto.put("use_root_motion", useRootMotion);
        dto.put("sockets", ComponentDataHolder.sourcesOf(sockets));

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

    /// Engine factory → editor union. An [ArmatureComponentBlueprint.ByDataPack] key re-resolves to
    /// the shared live holder.
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
