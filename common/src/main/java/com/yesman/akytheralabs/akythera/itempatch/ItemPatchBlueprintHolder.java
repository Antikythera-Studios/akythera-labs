package com.yesman.akytheralabs.akythera.itempatch;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.core.blueprint.ItemPatchBlueprint;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.dataholder.AbstractDataHolder;
import com.yesman.akytheralabs.dataholder.DTOProvider;
import com.yesman.akytheralabs.dataholder.DataHolder;
import com.yesman.akytheralabs.dataholder.EditorReference;
import com.yesman.akytheralabs.dataholder.FailedComponentHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.ComponentAttachableHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.ComponentDataHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.RootAttachableComponent;
import com.yesman.akytheralabs.akythera.itempatch.component.ItemPatchComponentHolders;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.gui.panel.main.processors.ResourceProcessor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
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
/// An item patch has no flows or variables, only the parent reference and the component list.
/// [#parent] is a blueprint-level editable field, shown in the Details panel while no component is
/// focused.
///
/// [#readFrom] reconstructs the editor state as far as the component holders allow. A component
/// carrying compiled script nodes cannot decompile them back into a canvas and re-imports with that
/// graph empty.
public final class ItemPatchBlueprintHolder extends AbstractDataHolder<ItemPatchBlueprint>
    implements DTOProvider, ComponentAttachableHolder
{
    /// The parent reference as it is stored: the id the engine resolves the entry under, which is not
    /// the one this editor keys it by — `minecraft:sword` on disk, `<project>:minecraft/sword` in
    /// memory. [ItemPatchBlueprint#assembleComponents] walks the chain through the registry, so the
    /// stored form is the only one that resolves outside the editor.
    private static final Codec<ResourceKey<ItemPatchBlueprint>> PARENT_CODEC =
        ResourceKey.codec(AkytheraRegistries.DataPack.ITEM_PATCH_KEY)
            .xmap(ItemPatchBlueprintHolder::projectKey, ItemPatchBlueprintHolder::exportedKey);

    public static final Codec<ItemPatchBlueprintHolder> EDITOR_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            PARENT_CODEC.optionalFieldOf("parent")
                .forGetter(h -> h.parent.identifier().equals(DataHolder.EMPTY_REFERENCE)
                    ? Optional.<ResourceKey<ItemPatchBlueprint>>empty() : Optional.of(h.parent)),
            ComponentAttachableHolder.componentsField(() -> ItemPatchComponentHolders.REGISTRY)
                .forGetter(ComponentAttachableHolder::componentsOf),
            BuiltInRegistries.ITEM.byNameCodec().optionalFieldOf("preview_item")
                .forGetter(ParseUtils.provideOptional(ItemPatchBlueprintHolder::getPreviewItem))
        ).apply(instance, (parent, components, previewItem) -> {
            ItemPatchBlueprintHolder h = new ItemPatchBlueprintHolder();
            parent.ifPresent(h::setParent);
            ComponentAttachableHolder.applyComponents(h, components);
            previewItem.ifPresent(h::setPreviewItem);
            return h;
        })
    );

    /// The parent blueprint reference. [DataHolder#EMPTY_REFERENCE] means no parent and displays as
    /// `<none>`.
    @EditorEditable(label = "gui.akytheralabs.item_patch_screen.row.parent")
    private ResourceKey<ItemPatchBlueprint> parent = emptyParent();

    /// The attached components' editor holders, keyed by the name the engine attaches them under and
    /// insertion-ordered so the file is stable.
    private SequencedMap<String, ComponentDataHolder<?>> components = ComponentAttachableHolder.newComponentMap();

    /// The item the editor preview draws this patch on. Only a **meta** patch reads it — an
    /// item-bound patch takes its item off the entry id. Editor-only: no engine counterpart.
    @Nullable
    private Item previewItem;

    /// [#inheritedComponents]' last answer.
    private @Nullable SequencedMap<String, ComponentDataHolder<?>> inheritedComponents;

    /// The [#parent] [#inheritedComponents] was folded from.
    private ResourceKey<ItemPatchBlueprint> inheritedFrom = emptyParent();

    public ItemPatchBlueprintHolder() {
    }

    private static ResourceKey<ItemPatchBlueprint> emptyParent() {
        return ResourceKey.create(AkytheraRegistries.DataPack.ITEM_PATCH_KEY, DataHolder.EMPTY_REFERENCE);
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

    @Override
    public void readFrom(ItemPatchBlueprint object) {
        parent = object.parent().map(ItemPatchBlueprintHolder::projectKey).orElse(emptyParent());
        ComponentAttachableHolder.readComponents(this, ItemPatchComponentHolders.REGISTRY, object.components());
    }

    @Override
    public ItemPatchBlueprint makeObject() {
        Optional<ResourceKey<ItemPatchBlueprint>> parentOpt =
            parent.identifier().equals(DataHolder.EMPTY_REFERENCE) ? Optional.empty() : Optional.of(exportedKey(parent));

        return new ItemPatchBlueprint(parentOpt, ComponentAttachableHolder.lowerComponents(components));
    }

    @Override
    public Stream<EditorReference> referencedReferences() {
        return ComponentAttachableHolder.flatten(this).flatMap(DataHolder::referencedReferences);
    }

    /// The first component the decode guard salvaged — a defaults-fallback or unknown-type
    /// placeholder — if any.
    @Override
    public @Nullable Exception getNestedFailureException() {
        return AbstractDataHolder.firstNestedFailure(components.values());
    }

    /// Tokens for `item_patch_blueprint_template.ftl`: `parent_id` when a parent is set, which the
    /// template lowers to `Optional.of(...)` against `Optional.empty()` for its absence, and the
    /// components' constructor expressions.
    @Override
    public Map<String, Object> getDTO() {
        Map<String, Object> dto = new HashMap<>();

        if (!parent.identifier().equals(DataHolder.EMPTY_REFERENCE)) {
            dto.put("parent_id", ResourceProcessor.exportedId(parent).toString());
        }

        dto.put("components", ComponentDataHolder.sourcesOf(healthyComponents()));

        return dto;
    }

    public ResourceKey<ItemPatchBlueprint> getParent() {
        return parent;
    }

    public void setParent(ResourceKey<ItemPatchBlueprint> parent) {
        this.parent = parent;
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

        while (!current.parent.identifier().equals(DataHolder.EMPTY_REFERENCE) && visited.add(current.parent.identifier())) {
            ItemPatchBlueprintHolder resolved = AkytheraProject.getCurrentProject()
                .<ItemPatchBlueprint, ItemPatchBlueprintHolder>getDataHolder(current.parent)
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
    // Preview item (editor-only)
    // ***************************************************************
    public @Nullable Item getPreviewItem() {
        return previewItem;
    }

    public void setPreviewItem(@Nullable Item previewItem) {
        this.previewItem = previewItem;
    }
}
