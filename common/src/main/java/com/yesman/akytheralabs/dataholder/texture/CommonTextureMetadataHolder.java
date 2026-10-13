package com.yesman.akytheralabs.dataholder.texture;

import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.client.renderer.texture.MipmapStrategy;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.server.packs.metadata.MetadataSectionType;

import java.nio.file.Path;

/// Editable view of a plain texture's [TextureMetadataSection].
public final class CommonTextureMetadataHolder extends TextureMetadataHolder<TextureMetadataSection> {
    @EditorEditable(
        label = LangKeys.GUI_TEXTURE_PREVIEW_SCREEN_WIDGET_BLUR,
        category = LangKeys.GUI_TEXTURE_PREVIEW_SCREEN_CATEGORY_TEXTURE,
        order = 0
    )
    private boolean blur;

    @EditorEditable(
        label = LangKeys.GUI_TEXTURE_PREVIEW_SCREEN_WIDGET_CLAMP,
        category = LangKeys.GUI_TEXTURE_PREVIEW_SCREEN_CATEGORY_TEXTURE,
        order = 1
    )
    private boolean clamp;

    @EditorEditable(
        label = LangKeys.GUI_TEXTURE_PREVIEW_SCREEN_WIDGET_MIPMAP_STRATEGY,
        category = LangKeys.GUI_TEXTURE_PREVIEW_SCREEN_CATEGORY_TEXTURE,
        order = 2
    )
    private MipmapStrategy mipmapStrategy;

    @EditorEditable(
        label = LangKeys.GUI_TEXTURE_PREVIEW_SCREEN_WIDGET_ALPHA_CUTOFF_BIAS,
        category = LangKeys.GUI_TEXTURE_PREVIEW_SCREEN_CATEGORY_TEXTURE,
        order = 3
    )
    private float alphaCutoffBias;

    CommonTextureMetadataHolder(final Path textureFile, final TextureMetadataSection section) {
        super(textureFile);

        blur = section.blur();
        clamp = section.clamp();
        mipmapStrategy = section.mipmapStrategy();
        alphaCutoffBias = section.alphaCutoffBias();
    }

    @Override
    protected MetadataSectionType<TextureMetadataSection> sectionType() {
        return TextureMetadataSection.TYPE;
    }

    @Override
    public TextureMetadataSection makeSection() {
        return new TextureMetadataSection(blur, clamp, mipmapStrategy, alphaCutoffBias);
    }

    public boolean getBlur() {
        return blur;
    }

    public void setBlur(final boolean blur) {
        this.blur = blur;
    }

    public boolean getClamp() {
        return clamp;
    }

    public void setClamp(final boolean clamp) {
        this.clamp = clamp;
    }

    public MipmapStrategy getMipmapStrategy() {
        return mipmapStrategy;
    }

    public void setMipmapStrategy(final MipmapStrategy mipmapStrategy) {
        this.mipmapStrategy = mipmapStrategy;
    }

    public float getAlphaCutoffBias() {
        return alphaCutoffBias;
    }

    public void setAlphaCutoffBias(final float alphaCutoffBias) {
        this.alphaCutoffBias = alphaCutoffBias;
    }
}
