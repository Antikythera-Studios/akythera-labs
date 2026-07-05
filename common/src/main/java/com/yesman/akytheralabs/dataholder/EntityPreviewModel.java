package com.yesman.akytheralabs.dataholder;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.client.gui.widget.ModelPreviewer;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/// The model an editor previewer displays for a holder: the model id plus how to interpret it
/// (skeletal mesh vs vanilla entity type). Replaces the old `previewModelId` + `previewModelType`
/// field pair so the tuple travels as one value. Holders keep a single nullable field of this
/// type — null means no preview model has been chosen yet.
public record EntityPreviewModel(Identifier modelId, ModelPreviewer.EntityModelComponent.ModelType modelType) {
    public static final Codec<EntityPreviewModel> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Identifier.CODEC.fieldOf("model_id").forGetter(EntityPreviewModel::modelId),
        ModelPreviewer.EntityModelComponent.ModelType.CODEC.fieldOf("model_type").forGetter(EntityPreviewModel::modelType)
    ).apply(instance, EntityPreviewModel::new));

    /// Null-safe display: shows `model` on `previewer`, or clears the previous entity component
    /// when `model` is null (matching [ModelPreviewer#displayEntity]'s null contract).
    public static void display(ModelPreviewer previewer, @Nullable EntityPreviewModel model, boolean deletePrevious, boolean locateCameraToFront) {
        previewer.displayEntity(
            model == null ? null : model.modelId(),
            model == null ? null : model.modelType(),
            deletePrevious,
            locateCameraToFront
        );
    }
}
