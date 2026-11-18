package com.yesman.akytheralabs.akythera.itempatch;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.api.world.patch.item.ItemPatch;
import com.yesman.akythera.core.blueprint.ItemPatchBlueprint;
import com.yesman.akythera.core.blueprint.script.DataDrivenItemPatchScript;
import com.yesman.akythera.core.blueprint.script.ItemPatchScript;
import com.yesman.akythera.core.reflection.BlueprintLayout;
import com.yesman.akythera.core.reflection.ScriptModularizeHelper;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.FreemarkerExporter;
import com.yesman.akytheralabs.NodeTreeSourceRenderer;
import com.yesman.akytheralabs.akythera.blueprint.BlueprintScriptData;
import com.yesman.akytheralabs.akythera.entitypatch.component.ComponentAttachableHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.ComponentDataHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.RootAttachableComponent;
import com.yesman.akytheralabs.akythera.itempatch.component.ItemPatchComponentHolders;
import com.yesman.akytheralabs.dataholder.AbstractDataHolder;
import com.yesman.akytheralabs.dataholder.DTOProvider;
import com.yesman.akytheralabs.dataholder.DataHolder;
import com.yesman.akytheralabs.dataholder.EditorHolderCodecs;
import com.yesman.akytheralabs.dataholder.EditorReference;
import com.yesman.akytheralabs.dataholder.FailedComponentHolder;
import com.yesman.akytheralabs.editor.EditorWidgetFactory;
import com.yesman.akytheralabs.gui.panel.main.processors.ResourceProcessor;
import com.yesman.akytheralabs.gui.panel.script.BlueprintEditorSession;
import com.yesman.akytheralabs.gui.widget.script.Compilation;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.Set;
import java.util.stream.Stream;

/// Modifiable data holder for an [ItemPatchBlueprint]
/// (registry [AkytheraRegistries.DataPack#ITEM_PATCH_KEY]).
///
/// An item patch holds the parent reference, the component list and the graphs of its [ItemPatchScript] events,
/// kept as one [BlueprintScriptData] with no member variable. [#parent] is a blueprint-level editable field,
/// shown in the Details panel while no component is focused. A patch laid over a Java class keeps the class in
/// [#sourceCodeScript]: its graphs run over the class's events, which run over the parent patch's.
///
/// [#readFrom] reconstructs the editor state as far as the component holders allow. Compiled script nodes
/// cannot decompile back into a canvas: a component carrying them re-imports with that graph empty, and so
/// does the Event Graph.
public final class ItemPatchBlueprintHolder extends AbstractDataHolder<ItemPatchBlueprint>
    implements DTOProvider, ComponentAttachableHolder
{
    /// The parent reference as it is stored: the id the engine resolves the entry under, which is not
    /// the one this editor keys it by — `minecraft:sword` on disk, `<project>:minecraft/sword` in
    /// memory. [ItemPatchBlueprint#assembleComponents] walks the chain through the registry, so the
    /// stored form is the only one that resolves outside the editor.
    private static final Codec<Holder<ItemPatchBlueprint>> PARENT_CODEC =
        ResourceKey.codec(AkytheraRegistries.DataPack.ITEM_PATCH_KEY)
            .xmap(ItemPatchBlueprintHolder::parentOf, holder -> exportedKey(holder.unwrapKey().orElseThrow()));

    public static final Codec<ItemPatchBlueprintHolder> EDITOR_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            PARENT_CODEC.optionalFieldOf("parent")
                .forGetter(h -> h.parent.is(DataHolder.EMPTY_REFERENCE) ? Optional.empty() : Optional.of(h.parent)),
            ComponentAttachableHolder.componentsField(() -> ItemPatchComponentHolders.REGISTRY)
                .forGetter(ComponentAttachableHolder::componentsOf),
            AkytheraRegistries.Frozen.SCRIPT_TYPE.byNameCodec().optionalFieldOf("source_code_script")
                .forGetter(ParseUtils.provideOptional(ItemPatchBlueprintHolder::getSourceCodeScript)),
            BlueprintScriptData.MAP_CODEC.forGetter(h -> h.script),
            BuiltInRegistries.ITEM.byNameCodec().optionalFieldOf("preview_item")
                .forGetter(ParseUtils.provideOptional(ItemPatchBlueprintHolder::getPreviewItem))
        ).apply(instance, (parent, components, sourceCodeScript, scriptData, previewItem) -> {
            ItemPatchBlueprintHolder h = new ItemPatchBlueprintHolder();
            parent.ifPresent(value -> h.parent = value);
            ComponentAttachableHolder.applyComponents(h, components);
            h.sourceCodeScript = sourceCodeScript.orElse(null);
            h.script = scriptData;
            h.syncParentScript();
            previewItem.ifPresent(h::setPreviewItem);
            return h;
        })
    );

    /// The parent blueprint reference, the shared live holder. [DataHolder#EMPTY_REFERENCE] means no
    /// parent.
    private Holder<ItemPatchBlueprint> parent = emptyParent();

    /// The attached components' editor holders, keyed by the name the engine attaches them under and
    /// insertion-ordered so the file is stable.
    private SequencedMap<String, ComponentDataHolder<?>> components = ComponentAttachableHolder.newComponentMap();

    private BlueprintScriptData script = new BlueprintScriptData();

    /// The [ItemPatchScript] class the graphs of [#script] are laid over, see [ItemPatchScript#runnable]. Null for
    /// none.
    private @Nullable ScriptType sourceCodeScript;

    /// The item the editor preview draws this patch on. Only a **meta** patch reads it — an
    /// item-bound patch takes its item off the entry id. Editor-only: no engine counterpart.
    @Nullable
    private Item previewItem;

    /// [#inheritedComponents]' last answer.
    private @Nullable SequencedMap<String, ComponentDataHolder<?>> inheritedComponents;

    /// The [#parent] [#inheritedComponents] was folded from.
    private Holder<ItemPatchBlueprint> inheritedFrom = emptyParent();

    public ItemPatchBlueprintHolder() {
    }

    /// The classes [#sourceCodeScript] may pick: every registered [ItemPatchScript#runnable] class but the data
    /// pack runner.
    public static List<ScriptType> scriptClassCandidates() {
        return AkytheraRegistries.Frozen.SCRIPT_TYPE.stream()
            .filter(type -> ItemPatchScript.runnable(type.type()) && type.type() != DataDrivenItemPatchScript.class)
            .sorted(Comparator.comparing(type -> type.type().getSimpleName()))
            .toList();
    }

    private static Holder<ItemPatchBlueprint> emptyParent() {
        return EditorHolderCodecs.resolve(AkytheraRegistries.DataPack.ITEM_PATCH_KEY, DataHolder.EMPTY_REFERENCE);
    }

    /// The shared live holder of the stored parent reference `key`.
    private static Holder<ItemPatchBlueprint> parentOf(ResourceKey<ItemPatchBlueprint> key) {
        return AkytheraProject.getCurrentProject().getEntryHolderOrDangling(projectKey(key));
    }

    /// `key` as this project keys the entry, see [ResourceProcessor#projectId].
    private static ResourceKey<ItemPatchBlueprint> projectKey(ResourceKey<ItemPatchBlueprint> key) {
        return ResourceKey.create(
            AkytheraRegistries.DataPack.ITEM_PATCH_KEY,
            ResourceProcessor.projectId(AkytheraRegistries.DataPack.ITEM_PATCH_KEY, key.identifier()));
    }

    /// `key` as the engine resolves it, see [ResourceProcessor#exportedId].
    private static ResourceKey<ItemPatchBlueprint> exportedKey(ResourceKey<ItemPatchBlueprint> key) {
        return ResourceKey.create(AkytheraRegistries.DataPack.ITEM_PATCH_KEY, ResourceProcessor.exportedId(key));
    }

    /// The patch is the root component host.
    @Override
    public List<Class<?>> attachableTypes() {
        return List.of(RootAttachableComponent.class);
    }

    /// The members the Event Graph is laid out with.
    public static BlueprintLayout layout() {
        return ScriptModularizeHelper.scanBlueprintLayout(ItemPatchScript.class);
    }

    @Override
    public void readFrom(ItemPatchBlueprint object) {
        parent = object.parent().map(ItemPatchBlueprintHolder::parentOf).orElseGet(ItemPatchBlueprintHolder::emptyParent);
        ComponentAttachableHolder.readComponents(this, ItemPatchComponentHolders.REGISTRY, object.components());
        script = new BlueprintScriptData();
        sourceCodeScript = switch (object.script()) {
            case ItemPatchBlueprint.ByClass byClass -> byClass.type();
            case ItemPatchBlueprint.ByData byData -> byData.parentScript().orElse(null);
        };
        syncParentScript();
    }

    @Override
    public ItemPatchBlueprint makeObject() {
        Optional<ResourceKey<ItemPatchBlueprint>> parentOpt =
            parent.is(DataHolder.EMPTY_REFERENCE) ? Optional.empty() : Optional.of(exportedKey(parent.unwrapKey().orElseThrow()));

        return new ItemPatchBlueprint(
            parentOpt,
            ComponentAttachableHolder.lowerComponents(components),
            new ItemPatchBlueprint.ByData(Optional.ofNullable(sourceCodeScript), script.compile(layout(), ItemPatch.class)));
    }

    /// Validates the Event Graph, the function canvases and the components' node graphs
    /// ([EditorWidgetFactory#compileNodeGraphs]), then builds the patch.
    @Override
    public void compile(final Compilation compilation) {
        BlueprintEditorSession.compileCanvases(compilation, script, layout(), ItemPatch.class, null);
        compilation.unresolved(script.failedNodes());
        EditorWidgetFactory.compileNodeGraphs(compilation, this, ItemPatch.class);
        compilation.build(this::makeObject);
    }

    @Override
    public Stream<EditorReference> referencedReferences() {
        return Stream.of(
            EditorHolderCodecs.referenceOf(parent),
            ComponentAttachableHolder.flatten(this).flatMap(DataHolder::referencedReferences),
            script.referencedReferences()
        ).flatMap(references -> references);
    }

    /// The first failure the decode guard salvaged: a defaults-fallback or unknown-type component
    /// placeholder, or an unresolved node kept on a canvas.
    @Override
    public @Nullable Exception getNestedFailureException() {
        Exception component = AbstractDataHolder.firstNestedFailure(components.values());
        if (component != null) return component;

        return script.firstFailure();
    }

    /// Tokens for `item_patch_blueprint_template.ftl`: `parent_id` when a parent is set, which the
    /// template lowers to `Optional.of(...)` against `Optional.empty()` for its absence, the
    /// components' constructor expressions, and `script` when a class runs the events: the source code script, or
    /// the compiled class ([FreemarkerExporter#compiledClass]) of a patch whose graphs author an event.
    @Override
    public Map<String, Object> getDTO() {
        Map<String, Object> dto = new HashMap<>();

        if (!parent.is(DataHolder.EMPTY_REFERENCE)) {
            dto.put("parent_id", ResourceProcessor.exportedId(parent.unwrapKey().orElseThrow()).toString());
        }

        dto.put("components", ComponentDataHolder.sourcesOf(healthyComponents(), false));

        String compiledClass = getOwnResourceKey() != null ? FreemarkerExporter.compiledClass(getOwnResourceKey()) : null;

        if (compiledClass != null) {
            dto.put("script", "ScriptType.of(" + compiledClass + ".class)");
        } else if (sourceCodeScript != null) {
            dto.put("script", NodeTreeSourceRenderer.scriptType(sourceCodeScript));
        }

        return dto;
    }

    public Holder<ItemPatchBlueprint> getParent() {
        return parent;
    }

    /// Sets the parent patch. A patch that had no parent script gets a Parent node behind every event entry that
    /// leads nowhere, see [BlueprintScriptData#seedParentCalls].
    public void setParent(Holder<ItemPatchBlueprint> parent) {
        boolean hadParentScript = script.hasParentScript();

        this.parent = parent;
        syncParentScript();

        if (!hadParentScript && script.hasParentScript()) script.seedParentCalls();
    }

    /// Tells [#script] what its graphs are laid over: [#sourceCodeScript], and the script of [#parent].
    private void syncParentScript() {
        script.setSourceParent(sourceCodeScript);
        script.setHolderParent(!parent.is(DataHolder.EMPTY_REFERENCE));
    }

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

    /// The components this patch inherits — its ancestors' folded, root first, its own excluded. Empty
    /// with no parent, and at the first ancestor the project cannot resolve: a dangling parent shows
    /// as nothing inherited rather than as a half tree.
    ///
    /// Held until [#parent] is re-pointed. One item patch editor is open at a time, so no ancestor's
    /// components can change while an answer stands.
    public SequencedMap<String, ComponentDataHolder<?>> inheritedComponents() {
        if (inheritedComponents != null && parent.equals(inheritedFrom)) {
            return inheritedComponents;
        }

        List<ItemPatchBlueprintHolder> ancestors = new ArrayList<>();
        Set<Identifier> visited = new HashSet<>();
        ItemPatchBlueprintHolder current = this;

        while (!current.parent.is(DataHolder.EMPTY_REFERENCE) && visited.add(EditorHolderCodecs.entryId(current.parent))) {
            ItemPatchBlueprintHolder resolved = AkytheraProject.getCurrentProject()
                .<ItemPatchBlueprint, ItemPatchBlueprintHolder>getDataHolder(current.parent.unwrapKey().orElseThrow())
                .orElse(null);

            if (resolved == null) break;

            ancestors.addFirst(resolved);
            current = resolved;
        }

        inheritedFrom = parent;
        inheritedComponents = ComponentAttachableHolder.foldChain(ancestors);

        return inheritedComponents;
    }

    // ***************************************************************
    // Graphs (flat editor form)
    // ***************************************************************
    public BlueprintScriptData getScript() {
        return script;
    }

    public @Nullable ScriptType getSourceCodeScript() {
        return sourceCodeScript;
    }

    /// Sets the script class. A patch that had no parent script gets a Parent node behind every event entry that
    /// leads nowhere, see [BlueprintScriptData#seedParentCalls].
    public void setSourceCodeScript(@Nullable ScriptType sourceCodeScript) {
        boolean hadParentScript = script.hasParentScript();

        this.sourceCodeScript = sourceCodeScript;
        syncParentScript();

        if (!hadParentScript && script.hasParentScript()) script.seedParentCalls();
    }

    // ***************************************************************
    // Preview item (editor-only)
    // ***************************************************************
    public @Nullable Item getPreviewItem() {
        return previewItem;
    }

    public void setPreviewItem(@Nullable Item previewItem) {
        this.previewItem = previewItem;
    }
}
