package com.yesman.akytheralabs.akythera.dataholder.datablock;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.world.patch.item.WeaponStance;
import com.yesman.akytheralabs.dataholder.DataBlockHolder;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.editor.EditorWidgetFactory;
import net.minecraft.resources.Identifier;

import java.util.List;

/// [DataBlockHolder] for the `WeaponStance` editable data block. The made object reads both its id
/// and its offhand flag **live** off this holder, so a rekey or a property edit is reflected by the
/// same instance; `universalOrdinal()` is `-1` because the manager assigns the real ordinal at
/// runtime, when it loads the exported json.
public final class WeaponStanceHolder extends DataBlockHolder<WeaponStance> {
    public static final Codec<WeaponStanceHolder> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            Codec.BOOL.optionalFieldOf("offhand_free", false).forGetter(h -> h.offhandFree)
        ).apply(instance, offhandFree -> {
            WeaponStanceHolder holder = new WeaponStanceHolder();
            holder.offhandFree = offhandFree;
            return holder;
        })
    );

    @EditorEditable(label = "gui.akytheralabs.data_block.row.offhand_free")
    private boolean offhandFree;

    /// Written reflectively by [EditorWidgetFactory] when the checkbox row commits.
    public void setOffhandFree(boolean offhandFree) {
        this.offhandFree = offhandFree;
    }

    @Override
    public void readFrom(final WeaponStance object) {
        offhandFree = object.isOffhandFree();
    }

    @Override
    public List<SourceProperty> sourceProperties() {
        return List.of(new SourceProperty("boolean", "offhandFree", "isOffhandFree", Boolean.toString(offhandFree)));
    }

    @Override
    protected WeaponStance instantiate() {
        return new WeaponStance() {
            @Override
            public int universalOrdinal() {
                return -1;
            }

            @Override
            public boolean isOffhandFree() {
                return offhandFree;
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
