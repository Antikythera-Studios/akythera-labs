package com.yesman.akytheralabs;

import com.yesman.akytheralabs.dataholder.sound.SoundEventHolder;
import com.yesman.akytheralabs.mixin.SoundBufferLibraryAccessor;
import com.yesman.akytheralabs.mixin.SoundEngineAccessor;
import com.yesman.akytheralabs.mixin.SoundManagerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.client.sounds.Weighted;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.util.valueproviders.MultipliedFloats;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/// Plays the open project's sounds through the game's [SoundManager]. The project file
/// `assets/sounds/<path>.akmeta` is the sound event `<projectName>:<path>` ([SoundEventHolder]), and
/// `assets/sounds/<path>.ogg` the sound file an entry names `<projectName>:<path>`. A project event
/// outranks the resource packs' event of the same id, the way a higher resource pack does.
@NullMarked
public final class ProjectSounds {
    /// `OggS`, the capture pattern every Ogg page starts with.
    private static final byte[] OGG_PAGE = "OggS".getBytes(StandardCharsets.US_ASCII);

    /// Packet type 1 followed by `vorbis`, the start of the Vorbis identification header.
    private static final byte[] VORBIS_ID_HEADER = "\u0001vorbis".getBytes(StandardCharsets.US_ASCII);

    private ProjectSounds() {}

    /// What [SoundManager#getSoundEvent] answers for `eventId`: the open project's event, merged with
    /// `packEvent` (the resource packs' event of the same id) unless it replaces it. Null when the
    /// project defines no such event.
    public static @Nullable WeighedSoundEvents resolve(Identifier eventId, @Nullable WeighedSoundEvents packEvent) {
        AkytheraProject project = AkytheraProject.getCurrentProjectOrNull();
        if (project == null) return null;

        Path eventFile = project.getSoundEventFile(eventId);
        if (eventFile == null || !Files.isRegularFile(eventFile)) return null;

        return makeEvent(eventId, SoundEventHolder.readFrom(eventFile), packEvent);
    }

    /// `holder` as the game plays it under `eventId`. Without [SoundEventHolder#getReplace], the
    /// sounds of `packEvent` stay in the pick and its subtitle is kept, as vanilla merges two packs.
    public static WeighedSoundEvents makeEvent(Identifier eventId, SoundEventHolder holder, @Nullable WeighedSoundEvents packEvent) {
        boolean merge = !holder.getReplace() && packEvent != null;
        String subtitle = holder.getSubtitle().isEmpty() ? null : holder.getSubtitle();

        WeighedSoundEvents event = new WeighedSoundEvents(eventId, subtitle) {
            @Override
            public @Nullable Component getSubtitle() {
                return merge ? packEvent.getSubtitle() : super.getSubtitle();
            }
        };

        if (merge) event.addSound(packEvent);

        for (SoundEventHolder.SoundHolder entry : holder.getSounds()) {
            Weighted<Sound> weighted = makeEntry(entry);
            if (weighted != null) event.addSound(weighted);
        }

        return event;
    }

    /// One entry as the game picks it, or null when its name is no valid id. A file entry plays the
    /// file; an event entry plays a pick of that event, scaled by the entry's volume and pitch the way
    /// vanilla's `SoundManager.Preparations#handleRegistration` does.
    public static @Nullable Weighted<Sound> makeEntry(SoundEventHolder.SoundHolder entry) {
        Sound sound = entry.makeSound();

        if (sound == null) return null;
        if (sound.getType() == Sound.Type.FILE) return sound;

        return new Weighted<>() {
            @Override
            public int getWeight() {
                WeighedSoundEvents event = Minecraft.getInstance().getSoundManager().getSoundEvent(sound.getLocation());
                return event == null ? 0 : event.getWeight();
            }

            @Override
            public Sound getSound(RandomSource random) {
                WeighedSoundEvents event = Minecraft.getInstance().getSoundManager().getSoundEvent(sound.getLocation());

                if (event == null) return SoundManager.EMPTY_SOUND;

                Sound picked = event.getSound(random);

                return new Sound(
                    picked.getLocation(),
                    new MultipliedFloats(picked.getVolume(), sound.getVolume()),
                    new MultipliedFloats(picked.getPitch(), sound.getPitch()),
                    sound.getWeight(),
                    Sound.Type.FILE,
                    picked.shouldStream() || sound.shouldStream(),
                    picked.shouldPreload(),
                    picked.getAttenuationDistance()
                );
            }

            @Override
            public void preloadIfRequired(SoundEngine soundEngine) {
            }
        };
    }

    /// Ids of every sound event the open project defines, in file order.
    public static List<Identifier> listProjectEvents() {
        AkytheraProject project = AkytheraProject.getCurrentProject();
        List<Identifier> eventIds = new ArrayList<>();

        FileSystemHelper.forEachFile(
            project.getProjectRoot().resolve("assets").resolve(AkytheraProject.SOUNDS_DIRECTORY),
            file -> file.toString().endsWith(AkytheraProject.SOUND_EVENT_EXTENSION),
            file -> {
                Identifier eventId = project.getSoundId(file);
                if (eventId != null) eventIds.add(eventId);
            }
        );

        return eventIds;
    }

    /// `packFiles` with the open project's sound files over it — what the game's sound buffers read
    /// from.
    public static ResourceProvider withProjectFiles(ResourceProvider packFiles) {
        return location -> findProjectFile(location).or(() -> packFiles.getResource(location));
    }

    /// The open project's file at `location` (`<projectName>:sounds/….ogg`), if it has one.
    private static Optional<Resource> findProjectFile(Identifier location) {
        AkytheraProject project = AkytheraProject.getCurrentProjectOrNull();

        if (project == null || !location.getNamespace().equals(project.getProjectName())) return Optional.empty();

        return AkytheraProject.getProjectResourceManager().getResource(location);
    }

    /// Drops the decoded audio of the sound file `location` (`<namespace>:sounds/….ogg`), so the next
    /// play reads the file again.
    public static void evictCachedBuffer(Identifier location) {
        SoundEngine soundEngine = ((SoundManagerAccessor) Minecraft.getInstance().getSoundManager()).akytheralabs$getSoundEngine();
        SoundBufferLibraryAccessor buffers = (SoundBufferLibraryAccessor) ((SoundEngineAccessor) soundEngine).akytheralabs$getSoundBuffers();

        // Left undiscarded: a channel may still be playing it.
        buffers.akytheralabs$getCache().remove(location);
    }

    /// Moves the playing `instance` to `position`, taken as listener space when it is relative. A
    /// no-op once it stopped.
    public static void moveSound(SoundInstance instance, Vec3 position) {
        SoundEngine soundEngine = ((SoundManagerAccessor) Minecraft.getInstance().getSoundManager()).akytheralabs$getSoundEngine();
        ChannelAccess.ChannelHandle handle = ((SoundEngineAccessor) soundEngine).akytheralabs$getInstanceToChannel().get(instance);

        if (handle != null) handle.execute(channel -> channel.setSelfPosition(position));
    }

    /// Length of the sound file `location` (`<namespace>:sounds/….ogg`) in seconds, the open project's
    /// file over the resource packs'. Completes with 0 for a file that is missing or no Ogg Vorbis.
    public static CompletableFuture<Float> durationOf(Identifier location) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<Resource> resource = findProjectFile(location)
                .or(() -> Minecraft.getInstance().getResourceManager().getResource(location));

            if (resource.isEmpty()) return 0.0F;

            try (InputStream in = resource.get().open()) {
                return oggDuration(in.readAllBytes());
            } catch (IOException e) {
                AkytheraLabs.LOGGER.error("Failed to read sound {}", location, e);
                return 0.0F;
            }
        }, Util.nonCriticalIoPool());
    }

    /// Length of the Ogg Vorbis file `bytes` in seconds: the granule position of its last page (the
    /// sample count) over the sample rate of its identification header. 0 when `bytes` is no Ogg
    /// Vorbis.
    public static float oggDuration(byte[] bytes) {
        int header = indexOf(bytes, VORBIS_ID_HEADER, false);
        int lastPage = indexOf(bytes, OGG_PAGE, true);

        // Rate: after the 7-byte header start, the 4-byte version and the 1-byte channel count.
        if (header < 0 || lastPage < 0 || header + 16 > bytes.length || lastPage + 14 > bytes.length) return 0.0F;

        ByteBuffer littleEndian = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int sampleRate = littleEndian.getInt(header + 12);
        long granulePosition = littleEndian.getLong(lastPage + 6);

        return sampleRate > 0 && granulePosition > 0 ? (float) granulePosition / sampleRate : 0.0F;
    }

    /// Index of the first, or the `last`, occurrence of `pattern` in `data`, or -1.
    private static int indexOf(byte[] data, byte[] pattern, boolean last) {
        int from = last ? data.length - pattern.length : 0;
        int step = last ? -1 : 1;

        for (int i = from; i >= 0 && i <= data.length - pattern.length; i += step) {
            int matched = 0;

            while (matched < pattern.length && data[i + matched] == pattern[matched]) matched++;

            if (matched == pattern.length) return i;
        }

        return -1;
    }

    /// A sound previewed from the origin of an editor viewer: listener-relative, so its channel takes
    /// the origin in the viewer camera's space, see [#moveSound]. Plays the one `entry` it was given.
    public static final class PreviewSoundInstance extends AbstractSoundInstance {
        private final Weighted<Sound> entry;

        public PreviewSoundInstance(Identifier eventId, Weighted<Sound> entry, Vec3 listenerSpacePosition) {
            super(eventId, SoundSource.MASTER, SoundInstance.createUnseededRandom());

            this.entry = entry;
            this.relative = true;
            this.x = listenerSpacePosition.x;
            this.y = listenerSpacePosition.y;
            this.z = listenerSpacePosition.z;
        }

        @Override
        public WeighedSoundEvents resolve(SoundManager soundManager) {
            this.sound = entry.getSound(random);

            return new WeighedSoundEvents(identifier, null);
        }
    }
}
