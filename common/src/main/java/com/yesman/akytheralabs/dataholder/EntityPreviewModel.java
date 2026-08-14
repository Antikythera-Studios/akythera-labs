package com.yesman.akytheralabs.dataholder;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.client.gui.widget.viewer.EntityModelComponent;
import com.yesman.akythera.client.gui.widget.viewer.Model3DViewer;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/// The model an editor previewer displays for a holder: the model id, plus how to read it — as a
/// skeletal mesh or as a vanilla entity type. Holders keep one nullable field of this type; null
/// means no preview model has been chosen.
public record EntityPreviewModel(Identifier modelId, EntityModelComponent.ModelType modelType) {
    public static final Codec<EntityPreviewModel> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Identifier.CODEC.fieldOf("model_id").forGetter(EntityPreviewModel::modelId),
        EntityModelComponent.ModelType.CODEC.fieldOf("model_type").forGetter(EntityPreviewModel::modelType)
    ).apply(instance, EntityPreviewModel::new));

    /// Null-safe display: shows `model` on `previewer`, or clears the previous entity component
    /// when `model` is null (matching [Model3DViewer#displayEntity]'s null contract).
    public static void display(Model3DViewer previewer, @Nullable EntityPreviewModel model, boolean deletePrevious, boolean locateCameraToFront) {
        previewer.displayEntity(
            model == null ? null : model.modelId(),
            model == null ? null : model.modelType(),
            deletePrevious,
            locateCameraToFront
        );
    }
}
