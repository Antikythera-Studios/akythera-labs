package com.yesman.akytheralabs.akythera.itempatch.component;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.api.world.patch.entity.LivingEntityPatch;
import com.yesman.akythera.api.world.patch.item.ItemPatch;
import com.yesman.akythera.api.world.patch.item.WeaponStance;
import com.yesman.akythera.api.world.patch.item.component.WeaponStanceVariationAdapter;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.NodeTreeSourceRenderer;
import com.yesman.akytheralabs.akythera.animation.ConditionGraphCompiler;
import com.yesman.akytheralabs.akythera.entitypatch.component.ComponentAttachableHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.ComponentDataHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.RootAttachableComponent;
import com.yesman.akytheralabs.akythera.itempatch.ItemPatchBlueprintHolder;
import com.yesman.akytheralabs.dataholder.AbstractDataHolder;
import com.yesman.akytheralabs.dataholder.ComponentHolderRegistry;
import com.yesman.akytheralabs.dataholder.DataBlockHolder;
import com.yesman.akytheralabs.dataholder.DataHolder;
import com.yesman.akytheralabs.dataholder.EditorReference;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.editor.EditorNodeGraph;
import com.yesman.akytheralabs.editor.EditorWidgetFactory;
import com.yesman.akytheralabs.editor.NodeGraphTitleProvider;
import com.yesman.akytheralabs.gui.widget.script.ScriptGraphData;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SequencedMap;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/// Editor holder for one [WeaponStanceVariationAdapter]: the stance it establishes, the condition
/// canvas that establishes it, and the components attached under it.
///
/// The component **names itself** `<stance>_adapter` ([#fixedName]) — the engine keys components by
/// name, so a self-derived name is what makes the stance unique among an item patch's adapters. A
/// fresh adapter opens on a free stance ([#createDefault]) and the stance row offers only the stances
/// no sibling adapter already holds ([#availableStances]), so the clash that naming would produce
/// cannot be authored in the first place.
///
/// Adapter order in the Components panel is **runtime priority order** (first matching condition
/// wins) within one item patch, and the main hand's patch is scanned before the off hand's. Nothing
/// here is the default: an item's default components hang off the patch itself, so an adapter with an
/// empty condition canvas simply never matches ([#compiledCondition]).
///
/// Both the stance and the condition are [EditorEditable#declarerOwned]: a patch overriding an
/// inherited adapter shows them grey and read-only, and only the components under the adapter are its
/// to declare.
public final class WeaponStanceVariationAdapterHolder extends ComponentDataHolder<WeaponStanceVariationAdapter>
    implements ComponentAttachableHolder, RootAttachableComponent, NodeGraphTitleProvider
{
    public static final MapCodec<WeaponStanceVariationAdapterHolder> EDITOR_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
            DataBlockHolder.blockCodec(WeaponStance.ENUM_MANAGER).fieldOf("stance").forGetter(h -> h.stance),
            ScriptGraphData.CODEC.optionalFieldOf("condition", ScriptGraphData.EMPTY).forGetter(h -> h.condition),
            ComponentAttachableHolder.componentsField(() -> ItemPatchComponentHolders.REGISTRY)
                .forGetter(ComponentAttachableHolder::componentsOf)
        ).apply(instance, (stance, condition, components) -> {
            WeaponStanceVariationAdapterHolder holder = new WeaponStanceVariationAdapterHolder(stance, condition);
            ComponentAttachableHolder.applyComponents(holder, components);
            return holder;
        })
    );

    /// Null only on the throwaway [ComponentHolderRegistry] builds to read this type's traits off
    /// while the project defines no stance — [#attachRefusal] turns the `+` away in that state.
    @EditorEditable(
        label = "gui.akytheralabs.item_patch_screen.row.stance",
        candidates = "availableStances",
        emptyCandidates = "gui.akytheralabs.item_patch_screen.empty.stances",
        order = 0,
        declarerOwned = true
    )
    private @Nullable WeaponStance stance;

    /// The boolean condition canvas — a raw [ScriptGraphData] field (the `AnimatorScriptHolder`
    /// storage convention) whose [EditorNodeGraph] declaration renders the "Edit Node Graph" button →
    /// center tab.
    @EditorEditable(label = "gui.akytheralabs.item_patch_screen.row.condition", order = 1, declarerOwned = true)
    @EditorNodeGraph(
        contextParams = { WeaponStanceVariationAdapter.EQUIPPER_PARAM, WeaponStanceVariationAdapter.SLOT_PARAM },
        contextParamTypes = { LivingEntityPatch.class, EquipmentSlot.class }
    )
    private ScriptGraphData condition;

    private final SequencedMap<String, ComponentDataHolder<?>> components = ComponentAttachableHolder.newComponentMap();

    private WeaponStanceVariationAdapterHolder(@Nullable WeaponStance stance, ScriptGraphData condition) {
        this.stance = stance;
        this.condition = condition;
    }

    /// The `+` button's fresh adapter, on the first stance no adapter under `host` already holds —
    /// two adapters of one stance would name themselves alike, which is what [#attachRefusal] turns
    /// the `+` away over once nothing is left. Null `host` leaves the surroundings unknown, so the
    /// first assignable stance stands in.
    public static WeaponStanceVariationAdapterHolder createDefault(@Nullable ComponentAttachableHolder host) {
        Set<WeaponStance> taken = host == null ? Set.of() : takenStances(host, null);

        return new WeaponStanceVariationAdapterHolder(
            assignableStances().stream().filter(candidate -> !taken.contains(candidate)).findFirst().orElse(null),
            ScriptGraphData.EMPTY
        );
    }

    /// Every stance an adapter may establish: everything the engine loaded plus everything this
    /// project defines.
    private static List<WeaponStance> assignableStances() {
        AkytheraProject project = AkytheraProject.getCurrentProject();

        return project.getMergedDataBlockIdentifiers(WeaponStance.ENUM_MANAGER).stream()
            .map(id -> project.getDataBlockHolder(WeaponStance.ENUM_MANAGER, id))
            .filter(Objects::nonNull)
            .map(Holder::value)
            .toList();
    }

    /// The stance, mirroring [WeaponStanceVariationAdapter#mergeKey].
    @Override
    public @Nullable Object mergeKey() {
        return stance;
    }

    /// The name this adapter is attached under: its stance's path plus `_adapter`. A space is not a
    /// legal component name character here, hence the snake case.
    @Override
    public String fixedName() {
        return stancePath(stance) + "_adapter";
    }

    /// The stances this adapter may take: every assignable stance no **sibling** adapter holds, plus
    /// the one it holds now (which [#takenStances] leaves out by excluding this adapter). Filled with
    /// the Details panel's context — the host in the component tree.
    List<WeaponStance> availableStances(ComponentAttachableHolder host) {
        Set<WeaponStance> taken = takenStances(host, this);

        return assignableStances().stream()
            .filter(candidate -> !taken.contains(candidate))
            .toList();
    }

    /// The stances the adapters under `host` hold — the ones attached to it and the ones it inherits
    /// — `except`, the adapter asking, aside.
    private static Set<WeaponStance> takenStances(
        ComponentAttachableHolder host, @Nullable WeaponStanceVariationAdapterHolder except
    ) {
        return Stream.concat(host.getComponents().values().stream(), inheritedComponentsOf(host))
            .filter(WeaponStanceVariationAdapterHolder.class::isInstance)
            .map(WeaponStanceVariationAdapterHolder.class::cast)
            .filter(sibling -> sibling != except)
            .map(sibling -> sibling.stance)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }

    /// What `host` inherits, for the one host that inherits anything — the patch itself.
    private static Stream<ComponentDataHolder<?>> inheritedComponentsOf(ComponentAttachableHolder host) {
        return host instanceof ItemPatchBlueprintHolder patch
            ? patch.inheritedComponents().values().stream()
            : Stream.empty();
    }

    /// This type's [ComponentHolderRegistry.AttachRule]: one more adapter is refused once every
    /// assignable stance is taken — by an adapter on this patch or by one it inherits, which is
    /// overridden rather than created a second time. With no stance to assign there is nothing to
    /// adapt to at all.
    public static @Nullable Component attachRefusal(ComponentAttachableHolder host) {
        List<WeaponStance> assignable = assignableStances();

        if (assignable.isEmpty()) {
            return Component.translatable("gui.akytheralabs.item_patch_screen.refusal.no_stances");
        }

        Set<WeaponStance> taken = takenStances(host, null);

        return assignable.stream().anyMatch(candidate -> !taken.contains(candidate))
            ? null
            : Component.translatable("gui.akytheralabs.item_patch_screen.refusal.stances_taken");
    }

    /// Written reflectively by [EditorWidgetFactory] when the stance row commits.
    public void setStance(WeaponStance stance) {
        this.stance = stance;
    }

    @Override
    public Component nodeGraphTabTitle(String fieldName, Component fallback) {
        return Component.literal(stancePath(stance) + " condition");
    }

    // ***************************************************************
    // ComponentAttachableHolder
    // ***************************************************************

    /// Any item patch component. The stance's data is whatever the author attaches here.
    @Override
    public List<Class<?>> attachableTypes() {
        return List.of(ComponentDataHolder.class);
    }

    /// Everything [#attachableTypes] admits except another adapter.
    /// [ItemPatch#getStanceAdapter] scans the patch's own components only, so a nested adapter would
    /// never be reached.
    @Override
    public boolean accepts(Class<?> holderClass) {
        return holderClass != WeaponStanceVariationAdapterHolder.class
            && ComponentAttachableHolder.super.accepts(holderClass);
    }

    @Override
    public SequencedMap<String, ComponentDataHolder<?>> getComponents() {
        return components;
    }

    // ***************************************************************
    // ComponentDataHolder
    // ***************************************************************

    @Override
    public void readFrom(WeaponStanceVariationAdapter object) {
        stance = object.getStance();

        // Runtime PureNode trees cannot be decompiled back into editor canvases (same limitation as
        // the entity-patch script surface): an imported adapter opens with an empty condition, which
        // never matches until the author rebuilds it.
        condition = ScriptGraphData.EMPTY;

        ComponentAttachableHolder.readComponents(this, ItemPatchComponentHolders.REGISTRY, object.getChildBlueprints());
    }

    @Override
    public WeaponStanceVariationAdapter makeObject() {
        return new WeaponStanceVariationAdapter(
            stance,
            compiledCondition(),
            ComponentAttachableHolder.lowerComponents(components),
            isEnabled()
        );
    }

    @Override
    public Stream<EditorReference> referencedReferences() {
        return ComponentAttachableHolder.flatten(this).flatMap(DataHolder::referencedReferences);
    }

    @Override
    public @Nullable Exception getNestedFailureException() {
        return AbstractDataHolder.firstNestedFailure(components.values());
    }

    /// [WeaponStanceVariationAdapter] is its own blueprint — it builds itself.
    @Override
    public Class<? extends PatchComponent> generateEventually() {
        return WeaponStanceVariationAdapter.class;
    }

    // ***************************************************************
    // Source export (File > Export > As Source Code)
    // ***************************************************************

    @Override
    public String sourceTemplate() {
        return "components/weapon_stance_variation_adapter.ftl";
    }

    /// Tokens for `components/weapon_stance_variation_adapter.ftl`. The condition renders through the
    /// compiled runtime tree ([NodeTreeSourceRenderer#pure]) — the same lowering [#makeObject]
    /// performs.
    @Override
    public Map<String, Object> getDTO() {
        return Map.of(
            "stance", stance == null ? "" : Objects.toString(stance.identifier(), ""),
            "condition", NodeTreeSourceRenderer.pure(compiledCondition(), 6)
        );
    }

    /// The canvas lowered to a runtime tree. An empty or unwired canvas resolves to a literal `false`,
    /// so an unauthored adapter never establishes its stance.
    private PureNode compiledCondition() {
        return ConditionGraphCompiler.compile(condition, ItemPatch.class, WeaponStanceVariationAdapter.EQUIPPER_PARAM);
    }

    /// The stance's identifier path — the adapter's name and its graph tab's title are both built from
    /// it. `unknown` for an unassigned stance, or one whose entry is gone.
    ///
    /// Read off the block itself rather than through [WeaponStance#ENUM_MANAGER], which knows only what
    /// the engine loaded: a stance this project defines carries its id live off its own
    /// [com.yesman.akytheralabs.dataholder.DataBlockHolder], and so answers here too.
    private static String stancePath(@Nullable WeaponStance stance) {
        Identifier id = stance == null ? null : stance.identifier();
        return id == null ? "unknown" : id.getPath();
    }
}
