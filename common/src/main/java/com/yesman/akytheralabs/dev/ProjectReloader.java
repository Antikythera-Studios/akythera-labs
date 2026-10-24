package com.yesman.akytheralabs.dev;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.yesman.akythera.api.event.BuiltInEventHooks;
import com.yesman.akythera.api.event.entity.EntitySpawnEvent;
import com.yesman.akythera.api.lifecycle.ComponentsFactory;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import com.yesman.akythera.mixinaccessor.common.EntityAccessor;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.FileSystemHelper;
import com.yesman.akytheralabs.gui.widget.main.BrokenEntriesModalGenerator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/// Applies the current project to the singleplayer world as temporary resource / data packs, and reloads
/// both sides until the datapack registries and every entity patch reflect them.
public final class ProjectReloader {
    private ProjectReloader() {}

    /// File name prefix of every temporary pack, followed by the hash code of the export.
    private static final String TEMP_PACK_PREFIX = "akythera_temp_";

    /// The temporary packs the world currently loads, or null when none.
    private static @Nullable TempPacks tempPacks;

    /// The latest pack switch. The next one starts once it completes.
    private static CompletableFuture<Void> pendingSwitch = CompletableFuture.completedFuture(null);

    /// The server steps [#reloadWorld] queued for the next client resource reload, or null when none.
    private static @Nullable Function<ReloadInstance, WorldReload> pendingWorldReload;

    /// The latest world reload the loading overlay shows, or null when none ran yet.
    private static @Nullable WorldReload activeWorldReload;

    /// The frame presented when the running world reload started, or null when none runs.
    private static @Nullable TextureTarget frozenFrame;

    /// Loads `project` into the world behind the loading overlay, then runs `onOpened`.
    public static void open(final AkytheraProject project, final Runnable onOpened) {
        project.compileAll();
        switchPacks(() -> exportTempPacks(project), onOpened);
    }

    /// Re-exports the project and reloads the world. Same gate as `File > Export`: broken entries are left
    /// out of the export, so the user is warned which files won't reach the world and confirms first.
    public static void reload(final Font font) {
        AkytheraProject project = AkytheraProject.getCurrentProjectOrNull();

        if (project == null) {
            close();
            return;
        }

        BrokenEntriesModalGenerator.confirmProceed(font, project, () -> switchPacks(() -> exportTempPacks(project), () -> {}));
    }

    /// Unloads the project's temporary packs from the world behind the loading overlay and deletes them.
    public static void close() {
        switchPacks(() -> null, () -> {});
    }

    /// Deletes the temporary packs once the game has closed every resource.
    public static void onGameClosed() {
        if (tempPacks != null) tempPacks.delete();
    }

    /// Deletes every temporary pack a crashed session left in `dataPackDir` and the resource pack directory.
    public static void deleteStaleTempPacks(final Path dataPackDir) {
        if (tempPacks != null) return;

        for (Path dir : List.of(dataPackDir, Minecraft.getInstance().getResourcePackDirectory())) {
            if (!Files.isDirectory(dir)) continue;

            try (Stream<Path> files = Files.list(dir)) {
                files.filter(file -> file.getFileName().toString().startsWith(TEMP_PACK_PREFIX)).forEach(FileSystemHelper::deleteRecursive);
            } catch (IOException e) {
                AkytheraLabs.LOGGER.warn("Failed to list {}", dir, e);
            }
        }
    }

    /// Writes `project` as temporary packs under a fresh hash code, so an archive the game holds open is
    /// never overwritten.
    private static TempPacks exportTempPacks(final AkytheraProject project) {
        Minecraft mc = Minecraft.getInstance();
        String name = TEMP_PACK_PREFIX + Integer.toHexString(Objects.hash(project.getProjectRoot(), System.nanoTime()));
        TempPacks packs = new TempPacks(
            mc.getResourcePackDirectory().resolve(name + "_resources.zip"),
            mc.getSingleplayerServer().getWorldPath(LevelResource.DATAPACK_DIR).resolve(name + "_data.zip")
        );

        project.exportResourcePack(packs.resourcePack());
        project.exportDataPack(packs.dataPack(), false);

        return packs;
    }

    /// Replaces [#tempPacks] with what `nextPacks` gives in both pack selections, reloads the world, deletes
    /// the replaced packs and runs `onSwitched`.
    private static void switchPacks(final Supplier<@Nullable TempPacks> nextPacks, final Runnable onSwitched) {
        Minecraft mc = Minecraft.getInstance();

        pendingSwitch = pendingSwitch
            .thenComposeAsync(_ -> {
                TempPacks previous = tempPacks;
                TempPacks next = nextPacks.get();
                PackRepository resourcePacks = mc.getResourcePackRepository();
                tempPacks = next;

                resourcePacks.reload();
                if (previous != null) resourcePacks.removePack(previous.resourcePackId());
                if (next != null) resourcePacks.addPack(next.resourcePackId());

                return reloadWorld(dataPacks -> {
                    dataPacks.reload();
                    Collection<String> selected = new ArrayList<>(dataPacks.getSelectedIds());
                    if (previous != null) selected.remove(previous.dataPackId());
                    if (next != null) selected.add(next.dataPackId());

                    return selected;
                }).whenComplete((_, _) -> {
                    if (previous != null) previous.delete();
                });
            }, mc)
            .whenCompleteAsync((_, error) -> {
                if (error != null) AkytheraLabs.LOGGER.error("Failed to reload the world", error);
                onSwitched.run();
            }, mc)
            .exceptionally(_ -> null);
    }

    /// Extends the client resource reload `client` with [#pendingWorldReload], so the loading overlay stays up
    /// until the server steps finish.
    public static ReloadInstance appendServerSteps(final ReloadInstance client) {
        Function<ReloadInstance, WorldReload> worldReload = pendingWorldReload;

        if (worldReload == null) return client;

        pendingWorldReload = null;
        activeWorldReload = worldReload.apply(client);

        return activeWorldReload;
    }

    /// Whether a world reload is queued or still running.
    public static boolean isReloadingWorld() {
        return pendingWorldReload != null || activeWorldReload != null && !activeWorldReload.isDone();
    }

    /// Reloads the client resources, then the server data packs `dataPackSelection` picks, the datapack
    /// registries on both sides and every entity patch, all behind one loading overlay.
    private static CompletableFuture<Void> reloadWorld(final Function<PackRepository, Collection<String>> dataPackSelection) {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();

        if (server == null) {
            return mc.reloadResourcePacks();
        }

        CompletableFuture<Void> serverSteps = new CompletableFuture<>();

        pendingWorldReload = client -> {
            WorldReload worldReload = new WorldReload(client, serverSteps);

            client.done()
                .thenCompose(_ -> server.submit(() -> server.reloadResources(dataPackSelection.apply(server.getPackRepository()))))
                .thenCompose(Function.identity())
                .thenRunAsync(() -> {
                    worldReload.finishedSteps.incrementAndGet();
                    // Server thread: re-decode the datapack registries from the freshly reloaded ResourceManager,
                    // then rebuild server-side patches off the renewed entries.
                    DataPackRegistryReloader.reloadServer(server);

                    for (ServerLevel level : server.getAllLevels()) {
                        level.getAllEntities().forEach(ProjectReloader::renewEntityPatch);
                    }

                    worldReload.finishedSteps.incrementAndGet();
                }, server)
                .thenRunAsync(() -> {
                    RegistryAccess clientAccess = StaticRegistryAccess.getRegistryAccessUnsafe();

                    if (clientAccess != null) {
                        DataPackRegistryReloader.copyServerToClient(server.registryAccess(), clientAccess);
                    }

                    if (mc.level != null) {
                        mc.level.entitiesForRendering().forEach(ProjectReloader::renewEntityPatch);
                    }
                }, mc)
                .whenComplete((_, error) -> {
                    if (error == null) {
                        serverSteps.complete(null);
                    } else {
                        serverSteps.completeExceptionally(error);
                    }
                });

            return worldReload;
        };

        AkytheraEditorLayoutManager layoutManager = AkytheraEditorLayoutManager.getInstance();
        RenderTarget presented = layoutManager.getPresentedRenderTarget();
        TextureTarget frame = new TextureTarget("Akythera world reload frame", presented.width, presented.height, false, GpuFormat.RGBA8_UNORM);

        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
            presented.getColorTexture(), frame.getColorTexture(), 0, 0, 0, 0, 0, presented.width, presented.height);
        frozenFrame = frame;
        layoutManager.suspendLayout();

        CompletableFuture<Void> clientReload = mc.reloadResourcePacks();

        return serverSteps.thenCompose(_ -> clientReload).whenCompleteAsync((_, _) -> {
            frozenFrame = null;
            frame.destroyBuffers();
            layoutManager.resumeLayout();
        }, mc);
    }

    /// Draws [#frozenFrame] into `target` in place of the level while a world reload runs.
    public static void drawFrozenFrame(final RenderTarget target) {
        if (frozenFrame == null || !isReloadingWorld()) return;

        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
            frozenFrame.getColorTexture(), target.getColorTexture(), 0, 0, 0, 0, 0,
            Math.min(frozenFrame.width, target.width), Math.min(frozenFrame.height, target.height));
    }

    /// Replaces `entity`'s patch with a freshly built one and replays the join-world event on it.
    private static void renewEntityPatch(final Entity entity) {
        EntityAccessor accessor = (EntityAccessor)entity;

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

    /// A client resource reload followed by the server steps of [#reloadWorld], reported to the loading overlay
    /// as one reload.
    private static final class WorldReload implements ReloadInstance {
        /// Share of the progress bar the client resource reload fills.
        private static final float CLIENT_SHARE = 0.8F;

        /// Data packs, then registries and server patches, then client registries and patches.
        private static final int SERVER_STEPS = 3;

        private final ReloadInstance client;

        /// Completes once the client reload and the server steps finish. Fails only when the client reload fails.
        private final CompletableFuture<?> done;

        /// Server steps finished so far.
        private final AtomicInteger finishedSteps = new AtomicInteger();

        private WorldReload(final ReloadInstance client, final CompletableFuture<Void> serverSteps) {
            this.client = client;
            this.done = client.done().thenCompose(_ -> serverSteps.exceptionally(_ -> null));
        }

        @Override
        public CompletableFuture<?> done() {
            return done;
        }

        @Override
        public float getActualProgress() {
            if (done.isDone()) return 1.0F;

            return client.getActualProgress() * CLIENT_SHARE + (1.0F - CLIENT_SHARE) * finishedSteps.get() / SERVER_STEPS;
        }
    }

    /// One export's temporary resource pack and data pack archives.
    private record TempPacks(Path resourcePack, Path dataPack) {
        private String resourcePackId() {
            return "file/" + resourcePack.getFileName();
        }

        private String dataPackId() {
            return "file/" + dataPack.getFileName();
        }

        private void delete() {
            FileSystemHelper.deleteRecursive(resourcePack, dataPack);
        }
    }
}
