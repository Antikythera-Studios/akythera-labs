package com.yesman.akytheralabs.akythera.itempatch.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.data.serializable.AttributeModifierEntry;
import com.yesman.akythera.api.world.patch.item.component.PatchAttributeModifiers;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import com.yesman.akytheralabs.dataholder.DataHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.ComponentDataHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.RootAttachableComponent;
import com.yesman.akytheralabs.editor.EditorEditable;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/// Editor holder for [PatchAttributeModifiers]: a list of modifier entries, edited through the
/// Details panel's list row. Each entry's fields are plain [EditorEditable] scalars — a ResourceKey
/// popup, a string box, a double box and an enum combo.
public final class PatchAttributeModifiersHolder extends ComponentDataHolder<PatchAttributeModifiers>
    implements RootAttachableComponent
{
    public static final MapCodec<PatchAttributeModifiersHolder> EDITOR_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
            EntryHolder.CODEC.listOf().optionalFieldOf("modifiers", List.of()).forGetter(h -> h.entries)
        ).apply(instance, entries -> {
            PatchAttributeModifiersHolder h = new PatchAttributeModifiersHolder();
            h.entries.addAll(entries);
            return h;
        })
    );

    /// The live entry list, a mutable [ArrayList] that add, remove and their undo write into
    /// directly.
    @EditorEditable(label = "gui.akytheralabs.item_patch_screen.section.modifiers", defaultElement = "defaultEntry")
    private final List<EntryHolder> entries = new ArrayList<>();

    public static PatchAttributeModifiersHolder createDefault() {
        return new PatchAttributeModifiersHolder();
    }

    /// The `+` button's fresh row, see [EditorEditable#defaultElement].
    static EntryHolder defaultEntry() {
        return new EntryHolder(
            ResourceKey.create(Registries.ATTRIBUTE, DataHolder.EMPTY_REFERENCE),
            "", 0.0D, AttributeModifier.Operation.ADD_VALUE
        );
    }

    @Override
    public void readFrom(PatchAttributeModifiers object) {
        entries.clear();
        for (AttributeModifierEntry entry : object.entries()) {
            entries.add(EntryHolder.from(entry));
        }
    }

    @Override
    public PatchAttributeModifiers makeObject() {
        Registry<Attribute> attributes = StaticRegistryAccess.getRegistryAccess().lookupOrThrow(Registries.ATTRIBUTE);

        List<AttributeModifierEntry> list = entries.stream()
            .map(d -> new AttributeModifierEntry(
                attributes.getOrThrow(d.attribute),
                new AttributeModifier(Identifier.parse(d.modifierId), d.amount, d.operation)
            ))
            .toList();

        return new PatchAttributeModifiers(list);
    }

    /// [PatchAttributeModifiers] is its own blueprint — it builds itself.
    @Override
    public Class<? extends PatchComponent> generateEventually() {
        return PatchAttributeModifiers.class;
    }

    /// ***************************************************************
    /// Source export (File > Export > As Source Code)
    /// ***************************************************************

    @Override
    public String sourceTemplate() {
        return "components/attribute_modifiers.ftl";
    }

    /// Tokens for `components/attribute_modifiers.ftl`: one row per entry — the attribute's
    /// registry id, the modifier's own id, the amount as a Java `double` literal, and the
    /// operation's enum constant name.
    @Override
    public Map<String, Object> getDTO() {
        List<Map<String, Object>> rows = entries.stream()
            .<Map<String, Object>>map(d -> Map.of(
                "attribute", d.attribute.identifier().toString(),
                "id", d.modifierId,
                "amount", d.amount + "D",
                "operation", d.operation.name()
            ))
            .toList();

        return Map.of("modifiers", rows);
    }

    /// One attribute modifier: the attribute's vanilla registry key, plus the modifier's own id,
    /// amount and operation. Built-in entries never rename, so the attribute is a key rather than a
    /// project holder.
    public static final class EntryHolder {
        static final Codec<EntryHolder> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                ResourceKey.codec(Registries.ATTRIBUTE).fieldOf("attribute").forGetter(d -> d.attribute),
                Codec.STRING.fieldOf("id").forGetter(d -> d.modifierId),
                Codec.DOUBLE.fieldOf("amount").forGetter(d -> d.amount),
                AttributeModifier.Operation.CODEC.fieldOf("operation").forGetter(d -> d.operation)
            ).apply(instance, EntryHolder::new)
        );

        @EditorEditable(label = "gui.akytheralabs.item_patch_screen.row.attribute", order = 0)
        private ResourceKey<Attribute> attribute;

        @EditorEditable(label = "gui.akytheralabs.item_patch_screen.row.modifier_id", order = 1)
        private String modifierId;

        @EditorEditable(label = "gui.akytheralabs.item_patch_screen.row.amount", order = 2)
        private double amount;

        @EditorEditable(label = "gui.akytheralabs.item_patch_screen.row.operation", order = 3)
        private AttributeModifier.Operation operation;

        EntryHolder(ResourceKey<Attribute> attribute, String modifierId, double amount, AttributeModifier.Operation operation) {
            this.attribute = attribute;
            this.modifierId = modifierId;
            this.amount = amount;
            this.operation = operation;
        }

        static EntryHolder from(AttributeModifierEntry entry) {
            return new EntryHolder(
                entry.attribute().unwrapKey().orElseThrow(),
                entry.modifier().id().toString(),
                entry.modifier().amount(),
                entry.modifier().operation()
            );
        }
    }
}
