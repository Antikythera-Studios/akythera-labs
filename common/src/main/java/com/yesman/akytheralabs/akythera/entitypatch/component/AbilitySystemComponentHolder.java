package com.yesman.akytheralabs.akythera.entitypatch.component;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.world.ability.AbilitySlot;
import com.yesman.akythera.api.world.ability.AbilitySystemComponent;
import com.yesman.akythera.core.blueprint.AbilityBlueprint;
import com.yesman.akythera.core.blueprint.AbilitySystemComponentBlueprint;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.ScriptNodeSourceCompiler;
import com.yesman.akytheralabs.dataholder.DataBlockHolder;
import com.yesman.akytheralabs.dataholder.DataHolder;
import com.yesman.akytheralabs.dataholder.EditorReference;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/// Editor holder for an `akythera:ability_system` ([AbilitySystemComponentBlueprint]) — the abilities
/// an entity holds, starting with the loadout listed here.
///
/// The loadout is a list of (slot, ability) rows, one row per slot: the `+` button and the slot combo
/// offer only the slots no other row holds. A row whose ability is still unpicked lowers to nothing.
public final class AbilitySystemComponentHolder extends ComponentDataHolder<AbilitySystemComponentBlueprint>
    implements RootAttachableComponent
{
    public static final MapCodec<AbilitySystemComponentHolder> EDITOR_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
            Codec.mapPair(
                    DataBlockHolder.blockCodec(AbilitySlot.ENUM_MANAGER).fieldOf("slot"),
                    ResourceKey.codec(AkytheraRegistries.DataPack.ABILITY_KEY).fieldOf("ability")
                )
                .codec().listOf().optionalFieldOf("loadout", List.of())
                .forGetter(h -> h.entries.stream().map(d -> Pair.of(d.slot, d.ability.unwrapKey().orElseThrow())).toList())
        ).apply(instance, rows -> {
            AbilitySystemComponentHolder h = new AbilitySystemComponentHolder();
            rows.forEach(row -> h.entries.add(new EntryHolder(h.entries, row.getFirst(), row.getSecond())));
            return h;
        })
    );

    /// The live loadout rows, mutated in place by the Details panel's list row.
    @EditorEditable(
        label = LangKeys.GUI_ENTITY_PATCH_SCREEN_SECTION_LOADOUT,
        defaultElement = "defaultEntry",
        emptyCandidates = LangKeys.GUI_ENTITY_PATCH_SCREEN_EMPTY_ABILITY_SLOTS,
        inheritable = true
    )
    private final List<EntryHolder> entries = new ArrayList<>();

    public static AbilitySystemComponentHolder createDefault() {
        return new AbilitySystemComponentHolder();
    }

    /// The `+` button's fresh row, see [EditorEditable#defaultElement]: the first slot `loadout` leaves
    /// free, holding no ability yet. Null while every slot is taken.
    static @Nullable EntryHolder defaultEntry(List<EntryHolder> loadout) {
        List<AbilitySlot> free = freeSlots(loadout, null);

        if (free.isEmpty()) return null;

        return new EntryHolder(
            loadout,
            free.getFirst(),
            ResourceKey.create(AkytheraRegistries.DataPack.ABILITY_KEY, DataHolder.EMPTY_REFERENCE)
        );
    }

    /// The project's ability slots that no row of `loadout` other than `except` holds.
    private static List<AbilitySlot> freeSlots(List<EntryHolder> loadout, @Nullable EntryHolder except) {
        AkytheraProject project = AkytheraProject.getCurrentProject();
        Set<AbilitySlot> taken = loadout.stream()
            .filter(d -> d != except)
            .map(d -> d.slot)
            .collect(Collectors.toSet());
        List<AbilitySlot> free = new ArrayList<>();

        for (Identifier id : project.getMergedDataBlockIdentifiers(AbilitySlot.ENUM_MANAGER)) {
            Holder<AbilitySlot> holder = project.getDataBlockHolder(AbilitySlot.ENUM_MANAGER, id);
            if (holder != null && holder.isBound() && !taken.contains(holder.value())) free.add(holder.value());
        }

        return free;
    }

    /// The shared ability holders the rows reference.
    @Override
    public Stream<EditorReference> referencedReferences() {
        return entries.stream()
            .map(d -> d.ability)
            .filter(h -> h instanceof EditorReference)
            .map(h -> (EditorReference) h);
    }

    @Override
    public void readFrom(AbilitySystemComponentBlueprint object) {
        entries.clear();
        object.loadout().forEach((slot, ability) -> entries.add(new EntryHolder(entries, slot, ability)));
    }

    @Override
    public AbilitySystemComponentBlueprint makeObject() {
        return new AbilitySystemComponentBlueprint(loadout(), isEnabled());
    }

    /// The rows as the blueprint's loadout, unpicked abilities left out.
    private Map<AbilitySlot, ResourceKey<AbilityBlueprint>> loadout() {
        Map<AbilitySlot, ResourceKey<AbilityBlueprint>> loadout = new LinkedHashMap<>();

        for (EntryHolder entry : entries) {
            ResourceKey<AbilityBlueprint> key = entry.ability.unwrapKey().orElseThrow();
            if (!key.identifier().equals(DataHolder.EMPTY_REFERENCE)) loadout.put(entry.slot, key);
        }

        return loadout;
    }

    @Override
    public Class<? extends PatchComponent> generateEventually() {
        return AbilitySystemComponent.class;
    }

    // ***************************************************************
    // Source export (File > Export > As Source Code)
    // ***************************************************************

    @Override
    public String sourceTemplate() {
        return "components/ability_system.ftl";
    }

    /// Tokens for `components/ability_system.ftl`: one row per loadout entry, carrying the slot as a
    /// Java expression and the ability's registry id.
    @Override
    public Map<String, Object> getDTO() {
        List<Map<String, Object>> rows = loadout().entrySet().stream()
            .<Map<String, Object>>map(e -> Map.of(
                "slot", ScriptNodeSourceCompiler.valueLiteral(e.getKey()),
                "ability", e.getValue().identifier().toString()
            ))
            .toList();

        return Map.of("loadout", rows);
    }

    /// One loadout row: the slot (a combo over the slots still free) and the ability equipped there
    /// (project holder popup).
    public static final class EntryHolder {
        /// The loadout this row sits in.
        private final List<EntryHolder> loadout;

        @EditorEditable(
            label = LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_ABILITY_SLOT,
            candidates = "slotCandidates",
            emptyCandidates = LangKeys.GUI_ENTITY_PATCH_SCREEN_EMPTY_ABILITY_SLOTS,
            order = 0,
            inheritable = true
        )
        private AbilitySlot slot;

        @EditorEditable(label = LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_ABILITY, order = 1, inheritable = true)
        private Holder<AbilityBlueprint> ability;

        EntryHolder(List<EntryHolder> loadout, AbilitySlot slot, ResourceKey<AbilityBlueprint> ability) {
            this.loadout = loadout;
            this.slot = slot;
            this.ability = AkytheraProject.getCurrentProject().getEntryHolderOrDangling(ability);
        }

        /// The slots this row may take, see [EditorEditable#candidates]: its own and the free ones.
        List<AbilitySlot> slotCandidates() {
            return freeSlots(loadout, this);
        }
    }
}
