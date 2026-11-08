package com.yesman.akytheralabs.akythera.behavior;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.yesman.akythera.api.world.behavior.BehaviorCondition;
import com.yesman.akythera.api.world.behavior.BehaviorNodeBlueprint;
import com.yesman.akythera.api.world.behavior.BehaviorSubtask;
import com.yesman.akythera.api.world.behavior.BehaviorTask;
import com.yesman.akythera.api.world.behavior.FlowControlNode;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/// The behavior node types the tree editor offers, by role, each with the JSON a fresh node starts from. A built-in
/// type whose fields have no default starts from a placeholder value. A type registered by another mod is offered
/// when it decodes from its `type` alone.
public final class BehaviorNodeCatalog {
    private BehaviorNodeCatalog() {}

    /// The starting JSON of each built-in type, without its `type`.
    private static final Map<Identifier, String> TEMPLATES = Map.ofEntries(
        Map.entry(Akythera.identifier("move_to"), "{\"destination\":{\"type\":\"target\"}}"),
        Map.entry(Akythera.identifier("play_montage"), "{\"montage\":\"akythera:montage\"}"),
        Map.entry(Akythera.identifier("activate_ability"), "{\"slot\":\"akythera:slot\"}"),
        Map.entry(Akythera.identifier("state_machine"), "{\"state_machine\":\"state_machine\"}"),
        Map.entry(Akythera.identifier("data_task"), "{\"task\":\"akythera:task\"}"),
        Map.entry(Akythera.identifier("data_subtask"), "{\"subtask\":\"akythera:subtask\"}"),
        Map.entry(Akythera.identifier("data_condition"), "{\"condition\":\"akythera:condition\"}"),
        Map.entry(Akythera.identifier("blackboard"), "{\"key\":{\"name\":\"\",\"type\":\"minecraft:boolean\"}}")
    );

    /// Where a node of a type is placed.
    public enum Role {
        FLOW_CONTROL,
        TASK,
        CONDITION,
        SUBTASK
    }

    /// The types of each role, by id, with their starting JSON.
    public static Map<Role, Map<Identifier, JsonElement>> entries() {
        Map<Role, Map<Identifier, JsonElement>> entries = new LinkedHashMap<>();

        for (Role role : Role.values()) entries.put(role, new LinkedHashMap<>());

        for (Identifier id : AkytheraRegistries.Frozen.BEHAVIOR_NODE_TYPES.keySet().stream().sorted().toList()) {
            if (id.equals(Akythera.identifier("class"))) continue;

            JsonObject json = JsonParser.parseString(TEMPLATES.getOrDefault(id, "{}")).getAsJsonObject();
            json.addProperty("type", id.toString());

            Role role = roleOf(json);

            if (role != null) entries.get(role).put(id, json);
        }

        return entries;
    }

    /// The blueprint `json` describes, or null when it does not decode.
    public static @Nullable BehaviorNodeBlueprint<?> blueprintOf(final JsonElement json) {
        return BehaviorNodeBlueprint.CODEC.parse(JsonOps.INSTANCE, json).result().orElse(null);
    }

    /// The role of the node `json` describes, by the class of the node its blueprint builds, or null when it does not
    /// decode.
    public static @Nullable Role roleOf(final JsonElement json) {
        BehaviorNodeBlueprint<?> blueprint = blueprintOf(json);

        if (blueprint == null) return null;

        Class<?> node = blueprint.nodeClass();

        if (FlowControlNode.class.isAssignableFrom(node)) return Role.FLOW_CONTROL;
        if (BehaviorTask.class.isAssignableFrom(node)) return Role.TASK;
        if (BehaviorCondition.class.isAssignableFrom(node)) return Role.CONDITION;
        if (BehaviorSubtask.class.isAssignableFrom(node)) return Role.SUBTASK;

        return null;
    }

    /// The type id `json` names, or null.
    public static @Nullable Identifier typeOf(final JsonElement json) {
        return json.isJsonObject() && json.getAsJsonObject().has("type")
            ? Identifier.tryParse(json.getAsJsonObject().get("type").getAsString())
            : null;
    }
}
