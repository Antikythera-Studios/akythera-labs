package com.yesman.akytheralabs.dataholder.sound;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.FileSystemHelper;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundEventRegistrationSerializer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.valueproviders.ConstantFloat;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/// Editable view of one sound event: the entry a resource pack's `sounds.json` keeps under the event's
/// id, with the fields [SoundEventRegistrationSerializer] reads. A project keeps each event in an
/// `.akmeta` file under [AkytheraProject#SOUNDS_DIRECTORY], whose path is the event's id, see
/// [AkytheraProject#getSoundId].
public final class SoundEventHolder {
    public static final Codec<SoundEventHolder> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.BOOL.optionalFieldOf("replace", false).forGetter(h -> h.replace),
        Codec.STRING.optionalFieldOf("subtitle", "").forGetter(h -> h.subtitle),
        SoundHolder.CODEC.listOf().optionalFieldOf("sounds", List.of()).forGetter(h -> h.sounds)
    ).apply(instance, (replace, subtitle, sounds) -> {
        SoundEventHolder holder = new SoundEventHolder();

        holder.replace = replace;
        holder.subtitle = subtitle;
        holder.sounds.addAll(sounds);

        return holder;
    }));

    /// Whether the event drops the sounds lower resource packs list under the same id.
    @EditorEditable(label = LangKeys.GUI_SOUND_ASSET_SCREEN_WIDGET_REPLACE, order = 0)
    private boolean replace;

    /// Lang key of the subtitle, or empty for none.
    @EditorEditable(label = LangKeys.GUI_SOUND_ASSET_SCREEN_WIDGET_SUBTITLE, order = 1, setOnCommit = true)
    private String subtitle = "";

    /// The live sound list, a mutable [ArrayList] that add, remove and their undo write into.
    @EditorEditable(label = LangKeys.GUI_SOUND_ASSET_SCREEN_WIDGET_SOUNDS, order = 2, defaultElement = "defaultSound")
    private final List<SoundHolder> sounds = new ArrayList<>();

    /// The `+` button's fresh row, see [EditorEditable#defaultElement].
    static SoundHolder defaultSound() {
        return new SoundHolder("");
    }

    /// The event `eventFile` holds, or an empty one when it is missing or unreadable.
    public static SoundEventHolder readFrom(Path eventFile) {
        return FileSystemHelper.readJson(eventFile, CODEC, new SoundEventHolder());
    }

    /// Writes the event into `eventFile`. A value the game would refuse is not written. Returns the
    /// reason on failure, or null.
    public @Nullable String save(Path eventFile) {
        for (SoundHolder sound : sounds) {
            String failure = sound.validate();
            if (failure != null) return failure;
        }

        return FileSystemHelper.writeJson(eventFile, CODEC, this) ? null : "Failed to write " + eventFile;
    }

    public boolean getReplace() {
        return replace;
    }

    public void setReplace(boolean replace) {
        this.replace = replace;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public List<SoundHolder> getSounds() {
        return sounds;
    }

    /// One entry of [#sounds]: a sound file, or another sound event played in its place.
    public static final class SoundHolder {
        private static final Codec<Sound.Type> TYPE_CODEC = Codec.STRING.comapFlatMap(
            name -> {
                Sound.Type type = Sound.Type.getByName(name);
                return type != null ? DataResult.success(type) : DataResult.error(() -> "Unknown sound type " + name);
            },
            type -> type == Sound.Type.FILE ? "file" : "event"
        );

        private static final Codec<SoundHolder> OBJECT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("name").forGetter(h -> h.name),
            TYPE_CODEC.optionalFieldOf("type", Sound.Type.FILE).forGetter(h -> h.type),
            Codec.FLOAT.optionalFieldOf("volume", 1.0F).forGetter(h -> h.volume),
            Codec.FLOAT.optionalFieldOf("pitch", 1.0F).forGetter(h -> h.pitch),
            Codec.INT.optionalFieldOf("weight", 1).forGetter(h -> h.weight),
            Codec.BOOL.optionalFieldOf("stream", false).forGetter(h -> h.stream),
            Codec.BOOL.optionalFieldOf("preload", false).forGetter(h -> h.preload),
            Codec.INT.optionalFieldOf("attenuation_distance", 16).forGetter(h -> h.attenuationDistance)
        ).apply(instance, (name, type, volume, pitch, weight, stream, preload, attenuationDistance) -> {
            SoundHolder holder = new SoundHolder(name);

            holder.type = type;
            holder.volume = volume;
            holder.pitch = pitch;
            holder.weight = weight;
            holder.stream = stream;
            holder.preload = preload;
            holder.attenuationDistance = attenuationDistance;

            return holder;
        }));

        /// An object, or the bare name a pack may list a sound by.
        public static final Codec<SoundHolder> CODEC = Codec.withAlternative(OBJECT_CODEC, Codec.STRING.xmap(SoundHolder::new, h -> h.name));

        /// Whether [#name] is a sound file or a sound event.
        @EditorEditable(label = LangKeys.GUI_SOUND_ASSET_SCREEN_WIDGET_TYPE, order = 0)
        private Sound.Type type = Sound.Type.FILE;

        /// `<namespace>:<path>` of the file `assets/<namespace>/sounds/<path>.ogg`, or a sound event id.
        @EditorEditable(label = LangKeys.GUI_SOUND_ASSET_SCREEN_WIDGET_NAME, order = 1, setOnCommit = true)
        private String name;

        @EditorEditable(label = LangKeys.GUI_SOUND_ASSET_SCREEN_WIDGET_VOLUME, order = 2)
        private float volume = 1.0F;

        @EditorEditable(label = LangKeys.GUI_SOUND_ASSET_SCREEN_WIDGET_PITCH, order = 3)
        private float pitch = 1.0F;

        /// Odds of this entry being picked, against the summed weights of the event.
        @EditorEditable(label = LangKeys.GUI_SOUND_ASSET_SCREEN_WIDGET_WEIGHT, order = 4)
        private int weight = 1;

        @EditorEditable(label = LangKeys.GUI_SOUND_ASSET_SCREEN_WIDGET_STREAM, order = 5)
        private boolean stream;

        @EditorEditable(label = LangKeys.GUI_SOUND_ASSET_SCREEN_WIDGET_PRELOAD, order = 6)
        private boolean preload;

        /// Distance the sound fades out over, in blocks. Scaled by [#volume] above 1.
        @EditorEditable(label = LangKeys.GUI_SOUND_ASSET_SCREEN_WIDGET_ATTENUATION_DISTANCE, order = 7)
        private int attenuationDistance = 16;

        public SoundHolder(String name) {
            this.name = name;
        }

        /// The entry as the game reads it, or null while [#name] is no valid id.
        public @Nullable Sound makeSound() {
            Identifier location = Identifier.tryParse(name);

            if (location == null) return null;

            return new Sound(location, ConstantFloat.of(volume), ConstantFloat.of(pitch), weight, type, stream, preload, attenuationDistance);
        }

        /// The reason the game would refuse this entry, or null. The same checks as
        /// [SoundEventRegistrationSerializer].
        private @Nullable String validate() {
            if (Identifier.tryParse(name) == null) return "Invalid sound name \"" + name + "\"";
            if (volume <= 0.0F) return "Volume of " + name + " must be positive";
            if (pitch <= 0.0F) return "Pitch of " + name + " must be positive";
            if (weight <= 0) return "Weight of " + name + " must be positive";

            return null;
        }

        public Sound.Type getType() {
            return type;
        }

        public void setType(Sound.Type type) {
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public float getVolume() {
            return volume;
        }

        public void setVolume(float volume) {
            this.volume = volume;
        }

        public float getPitch() {
            return pitch;
        }

        public void setPitch(float pitch) {
            this.pitch = pitch;
        }

        public int getWeight() {
            return weight;
        }

        public void setWeight(int weight) {
            this.weight = weight;
        }

        public boolean getStream() {
            return stream;
        }

        public void setStream(boolean stream) {
            this.stream = stream;
        }

        public boolean getPreload() {
            return preload;
        }

        public void setPreload(boolean preload) {
            this.preload = preload;
        }

        public int getAttenuationDistance() {
            return attenuationDistance;
        }

        public void setAttenuationDistance(int attenuationDistance) {
            this.attenuationDistance = attenuationDistance;
        }
    }
}
