package com.yesman.akytheralabs.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.yesman.akythera.api.animation.animator.AnimationLayer;
import com.yesman.akythera.api.component.impl.ArmatureComponent;
import com.yesman.akythera.api.input.InputAction;
import com.yesman.akythera.api.world.ability.AbilityEffectInstance;
import com.yesman.akythera.api.world.ability.AbilitySystemComponent;
import com.yesman.akythera.api.world.patch.AkytheraPatches;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.api.world.patch.entity.LivingEntityPatch;
import com.yesman.akythera.core.animation.AnimationLayerSignature;
import com.yesman.akythera.core.animation.animator.BlendingAnimator;
import com.yesman.akythera.core.animation.driver.AnimationDriver;
import com.yesman.akythera.core.animation.playable.AnimationMontage;
import com.yesman.akythera.core.animation.playable.AnimationSequence;
import com.yesman.akythera.core.animation.playable.BlendSpace2D;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import com.yesman.akythera.core.blueprint.script.ScriptVariables;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.dev.ViewportCamera;
import com.yesman.akytheralabs.editor.debug.AnimatorProbe;
import com.yesman.akytheralabs.gui.panel.main.WorldviewPanel;
import com.yesman.akytheralabs.gui.screen.MainEditorScreen;
import com.yesman.akytheralabs.mixin.AnimationLayerAccessor;
import com.yesman.akytheralabs.mixin.BlendingAnimatorAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;

/// The [McpTool]s that act on the singleplayer world: entities, input and game time.
final class McpWorldTools {
    /// Longest text a value of an entity state keeps before it is cut.
    private static final int VALUE_TEXT_LIMIT = 200;
    private static final long TICK_MILLIS = 50L;

    private McpWorldTools() {}

    static void registerAll() {
        McpTools.register(McpTool.of(
            "summon_entity",
            "Spawns entities in the singleplayer world, like /summon. Returns the UUID of each spawned entity.",
            """
            {
              "type": "object",
              "properties": {
                "entity": {"type": "string", "description": "An entity type id, e.g. 'minecraft:zombie'."},
                "x": {"type": "number"}, "y": {"type": "number"}, "z": {"type": "number"},
                "count": {"type": "integer", "minimum": 1, "maximum": 64, "description": "Default: 1."}
              },
              "required": ["entity"]
            }
            """,
            arguments -> {
                Identifier id = McpTools.requireId(arguments.get("entity").getAsString());

                if (!BuiltInRegistries.ENTITY_TYPE.containsKey(id)) return McpToolResult.error("Unknown entity type: " + id);

                EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(id);
                int count = arguments.has("count") ? Math.clamp(arguments.get("count").getAsInt(), 1, 64) : 1;

                return McpTools.onServer(player -> {
                    Vec3 front = player.position().add(player.getLookAngle().multiply(3.0D, 0.0D, 3.0D));
                    Vec3 pos = arguments.has("x") && arguments.has("y") && arguments.has("z")
                        ? new Vec3(arguments.get("x").getAsDouble(), arguments.get("y").getAsDouble(), arguments.get("z").getAsDouble())
                        : front;
                    List<String> spawned = new ArrayList<>();

                    for (int i = 0; i < count; i++) {
                        Entity entity = type.spawn(player.level(), BlockPos.containing(pos), EntitySpawnReason.COMMAND);
                        if (entity != null) spawned.add(entity.getUUID().toString());
                    }

                    if (spawned.isEmpty()) return McpToolResult.error("Failed to spawn " + id);

                    return McpToolResult.text("Spawned " + id + " at " + BlockPos.containing(pos).toShortString() + ":\n" + String.join("\n", spawned));
                });
            }
        ));

        McpTools.register(McpTool.of(
            "equip_item",
            "Puts an item into an equipment slot of the local player, or of the living entity with the given UUID. "
                + "An item already in the player's slot goes back to the player's inventory.",
            """
            {
              "type": "object",
              "properties": {
                "item": {"type": "string", "description": "An item id, e.g. 'minecraft:iron_sword'."},
                "slot": {"type": "string", "enum": ["mainhand", "offhand", "head", "chest", "legs", "feet", "body", "saddle"], "description": "Default: mainhand."},
                "count": {"type": "integer", "minimum": 1, "maximum": 99, "description": "Default: 1."},
                "entity": {"type": "string", "description": "UUID of the entity to equip. Default: the local player."}
              },
              "required": ["item"]
            }
            """,
            arguments -> {
                Identifier id = McpTools.requireId(arguments.get("item").getAsString());

                if (!BuiltInRegistries.ITEM.containsKey(id)) return McpToolResult.error("Unknown item: " + id);

                EquipmentSlot slot = EquipmentSlot.byName(arguments.has("slot") ? arguments.get("slot").getAsString() : "mainhand");
                int count = arguments.has("count") ? Math.clamp(arguments.get("count").getAsInt(), 1, 99) : 1;
                UUID uuid = arguments.has("entity") ? UUID.fromString(arguments.get("entity").getAsString()) : null;

                return McpTools.onServer(player -> {
                    ServerLevel level = player.level();
                    Entity target = uuid == null ? player : level.getEntity(uuid);

                    if (!(target instanceof LivingEntity living)) return McpToolResult.error("No living entity with UUID " + uuid + " in " + level.dimension().identifier());

                    ItemStack previous = living.getItemBySlot(slot).copy();
                    living.setItemSlot(slot, new ItemStack(BuiltInRegistries.ITEM.getValue(id), count));

                    if (living == player && !previous.isEmpty()) player.getInventory().placeItemBackInInventory(previous);

                    return McpToolResult.text("Equipped " + count + " " + id + " in " + slot.getName() + " of " + living.getName().getString());
                });
            }
        ));

        McpTools.register(McpTool.of(
            "run_command",
            "Runs a Minecraft chat command as the local player, with the player's permissions, and returns the command's chat output.",
            """
            {
              "type": "object",
              "properties": {
                "command": {"type": "string", "description": "The command with or without the leading '/', e.g. 'time set day'."}
              },
              "required": ["command"]
            }
            """,
            arguments -> {
                String command = arguments.get("command").getAsString();

                return McpTools.onServer(player -> {
                    List<String> output = new ArrayList<>();
                    CommandSource capture = new CommandSource() {
                        @Override
                        public void sendSystemMessage(final Component message) {
                            output.add(message.getString());
                        }

                        @Override
                        public boolean acceptsSuccess() {
                            return true;
                        }

                        @Override
                        public boolean acceptsFailure() {
                            return true;
                        }

                        @Override
                        public boolean shouldInformAdmins() {
                            return false;
                        }
                    };

                    player.level().getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack().withSource(capture), command);

                    return McpToolResult.text(output.isEmpty() ? "(no output)" : String.join("\n", output));
                });
            }
        ));

        McpTools.register(McpTool.of(
            "get_entity_state",
            "Returns what an entity runs as JSON: its entity patch and components, each animation layer's playing animation "
                + "and montage with their times, the active state machine states, the variables of its animator script and "
                + "patch script, and its abilities, ability effects and ability tags.",
            """
            {
              "type": "object",
              "properties": {
                "entity": {"type": "string", "description": "UUID of the entity. Default: the local player."},
                "side": {"type": "string", "enum": ["client", "server"], "description": "Which side's copy to read. Default: client."}
              }
            }
            """,
            arguments -> {
                UUID uuid = arguments.has("entity") ? UUID.fromString(arguments.get("entity").getAsString()) : null;
                boolean server = arguments.has("side") && "server".equals(arguments.get("side").getAsString());

                if (server) {
                    return McpTools.onServer(player -> {
                        Entity entity = uuid == null ? player : player.level().getEntity(uuid);

                        return entity == null ? McpToolResult.error("No entity with UUID " + uuid + " on the server") : McpToolResult.text(McpTools.PRETTY_GSON.toJson(describeEntity(entity)));
                    });
                }

                return McpTools.onClient(() -> {
                    Minecraft minecraft = Minecraft.getInstance();

                    if (minecraft.level == null || minecraft.player == null) return McpToolResult.error("No world is loaded");

                    Entity entity = uuid == null
                        ? minecraft.player
                        : clientEntity(uuid);

                    return entity == null ? McpToolResult.error("No entity with UUID " + uuid + " on the client") : McpToolResult.text(McpTools.PRETTY_GSON.toJson(describeEntity(entity)));
                });
            }
        ));

        McpTools.register(McpTool.of(
            "trigger_input_action",
            "Holds down the key mapping of an input action for some ticks, then releases it, so the action fires through the "
                + "engine's timing table as if the user pressed the key. The world must run with no game screen open; see "
                + "set_game_speed 'resume'.",
            """
            {
              "type": "object",
              "properties": {
                "action": {"type": "string", "description": "An input action id, e.g. 'epicfight:combo_attack'."},
                "holdTicks": {"type": "integer", "minimum": 1, "maximum": 200, "description": "About how many ticks the key stays down. Default: 1."}
              },
              "required": ["action"]
            }
            """,
            arguments -> {
                Identifier id = McpTools.requireId(arguments.get("action").getAsString());
                int holdTicks = arguments.has("holdTicks") ? Math.clamp(arguments.get("holdTicks").getAsInt(), 1, 200) : 1;

                KeyMapping keyMapping = McpTools.onClient(() -> {
                    Minecraft minecraft = Minecraft.getInstance();

                    if (minecraft.level == null) throw new IllegalStateException("No world is loaded");
                    if (minecraft.gui.screen() != null) {
                        throw new IllegalStateException(minecraft.gui.screen().getClass().getSimpleName() + " is open, and input is not dispatched under a game screen. Call set_game_speed with resume=true first");
                    }

                    InputAction action = minecraft.level.registryAccess().lookupOrThrow(AkytheraRegistries.DataPack.INPUT_ACTION_KEY).getValue(id);

                    if (action == null) throw new IllegalArgumentException("Unknown input action: " + id);

                    KeyMapping mapping = KeyMapping.get(action.keyMapping());

                    if (mapping == null) throw new IllegalStateException("No key mapping is registered as " + action.keyMapping());

                    return mapping;
                });

                for (int tick = 0; tick < holdTicks; tick++) {
                    McpTools.onClient(() -> {
                        keyMapping.setDown(true);
                        return null;
                    });
                    Thread.sleep(TICK_MILLIS);
                }

                McpTools.onClient(() -> {
                    keyMapping.setDown(false);
                    return null;
                });
                Thread.sleep(TICK_MILLIS * 2);

                return McpToolResult.text("Held " + keyMapping.getName() + " for about " + holdTicks + " ticks and released it");
            }
        ));

        McpTools.register(McpTool.of(
            "set_game_speed",
            "Controls how the world runs. 'resume' closes the game's pause screen the same as the viewport's play button; "
                + "'frozen' stops or restarts ticking like /tick freeze; 'tickRate' sets ticks per second like /tick rate. "
                + "Returns the resulting state.",
            """
            {
              "type": "object",
              "properties": {
                "resume": {"type": "boolean", "description": "Close the pause screen so the world runs in the viewport."},
                "pauseOnLostFocus": {"type": "boolean", "description": "Whether the game pauses while its window is unfocused, like F3+P. False keeps it running while you work."},
                "frozen": {"type": "boolean"},
                "tickRate": {"type": "number", "minimum": 1, "maximum": 10000, "description": "Ticks per second. Vanilla runs 20."}
              }
            }
            """,
            arguments -> {
                McpTools.onClient(() -> {
                    Minecraft minecraft = Minecraft.getInstance();

                    if (arguments.has("pauseOnLostFocus")) minecraft.options.pauseOnLostFocus = arguments.get("pauseOnLostFocus").getAsBoolean();

                    if (arguments.has("resume") && arguments.get("resume").getAsBoolean()) {
                        MainEditorScreen mainScreen = AkytheraEditorLayoutManager.getInstance().getMainScreen();
                        Optional<WorldviewPanel> viewport = mainScreen == null ? Optional.empty() : McpTools.findWidgets(mainScreen, WorldviewPanel.class).stream().findFirst();

                        viewport.ifPresentOrElse(WorldviewPanel::resume, () -> minecraft.gui.setScreen(null));
                    }

                    return null;
                });

                return McpTools.onServer(player -> {
                    ServerTickRateManager ticks = player.level().getServer().tickRateManager();

                    if (arguments.has("frozen")) ticks.setFrozen(arguments.get("frozen").getAsBoolean());
                    if (arguments.has("tickRate")) ticks.setTickRate(Math.clamp(arguments.get("tickRate").getAsFloat(), 1.0F, 10000.0F));

                    Minecraft minecraft = Minecraft.getInstance();

                    return McpToolResult.text("frozen=" + ticks.isFrozen() + ", tickRate=" + ticks.tickrate()
                        + ", pauseOnLostFocus=" + minecraft.options.pauseOnLostFocus + ", windowFocused=" + minecraft.isWindowActive());
                });
            }
        ));

        McpTools.register(McpTool.of(
            "step_ticks",
            "Runs the frozen world forward by some ticks, like /tick step. Freeze it first with set_game_speed frozen=true.",
            """
            {
              "type": "object",
              "properties": {
                "count": {"type": "integer", "minimum": 1, "maximum": 1000, "description": "Default: 1."}
              }
            }
            """,
            arguments -> {
                int count = arguments.has("count") ? Math.clamp(arguments.get("count").getAsInt(), 1, 1000) : 1;

                return McpTools.onServer(player -> player.level().getServer().tickRateManager().stepGameIfPaused(count)
                    ? McpToolResult.text("Stepping " + count + " ticks")
                    : McpToolResult.error("The world is not frozen. Call set_game_speed with frozen=true first"));
            }
        ));

        McpTools.register(McpTool.of(
            "set_camera",
            "Places the world viewport's camera apart from the local player, so the game itself is left unchanged; the view "
                + "switches to third person while it is placed. Give x/y/z (and yaw/pitch) for a fixed camera, or "
                + "'lookAtEntity' to face an entity from 'distance' blocks in front of it at eye height. 'follow' keeps "
                + "that offset as the entity moves. 'release' gives the view back to the player. The editor layout must be open.",
            """
            {
              "type": "object",
              "properties": {
                "x": {"type": "number"}, "y": {"type": "number"}, "z": {"type": "number"},
                "yaw": {"type": "number"}, "pitch": {"type": "number"},
                "lookAtEntity": {"type": "string", "description": "UUID of the entity to face."},
                "distance": {"type": "number", "minimum": 0.5, "maximum": 64, "description": "Default: 4."},
                "follow": {"type": "boolean", "description": "Move with 'lookAtEntity'. Default: false."},
                "release": {"type": "boolean", "description": "Return the view to the player and restore the camera type."}
              }
            }
            """,
            arguments -> McpTools.onClient(() -> {
                Minecraft minecraft = Minecraft.getInstance();
                AkytheraEditorLayoutManager layout = AkytheraEditorLayoutManager.getInstance();
                ViewportCamera camera = layout.getViewportCamera();

                if (!layout.isActive()) return McpToolResult.error("The editor layout is not open");
                if (minecraft.player == null) return McpToolResult.error("No world is loaded");

                if (arguments.has("release") && arguments.get("release").getAsBoolean()) {
                    camera.release();
                    return McpToolResult.text("The viewport shows the player's view again");
                }

                UUID uuid = arguments.has("lookAtEntity") ? UUID.fromString(arguments.get("lookAtEntity").getAsString()) : null;
                Entity target = uuid == null ? null : clientEntity(uuid);

                if (uuid != null && target == null) return McpToolResult.error("No entity with UUID " + uuid + " on the client");

                Vec3 eyes = target == null ? null : target.position().add(0.0D, target.getEyeHeight(), 0.0D);
                double distance = arguments.has("distance") ? Math.clamp(arguments.get("distance").getAsDouble(), 0.5D, 64.0D) : 4.0D;
                Vec3 position = arguments.has("x") && arguments.has("y") && arguments.has("z")
                    ? new Vec3(arguments.get("x").getAsDouble(), arguments.get("y").getAsDouble(), arguments.get("z").getAsDouble())
                    : eyes != null
                        ? eyes.add(Vec3.directionFromRotation(0.0F, target.getYRot()).scale(distance))
                        : minecraft.gameRenderer.mainCamera().position();

                if (target != null && arguments.has("follow") && arguments.get("follow").getAsBoolean()) {
                    camera.follow(target, position.subtract(target.position()));
                    return McpToolResult.text("Following " + target.getName().getString() + " from " + position.subtract(target.position()));
                }

                ViewportCamera.Placement placement = eyes != null
                    ? ViewportCamera.Placement.lookingAt(position, eyes)
                    : new ViewportCamera.Placement(
                        position,
                        arguments.has("yaw") ? arguments.get("yaw").getAsFloat() : minecraft.player.getYRot(),
                        arguments.has("pitch") ? arguments.get("pitch").getAsFloat() : minecraft.player.getXRot()
                    );

                camera.place(placement.position(), placement.yaw(), placement.pitch());

                return McpToolResult.text("Camera at %.2f, %.2f, %.2f facing yaw %.1f, pitch %.1f".formatted(
                    placement.position().x, placement.position().y, placement.position().z, placement.yaw(), placement.pitch()
                ));
            })
        ));
    }

    /// The client copy of the entity with `uuid`, or null when the client has none. Call on the client thread.
    private static @Nullable Entity clientEntity(final UUID uuid) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null) return null;

        return StreamSupport.stream(minecraft.level.entitiesForRendering().spliterator(), false)
            .filter(entity -> entity.getUUID().equals(uuid))
            .findFirst()
            .orElse(null);
    }

    /// The entity state `get_entity_state` reports, read on the entity's own side.
    private static JsonObject describeEntity(final Entity entity) {
        RegistryAccess registryAccess = entity.level().registryAccess();
        JsonObject json = new JsonObject();

        json.addProperty("uuid", entity.getUUID().toString());
        json.addProperty("type", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
        json.addProperty("name", entity.getName().getString());
        json.addProperty("position", "%.2f, %.2f, %.2f".formatted(entity.getX(), entity.getY(), entity.getZ()));
        json.addProperty("onGround", entity.onGround());

        if (entity instanceof LivingEntity living) json.addProperty("health", living.getHealth());

        EntityPatch<?> patch = AkytheraPatches.getEntityPatch(entity);

        if (patch == null) {
            json.add("patch", null);
            return json;
        }

        JsonObject patchJson = new JsonObject();
        JsonObject components = new JsonObject();

        patchJson.addProperty("class", patch.getClass().getSimpleName());
        patch.getComponents().forEach((path, component) -> components.addProperty(path, component.getClass().getSimpleName()));
        patchJson.add("components", components);

        if (patch instanceof LivingEntityPatch<?> livingPatch) patchJson.addProperty("stance", brief(livingPatch.getStance()));
        if (patch.getScript() instanceof DataDrivenScript<?, ?> script) patchJson.add("patchScriptVariables", variablesJson(script.getScriptVariable()));

        json.add("patch", patchJson);

        ArmatureComponent armatureComponent = patch.getArmatureComponent();

        if (armatureComponent != null) {
            JsonObject animator = new JsonObject();
            animator.addProperty("class", armatureComponent.getAnimator().getClass().getSimpleName());

            if (armatureComponent.getAnimator() instanceof BlendingAnimator blendingAnimator) {
                JsonArray layers = new JsonArray();

                for (Map.Entry<AnimationLayerSignature, AnimationLayer> entry : ((BlendingAnimatorAccessor)(Object)blendingAnimator).akytheralabs$getAnimLayers().entrySet()) {
                    JsonObject layer = new JsonObject();

                    layer.addProperty("layer", entry.getKey().identifier() == null ? entry.getKey().toString() : entry.getKey().identifier().toString());
                    layer.add("base", driverJson(registryAccess, ((AnimationLayerAccessor)(Object)entry.getValue()).akytheralabs$getAnimDriver()));
                    layer.add("montage", driverJson(registryAccess, entry.getValue().montagePlayer()));
                    layers.add(layer);
                }

                animator.add("layers", layers);

                JsonArray activeStates = new JsonArray();
                AnimatorProbe.activeStateIds(patch).stream().sorted().forEach(activeStates::add);
                animator.add("activeStates", activeStates);

                if (AnimatorProbe.dataDrivenScript(patch) instanceof DataDrivenScript<?, ?> script) animator.add("scriptVariables", variablesJson(script.getScriptVariable()));
            }

            json.add("animator", animator);
        }

        for (Object component : patch.getComponents().values()) {
            if (!(component instanceof AbilitySystemComponent abilitySystem)) continue;

            JsonObject abilities = new JsonObject();
            JsonArray slots = new JsonArray();

            abilitySystem.getInstances().forEach((slot, instance) -> {
                JsonObject slotJson = new JsonObject();
                ResourceKey<?> blueprint = instance.getAbility() == null ? null : instance.getAbility().getBlueprintKey();

                slotJson.addProperty("slot", slot.identifier() == null ? slot.toString() : slot.identifier().toString());
                slotJson.addProperty("ability", instance.getAbility() == null ? null : blueprint == null ? instance.getAbility().getClass().getSimpleName() : blueprint.identifier().toString());
                slotJson.addProperty("state", instance.getState().getClass().getSimpleName());

                if (instance.getState() instanceof DataDrivenScript<?, ?> script) slotJson.add("variables", variablesJson(script.getScriptVariable()));

                slots.add(slotJson);
            });
            abilities.add("slots", slots);

            JsonArray effects = new JsonArray();

            for (AbilityEffectInstance effect : abilitySystem.getAllEffectInstances()) {
                JsonObject effectJson = new JsonObject();
                effectJson.addProperty("effect", effect.getEffect().getId().toString());
                effectJson.addProperty("role", effect.getRole().toString());
                effectJson.addProperty("remainingTicks", effect.getRemainingTicks());
                effectJson.addProperty("stacks", effect.getStacks());
                effects.add(effectJson);
            }

            abilities.add("effects", effects);

            JsonObject tags = new JsonObject();
            abilitySystem.getOwnerTags().forEach((tag, count) -> tags.addProperty(tag.toString(), count));
            abilities.add("tags", tags);
            abilities.addProperty("playingCues", abilitySystem.getCues().size());

            json.add("abilities", abilities);
        }

        return json;
    }

    private static @Nullable JsonObject driverJson(final RegistryAccess registryAccess, final AnimationDriver driver) {
        PlayableAnimation playing = driver.getPlayingAnimation();

        if (playing == null) return null;

        Optional<? extends ResourceKey<?>> key = switch (playing) {
            case AnimationSequence sequence -> registryAccess.lookupOrThrow(AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY).getResourceKey(sequence);
            case AnimationMontage montage -> registryAccess.lookupOrThrow(AkytheraRegistries.DataPack.ANIM_MONTAGE_KEY).getResourceKey(montage);
            case BlendSpace2D blendSpace -> registryAccess.lookupOrThrow(AkytheraRegistries.DataPack.BLEND_SPACE_2D_KEY).getResourceKey(blendSpace);
            default -> Optional.empty();
        };

        JsonObject json = new JsonObject();
        json.addProperty("animation", key.map(k -> k.identifier().toString()).orElse(playing.getClass().getSimpleName()));
        json.addProperty("driver", driver.getClass().getSimpleName());
        json.addProperty("time", driver.getTime());
        json.addProperty("totalTime", playing.getTotalFrameTime());
        json.addProperty("weight", driver.getWeight());
        json.addProperty("finished", driver.isFinished());

        return json;
    }

    private static JsonObject variablesJson(final ScriptVariables variables) {
        JsonObject json = new JsonObject();
        variables.values().forEach((name, value) -> json.addProperty(name, brief(value)));

        return json;
    }

    private static @Nullable String brief(final @Nullable Object value) {
        if (value == null) return null;

        String text = String.valueOf(value);

        return text.length() > VALUE_TEXT_LIMIT ? text.substring(0, VALUE_TEXT_LIMIT) + "..." : text;
    }
}
