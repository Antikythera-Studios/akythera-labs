package com.yesman.akytheralabs.akythera.dataholder.datablock;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.world.ability.AbilitySlot;
import com.yesman.akythera.api.world.ability.AbilitySlotGroup;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.ScriptNodeSourceCompiler;
import com.yesman.akytheralabs.dataholder.DataBlockHolder;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.List;

/// [DataBlockHolder] for the `AbilitySlot` editable data block. The made object reads its id and
/// group **live** off this holder, like [WeaponStanceHolder].
///
/// A stored group that no longer resolves decodes as unset.
public final class AbilitySlotHolder extends DataBlockHolder<AbilitySlot> {
    public static final Codec<AbilitySlotHolder> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            DataBlockHolder.blockCodec(AbilitySlotGroup.ENUM_MANAGER).lenientOptionalFieldOf("group")
                .forGetter(ParseUtils.provideOptional(h -> h.group))
        ).apply(instance, group -> {
            AbilitySlotHolder holder = new AbilitySlotHolder();
            holder.group = group.orElse(null);
            return holder;
        })
    );

    /// The group this slot is listed under in UI. Null for a slot no UI lists.
    @EditorEditable(label = LangKeys.GUI_DATA_BLOCK_ROW_GROUP)
    private @Nullable AbilitySlotGroup group;

    @Override
    public void readFrom(final AbilitySlot object) {
        group = object.group();
    }

    @Override
    public List<SourceProperty> sourceProperties() {
        return List.of(new SourceProperty("AbilitySlotGroup", "group", "group", ScriptNodeSourceCompiler.valueLiteral(group)));
    }

    @Override
    protected AbilitySlot instantiate() {
        return new AbilitySlot() {
            @Override
            public int universalOrdinal() {
                return -1;
            }

            @Override
            public @Nullable AbilitySlotGroup group() {
                return group;
            }

            @Override
            public Identifier identifier() {
                return currentId();
            }

            @Override
            public String toString() {
                return currentId().toString();
            }
        };
    }
}
