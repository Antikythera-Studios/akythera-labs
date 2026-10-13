package com.yesman.akytheralabs.akythera.animation.notify.impl;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.animation.notify.impl.PlaySoundNotify;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.core.util.LogicalSide;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.akythera.animation.notify.AnimationNotifyHolder;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.joml.Vector3f;
import org.jspecify.annotations.NonNull;

import java.util.Map;

public final class PlaySoundNotifyHolder extends AnimationNotifyHolder<PlaySoundNotify> {
    public static final Codec<PlaySoundNotifyHolder> EDITOR_CODEC = RecordCodecBuilder.create(inst ->
        inst.group(
            SoundEvent.CODEC.fieldOf("sound").forGetter(h -> ParseUtils.holderOf(h.sound))
        ).apply(inst, sound -> {
            PlaySoundNotifyHolder h = new PlaySoundNotifyHolder();
            h.sound = sound.value();
            return h;
        })
    );

    /// The sound to play: a registered one, or an event only resource packs define.
    @EditorEditable(label = LangKeys.GUI_NOTIFY_TRACKS_FIELD_SOUND, order = 0)
    public SoundEvent sound = SoundEvents.UI_BUTTON_CLICK.value();


    @Override
    public void readFrom(PlaySoundNotify object) {
        this.sound = object.soundEvent().value();
    }

    @Override
    public PlaySoundNotify makeObject() {
        return new PlaySoundNotify(ParseUtils.holderOf(sound));
    }

    @Override
    public LogicalSide logicalSide() {
        return LogicalSide.CLIENT; // PlaySoundNotify hard-codes it
    }

    @Override
    public String notifyName() {
        return PlaySoundNotify.class.getSimpleName();
    }

    /// Plays [#sound] from the previewed entity, at the origin of the previewer.
    @Override
    public void simulate(Model3DViewer previewer) {
        Vector3f origin = previewer.toViewSpace(new Vector3f());
        if (origin == null) origin = new Vector3f();

        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(
            sound.location(), SoundSource.MASTER, 1.0F, 1.0F, SoundInstance.createUnseededRandom(),
            false, 0, SoundInstance.Attenuation.LINEAR, origin.x, origin.y, origin.z, true
        ));
    }

    @Override
    public String sourceTemplate() {
        return "notify/play_sound.ftl";
    }

    @Override
    public Map<String, Object> getDTO() {
        // The template rebuilds a direct holder from the sound id.
        return Map.of("sound", sound.location().toString());
    }
}
