package com.yesman.akytheralabs.editor.debug;

import com.yesman.akythera.api.world.behavior.BehaviorTreeInstance;
import com.yesman.akythera.api.world.behavior.BrainComponent;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.client.api.event.BuiltInClientEventHooks;
import com.yesman.akythera.client.core.renderer.collider.ColliderDebugRenderer;
import com.yesman.akythera.core.math.physics.collider.component.ColliderComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/// Reads the brain of an entity from the integrated server, for the editor screens that mirror it. AkytheraLabs runs
/// only in a singleplayer world, so the server's objects are read directly, with no payload.
public final class BrainProbe {
    private BrainProbe() {}

    private static final int SIGHT_COLOR = 0xFF33AAFF;

    /// Draws the sight shape of every brain in the hitbox scene, in the server's last placement.
    public static void registerEvents() {
        BuiltInClientEventHooks.Render.COLLECT_DEBUG_COLLIDERS.registerEvent(event -> {
            BehaviorTreeInstance tree = treeOf(event.getEntityPatch().getOriginal());
            ColliderComponent<?> sight = tree == null ? null : tree.getSight();

            if (sight == null) return;

            event.add(new ColliderDebugRenderer.ColliderSubmit(
                sight.getCollider(), tree.getSightTransform().relativeTo(event.getEntityPatch().getModelTransform()), SIGHT_COLOR));
        });
    }

    /// The running tree of the server copy of `clientEntity`, or null when there is no integrated server or no brain.
    public static @Nullable BehaviorTreeInstance treeOf(final Entity clientEntity) {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        ServerLevel level = server == null ? null : server.getLevel(clientEntity.level().dimension());
        Entity serverEntity = level == null ? null : level.getEntity(clientEntity.getId());

        return serverEntity == null ? null : BrainComponent.treeOf(serverEntity);
    }

    /// The running tree of the server copy of `entityPatch`'s entity, or null.
    public static @Nullable BehaviorTreeInstance treeOf(final EntityPatch<?> entityPatch) {
        return treeOf(entityPatch.getOriginal());
    }
}
