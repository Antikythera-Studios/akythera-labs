package com.yesman.akytheralabs.akythera.asset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.lifecycle.ComponentsFactory;
import com.yesman.akythera.core.animation.keyframe.AnimationClip;
import com.yesman.akythera.core.asset.loader.AnimationLoader;
import com.yesman.akythera.core.asset.loader.AssetLoader;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.FileSystemHelper;
import com.yesman.akytheralabs.dataholder.EntityPreviewModel;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.editor.EditorWidgetFactory;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/// Editable view of an animation asset's `.akmeta` sidecar: the engine's [AnimationLoader.Metadata]
/// plus the preview model only the editor reads. Lives on the animation's accessor, one per
/// identifier — see [AkytheraProject.EditorAnimationAccessor#metadata].
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
        return readSidecar(
            accessor.identifier(),
            editorCodec(accessor),
            new AnimationMetadataHolder(accessor, AnimationLoader.Metadata.DEFAULT)
        );
    }

    /// The engine's half of any animation's sidecar, for the walks that only need the reference
    /// pose chain and have no accessor to bind.
    public static AnimationLoader.Metadata metadataOf(final Identifier animationId) {
        return readSidecar(animationId, AnimationLoader.Metadata.CODEC, AnimationLoader.Metadata.DEFAULT);
    }

    /// `animationId`'s sidecar as `codec` reads it: the open project first, the game's packs
    /// second. `readMetadata` hands back the `absent` instance itself when a file is missing, so
    /// the fallback is an identity check.
    private static <T> T readSidecar(final Identifier animationId, final Codec<T> codec, final T absent) {
        T fromProject = AssetLoader.readMetadata(
            animationId,
            AkytheraProject.getProjectResourceManager(),
            codec,
            absent
        );

        if (fromProject != absent) {
            return fromProject;
        }

        return AssetLoader.readMetadata(
            animationId,
            ComponentsFactory.getImpl().getResourceManager(),
            codec,
            absent
        );
    }

    /// The sidecar's json for a holder of `accessor`: the engine's fields at the root through
    /// [AnimationLoader.Metadata#MAP_CODEC], with `preview_model` beside them. The file names the
    /// animation nowhere, so the accessor a decoded holder belongs to comes from here.
    private static Codec<AnimationMetadataHolder> editorCodec(final AkytheraProject.EditorAnimationAccessor accessor) {
        return RecordCodecBuilder.create(
            instance ->
                instance.group(
                    AnimationLoader.Metadata.MAP_CODEC.forGetter(AnimationMetadataHolder::makeMetadata),
                    EntityPreviewModel.CODEC.optionalFieldOf("preview_model")
                        .forGetter(ParseUtils.provideOptional(AnimationMetadataHolder::getPreviewModel))
                ).apply(instance, (metadata, previewModel) -> {
                    AnimationMetadataHolder holder = new AnimationMetadataHolder(accessor, metadata);
                    previewModel.ifPresent(holder::setPreviewModel);

                    return holder;
                })
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

    /// Writes the sidecar next to its asset, then drops the parsed clip: the metadata decides
    /// whether the file reads raw or additive, so the next reference must re-read it.
    public boolean save() {
        Path metadataFile = AkytheraProject.getMetadataFileOf(accessor.identifier());

        if (!FileSystemHelper.writeJson(metadataFile, editorCodec(accessor), this)) {
            AkytheraLabs.LOGGER.error("Failed to save animation metadata '{}'", metadataFile);
            return false;
        }

        AkytheraProject.getCurrentProject().invalidateEditorAnimation(accessor.identifier());

        return true;
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
