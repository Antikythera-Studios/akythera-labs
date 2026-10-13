package com.yesman.akytheralabs.gui.widget;

import com.mojang.blaze3d.platform.NativeImage;
import com.yesman.akytheralabs.AkytheraLabs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/// GPU copies of the images an editor widget previews: a file on disk, or a texture the loaded
/// resource packs carry. Each image is read off the render thread on its first request, and the
/// owner frees them all with [#releaseAll].
public final class PreviewTextureCache {
    /// Numbers the texture slots, unique across every cache.
    private static final AtomicInteger NEXT_SLOT = new AtomicInteger();

    /// One uploaded image: the id it is bound under, and its size in pixels.
    public record Entry(Identifier textureId, int width, int height) {}

    /// Uploaded images by source. A source still being read, or one that failed to read, maps to null.
    private final Map<Object, @Nullable Entry> entries = new HashMap<>();

    /// Bumped by [#releaseAll], so a read that lands after it is dropped.
    private int generation;

    /// `file` read from disk, or null until it is uploaded.
    public @Nullable Entry get(Path file) {
        return get(file, () -> Files.newInputStream(file));
    }

    /// `textureId` read from the loaded resource packs, or null until it is uploaded.
    public @Nullable Entry get(Identifier textureId) {
        return get(textureId, () -> Minecraft.getInstance().getResourceManager().open(textureId));
    }

    private @Nullable Entry get(Object source, IoSupplier<InputStream> opener) {
        if (entries.containsKey(source)) return entries.get(source);

        entries.put(source, null);
        int requestedGeneration = generation;

        CompletableFuture.supplyAsync(() -> {
            try (InputStream in = opener.get()) {
                return NativeImage.read(in);
            } catch (IOException e) {
                AkytheraLabs.LOGGER.warn("Failed to read texture {}", source, e);
                return null;
            }
        }).thenAcceptAsync(image -> {
            if (image == null) return;

            if (requestedGeneration != generation) {
                image.close();
                return;
            }

            Identifier textureId = AkytheraLabs.identifier("preview_texture/" + NEXT_SLOT.getAndIncrement());
            Entry entry = new Entry(textureId, image.getWidth(), image.getHeight());

            Minecraft.getInstance().getTextureManager().register(textureId, new DynamicTexture(source::toString, image));
            entries.put(source, entry);
        }, Minecraft.getInstance());

        return null;
    }

    /// Frees every uploaded image. A later request reads its source again.
    public void releaseAll() {
        for (Entry entry : entries.values()) {
            if (entry != null) Minecraft.getInstance().getTextureManager().release(entry.textureId());
        }

        entries.clear();
        generation++;
    }
}
