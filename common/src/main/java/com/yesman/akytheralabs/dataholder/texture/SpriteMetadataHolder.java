package com.yesman.akytheralabs.dataholder.texture;

import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.client.resources.metadata.gui.GuiMetadataSection;
import net.minecraft.client.resources.metadata.gui.GuiSpriteScaling;
import net.minecraft.server.packs.metadata.MetadataSectionType;

import java.nio.file.Path;

/// Editable view of a GUI sprite's [GuiMetadataSection].
///
/// [GuiSpriteScaling#CODEC] dispatches the scalings on `type`, so the type is a row here:
/// [#scalingType] picks the [GuiSpriteScaling.Type] and [#scaling] holds that type's own parameters,
/// swapped through [SpriteScalingHolder#create] when the pick changes.
public final class SpriteMetadataHolder extends TextureMetadataHolder<GuiMetadataSection> {
    /// The type [#scaling] holds. Writing it swaps [#scaling] for that type's holder, so the row
    /// re-renders the block behind it.
    @EditorEditable(
        label = LangKeys.GUI_TEXTURE_PREVIEW_SCREEN_WIDGET_SCALING,
        category = LangKeys.GUI_TEXTURE_PREVIEW_SCREEN_CATEGORY_SPRITE,
        order = 0,
        rebuildOnWrite = true
    )
    private GuiSpriteScaling.Type scalingType;

    /// [#scalingType]'s own parameters.
    @EditorEditable(
        label = LangKeys.GUI_TEXTURE_PREVIEW_SCREEN_WIDGET_SCALING_PARAMETERS,
        category = LangKeys.GUI_TEXTURE_PREVIEW_SCREEN_CATEGORY_SPRITE,
        order = 1
    )
    private SpriteScalingHolder scaling;

    SpriteMetadataHolder(final Path textureFile, final GuiMetadataSection section) {
        super(textureFile);

        scaling = SpriteScalingHolder.of(section.scaling());
        scalingType = scaling.type();
    }

    @Override
    protected MetadataSectionType<GuiMetadataSection> sectionType() {
        return GuiMetadataSection.TYPE;
    }

    @Override
    public GuiMetadataSection makeSection() {
        return new GuiMetadataSection(scaling.makeScaling());
    }

    public GuiSpriteScaling.Type getScalingType() {
        return scalingType;
    }

    /// Swaps [#scaling] for `scalingType`'s holder, at that type's defaults.
    public void setScalingType(final GuiSpriteScaling.Type scalingType) {
        if (this.scalingType == scalingType) return;

        this.scalingType = scalingType;
        this.scaling = SpriteScalingHolder.create(scalingType);
    }

    public SpriteScalingHolder getScaling() {
        return scaling;
    }
}
