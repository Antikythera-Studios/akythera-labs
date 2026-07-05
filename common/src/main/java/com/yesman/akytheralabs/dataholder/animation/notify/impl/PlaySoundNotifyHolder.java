package com.yesman.akytheralabs.dataholder.animation.notify.impl;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.animation.notify.impl.PlaySoundNotify;
import com.yesman.akythera.core.util.LogicalSide;
import com.yesman.akytheralabs.dataholder.animation.notify.AnimationNotifyHolder;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import org.jspecify.annotations.NonNull;

import java.util.Map;

public final class PlaySoundNotifyHolder extends AnimationNotifyHolder<PlaySoundNotify> {
    public static final Codec<PlaySoundNotifyHolder> EDITOR_CODEC = RecordCodecBuilder.create(inst ->
        inst.group(
            SoundEvent.CODEC.fieldOf("sound").forGetter(h -> h.sound)
        ).apply(inst, sound -> {
            PlaySoundNotifyHolder h = new PlaySoundNotifyHolder();
            h.sound = sound;
            return h;
        })
    );

    /// Default sound so a freshly-added notify round-trips encode without
    /// the user having opened a picker yet. Generic editor support for
    /// `Holder<SoundEvent>` is a follow-up (see plan §3c).
    public Holder<@NonNull SoundEvent> sound = SoundEvents.UI_BUTTON_CLICK;

    @Override
    public void readFrom(PlaySoundNotify object) {
        this.sound = object.soundEvent();
    }

    @Override
    public PlaySoundNotify makeObject() {
        return new PlaySoundNotify(sound);
    }

    @Override
    public LogicalSide logicalSide() {
        // PlaySoundNotify hard-codes CLIENT; user can't change it from the editor.
        return LogicalSide.CLIENT;
    }

    @Override
    public String notifyName() {
        return PlaySoundNotify.class.getSimpleName();
    }

    @Override
    public String sourceTemplate() {
        return "notify/play_sound.ftl";
    }

    @Override
    public Map<String, Object> getDTO() {
        // SoundEvent#location() is the sound id; the template rebuilds a direct holder from it
        // (PlaySoundNotify only ever reads soundEvent.value()).
        return Map.of("sound", sound.value().location().toString());
    }
}