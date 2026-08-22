package com.yesman.akytheralabs.editor.debug;

import com.yesman.akythera.api.data.modularize.node.ExecutionRecord;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.blueprint.script.DataDrivenAnimatorScript;
import com.yesman.akythera.mixinaccessor.common.EntityAccessor;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.editor.keybind.EditorKeyMappings;
import com.yesman.akytheralabs.gui.screen.DebuggableScreen;
import com.yesman.akytheralabs.mixin.MixinMob;
import com.yesman.akytheralabs.mixin.MixinPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/// Drives the entity the editor possesses: puts the player into spectator on the integrated server,
/// points its camera at the entity, and every client tick forwards the local player's look and movement
/// keys onto it. Also feeds the possessed entity's patch to the editor screen on top.
///
/// Possessing the local player only feeds the patch — the player already drives itself, so its game
/// mode and camera stay untouched.
///
/// [#possessedEntityId] and [#possessorId] are read on the server thread by [MixinMob] and [MixinPlayer].
public final class EntityDebugSession {
    private static final EntityDebugSession INSTANCE = new EntityDebugSession();

    public static EntityDebugSession getInstance() {
        return INSTANCE;
    }

    private volatile int possessedEntityId = -1;
    private volatile @Nullable UUID possessorId;

    /// False when the possessed entity is the local player.
    private boolean drivesEntity;

    /// The game mode to hand back on [#end]. Written and read on the server thread.
    private volatile @Nullable GameType previousGameType;

    /// Set by [#tryCaptureKey]; cleared by the tick that writes the log.
    private boolean captureRequested;

    public void begin(final Entity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;

        end();

        possessedEntityId = entity.getId();
        drivesEntity = entity != player;

        if (!drivesEntity) {
            return;
        }

        IntegratedServer server = minecraft.getSingleplayerServer();
        UUID playerId = player.getUUID();
        int entityId = entity.getId();

        possessorId = playerId;

        server.execute(() -> {
            ServerPlayer serverPlayer = server.getPlayerList().getPlayer(playerId);
            Entity target = serverPlayer.level().getEntity(entityId);

            if (target == null) {
                return;
            }

            previousGameType = serverPlayer.gameMode.getGameModeForPlayer();
            serverPlayer.setGameMode(GameType.SPECTATOR);
            serverPlayer.setCamera(target);
        });
    }

    public void end() {
        if (possessedEntityId == -1) {
            return;
        }

        Entity possessed = getPossessedEntity();
        EntityPatch<?> possessedPatch =
            possessed == null ? null : ((EntityAccessor)possessed).akythera$getEntityPatch();

        if (possessedPatch != null) {
            clearLiveRecords(possessedPatch);
        }

        possessedEntityId = -1;

        if (!drivesEntity) {
            return;
        }

        drivesEntity = false;
        possessorId = null;

        Minecraft minecraft = Minecraft.getInstance();
        IntegratedServer server = minecraft.getSingleplayerServer();
        UUID playerId = minecraft.player.getUUID();

        server.execute(() -> {
            ServerPlayer serverPlayer = server.getPlayerList().getPlayer(playerId);
            serverPlayer.setCamera(null);

            GameType restored = previousGameType;

            if (restored != null) {
                serverPlayer.setGameMode(restored);
            }

            previousGameType = null;
        });
    }

    public boolean isActive() {
        return possessedEntityId != -1;
    }

    /// The client-side copy of the possessed entity, null once it leaves the level.
    public @Nullable Entity getPossessedEntity() {
        if (possessedEntityId == -1) {
            return null;
        }

        return Minecraft.getInstance().level.getEntity(possessedEntityId);
    }

    /// Whether `entity` is the one the editor drives.
    public boolean isPossessed(final Entity entity) {
        return possessedEntityId != -1 && entity.getId() == possessedEntityId;
    }

    /// Whether `player` is the one whose camera the editor holds.
    public boolean isPossessor(final Player player) {
        UUID possessor = possessorId;

        return possessor != null && possessor.equals(player.getUUID());
    }

    /// Honours [EditorKeyMappings#PRINT_SCRIPT_LOG] while a session is running, and reports whether
    /// the event was consumed. The next tick writes the possessed entity's recorded runs to the log
    /// once; the record itself is rebuilt every tick.
    ///
    /// Runs ahead of all vanilla handling, off the keyboard handler rather than the focus chain, so
    /// it stays reachable while the viewport holds the pointer.
    public boolean tryCaptureKey(final KeyEvent event) {
        if (!isActive() || !EditorKeyMappings.PRINT_SCRIPT_LOG.matches(event)) {
            return false;
        }

        captureRequested = true;

        return true;
    }

    public void clientTick() {
        if (possessedEntityId == -1) {
            return;
        }

        Entity entity = getPossessedEntity();

        if (entity == null) {
            return;
        }

        if (drivesEntity) {
            driveEntity(Minecraft.getInstance(), entity.getId());
        }

        EntityPatch<?> patch = ((EntityAccessor)entity).akythera$getEntityPatch();

        if (patch == null) {
            return;
        }

        if (captureRequested) {
            captureRequested = false;
            printScriptLog(patch);
        }

        if (AkytheraEditorLayoutManager.getInstance().getRecentEditorScreen() instanceof DebuggableScreen debuggable) {
            debuggable.onDebugTick(patch);
        }

        clearLiveRecords(patch);
    }

    /// Drops the script's per-canvas records. They fill across the render frames between two ticks, so
    /// a canvas nothing sampled reads empty instead of holding what it last showed.
    private static void clearLiveRecords(final EntityPatch<?> entityPatch) {
        DataDrivenAnimatorScript script = AnimatorProbe.dataDrivenScript(entityPatch);

        if (script != null) {
            ((ScriptExecutionRecords)(Object)script).akytheralabs$clearLiveRecords();
        }
    }

    /// Writes the entity's recorded animator-script runs to the log.
    private static void printScriptLog(final EntityPatch<?> entityPatch) {
        DataDrivenAnimatorScript dataDrivenScript = AnimatorProbe.dataDrivenScript(entityPatch);

        if (dataDrivenScript == null) {
            AkytheraLabs.LOGGER.info("Possessed entity has no data-driven animator script to record");
            return;
        }

        ScriptExecutionRecords records = (ScriptExecutionRecords)(Object)dataDrivenScript;

        logRecord("construct", records.akytheralabs$getConstructRecord());
        logRecord("update", records.akytheralabs$getUpdateRecord());
        logRecord("getFinalPose", records.akytheralabs$getPoseRecord());

        records.akytheralabs$getLiveRecords().forEach((canvasKey, record) -> logRecord("canvas " + canvasKey, record));
    }

    private static void logRecord(final String entryPoint, final @Nullable ExecutionRecord record) {
        if (record == null || record.isEmpty()) {
            AkytheraLabs.LOGGER.info("--- {}: nothing recorded", entryPoint);
            return;
        }

        AkytheraLabs.LOGGER.info("--- {}\n{}", entryPoint, record.format());
    }

    /// Snapshots the local player's look and keys on the render thread, then applies them to the
    /// possessed entity on the server thread.
    private static void driveEntity(final Minecraft minecraft, final int entityId) {
        IntegratedServer server = minecraft.getSingleplayerServer();
        LocalPlayer player = minecraft.player;

        Input keys = player.input.keyPresses;
        float yRot = player.getYRot();
        float xRot = player.getXRot();
        ResourceKey<Level> dimension = minecraft.level.dimension();

        server.execute(() -> {
            ServerLevel level = server.getLevel(dimension);

            if (level == null) {
                return;
            }

            Entity target = level.getEntity(entityId);

            if (target == null) {
                return;
            }

            target.setYRot(yRot);
            target.setXRot(xRot);
            target.setYHeadRot(yRot);

            if (target instanceof LivingEntity living) {
                living.yBodyRot = yRot;
                living.zza = (keys.forward() ? 1.0F : 0.0F) - (keys.backward() ? 1.0F : 0.0F);
                living.xxa = (keys.left() ? 1.0F : 0.0F) - (keys.right() ? 1.0F : 0.0F);
                living.setSpeed((float)living.getAttributeValue(Attributes.MOVEMENT_SPEED));
                living.setJumping(keys.jump());
                living.setSprinting(keys.sprint());
                living.setShiftKeyDown(keys.shift());
            }
        });
    }
}
