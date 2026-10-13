package com.yesman.akytheralabs.dataholder.texture;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.FileSystemHelper;
import net.minecraft.client.resources.metadata.gui.GuiMetadataSection;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

/// Editable view of one section of a texture's `.mcmeta` sidecar. Which section is decided by where
/// the texture lives: a GUI sprite under [AkytheraProject#GUI_SPRITES_DIRECTORY] reads
/// [GuiMetadataSection], any other texture [TextureMetadataSection].
///
/// @param <S> The metadata section type
public abstract class TextureMetadataHolder<S> {
    private static final Gson GSON_PRETTY = new GsonBuilder().setPrettyPrinting().create();

    /// The PNG this sidecar describes.
    private final Path textureFile;

    protected TextureMetadataHolder(final Path textureFile) {
        this.textureFile = textureFile;
    }

    /// The holder matching where `textureFile` lives, filled from its sidecar, or at the section's
    /// defaults when the sidecar or the section is missing.
    public static TextureMetadataHolder<?> readFrom(final Path textureFile) {
        JsonObject sidecar = ParseUtils.nvl(
            FileSystemHelper.readJsonObject(AkytheraProject.getMcmetaFileBeside(textureFile)),
            new JsonObject()
        );

        return AkytheraProject.getCurrentProject().isGuiSprite(textureFile)
            ? new SpriteMetadataHolder(textureFile, readSection(sidecar, GuiMetadataSection.TYPE))
            : new CommonTextureMetadataHolder(textureFile, readSection(sidecar, TextureMetadataSection.TYPE));
    }

    /// `type`'s section of `sidecar`. A missing or unreadable section decodes from an empty object,
    /// which gives the section's defaults.
    private static <T> T readSection(final JsonObject sidecar, final MetadataSectionType<T> type) {
        JsonElement section = sidecar.has(type.name()) ? sidecar.get(type.name()) : new JsonObject();

        return type.codec().parse(JsonOps.INSTANCE, section)
            .resultOrPartial(error -> AkytheraLabs.LOGGER.error("Failed at decoding {} section: {}", type.name(), error))
            .orElseGet(() -> type.codec().parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow());
    }

    /// The section this holder edits.
    protected abstract MetadataSectionType<S> sectionType();

    /// The section as the fields stand.
    public abstract S makeSection();

    /// Writes [#makeSection] into the sidecar beside the texture, creating the file when absent. The
    /// other sections the file already holds are kept. A section at its defaults is left out, and a
    /// sidecar left with no section is deleted. Returns the reason on failure, or null.
    public @Nullable String save() {
        Path mcmetaFile = AkytheraProject.getMcmetaFileBeside(textureFile);
        JsonObject sidecar = ParseUtils.nvl(FileSystemHelper.readJsonObject(mcmetaFile), new JsonObject());
        S section = makeSection();

        if (section.equals(readSection(new JsonObject(), sectionType()))) {
            sidecar.remove(sectionType().name());
        } else {
            DataResult<JsonElement> encoded = sectionType().codec().encodeStart(JsonOps.INSTANCE, section);

            if (encoded.isError()) {
                return encoded.error().map(DataResult.Error::message).orElse("");
            }

            sidecar.add(sectionType().name(), encoded.getOrThrow());
        }

        if (sidecar.isEmpty()) {
            FileSystemHelper.deleteRecursive(mcmetaFile);
            return null;
        }

        if (!FileSystemHelper.writeString(mcmetaFile, GSON_PRETTY.toJson(sidecar))) {
            return "Failed to write " + mcmetaFile;
        }

        return null;
    }
}
