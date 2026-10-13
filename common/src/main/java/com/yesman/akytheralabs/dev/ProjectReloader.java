package com.yesman.akytheralabs.dev;

import com.yesman.akythera.api.event.BuiltInEventHooks;
import com.yesman.akythera.api.event.entity.EntitySpawnEvent;
import com.yesman.akythera.api.lifecycle.ComponentsFactory;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import com.yesman.akythera.mixinaccessor.common.EntityAccessor;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.gui.widget.main.BrokenEntriesModalGenerator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

/// Re-exports the project into the world's pack directories and reloads them. Same gate as
/// `File > Export`: broken entries are left out of the export, so the user is warned which files
/// won't reach the world and confirms before anything is written.
public final class ProjectReloader {
    private ProjectReloader() {}

    public static void reload(final Font font) {
        AkytheraProject project = AkytheraProject.getCurrentProjectOrNull();

        if (project == null) {
            applyProject(null);
            return;
        }

        BrokenEntriesModalGenerator.confirmProceed(font, project, () -> applyProject(project));
    }

    /// Exports `project` (when there is one) as the world's resource / data packs, then reloads both
    /// sides and rebuilds every entity patch off the renewed registries.
    private static void applyProject(final @Nullable AkytheraProject project) {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();

        if (project != null) {
            Path dataPackDir = server.getWorldPath(LevelResource.DATAPACK_DIR);
            String resourcePackName = project.exportAsPacks(mc.getResourcePackDirectory(), dataPackDir);
            PackRepository repository = mc.getResourcePackRepository();
            repository.reload();

            if (repository.addPack("file/" + resourcePackName)) {
                mc.options.updateResourcePacks(repository);
            }
        }

        server.reloadResources(server.getPackRepository().getSelectedIds())
            .thenRunAsync(() -> {
                // Server thread: re-decode the datapack registries from the freshly reloaded
                // ResourceManager and refill them in place, then rebuild server-side patches so
                // they read the renewed entries.
                DataPackRegistryReloader.reloadServer(server);
                reloadServerEntityPatches(server);

                // Hop to the render thread for the in-process client copy + the single client
                // resource reload (single-player owner only; no networking).
                mc.execute(() -> {
                    RegistryAccess clientAccess = StaticRegistryAccess.getRegistryAccessUnsafe();

                    if (clientAccess != null) {
                        DataPackRegistryReloader.copyServerToClient(server.registryAccess(), clientAccess);
                    }

                    // Client-side patches read reloaded assets, so rebuild them only once the
                    // single resource-pack reload (and its lone loading overlay) completes.
                    mc.reloadResourcePacks().thenRunAsync(() -> reloadClientEntityPatches(mc), mc);
                });
            }, server);
    }

    /// Render-thread: rebuild every client-level entity patch from freshly reloaded data.
    private static void reloadClientEntityPatches(final Minecraft mc) {
        ClientLevel level = mc.level;

        if (level == null) {
            return;
        }

        level.entitiesForRendering().forEach(ProjectReloader::renewEntityPatch);
    }

    /// Server-thread: rebuild every server-level entity patch from freshly reloaded data.
    private static void reloadServerEntityPatches(final IntegratedServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            level.getAllEntities().forEach(ProjectReloader::renewEntityPatch);
        }
    }

    /// Replaces `entity`'s patch with a freshly built one and replays the join-world event on it.
    private static void renewEntityPatch(final Entity entity) {
        EntityAccessor accessor = (EntityAccessor)entity;

        // TODO: Fill data registries with newly generated entries in editor

        // Reload all components, provided by components factory to get refreshed data pack
        EntityPatch<?> entityPatch = ComponentsFactory.getImpl().getEntityPatch(entity);
        accessor.akythera$setEntityPatch(entityPatch);

        if (entityPatch != null) {
            BuiltInEventHooks.Entity.SPAWNED.postWithListener(
                new EntitySpawnEvent(entityPatch, entity.level(), false),
                entityPatch.getEntityEventListener()
            );
        }
    }
}
