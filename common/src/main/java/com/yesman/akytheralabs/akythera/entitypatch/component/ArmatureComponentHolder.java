package com.yesman.akytheralabs.akythera.entitypatch.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.impl.ArmatureComponent;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.asset.armature.ArmatureManager;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.component.TransformInheritPolicy;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.blueprint.ArmatureComponentBlueprint;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.akythera.asset.EditorAssetAccessors;
import com.yesman.akytheralabs.akythera.entitypatch.EntityPatchBlueprintHolder;
import com.yesman.akytheralabs.dataholder.AbstractDataHolder;
import com.yesman.akytheralabs.dataholder.DataHolder;
import com.yesman.akytheralabs.dataholder.EditorReference;
import com.yesman.akytheralabs.dataholder.TransformHolder;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.stream.Stream;

public final class ArmatureComponentHolder extends ComponentDataHolder<ArmatureComponentBlueprint>
    implements ComponentAttachableHolder, RootAttachableComponent, TransformComponentHolder
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
    /// The attached components ride [ComponentAttachableHolder#componentsField], which dispatches back
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
            TransformHolder.CODEC.optionalFieldOf("transform")
                .forGetter(h -> Optional.of(h.localTransform)),
            TransformInheritPolicy.CODEC.optionalFieldOf("transform_inherit_policy", TransformInheritPolicy.LOC_ROT_SCA)
                .forGetter(TransformComponentHolder::getTransformInheritPolicy),
            ComponentAttachableHolder.componentsField(() -> EntityPatchComponentHolders.REGISTRY)
                .forGetter(ComponentAttachableHolder::componentsOf)
        ).apply(instance, (armatureId, script, useRootMotion, transform, transformInheritPolicy, components) -> {
            ArmatureComponentHolder holder = new ArmatureComponentHolder(
                resolveArmature(armatureId),
                script,
                useRootMotion,
                transform.orElseGet(TransformHolder::identity),
                transformInheritPolicy
            );
            ComponentAttachableHolder.applyComponents(holder, components);
            return holder;
        })
    );

    @EditorEditable(label = LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_ARMATURE, order = 0)
    private AssetAccessor<? extends Armature> armature;

    @EditorEditable(label = LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_ANIM_SCRIPT, order = 1)
    private AnimatorScriptRef animatorScript;

    @EditorEditable(label = LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_ROOT_MOTION, order = 2)
    private boolean useRootMotion;

    /// The elements of the patch's space the skeleton takes on.
    @EditorEditable(label = LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_TRANSFORM_INHERIT_POLICY, order = 3)
    private TransformInheritPolicy transformInheritPolicy;

    /// Where the skeleton sits in the patch's space. A nested editor object, rendered as location / rotation / scale
    /// rows under one "Local Transform" heading.
    @EditorEditable(label = LangKeys.GUI_ENTITY_PATCH_SCREEN_SECTION_LOCAL_TRANSFORM, order = 4)
    private TransformHolder localTransform;

    /// The attached component holders by name — the armature's [ComponentAttachableHolder] children,
    /// edited as structure in the Components tree rather than as Details rows.
    private final SequencedMap<String, ComponentDataHolder<?>> components = ComponentAttachableHolder.newComponentMap();

    public ArmatureComponentHolder(
        AssetAccessor<? extends Armature> armature,
        AnimatorScriptRef animatorScript,
        boolean useRootMotion,
        TransformHolder localTransform,
        TransformInheritPolicy transformInheritPolicy
    ) {
        this.armature = armature;
        this.animatorScript = animatorScript;
        this.useRootMotion = useRootMotion;
        this.localTransform = localTransform;
        this.transformInheritPolicy = transformInheritPolicy;
    }

    /// A freshly added, unconfigured armature component. The armature is the [DataHolder#EMPTY_REFERENCE]
    /// placeholder and the script a dangling data pack ref; both are non-null and display as `<none>`, so the
    /// editor widgets can still read the accessor's `type()` and the ref's union kind to route their pick
    /// modals.
    public static ArmatureComponentHolder createDefault() {
        return new ArmatureComponentHolder(
            resolveArmature(EMPTY_REFERENCE),
            AnimatorScriptRef.none(),
            false,
            TransformHolder.identity(),
            TransformInheritPolicy.LOC_ROT_SCA
        );
    }

    /// Trimmed armature id → accessor, never null — see [EditorAssetAccessors#armature].
    public static AssetAccessor<? extends Armature> resolveArmature(Identifier trimmedId) {
        return EditorAssetAccessors.armature(trimmedId);
    }

    /// The referenced armature's bone names, sorted — the candidate list an attached
    /// [TransformSocketHolder] binds to. Empty while the component points at the `<none>` placeholder or
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

    @Override
    public TransformHolder getLocalTransform() {
        return localTransform;
    }

    public void setLocalTransform(TransformHolder localTransform) {
        this.localTransform = localTransform;
    }

    @Override
    public TransformInheritPolicy getTransformInheritPolicy() {
        return transformInheritPolicy;
    }

    public void setTransformInheritPolicy(TransformInheritPolicy transformInheritPolicy) {
        this.transformInheritPolicy = transformInheritPolicy;
    }

    /// What an armature hosts: a [TransformSocketHolder] binding a bone, and the entity's own model
    /// — [MeshComponentHolder]s drawing meshes, or a [PALPoseBinderHolder] leaving it to PAL.
    @Override
    public List<Class<?>> attachableTypes() {
        return List.of(TransformSocketHolder.class, MeshComponentHolder.class, PALPoseBinderHolder.class);
    }

    @Override
    public SequencedMap<String, ComponentDataHolder<?>> getComponents() {
        return components;
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
            components.values().stream().flatMap(DataHolder::referencedReferences)
        );
    }

    /// The first attached component the decode guard salvaged, if any.
    @Override
    public @Nullable Exception getNestedFailureException() {
        return AbstractDataHolder.firstNestedFailure(components.values());
    }

    /// The shared data pack script holder when this component points at one, else null.
    private @Nullable Object scriptReference() {
        return animatorScript instanceof AnimatorScriptRef.DataPackScript dataPackScript ? dataPackScript.holder() : null;
    }

    @Override
    public void readFrom(ArmatureComponentBlueprint object) {
        armature = object.armature();
        animatorScript = AnimatorScriptRef.of(object.scriptFactory());
        useRootMotion = object.useRootMotion();
        localTransform.readFrom(object.transform());
        transformInheritPolicy = object.transformInheritPolicy();

        ComponentAttachableHolder.readComponents(
            this, EntityPatchComponentHolders.REGISTRY, object.childBlueprints());
    }

    @Override
    public ArmatureComponentBlueprint makeObject() {
        return new ArmatureComponentBlueprint(
            armature, animatorScript.toFactory(), useRootMotion, localTransform.makeObject(), transformInheritPolicy,
            ComponentAttachableHolder.lowerComponents(components), isEnabled());
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
    /// `ArmatureComponentBlueprint.ByClass` / `ByDataPack`, the root-motion flag, the transform's
    /// [TransformHolder#sourceTokens] and the inherit policy's constant name. A script whose id resolves nowhere emits an empty `script_id`.
    @Override
    public Map<String, Object> getDTO() {
        Identifier armatureId = trimDirectory(armature.identifier());

        Map<String, Object> dto = new HashMap<>();
        dto.put("armature_namespace", armatureId.getNamespace());
        dto.put("armature_path", armatureId.getPath());
        dto.put("use_root_motion", useRootMotion);
        dto.put("transform", localTransform.sourceTokens());
        dto.put("transform_inherit_policy", transformInheritPolicy.name());

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
}
