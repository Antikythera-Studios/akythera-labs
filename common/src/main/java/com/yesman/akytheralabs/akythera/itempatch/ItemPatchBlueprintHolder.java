package com.yesman.akytheralabs.akythera.itempatch;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;
import com.yesman.akythera.core.blueprint.ItemPatchBlueprint;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.akythera.entitypatch.EntityPatchBlueprintHolder;
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
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.stream.Stream;

/// Modifiable data holder for an [ItemPatchBlueprint]
/// (registry [AkytheraRegistries.DataPack#ITEM_PATCH_KEY]).
///
/// The shape of [EntityPatchBlueprintHolder] without the script surface: an item patch has no flows
/// or variables, only the parent reference and the component list. [#parent] is a blueprint-level
/// editable field, shown in the Details panel while no component is focused.
///
/// [#readFrom] reconstructs the editor state as far as the component holders allow. A component
/// carrying compiled script nodes cannot decompile them back into a canvas and re-imports with that
/// graph empty.
public final class ItemPatchBlueprintHolder extends AbstractDataHolder<ItemPatchBlueprint>
    implements DTOProvider, ComponentAttachableHolder
{
    public static final Codec<ItemPatchBlueprintHolder> EDITOR_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            ResourceKey.codec(AkytheraRegistries.DataPack.ITEM_PATCH_KEY).optionalFieldOf("parent")
                .forGetter(h -> h.parent.identifier().equals(DataHolder.EMPTY_REFERENCE)
                    ? Optional.<ResourceKey<ItemPatchBlueprint>>empty() : Optional.of(h.parent)),
            ComponentAttachableHolder.componentsField(() -> ItemPatchComponentHolders.REGISTRY)
                .forGetter(ComponentAttachableHolder::componentsOf)
        ).apply(instance, (parent, components) -> {
            ItemPatchBlueprintHolder h = new ItemPatchBlueprintHolder();
            parent.ifPresent(h::setParent);
            ComponentAttachableHolder.applyComponents(h, components);
            return h;
        })
    );

    /// The parent blueprint reference. [DataHolder#EMPTY_REFERENCE] means no parent and displays as
    /// `<none>`. A cycle is caught by the engine's hierarchy walk at instantiate, not here.
    @EditorEditable(label = "gui.akytheralabs.item_patch_screen.row.parent")
    private ResourceKey<ItemPatchBlueprint> parent = emptyParent();

    /// The attached components' editor holders, keyed by the name the engine attaches them under and
    /// insertion-ordered so the file is stable.
    private SequencedMap<String, ComponentDataHolder<?>> components = ComponentAttachableHolder.newComponentMap();

    public ItemPatchBlueprintHolder() {
    }

    private static ResourceKey<ItemPatchBlueprint> emptyParent() {
        return ResourceKey.create(AkytheraRegistries.DataPack.ITEM_PATCH_KEY, DataHolder.EMPTY_REFERENCE);
    }

    /// The patch is the root component host, see [EntityPatchBlueprintHolder#attachableTypes].
    @Override
    public List<Class<?>> attachableTypes() {
        return List.of(RootAttachableComponent.class);
    }

    @Override
    public void readFrom(ItemPatchBlueprint object) {
        parent = object.parent().orElse(emptyParent());
        components.clear();

        object.components().forEach((name, blueprint) -> {
            Identifier typeId = AkytheraRegistries.Frozen.COMPONENT_BLUEPRINT_TYPES.getKey(blueprint.mapCodec());
            if (typeId == null) return;

            ComponentDataHolder<?> holder = ItemPatchComponentHolders.REGISTRY.createDefault(typeId);
            if (holder == null) return;

            readInto(holder, blueprint);
            components.put(name, holder);
        });
    }

    /// The runtime component records are their own blueprints, so a holder's `readFrom` accepts them
    /// directly. The cast recovers what the wildcard erased.
    @SuppressWarnings("unchecked")
    private static <T> void readInto(AbstractDataHolder<T> holder, ComponentBlueprint<?, ?> blueprint) {
        holder.readFrom((T) blueprint);
    }

    @Override
    public ItemPatchBlueprint makeObject() {
        Optional<ResourceKey<ItemPatchBlueprint>> parentOpt =
            parent.identifier().equals(DataHolder.EMPTY_REFERENCE) ? Optional.empty() : Optional.of(parent);

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
            dto.put("parent_id", parent.identifier().toString());
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
}
