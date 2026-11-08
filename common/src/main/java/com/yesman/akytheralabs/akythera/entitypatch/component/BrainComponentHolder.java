package com.yesman.akytheralabs.akythera.entitypatch.component;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.world.behavior.BrainComponent;
import com.yesman.akythera.core.blueprint.behavior.BehaviorTreeBlueprint;
import com.yesman.akythera.core.blueprint.behavior.BrainComponentBlueprint;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.dataholder.DataHolder;
import com.yesman.akytheralabs.dataholder.EditorReference;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/// Editor holder for an `akythera:brain` ([BrainComponentBlueprint]) — the behavior tree the entity starts with and
/// the entity type tags it treats as hostile or friendly before its vanilla goals are asked.
public final class BrainComponentHolder extends ComponentDataHolder<BrainComponentBlueprint> implements RootAttachableComponent {
    public static final MapCodec<BrainComponentHolder> EDITOR_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
            ResourceKey.codec(AkytheraRegistries.DataPack.BEHAVIOR_TREE_KEY).optionalFieldOf("behavior_tree")
                .forGetter(h -> h.behaviorTree.unwrapKey().filter(key -> !key.identifier().equals(DataHolder.EMPTY_REFERENCE))),
            TagKey.codec(Registries.ENTITY_TYPE).listOf().optionalFieldOf("hostile_types", List.of()).forGetter(h -> h.hostileTypes),
            TagKey.codec(Registries.ENTITY_TYPE).listOf().optionalFieldOf("friendly_types", List.of()).forGetter(h -> h.friendlyTypes)
        ).apply(instance, (tree, hostile, friendly) -> {
            BrainComponentHolder h = new BrainComponentHolder();
            tree.ifPresent(key -> h.behaviorTree = AkytheraProject.getCurrentProject().getEntryHolderOrDangling(key));
            h.hostileTypes.addAll(hostile);
            h.friendlyTypes.addAll(friendly);
            return h;
        })
    );

    /// The tree the brain starts with.
    @EditorEditable(label = LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_BEHAVIOR_TREE, order = 0)
    private Holder<BehaviorTreeBlueprint> behaviorTree = AkytheraProject.getCurrentProject().getEntryHolderOrDangling(
        ResourceKey.create(AkytheraRegistries.DataPack.BEHAVIOR_TREE_KEY, DataHolder.EMPTY_REFERENCE));

    /// Entity type tags treated as hostile. A tag keyed [DataHolder#EMPTY_REFERENCE] is one not picked yet.
    @EditorEditable(label = LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_HOSTILE_TYPES, order = 1, defaultElement = "unpickedTag")
    private final List<TagKey<EntityType<?>>> hostileTypes = new ArrayList<>();

    /// Entity type tags treated as friendly. A tag keyed [DataHolder#EMPTY_REFERENCE] is one not picked yet.
    @EditorEditable(label = LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_FRIENDLY_TYPES, order = 2, defaultElement = "unpickedTag")
    private final List<TagKey<EntityType<?>>> friendlyTypes = new ArrayList<>();

    public static BrainComponentHolder createDefault() {
        return new BrainComponentHolder();
    }

    /// [#hostileTypes] and [#friendlyTypes]' [EditorEditable#defaultElement] source.
    private static TagKey<EntityType<?>> unpickedTag() {
        return TagKey.create(Registries.ENTITY_TYPE, DataHolder.EMPTY_REFERENCE);
    }

    @Override
    public void readFrom(final BrainComponentBlueprint object) {
        object.behaviorTree().ifPresent(key -> behaviorTree = AkytheraProject.getCurrentProject().getEntryHolderOrDangling(key));
        hostileTypes.clear();
        hostileTypes.addAll(object.hostileTypes());
        friendlyTypes.clear();
        friendlyTypes.addAll(object.friendlyTypes());
    }

    @Override
    public BrainComponentBlueprint makeObject() {
        return new BrainComponentBlueprint(
            behaviorTree.unwrapKey().filter(key -> !key.identifier().equals(DataHolder.EMPTY_REFERENCE)),
            picked(hostileTypes), picked(friendlyTypes), isEnabled()
        );
    }

    @Override
    public Stream<EditorReference> referencedReferences() {
        return behaviorTree instanceof EditorReference reference ? Stream.of(reference) : Stream.empty();
    }

    @Override
    public Class<? extends PatchComponent> generateEventually() {
        return BrainComponent.class;
    }

    /// `tags` without the ones not picked yet.
    private static List<TagKey<EntityType<?>>> picked(final List<TagKey<EntityType<?>>> tags) {
        return tags.stream().filter(tag -> !tag.location().equals(DataHolder.EMPTY_REFERENCE)).toList();
    }

    // ***************************************************************
    // Source export (File > Export > As Source Code)
    // ***************************************************************

    @Override
    public String sourceTemplate() {
        return "components/brain.ftl";
    }

    /// Tokens for `components/brain.ftl`: the tree's id or an empty string, and the picked tag ids.
    @Override
    public Map<String, Object> getDTO() {
        BrainComponentBlueprint blueprint = makeObject();

        return Map.of(
            "behavior_tree", blueprint.behaviorTree().map(key -> key.identifier().toString()).orElse(""),
            "hostile_types", blueprint.hostileTypes().stream().map(tag -> tag.location().toString()).toList(),
            "friendly_types", blueprint.friendlyTypes().stream().map(tag -> tag.location().toString()).toList()
        );
    }
}
