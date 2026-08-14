package com.yesman.akytheralabs.akythera.asset;

import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.lifecycle.ComponentsFactory;
import com.yesman.akythera.core.animation.keyframe.AnimationClip;
import com.yesman.akythera.core.asset.loader.AnimationLoader;
import com.yesman.akythera.core.asset.loader.AssetLoader;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.dataholder.EntityPreviewModel;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.editor.EditorWidgetFactory;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/// Editable view of an animation asset's sidecar [AnimationLoader.Metadata]. Lives on the
/// animation's accessor, one per identifier — see
/// [AkytheraProject.EditorAnimationAccessor#metadata].
public final class AnimationMetadataHolder {
    @EditorEditable(label = LangKeys.GUI_ANIMATION_ASSET_SCREEN_WIDGET_IMPORT_TYPE, order = 0)
    private AnimationLoader.ImportType importType;

    @EditorEditable(label = LangKeys.GUI_ANIMATION_ASSET_SCREEN_WIDGET_REFERENCE_POSE, order = 1)
    private ReferencePose referencePose;

    /// The animation this sidecar describes. A rename mutates the id in place on this instance, so
    /// the holder keeps pointing at the file.
    private final AkytheraProject.EditorAnimationAccessor accessor;

    /// The model the previewer shows. [EditorWidgetFactory#getPreviewModelField] finds it by type.
    private @Nullable EntityPreviewModel previewModel;

    private AnimationMetadataHolder(
        final AkytheraProject.EditorAnimationAccessor accessor,
        final AnimationLoader.Metadata metadata
    ) {
        this.accessor = accessor;
        importType = metadata.importType();
        referencePose = new ReferencePose(metadata.refPoseProvider(), metadata.refPoseTimeStamp());
    }

    /// The sidecar of `accessor` as it stands on disk, or the defaults when the file is absent.
    public static AnimationMetadataHolder readFrom(final AkytheraProject.EditorAnimationAccessor accessor) {
        return new AnimationMetadataHolder(accessor, metadataOf(accessor.identifier()));
    }

    /// The sidecar of any animation the editor can see: the open project first, the game's packs
    /// second. `readMetadata` hands back the `orElse` instance itself when a file is absent, so the
    /// fallback is an identity check.
    public static AnimationLoader.Metadata metadataOf(final Identifier animationId) {
        AnimationLoader.Metadata fromProject = AssetLoader.readMetadata(
            animationId,
            AkytheraProject.getProjectResourceManager(),
            AnimationLoader.Metadata.CODEC,
            AnimationLoader.Metadata.DEFAULT
        );

        if (fromProject != AnimationLoader.Metadata.DEFAULT) {
            return fromProject;
        }

        return AssetLoader.readMetadata(
            animationId,
            ComponentsFactory.getImpl().getResourceManager(),
            AnimationLoader.Metadata.CODEC,
            AnimationLoader.Metadata.DEFAULT
        );
    }

    /// Whether making `candidate` the reference pose of `edited` would close a reference cycle:
    /// `candidate` is `edited` itself, or its own reference pose chain reaches back to it.
    public static boolean formsCycle(final Identifier edited, final Identifier candidate) {
        Set<Identifier> visited = new HashSet<>();
        Identifier cursor = candidate;

        while (cursor != null && visited.add(cursor)) {
            if (cursor.equals(edited)) {
                return true;
            }

            AssetAccessor<AnimationClip> provider = metadataOf(cursor).refPoseProvider();
            cursor = provider == null ? null : provider.identifier();
        }

        return false;
    }

    public AnimationLoader.Metadata makeMetadata() {
        return new AnimationLoader.Metadata(importType, referencePose.clip(), referencePose.timeStamp());
    }

    public AnimationLoader.ImportType getImportType() {
        return importType;
    }

    public void setImportType(final AnimationLoader.ImportType importType) {
        this.importType = importType;
    }

    public ReferencePose getReferencePose() {
        return referencePose;
    }

    public void setReferencePose(final ReferencePose referencePose) {
        this.referencePose = referencePose;
    }

    public AkytheraProject.EditorAnimationAccessor getAccessor() {
        return accessor;
    }

    public @Nullable EntityPreviewModel getPreviewModel() {
        return previewModel;
    }

    public void setPreviewModel(final @Nullable EntityPreviewModel previewModel) {
        this.previewModel = previewModel;
    }
}
