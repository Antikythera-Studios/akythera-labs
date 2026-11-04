package com.yesman.akytheralabs.akythera.widget;

import com.yesman.akythera.api.data.serializable.RGBA;
import com.yesman.akythera.api.data.serializable.WidgetImage;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.dataholder.EditorReference;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.stream.Stream;

/// A [WidgetImage] as the editor holds it: `texture` tinted by `color`, or a plain `color` fill while `texture` is
/// null.
///
/// @param sprite whether the image names `texture` by its GUI sprite id, picked among `textures/gui/sprites/**`,
///               rather than by its full path
public record WidgetImageRef(AkytheraProject.@Nullable EditorTextureAccessor texture, RGBA color, boolean sprite) {
    public static WidgetImageRef of(final WidgetImage image, final boolean sprite) {
        Identifier id = image.texture();

        if (id == null) return new WidgetImageRef(null, image.color(), sprite);

        return new WidgetImageRef(
            sprite ? AkytheraProject.spriteOf(AkytheraProject.GUI_SPRITES_DIRECTORY, id) : AkytheraProject.textureOf(id),
            image.color(),
            sprite
        );
    }

    /// The id [WidgetImage#texture] stores: the sprite id, or the full path.
    public @Nullable Identifier textureId() {
        if (texture == null) return null;

        return sprite ? texture.spriteId(AkytheraProject.GUI_SPRITES_DIRECTORY) : texture.texturePath();
    }

    public WidgetImageRef withColor(final RGBA color) {
        return new WidgetImageRef(texture, color, sprite);
    }

    public WidgetImageRef withTexture(final @Nullable Identifier textureId) {
        return of(new WidgetImage(textureId, color), sprite);
    }

    public WidgetImage makeObject() {
        return new WidgetImage(textureId(), color);
    }

    public Stream<EditorReference> referencedReferences() {
        return Stream.ofNullable(texture);
    }
}
