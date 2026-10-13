package com.yesman.akytheralabs.akythera.physics;

import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.client.core.asset.loader.binary.ClothPhysicsLoader;
import com.yesman.akythera.client.core.asset.mesh.SkeletalMesh;
import com.yesman.akythera.client.core.asset.physics.ClothPhysicsData;
import com.yesman.akythera.client.core.asset.physics.ClothSimulationParameters;
import com.yesman.akythera.core.asset.loader.ResourceLoadingFailureException;
import com.yesman.akythera.core.asset.loader.binary.BinaryAssetLoader;
import com.yesman.akythera.core.asset.physics.ArmaturePhysicsData;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.FileSystemHelper;
import com.yesman.akytheralabs.dataholder.DataHolder;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.main.AssetExportManager;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/// Editable view of the cloth a skeletal mesh carries. One per mesh identifier — see
/// [AkytheraProject#getClothPhysicsHolder].
public final class ClothPhysicsDataHolder {
    /// [DataHolder#EMPTY_REFERENCE] while the cloth collides with nothing.
    @EditorEditable(label = LangKeys.GUI_CLOTH_PHYSICS_SCREEN_WIDGET_ARMATURE_PHYSICS, order = 0, rebuildOnWrite = true)
    private AssetAccessor<? extends ArmaturePhysicsData> armaturePhysics;

    @EditorEditable(label = LangKeys.GUI_CLOTH_PHYSICS_SCREEN_WIDGET_PARAMETERS, order = 1)
    private final ClothSimulationParametersHolder parameters;

    /// Weight per mesh position index. Replaced whole on every commit, never written in place.
    private float[] weights;

    /// Mesh position index pairs, flattened as `root, offset`. Replaced whole on every commit, never
    /// written in place.
    private int[] offsetPairs;

    /// The mesh this cloth belongs to.
    private final AssetAccessor<? extends SkeletalMesh> mesh;

    private ClothPhysicsDataHolder(final AssetAccessor<? extends SkeletalMesh> mesh, final @Nullable ClothPhysicsData data) {
        this.mesh = mesh;
        this.armaturePhysics = AkytheraProject.getCurrentProject().getOrIssueEditorArmaturePhysicsAccessor(
            data == null || data.armaturePhysics() == null ? DataHolder.EMPTY_REFERENCE : data.armaturePhysics().identifier()
        );
        this.parameters = new ClothSimulationParametersHolder(data == null ? ClothSimulationParameters.DEFAULT : data.parameters());
        this.weights = data == null ? new float[0] : data.weights();
        this.offsetPairs = data == null ? new int[0] : data.offsetPairs();
    }

    /// The cloth of `mesh` as it stands on disk, or an empty cloth when it has none or it cannot be read.
    public static ClothPhysicsDataHolder readFrom(final AssetAccessor<? extends SkeletalMesh> mesh) {
        ClothPhysicsData data = null;

        try {
            data = ClothPhysicsLoader.read(mesh.identifier(), AkytheraProject.getProjectResourceManager());
        } catch (ResourceLoadingFailureException e) {
            AkytheraLabs.LOGGER.error("Failed to read the cloth of '{}'", mesh.identifier(), e);
        }

        return new ClothPhysicsDataHolder(mesh, data);
    }

    public ClothPhysicsData makeObject() {
        return new ClothPhysicsData(armaturePhysicsOrNull(), parameters.makeObject(), weights, offsetPairs);
    }

    /// Writes the weights and offset pairs `.akasset` and the `.akmeta` sidecar, then drops the parsed
    /// mesh so its next reference loads the new cloth.
    public boolean save() {
        Identifier clothId = ClothPhysicsLoader.clothPathOf(mesh.identifier());

        if (clothId == null) {
            AkytheraLabs.LOGGER.error("'{}' is not under the mesh directory and cannot carry a cloth", mesh.identifier());
            return false;
        }

        AssetExportManager.AkAssetWriter writer = new AssetExportManager.AkAssetWriter();
        writer.addFloatArray(BinaryAssetLoader.DataType.CLOTH_WEIGHTS, weights);
        writer.addIntArray(BinaryAssetLoader.DataType.CLOTH_OFFSET_PAIRS, offsetPairs);

        Path binaryFile = AkytheraProject.getBinaryAssetFileOf(clothId);

        try {
            Files.createDirectories(binaryFile.getParent());
            Files.write(binaryFile, writer.toBytes());
        } catch (IOException e) {
            AkytheraLabs.LOGGER.error("Failed to save cloth weights '{}'", binaryFile, e);
            return false;
        }

        ClothPhysicsData.Metadata metadata = new ClothPhysicsData.Metadata(armaturePhysicsOrNull(), parameters.makeObject());

        if (!FileSystemHelper.writeJson(AkytheraProject.getMetadataFileOf(clothId), ClothPhysicsData.Metadata.CODEC, metadata)) {
            AkytheraLabs.LOGGER.error("Failed to save cloth metadata of '{}'", clothId);
            return false;
        }

        AkytheraProject.getCurrentProject().invalidateEditorMesh(mesh.identifier());

        return true;
    }

    private @Nullable AssetAccessor<? extends ArmaturePhysicsData> armaturePhysicsOrNull() {
        return armaturePhysics.identifier().equals(DataHolder.EMPTY_REFERENCE) ? null : armaturePhysics;
    }

    /// Pads with kinematic weights, or truncates, to `positionCount`. Keeps the array identity when it
    /// already fits.
    public void fitWeights(final int positionCount) {
        if (weights.length != positionCount) {
            weights = Arrays.copyOf(weights, positionCount);
        }
    }

    public AssetAccessor<? extends SkeletalMesh> getMesh() {
        return mesh;
    }

    public AssetAccessor<? extends ArmaturePhysicsData> getArmaturePhysics() {
        return armaturePhysics;
    }

    public void setArmaturePhysics(final AssetAccessor<? extends ArmaturePhysicsData> armaturePhysics) {
        this.armaturePhysics = armaturePhysics;
    }

    public ClothSimulationParametersHolder getParameters() {
        return parameters;
    }

    public float[] getWeights() {
        return weights;
    }

    public void setWeights(final float[] weights) {
        this.weights = weights;
    }

    public int[] getOffsetPairs() {
        return offsetPairs;
    }

    public void setOffsetPairs(final int[] offsetPairs) {
        this.offsetPairs = offsetPairs;
    }
}
