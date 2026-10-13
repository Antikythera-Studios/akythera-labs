package com.yesman.akytheralabs.dataholder;

import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.lifecycle.ComponentsFactory;
import com.yesman.akythera.client.api.gui.viewer.EntityModelComponent;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.client.api.mesh.MeshManager;
import com.yesman.akythera.client.core.asset.mesh.SkeletalMesh;
import com.yesman.akythera.compat.CompatibleMod;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.platform.ModPlatformProvider;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.gui.widget.viewer.PreviewMappingWarningCard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/// What each [EntityType] is previewed as: a mesh ([EntityPreviewModel]) and an [Armature]. Editor
/// previewers name an entity type and read the pair from here.
///
/// The armature side is stored in [ComponentsFactory], which the engine reads while setting up a
/// vanilla render state, and is only reached through this class.
public final class EntityPreviewModelMapping {
    private static final Map<EntityType<?>, EntityPreviewModel> MESHES = new HashMap<>();

    private EntityPreviewModelMapping() {
    }

    /// Whether a vanilla entity model can be animated, which Player Animation Library provides.
    public static boolean vanillaModelsAnimatable() {
        return ModPlatformProvider.get().isModLoaded(CompatibleMod.PLAYER_ANIMATION_LIBRARY.getModId());
    }

    public static @Nullable EntityPreviewModel getMesh(EntityType<?> entityType) {
        return MESHES.get(entityType);
    }

    /// Binds `entityType`'s mesh. Null removes the binding.
    public static void setMesh(EntityType<?> entityType, @Nullable EntityPreviewModel mesh) {
        if (mesh == null) {
            MESHES.remove(entityType);
        } else {
            MESHES.put(entityType, mesh);
        }
    }

    public static @Nullable AssetAccessor<? extends Armature> getArmature(EntityType<?> entityType) {
        return ComponentsFactory.getImpl().getExternalArmatureMappingFor(entityType);
    }

    /// Binds `entityType`'s armature. Null removes the binding.
    public static void setArmature(EntityType<?> entityType, @Nullable AssetAccessor<? extends Armature> armature) {
        ComponentsFactory.getImpl().registerDefaultArmature(entityType, armature);
    }

    public static void clear() {
        MESHES.clear();
        ComponentsFactory.getImpl().clearArmatureMappings();
    }

    /// Binds every previewable entity type to its own vanilla model. Player Animation Library is
    /// what poses such a model from an [Armature], so without it this binds nothing.
    public static void fillVanillaBindings() {
        if (!vanillaModelsAnimatable()) return;

        for (EntityType<?> type : previewableTypes()) {
            setMesh(type, new EntityPreviewModel(
                BuiltInRegistries.ENTITY_TYPE.getKey(type), EntityModelComponent.ModelType.VANILLA_ENTITY));
        }
    }

    /// Every entity type holding a mesh binding, an armature binding, or both.
    public static List<EntityType<?>> boundTypes() {
        return BuiltInRegistries.ENTITY_TYPE.stream()
            .filter(type -> MESHES.containsKey(type) || getArmature(type) != null)
            .sorted(Comparator.comparing(EntityPreviewModelMapping::label))
            .toList();
    }

    /// Every entity type the previewer can draw: the render dispatcher's living-renderer entries,
    /// plus the player (kept in a separate map).
    public static List<EntityType<?>> previewableTypes() {
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();

        return Stream.concat(
                dispatcher.renderers.entrySet().stream()
                    .filter(e -> e.getValue() instanceof LivingEntityRenderer<?, ?, ?>)
                    .map(Map.Entry::getKey),
                Stream.<EntityType<?>>of(EntityTypes.PLAYER)
            )
            .distinct()
            .sorted(Comparator.comparing(EntityPreviewModelMapping::label))
            .toList();
    }

    public static String label(EntityType<?> entityType) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entityType).toString();
    }

    /// The entity type `id` names, or null when the registry holds none.
    public static @Nullable EntityType<?> typeOf(@Nullable Identifier id) {
        return id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)
            ? null
            : BuiltInRegistries.ENTITY_TYPE.getValue(id);
    }

    /// Shows `entityTypeId`'s bound mesh on `previewer`, skinned by its bound armature, and returns
    /// the installed component. An unknown entity type, or one with no mesh bound, installs nothing
    /// and answers null.
    ///
    /// A picker previews with this: its rows already read the binding, so a row that shows nothing is
    /// already telling the user what it knows.
    public static @Nullable EntityModelComponent display(
        Model3DViewer previewer,
        @Nullable Identifier entityTypeId,
        boolean deletePrevious,
        boolean locateCameraToFront
    ) {
        EntityType<?> entityType = typeOf(entityTypeId);
        EntityPreviewModel mesh = entityType == null ? null : getMesh(entityType);

        if (mesh == null) {
            if (deletePrevious) previewer.removeAllComponents(EntityModelComponent.class);
            return null;
        }

        PreviewMappingWarningCard.clear(previewer);

        AssetAccessor<? extends Armature> armature = getArmature(entityType);

        if (mesh.modelType() == EntityModelComponent.ModelType.VANILLA_ENTITY) {
            return previewer.displayEntity(
                mesh.modelId(), mesh.modelType(), armature, deletePrevious, locateCameraToFront);
        }

        AssetAccessor<? extends SkeletalMesh> skeletalMesh = resolveSkeletalMesh(mesh.modelId());

        if (skeletalMesh == null) {
            if (deletePrevious) previewer.removeAllComponents(EntityModelComponent.class);
            return null;
        }

        return previewer.displaySkeletalMesh(skeletalMesh, armature, deletePrevious, locateCameraToFront);
    }

    /// The project's skeletal mesh accessor for `meshId`, or null when it names none.
    ///
    /// Resolved against the project's own merged listing rather than [MeshManager]: a mesh the
    /// project owns is never registered there, and one that is reached through
    /// [MeshManager#CODEC] reports [AssetAccessor.Type#STATIC_MESH] until it is first loaded.
    @SuppressWarnings("unchecked")
    public static @Nullable AssetAccessor<? extends SkeletalMesh> resolveSkeletalMesh(Identifier meshId) {
        return (AssetAccessor<? extends SkeletalMesh>) AkytheraProject.getCurrentProject()
            .listAllMeshAccessors()
            .filter(acc -> acc.identifier().equals(meshId) && acc.checkType(AssetAccessor.Type.SKELETAL_MESH))
            .findFirst()
            .orElse(null);
    }

    /// [#display], and a known entity type with no mesh bound raises [PreviewMappingWarningCard] over
    /// the view — so the gap names itself and the mapping modal is one press away.
    ///
    /// An editor screen previews with this: it is showing work, not offering a choice, and a blank
    /// view there says nothing about why.
    public static @Nullable EntityModelComponent displayOrWarn(
        Model3DViewer previewer,
        Font font,
        @Nullable Identifier entityTypeId,
        boolean deletePrevious,
        boolean locateCameraToFront
    ) {
        EntityModelComponent component = display(previewer, entityTypeId, deletePrevious, locateCameraToFront);

        if (component == null && typeOf(entityTypeId) != null) {
            PreviewMappingWarningCard.raise(previewer, font, entityTypeId);
        }

        return component;
    }
}
