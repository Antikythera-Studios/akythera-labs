package com.yesman.akytheralabs.dataholder;

import com.yesman.akythera.client.api.gui.viewer.EntityModelComponent;
import net.minecraft.resources.Identifier;

/// The model an entity type is previewed with: the model id, plus how to read it — as a skeletal
/// mesh or as a vanilla entity model. Half of an [EntityPreviewModelMapping] binding, the armature
/// being the other.
public record EntityPreviewModel(Identifier modelId, EntityModelComponent.ModelType modelType) {
}
