package com.yesman.akytheralabs.akythera.ability;

import com.mojang.serialization.Codec;
import com.yesman.akythera.api.world.ability.AbilityEffect;
import com.yesman.akytheralabs.dataholder.AbstractDataHolder;
import com.yesman.akytheralabs.editor.EditorEditable;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/// Modifiable data holder for [AbilityEffect]. The fields are the editor's properties panel; the
/// record is rebuilt from them on [#makeObject].
///
/// The modifier list has no widget yet, so it round-trips untouched.
public final class AbilityEffectHolder extends AbstractDataHolder<AbilityEffect> {
    public static final Codec<AbilityEffectHolder> EDITOR_CODEC =
        AbilityEffect.CODEC.xmap(
            effect -> {
                AbilityEffectHolder holder = new AbilityEffectHolder();
                holder.readFrom(effect);

                return holder;
            },
            AbilityEffectHolder::makeObject
        );

    /// Names the effect, and is the stem of every attribute modifier it applies.
    @EditorEditable
    private String id = "akythera:new_effect";

    @EditorEditable
    private AbilityEffect.ApplicationType applicationType = AbilityEffect.ApplicationType.INSTANT;

    /// Ticks one application lasts, or the recharge period when the effect is used as a cooldown.
    @EditorEditable
    private int duration;

    /// Charges a cooldown banks.
    @EditorEditable
    private int maxStack = 1;

    private List<AbilityEffect.Modifier<?>> modifiers = new ArrayList<>();

    @Override
    public void readFrom(final AbilityEffect object) {
        this.id = object.getId().toString();
        this.applicationType = object.getApplicationType();
        this.duration = object.getDuration();
        this.maxStack = object.getMaxStack();
        this.modifiers = new ArrayList<>(object.getModifiers());
    }

    @Override
    public AbilityEffect makeObject() {
        return new AbilityEffect(
            Identifier.parse(id), List.copyOf(modifiers), applicationType, duration, maxStack
        );
    }

    public String getId() {
        return id;
    }

    public AbilityEffect.ApplicationType getApplicationType() {
        return applicationType;
    }

    public int getDuration() {
        return duration;
    }

    public int getMaxStack() {
        return maxStack;
    }

    public List<AbilityEffect.Modifier<?>> getModifiers() {
        return modifiers;
    }
}
