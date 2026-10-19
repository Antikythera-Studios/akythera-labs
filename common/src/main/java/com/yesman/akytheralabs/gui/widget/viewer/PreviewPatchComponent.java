package com.yesman.akytheralabs.gui.widget.viewer;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.TransformInheritPolicy;
import com.yesman.akythera.api.component.impl.EquipmentSlotBinder;
import com.yesman.akythera.api.world.patch.entity.LivingEntityPatch;
import com.yesman.akythera.client.api.gui.viewer.EntityModelComponent;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewerComponent;
import com.yesman.akythera.core.blueprint.EntityPatchBlueprint;
import com.yesman.akythera.core.blueprint.ItemPatchBlueprint;
import com.yesman.akythera.core.blueprint.particle.ParticleAttachment;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.akythera.animation.PreviewItem;
import com.yesman.akytheralabs.akythera.blueprint.ComponentPaths;
import com.yesman.akytheralabs.akythera.entitypatch.EntityPatchBlueprintHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.ArmatureComponentHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.ComponentAttachableHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.ComponentDataHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.EquipmentSlotBinderHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.TransformComponentHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.TransformSocketHolder;
import com.yesman.akytheralabs.akythera.itempatch.ItemPatchBlueprintHolder;
import com.yesman.akytheralabs.dataholder.TransformHolder;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.main.processors.ResourceProcessor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemDisplayContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.function.Supplier;

/// The open project's entity patch of an animation's preview entity type, in an animation editor's [Model3DViewer]. It
/// places the viewer's [EntityModelComponent] by the patch's armature, holds the animation's [PreviewItem]s, and
/// resolves [ParticleAttachment#socketPath]s on the patch and on those items. A debug message names a missing patch,
/// and each preview item whose socket the patch lacks.
///
/// A preview item is drawn in the display context of the [EquipmentSlotBinderHolder] under its socket, else in the
/// third-person right hand. Its item patch's transform components sit under the socket, as an [EquipmentSlotBinder]
/// attaches them: `armature/right_hand_socket/trail_start`.
public final class PreviewPatchComponent implements Model3DViewerComponent {
    private final Supplier<@Nullable Identifier> previewEntityType;
    private final Supplier<List<PreviewItem>> previewItems;

    /// How each preview item is held, built again when its socket's holder changes.
    private final Map<PreviewItem, Binding> bindings = new HashMap<>();

    /// The model whose transform source this component installed.
    private @Nullable EntityModelComponent placedModel;

    public PreviewPatchComponent(final Supplier<@Nullable Identifier> previewEntityType, final Supplier<List<PreviewItem>> previewItems) {
        this.previewEntityType = previewEntityType;
        this.previewItems = previewItems;
    }

    @Override
    public void extractComponent(
        GuiGraphicsExtractor graphics, Model3DViewer model3DViewer, PoseStack poseStack, ByteBufferBuilder byteBuffer, int mouseX, int mouseY, float a
    ) {
        Identifier entityType = previewEntityType.get();
        EntityPatchBlueprintHolder patch = patch();
        EntityModelComponent model = model3DViewer.getComponent(EntityModelComponent.class);
        List<PreviewItem> items = previewItems.get();
        List<Component> messages = new ArrayList<>();

        if (entityType != null && patch == null) {
            messages.add(Component.translatable(LangKeys.GUI_ANIMATION_PREVIEW_PATCH_NOT_FOUND, entityType.toString()));
        }

        if (model != placedModel) {
            placedModel = model;

            if (model != null) {
                model.setTransformSource(() -> {
                    EntityPatchBlueprintHolder placing = patch();
                    ArmatureComponentHolder armature = placing == null ? null : ComponentAttachableHolder.flattenEnabled(placing)
                        .filter(ArmatureComponentHolder.class::isInstance)
                        .map(ArmatureComponentHolder.class::cast)
                        .findFirst()
                        .orElse(null);

                    return armature == null ? Transform.IDENTITY : space(model3DViewer, placing).modelTransform(armature);
                });
            }
        }

        bindings.keySet().retainAll(items);

        if (model != null && patch != null) {
            for (PreviewItem item : items) {
                Binding binding = binding(model3DViewer, patch, item);

                if (binding == null) {
                    messages.add(Component.translatable(
                        LangKeys.GUI_NOTIFY_TRACKS_SOCKET_NOT_FOUND, item.socket(), Component.translatable(LangKeys.GUI_ANIMATION_PREVIEW_PREVIEW_ITEM)
                    ));
                } else {
                    binding.model().extractComponent(graphics, model3DViewer, poseStack, byteBuffer, mouseX, mouseY, a);
                }
            }
        }

        model3DViewer.setDebugMessages(this, messages);
    }

    /// `socketPath`'s transform in viewer space, resolved as [ParticleAttachment#worldTransform] does: the entity's root
    /// for a blank path, else the patch's component at the path, else a component of a preview item's item patch under
    /// its socket (`<socket>/<path>`). `<slot>/<path>` reaches the socket of the patch's binder of that slot, as
    /// [LivingEntityPatch#getComponentAt] does. Null with no model, or when no transform component sits there.
    public @Nullable Transform socketTransform(final Model3DViewer viewer, final String socketPath) {
        if (viewer.getComponent(EntityModelComponent.class) == null) return null;
        if (socketPath.isEmpty()) return Transform.IDENTITY;

        EntityPatchBlueprintHolder patch = patch();

        if (patch == null) return null;

        TransformComponentHolder.Space space = space(viewer, patch);

        if (ComponentPaths.at(patch, socketPath) instanceof TransformComponentHolder component) return space.modelTransform(component);

        String[] parts = socketPath.split(ComponentAttachable.PATH_SEPARATOR, 2);
        EquipmentSlot slot = parts.length == 2
            ? Arrays.stream(EquipmentSlot.values()).filter(candidate -> candidate.getSerializedName().equals(parts[0])).findFirst().orElse(null)
            : null;
        String path = slot == null ? socketPath : ComponentPaths.known(patch).keySet().stream()
            .filter(known -> ComponentPaths.at(patch, known) instanceof TransformSocketHolder socket
                && socket.getComponents().values().stream().anyMatch(child -> child instanceof EquipmentSlotBinderHolder binder && binder.getEquipmentSlot() == slot))
            .findFirst()
            .map(known -> known + ComponentAttachable.PATH_SEPARATOR + parts[1])
            .orElse(socketPath);

        for (PreviewItem item : previewItems.get()) {
            String prefix = item.socket() + ComponentAttachable.PATH_SEPARATOR;
            Binding binding = path.startsWith(prefix) ? binding(viewer, patch, item) : null;
            ItemPatchBlueprintHolder itemPatch = binding == null
                ? null
                : AkytheraProject.getCurrentProject().<ItemPatchBlueprint, ItemPatchBlueprintHolder>getDataHolder(ResourceKey.create(
                    AkytheraRegistries.DataPack.ITEM_PATCH_KEY,
                    ResourceProcessor.projectId(AkytheraRegistries.DataPack.ITEM_PATCH_KEY, BuiltInRegistries.ITEM.getKey(item.item()))
                )).orElse(null);

            if (itemPatch == null) continue;

            ComponentAttachableHolder tree = new FoldedTree(ComponentAttachableHolder.foldChain(List.of(new FoldedTree(itemPatch.inheritedComponents()), itemPatch)));

            if (ComponentPaths.at(tree, path.substring(prefix.length())) instanceof TransformComponentHolder component) {
                return new TransformComponentHolder.Space(
                    holder -> ComponentAttachableHolder.hostOf(tree, holder),
                    _ -> Transform.IDENTITY,
                    () -> space.modelTransform(binding.socket())
                ).modelTransform(component);
            }
        }

        return null;
    }

    /// The open project's entity patch of the preview entity type, or null when there is none.
    public @Nullable EntityPatchBlueprintHolder patch() {
        Identifier entityType = previewEntityType.get();

        return entityType == null
            ? null
            : AkytheraProject.getCurrentProject().<EntityPatchBlueprint, EntityPatchBlueprintHolder>getDataHolder(ResourceKey.create(
                AkytheraRegistries.DataPack.ENTITY_PATCH_KEY,
                ResourceProcessor.projectId(AkytheraRegistries.DataPack.ENTITY_PATCH_KEY, entityType)
            )).orElse(null);
    }

    /// How `item` is held on `patch`, or null while the patch has no socket at its path.
    private @Nullable Binding binding(final Model3DViewer viewer, final EntityPatchBlueprintHolder patch, final PreviewItem item) {
        if (!(ComponentPaths.at(patch, item.socket()) instanceof TransformSocketHolder socket)) {
            bindings.remove(item);
            return null;
        }

        Binding bound = bindings.get(item);

        if (bound != null && bound.socket() == socket) return bound;

        ItemDisplayContext displayContext = socket.getComponents().values().stream()
            .filter(EquipmentSlotBinderHolder.class::isInstance)
            .map(EquipmentSlotBinderHolder.class::cast)
            .filter(binder -> !binder.isModel3d())
            .map(EquipmentSlotBinderHolder::getItemDisplayContext)
            .findFirst()
            .orElse(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);
        TransformComponentHolder.Space space = space(viewer, patch);
        ItemModelComponent model = new ItemModelComponent(item::item);

        model.setDisplayContext(displayContext);
        model.placeUnder(() -> space.modelTransform(socket), TransformHolder.identity(), () -> TransformInheritPolicy.LOC_ROT_SCA, null);

        Binding binding = new Binding(socket, model);

        bindings.put(item, binding);

        return binding;
    }

    /// The spaces of `patch`'s holders, posed by the viewer's model.
    private static TransformComponentHolder.Space space(final Model3DViewer viewer, final EntityPatchBlueprintHolder patch) {
        return new TransformComponentHolder.Space(
            holder -> ComponentAttachableHolder.hostOf(patch, holder),
            bone -> {
                EntityModelComponent model = viewer.getComponent(EntityModelComponent.class);

                return model == null ? Transform.IDENTITY : model.getArmatureComponent().getBoneModelTransform(bone);
            },
            () -> Transform.IDENTITY
        );
    }

    @Override
    public int drawOrder() {
        return 15; // after grid (1) and model (10), before trajectory (20)
    }

    @Override
    public void destroy() {
        bindings.clear();
    }

    /// How one preview item is held: drawn by `model` under `socket`.
    private record Binding(TransformSocketHolder socket, ItemModelComponent model) {
    }

    /// An item patch's components folded with its ancestors', as one host.
    private record FoldedTree(SequencedMap<String, ComponentDataHolder<?>> getComponents) implements ComponentAttachableHolder {
        @Override
        public List<Class<?>> attachableTypes() {
            return List.of();
        }
    }
}
