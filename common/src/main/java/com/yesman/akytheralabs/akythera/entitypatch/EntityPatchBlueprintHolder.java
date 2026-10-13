package com.yesman.akytheralabs.akythera.entitypatch;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.data.modularize.AkytheraScriptFunctions;
import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;
import com.yesman.akythera.core.blueprint.DataDrivenEntityPatchBlueprint;
import com.yesman.akythera.core.blueprint.EntityPatchBlueprint;
import com.yesman.akythera.core.blueprint.SourceCodeEntityPatchBlueprint;
import com.yesman.akythera.core.blueprint.script.BlueprintBody;
import com.yesman.akythera.core.blueprint.script.EntityPatchScript;
import com.yesman.akythera.core.reflection.BlueprintLayout;
import com.yesman.akythera.core.reflection.ScriptModularizeHelper;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.NodeTreeSourceRenderer;
import com.yesman.akytheralabs.akythera.blueprint.BlueprintParentRef;
import com.yesman.akytheralabs.akythera.blueprint.BlueprintScriptData;
import com.yesman.akytheralabs.akythera.entitypatch.component.ArmatureComponentHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.ComponentAttachableHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.ComponentDataHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.EntityPatchComponentHolders;
import com.yesman.akytheralabs.akythera.entitypatch.component.RootAttachableComponent;
import com.yesman.akytheralabs.dataholder.AbstractDataHolder;
import com.yesman.akytheralabs.dataholder.DTOProvider;
import com.yesman.akytheralabs.dataholder.DataHolder;
import com.yesman.akytheralabs.dataholder.EditorReference;
import com.yesman.akytheralabs.dataholder.FailedComponentHolder;
import com.yesman.akytheralabs.gui.widget.script.ScriptGraphData;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.stream.Stream;
import net.minecraft.resources.Identifier;

/// Modifiable data holder for an [EntityPatchBlueprint] (registry
/// [AkytheraRegistries.DataPack#ENTITY_PATCH_KEY]).
///
/// The editor keeps the blueprint's graphs, property values and variables as one [BlueprintScriptData],
/// laid out by [EntityPatchScript]. [#makeObject] is the compile step: it lowers the data into a
/// [BlueprintBody] and assembles the runtime [EntityPatchBlueprint].
///
/// Components are editor holders such as [ArmatureComponentHolder], keyed by the author-chosen name the
/// engine attaches them under. The patch itself is the root [ComponentAttachableHolder], as [EntityPatch] is
/// the root [ComponentAttachable]. Their properties are edited in the Details panel; [#EDITOR_CODEC] persists
/// the whole tree through [ComponentAttachableHolder#componentsField], and [#makeObject] lowers each holder
/// into its runtime [ComponentBlueprint].
///
/// [#parent] is either an [EntityPatch] subclass, lowered into a [DataDrivenEntityPatchBlueprint], or a
/// source code script, lowered into a [SourceCodeEntityPatchBlueprint] that carries nothing else. Its
/// constructor's return type is [#ownerType], the script owner every canvas on this blueprint is typed against.
public final class EntityPatchBlueprintHolder extends AbstractDataHolder<EntityPatchBlueprint>
    implements DTOProvider, ComponentAttachableHolder
{
    public static final Codec<EntityPatchBlueprintHolder> EDITOR_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            AkytheraRegistries.Frozen.SCRIPT_FUNCTION.byNameCodec().optionalFieldOf("parent")
                .forGetter(h -> Optional.of(h.parent.ctor())),
            AkytheraRegistries.Frozen.SCRIPT_TYPE.byNameCodec().optionalFieldOf("source_code_script")
                .forGetter(h -> h.parent instanceof BlueprintParentRef.SourceCodeScript sourceCode
                    ? Optional.of(sourceCode.script())
                    : Optional.empty()),
            ComponentAttachableHolder.componentsField(() -> EntityPatchComponentHolders.REGISTRY)
                .forGetter(ComponentAttachableHolder::componentsOf),
            BlueprintScriptData.MAP_CODEC.forGetter(h -> h.script),
            Identifier.CODEC.optionalFieldOf("preview_entity_type")
                .forGetter(ParseUtils.provideOptional(EntityPatchBlueprintHolder::getPreviewEntityType))
        ).apply(instance, (ctor, sourceCodeScript, components, scriptData, pm) -> {
            EntityPatchBlueprintHolder h = new EntityPatchBlueprintHolder();
            ScriptFunction parentCtor = ctor.orElseGet(EntityPatchBlueprintHolder::defaultCtor);
            h.parent = sourceCodeScript
                .<BlueprintParentRef>map(script -> new BlueprintParentRef.SourceCodeScript(parentCtor, script))
                .orElseGet(() -> new BlueprintParentRef.ParentClass(parentCtor));
            ComponentAttachableHolder.applyComponents(h, components);
            h.script = scriptData;
            pm.ifPresent(h::setPreviewEntityType);
            return h;
        })
    );

    /// What the engine builds this patch with. Its constructor's return type is the patch's class, and
    /// so the script owner type of every flow on it.
    private BlueprintParentRef parent = new BlueprintParentRef.ParentClass(defaultCtor());

    /// The attached components' editor holders, keyed by the name the engine attaches them under and
    /// insertion-ordered so the file is stable. Components that host children keep them on
    /// themselves, making this the root of the whole tree.
    private SequencedMap<String, ComponentDataHolder<?>> components = ComponentAttachableHolder.newComponentMap();

    private BlueprintScriptData script = new BlueprintScriptData();

    @Nullable
    private Identifier previewEntityType;

    public EntityPatchBlueprintHolder() {
    }

    /// ⚠ Requires the script-function registry to be filled (`Akythera.onModInitialized`).
    private static ScriptFunction defaultCtor() {
        return AkytheraScriptFunctions.ENTITY_PATCH_CONSTRUCTOR.value();
    }

    public BlueprintParentRef getParent() {
        return parent;
    }

    public void setParent(BlueprintParentRef parent) {
        this.parent = parent;
    }

    /// The patch's own class — what `self` is, and what a member [ScriptFunction]'s receiver slot has
    /// to accept for the function to be callable here.
    public Class<?> ownerType() {
        return parent.ctor().functionSignature().returnType();
    }

    /// The members [#ownerType] lays the editor out with.
    public BlueprintLayout layout() {
        return Objects.requireNonNull(ScriptModularizeHelper.blueprintLayoutOf(ownerType()));
    }

    /// The patch is the root component host: anything marked [RootAttachableComponent] may be
    /// attached directly on it, and everything else only under a component that names it.
    @Override
    public List<Class<?>> attachableTypes() {
        return List.of(RootAttachableComponent.class);
    }

    @Override
    public void readFrom(EntityPatchBlueprint object) {
        switch (object) {
            // The runtime blueprint carries no on-canvas layout, so only the values round-trip.
            case DataDrivenEntityPatchBlueprint dataDriven -> {
                parent = new BlueprintParentRef.ParentClass(dataDriven.ctor());
                script = new BlueprintScriptData(
                    ScriptGraphData.EMPTY, Map.of(), List.of(), dataDriven.body().properties(),
                    dataDriven.body().variables().stream().map(BlueprintScriptData.Variable::of).toList());
            }
            case SourceCodeEntityPatchBlueprint sourceCode ->
                parent = new BlueprintParentRef.SourceCodeScript(sourceCode.ctor(), sourceCode.script());
        }
    }

    @Override
    public EntityPatchBlueprint makeObject() {
        return switch (parent) {
            case BlueprintParentRef.ParentClass patchClass -> new DataDrivenEntityPatchBlueprint(
                ComponentAttachableHolder.lowerComponents(components), patchClass.ctor(),
                script.compile(layout(), ownerType()));
            case BlueprintParentRef.SourceCodeScript sourceCode ->
                new SourceCodeEntityPatchBlueprint(sourceCode.ctor(), sourceCode.script());
        };
    }

    /// Everything the attached components reference — armature accessors, animator script holders
    /// and so on — plus every asset the canvases' nodes bind. Flattens each component holder's
    /// own [DataHolder#referencedReferences], attached children included.
    @Override
    public Stream<EditorReference> referencedReferences() {
        return Stream.concat(
            ComponentAttachableHolder.flatten(this).flatMap(DataHolder::referencedReferences),
            script.referencedReferences()
        );
    }

    /// The first failure the decode guard salvaged: a defaults-fallback or unknown-type component
    /// placeholder, or an unresolved node kept on a canvas.
    @Override
    public @Nullable Exception getNestedFailureException() {
        Exception component = AbstractDataHolder.firstNestedFailure(components.values());
        if (component != null) return component;

        return script.firstFailure();
    }

    /// Tokens for `entity_patch_blueprint_template.ftl`, as Java constructor expressions from
    /// [NodeTreeSourceRenderer] lowered through [#makeObject]: the parent constructor, plus either the
    /// source code script (`source_code_script`) or the [BlueprintBody] and the attached components'
    /// constructor expressions. The indent base, 4, matches the template's splice point.
    @Override
    public Map<String, Object> getDTO() {
        EntityPatchBlueprint blueprint = makeObject();

        Map<String, Object> dto = new HashMap<>();
        dto.put("parent", NodeTreeSourceRenderer.scriptFunction(blueprint.ctor()));

        switch (blueprint) {
            case SourceCodeEntityPatchBlueprint sourceCode ->
                dto.put("source_code_script", NodeTreeSourceRenderer.scriptType(sourceCode.script()));
            case DataDrivenEntityPatchBlueprint dataDriven -> {
                dto.put("components", ComponentDataHolder.sourcesOf(healthyComponents(), false));
                dto.put("body", NodeTreeSourceRenderer.body(dataDriven.body(), 4));
            }
        }

        return dto;
    }

    // ***************************************************************
    // Components (editor holders keyed by attachment name)
    // ***************************************************************
    /// The component map minus unknown-type placeholders. A [FailedComponentHolder] has no source
    /// form and round-trips only through its raw JSON.
    private SequencedMap<String, ComponentDataHolder<?>> healthyComponents() {
        SequencedMap<String, ComponentDataHolder<?>> healthy = ComponentAttachableHolder.newComponentMap();
        components.forEach((name, c) -> {
            if (!(c instanceof FailedComponentHolder)) healthy.put(name, c);
        });
        return healthy;
    }

    @Override
    public SequencedMap<String, ComponentDataHolder<?>> getComponents() {
        return components;
    }

    public void setComponents(Map<String, ComponentDataHolder<?>> components) {
        this.components = new LinkedHashMap<>(components);
    }

    // ***************************************************************
    // Graphs, properties and variables (flat editor form)
    // ***************************************************************
    public BlueprintScriptData getScript() {
        return script;
    }

    // ***************************************************************
    // Preview model (editor-only)
    // ***************************************************************
    public @Nullable Identifier getPreviewEntityType() {
        return previewEntityType;
    }

    public void setPreviewEntityType(@Nullable Identifier previewEntityType) {
        this.previewEntityType = previewEntityType;
    }
}