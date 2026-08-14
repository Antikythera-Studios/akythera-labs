package com.yesman.akytheralabs.akythera.asset;

import com.yesman.akythera.client.core.asset.loader.MeshLoader;
import com.yesman.akythera.core.asset.loader.AssetLoader;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/// Editable view of a mesh asset's sidecar [MeshLoader.Metadata]. Lives on the mesh's accessor, one
/// per identifier — see [AkytheraProject.EditorMeshAccessor#metadata].
public final class MeshMetadataHolder {
    @EditorEditable(label = LangKeys.GUI_MESH_ASSET_SCREEN_WIDGET_WIDTH, min = 0.0F, order = 0)
    private float width;

    @EditorEditable(label = LangKeys.GUI_MESH_ASSET_SCREEN_WIDGET_HEIGHT, min = 0.0F, order = 1)
    private float height;

    /// The texture path as the edit box holds it, so a half-typed path is a valid state here.
    /// [#getTextureLocation] is the typed view. Written on commit only, so no mid-word path ever
    /// reaches the texture manager.
    @EditorEditable(label = LangKeys.GUI_MESH_ASSET_SCREEN_WIDGET_TEXTURE, order = 2, setOnCommit = true)
    private String texture;

    /// The mesh this sidecar describes. A rename mutates the id in place on this instance, so the
    /// holder keeps pointing at the file.
    private final AkytheraProject.EditorMeshAccessor<?> accessor;

    private MeshMetadataHolder(
        final AkytheraProject.EditorMeshAccessor<?> accessor,
        final MeshLoader.Metadata metadata
    ) {
        this.accessor = accessor;
        width = metadata.width();
        height = metadata.height();
        texture = metadata.texture() == null ? "" : metadata.texture().toString();
    }

    /// The sidecar of `accessor` as it stands on disk, or the defaults when the file is absent.
    public static MeshMetadataHolder readFrom(final AkytheraProject.EditorMeshAccessor<?> accessor) {
        return new MeshMetadataHolder(accessor, AssetLoader.readMetadata(
            accessor.identifier(),
            AkytheraProject.getProjectResourceManager(),
            MeshLoader.Metadata.CODEC,
            MeshLoader.Metadata.DEFAULT
        ));
    }

    public AkytheraProject.EditorMeshAccessor<?> getAccessor() {
        return accessor;
    }

    public MeshLoader.Metadata makeMetadata() {
        return new MeshLoader.Metadata(width, height, getTextureLocation());
    }

    /// [#texture] as an [Identifier], or null when it is blank or not a well-formed one.
    public @Nullable Identifier getTextureLocation() {
        return texture.isBlank() ? null : Identifier.tryParse(texture);
    }

    public float getWidth() {
        return width;
    }

    public void setWidth(final float width) {
        this.width = width;
    }

    public float getHeight() {
        return height;
    }

    public void setHeight(final float height) {
        this.height = height;
    }

    public String getTexture() {
        return texture;
    }

    public void setTexture(final String texture) {
        this.texture = texture;
    }
}
